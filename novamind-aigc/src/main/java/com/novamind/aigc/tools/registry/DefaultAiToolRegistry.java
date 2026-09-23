package com.novamind.aigc.tools.registry;

import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.ToolRegistryProperties;
import com.novamind.aigc.enums.AgentTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAiToolRegistry implements AiToolRegistry {

    private static final Object[] EMPTY_TOOLS = new Object[0];

    private final ApplicationContext applicationContext;
    private final ToolRegistryProperties properties;

    private final AtomicReference<Map<String, RegisteredTool>> configuredTools = new AtomicReference<>(Map.of());
    private final ConcurrentMap<String, RegisteredTool> dynamicTools = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChanged(EnvironmentChangeEvent event) {
        boolean toolsChanged = event.getKeys().stream().anyMatch(key -> key.startsWith("tj.ai.tools"));
        if (toolsChanged) {
            reload();
        }
    }

    @Override
    public Object[] resolveTools(AgentTypeEnum agentType, Object[] fallbackTools) {
        if (!properties.isEnabled() || !properties.isProgressiveDisclosure()) {
            return fallbackTools == null ? EMPTY_TOOLS : fallbackTools;
        }

        var registeredTools = allTools().values().stream()
                .filter(tool -> tool.supports(agentType))
                .sorted(Comparator.comparingInt(tool -> tool.metadata().getPriority()))
                .map(this::resolveToolBean)
                .filter(Objects::nonNull)
                .distinct()
                .toArray();

        if (registeredTools.length > 0 || hasDefinitions()) {
            return registeredTools;
        }
        return fallbackTools == null ? EMPTY_TOOLS : fallbackTools;
    }

    @Override
    public List<AiToolMetadata> metadata(AgentTypeEnum agentType, int maxContextTokens) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        int tokenBudget = Math.max(1, (int) Math.floor(maxContextTokens * properties.getMetadataBudgetRatio()));
        int usedTokens = 0;
        var result = new ArrayList<AiToolMetadata>();
        var tools = allTools().values().stream()
                .filter(tool -> tool.supports(agentType))
                .sorted(Comparator.comparingInt(tool -> tool.metadata().getPriority()))
                .toList();

        for (RegisteredTool tool : tools) {
            int estimatedTokens = tool.metadata().getEstimatedTokens();
            if (usedTokens + estimatedTokens > tokenBudget) {
                log.warn("Skip AI tool metadata. agent={}, toolId={}, budget={}, used={}, toolTokens={}",
                        agentType, tool.metadata().getId(), tokenBudget, usedTokens, estimatedTokens);
                continue;
            }
            result.add(tool.metadata());
            usedTokens += estimatedTokens;
        }
        return result;
    }

    @Override
    public void registerDynamicTool(AiToolMetadata metadata, Object toolBean) {
        if (metadata == null || toolBean == null || StrUtil.isBlank(metadata.getId())) {
            throw new IllegalArgumentException("Dynamic tool metadata and bean must be valid");
        }
        validateToolBean(metadata.getId(), toolBean);
        dynamicTools.put(metadata.getId(), new RegisteredTool(metadata, toolBean));
    }

    @Override
    public void unregisterDynamicTool(String id) {
        if (StrUtil.isBlank(id)) {
            return;
        }
        dynamicTools.remove(id);
    }

    @Override
    public void reload() {
        var nextRegistry = new LinkedHashMap<String, RegisteredTool>();
        for (ToolRegistryProperties.ToolDefinition definition : properties.getDefinitions()) {
            if (!definition.isEnabled()) {
                continue;
            }
            if (StrUtil.isBlank(definition.getId()) || StrUtil.isBlank(definition.getBeanName())) {
                log.warn("Ignore invalid AI tool definition. id={}, beanName={}", definition.getId(), definition.getBeanName());
                continue;
            }
            if (!applicationContext.containsBean(definition.getBeanName())) {
                log.warn("Ignore AI tool definition. bean not found: {}", definition.getBeanName());
                continue;
            }
            Object bean = applicationContext.getBean(definition.getBeanName());
            validateToolBean(definition.getId(), bean);
            var metadata = AiToolMetadata.builder()
                    .id(definition.getId())
                    .beanName(definition.getBeanName())
                    .name(defaultIfBlank(definition.getName(), definition.getId()))
                    .description(defaultIfBlank(definition.getDescription(), summarizeToolBean(bean)))
                    .agents(definition.getAgents())
                    .priority(definition.getPriority())
                    .enabled(definition.isEnabled())
                    .estimatedTokens(estimateMetadataTokens(definition.getId(), definition.getName(), definition.getDescription()))
                    .build();
            nextRegistry.put(definition.getId(), new RegisteredTool(metadata, bean));
        }
        configuredTools.set(Map.copyOf(nextRegistry));
        log.info("AI tool registry loaded. configured={}, dynamic={}", nextRegistry.size(), dynamicTools.size());
    }

    private Map<String, RegisteredTool> allTools() {
        var tools = new LinkedHashMap<String, RegisteredTool>();
        tools.putAll(configuredTools.get());
        tools.putAll(dynamicTools);
        return tools;
    }

    private boolean hasDefinitions() {
        return !properties.getDefinitions().isEmpty() || !dynamicTools.isEmpty();
    }

    private Object resolveToolBean(RegisteredTool tool) {
        if (tool.bean() != null) {
            return tool.bean();
        }
        String beanName = tool.metadata().getBeanName();
        if (StrUtil.isBlank(beanName) || !applicationContext.containsBean(beanName)) {
            log.warn("AI tool bean unavailable. toolId={}, beanName={}", tool.metadata().getId(), beanName);
            return null;
        }
        return applicationContext.getBean(beanName);
    }

    private void validateToolBean(String toolId, Object bean) {
        boolean hasToolMethod = false;
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                hasToolMethod = true;
                break;
            }
        }
        if (!hasToolMethod) {
            throw new IllegalArgumentException("Tool bean has no @Tool method. toolId=" + toolId + ", bean=" + bean.getClass().getName());
        }
    }

    private String summarizeToolBean(Object bean) {
        var descriptions = new ArrayList<String>();
        for (Method method : bean.getClass().getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            descriptions.add(defaultIfBlank(tool.description(), method.getName()));
        }
        return String.join("; ", descriptions);
    }

    private int estimateMetadataTokens(String... values) {
        int chars = 0;
        for (String value : values) {
            if (value != null) {
                chars += value.length();
            }
        }
        return Math.max(1, (int) Math.ceil(chars / 4.0d));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    private record RegisteredTool(AiToolMetadata metadata, Object bean) {

        private boolean supports(AgentTypeEnum agentType) {
            Set<AgentTypeEnum> agents = metadata.getAgents();
            return metadata.isEnabled() && (agents == null || agents.isEmpty() || agents.contains(agentType));
        }
    }
}

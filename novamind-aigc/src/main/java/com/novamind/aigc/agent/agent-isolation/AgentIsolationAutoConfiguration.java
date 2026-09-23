package com.novamind.aigc.agent.agentisolation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * <h2>R24 Agent Sentinel 隔离 —— 启动自动配置</h2>
 *
 * <p>应用启动时执行：
 * <ol>
 *   <li>读取 classpath:<b>sentinel-agent-rules.json</b>（或 Nacos 下发的同结构配置）</li>
 *   <li>为每个 Agent (learning / qa / homework / writing) 注册独立 FlowRule</li>
 *   <li>把全部规则一次性 {@code FlowRuleManager.loadRules(...)} 推入 Sentinel</li>
 * </ol>
 *
 * <p><b>为什么用独立 JSON 而不是 application.yml？</b>
 * <ul>
 *   <li>运维期望：Nacos 配置中心一键下发/回滚 Agent 限流阈值，JSON 更易在 Nacos 控制台编辑</li>
 *   <li>解耦：业务 application.yml 改不动，限流规则独立管理</li>
 *   <li>可观测：JSON 结构对齐 Sentinel Dashboard 的 "flowRules" 数组，方便批量导入</li>
 * </ul>
 *
 * <p><b>启动隔离性自检：</b>配置类加载完成后会立即读取
 * {@code tj.aigc.sentinel.isolation.agents.<name>.qps}（如果同时配了 properties 会覆盖 JSON）。
 * </p>
 *
 * @author R24-refactor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager")
@ConditionalOnProperty(prefix = "tj.aigc.sentinel.isolation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentIsolationAutoConfiguration {

    /**
     * classpath 下的默认规则文件。可通过 {@code tj.aigc.sentinel.isolation.rules-location} 覆盖。
     */
    public static final String DEFAULT_RULES_LOCATION = "sentinel-agent-rules.json";

    @Value("${tj.aigc.sentinel.isolation.rules-location:" + DEFAULT_RULES_LOCATION + "}")
    private String rulesLocation;

    /**
     * 注册一个全局单例的 {@link AgentSentinelRegistry} Bean，供其他模块注入使用。
     */
    @Bean
    public AgentSentinelRegistry agentSentinelRegistry() {
        return new AgentSentinelRegistry();
    }

    /**
     * 启动时加载外部化规则。
     */
    @PostConstruct
    public void init() {
        log.info("[R24 AgentIsolation] 启动, 加载规则文件: {}", rulesLocation);
        Map<String, Integer> rules = loadRulesFromResource();
        AgentSentinelRegistry registry = agentSentinelRegistry();
        rules.forEach(registry::registerAgent);
        registry.loadAll();
        log.info("[R24 AgentIsolation] 启动完成, agents={}", registry.listAgents());
    }

    /**
     * 从 classpath 读取 JSON，格式：
     * <pre>
     * {
     *   "learning": 100,
     *   "qa": 50,
     *   "homework": 30,
     *   "writing": 40
     * }
     * </pre>
     */
    private Map<String, Integer> loadRulesFromResource() {
        Resource resource = new ClassPathResource(rulesLocation);
        if (!resource.exists()) {
            log.warn("[R24 AgentIsolation] 规则文件 {} 不存在, 使用默认 QPS", rulesLocation);
            return defaultRules();
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Integer> map = new ObjectMapper().readValue(in, new TypeReference<>() {});
            if (map == null || map.isEmpty()) {
                return defaultRules();
            }
            log.info("[R24 AgentIsolation] 已读取 {} 条规则: {}", map.size(), map);
            return map;
        } catch (Exception e) {
            log.error("[R24 AgentIsolation] 读取规则失败, 走默认", e);
            return defaultRules();
        }
    }

    /**
     * 兜底默认 QPS（仅在 JSON 缺失时使用）。
     */
    private Map<String, Integer> defaultRules() {
        Map<String, Integer> map = new HashMap<>();
        map.put(AgentSentinelRegistry.AGENT_LEARNING, 100);
        map.put(AgentSentinelRegistry.AGENT_QA, 50);
        map.put(AgentSentinelRegistry.AGENT_HOMEWORK, 30);
        map.put(AgentSentinelRegistry.AGENT_WRITING, 40);
        return map;
    }
}

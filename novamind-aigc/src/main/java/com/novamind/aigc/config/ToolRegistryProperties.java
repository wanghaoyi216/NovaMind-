package com.novamind.aigc.config;

import com.novamind.aigc.enums.AgentTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.tools")
public class ToolRegistryProperties {

    private boolean enabled = true;

    private boolean progressiveDisclosure = true;

    /**
     * Hard budget for lightweight tool metadata in the model context.
     */
    private double metadataBudgetRatio = 0.02d;

    private List<ToolDefinition> definitions = new ArrayList<>();

    @Data
    public static class ToolDefinition {
        private String id;
        private String beanName;
        private String name;
        private String description;
        private Set<AgentTypeEnum> agents = EnumSet.noneOf(AgentTypeEnum.class);
        private int priority = 100;
        private boolean enabled = true;
    }
}

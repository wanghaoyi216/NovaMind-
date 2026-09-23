package com.novamind.aigc.tools.registry;

import com.novamind.aigc.enums.AgentTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class AiToolMetadata {

    private String id;
    private String beanName;
    private String name;
    private String description;
    private Set<AgentTypeEnum> agents;
    private int priority;
    private boolean enabled;
    private int estimatedTokens;
}

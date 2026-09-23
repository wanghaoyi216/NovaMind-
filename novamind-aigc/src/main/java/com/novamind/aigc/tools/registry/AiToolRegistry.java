package com.novamind.aigc.tools.registry;

import com.novamind.aigc.enums.AgentTypeEnum;

import java.util.List;

public interface AiToolRegistry {

    Object[] resolveTools(AgentTypeEnum agentType, Object[] fallbackTools);

    List<AiToolMetadata> metadata(AgentTypeEnum agentType, int maxContextTokens);

    void registerDynamicTool(AiToolMetadata metadata, Object toolBean);

    void unregisterDynamicTool(String id);

    void reload();
}

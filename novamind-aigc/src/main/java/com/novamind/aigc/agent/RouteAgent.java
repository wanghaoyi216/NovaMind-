package com.novamind.aigc.agent;

import com.novamind.aigc.config.SystemPromptConfig;
import com.novamind.aigc.enums.AgentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 路由智能体
 */
@Component
@RequiredArgsConstructor
public class RouteAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.ROUTE;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRouteAgentSystemMessage().get();
    }

    @Override
    public java.util.Map<String, Object> advisorParams(String sessionId, String requestId) {
        var params = new java.util.HashMap<>(super.advisorParams(sessionId, requestId));
        params.put("isRouting", true);
        return params;
    }
}

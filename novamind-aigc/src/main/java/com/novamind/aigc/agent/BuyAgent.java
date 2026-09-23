package com.novamind.aigc.agent;

import com.novamind.aigc.config.SystemPromptConfig;
import com.novamind.aigc.constants.Constant;
import com.novamind.aigc.enums.AgentTypeEnum;
import com.novamind.aigc.tools.OrderTools;
import com.novamind.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 课程购买智能体
 */
@Component
@RequiredArgsConstructor
public class BuyAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final OrderTools orderTools;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.BUY;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getBuyAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{this.orderTools};
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        var userId = UserContext.getUser();
        return Map.of(
                Constant.USER_ID, userId,
                Constant.REQUEST_ID, requestId);
    }
}

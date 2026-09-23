package com.novamind.aigc.agent;

import com.novamind.aigc.advisor.AdvisorFactory;
import com.novamind.aigc.config.SystemPromptConfig;
import com.novamind.aigc.constants.Constant;
import com.novamind.aigc.enums.AgentTypeEnum;
import com.novamind.aigc.tools.CourseTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 课程推荐智能体
 */
@Component
@RequiredArgsConstructor
public class RecommendAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final CourseTools courseTools;
    private final AdvisorFactory advisorFactory;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRecommendAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{this.courseTools};
    }

    @Override
    public List<Advisor> advisors() {
        return List.of(this.advisorFactory.buildQuestionAnswerAdvisor());
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        return Map.of(Constant.REQUEST_ID, requestId);
    }
}

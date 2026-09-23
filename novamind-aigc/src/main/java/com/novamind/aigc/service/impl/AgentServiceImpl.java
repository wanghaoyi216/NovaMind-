package com.novamind.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.novamind.aigc.agent.AbstractAgent;
import com.novamind.aigc.agent.Agent;
import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.enums.AgentTypeEnum;
import com.novamind.aigc.enums.ChatEventTypeEnum;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.vo.ChatEventVO;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.EnumMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
public class AgentServiceImpl implements ChatService {

    private final Map<AgentTypeEnum, Agent> agentCache = new EnumMap<>(AgentTypeEnum.class);

    @PostConstruct
    public void init() {
        var agents = SpringUtil.getBeansOfType(Agent.class);
        for (Agent agent : agents.values()) {
            this.agentCache.put(agent.getAgentType(), agent);
        }
    }

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        return chat(ChatDTO.builder()
                .question(question)
                .sessionId(sessionId)
                .build());
    }

    @Override
    public Flux<ChatEventVO> chat(ChatDTO chatDTO) {
        var question = chatDTO.getQuestion();
        var sessionId = chatDTO.getSessionId();
        var requestId = cn.hutool.core.util.IdUtil.fastSimpleUUID();

        return Flux.concat(
                Flux.just(
                        ChatEventVO.of(ChatEventTypeEnum.THOUGHT, "分析用户意图，匹配专业的智能体...", requestId, "PREPARE"),
                        ChatEventVO.of(ChatEventTypeEnum.ACTION, "正在智能分流中...", requestId, "ROUTE")
                ),
                Flux.defer(() -> {
                    var routeAgent = this.agentCache.get(AgentTypeEnum.ROUTE);
                    var routeResult = routeAgent.process(question, sessionId);

                    var agentType = AgentTypeEnum.agentNameOf(routeResult);
                    var agent = this.agentCache.get(agentType);
                    if (agent == null) {
                        return Flux.just(
                                ChatEventVO.of(ChatEventTypeEnum.DATA, routeResult, requestId, "STREAM"),
                                AbstractAgent.STOP_EVENT
                        );
                    }
                    return agent.processStream(question, sessionId, requestId);
                })
        );
    }

    @Override
    public void stop(String sessionId) {
        var routeAgent = this.agentCache.get(AgentTypeEnum.ROUTE);
        routeAgent.stop(sessionId);
    }

    @Override
    public String chatText(String question) {
        return "";
    }

}

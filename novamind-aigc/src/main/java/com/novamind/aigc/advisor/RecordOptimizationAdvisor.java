package com.novamind.aigc.advisor;

import cn.hutool.core.map.MapUtil;
import com.novamind.aigc.enums.AgentTypeEnum;
import com.novamind.aigc.memory.MyChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

public class RecordOptimizationAdvisor implements BaseAdvisor {

    private final MyChatMemoryRepository myChatMemoryRepository;

    public RecordOptimizationAdvisor(MyChatMemoryRepository myChatMemoryRepository) {
        this.myChatMemoryRepository = myChatMemoryRepository;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        var context = chatClientResponse.context();
        if (Boolean.TRUE.equals(context.get("isRouting"))) {
            var chatResponse = chatClientResponse.chatResponse();
            if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                var text = chatResponse.getResult().getOutput().getText();
                var agentType = AgentTypeEnum.agentNameOf(text);
                if (agentType != null) {
                    var conversationId = MapUtil.getStr(context, ChatMemory.CONVERSATION_ID);
                    if (conversationId != null) {
                        this.myChatMemoryRepository.optimization(conversationId);
                    }
                }
            }
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }

}
package com.novamind.aigc.advisor;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.memory.compaction.SemanticMemoryCompactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

@RequiredArgsConstructor
public class ContextWindowAdvisor implements BaseAdvisor {

    private final SemanticMemoryCompactionService semanticMemoryCompactionService;

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        var conversationId = MapUtil.getStr(chatClientResponse.context(), ChatMemory.CONVERSATION_ID);
        if (StrUtil.isNotBlank(conversationId)) {
            semanticMemoryCompactionService.compactIfNecessary(conversationId);
        }
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 120;
    }
}

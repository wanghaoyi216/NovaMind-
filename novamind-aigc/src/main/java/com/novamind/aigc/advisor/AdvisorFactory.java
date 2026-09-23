package com.novamind.aigc.advisor;

import com.novamind.aigc.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 集中管理 Spring AI Advisor 实例构建的工厂类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvisorFactory {

    // ES向量数据库
    private final VectorStore vectorStore;
    // RAG检索参数
    private final RagProperties ragProperties;

    /**
     * 构建基于 ES/向量库检索的 RAG 问答拦截器
     *
     * @return QuestionAnswerAdvisor 实例
     */
    public QuestionAnswerAdvisor buildQuestionAnswerAdvisor() {
        return QuestionAnswerAdvisor.builder(this.vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(this.ragProperties.getSimilarityThreshold())
                        .topK(this.ragProperties.getTopK())
                        .build())
                .build();
    }

    /**
     * 根据意图识别结果构建 RAG Advisor
     * 如果不需要 RAG，返回 null
     *
     * @param useRag 是否使用 RAG（由 IntentClassifier 判断）
     * @return QuestionAnswerAdvisor 实例，如果不需要 RAG 则返回 null
     */
    public QuestionAnswerAdvisor buildQuestionAnswerAdvisorIfNeeded(boolean useRag) {
        if (!useRag) {
            log.debug("根据意图识别结果，跳过 RAG Advisor 构建");
            return null;
        }
        log.debug("根据意图识别结果，构建 RAG Advisor");
        return buildQuestionAnswerAdvisor();
    }
}

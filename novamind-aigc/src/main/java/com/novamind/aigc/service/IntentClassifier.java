package com.novamind.aigc.service;

import com.novamind.aigc.config.IntentProperties;
import com.novamind.aigc.constants.RagConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务
 * 用于判断用户问题是否需要进行 RAG 检索增强
 * <p>
 * 基于配置化的关键词匹配和规则引擎，支持动态调整策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifier {

    private final IntentProperties intentProperties;

    /**
     * 判断是否需要使用 RAG
     *
     * @param question 用户问题
     * @return true=需要RAG，false=直接回答
     */
    public boolean shouldUseRag(String question) {
        // 如果意图识别功能未启用，默认使用 RAG
        if (!intentProperties.isEnabled()) {
            log.debug("意图识别功能已禁用，默认启用 RAG");
            return true;
        }

        if (question == null || question.trim().isEmpty()) {
            return false;
        }

        String trimmedQuestion = question.trim();

        // 1. 问题太短，通常是简单对话
        if (trimmedQuestion.length() < intentProperties.getMinLength()) {
            log.debug("问题长度过短({}字符)，跳过RAG: {}", trimmedQuestion.length(), trimmedQuestion);
            return false;
        }

        // 2. 检测是否是闲聊
        for (String keyword : intentProperties.getChatKeywords()) {
            if (trimmedQuestion.startsWith(keyword) || trimmedQuestion.equals(keyword)) {
                log.debug("检测到闲聊关键词 '{}'，跳过RAG", keyword);
                return false;
            }
        }

        // 3. 检测是否包含 RAG 相关关键词
        for (String keyword : intentProperties.getRagKeywords()) {
            if (trimmedQuestion.contains(keyword)) {
                log.debug("检测到RAG关键词 '{}'，启用RAG", keyword);
                return true;
            }
        }

        // 4. 问题较长且没有明确的闲聊特征，默认启用 RAG
        if (trimmedQuestion.length() >= intentProperties.getLongQuestionThreshold()) {
            log.debug("问题较长({}字符)，默认启用RAG", trimmedQuestion.length());
            return true;
        }

        // 5. 默认不启用 RAG
        log.debug("未检测到明确意图，跳过RAG: {}", trimmedQuestion);
        return false;
    }

    /**
     * 获取意图分类结果（用于日志和调试）
     *
     * @param question 用户问题
     * @return 意图描述
     */
    public String classifyIntent(String question) {
        // 如果意图识别功能未启用，返回默认值
        if (!intentProperties.isEnabled()) {
            return RagConstants.INTENT_KNOWLEDGE_QUERY;
        }

        if (question == null || question.trim().isEmpty()) {
            return RagConstants.INTENT_EMPTY;
        }

        String trimmedQuestion = question.trim();

        // 检测闲聊
        for (String keyword : intentProperties.getChatKeywords()) {
            if (trimmedQuestion.startsWith(keyword) || trimmedQuestion.equals(keyword)) {
                return RagConstants.INTENT_CHAT;
            }
        }

        // 检测 RAG 需求
        for (String keyword : intentProperties.getRagKeywords()) {
            if (trimmedQuestion.contains(keyword)) {
                return RagConstants.INTENT_KNOWLEDGE_QUERY;
            }
        }

        // 长问题
        if (trimmedQuestion.length() >= intentProperties.getLongQuestionThreshold()) {
            return RagConstants.INTENT_COMPLEX_QUERY;
        }

        return RagConstants.INTENT_SIMPLE_QUERY;
    }
}

package com.novamind.aigc.constants;

/**
 * RAG 相关常量
 */
public final class RagConstants {

    private RagConstants() {
        // 工具类，禁止实例化
    }

    /**
     * RAG 状态 - 已启用
     */
    public static final String RAG_STATUS_ENABLED = "ENABLED";

    /**
     * RAG 状态 - 已禁用
     */
    public static final String RAG_STATUS_DISABLED = "DISABLED";

    /**
     * RAG 状态 - 正在检索
     */
    public static final String RAG_STATUS_SEARCHING = "SEARCHING";

    /**
     * RAG 状态 - 检索完成
     */
    public static final String RAG_STATUS_SEARCHED = "SEARCHED";

    /**
     * 意图类型 - 知识查询
     */
    public static final String INTENT_KNOWLEDGE_QUERY = "KNOWLEDGE_QUERY";

    /**
     * 意图类型 - 复杂查询
     */
    public static final String INTENT_COMPLEX_QUERY = "COMPLEX_QUERY";

    /**
     * 意图类型 - 简单查询
     */
    public static final String INTENT_SIMPLE_QUERY = "SIMPLE_QUERY";

    /**
     * 意图类型 - 闲聊
     */
    public static final String INTENT_CHAT = "CHAT";

    /**
     * 意图类型 - 空输入
     */
    public static final String INTENT_EMPTY = "EMPTY";

    /**
     * 元数据键 - RAG 是否启用
     */
    public static final String METADATA_RAG_ENABLED = "ragEnabled";

    /**
     * 元数据键 - 意图类型
     */
    public static final String METADATA_INTENT_TYPE = "intentType";

    /**
     * 元数据键 - RAG TopK
     */
    public static final String METADATA_RAG_TOP_K = "ragTopK";

    /**
     * 元数据键 - RAG 相似度阈值
     */
    public static final String METADATA_RAG_SIMILARITY_THRESHOLD = "ragSimilarityThreshold";
}

package com.novamind.aigc.enums;

import lombok.Getter;

/**
 * 知识文档状态
 */
@Getter
public enum KnowledgeDocumentStatusEnum {
    PENDING("PENDING","待处理"),
    PROCESSING("PROCESSING","处理中"),
    PROCESS("PROCESS", "已处理"),
    FAILED("FAILED","处理失败");

    private final String code;
    private final String desc;

    KnowledgeDocumentStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.code;
    }
}

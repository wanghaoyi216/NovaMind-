package com.novamind.aigc.enums;

import lombok.Getter;

/**
 * 聊天会话大模型生成状态枚举
 */
@Getter
public enum ChatGenerationState {
    PREPARING("PREPARING", "准备中"),
    GENERATING("GENERATING", "生成中"),
    COMPLETED("COMPLETED", "已完成"),
    INTERRUPTED("INTERRUPTED", "已被用户中断"),
    FAILED("FAILED", "发生异常失败");

    private final String code;
    private final String desc;

    ChatGenerationState(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.code;
    }
}

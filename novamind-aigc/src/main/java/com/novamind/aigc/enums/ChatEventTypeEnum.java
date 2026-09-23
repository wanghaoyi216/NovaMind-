package com.novamind.aigc.enums;

import com.novamind.common.enums.BaseEnum;
import lombok.Getter;

/**
 * 聊天消息事件类型
 */
@Getter
public enum ChatEventTypeEnum implements BaseEnum {
    DATA(1001, "数据事件"),
    STOP(1002, "停止事件"),
    PARAM(1003, "参数事件"),
    THOUGHT(1101, "思考事件"),
    ACTION(1102, "行动事件"),
    OBSERVATION(1103, "观察事件"),
    METRICS(1104, "指标事件"),
    MEDIA(1105, "多媒体事件"),
    RICH_CONTENT(1106, "富内容事件"),
    ERROR(1500, "异常事件");

    private final int value;
    private final String desc;

    ChatEventTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.name();
    }
}

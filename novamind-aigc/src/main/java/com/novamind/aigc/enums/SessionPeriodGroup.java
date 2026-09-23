package com.novamind.aigc.enums;

import lombok.Getter;

/**
 * 历史会话时间分组枚举
 */
@Getter
public enum SessionPeriodGroup {
    TODAY("当天"),
    LAST_30_DAYS("最近30天"),
    LAST_YEAR("最近1年"),
    MORE_THAN_YEAR("1年以上");

    private final String desc;

    SessionPeriodGroup(String desc) {
        this.desc = desc;
    }

    /**
     * 根据距今天数获取对应的时间分组描述
     *
     * @param days 距今天数差异
     * @return 分组描述名称
     */
    public static String getDescByDays(long days) {
        if (days == 0) {
            return TODAY.getDesc();
        } else if (days <= 30) {
            return LAST_30_DAYS.getDesc();
        } else if (days <= 365) {
            return LAST_YEAR.getDesc();
        } else {
            return MORE_THAN_YEAR.getDesc();
        }
    }
}

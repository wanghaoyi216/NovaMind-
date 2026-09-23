package com.novamind.common.enums;

import lombok.Getter;

import java.util.function.Function;

/**
 * 脱敏类型枚举与策略
 */
@Getter
public enum DesensitizeType {

    /**
     * 手机号脱敏：保留前3后4，如 138****1234
     */
    PHONE(str -> {
        if (str == null || str.length() < 7) {
            return str;
        }
        return str.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }),

    /**
     * 身份证脱敏：保留前6后4，如 110101********1234
     */
    ID_CARD(str -> {
        if (str == null || str.length() < 10) {
            return str;
        }
        return str.replaceAll("(\\d{6})\\d+(\\w{4})", "$1********$2");
    }),

    /**
     * 电子邮箱脱敏：如 a***b@domain.com
     */
    EMAIL(str -> {
        if (str == null || !str.contains("@")) {
            return str;
        }
        int atIndex = str.indexOf("@");
        if (atIndex <= 1) {
            return str;
        }
        return str.substring(0, 1) + "***" + str.substring(atIndex - 1);
    }),

    /**
     * 密码或密钥完全脱敏：******
     */
    PASSWORD(str -> "******"),

    /**
     * 银行卡号脱敏：保留前4后4
     */
    BANK_CARD(str -> {
        if (str == null || str.length() < 8) {
            return str;
        }
        return str.replaceAll("(\\d{4})\\d+(\\d{4})", "$1 **** **** $2");
    });

    private final Function<String, String> desensitizer;

    DesensitizeType(Function<String, String> desensitizer) {
        this.desensitizer = desensitizer;
    }
}

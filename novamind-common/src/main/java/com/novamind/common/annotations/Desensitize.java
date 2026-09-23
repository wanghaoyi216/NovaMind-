package com.novamind.common.annotations;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.novamind.common.autoconfigure.mvc.serializer.DesensitizeSerializer;
import com.novamind.common.enums.DesensitizeType;

import java.lang.annotation.*;

/**
 * 敏感数据脱敏展示注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizeSerializer.class)
public @interface Desensitize {

    /**
     * 脱敏类型
     */
    DesensitizeType type() default DesensitizeType.PHONE;
}

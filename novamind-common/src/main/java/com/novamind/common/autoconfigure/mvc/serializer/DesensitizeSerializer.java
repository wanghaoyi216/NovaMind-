package com.novamind.common.autoconfigure.mvc.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.novamind.common.annotations.Desensitize;
import com.novamind.common.enums.DesensitizeType;

import java.io.IOException;
import java.util.Objects;

/**
 * Jackson 敏感数据脱敏序列化器
 */
public class DesensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private DesensitizeType desensitizeType;

    public DesensitizeSerializer() {
    }

    public DesensitizeSerializer(DesensitizeType desensitizeType) {
        this.desensitizeType = desensitizeType;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (desensitizeType != null) {
            gen.writeString(desensitizeType.getDesensitizer().apply(value));
        } else {
            gen.writeString(value);
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            if (Objects.equals(property.getType().getRawClass(), String.class)) {
                Desensitize desensitize = property.getAnnotation(Desensitize.class);
                if (desensitize == null) {
                    desensitize = property.getContextAnnotation(Desensitize.class);
                }
                if (desensitize != null) {
                    return new DesensitizeSerializer(desensitize.type());
                }
            }
            return prov.findValueSerializer(property.getType(), property);
        }
        return prov.findNullValueSerializer(null);
    }
}

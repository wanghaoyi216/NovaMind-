package com.novamind.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * R25 ES8 升级：标记 {@code dense_vector} 向量字段的注解
 * <p>
 * 7.x 中没有向量字段类型，需通过 runtime script 间接实现；
 * 8.x 原生 {@code dense_vector} 字段，搭配 HNSW 索引与 {@code cosineSimilarity} 函数使用。
 * <p>
 * 使用方式：
 * <pre>
 * public class MemoryDoc {
 *     {@literal @VectorField(dims = 1536, similarity = "cosine")}
 *     private List&lt;Float&gt; embedding;
 * }
 * </pre>
 *
 * @author novamind
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VectorField {

    /** 向量维度（与 Embedding 模型输出一致，如 ada-002 = 1536） */
    int dims() default 1536;

    /** 距离函数：cosine / dot_product / l2_norm */
    String similarity() default "cosine";

    /** 是否建立 HNSW 索引（生产环境必须为 true） */
    boolean index() default true;
}

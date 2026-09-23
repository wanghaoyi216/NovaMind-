package com.novamind.common.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>方法级幂等注解 (P0 改造)</h1>
 *
 * <p>用于写接口（创建订单、支付回调等），避免重复请求导致业务副作用。
 * 与 {@link com.novamind.common.autoconfigure.idempotency.IdempotencyKeyInterceptor}
 * 不同的是：注解作用于 Service / 方法层，能拿到方法参数 / 返回值 SpEL；拦截器作用在 HTTP 层。</p>
 *
 * <p>使用样例：</p>
 * <pre>{@code
 * @Service
 * public class OrderServiceImpl {
 *     @Idempotent(key = "#req.orderId", message = "订单重复创建")
 *     public PlaceOrderResultVO placeOrder(PlaceOrderDTO req) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>SpEL 表达式里支持的变量：</p>
 * <ul>
 *   <li>{@code #req}：绑定到方法的第一个参数（兼容现有代码 {@code placeOrder(PlaceOrderDTO req)}）</li>
 *   <li>{@code #a0}、{@code #a1}...：第 0、1... 个参数</li>
 *   <li>{@code #root.args[i]}：数组方式访问</li>
 * </ul>
 *
 * @author P0-refactor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * SpEL 表达式，最终拼接成 Redis key 后缀：{@code <cacheKeyPrefix><spEL result>}
     * 例如 {@code "#req.orderId"} 会得到 {@code idempotency:annotation:10086}
     */
    String key();

    /**
     * Redis key 前缀，可选；默认为 {@code "idempotency:annotation:"}。
     * 想要跨业务方复用，可以传 {@code "idempotency:order:place:"} 等
     */
    String prefix() default "idempotency:annotation:";

    /**
     * 命中后是否要返回原结果。true=返回反序列化结果；false=抛业务异常。
     */
    boolean returnCached() default true;

    /**
     * 当 returnCached=false 时，命中缓存抛出的业务提示。
     */
    String message() default "操作重复，请稍后再试";

    /**
     * 缓存有效期（秒）。默认 -1 表示读 {@code novamind.idempotency.expire-hours}。
     */
    long ttlSeconds() default -1;
}

package com.novamind.common.autoconfigure.idempotency;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * <h1>幂等性配置 (P0 改造)</h1>
 *
 * <p>对外暴露 Idempotency-Key 拦截器和 @Idempotent 注解所需要的开关与参数。
 * 通过 {@code novamind.idempotency.*} 配置。</p>
 *
 * <pre>{@code
 * novamind:
 *   idempotency:
 *     enabled: true
 *     header-name: Idempotency-Key          # HTTP 头名
 *     cache-key-prefix: "idempotency:"      # Redis key 前缀
 *     expire-hours: 24                      # 响应缓存有效时长
 *     interceptor-enabled: true             # 拦截器总开关
 *     annotation-enabled: true              # @Idempotent 切面总开关
 *     exclude-paths:                        # 排除的 URL 前缀
 *       - /notify/
 *       - /webhooks/
 * }</pre>
 *
 * @author P0-refactor
 */
@Data
@ConfigurationProperties(prefix = "novamind.idempotency")
public class IdempotencyProperties {

    /** 总开关，业务方可以在 application.yml 里关掉 */
    private boolean enabled = true;

    /** HTTP 头名，符合 RFC 的标准头是 {@code Idempotency-Key}（Stripe / GitHub 都用这个） */
    private String headerName = "Idempotency-Key";

    /** Redis key 前缀，配合 header 值形成 {@code idempotency:<uuid>} */
    private String cacheKeyPrefix = "idempotency:";

    /** 响应/缓存有效时长（小时） */
    private long expireHours = 24;

    /** 拦截器开关：HTTP 层（POST/PUT/DELETE）的幂等 */
    private boolean interceptorEnabled = true;

    /** @Idempotent 注解切面开关：方法层的幂等 */
    private boolean annotationEnabled = true;

    /** 不走拦截器的 URL 前缀集合（路径前缀匹配） */
    private Set<String> excludePaths = new HashSet<>(java.util.Arrays.asList("/notify/", "/webhook", "/actuator/"));

    /**
     * 网关层 {@code IdempotencyGlobalFilter} 要拦截的业务前缀（对应网关路由短前缀）。
     * 默认只拦交易/支付/促销域；置为空列表时由网关 Filter 的内置默认值兜底。
     * Servlet 拦截器（业务微服务层）不读此字段，由各服务 WebConfig 自行 addPathPatterns。
     */
    private Set<String> includePrefixes = new HashSet<>(java.util.Arrays.asList("/ts/", "/ps/", "/prs/"));

    /** 网关层响应体缓冲上限（字节），超过则截断不缓存，防止异常大响应撑爆内存 */
    private int maxCachedBodyBytes = 64 * 1024;

    /** 拦截器要拦截的 HTTP 方法集合 */
    private Set<String> interceptMethods = new HashSet<>(java.util.Arrays.asList("POST", "PUT", "DELETE", "PATCH"));

    /** 是否要求必须携带 Idempotency-Key 头（true=缺头直接 400） */
    private boolean requireHeader = false;
}

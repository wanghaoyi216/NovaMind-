package com.novamind.common.autoconfigure.refreshscope;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <h1>运行时可热更新的开关 (R12 配置中心改造)</h1>
 *
 * <p>设计目的：把"频繁在测试 / 灰度时调整"的开关从代码里搬到 Nacos，
 * 不需要重启服务即可生效。典型场景：</p>
 *
 * <ul>
 *   <li>灰度期间临时关闭某个特性（feature-toggle）</li>
 *   <li>调整限流阈值、缓存有效期</li>
 *   <li>灰度名单</li>
 * </ul>
 *
 * <p>Nacos 上对应的配置段：</p>
 * <pre>{@code
 * novamind:
 *   runtime-config:
 *     feature-toggle:
 *       enable-coupon: true
 *       enable-points: false
 *     rate-limit:
 *       orders-per-sec: 200
 *     gray-list:
 *       - user-1001
 *       - user-1002
 * }</pre>
 *
 * <p>关键点：本类绑定的 bean 必须是 {@code @RefreshScope} 作用域，
 * 否则 Nacos 推送 ConfigurationChangeEvent 后只通知 {@code Environment} 变更，
 * 不会重新构造 bean，热更新即失效。</p>
 *
 * @author R12-refactor
 */
@Data
@ConfigurationProperties(prefix = "novamind.runtime-config")
public class RuntimeConfigProperties {

    /**
     * 灰度期间 / 临时关闭特性的开关
     */
    private FeatureToggle featureToggle = new FeatureToggle();

    /**
     * 运行时限流阈值（业务方自行读取）
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 灰度用户名单（小规模名单 OK，大规模建议走 Redis Set）
     */
    private java.util.List<String> grayList = new java.util.ArrayList<>();

    @Data
    public static class FeatureToggle {
        private boolean enableCoupon = true;
        private boolean enablePoints = true;
        private boolean enableDelayMq = true;
    }

    @Data
    public static class RateLimit {
        private int ordersPerSec = 200;
        private int loginPerMin = 60;
    }
}

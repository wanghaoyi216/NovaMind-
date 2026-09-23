package com.novamind.gateway.sentinel;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * <h1>R9 Hot Key 防护：网关层 Sentinel 热点参数限流入口</h1>
 *
 * <p>作为 Sentinel 的 {@code @SentinelResource("hotRule")} 标注点，
 * 在网关收到带 {@code bizOrderNo} / {@code couponId} 参数的请求时，
 * 由网关路由处理器调用本组件的方法，由 Sentinel AOP 拦截并按参数索引做热点限流。</p>
 *
 * <p><b>为什么用单独的 Dispatcher 而不是直接注解 Controller 方法？</b></p>
 * <ul>
 *   <li>Spring Cloud Gateway 是 WebFlux 响应式栈，Controller 不走 Spring MVC，
 *       直接注解在 {@code @RequestMapping} 方法上不生效。</li>
 *   <li>在网关中显式定义一个 Sentinel 标注的服务方法，由 GlobalFilter
 *       或自定义 predicate 调用，确保 Sentinel 能切入。</li>
 * </ul>
 *
 * @author R9-refactor
 */
@Slf4j
@Component
public class HotKeyDispatcher {

    /**
     * 限流入口。Spring Cloud Gateway 网关 filter 收到请求后，
     * 会调用本方法做参数级热点限流判断。
     *
     * @param bizOrderNo 业务订单号（参数索引 0）
     * @param couponId   优惠券 ID（参数索引 1）
     * @return true = 放行；false = 被 Sentinel 限流
     */
    @SentinelResource(value = HotKeySentinelConfig.RESOURCE_HOT_RULE,
            blockHandler = "handleBlock",
            fallback = "fallback")
    public boolean passHotKey(Long bizOrderNo, Long couponId) {
        log.debug("[HotKey] 通过, bizOrderNo={}, couponId={}", bizOrderNo, couponId);
        return true;
    }

    /**
     * Sentinel 限流兜底：被限流时调用此方法，返回 false 由网关 filter 决定下一步动作（429/降级）。
     */
    @SuppressWarnings("unused")
    public boolean handleBlock(Long bizOrderNo, Long couponId, BlockException ex) {
        log.warn("[HotKey] 被 Sentinel 限流, bizOrderNo={}, couponId={}, rule={}",
                bizOrderNo, couponId, ex.getRule());
        return false;
    }

    /**
     * Sentinel 业务异常兜底（保证非限流异常不会让网关 500）。
     */
    @SuppressWarnings("unused")
    public boolean fallback(Long bizOrderNo, Long couponId, Throwable t) {
        log.error("[HotKey] 异常, bizOrderNo={}, couponId={}", bizOrderNo, couponId, t);
        // 异常情况下保守放行，避免误杀正常流量
        return true;
    }

    /**
     * 便捷方法：从 query/form 参数 Map 中抽取热点参数。
     */
    public boolean passHotKeyFromParams(Map<String, String> params) {
        Long bizOrderNo = parseLong(params.get("bizOrderNo"));
        Long couponId = parseLong(params.get("couponId"));
        return passHotKey(bizOrderNo, couponId);
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }
}
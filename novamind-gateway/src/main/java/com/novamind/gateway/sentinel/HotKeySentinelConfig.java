package com.novamind.gateway.sentinel;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowClusterConfig;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowItem;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h1>R9 Hot Key 防护：网关层 Sentinel 热点参数限流</h1>
 *
 * <p><b>业务场景：</b>下单/支付接口在秒杀场景下，单一用户 / 单一订单号可能瞬时打到网关几万 QPS，
 * 普通 {@code FLOW_GRADE_QPS} 流控只能按"接口整体"限流，无法保护"具体某个参数值"的热点。</p>
 *
 * <p><b>方案：</b>用 Sentinel 的 <b>PARAM 模式</b> 热点参数限流，
 * 通过 {@link SentinelResource#value()} 与 {@link ParamFlowRule} 把
 * {@code bizOrderNo} / {@code couponId} 这两个热参数单独限流：</p>
 * <ul>
 *   <li>普通参数：每秒 1000 次</li>
 *   <li>特定值（如大客户黑名单 value=99999）：每秒 50 次</li>
 * </ul>
 *
 * <p><b>工作流：</b></p>
 * <ol>
 *   <li>{@code HotKeyDispatcher} 上的 {@link SentinelResource#value()} 标注为 {@code "hotRule"}，
 *       网关入口收到请求后由 Sentinel 拦截。</li>
 *   <li>Sentinel 读取 {@link ParamFlowRuleManager} 中已注册的 {@code hotRule} 规则，按参数索引 (0/1)
 *       提取 {@code bizOrderNo} / {@code couponId} 做参数级 token bucket 计数。</li>
 *   <li>某参数值超过阈值 → 抛 {@code ParamFlowException}，网关直接 429，不转发下游。</li>
 * </ol>
 *
 * @author R9-refactor
 */
@Slf4j
@Configuration
public class HotKeySentinelConfig {

    /** Sentinel 资源名，必须与 {@link SentinelResource#value()} 一致 */
    public static final String RESOURCE_HOT_RULE = "hotRule";

    /** 普通参数阈值 */
    private static final int DEFAULT_PARAM_QPS = 1000;

    /** 特定参数值阈值（黑名单 value） */
    private static final int HOT_VALUE_QPS = 50;

    /**
     * 应用启动时注册热点规则。
     * <p>
     * Sentinel 规则是 in-memory 静态注册；如果需要热更新，可改造成监听 Nacos 配置中心。
     * </p>
     */
    @PostConstruct
    public void registerHotRules() {
        // 1) 同时注册一个 FlowRule（避免被默认 QPS 拦截器误判）
        FlowRule flow = new FlowRule(RESOURCE_HOT_RULE);
        flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
        flow.setCount(100_000); // 兜底超大阈值，限流交给 ParamFlowRule
        FlowRuleManager.loadRules(Collections.singletonList(flow));

        // 2) 注册热点参数限流规则：参数索引 0 = bizOrderNo
        ParamFlowRule bizRule = new ParamFlowRule(RESOURCE_HOT_RULE)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(DEFAULT_PARAM_QPS);

        // 3) 参数索引 1 = couponId，对特定 value（黑名单 99999）单独低阈值
        ParamFlowRule couponRule = new ParamFlowRule(RESOURCE_HOT_RULE)
                .setParamIdx(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(DEFAULT_PARAM_QPS);
        couponRule.setParamFlowItemList(Collections.singletonList(
                new ParamFlowItem()
                        .setClassType("int")
                        .setObject("99999")
                        .setCount(HOT_VALUE_QPS)
        ));

        // 4) 单机模式即可，不开 cluster
        bizRule.setClusterConfig(new ParamFlowClusterConfig().setFallbackToLocalWhenFail(true));
        couponRule.setClusterConfig(new ParamFlowClusterConfig().setFallbackToLocalWhenFail(true));

        List<ParamFlowRule> paramRules = new ArrayList<>();
        paramRules.add(bizRule);
        paramRules.add(couponRule);

        ParamFlowRuleManager.loadRules(paramRules);
        log.info("[HotKey Sentinel] 已注册热点规则: resource={}, bizOrderNo.qps={}, couponId.hotValue.qps={}",
                RESOURCE_HOT_RULE, DEFAULT_PARAM_QPS, HOT_VALUE_QPS);
    }
}
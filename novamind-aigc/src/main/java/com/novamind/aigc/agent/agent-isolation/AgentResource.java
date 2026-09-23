package com.novamind.aigc.agent.agentisolation;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * <h2>R24 Agent 维度的 ResourceWrapper</h2>
 *
 * <p>本类把 Sentinel 的 {@code resourceName} 抽象为「带 agentName 维度的资源」，
 * 对外暴露 {@link #entry()} / {@link #exit(Entry)}，
 * 业务侧只需要关心「当前请求属于哪个 Agent」，不必关心 resourceName 怎么拼接。</p>
 *
 * <p><b>为什么需要 ResourceWrapper？</b>
 * <ul>
 *   <li>Sentinel 资源名是一个字符串，但每个 Agent 都要写一遍拼接容易出错</li>
 *   <li>把"进入 / 退出"和"日志 / 异常"封装在一个 try-with-resources 里，
 *       避免业务代码漏写 {@code entry.exit()} 导致 slot 统计不准</li>
 *   <li>统一打印「agent 限流日志」便于排查</li>
 * </ul>
 * </p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>
 *   try (AgentResource resource = AgentResource.of("learning")) {
 *       // 业务逻辑
 *   } catch (BlockException e) {
 *       // learning Agent 被限流
 *   }
 * </pre>
 *
 * @author R24-refactor
 */
@Slf4j
@Getter
public class AgentResource implements AutoCloseable {

    private final String agentName;
    private final String resourceName;
    private Entry entry;
    private boolean blocked;

    private AgentResource(String agentName) {
        this.agentName = Objects.requireNonNull(agentName, "agentName");
        this.resourceName = AgentSentinelRegistry.buildResourceName(agentName);
    }

    /**
     * 工厂方法：基于 agentName 构造 wrapper，并立即进入 Sentinel slot。
     * 如果被限流，会抛 {@link BlockException}，由调用方决定如何降级。
     */
    public static AgentResource of(String agentName) throws BlockException {
        AgentResource res = new AgentResource(agentName);
        res.entry = SphU.entry(res.resourceName);
        return res;
    }

    /**
     * 显式退出（一般 try-with-resources 已经处理；保留给手动管理场景）。
     */
    public void exit(Entry entry) {
        if (entry != null) {
            entry.exit();
        }
    }

    @Override
    public void close() {
        if (this.entry != null) {
            this.entry.exit();
        }
    }

    /**
     * 工具型 builder，附带被限流时的快速判断。
     */
    public boolean isBlocked() {
        return this.blocked;
    }

    /**
     * 给 Spring AOP / 自定义注解用的内部 holder，记录当前线程正在执行的 Agent 名称。
     */
    private static final ThreadLocal<String> CURRENT_AGENT = new ThreadLocal<>();

    public static void setCurrentAgent(String agentName) {
        CURRENT_AGENT.set(agentName);
    }

    public static String getCurrentAgent() {
        return CURRENT_AGENT.get();
    }

    public static void clearCurrentAgent() {
        CURRENT_AGENT.remove();
    }
}

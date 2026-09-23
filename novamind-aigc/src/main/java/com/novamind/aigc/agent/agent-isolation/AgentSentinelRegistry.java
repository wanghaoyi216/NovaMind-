package com.novamind.aigc.agent.agentisolation;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>R24 Sentinel 工具权限按 Agent 隔离 —— 规则注册中心</h2>
 *
 * <p><b>核心职责：</b>为不同的 Agent (learning / qa / homework / writing) 注册相互独立的 Sentinel FlowRule。
 * Sentinel 的 {@link FlowRuleManager} 是 JVM 进程级的单例，所有 FlowRule 共享同一个 name space；
 * 本类通过 <b>"资源名以 agentName 为前缀"</b> 的方式实现逻辑隔离：</p>
 *
 * <pre>
 *   resourceName = "agent:learning:tool:course_query"
 *   resourceName = "agent:qa:tool:doc_search"
 * </pre>
 *
 * <p><b>维度选择：</b>Sentinel 原生 FlowRule 按 {@code resourceName} 粒度限流。
 * 同一个 resourceName 共享一套规则；不同 resourceName 互不影响。
 * 因此只要在 resourceName 中嵌入 {@code agentName}，就实现了按 Agent 隔离的限流池。</p>
 *
 * <p><b>为什么不直接用 URL 维度？</b>
 * <ul>
 *   <li>URL 维度适用于"网关层对用户调用接口"的限流（按接口名）</li>
 *   <li>本服务是 AI Agent 工具调用层，<b>同一个 HTTP 入口（/ais/chat）背后可能调用不同的 Agent</b>，
 *       真正的限流粒度是"Agent"，而不是"URL"</li>
 *   <li>所有 Agent 共用同一个 Controller，因此 URL 维度无法区分</li>
 * </ul>
 * </p>
 *
 * @author R24-refactor
 */
@Slf4j
public class AgentSentinelRegistry {

    /** Agent 名称常量（与文档/notes 中提到的业务 Agent 一致） */
    public static final String AGENT_LEARNING = "learning";
    public static final String AGENT_QA = "qa";
    public static final String AGENT_HOMEWORK = "homework";
    public static final String AGENT_WRITING = "writing";

    /** 所有受管的 Agent 名（启动时遍历加载规则） */
    public static final List<String> MANAGED_AGENTS = List.of(
            AGENT_LEARNING, AGENT_QA, AGENT_HOMEWORK, AGENT_WRITING
    );

    /** 资源名前缀，便于在 dashboard 上一眼识别 */
    private static final String RESOURCE_PREFIX = "agent:";

    /** 工具资源后缀 */
    private static final String TOOL_SUFFIX = ":tool:default";

    /** Agent -> QPS 阈值 的运行期快照（外部化配置加载后会写到这里） */
    private final Map<String, Integer> agentQpsMap = new ConcurrentHashMap<>();

    /** 已注册的 FlowRule 缓存：resourceName -> FlowRule */
    private final Map<String, FlowRule> registeredRules = new ConcurrentHashMap<>();

    /**
     * 为单个 Agent 注册一条 FlowRule，resource 形如 {@code agent:learning:tool:default}。
     *
     * @param agentName Agent 名称
     * @param qps      QPS 阈值
     */
    public FlowRule registerAgent(String agentName, double qps) {
        String resource = buildResourceName(agentName);
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        // 部署修复注记：sentinel 1.8.x 的 RuleConstant 无 CONTROL_BEHAVIOR_REJECT，
        // QPS 超限的默认行为即直接拒绝，对应常量为 CONTROL_BEHAVIOR_DEFAULT
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT); // 超出直接拒绝
        rule.setLimitApp("default");

        this.registeredRules.put(resource, rule);
        this.agentQpsMap.put(agentName, (int) qps);
        log.info("[R24 AgentSentinel] register agent={}, resource={}, qps={}",
                agentName, resource, qps);
        return rule;
    }

    /**
     * 把当前所有已注册的 FlowRule 一次性推送到 {@link FlowRuleManager}。
     * 注意：{@link FlowRuleManager#loadRules(List)} 会覆盖同名规则，
     * 但<b>不会</b>清空其他 resourceName 的规则——所以多个 Agent 各自独立、互不影响。
     */
    public void loadAll() {
        List<FlowRule> all = new ArrayList<>(this.registeredRules.values());
        if (all.isEmpty()) {
            log.warn("[R24 AgentSentinel] 没有可加载的 FlowRule，跳过 loadRules");
            return;
        }
        FlowRuleManager.loadRules(all);
        log.info("[R24 AgentSentinel] loaded {} FlowRule(s): {}", all.size(), all);
    }

    /**
     * 重新加载：清掉指定 Agent 名的旧规则，按新 QPS 注册并推送。
     * 用于"把 learning 拉到 1000 QPS、qa 仍 50 QPS"这类动态调整场景。
     */
    public void reloadAgent(String agentName, double newQps) {
        String resource = buildResourceName(agentName);
        this.registeredRules.remove(resource);

        // 重新构造一个只含「非本 Agent 规则 + 新 Agent 规则」的列表，
        // 然后 loadRules —— 因为 FlowRuleManager.loadRules 是「按 resourceName 整体覆盖」，
        // 所以必须保留其他 Agent 的规则，否则会误清空。
        List<FlowRule> next = new ArrayList<>(this.registeredRules.values());
        next.removeIf(r -> r.getResource().startsWith(RESOURCE_PREFIX + agentName + ":"));
        next.add(registerAgent(agentName, newQps));

        FlowRuleManager.loadRules(next);
        this.registeredRules.clear();
        next.forEach(r -> this.registeredRules.put(r.getResource(), r));
        log.info("[R24 AgentSentinel] reload agent={} -> qps={}, totalRules={}",
                agentName, newQps, next.size());
    }

    /**
     * 查询某 Agent 当前的 QPS 阈值（用于测试断言）。
     */
    public int getQps(String agentName) {
        return this.agentQpsMap.getOrDefault(agentName, -1);
    }

    /**
     * 列出所有已注册的 Agent。
     */
    public List<String> listAgents() {
        return Collections.unmodifiableList(new ArrayList<>(this.agentQpsMap.keySet()));
    }

    /**
     * 构造 Sentinel 资源名：{@code agent:<agentName>:tool:default}。
     */
    public static String buildResourceName(String agentName) {
        return RESOURCE_PREFIX + agentName + TOOL_SUFFIX;
    }
}

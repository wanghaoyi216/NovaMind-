package com.novamind.aigc.agent.agentisolation;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h2>R24 Agent Sentinel 隔离测试</h2>
 *
 * <p><b>测试目标：</b>验证
 * <ol>
 *   <li>四个 Agent (learning / qa / homework / writing) 启动后各自有独立 FlowRule</li>
 *   <li>resourceName 完全独立（基于 agentName 维度）</li>
 *   <li>把 learning 拉到 1000 QPS 后，qa 仍然按 50 QPS 服务——互不影响</li>
 * </ol>
 *
 * <p><b>测试策略：</b>直接调用 {@link AgentSentinelRegistry} 注册规则，
 * 再用 {@link AgentResource#of(String)} 触发限流计数；Sentinel 的 slot 统计是进程内单例，
 * 用真实 QPS 跑测试会很慢，所以这里用 <b>直接调 FlowRuleManager 验证规则加载</b> +
 * <b>低 QPS + 高并发触发 BlockException</b> 的组合，确保测试稳定且快速。</p>
 */
class AgentSentinelIsolationTest {

    private AgentSentinelRegistry registry;

    @BeforeEach
    void setUp() {
        // 每个测试方法前重置：清空 FlowRuleManager + 重建 registry
        FlowRuleManager.loadRules(List.of());
        registry = new AgentSentinelRegistry();
    }

    @Test
    @DisplayName("1) 四个 Agent 启动后各自有独立 FlowRule, resourceName 互不重叠")
    void testEachAgentHasIndependentFlowRule() {
        registry.registerAgent(AgentSentinelRegistry.AGENT_LEARNING, 100);
        registry.registerAgent(AgentSentinelRegistry.AGENT_QA, 50);
        registry.registerAgent(AgentSentinelRegistry.AGENT_HOMEWORK, 30);
        registry.registerAgent(AgentSentinelRegistry.AGENT_WRITING, 40);
        registry.loadAll();

        // 断言：所有 4 个 resource 都在 FlowRuleManager 里
        List<FlowRule> rules = FlowRuleManager.getRules();
        assertEquals(4, rules.size(), "应当注册 4 条 FlowRule");

        assertTrue(rules.stream().anyMatch(r -> r.getResource().equals("agent:learning:tool:default")));
        assertTrue(rules.stream().anyMatch(r -> r.getResource().equals("agent:qa:tool:default")));
        assertTrue(rules.stream().anyMatch(r -> r.getResource().equals("agent:homework:tool:default")));
        assertTrue(rules.stream().anyMatch(r -> r.getResource().equals("agent:writing:tool:default")));

        // 断言 QPS 与配置一致
        assertEquals(100, rules.stream().filter(r -> r.getResource().contains("learning")).findFirst().orElseThrow().getCount());
        assertEquals(50, rules.stream().filter(r -> r.getResource().contains("qa")).findFirst().orElseThrow().getCount());
        assertEquals(30, rules.stream().filter(r -> r.getResource().contains("homework")).findFirst().orElseThrow().getCount());
        assertEquals(40, rules.stream().filter(r -> r.getResource().contains("writing")).findFirst().orElseThrow().getCount());
    }

    @Test
    @DisplayName("2) 隔离性: 把 learning 拉到 1000 QPS, qa 仍按 50 QPS 工作")
    void testIsolationWhenLearningSaturated() {
        // 先用默认阈值启动
        registry.registerAgent(AgentSentinelRegistry.AGENT_LEARNING, 100);
        registry.registerAgent(AgentSentinelRegistry.AGENT_QA, 50);
        registry.registerAgent(AgentSentinelRegistry.AGENT_HOMEWORK, 30);
        registry.registerAgent(AgentSentinelRegistry.AGENT_WRITING, 40);
        registry.loadAll();

        // 学习 Agent 规则「拉满」：100 -> 1000
        registry.reloadAgent(AgentSentinelRegistry.AGENT_LEARNING, 1000);

        List<FlowRule> rules = FlowRuleManager.getRules();
        FlowRule learning = rules.stream()
                .filter(r -> r.getResource().equals("agent:learning:tool:default"))
                .findFirst().orElseThrow();
        FlowRule qa = rules.stream()
                .filter(r -> r.getResource().equals("agent:qa:tool:default"))
                .findFirst().orElseThrow();

        // 断言：learning 已经变成 1000
        assertEquals(1000.0, learning.getCount(), 0.01, "learning 应被拉到 1000 QPS");
        // 断言：qa 仍然是 50（隔离生效）
        assertEquals(50.0, qa.getCount(), 0.01, "qa 仍应保持 50 QPS 不受影响");

        // registry 内部也记录了正确的 QPS
        assertEquals(1000, registry.getQps(AgentSentinelRegistry.AGENT_LEARNING));
        assertEquals(50, registry.getQps(AgentSentinelRegistry.AGENT_QA));
    }

    @Test
    @DisplayName("3) 隔离性: 低 QPS 的 qa 在高并发下被限流, learning 仍能正常进入")
    void testRuntimeIsolation() throws InterruptedException {
        // qa 限流非常小，方便测试触发 BlockException
        registry.registerAgent(AgentSentinelRegistry.AGENT_QA, 2);
        registry.registerAgent(AgentSentinelRegistry.AGENT_LEARNING, 10000);
        registry.loadAll();

        AtomicInteger qaPassed = new AtomicInteger();
        AtomicInteger qaBlocked = new AtomicInteger();
        AtomicInteger learningPassed = new AtomicInteger();

        // 启动一个线程猛打 qa
        Thread qaAttacker = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                try (AgentResource r = AgentResource.of(AgentSentinelRegistry.AGENT_QA)) {
                    qaPassed.incrementAndGet();
                } catch (BlockException e) {
                    qaBlocked.incrementAndGet();
                }
            }
        });

        // 同时 learning 完全无压力
        Thread learningWorker = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                try (AgentResource r = AgentResource.of(AgentSentinelRegistry.AGENT_LEARNING)) {
                    learningPassed.incrementAndGet();
                } catch (BlockException ignore) {
                }
            }
        });

        qaAttacker.start();
        learningWorker.start();
        qaAttacker.join();
        learningWorker.join();

        // 1) qa 一定有被限流的请求
        assertTrue(qaBlocked.get() > 0, "qa 在 QPS=2 的限流下, 应当出现被 Block 的请求, blocked=" + qaBlocked.get());
        // 2) learning 全部通过（QPS=10000 完全不构成压力）
        assertEquals(100, learningPassed.get(), "learning 在 QPS=10000 下应当 100% 通过");
        // 3) qa 通过的请求数远小于 200，证明限流确实生效
        assertTrue(qaPassed.get() < 200, "qa 应当被部分拦截, passed=" + qaPassed.get());
    }

    @Test
    @DisplayName("4) 资源名拼接规则: agent:<name>:tool:default")
    void testResourceNameFormat() {
        assertEquals("agent:learning:tool:default", AgentSentinelRegistry.buildResourceName("learning"));
        assertEquals("agent:qa:tool:default", AgentSentinelRegistry.buildResourceName("qa"));
        assertEquals("agent:homework:tool:default", AgentSentinelRegistry.buildResourceName("homework"));
        assertEquals("agent:writing:tool:default", AgentSentinelRegistry.buildResourceName("writing"));
    }

    @Test
    @DisplayName("5) ThreadLocal: currentAgent 在嵌套调用中能正确传递")
    void testCurrentAgentHolder() {
        AgentResource.setCurrentAgent("learning");
        assertEquals("learning", AgentResource.getCurrentAgent());

        AgentResource.setCurrentAgent("qa");
        assertEquals("qa", AgentResource.getCurrentAgent());

        AgentResource.clearCurrentAgent();
        assertNull(AgentResource.getCurrentAgent());
    }
}

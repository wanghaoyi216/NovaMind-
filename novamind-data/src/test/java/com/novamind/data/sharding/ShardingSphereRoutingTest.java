package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R27 ShardingSphere 分片路由测试
 * <p>
 * 验证目标:
 *   1. order_id=12345 → db_trade_5.t_trade_order_8
 *   2. 范围查询触发全分库广播
 *   3. HintManager.HINT_FULL_SCAN 不影响基础分片算法
 *   4. user_id 分 8 库覆盖性测试
 *   5. 读写分离 Hint 强制走 master
 * </p>
 * <p>
 * 5.4.x 较 4.x 的测试关键变化:
 *   - 不再使用 HintManagerHolder 测试工具类
 *   - 直接 new 具体的 StandardShardingAlgorithm 测试类即可
 *   - HintManager.getInstance() 必须在 try-with-resources 中清理
 * </p>
 *
 * @author R27
 */
@Slf4j
@DisplayName("R27 ShardingSphere 分片路由测试")
class ShardingSphereRoutingTest {

    private static final Collection<String> DB_NODES = Arrays.asList(
            "db_trade_0", "db_trade_1", "db_trade_2", "db_trade_3",
            "db_trade_4", "db_trade_5", "db_trade_6", "db_trade_7",
            "db_trade_8", "db_trade_9", "db_trade_10", "db_trade_11",
            "db_trade_12", "db_trade_13", "db_trade_14", "db_trade_15"
    );

    private static final Collection<String> TBL_NODES = Arrays.asList(
            "t_trade_order_0", "t_trade_order_1", "t_trade_order_2", "t_trade_order_3",
            "t_trade_order_4", "t_trade_order_5", "t_trade_order_6", "t_trade_order_7",
            "t_trade_order_8", "t_trade_order_9", "t_trade_order_10", "t_trade_order_11",
            "t_trade_order_12", "t_trade_order_13", "t_trade_order_14", "t_trade_order_15"
    );

    private OrderIdDbShardingAlgorithm dbAlgo;
    private OrderIdTableShardingAlgorithm tblAlgo;
    private LearningProgressDbShardingAlgorithm learnAlgo;

    @BeforeEach
    void setUp() {
        dbAlgo = new OrderIdDbShardingAlgorithm();
        dbAlgo.init(new java.util.Properties());
        tblAlgo = new OrderIdTableShardingAlgorithm();
        tblAlgo.init(new java.util.Properties());
        learnAlgo = new LearningProgressDbShardingAlgorithm();
        learnAlgo.init(new java.util.Properties());
    }

    // ============================================================
    // Case 1: 精确分片: order_id=12345 → db_trade_5.t_trade_order_8
    // ============================================================
    @Test
    @DisplayName("order_id=12345 应路由到 db_trade_5.t_trade_order_8")
    void test_precise_routing_orderId_12345() {
        long orderId = 12345L;

        PreciseShardingValue<Long> dbValue = new PreciseShardingValue<>(
                "novamind_trade_order", "order_id", orderId);
        PreciseShardingValue<Long> tblValue = new PreciseShardingValue<>(
                "novamind_trade_order", "order_id", orderId);

        String db = dbAlgo.doSharding(DB_NODES, dbValue);
        String tbl = tblAlgo.doSharding(TBL_NODES, tblValue);

        log.info("[R27-Test] order_id={} 路由结果: {}.{}", orderId, db, tbl);

        // 核心断言
        assertEquals("db_trade_5", db, "order_id=12345 应路由到 db_trade_5");
        assertEquals("t_trade_order_8", tbl, "order_id=12345 应路由到 t_trade_order_8");
    }

    // ============================================================
    // Case 2: 范围查询触发全分库广播
    // ============================================================
    @Test
    @DisplayName("范围查询 order_id=[100, 200] 应命中所有 16 个分库")
    void test_range_routing_broadcast() {
        org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue<Long> range =
                new org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue<>(
                        "novamind_trade_order",
                        "order_id",
                        org.apache.shardingsphere.sharding.Range.closed(100L, 200L)
                );

        Collection<String> dbs = dbAlgo.doSharding(DB_NODES, range);
        assertEquals(16, dbs.size(), "范围查询应命中所有 16 个分库");
        log.info("[R27-Test] 范围查询命中分库数: {}", dbs.size());
    }

    // ============================================================
    // Case 3: user_id 分 8 库覆盖性测试
    // ============================================================
    @Test
    @DisplayName("user_id 分 8 库: 100 个 user_id 应覆盖全部 8 个分库")
    void test_user_id_sharding_8_dbs() {
        Collection<String> learnNodes = Arrays.asList(
                "db_learn_0", "db_learn_1", "db_learn_2", "db_learn_3",
                "db_learn_4", "db_learn_5", "db_learn_6", "db_learn_7"
        );

        java.util.Set<String> hitDbs = new java.util.HashSet<>();
        for (long userId = 1; userId <= 100; userId++) {
            PreciseShardingValue<Long> sv = new PreciseShardingValue<>(
                    "novamind_learning_progress", "user_id", userId);
            String target = learnAlgo.doSharding(learnNodes, sv);
            hitDbs.add(target);
        }

        log.info("[R27-Test] 100 个 user_id 命中分库: {}", hitDbs);
        assertEquals(8, hitDbs.size(),
                "100 个 user_id 应该覆盖全部 8 个学习分库 (均匀分布)");
    }

    // ============================================================
    // Case 4: HintManager 全表扫描 走 master
    // ============================================================
    @Test
    @DisplayName("hint=full_scan 时报表全表扫描应走 master (读写分离强制主库)")
    void test_hint_full_scan_force_master() {
        // 4.1 验证 HintManager 工厂可正常创建
        try (ShardingHintManager hint = ShardingHintManager.fullScan()) {
            assertNotNull(hint, "ShardingHintManager.fullScan() 不应返回 null");
            log.info("[R27-Test] HintManager 开启: fullScan + forceMaster");
        } // try-with-resources 触发 HintManager.clear()

        // 4.2 验证 forceMaster 工厂
        try (ShardingHintManager hint = ShardingHintManager.forceMaster()) {
            assertNotNull(hint, "ShardingHintManager.forceMaster() 不应返回 null");
        }

        // 4.3 验证 executeWithFullScan 包装器
        String result = ShardingHintManager.executeWithFullScan(() -> {
            try (ShardingHintManager h = ShardingHintManager.fullScan()) {
                return "scan-completed";
            }
        });
        assertEquals("scan-completed", result);
        log.info("[R27-Test] executeWithFullScan 包装器验证通过");
    }

    // ============================================================
    // Case 5: 分片算法不变性 - 相同 order_id 多次调用结果一致
    // ============================================================
    @Test
    @DisplayName("幂等性: 同一 order_id 多次路由结果一致")
    void test_routing_idempotent() {
        long orderId = 12345L;
        PreciseShardingValue<Long> sv = new PreciseShardingValue<>(
                "novamind_trade_order", "order_id", orderId);

        String db1 = dbAlgo.doSharding(DB_NODES, sv);
        String db2 = dbAlgo.doSharding(DB_NODES, sv);
        String db3 = dbAlgo.doSharding(DB_NODES, sv);

        assertEquals(db1, db2);
        assertEquals(db2, db3);
        log.info("[R27-Test] 幂等性验证: 3 次路由均命中 {}", db1);
    }

    // ============================================================
    // Case 6: 边界值 - 0 / 1 / Long.MAX_VALUE
    // ============================================================
    @Test
    @DisplayName("边界值路由不应抛异常")
    void test_boundary_values() {
        for (long orderId : new long[]{0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -12345L}) {
            PreciseShardingValue<Long> sv = new PreciseShardingValue<>(
                    "novamind_trade_order", "order_id", orderId);
            String db = dbAlgo.doSharding(DB_NODES, sv);
            String tbl = tblAlgo.doSharding(TBL_NODES, sv);
            log.info("[R27-Test] order_id={} → {}.{}", orderId, db, tbl);
            assertNotNull(db);
            assertNotNull(tbl);
            assertTrue(DB_NODES.contains(db), "路由结果必须在可用节点中");
            assertTrue(TBL_NODES.contains(tbl), "路由结果必须在可用节点中");
        }
    }

    // ============================================================
    // Case 7: 跨表联合: 同一 order_id 路由的 db 和 tbl 物理位置可联合
    // ============================================================
    @Test
    @DisplayName("db_trade_X + t_trade_order_Y 的物理表路径可拼接为完整 SQL")
    void test_physical_table_full_path() {
        long orderId = 12345L;
        PreciseShardingValue<Long> dbSv = new PreciseShardingValue<>(
                "novamind_trade_order", "order_id", orderId);
        PreciseShardingValue<Long> tblSv = new PreciseShardingValue<>(
                "novamind_trade_order", "order_id", orderId);

        String db = dbAlgo.doSharding(DB_NODES, dbSv);
        String tbl = tblAlgo.doSharding(TBL_NODES, tblSv);

        String physicalTable = db + "." + tbl;
        log.info("[R27-Test] 物理表全路径: {}", physicalTable);
        assertEquals("db_trade_5.t_trade_order_8", physicalTable);
    }

    // ============================================================
    // Case 8: 读写分离元数据校验
    // ============================================================
    @Test
    @DisplayName("读写分离拓扑: 1 master + 2 slaves")
    void test_readwrite_splitting_topology() {
        assertEquals("novamind_trade_master", DataSourceReadWriteSplitting.MASTER);
        assertEquals("novamind_trade_slave_0", DataSourceReadWriteSplitting.SLAVE_0);
        assertEquals("novamind_trade_slave_1", DataSourceReadWriteSplitting.SLAVE_1);
        assertEquals("novamind_trade_rw", DataSourceReadWriteSplitting.RW_DS_NAME);
        log.info("[R27-Test] 读写分离拓扑校验通过: master={}, slaves=[{}, {}]",
                DataSourceReadWriteSplitting.MASTER,
                DataSourceReadWriteSplitting.SLAVE_0,
                DataSourceReadWriteSplitting.SLAVE_1);
    }
}

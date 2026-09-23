package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.hint.HintManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * R27 ShardingSphere 5.4.x HintManager 强制路由工具
 * <p>
 * 用途:
 *   - 报表类全表扫描: HintManager.setDatabaseShardingValue / addTableShardingValue
 *   - 强制走主库: HintManager.setWriteRouteOnly (写)
 *   - 强制走主库读: HintManager.setReadWriteSplittingHint (5.4.x 新增)
 * </p>
 * <p>
 * 5.4.x 关键变化 (vs 4.x):
 *   - 4.x: HintManager.getInstance() 单例
 *   - 5.4.x: HintManager.clear() 必须显式调用, 否则 ThreadLocal 泄漏
 *   - 5.4.x: 新增 HintManager.setReadWriteSplittingHint("force_master")
 * </p>
 * <p>
 * 典型用法 (try-with-resources):
 * <pre>
 *   try (ShardingHintManager hint = ShardingHintManager.fullScan()) {
 *       reportMapper.fullScanAll();
 *   } // 自动 clear
 * </pre>
 *
 * @author R27
 */
@Slf4j
public class ShardingHintManager implements AutoCloseable {

    /** Hint 名: 强制全分库全表扫描 (报表场景) */
    public static final String HINT_FULL_SCAN = "full_scan";

    /** Hint 名: 强制读写分离走 master (报表实时性要求) */
    public static final String HINT_FORCE_MASTER = "force_master";

    private final HintManager delegate;

    private ShardingHintManager(HintManager delegate) {
        this.delegate = delegate;
    }

    // ============================================================
    // 工厂方法
    // ============================================================

    /**
     * 全表扫描 Hint: 强制路由到所有分库分表, 用于报表聚合
     * <p>
     * 等价于 4.x 的:
     *   HintManager hint = HintManager.getInstance();
     *   hint.setDatabaseShardingValue(HINT_FULL_SCAN);
     *   hint.addTableShardingValue(HINT_FULL_SCAN);
     * </p>
     */
    public static ShardingHintManager fullScan() {
        log.info("[R27-Hint] 开启全表扫描路由 (报表场景)");
        HintManager hint = HintManager.getInstance();
        hint.setDatabaseShardingValue(HINT_FULL_SCAN);
        hint.addTableShardingValue(HINT_FULL_SCAN);
        // 5.4.x 新增: 报表要求强实时, 强制走 master
        hint.setReadWriteSplittingHint(HINT_FORCE_MASTER);
        return new ShardingHintManager(hint);
    }

    /**
     * 强制走 master (非全表扫描, 单一订单查询但要求读主库)
     */
    public static ShardingHintManager forceMaster() {
        log.info("[R27-Hint] 强制读写分离走 master");
        HintManager hint = HintManager.getInstance();
        hint.setReadWriteSplittingHint(HINT_FORCE_MASTER);
        return new ShardingHintManager(hint);
    }

    /**
     * 自定义 Hint 路由 (高级用法)
     *
     * @param dbHint     数据库分片 hint 值
     * @param tableHints 表分片 hint 值
     */
    public static ShardingHintManager custom(String dbHint, String... tableHints) {
        HintManager hint = HintManager.getInstance();
        hint.setDatabaseShardingValue(dbHint);
        for (String t : tableHints) {
            hint.addTableShardingValue(t);
        }
        return new ShardingHintManager(hint);
    }

    /**
     * 包装任意 Supplier, 自动管理 Hint 生命周期
     */
    public static <T> T executeWithFullScan(Supplier<T> action) {
        try (ShardingHintManager hint = fullScan()) {
            return action.get();
        }
    }

    // ============================================================
    // AutoCloseable
    // ============================================================

    @Override
    public void close() {
        try {
            HintManager.clear();
            log.debug("[R27-Hint] HintManager 资源已清理 (ThreadLocal cleared)");
        } catch (Exception e) {
            log.warn("[R27-Hint] HintManager 清理异常", e);
        }
    }
}

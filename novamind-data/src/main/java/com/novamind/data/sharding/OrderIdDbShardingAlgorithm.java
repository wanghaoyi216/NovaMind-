package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * R27 订单分库策略: 按 order_id 哈希分 16 库
 * <p>
 * 公式: db_index = (|order_id| * 13) % 16
 * </p>
 * <p>
 * 验证: order_id=12345 → (12345*13) % 16 = 160485 % 16 = 5 → db_5
 * </p>
 *
 * @author R27
 * @since 1.0
 */
@Slf4j
public class OrderIdDbShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    /** 16 个分库 */
    private static final int DB_COUNT = 16;
    /** 哈希乘法因子 (Knuth multiplicative hash) */
    private static final long MULTIPLIER = 13L;

    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<Long> shardingValue) {
        long orderId = nullSafe(shardingValue.getValue());
        int dbIndex = (int) (Math.abs(orderId * MULTIPLIER) % DB_COUNT);
        String target = "db_trade_" + dbIndex;

        log.debug("[R27-Sharding] order_id={} → 路由到分库: {}", orderId, target);

        if (!availableTargetNames.contains(target)) {
            throw new UnsupportedOperationException(
                    "R27-Sharding 路由错误: order_id=" + orderId
                            + " 计算分库=" + target
                            + " 不在可用节点 " + availableTargetNames + " 中");
        }
        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<Long> shardingValue) {
        // 范围查询: 命中所有分库 (HintManager.fullScan=true 时强制走 master)
        log.warn("[R27-Sharding] order_id 范围查询 [{} - {}] 触发全分库广播, 共 {} 个分库",
                shardingValue.getValueRange().lowerEndpoint(),
                shardingValue.getValueRange().upperEndpoint(),
                availableTargetNames.size());
        return new LinkedHashSet<>(availableTargetNames);
    }

    @Override
    public void init(Properties props) {
        log.info("R27 OrderIdDbShardingAlgorithm init: {}", props);
    }

    @Override
    public String getType() {
        return "ORDER_ID_DB";
    }

    private long nullSafe(Long v) {
        return v == null ? 0L : v;
    }
}

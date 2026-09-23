package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Properties;

/**
 * R27 订单分表策略: 按 order_id 哈希分 16 表 (单库内)
 * <p>
 * 公式: tbl_index = (|order_id| * 27 + 5) % 16
 * </p>
 * <p>
 * 验证: order_id=12345 → (12345*27 + 5) % 16 = 333320 % 16 = 8 → t_trade_order_8
 * </p>
 * <p>
 * 注意: db_index 和 tbl_index 使用不同乘法因子, 避免热点
 * </p>
 *
 * @author R27
 */
@Slf4j
public class OrderIdTableShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    /** 16 张物理表 */
    private static final int TBL_COUNT = 16;
    /** 表格使用不同的乘法因子以分散热点 */
    private static final long MULTIPLIER = 27L;
    private static final long OFFSET = 5L;

    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<Long> shardingValue) {
        long orderId = nullSafe(shardingValue.getValue());
        int tblIndex = (int) (Math.abs(orderId * MULTIPLIER + OFFSET) % TBL_COUNT);
        String target = "t_trade_order_" + tblIndex;

        log.debug("[R27-Sharding] order_id={} → 路由到物理表: {}", orderId, target);

        if (!availableTargetNames.contains(target)) {
            throw new UnsupportedOperationException(
                    "R27-Sharding 路由错误: order_id=" + orderId
                            + " 计算分表=" + target
                            + " 不在可用表 " + availableTargetNames + " 中");
        }
        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<Long> shardingValue) {
        log.warn("[R27-Sharding] order_id 范围查询触发全表广播, 共 {} 张物理表",
                availableTargetNames.size());
        return new LinkedHashSet<>(availableTargetNames);
    }

    @Override
    public void init(Properties props) {
        log.info("R27 OrderIdTableShardingAlgorithm init: {}", props);
    }

    @Override
    public String getType() {
        return "ORDER_ID_TBL";
    }

    private long nullSafe(Long v) {
        return v == null ? 0L : v;
    }
}

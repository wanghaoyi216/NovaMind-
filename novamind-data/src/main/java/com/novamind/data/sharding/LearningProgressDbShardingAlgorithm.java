package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Properties;

/**
 * R27 学习进度分库策略: 按 user_id 哈希分 8 库
 * <p>
 * 公式: db_index = (|user_id| * 17) % 8
 * </p>
 * <p>
 * 表名: novamind_learning_progress (单库单表, 不分表)
 * </p>
 *
 * @author R27
 */
@Slf4j
public class LearningProgressDbShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    private static final int DB_COUNT = 8;
    private static final long MULTIPLIER = 17L;

    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<Long> shardingValue) {
        long userId = nullSafe(shardingValue.getValue());
        int dbIndex = (int) (Math.abs(userId * MULTIPLIER) % DB_COUNT);
        String target = "db_learn_" + dbIndex;

        log.debug("[R27-Sharding] user_id={} → 路由到学习分库: {}", userId, target);
        return target;
    }

    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<Long> shardingValue) {
        return new LinkedHashSet<>(availableTargetNames);
    }

    @Override
    public void init(Properties props) {
        log.info("R27 LearningProgressDbShardingAlgorithm init: {}", props);
    }

    @Override
    public String getType() {
        return "USER_ID_DB";
    }

    private long nullSafe(Long v) {
        return v == null ? 0L : v;
    }
}

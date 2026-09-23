package com.novamind.data.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.readwritesplitting.api.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.api.rule.ReadwriteSplittingDataSourceRuleConfiguration;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * R27 ShardingSphere 5.4.x 读写分离数据源
 * <p>
 * 拓扑: novamind-trade 集群
 * <pre>
 *   novamind_trade_master  ── 主库 (写)
 *   novamind_trade_slave_0 ── 从库 0 (读)  ROUND_ROBIN
 *   novamind_trade_slave_1 ── 从库 1 (读)  ROUND_ROBIN
 * </pre>
 * </p>
 * <p>
 * 5.4.x 关键变化 (vs 4.x):
 *   - 4.x: 通过 YAML 的 !READWRITE_SPLITTING 自动装配
 *   - 5.4.x: 也支持 YAML, 但编程式通过 ReadwriteSplittingRuleConfiguration 更灵活
 *   - 5.4.x: 读写分离从 ShardingRule 中剥离, 独立成 ReadwriteSplittingRule
 * </p>
 * <p>
 * 强制主库: 见 {@link ShardingHintManager#HINT_FORCE_MASTER}
 * </p>
 *
 * @author R27
 */
@Slf4j
@Configuration
public class DataSourceReadWriteSplitting {

    /** 读写分离逻辑数据源名 (与 sharding-rules.yaml 中 dataSources.novamind_trade_rw 对应) */
    public static final String RW_DS_NAME = "novamind_trade_rw";

    /** 主库数据源名 */
    public static final String MASTER = "novamind_trade_master";
    /** 从库 0 */
    public static final String SLAVE_0 = "novamind_trade_slave_0";
    /** 从库 1 */
    public static final String SLAVE_1 = "novamind_trade_slave_1";

    /** 负载均衡器 SPI 名（ShardingSphere 内置 ROUND_ROBIN），同时作为 loadBalancers 的 key */
    public static final String LOAD_BALANCER = "ROUND_ROBIN";

    /**
     * 5.4.x 编程式构建 ReadwriteSplittingRuleConfiguration
     * <p>
     * 在 DataSourceShardingConfig#shardingDataSource() 加载 sharding-rules.yaml 时,
     * 此规则会被合并进最终的 ShardingSphereDataSource.
     * </p>
     */
    @Bean(name = "readwriteSplittingRule")
    public ReadwriteSplittingRuleConfiguration readwriteSplittingRuleConfiguration() {
        log.info("====== R27 初始化 5.4.x 读写分离规则: master={}, slaves=[{}, {}] ======",
                MASTER, SLAVE_0, SLAVE_1);

        // 1. 配置数据源规则。
        //    5.4.1 里 readwritesplitting api 的 strategy 包已被整体移除
        //    （StaticReadwriteSplittingStrategyConfiguration 不复存在），
        //    主库/从库直接作为构造参数传入，顺序为
        //    (name, writeDataSourceName, readDataSourceNames, transactionalReadQueryStrategy, loadBalancerName)。
        //    不传 TransactionalReadQueryStrategy 保持原先“不带事务感知”的语义。
        ReadwriteSplittingDataSourceRuleConfiguration dsRule =
                new ReadwriteSplittingDataSourceRuleConfiguration(
                        RW_DS_NAME,
                        MASTER,
                        Arrays.asList(SLAVE_0, SLAVE_1),
                        null,               // 不带事务感知
                        LOAD_BALANCER       // 负载均衡器 SPI 名（需与 loadBalancers 的 key 一致）
                );

        // 2. 负载均衡器：5.4.1 改用 AlgorithmConfiguration(type, props)，
        //    不再是 Map<String, Properties>。
        Map<String, AlgorithmConfiguration> loadBalancers = new HashMap<>();
        loadBalancers.put(LOAD_BALANCER, new AlgorithmConfiguration(LOAD_BALANCER, new Properties()));

        // 3. 组装顶层规则
        return new ReadwriteSplittingRuleConfiguration(
                Collections.singletonList(dsRule),
                loadBalancers
        );
    }

    /**
     * 读写分离配置项 (供 application.yml 中的 spring.shardingsphere.readwrite-splitting.* 使用)
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.shardingsphere.readwrite-splitting")
    public ReadwriteSplittingProperties readwriteSplittingProperties() {
        return new ReadwriteSplittingProperties();
    }

    /**
     * 5.4.x 配套配置属性 POJO
     */
    @lombok.Data
    public static class ReadwriteSplittingProperties {
        /** 是否启用 */
        private boolean enabled = true;
        /** 主库 JNDI/数据源名 */
        private String master = MASTER;
        /** 从库列表 (逗号分隔) */
        private String slaves = SLAVE_0 + "," + SLAVE_1;
        /** 负载均衡: ROUND_ROBIN / RANDOM / WEIGHT */
        private String loadBalancer = "ROUND_ROBIN";
        /** 读查询是否允许降级主库 (从库不可用时) */
        private boolean readFromMasterWhenNoSlaves = true;
    }
}

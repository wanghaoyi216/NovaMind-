package com.novamind.common.autoconfigure.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * <h1>RabbitMQ 拓扑与 DLX 全局配置 (P0 改造)</h1>
 *
 * <p>把所有 RabbitMQ 的交换机 / 队列 / 路由键 / DLX 死信交换机 / 死信路由键统一收敛到这里。
 * 业务方不要再硬编码字符串，统一通过 {@code @Resource RabbitMqProperties} 拿到，
 * 在启动时根据本类信息声明队列与绑定关系。</p>
 *
 * <p>DLX 设计约定：</p>
 * <ul>
 *   <li>业务队列统一设置 {@code x-dead-letter-exchange} 指向 {@link #dlxExchange}，
 *       配合 {@code x-dead-letter-routing-key} 指向 {@link #dlxQueueSuffix} + service。</li>
 *   <li>死信队列命名规则：{@code <service>.dlx.queue}，统一由本类配置。</li>
 * </ul>
 *
 * <p>使用样例（application.yml 中覆盖默认值）：</p>
 * <pre>{@code
 * novamind:
 *   rabbitmq:
 *     main-exchange: order.topic
 *     main-queue-suffix: order
 *     dlx-exchange: error.topic
 *     dlx-queue-suffix: dlx
 *     service-name: ${spring.application.name}
 * }</pre>
 *
 * @author P0-refactor
 */
@Data
@ConfigurationProperties(prefix = "novamind.rabbitmq")
public class RabbitMqProperties {

    /** 默认主交换机（如果业务方未单独声明，会用这个） */
    private String mainExchange = "default.topic";

    /** 主队列后缀，配合 service-name 形成完整队列名 */
    private String mainQueueSuffix = "default";

    /**
     * 全局死信交换机（DLX）。
     * 所有业务队列的 {@code x-dead-letter-exchange} 都指向这个。
     */
    private String dlxExchange = "dlx.topic";

    /**
     * 死信路由键前缀 + 服务名拼接成完整路由键，例如 {@code dlx.trade-service}。
     */
    private String dlxRoutingKeyPrefix = "dlx.";

    /**
     * 死信队列名模板，使用 {@code {service}} 占位，最终形如 {@code error.{service}.queue}。
     * 项目已有 ERROR_QUEUE_TEMPLATE 在 {@link com.novamind.common.constants.MqConstants.Queue}，
     * 这里再提供一个 DLX 专用队列模板，方便新增业务。
     */
    private String dlxQueueTemplate = "{service}.dlx.queue";

    /** 当前微服务名，自动由 Environment 注入 */
    private String serviceName = "unknown";

    /**
     * 自定义绑定关系 key-value 映射，key = 业务名, value = 该业务的路由键。
     * 默认空，业务方按需在 yaml 里扩展即可：
     * <pre>{@code
     * novamind:
     *   rabbitmq:
     *     bindings:
     *       order-pay: order.pay
     *       order-refund: order.refund
     * }</pre>
     */
    private Map<String, String> bindings = new HashMap<>();

    /**
     * 是否启用本地 outbox（消息先入库，再投递，nack/return 时定时重投）
     */
    private boolean outboxEnabled = true;

    /**
     * outbox 重试间隔（毫秒），仅定时任务生效；nack 触发的即时重试不受此限
     */
    private long outboxRetryIntervalMs = 30_000L;

    /**
     * outbox 单条消息最大重试次数，超过后转人工
     */
    private int outboxMaxRetries = 5;

    /**
     * 单次扫描时最多处理的消息数（防止定时任务卡住线程）
     */
    private int outboxBatchSize = 200;

    /**
     * 拼出当前服务的死信队列名：{@code error.<service>.queue}。
     */
    public String currentDlxQueueName() {
        return dlxQueueTemplate.replace("{service}", serviceName);
    }

    /**
     * 拼出当前服务的死信路由键：{@code dlx.<service>}。
     */
    public String currentDlxRoutingKey() {
        return dlxRoutingKeyPrefix + serviceName;
    }
}

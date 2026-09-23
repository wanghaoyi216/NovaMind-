-- =====================================================================
-- P0 改造：本地消息表（Outbox 模式）
-- 解决 RabbitMQ 投递与本地事务的一致性，支持重试、指数退避与 DEAD 归档
-- =====================================================================
CREATE TABLE IF NOT EXISTS `mq_outbox` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `exchange`        VARCHAR(128) NOT NULL                COMMENT 'RabbitMQ 交换机',
    `routing_key`     VARCHAR(128) NOT NULL                COMMENT 'RabbitMQ 路由键',
    `payload`         TEXT         NOT NULL                COMMENT '消息体 JSON',
    `status`          VARCHAR(16)  NOT NULL                COMMENT '状态: PENDING/SENT/FAILED/DEAD',
    `retry_count`     INT          NOT NULL DEFAULT 0      COMMENT '已重试次数',
    `next_retry_time` DATETIME     NULL                    COMMENT '下次重试时间（指数退避）',
    `last_error`      VARCHAR(500) NULL                    COMMENT '最近一次错误信息',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_status_next` (`status`, `next_retry_time`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地消息表（Outbox模式）';

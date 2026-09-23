-- ============================================================
-- R8 部署提示（重要）：
-- 本文件位于 docker-entrypoint-initdb.d，仅在 MySQL 数据卷【首次初始化】时执行。
-- 存量环境升级请手动执行本文件内容：
--   docker exec -i novamind-mysql mysql -uroot -p<密码> novamind_pay < 本文件
-- 否则支付回调运行时报 Table 'idempotency_record' doesn't exist。
-- ============================================================
-- =====================================================================
-- P0 改造：novamind-pay idempotency_record 表
-- 用于保存微信 / 支付宝回调的幂等键，避免同一笔支付回调被处理多次
-- =====================================================================
CREATE DATABASE IF NOT EXISTS novamind_pay DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE novamind_pay;

DROP TABLE IF EXISTS `idempotency_record`;
CREATE TABLE `idempotency_record` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `idem_key`     VARCHAR(128) NOT NULL                COMMENT '幂等键（外部传入 / 业务拼接）',
    `biz_type`     VARCHAR(32)  NOT NULL                COMMENT '业务类型: PAY_CALLBACK / REFUND_CALLBACK ...',
    `biz_id`       VARCHAR(64)                          COMMENT '业务单号（订单号 / 退款单号）',
    `status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '0=处理中 1=成功 2=失败',
    `result`       TEXT                                  COMMENT '成功响应或错误堆栈（截断 4k）',
    `response_to_channel` TEXT                          COMMENT '回写给微信/支付宝的原文',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 关键：biz_type + idem_key 唯一，保证同一业务同一 key 只有一条记录
    UNIQUE KEY `uk_biz_key` (`biz_type`, `idem_key`),
    KEY `idx_biz_id` (`biz_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='P0 改造：支付幂等键表，微信/支付宝回调去重';

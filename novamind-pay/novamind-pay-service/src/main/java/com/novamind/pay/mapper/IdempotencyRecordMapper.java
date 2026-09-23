package com.novamind.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novamind.pay.domain.po.IdempotencyRecord;

/**
 * <p>
 * 支付幂等键记录 Mapper（P0 改造）
 * </p>
 *
 * <p>注意：放在 {@code com.novamind.pay.mapper} 包下而非 {@code com.novamind.pay.dao}，
 * 因为 PayApplication 的 {@code @MapperScan("com.novamind.pay.mapper")} 只扫描本包，
 * 且与本模块既有 PayOrderMapper / RefundOrderMapper 保持同一约定。</p>
 *
 * @author P0-refactor
 * @since 2026-08-24
 */
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

}

/**
 * 金额格式化。
 *
 * 后端所有金额字段（课程 price、购物车 price/nowPrice、订单金额等）单位都是
 * **分**（见 `Course.price` 注释「课程价格，单位为分」）。此前前端直接把分值
 * 渲染成 `¥19900`，比真实价格大了 100 倍；这里统一收敛成一处换算。
 */

/** 分 → 元，保留两位小数（不带符号）。 */
export function formatYuan(cents: number | string | null | undefined): string {
  const value = Number(cents)
  if (!Number.isFinite(value)) return '0.00'
  return (value / 100).toFixed(2)
}

/**
 * 分 → `¥xx.xx`。
 * 价格为 0（或非正数）时返回「免费」，除非显式传入 `zeroAsFree: false`。
 */
export function formatPrice(
  cents: number | string | null | undefined,
  options: { zeroAsFree?: boolean } = {}
): string {
  const value = Number(cents)
  const { zeroAsFree = true } = options
  if (!Number.isFinite(value)) return zeroAsFree ? '免费' : '¥0.00'
  if (value <= 0) return zeroAsFree ? '免费' : '¥0.00'
  return `¥${formatYuan(value)}`
}

/** 分 → `¥xx.xx`，始终带符号（用于小计 / 合计等金额，0 也要显示成 ¥0.00）。 */
export function formatMoney(cents: number | string | null | undefined): string {
  return `¥${formatYuan(cents)}`
}

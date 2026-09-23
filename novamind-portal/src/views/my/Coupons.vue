<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { useRouter } from 'vue-router'

/** 与 MyLayout.vue 中 provide 的上下文保持一致 */
interface MyPageAction {
  key: string
  label: string
  variant?: 'primary' | 'accent' | 'ghost'
  disabled?: boolean
  loading?: boolean
  onClick?: () => void
}

interface MyPageContext {
  title: string
  description: string
  actions: MyPageAction[]
  refreshStats: () => void
}

const MY_PAGE_CONTEXT = 'nm-my-page-context'

type CouponState = 'available' | 'used' | 'expired'
type CouponKind = 'cash' | 'discount'

interface Coupon {
  id: number
  kind: CouponKind
  kindLabel: string
  name: string
  scope: string
  /** 满减券为金额，折扣券为折数 */
  value: number
  /** 0 表示无门槛 */
  threshold: number
  code: string
  startAt: string
  endAt: string
  state: CouponState
}

const router = useRouter()
const page = inject<MyPageContext | null>(MY_PAGE_CONTEXT, null)

const filter = ref<CouponState>('available')

/**
 * 优惠券服务尚未接入后端（@/api/trade 无对应端点），
 * 这里用本地演示数据驱动页面结构，空态另有完整设计。
 */
const coupons = ref<Coupon[]>([
  {
    id: 1,
    kind: 'cash',
    kindLabel: '满减券',
    name: '新人专享 · 全站通用',
    scope: '适用于全站课程，特价课除外',
    value: 50,
    threshold: 199,
    code: 'NM-9F3A21',
    startAt: '2026-09-01',
    endAt: '2026-12-31',
    state: 'available',
  },
  {
    id: 2,
    kind: 'discount',
    kindLabel: '折扣券',
    name: '会员日 · 全场 8.5 折',
    scope: '单笔订单限用一张',
    value: 8.5,
    threshold: 100,
    code: 'NM-7C1B08',
    startAt: '2026-09-10',
    endAt: '2026-10-10',
    state: 'available',
  },
  {
    id: 3,
    kind: 'cash',
    kindLabel: '无门槛券',
    name: '学习激励金',
    scope: '任意课程可直接抵扣',
    value: 20,
    threshold: 0,
    code: 'NM-2E5D44',
    startAt: '2026-09-01',
    endAt: '2026-11-30',
    state: 'available',
  },
  {
    id: 4,
    kind: 'cash',
    kindLabel: '满减券',
    name: '春季焕新课 · 前端专场',
    scope: '适用于前端方向课程',
    value: 30,
    threshold: 149,
    code: 'NM-4A0F93',
    startAt: '2026-03-01',
    endAt: '2026-04-30',
    state: 'used',
  },
])

const tabs: { key: CouponState; label: string }[] = [
  { key: 'available', label: '可用' },
  { key: 'used', label: '已使用' },
  { key: 'expired', label: '已过期' },
]

const counts = computed(() => ({
  available: coupons.value.filter((item) => item.state === 'available').length,
  used: coupons.value.filter((item) => item.state === 'used').length,
  expired: coupons.value.filter((item) => item.state === 'expired').length,
}))

const visibleCoupons = computed(() => coupons.value.filter((item) => item.state === filter.value))

const emptyCopy: Record<CouponState, { title: string; desc: string }> = {
  available: {
    title: '暂无可用优惠券',
    desc: '领券中心会不定期放出满减券与折扣券，领到的券会立即出现在这里。',
  },
  used: {
    title: '还没有使用过优惠券',
    desc: '下单时选择优惠券抵扣，使用记录会保留在这里，方便你回顾。',
  },
  expired: {
    title: '没有已过期的优惠券',
    desc: '状态不错，你领到的券都用上了。',
  },
}

function valueText(coupon: Coupon) {
  return `${coupon.value}`
}

function condText(coupon: Coupon) {
  return coupon.threshold > 0 ? `满 ${coupon.threshold} 可用` : '无门槛'
}

function discountLine(coupon: Coupon) {
  return coupon.kind === 'discount' ? `按 ${coupon.value} 折结算` : `立减 ¥${coupon.value}`
}

function stateLabel(state: CouponState) {
  if (state === 'used') return '已使用'
  if (state === 'expired') return '已过期'
  return '可用'
}

function stateBadge(state: CouponState) {
  if (state === 'used') return 'nm-badge--neutral'
  if (state === 'expired') return 'nm-badge--neutral'
  return 'nm-badge--accent'
}

function useCoupon(coupon: Coupon) {
  if (coupon.state !== 'available') return
  router.push('/portal/courses')
}

const rulesVisible = ref(false)
const rulesCoupon = ref<Coupon | null>(null)

function showRules(coupon: Coupon) {
  rulesCoupon.value = coupon
  rulesVisible.value = true
}

if (page) {
  page.title = '我的优惠券'
  page.description = '可用、已使用与已过期的券都收纳在这里，下单时自动匹配最优优惠。'
  page.actions = [
    {
      key: 'center',
      label: '去领券中心',
      variant: 'primary',
      onClick: () => {
        router.push('/portal/courses')
      },
    },
  ]
}
</script>

<template>
  <div class="cp">
    <div class="cp-bar">
      <div class="cp-tabs" role="tablist" aria-label="优惠券状态筛选">
        <button
          v-for="item in tabs"
          :key="item.key"
          type="button"
          role="tab"
          :aria-selected="filter === item.key"
          :class="['nm-chip', { 'is-active': filter === item.key }]"
          @click="filter = item.key"
        >
          {{ item.label }}
          <span class="nm-num cp-tabs__count">{{ counts[item.key] }}</span>
        </button>
      </div>
      <p class="cp-bar__note nm-muted">演示数据 · 优惠券接口尚未接入</p>
    </div>

    <!-- 券票卡 -->
    <div v-if="visibleCoupons.length" class="cp-grid">
      <article
        v-for="coupon in visibleCoupons"
        :key="coupon.id"
        :class="['cp-ticket', `is-${coupon.state}`]"
      >
        <div class="cp-ticket__left">
          <p class="cp-ticket__value nm-num">
            <span v-if="coupon.kind === 'cash'" class="cp-ticket__unit">¥</span>{{ valueText(coupon)
            }}<span v-if="coupon.kind === 'discount'" class="cp-ticket__unit">折</span>
          </p>
          <p class="cp-ticket__cond nm-num">{{ condText(coupon) }}</p>
        </div>

        <div class="cp-ticket__perf" aria-hidden="true">
          <span class="cp-ticket__notch cp-ticket__notch--top" />
          <span class="cp-ticket__notch cp-ticket__notch--bottom" />
        </div>

        <div class="cp-ticket__right">
          <div class="cp-ticket__head">
            <span class="nm-tag">{{ coupon.kindLabel }}</span>
            <span class="nm-badge" :class="stateBadge(coupon.state)">{{ stateLabel(coupon.state) }}</span>
          </div>

          <h3 class="cp-ticket__name">{{ coupon.name }}</h3>
          <p class="cp-ticket__scope nm-muted">{{ coupon.scope }}</p>

          <dl class="cp-ticket__meta">
            <div class="cp-ticket__meta-row">
              <dt>有效期</dt>
              <dd class="nm-num">{{ coupon.startAt }} ~ {{ coupon.endAt }}</dd>
            </div>
            <div class="cp-ticket__meta-row">
              <dt>券码</dt>
              <dd class="nm-num">{{ coupon.code }}</dd>
            </div>
          </dl>

          <div class="cp-ticket__actions">
            <button
              v-if="coupon.state === 'available'"
              type="button"
              class="nm-btn nm-btn--sm nm-btn--accent"
              @click="useCoupon(coupon)"
            >
              立即使用
            </button>
            <button
              v-else
              type="button"
              class="nm-btn nm-btn--sm nm-btn--ghost"
              disabled
            >
              {{ stateLabel(coupon.state) }}
            </button>
            <button type="button" class="nm-btn nm-btn--sm nm-btn--ghost" @click="showRules(coupon)">
              使用规则
            </button>
          </div>
        </div>
      </article>
    </div>

    <!-- 空态 -->
    <div v-else class="nm-state">
      <span class="nm-state__icon" aria-hidden="true">
        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path
            d="M3.8 8.2V6.6A1.3 1.3 0 0 1 5.1 5.3h13.8a1.3 1.3 0 0 1 1.3 1.3v1.6a2.6 2.6 0 0 0 0 5.2V15a1.3 1.3 0 0 1-1.3 1.3H5.1A1.3 1.3 0 0 1 3.8 15v-1.6a2.6 2.6 0 0 0 0-5.2Z"
            stroke-linejoin="round"
          />
          <path d="M14.2 6v11.6" stroke-dasharray="2 2.4" stroke-opacity="0.8" />
        </svg>
      </span>
      <p class="nm-state__title">{{ emptyCopy[filter].title }}</p>
      <p class="nm-state__desc">{{ emptyCopy[filter].desc }}</p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
          去领券中心
        </button>
        <button
          v-if="filter !== 'available'"
          type="button"
          class="nm-btn nm-btn--ghost"
          @click="filter = 'available'"
        >
          查看可用券
        </button>
      </div>
    </div>

    <!-- 使用规则 -->
    <el-dialog v-model="rulesVisible" title="优惠券使用规则" width="440px" align-center>
      <dl v-if="rulesCoupon" class="cp-rules">
        <div class="cp-rules__row">
          <dt>券名称</dt>
          <dd>{{ rulesCoupon.name }}</dd>
        </div>
        <div class="cp-rules__row">
          <dt>优惠内容</dt>
          <dd>
            {{ condText(rulesCoupon) }}，{{ discountLine(rulesCoupon) }}
          </dd>
        </div>
        <div class="cp-rules__row">
          <dt>适用范围</dt>
          <dd>{{ rulesCoupon.scope }}</dd>
        </div>
        <div class="cp-rules__row">
          <dt>有效期</dt>
          <dd class="nm-num">{{ rulesCoupon.startAt }} ~ {{ rulesCoupon.endAt }}</dd>
        </div>
        <div class="cp-rules__row">
          <dt>券码</dt>
          <dd class="nm-num">{{ rulesCoupon.code }}</dd>
        </div>
      </dl>
      <p class="cp-rules__note nm-muted">每笔订单限用一张，不与其他优惠叠加；退款时优惠券按原规则退回。</p>
      <template #footer>
        <button type="button" class="nm-btn nm-btn--primary" @click="rulesVisible = false">
          知道了
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.cp {
  display: grid;
  gap: clamp(18px, 2.2vw, 26px);
}

/* 禁用态（设计系统只定义常态，这里按页面需要补齐） */
.nm-btn:disabled,
.nm-btn:disabled:hover {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ---------------- 过滤条 ---------------- */
.cp-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.cp-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cp-tabs__count {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.nm-chip.is-active .cp-tabs__count {
  color: var(--nm-paper);
}

[data-theme='dark'] .nm-chip.is-active .cp-tabs__count {
  color: var(--nm-accent-ink);
}

.cp-bar__note {
  font-size: var(--nm-fs-xs);
}

/* ---------------- 券票卡 ---------------- */
.cp-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(auto-fill, minmax(430px, 1fr));
}

.cp-ticket {
  position: relative;
  display: grid;
  grid-template-columns: 152px 1px minmax(0, 1fr);
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
  transition: transform var(--nm-dur) var(--nm-ease), box-shadow var(--nm-dur) var(--nm-ease),
    border-color var(--nm-dur) var(--nm-ease);
}

.cp-ticket.is-available:hover {
  transform: translateY(-3px);
  border-color: var(--nm-accent-line);
  box-shadow: var(--nm-sh-3);
}

.cp-ticket.is-used,
.cp-ticket.is-expired {
  background: var(--nm-surface-2);
}

/* 左侧面额区 */
.cp-ticket__left {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 6px;
  padding: 22px 14px;
  border-radius: calc(var(--nm-r-lg) - 1px) 0 0 calc(var(--nm-r-lg) - 1px);
  background: var(--nm-accent-soft);
  text-align: center;
}

.cp-ticket.is-used .cp-ticket__left,
.cp-ticket.is-expired .cp-ticket__left {
  background: var(--nm-surface-3);
}

.cp-ticket__value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: 2.5rem;
  font-weight: 750;
  line-height: 1;
  letter-spacing: -0.045em;
  color: var(--nm-accent);
}

.cp-ticket.is-used .cp-ticket__value,
.cp-ticket.is-expired .cp-ticket__value {
  color: var(--nm-ink-4);
}

.cp-ticket__unit {
  font-size: 1.0625rem;
  font-weight: 700;
  letter-spacing: 0;
}

.cp-ticket__cond {
  font-size: var(--nm-fs-xs);
  color: var(--nm-accent);
  opacity: 0.86;
}

.cp-ticket.is-used .cp-ticket__cond,
.cp-ticket.is-expired .cp-ticket__cond {
  color: var(--nm-ink-3);
  opacity: 1;
}

/* 骑缝线 + 半圆缺口 */
.cp-ticket__perf {
  position: relative;
  width: 1px;
  background-image: repeating-linear-gradient(
    to bottom,
    var(--nm-line-strong) 0 5px,
    transparent 5px 11px
  );
}

.cp-ticket__notch {
  position: absolute;
  left: calc(50% - 8px);
  width: 16px;
  height: 16px;
  border-radius: var(--nm-r-full);
  background: var(--nm-paper);
}

.cp-ticket__notch--top {
  top: -9px;
}

.cp-ticket__notch--bottom {
  bottom: -9px;
}

/* 右侧信息区 */
.cp-ticket__right {
  display: grid;
  align-content: center;
  gap: 7px;
  padding: 20px 22px;
  border-radius: 0 calc(var(--nm-r-lg) - 1px) calc(var(--nm-r-lg) - 1px) 0;
}

.cp-ticket__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cp-ticket__name {
  font-size: 1.0625rem;
  font-weight: 700;
  letter-spacing: -0.018em;
  color: var(--nm-ink);
}

.cp-ticket.is-used .cp-ticket__name,
.cp-ticket.is-expired .cp-ticket__name {
  color: var(--nm-ink-2);
}

.cp-ticket__scope {
  font-size: var(--nm-fs-xs);
}

.cp-ticket__meta {
  display: grid;
  gap: 3px;
  margin-top: 2px;
}

.cp-ticket__meta-row {
  display: flex;
  gap: 10px;
  font-size: var(--nm-fs-xs);
}

.cp-ticket__meta-row dt {
  color: var(--nm-ink-3);
  flex-shrink: 0;
}

.cp-ticket__meta-row dd {
  color: var(--nm-ink-2);
}

.cp-ticket__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

/* ---------------- 使用规则弹窗 ---------------- */
.cp-rules {
  display: grid;
  gap: 10px;
}

.cp-rules__row {
  display: flex;
  gap: 14px;
  font-size: var(--nm-fs-sm);
}

.cp-rules__row dt {
  flex-shrink: 0;
  width: 68px;
  color: var(--nm-ink-3);
}

.cp-rules__row dd {
  color: var(--nm-ink);
  font-weight: 550;
}

.cp-rules__note {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
  font-size: var(--nm-fs-xs);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 760px) {
  .cp-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .cp-ticket {
    grid-template-columns: 116px 1px minmax(0, 1fr);
  }

  .cp-ticket__value {
    font-size: 1.9rem;
  }

  .cp-ticket__left {
    padding: 18px 8px;
  }

  .cp-ticket__right {
    padding: 16px;
  }
}

@media (max-width: 480px) {
  .cp-ticket__actions .nm-btn {
    flex: 1;
  }

  .cp-bar__note {
    display: none;
  }
}
</style>

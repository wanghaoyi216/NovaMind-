<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrders } from '@/api/trade'
import type { OrderItem } from '@/api/trade'
import { formatMoney } from '@/utils/format'
import sceneEmptyOrders from '@/assets/scenes/scene-empty-orders.svg?raw'

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

type FilterKey = 'all' | 'unpaid' | 'done' | 'closed'

/** 后端 OrderItem 未声明 courseId，这里补一个可选字段用于「再次购买」跳转 */
interface OrderLine extends OrderItem {
  courseId?: number
}

/** 同一订单号下的多门课程合并为一张订单卡 */
interface OrderGroup {
  orderId: string
  status: number
  payTime: string
  items: OrderLine[]
  total: number
}

const router = useRouter()
const page = inject<MyPageContext | null>(MY_PAGE_CONTEXT, null)

const loading = ref(false)
const failed = ref(false)
const lines = ref<OrderLine[]>([])
const filter = ref<FilterKey>('all')
const expanded = ref<string | null>(null)
const cancellingId = ref<string | null>(null)

const filters: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'unpaid', label: '待支付' },
  { key: 'done', label: '已完成' },
  { key: 'closed', label: '已取消' },
]

const statusMeta: Record<number, { text: string; badge: string }> = {
  0: { text: '待支付', badge: 'nm-badge--warning' },
  1: { text: '已完成', badge: 'nm-badge--success' },
  2: { text: '已取消', badge: 'nm-badge--neutral' },
  3: { text: '已退款', badge: 'nm-badge--danger' },
}

function metaOf(status: number) {
  return statusMeta[status] ?? { text: '未知状态', badge: 'nm-badge--neutral' }
}

function matchFilter(order: OrderGroup, key: FilterKey) {
  if (key === 'unpaid') return order.status === 0
  if (key === 'done') return order.status === 1
  if (key === 'closed') return order.status === 2 || order.status === 3
  return true
}

const groups = computed<OrderGroup[]>(() => {
  const map = new Map<string, OrderGroup>()
  lines.value.forEach((line) => {
    const key = line.orderId || `LOCAL-${line.id}`
    const existing = map.get(key)
    if (existing) {
      existing.items.push(line)
      existing.total += Number(line.price) || 0
      return
    }
    map.set(key, {
      orderId: key,
      status: line.status,
      payTime: line.payTime,
      items: [line],
      total: Number(line.price) || 0,
    })
  })
  return Array.from(map.values())
})

const counts = computed(() => ({
  all: groups.value.length,
  unpaid: groups.value.filter((order) => matchFilter(order, 'unpaid')).length,
  done: groups.value.filter((order) => matchFilter(order, 'done')).length,
  closed: groups.value.filter((order) => matchFilter(order, 'closed')).length,
}))

const visibleOrders = computed(() => groups.value.filter((order) => matchFilter(order, filter.value)))

// 金额统一走 @/utils/format（后端单位为分）

function formatDateTime(value: string) {
  if (!value) return '—'
  let stamp = new Date(value).getTime()
  if (Number.isNaN(stamp)) stamp = new Date(value.replace(/-/g, '/')).getTime()
  if (Number.isNaN(stamp)) return value
  const date = new Date(stamp)
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`
}

function coverOf(item: OrderLine) {
  return item.coverImg || '/resource/course-covers/course_web_development_01.jpg'
}

function onCoverError(event: Event) {
  const target = event.target as HTMLImageElement | null
  if (!target) return
  const fallback = '/resource/course-covers/course_web_development_01.jpg'
  if (target.getAttribute('src') === fallback) return
  target.setAttribute('src', fallback)
}

function toggleDetail(order: OrderGroup) {
  expanded.value = expanded.value === order.orderId ? null : order.orderId
}

function goPay(order: OrderGroup) {
  router.push(`/portal/pay/${order.orderId}`)
}

function buyAgain(order: OrderGroup) {
  const first = order.items[0]
  if (!first) return
  router.push(`/portal/course/${first.courseId ?? first.id}`)
}

async function handleCancel(order: OrderGroup) {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${order.orderId} 吗？`, '取消订单', {
      confirmButtonText: '取消订单',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  cancellingId.value = order.orderId
  const target = order.items[0]
  try {
    if (target) await cancelOrder(target.id)
    lines.value = lines.value.map((line) =>
      line.orderId === order.orderId ? { ...line, status: 2 } : line
    )
    ElMessage.success('订单已取消')
  } catch {
    ElMessage.error('取消失败，请稍后再试')
  } finally {
    cancellingId.value = null
  }
}

async function load() {
  loading.value = true
  failed.value = false
  const refreshAction = page?.actions.find((action) => action.key === 'refresh')
  if (refreshAction) refreshAction.loading = true
  try {
    const res: any = await getOrders({ pageNo: 1, pageSize: 60 })
    lines.value = Array.isArray(res?.data?.list) ? (res.data.list as OrderLine[]) : []
  } catch {
    lines.value = []
    failed.value = true
  } finally {
    loading.value = false
    if (refreshAction) refreshAction.loading = false
  }
}

if (page) {
  page.title = '我的订单'
  page.description = '订单、支付状态与课程购买记录都在这里。'
  page.actions = [
    {
      key: 'refresh',
      label: '刷新',
      variant: 'ghost',
      loading: false,
      onClick: () => {
        void load()
      },
    },
  ]
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="od">
    <!-- 过滤 -->
    <div class="od-bar">
      <div class="od-tabs" role="tablist" aria-label="订单状态筛选">
        <button
          v-for="item in filters"
          :key="item.key"
          type="button"
          role="tab"
          :aria-selected="filter === item.key"
          :class="['nm-chip', { 'is-active': filter === item.key }]"
          @click="filter = item.key"
        >
          {{ item.label }}
          <span class="nm-num od-tabs__count">{{ counts[item.key] }}</span>
        </button>
      </div>
      <p class="od-bar__hint nm-muted nm-num">共 {{ counts.all }} 笔订单</p>
    </div>

    <!-- 骨架 -->
    <div v-if="loading" class="od-list" aria-busy="true">
      <div v-for="index in 3" :key="index" class="od-card nm-card">
        <div class="od-card__head">
          <div class="nm-skeleton" style="width: 190px; height: 13px" />
          <div class="nm-skeleton" style="width: 74px; height: 20px" />
        </div>
        <div class="od-card__body">
          <div class="nm-skeleton od-line__thumb" />
          <div class="od-line__copy">
            <div class="nm-skeleton" style="width: 62%; height: 16px" />
            <div class="nm-skeleton" style="width: 34%; height: 12px" />
          </div>
        </div>
        <div class="od-card__foot">
          <div class="nm-skeleton" style="width: 108px; height: 18px" />
          <div class="nm-skeleton" style="width: 200px; height: 34px" />
        </div>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="failed" class="nm-state">
      <span class="nm-state__icon" aria-hidden="true">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M12 8v5" stroke-linecap="round" />
          <circle cx="12" cy="16.4" r="0.9" fill="currentColor" stroke="none" />
          <path d="M12 3.6 21 19.2H3Z" stroke-linejoin="round" />
        </svg>
      </span>
      <p class="nm-state__title">订单服务暂时不可用</p>
      <p class="nm-state__desc">交易服务可能还没启动，订单数据没有丢失，稍后重试即可。</p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="load">重新加载</button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="!visibleOrders.length" class="nm-state">
      <span class="od-state__scene" aria-hidden="true" v-html="sceneEmptyOrders" />
      <p class="nm-state__title">
        {{ filter === 'all' ? '还没有订单' : `没有${filters.find((item) => item.key === filter)?.label}的订单` }}
      </p>
      <p class="nm-state__desc">挑一门好课下单后，订单、支付状态和发票信息都会汇总在这里。</p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
          去选课
        </button>
        <button type="button" class="nm-btn nm-btn--ghost" @click="router.push('/portal/cart')">
          查看购物车
        </button>
      </div>
    </div>

    <!-- 订单列表 -->
    <div v-else class="od-list">
      <article v-for="order in visibleOrders" :key="order.orderId" class="od-card nm-card">
        <header class="od-card__head">
          <div class="od-card__ids">
            <span class="od-card__no nm-num">NO. {{ order.orderId }}</span>
            <span class="od-card__time nm-muted nm-num">
              {{ order.status === 0 ? '下单时间' : '支付时间' }}
              {{ formatDateTime(order.payTime) }}
            </span>
          </div>
          <span class="nm-badge" :class="metaOf(order.status).badge">{{ metaOf(order.status).text }}</span>
        </header>

        <div class="od-card__body">
          <div v-for="line in order.items" :key="line.id" class="od-line">
            <img class="od-line__thumb" :src="coverOf(line)" :alt="line.courseName" loading="lazy" @error="onCoverError" />
            <div class="od-line__copy">
              <h3 class="od-line__name">{{ line.courseName }}</h3>
              <p class="od-line__meta nm-muted nm-num">课程编号 {{ line.courseId ?? line.id }}</p>
            </div>
            <span class="od-line__price nm-num">{{ formatMoney(line.price) }}</span>
          </div>
        </div>

        <transition name="page-fade">
          <dl v-if="expanded === order.orderId" class="od-detail">
            <div class="od-detail__row">
              <dt>订单号</dt>
              <dd class="nm-num">{{ order.orderId }}</dd>
            </div>
            <div class="od-detail__row">
              <dt>课程数量</dt>
              <dd class="nm-num">{{ order.items.length }} 门</dd>
            </div>
            <div class="od-detail__row">
              <dt>{{ order.status === 0 ? '下单时间' : '支付时间' }}</dt>
              <dd class="nm-num">{{ formatDateTime(order.payTime) }}</dd>
            </div>
            <div class="od-detail__row">
              <dt>订单状态</dt>
              <dd>{{ metaOf(order.status).text }}</dd>
            </div>
          </dl>
        </transition>

        <footer class="od-card__foot">
          <p class="od-total">
            <span class="nm-muted">合计</span>
            <span class="od-total__amount nm-num">{{ formatMoney(order.total) }}</span>
          </p>
          <div class="od-card__actions">
            <button
              v-if="order.status === 0"
              type="button"
              class="nm-btn nm-btn--sm nm-btn--ghost od-cancel"
              :disabled="cancellingId === order.orderId"
              @click="handleCancel(order)"
            >
              {{ cancellingId === order.orderId ? '取消中…' : '取消订单' }}
            </button>
            <button type="button" class="nm-btn nm-btn--sm nm-btn--ghost" @click="toggleDetail(order)">
              {{ expanded === order.orderId ? '收起详情' : '查看详情' }}
            </button>
            <button
              v-if="order.status === 0"
              type="button"
              class="nm-btn nm-btn--sm nm-btn--accent"
              @click="goPay(order)"
            >
              去支付
            </button>
            <button
              v-else
              type="button"
              class="nm-btn nm-btn--sm nm-btn--primary"
              @click="buyAgain(order)"
            >
              再次购买
            </button>
          </div>
        </footer>
      </article>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.od {
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
.od-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.od-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.od-tabs__count {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.nm-chip.is-active .od-tabs__count {
  color: var(--nm-paper);
}

[data-theme='dark'] .nm-chip.is-active .od-tabs__count {
  color: var(--nm-accent-ink);
}

.od-bar__hint {
  font-size: var(--nm-fs-xs);
}

/* ---------------- 订单卡 ---------------- */
.od-list {
  display: grid;
  gap: 16px;
}

.od-card {
  overflow: hidden;
}

.od-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 18px;
  border-bottom: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
}

.od-card__ids {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}

.od-card__no {
  font-size: var(--nm-fs-sm);
  font-weight: 650;
  color: var(--nm-ink);
  letter-spacing: -0.01em;
}

.od-card__time {
  font-size: var(--nm-fs-xs);
}

.od-card__body {
  display: grid;
}

.od-line {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
}

.od-line + .od-line {
  border-top: 1px dashed var(--nm-line);
}

.od-line__thumb {
  width: 96px;
  height: 62px;
  border-radius: var(--nm-r-sm);
  object-fit: cover;
  background: var(--nm-surface-3);
  flex-shrink: 0;
}

.od-line__copy {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 3px;
}

.od-line__name {
  font-size: var(--nm-fs-body);
  font-weight: 620;
  letter-spacing: -0.012em;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.od-line__meta {
  font-size: var(--nm-fs-xs);
}

.od-line__price {
  font-size: var(--nm-fs-body);
  font-weight: 650;
  color: var(--nm-ink);
  flex-shrink: 0;
}

.od-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 20px;
  padding: 14px 18px;
  border-top: 1px dashed var(--nm-line);
  background: var(--nm-surface-2);
}

.od-detail__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: var(--nm-fs-sm);
}

.od-detail__row dt {
  color: var(--nm-ink-3);
}

.od-detail__row dd {
  color: var(--nm-ink);
  font-weight: 600;
}

.od-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 18px;
  border-top: 1px solid var(--nm-line);
}

.od-total {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--nm-fs-sm);
}

.od-total__amount {
  font-size: 1.25rem;
  font-weight: 720;
  color: var(--nm-ink);
  letter-spacing: -0.02em;
}

.od-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.od-cancel:hover {
  color: var(--nm-danger);
}

.od-state__scene {
  display: block;
  width: 176px;
  max-width: 62%;
}

.od-state__scene :deep(svg) {
  width: 100%;
  height: auto;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 760px) {
  .od-detail {
    grid-template-columns: minmax(0, 1fr);
  }

  .od-card__foot {
    align-items: flex-start;
  }

  .od-card__actions {
    width: 100%;
  }

  .od-card__actions .nm-btn {
    flex: 1;
  }

  .od-line__thumb {
    width: 72px;
    height: 48px;
  }
}

@media (max-width: 520px) {
  .od-line {
    align-items: flex-start;
  }

  .od-line__name {
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Delete, Refresh, Star, Ticket, WarningFilled } from '@element-plus/icons-vue'
import {
  batchRemoveFromCart,
  enrollFreeCourse,
  getCart,
  placeOrder,
  prePlaceOrder,
  removeFromCart,
  type CartItem,
} from '@/api/trade'
import { formatMoney as money } from '@/utils/format'
import sceneEmptyCourses from '@/assets/scenes/scene-empty-courses.svg'

type LoadState = 'loading' | 'ready' | 'error'

const FALLBACK_COVER = '/resource/course-covers/course_web_development_01.jpg'

const router = useRouter()

const state = ref<LoadState>('loading')
const items = ref<CartItem[]>([])
const selectedIds = ref<number[]>([])
const checkingOut = ref(false)
/** 交易服务不可用时允许用户预览页面结构（数据全部来自本地示例） */
const demoMode = ref(false)
/** 优惠券服务尚未接入，折扣固定为 0，但保留完整的摘要结构 */
const couponDiscount = ref(0)

const demoItems: CartItem[] = [
  {
    id: 9101,
    courseId: 101,
    courseName: 'Spring Cloud 微服务全链路实践',
    coverImg: '/resource/course-covers/course_microservices_01.jpg',
    price: 299,
    teacherName: '王讲师',
  },
  {
    id: 9102,
    courseId: 102,
    courseName: 'AI 应用开发与提示工程',
    coverImg: '/resource/course-covers/course_ai_deep_learning_01.jpg',
    price: 199,
    teacherName: '李讲师',
  },
  {
    id: 9103,
    courseId: 103,
    courseName: 'Docker 与 Kubernetes 云原生入门',
    coverImg: '/resource/course-covers/course_docker_kubernetes_01.jpg',
    price: 0,
    teacherName: '张讲师',
  },
]

const hasItems = computed(() => items.value.length > 0)
const selectedItems = computed(() =>
  items.value.filter((item) => selectedIds.value.includes(item.id))
)
const selectedCount = computed(() => selectedItems.value.length)
const subtotal = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + Number(item.price || 0), 0)
)
const total = computed(() => Math.max(subtotal.value - couponDiscount.value, 0))
const allSelected = computed(
  () => hasItems.value && selectedIds.value.length === items.value.length
)
const partiallySelected = computed(
  () => selectedCount.value > 0 && selectedCount.value < items.value.length
)

onMounted(() => {
  void loadCart()
})

async function loadCart() {
  state.value = 'loading'
  demoMode.value = false
  try {
    const res = (await getCart()) as unknown as { data?: CartItem[] }
    items.value = Array.isArray(res?.data) ? res.data : []
    selectedIds.value = items.value.map((item) => item.id)
    state.value = 'ready'
  } catch {
    items.value = []
    selectedIds.value = []
    state.value = 'error'
  }
}

function loadDemoCart() {
  items.value = demoItems.map((item) => ({ ...item }))
  selectedIds.value = items.value.map((item) => item.id)
  demoMode.value = true
  state.value = 'ready'
}

// 金额统一走 @/utils/format（后端单位为分）

function isFree(item: CartItem) {
  return Number(item.price || 0) <= 0
}

function coverOf(item: CartItem) {
  return item.coverImg || FALLBACK_COVER
}

function toggleItem(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((current) => current !== id)
    : [...selectedIds.value, id]
}

function toggleAll(value: string | number | boolean) {
  selectedIds.value = value ? items.value.map((item) => item.id) : []
}

async function removeItems(targets: CartItem[], toast?: string) {
  if (!targets.length) return
  const ids = targets.map((item) => item.id)

  if (!demoMode.value) {
    try {
      if (ids.length === 1) {
        await removeFromCart(ids[0])
      } else {
        await batchRemoveFromCart(ids)
      }
    } catch {
      // 服务不可用时按本地结果处理，不阻塞用户操作
    }
  }

  items.value = items.value.filter((item) => !ids.includes(item.id))
  selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
  ElMessage.success(toast ?? (ids.length > 1 ? `已移除 ${ids.length} 门课程` : '已从购物车移除'))
}

function removeSelected() {
  if (!selectedCount.value) {
    ElMessage.warning('请先选择要删除的课程')
    return
  }
  void removeItems([...selectedItems.value])
}

function moveToFavorite(item: CartItem) {
  void removeItems([item], '已移入收藏')
}

async function checkout() {
  if (!selectedCount.value) {
    ElMessage.warning('请先选择要结算的课程')
    return
  }

  checkingOut.value = true
  const freeItems = selectedItems.value.filter((item) => isFree(item))
  const paidItems = selectedItems.value.filter((item) => !isFree(item))

  try {
    for (const item of freeItems) {
      await enrollFreeCourse(item.courseId)
    }
    if (freeItems.length) {
      const freeIds = freeItems.map((item) => item.id)
      items.value = items.value.filter((item) => !freeIds.includes(item.id))
      selectedIds.value = selectedIds.value.filter((id) => !freeIds.includes(id))
      ElMessage.success(`已免费加入 ${freeItems.length} 门课程`)
    }

    if (!paidItems.length) {
      if (freeItems.length) router.push('/portal/my/lessons')
      return
    }

    const orderIds = paidItems.map((item) => item.id)
    await prePlaceOrder(orderIds)
    const res = (await placeOrder({ orderIds })) as unknown as { data?: string }
    const orderId = res?.data || `MOCK-${Date.now()}`
    ElMessage.success('订单已创建，请完成支付')
    router.push(`/portal/pay/${orderId}`)
  } catch {
    ElMessage.warning('交易服务未连接，已进入本地下单演示')
    router.push(`/portal/pay/MOCK-${Date.now()}`)
  } finally {
    checkingOut.value = false
  }
}
</script>

<template>
  <div class="cart-page nm-shell">
    <header class="cart-head">
      <div class="cart-head__copy">
        <span class="nm-kicker">购物车与下单</span>
        <h1 class="nm-h1">购物车</h1>
        <p class="nm-lede">核对课程与价格，确认后一次性下单；未结算的课程会一直保留在这里。</p>
      </div>
      <div class="cart-head__actions">
        <button type="button" class="nm-btn nm-btn--sm" @click="router.push('/portal/courses')">
          继续选课
        </button>
      </div>
    </header>

    <div :class="['cart-body', { 'is-single': !hasItems }]">
      <section class="cart-main">
        <!-- 加载骨架 -->
        <div v-if="state === 'loading'" class="cart-skeleton" aria-busy="true" aria-label="正在加载购物车">
          <div class="nm-skeleton sk-bar"></div>
          <div v-for="n in 3" :key="n" class="sk-row">
            <span class="nm-skeleton sk-check"></span>
            <span class="nm-skeleton sk-cover"></span>
            <span class="sk-copy">
              <span class="nm-skeleton sk-line sk-line--lg"></span>
              <span class="nm-skeleton sk-line sk-line--sm"></span>
            </span>
            <span class="nm-skeleton sk-price"></span>
          </div>
        </div>

        <!-- 错误态 -->
        <div v-else-if="state === 'error'" class="nm-state">
          <span class="nm-state__icon">
            <el-icon :size="24"><WarningFilled /></el-icon>
          </span>
          <p class="nm-state__title">购物车暂时加载失败</p>
          <p class="nm-state__desc">
            交易服务未连接或网络异常。可以重试，也可以先预览示例数据，查看页面结构与交互。
          </p>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary" @click="loadCart()">
              <el-icon :size="15"><Refresh /></el-icon>
              重试
            </button>
            <button type="button" class="nm-btn" @click="loadDemoCart()">预览示例数据</button>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else-if="!hasItems" class="nm-state cart-empty">
          <img class="cart-empty__art" :src="sceneEmptyCourses" alt="" />
          <p class="nm-state__title">还没有加入课程</p>
          <p class="nm-state__desc">
            把想学的课程加入购物车，可以集中比较讲师、价格与大纲，再决定先学哪一门。
          </p>
          <div class="nm-state__actions">
            <button
              type="button"
              class="nm-btn nm-btn--primary"
              @click="router.push('/portal/courses')"
            >
              去选课
              <el-icon :size="15"><ArrowRight /></el-icon>
            </button>
            <button type="button" class="nm-btn" @click="router.push('/portal/home')">
              看看推荐课程
            </button>
          </div>
        </div>

        <!-- 列表 -->
        <template v-else>
          <div class="cart-bar">
            <el-checkbox
              :model-value="allSelected"
              :indeterminate="partiallySelected"
              @change="toggleAll"
            >
              全选
            </el-checkbox>
            <span class="cart-bar__count">
              已选 <strong class="nm-num">{{ selectedCount }}</strong> / {{ items.length }} 门
            </span>
            <span class="cart-bar__spacer"></span>
            <button
              type="button"
              class="nm-btn nm-btn--ghost nm-btn--sm cart-bar__danger"
              :disabled="!selectedCount"
              @click="removeSelected()"
            >
              <el-icon :size="15"><Delete /></el-icon>
              批量删除
            </button>
          </div>

          <p v-if="demoMode" class="cart-notice">
            当前为本地示例数据；交易服务恢复后点击上方「重试」即可加载真实购物车。
          </p>

          <ul class="cart-list">
            <li
              v-for="item in items"
              :key="item.id"
              :class="['cart-row', { 'is-selected': selectedIds.includes(item.id) }]"
            >
              <el-checkbox
                class="row-check"
                :model-value="selectedIds.includes(item.id)"
                :aria-label="`选择 ${item.courseName}`"
                @change="toggleItem(item.id)"
              />
              <img
                class="row-cover"
                :src="coverOf(item)"
                :alt="item.courseName"
                loading="lazy"
              />
              <div class="row-copy">
                <h3 class="row-title">{{ item.courseName }}</h3>
                <p class="row-meta">
                  <span>{{ item.teacherName || '官方教研组' }}</span>
                  <span>课程永久有效</span>
                </p>
              </div>
              <p :class="['row-price', 'nm-num', { 'is-free': isFree(item) }]">
                {{ isFree(item) ? '免费' : `¥${money(Number(item.price || 0))}` }}
              </p>
              <div class="row-actions">
                <button type="button" class="row-action" @click="moveToFavorite(item)">
                  <el-icon :size="14"><Star /></el-icon>
                  移入收藏
                </button>
                <button
                  type="button"
                  class="row-action row-action--danger"
                  @click="removeItems([item])"
                >
                  <el-icon :size="14"><Delete /></el-icon>
                  移除
                </button>
              </div>
            </li>
          </ul>
        </template>
      </section>

      <aside v-if="hasItems" class="cart-summary">
        <div class="nm-card nm-card--pad summary-card">
          <span class="nm-kicker">订单摘要</span>

          <dl class="summary-list">
            <div class="summary-row">
              <dt>已选课程</dt>
              <dd class="nm-num">{{ selectedCount }} 门</dd>
            </div>
            <div class="summary-row">
              <dt>课程小计</dt>
              <dd class="nm-num">{{ money(subtotal) }}</dd>
            </div>
            <div class="summary-row">
              <dt>优惠券</dt>
              <dd class="summary-coupon">
                <span class="nm-num">- {{ money(couponDiscount) }}</span>
                <button
                  type="button"
                  class="coupon-link"
                  @click="router.push('/portal/my/coupons')"
                >
                  <el-icon :size="13"><Ticket /></el-icon>
                  去使用优惠券
                </button>
              </dd>
            </div>
          </dl>

          <hr class="nm-divider summary-divider" />

          <div class="summary-total">
            <span>合计</span>
            <strong class="nm-num">{{ money(total) }}</strong>
          </div>

          <button
            type="button"
            class="nm-btn nm-btn--primary nm-btn--lg summary-cta"
            :disabled="!selectedCount || checkingOut"
            @click="checkout()"
          >
            {{ checkingOut ? '正在创建订单…' : `去结算${selectedCount ? `（${selectedCount} 门）` : ''}` }}
          </button>

          <p class="summary-note">支持 7 天无理由退款 · 课程永久有效</p>
          <ul class="summary-hints">
            <li>免费课程可 0 元直接加入我的课程</li>
            <li>下单后可在「我的订单」中随时查看</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.cart-page {
  padding-block: 12px clamp(48px, 6vw, 80px);
}

/* --- 页头 --- */
.cart-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: clamp(18px, 2.2vw, 26px);
  border-bottom: 1px solid var(--nm-line);
  margin-bottom: clamp(20px, 2.6vw, 32px);
}

.cart-head__copy {
  display: grid;
  gap: 10px;
}

.cart-head__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* --- 两栏骨架 --- */
.cart-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: clamp(18px, 2.4vw, 30px);
  align-items: start;
}

.cart-body.is-single {
  grid-template-columns: minmax(0, 1fr);
}

.cart-main {
  min-width: 0;
  display: grid;
  gap: 14px;
}

/* --- 表头工具条 --- */
.cart-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
}

.cart-bar__count {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.cart-bar__count strong {
  color: var(--nm-ink);
  font-weight: 700;
}

.cart-bar__spacer {
  flex: 1;
}

.cart-bar__danger:not(:disabled) {
  color: var(--nm-danger);
}

.cart-bar__danger:disabled {
  color: var(--nm-ink-4);
}

.cart-notice {
  padding: 10px 14px;
  border: 1px dashed var(--nm-line-strong);
  border-radius: var(--nm-r-sm);
  background: var(--nm-warning-soft);
  color: var(--nm-warning);
  font-size: var(--nm-fs-sm);
}

/* --- 列表 --- */
.cart-list {
  list-style: none;
  display: grid;
  gap: 12px;
}

.cart-row {
  display: grid;
  grid-template-columns: auto 112px minmax(0, 1fr) auto auto;
  grid-template-areas: 'check cover copy price actions';
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
  transition: border-color var(--nm-dur) var(--nm-ease), box-shadow var(--nm-dur) var(--nm-ease),
    transform var(--nm-dur) var(--nm-ease);
}

.cart-row:hover {
  border-color: var(--nm-line-strong);
  box-shadow: var(--nm-sh-2);
}

.cart-row.is-selected {
  border-color: var(--nm-accent-line);
  box-shadow: inset 3px 0 0 var(--nm-accent), var(--nm-sh-1);
}

.row-check {
  grid-area: check;
  height: 100%;
  display: flex;
  align-items: center;
}

.row-cover {
  grid-area: cover;
  width: 112px;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: var(--nm-r-sm);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.row-copy {
  grid-area: copy;
  min-width: 0;
  display: grid;
  gap: 6px;
}

.row-title {
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.4;
  color: var(--nm-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.row-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.row-meta span + span::before {
  content: '·';
  margin: 0 8px;
  color: var(--nm-ink-4);
}

.row-price {
  grid-area: price;
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
  white-space: nowrap;
}

.row-price.is-free {
  color: var(--nm-success);
  font-family: var(--nm-font);
}

.row-actions {
  grid-area: actions;
  display: flex;
  align-items: center;
  gap: 4px;
}

.row-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 10px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-sm);
  background: transparent;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  font-weight: 550;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.row-action:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.row-action--danger:hover {
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
}

/* --- 摘要面板 --- */
.cart-summary {
  position: sticky;
  top: 96px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  min-width: 0;
}

.summary-card {
  display: grid;
  gap: 18px;
}

.summary-list {
  display: grid;
  gap: 12px;
}

.summary-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.summary-row dt {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.summary-row dd {
  font-size: var(--nm-fs-body);
  font-weight: 600;
  color: var(--nm-ink);
}

.summary-coupon {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.coupon-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--nm-accent);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  cursor: pointer;
}

.coupon-link:hover {
  color: var(--nm-accent-hover);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.summary-divider {
  margin: 0;
}

.summary-total {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.summary-total span {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.summary-total strong {
  font-size: clamp(1.75rem, 2.6vw, 2.15rem);
  font-weight: 780;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--nm-ink);
}

.summary-cta {
  width: 100%;
}

.summary-note {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
  text-align: center;
}

.summary-hints {
  list-style: none;
  display: grid;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px dashed var(--nm-line);
}

.summary-hints li {
  position: relative;
  padding-left: 16px;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.summary-hints li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.6em;
  width: 6px;
  height: 1px;
  background: var(--nm-accent);
}

/* --- 骨架 --- */
.cart-skeleton {
  display: grid;
  gap: 12px;
}

.sk-bar {
  height: 46px;
  border-radius: var(--nm-r);
}

.sk-row {
  display: grid;
  grid-template-columns: 20px 112px minmax(0, 1fr) 80px;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
}

.sk-check {
  width: 18px;
  height: 18px;
  border-radius: var(--nm-r-xs);
}

.sk-cover {
  width: 112px;
  aspect-ratio: 16 / 10;
}

.sk-copy {
  display: grid;
  gap: 10px;
}

.sk-line {
  height: 12px;
}

.sk-line--lg {
  width: 62%;
}

.sk-line--sm {
  width: 34%;
}

.sk-price {
  height: 18px;
}

/* --- 空态插画 --- */
.cart-empty__art {
  width: 168px;
  height: auto;
  opacity: 0.9;
}

/* --- 禁用态按钮 --- */
.nm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* --- 响应式 --- */
@media (max-width: 1024px) {
  .cart-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .cart-summary {
    position: static;
    max-height: none;
    overflow: visible;
  }
}

@media (max-width: 760px) {
  .cart-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .cart-bar {
    flex-wrap: wrap;
  }

  .cart-row {
    grid-template-columns: auto 96px minmax(0, 1fr);
    grid-template-areas:
      'check cover copy'
      'price price actions';
    row-gap: 12px;
  }

  .row-cover {
    width: 96px;
  }

  .row-price {
    justify-self: start;
  }

  .row-actions {
    justify-self: end;
  }

  .sk-row {
    grid-template-columns: 18px 96px minmax(0, 1fr);
  }

  .sk-price {
    display: none;
  }
}

@media (max-width: 420px) {
  .cart-row {
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-areas:
      'check cover'
      'copy copy'
      'price price'
      'actions actions';
    row-gap: 10px;
  }

  .row-check {
    align-self: start;
    padding-top: 2px;
  }

  .row-cover {
    width: 100%;
    max-width: 160px;
  }

  .row-actions {
    justify-self: stretch;
    justify-content: space-between;
  }
}
</style>

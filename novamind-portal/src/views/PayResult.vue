<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, Clock, CopyDocument, Check } from '@element-plus/icons-vue'

type PayState = 'success' | 'processing' | 'failed'

interface StateMeta {
  tone: PayState
  badge: string
  icon: Component
  title: string
  desc: string
  primaryLabel: string
  primaryTo: string
  note: string
}

const route = useRoute()

const STATE_META: Record<PayState, StateMeta> = {
  success: {
    tone: 'success',
    badge: '支付成功',
    icon: CircleCheck,
    title: '支付已完成',
    desc: '款项已收到，课程已加入你的学习空间，现在就可以开始学习。',
    primaryLabel: '进入我的课程',
    primaryTo: '/portal/my/lessons',
    note: '如未在「我的课程」中看到该课程，可稍等片刻后刷新，或前往「我的订单」核对。',
  },
  processing: {
    tone: 'processing',
    badge: '处理中',
    icon: Clock,
    title: '支付处理中',
    desc: '我们正在等待支付渠道的确认结果，通常几秒内即可完成，请不要重复支付。',
    primaryLabel: '查看我的订单',
    primaryTo: '/portal/my/orders',
    note: '支付结果由支付渠道异步通知，若长时间停留在处理中，可在「我的订单」查看最终状态。',
  },
  failed: {
    tone: 'failed',
    badge: '未完成',
    icon: CircleClose,
    title: '支付未完成',
    desc: '本次支付没有成功，可能是支付被取消、超时或渠道返回失败，订单不会占用课程名额。',
    primaryLabel: '重新选择课程',
    primaryTo: '/portal/courses',
    note: '你可以重新下单支付，或在「我的订单」中查看这笔订单的详细状态。',
  },
}

/** 查询参数 -> 支付状态；未识别或缺失时按“处理中”呈现，避免把未知结果谎报成成功 */
const STATUS_ALIASES: Record<string, PayState> = {
  success: 'success',
  succeeded: 'success',
  ok: 'success',
  paid: 'success',
  '1': 'success',
  processing: 'processing',
  pending: 'processing',
  waiting: 'processing',
  paying: 'processing',
  failed: 'failed',
  failure: 'failed',
  fail: 'failed',
  error: 'failed',
  canceled: 'failed',
  cancelled: 'failed',
  '0': 'failed',
}

function firstValue(value: unknown): string {
  if (Array.isArray(value)) {
    const first: unknown = value[0]
    return typeof first === 'string' ? first.trim() : ''
  }
  return typeof value === 'string' ? value.trim() : ''
}

const orderId = computed(() => firstValue(route.params.orderId))

const state = computed<PayState>(() => {
  const raw = firstValue(route.query.status).toLowerCase()
  return STATUS_ALIASES[raw] ?? 'processing'
})

const meta = computed<StateMeta>(() => STATE_META[state.value])

const checking = ref(false)
const copied = ref(false)
const checkedAt = ref<Date>(new Date())
let copiedTimer: ReturnType<typeof setTimeout> | null = null

const checkedAtLabel = computed(() => {
  const d = checkedAt.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

function refresh() {
  if (checking.value) return
  checking.value = true
  // 没有可查询的订单接口时，这里只重新读取路由上的状态并刷新确认时间
  window.setTimeout(() => {
    checkedAt.value = new Date()
    checking.value = false
    if (state.value === 'processing') {
      ElMessage.info('仍在等待支付渠道确认，请稍后再试')
    }
  }, 800)
}

async function copyOrderId() {
  const id = orderId.value
  if (!id) return
  if (!navigator.clipboard) {
    ElMessage.warning('当前浏览器不支持自动复制，请手动记录订单号')
    return
  }
  try {
    await navigator.clipboard.writeText(id)
    copied.value = true
    if (copiedTimer) clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => {
      copied.value = false
    }, 1800)
  } catch {
    ElMessage.warning('复制失败，请手动记录订单号')
  }
}

onMounted(() => {
  checkedAt.value = new Date()
})
</script>

<template>
  <div class="pr">
    <div class="pr__inner nm-shell">
      <article class="pr__card nm-card">
        <header class="pr__head">
          <span :class="['pr__icon', `is-${meta.tone}`]">
            <el-icon :size="30"><component :is="meta.icon" /></el-icon>
          </span>
          <p class="nm-kicker pr__kicker">PAYMENT · 支付结果</p>
          <h1 class="nm-h2 pr__title">{{ meta.title }}</h1>
          <p class="pr__desc">{{ meta.desc }}</p>
          <span :class="['nm-badge', `nm-badge--${meta.tone === 'processing' ? 'warning' : meta.tone}`]">
            {{ meta.badge }}
          </span>
        </header>

        <hr class="nm-divider" />

        <dl class="pr__facts">
          <div class="pr__fact">
            <dt>订单号</dt>
            <dd>
              <span class="nm-num pr__order">{{ orderId || '未提供' }}</span>
              <button
                v-if="orderId"
                type="button"
                class="pr__copy"
                :aria-label="copied ? '订单号已复制' : '复制订单号'"
                @click="copyOrderId"
              >
                <el-icon :size="14">
                  <Check v-if="copied" />
                  <CopyDocument v-else />
                </el-icon>
                <span>{{ copied ? '已复制' : '复制' }}</span>
              </button>
            </dd>
          </div>
          <div class="pr__fact">
            <dt>结果确认时间</dt>
            <dd><span class="nm-num">{{ checkedAtLabel }}</span></dd>
          </div>
          <div class="pr__fact">
            <dt>说明</dt>
            <dd class="pr__note">{{ meta.note }}</dd>
          </div>
        </dl>

        <div class="pr__actions">
          <button
            v-if="state === 'processing'"
            type="button"
            class="nm-btn nm-btn--primary nm-btn--lg"
            :disabled="checking"
            @click="refresh"
          >
            <span v-if="checking" class="pr__spinner" aria-hidden="true"></span>
            {{ checking ? '正在查询…' : '刷新状态' }}
          </button>
          <router-link v-else class="nm-btn nm-btn--primary nm-btn--lg" :to="meta.primaryTo">
            {{ meta.primaryLabel }}
          </router-link>

          <router-link class="nm-btn nm-btn--lg" to="/portal/my/orders">查看订单</router-link>
          <router-link class="nm-btn nm-btn--ghost nm-btn--lg" to="/portal/home">返回首页</router-link>
        </div>

        <p class="pr__foot">
          支付遇到问题？可前往
          <router-link class="pr__foot-link" to="/portal/my/orders">我的订单</router-link>
          查看记录，或稍后在
          <router-link class="pr__foot-link" to="/portal/courses">课程中心</router-link>
          重新下单。
        </p>
      </article>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.pr {
  min-height: calc(100vh - 88px);
  display: flex;
  align-items: center;
  padding-block: clamp(32px, 5vw, 72px);
  background: var(--nm-paper);
}

.pr__inner {
  width: 100%;
  display: flex;
  justify-content: center;
}

.pr__card {
  width: 100%;
  max-width: 620px;
  padding: clamp(26px, 3.6vw, 44px);
  box-shadow: var(--nm-sh-3);
}

.pr__head {
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
}

.pr__icon {
  width: 68px;
  height: 68px;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-xl);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  margin-bottom: 4px;
}

.pr__icon.is-success {
  color: var(--nm-success);
  background: var(--nm-success-soft);
  border-color: var(--nm-success-soft);
}

.pr__icon.is-processing {
  color: var(--nm-warning);
  background: var(--nm-warning-soft);
  border-color: var(--nm-warning-soft);
  animation: pulseGlow 2.4s var(--nm-ease) infinite;
}

.pr__icon.is-failed {
  color: var(--nm-danger);
  background: var(--nm-danger-soft);
  border-color: var(--nm-danger-soft);
}

.pr__kicker {
  color: var(--nm-ink-3);
}

.pr__title {
  margin-top: 2px;
}

.pr__desc {
  max-width: 44ch;
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-body);
  line-height: 1.7;
}

.pr__facts {
  display: grid;
  gap: 14px;
  margin: 0;
  padding-block: clamp(18px, 2.4vw, 26px);
}

.pr__fact {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.pr__fact dt {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
  line-height: 1.9;
}

.pr__fact dd {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: var(--nm-ink);
  font-size: var(--nm-fs-body);
  line-height: 1.7;
  word-break: break-all;
}

.pr__order {
  font-size: var(--nm-fs-body);
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--nm-r-xs);
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.pr__copy {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border: 1px solid var(--nm-line-strong);
  border-radius: var(--nm-r-xs);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  cursor: pointer;
  transition: color var(--nm-dur-fast) var(--nm-ease),
    border-color var(--nm-dur-fast) var(--nm-ease), background-color var(--nm-dur-fast) var(--nm-ease);
}

.pr__copy:hover {
  color: var(--nm-accent);
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
}

.pr__note {
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
}

.pr__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: clamp(18px, 2.4vw, 26px);
  border-top: 1px solid var(--nm-line);
}

.pr__spinner {
  width: 15px;
  height: 15px;
  border-radius: var(--nm-r-full);
  border: 2px solid currentColor;
  border-top-color: transparent;
  animation: pr-spin 0.7s linear infinite;
}

@keyframes pr-spin {
  to {
    transform: rotate(360deg);
  }
}

.pr__foot {
  margin-top: 20px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
  text-align: center;
}

.pr__foot-link {
  color: var(--nm-accent);
  font-weight: 600;
}

.pr__foot-link:hover {
  color: var(--nm-accent-hover);
  text-decoration: underline;
}

@media (max-width: 560px) {
  .pr__fact {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }

  .pr__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .pr__actions .nm-btn {
    width: 100%;
  }
}
</style>

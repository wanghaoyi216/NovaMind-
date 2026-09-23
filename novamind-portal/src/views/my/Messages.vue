<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import iconBell from '@/assets/icons/icon-bell.svg?raw'
import iconCourses from '@/assets/icons/icon-courses.svg?raw'
import iconCommunity from '@/assets/icons/icon-community.svg?raw'
import sceneEmptyChat from '@/assets/scenes/scene-empty-chat.svg?raw'

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

type MsgKind = 'system' | 'course' | 'interact'
type FilterKey = 'all' | 'unread' | 'system' | 'course'

interface MsgThread {
  id: number
  kind: MsgKind
  title: string
  snippet: string
  paragraphs: string[]
  sender: string
  avatar?: string
  time: string
  unread: boolean
  cta: { label: string; path: string }
}

const router = useRouter()
const page = inject<MyPageContext | null>(MY_PAGE_CONTEXT, null)

const kindMeta: Record<MsgKind, { label: string; icon: string; badge: string }> = {
  system: { label: '系统通知', icon: iconBell, badge: 'nm-badge--accent' },
  course: { label: '课程提醒', icon: iconCourses, badge: 'nm-badge--warning' },
  interact: { label: '互动消息', icon: iconCommunity, badge: 'nm-badge--success' },
}

function minutesAgo(minutes: number) {
  return new Date(Date.now() - minutes * 60000).toISOString()
}

/**
 * 消息服务尚未接入后端（@/api 无对应端点），
 * 这里用本地演示数据驱动双栏结构，空态另有完整设计。
 */
const threads = ref<MsgThread[]>([
  {
    id: 1,
    kind: 'system',
    title: '订单支付成功，课程已加入学习',
    snippet: '订单 NO.20260918001 已完成支付，可立即开始学习。',
    paragraphs: [
      '你的订单 NO.20260918001 已于今日完成支付，金额 ¥199.00。',
      '课程《Vue 3 企业级项目实战》已加入「我的课程」，学习进度会自动同步，换设备登录也能接着学。',
      '如需开具发票或申请退款，可在订单详情中操作。',
    ],
    sender: 'NovaMind 系统',
    time: minutesAgo(12),
    unread: true,
    cta: { label: '去我的订单', path: '/portal/my/orders' },
  },
  {
    id: 2,
    kind: 'course',
    title: '课程更新：新增 3 节实战内容',
    snippet: '《Vue 3 企业级项目实战》新增「组合式 API 进阶」等 3 节内容。',
    paragraphs: [
      '你正在学的《Vue 3 企业级项目实战》已更新，新增 3 节内容：组合式 API 进阶、状态管理重构、性能优化清单。',
      '更新内容已并入原有章节顺序，建议在完成当前章节后继续学习。',
    ],
    sender: '课程助教 · 李老师',
    avatar: '/resource/user-avatars/avatar_teacher_female_01.jpg',
    time: minutesAgo(180),
    unread: true,
    cta: { label: '去我的课程', path: '/portal/my/lessons' },
  },
  {
    id: 3,
    kind: 'system',
    title: '优惠券已到账：新人专享 ¥50',
    snippet: '满 199 可用，有效期至 2026-12-31，下单时自动匹配。',
    paragraphs: [
      '恭喜获得新人专享券：满 199 减 50，适用于全站课程（特价课除外）。',
      '有效期至 2026-12-31，下单时系统会自动匹配最优惠的一张。',
    ],
    sender: 'NovaMind 系统',
    time: minutesAgo(60 * 26),
    unread: false,
    cta: { label: '去我的优惠券', path: '/portal/my/coupons' },
  },
  {
    id: 4,
    kind: 'course',
    title: '学习提醒：已经 3 天没有学习啦',
    snippet: '《Spring Boot 微服务架构》还差 2 节就能完成本章。',
    paragraphs: [
      '你上次学习《Spring Boot 微服务架构》是在 3 天前，停在「服务注册与发现」这一节。',
      '本章还剩 2 节，约 26 分钟即可完成。保持节奏，学完的课程会出现在「已完成」里。',
    ],
    sender: '课程助教 · 王老师',
    avatar: '/resource/user-avatars/avatar_teacher_male_01.jpg',
    time: minutesAgo(60 * 74),
    unread: true,
    cta: { label: '继续学习', path: '/portal/my/lessons' },
  },
  {
    id: 5,
    kind: 'interact',
    title: '李老师回复了你的提问',
    snippet: '「服务注册与发现」下的提问有新回复。',
    paragraphs: [
      '你在《Spring Boot 微服务架构》「服务注册与发现」一节的提问收到了讲师回复。',
      '回复：Nacos 的心跳间隔可以在实例配置里调整，生产环境建议保持默认值并配合健康检查使用。',
    ],
    sender: '李老师',
    avatar: '/resource/user-avatars/avatar_businessman_phone_01.jpg',
    time: minutesAgo(60 * 100),
    unread: false,
    cta: { label: '去课程讨论', path: '/portal/courses' },
  },
  {
    id: 6,
    kind: 'system',
    title: '账号安全提醒：新设备登录',
    snippet: '你的账号在一台新设备上登录，若非本人操作请及时修改密码。',
    paragraphs: [
      '检测到你的账号在 Windows 设备上通过验证码登录，登录地点：本地网络。',
      '如果这是你本人的操作，可以忽略本消息；否则请尽快前往「个人中心 - 账号安全」修改密码。',
    ],
    sender: 'NovaMind 安全中心',
    time: minutesAgo(60 * 240),
    unread: false,
    cta: { label: '去个人中心', path: '/portal/my/profile' },
  },
])

const filters: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'system', label: '系统通知' },
  { key: 'course', label: '课程提醒' },
]

const filter = ref<FilterKey>('all')
const activeId = ref<number | null>(1)
const detailOpen = ref(false)
/** 「未读」筛选下打开消息会立即标记已读，这里把它钉在列表里，避免阅读中列表跳动 */
const pinnedIds = ref<number[]>([])

const unreadCount = computed(() => threads.value.filter((item) => item.unread).length)

function matchFilter(thread: MsgThread, key: FilterKey) {
  if (key === 'unread') return thread.unread
  if (key === 'system') return thread.kind === 'system'
  if (key === 'course') return thread.kind === 'course'
  return true
}

const visibleThreads = computed(() =>
  threads.value.filter(
    (item) =>
      matchFilter(item, filter.value) ||
      (filter.value === 'unread' && pinnedIds.value.includes(item.id))
  )
)

const counts = computed(() => ({
  all: threads.value.length,
  unread: threads.value.filter((item) => matchFilter(item, 'unread')).length,
  system: threads.value.filter((item) => matchFilter(item, 'system')).length,
  course: threads.value.filter((item) => matchFilter(item, 'course')).length,
}))

const activeThread = computed(() => threads.value.find((item) => item.id === activeId.value) ?? null)

function relativeTime(value: string) {
  const stamp = new Date(value).getTime()
  if (Number.isNaN(stamp)) return value
  const diff = Date.now() - stamp
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  const date = new Date(stamp)
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function absoluteTime(value: string) {
  const stamp = new Date(value).getTime()
  if (Number.isNaN(stamp)) return value
  const date = new Date(stamp)
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`
}

function openThread(thread: MsgThread) {
  activeId.value = thread.id
  detailOpen.value = true
  if (thread.unread) {
    if (filter.value === 'unread' && !pinnedIds.value.includes(thread.id)) {
      pinnedIds.value = [...pinnedIds.value, thread.id]
    }
    threads.value = threads.value.map((item) =>
      item.id === thread.id ? { ...item, unread: false } : item
    )
  }
}

function backToList() {
  detailOpen.value = false
}

function markUnread(thread: MsgThread) {
  threads.value = threads.value.map((item) =>
    item.id === thread.id ? { ...item, unread: true } : item
  )
}

function removeThread(thread: MsgThread) {
  threads.value = threads.value.filter((item) => item.id !== thread.id)
  pinnedIds.value = pinnedIds.value.filter((id) => id !== thread.id)
  if (activeId.value === thread.id) {
    activeId.value = threads.value[0]?.id ?? null
    detailOpen.value = false
  }
}

function markAllRead() {
  if (!unreadCount.value) return
  threads.value = threads.value.map((item) => ({ ...item, unread: false }))
  pinnedIds.value = []
}

function syncHeaderAction() {
  const action = page?.actions.find((item) => item.key === 'read-all')
  if (action) action.disabled = unreadCount.value === 0
}

watch(filter, () => {
  detailOpen.value = false
  pinnedIds.value = []
  const first = visibleThreads.value[0]
  if (!first || !visibleThreads.value.some((item) => item.id === activeId.value)) {
    activeId.value = first?.id ?? null
  }
})

watch(unreadCount, syncHeaderAction)

if (page) {
  page.title = '消息中心'
  page.description = '订单、课程与互动的通知集中在这里，未读消息会同步到顶栏提醒。'
  page.actions = [
    {
      key: 'read-all',
      label: '全部标为已读',
      variant: 'ghost',
      disabled: unreadCount.value === 0,
      onClick: markAllRead,
    },
  ]
}
</script>

<template>
  <div class="ms">
    <!-- 过滤条 -->
    <div class="ms-bar">
      <div class="ms-tabs" role="tablist" aria-label="消息筛选">
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
          <span class="nm-num ms-tabs__count">{{ counts[item.key] }}</span>
        </button>
      </div>
      <p class="ms-bar__hint nm-muted">
        <template v-if="unreadCount">
          <span class="ms-dot" aria-hidden="true" />
          <span class="nm-num">{{ unreadCount }}</span> 条未读
        </template>
        <template v-else>已全部读完</template>
      </p>
    </div>

    <!-- 空态 -->
    <div v-if="!visibleThreads.length" class="nm-state">
      <span class="ms-state__scene" aria-hidden="true" v-html="sceneEmptyChat" />
      <p class="nm-state__title">
        {{ filter === 'unread' ? '没有未读消息' : filter === 'all' ? '暂时没有消息' : '这个分类下还没有消息' }}
      </p>
      <p class="nm-state__desc">
        {{
          filter === 'unread'
            ? '所有消息都已读完，新的通知会在到达时出现在这里。'
            : '下单、课程更新与讲师回复都会以通知的形式推送到这里。'
        }}
      </p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
          去课程中心
        </button>
        <button
          v-if="filter !== 'all'"
          type="button"
          class="nm-btn nm-btn--ghost"
          @click="filter = 'all'"
        >
          查看全部消息
        </button>
      </div>
    </div>

    <!-- 双栏：列表 + 阅读区 -->
    <div v-else :class="['ms-pane', { 'is-detail': detailOpen }]">
      <ul class="ms-list" aria-label="消息列表">
        <li v-for="thread in visibleThreads" :key="thread.id">
          <button
            type="button"
            :class="['ms-item', { 'is-active': activeId === thread.id, 'is-unread': thread.unread }]"
            @click="openThread(thread)"
          >
            <span class="ms-item__media">
              <img v-if="thread.avatar" :src="thread.avatar" alt="" loading="lazy" />
              <span v-else class="ms-item__icon" aria-hidden="true" v-html="kindMeta[thread.kind].icon" />
            </span>

            <span class="ms-item__copy">
              <span class="ms-item__head">
                <span class="ms-item__title">{{ thread.title }}</span>
                <span class="ms-item__time nm-num">{{ relativeTime(thread.time) }}</span>
              </span>
              <span class="ms-item__snippet">{{ thread.snippet }}</span>
              <span class="ms-item__foot">
                <span class="nm-badge" :class="kindMeta[thread.kind].badge">
                  {{ kindMeta[thread.kind].label }}
                </span>
                <span v-if="thread.unread" class="ms-dot" aria-label="未读" />
              </span>
            </span>
          </button>
        </li>
      </ul>

      <section class="ms-read">
        <template v-if="activeThread">
          <header class="ms-read__head">
            <button type="button" class="ms-back" @click="backToList">← 返回列表</button>
            <span class="nm-badge" :class="kindMeta[activeThread.kind].badge">
              {{ kindMeta[activeThread.kind].label }}
            </span>
            <h2 class="ms-read__title nm-h3">{{ activeThread.title }}</h2>
            <p class="ms-read__meta nm-muted">
              <span>{{ activeThread.sender }}</span>
              <span class="ms-read__dot" aria-hidden="true">·</span>
              <span class="nm-num">{{ absoluteTime(activeThread.time) }}</span>
            </p>
          </header>

          <div class="ms-read__body">
            <p v-for="(paragraph, index) in activeThread.paragraphs" :key="index">{{ paragraph }}</p>
          </div>

          <footer class="ms-read__foot">
            <button
              type="button"
              class="nm-btn nm-btn--sm nm-btn--accent"
              @click="router.push(activeThread.cta.path)"
            >
              {{ activeThread.cta.label }}
            </button>
            <button
              type="button"
              class="nm-btn nm-btn--sm nm-btn--ghost"
              @click="markUnread(activeThread)"
            >
              标为未读
            </button>
            <button
              type="button"
              class="nm-btn nm-btn--sm nm-btn--ghost ms-del"
              @click="removeThread(activeThread)"
            >
              删除
            </button>
          </footer>
        </template>

        <div v-else class="ms-read__blank nm-muted">从左侧选择一条消息查看详情</div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.ms {
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
.ms-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.ms-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ms-tabs__count {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.nm-chip.is-active .ms-tabs__count {
  color: var(--nm-paper);
}

[data-theme='dark'] .nm-chip.is-active .ms-tabs__count {
  color: var(--nm-accent-ink);
}

.ms-bar__hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--nm-fs-xs);
}

.ms-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
}

/* ---------------- 双栏 ---------------- */
.ms-pane {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  height: min(72vh, 680px);
  min-height: 460px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
  overflow: hidden;
}

.ms-list {
  list-style: none;
  overflow-y: auto;
  border-right: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
}

.ms-item {
  display: flex;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid var(--nm-line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease);
}

.ms-item:hover {
  background: var(--nm-surface-3);
}

.ms-item.is-active {
  background: var(--nm-surface);
  box-shadow: inset 3px 0 0 var(--nm-accent);
}

.ms-item__media {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: var(--nm-r-sm);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface);
  overflow: hidden;
}

.ms-item__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ms-item__icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  opacity: 0.75;
}

.ms-item__icon :deep(svg) {
  width: 100%;
  height: 100%;
}

.ms-item__copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.ms-item__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.ms-item__title {
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-item.is-unread .ms-item__title {
  color: var(--nm-ink);
  font-weight: 700;
}

.ms-item__time {
  font-size: 11px;
  color: var(--nm-ink-4);
  flex-shrink: 0;
}

.ms-item__snippet {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-item__foot {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ---------------- 阅读区 ---------------- */
.ms-read {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.ms-read__head {
  display: grid;
  gap: 8px;
  padding: clamp(20px, 2.4vw, 30px);
  border-bottom: 1px solid var(--nm-line);
}

.ms-back {
  display: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--nm-accent);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  cursor: pointer;
  justify-self: start;
}

.ms-read__title {
  margin-top: 2px;
}

.ms-read__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--nm-fs-xs);
}

.ms-read__dot {
  color: var(--nm-ink-4);
}

.ms-read__body {
  display: grid;
  gap: 14px;
  padding: clamp(20px, 2.4vw, 30px);
  font-size: var(--nm-fs-body);
  line-height: 1.78;
  color: var(--nm-ink-2);
  flex: 1;
}

.ms-read__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px clamp(20px, 2.4vw, 30px);
  border-top: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
}

.ms-del:hover {
  color: var(--nm-danger);
}

.ms-read__blank {
  display: grid;
  place-items: center;
  flex: 1;
  font-size: var(--nm-fs-sm);
}

.ms-state__scene {
  display: block;
  width: 176px;
  max-width: 62%;
}

.ms-state__scene :deep(svg) {
  width: 100%;
  height: auto;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 860px) {
  .ms-pane {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 0;
  }

  .ms-list {
    max-height: 60vh;
    border-right: 0;
  }

  .ms-pane.is-detail .ms-list {
    display: none;
  }

  .ms-pane:not(.is-detail) .ms-read {
    display: none;
  }

  .ms-back {
    display: inline-flex;
  }

  .ms-read {
    min-height: 320px;
  }
}
</style>

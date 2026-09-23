<script setup lang="ts">
import { computed, onMounted, provide, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserInfo } from '@/api/auth'
import { getMyLessons } from '@/api/learning'
import type { LearningLesson } from '@/api/learning'
import { useUserStore } from '@/stores/user'
import iconCourses from '@/assets/icons/icon-courses.svg?raw'
import iconOrders from '@/assets/icons/icon-orders.svg?raw'
import iconCoupon from '@/assets/icons/icon-coupon.svg?raw'
import iconBell from '@/assets/icons/icon-bell.svg?raw'
import iconProfile from '@/assets/icons/icon-profile.svg?raw'

/** 会员中心页头动作：由子页面注入 */
interface MyPageAction {
  key: string
  label: string
  variant?: 'primary' | 'accent' | 'ghost'
  disabled?: boolean
  loading?: boolean
  onClick?: () => void
}

/** 会员中心页面上下文：标题 / 描述 / 页头动作 / 侧栏统计刷新 */
interface MyPageContext {
  title: string
  description: string
  actions: MyPageAction[]
  refreshStats: () => void
}

/** 子页面 inject 同一个 key（字符串常量，避免跨文件类型依赖） */
const MY_PAGE_CONTEXT = 'nm-my-page-context'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const navItems = [
  { path: '/portal/my/lessons', label: '我的课程', desc: '继续未完成的进度', icon: iconCourses },
  { path: '/portal/my/orders', label: '我的订单', desc: '购买记录与支付', icon: iconOrders },
  { path: '/portal/my/coupons', label: '我的优惠券', desc: '可用 / 已用 / 过期', icon: iconCoupon },
  { path: '/portal/my/messages', label: '消息中心', desc: '通知与课程提醒', icon: iconBell },
  { path: '/portal/my/profile', label: '个人中心', desc: '资料与学习偏好', icon: iconProfile },
]

const fallbackAvatar = '/resource/user-avatars/avatar_student_male_01.jpg'

const stats = ref<{ learning: number; finished: number; hours: number } | null>(null)

const page = reactive<MyPageContext>({
  title: typeof route.meta.title === 'string' ? route.meta.title : '我的课程',
  description: '',
  actions: [],
  refreshStats: () => {
    void loadStats()
  },
})

provide(MY_PAGE_CONTEXT, page)

const displayName = computed(() => userStore.userInfo?.username || '学员用户')
const avatar = computed(() => userStore.userInfo?.avatar || fallbackAvatar)
const phoneText = computed(() => {
  const phone = userStore.userInfo?.phone
  if (!phone) return '未绑定手机号'
  return phone.length >= 11 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
})

function isActive(path: string) {
  return route.path.startsWith(path)
}

async function loadStats() {
  try {
    const res: any = await getMyLessons({ pageNo: 1, pageSize: 200 })
    const list: LearningLesson[] = Array.isArray(res?.data?.list) ? res.data.list : []
    stats.value = {
      learning: list.filter((item) => !item.finish).length,
      finished: list.filter((item) => item.finish).length,
      hours: Math.round(list.reduce((sum, item) => sum + (item.studiedDuration || 0), 0) / 3600),
    }
  } catch {
    // 后端未启动时静默降级：统计行整体不渲染
    stats.value = null
  }
}

async function ensureUser() {
  if (userStore.userInfo) return
  const token = localStorage.getItem('nova_token')
  if (!token) return
  try {
    const res: any = await getUserInfo()
    if (res?.data) {
      if (!userStore.token) userStore.setToken(token)
      userStore.setInfo(res.data)
    }
  } catch {
    // 身份信息拉取失败时保留占位展示，不阻塞页面
  }
}

watch(
  () => route.path,
  () => {
    page.title = typeof route.meta.title === 'string' ? route.meta.title : '会员中心'
    page.description = ''
    page.actions = []
  }
)

onMounted(() => {
  void ensureUser()
  void loadStats()
})
</script>

<template>
  <div class="my-shell">
    <div class="my-shell__inner nm-shell">
      <aside class="my-aside">
        <section class="my-id nm-card nm-card--pad">
          <div class="my-id__top">
            <img class="my-id__avatar" :src="avatar" alt="" />
            <div class="my-id__copy">
              <p class="nm-kicker nm-kicker--plain">MEMBER</p>
              <h2 class="my-id__name">{{ displayName }}</h2>
              <p class="my-id__phone nm-num">{{ phoneText }}</p>
            </div>
          </div>

          <div v-if="stats" class="my-stats">
            <div class="my-stats__item">
              <span class="my-stats__num nm-num">{{ stats.learning }}</span>
              <span class="my-stats__label">在学</span>
            </div>
            <div class="my-stats__item">
              <span class="my-stats__num nm-num">{{ stats.finished }}</span>
              <span class="my-stats__label">已完成</span>
            </div>
            <div class="my-stats__item">
              <span class="my-stats__num nm-num">{{ stats.hours }}</span>
              <span class="my-stats__label">学时</span>
            </div>
          </div>
        </section>

        <nav class="my-nav" aria-label="会员中心导航">
          <ul class="my-nav__list">
            <li v-for="item in navItems" :key="item.path">
              <button
                type="button"
                :class="['my-nav__item', { 'is-active': isActive(item.path) }]"
                :aria-current="isActive(item.path) ? 'page' : undefined"
                @click="router.push(item.path)"
              >
                <span class="my-nav__ico" aria-hidden="true" v-html="item.icon" />
                <span class="my-nav__text">
                  <span class="my-nav__label">{{ item.label }}</span>
                  <span class="my-nav__desc">{{ item.desc }}</span>
                </span>
              </button>
            </li>
          </ul>
        </nav>

        <router-link to="/portal/courses" class="my-cta nm-card nm-card--pad nm-card--hover">
          <span class="nm-kicker nm-kicker--plain">EXPLORE</span>
          <strong class="my-cta__title">去课程中心选课</strong>
          <span class="my-cta__desc">28 门实战课程，按方向挑选</span>
          <span class="my-cta__arrow" aria-hidden="true">→</span>
        </router-link>
      </aside>

      <section class="my-main">
        <header class="my-head">
          <div class="my-head__copy">
            <p class="nm-kicker">MEMBER CENTER</p>
            <h1 class="nm-h1">{{ page.title }}</h1>
            <p v-if="page.description" class="my-head__desc nm-lede">{{ page.description }}</p>
          </div>
          <div v-if="page.actions.length" class="my-head__actions">
            <button
              v-for="action in page.actions"
              :key="action.key"
              type="button"
              class="nm-btn"
              :class="{
                'nm-btn--primary': action.variant === 'primary',
                'nm-btn--accent': action.variant === 'accent',
                'nm-btn--ghost': action.variant === 'ghost',
                'nm-btn--sm': true,
              }"
              :disabled="action.disabled || action.loading"
              @click="action.onClick?.()"
            >
              <span v-if="action.loading" class="my-spin" aria-hidden="true" />
              {{ action.label }}
            </button>
          </div>
        </header>

        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.my-shell {
  padding-block: clamp(22px, 3vw, 44px) clamp(48px, 6vw, 88px);
}

/* 禁用态（设计系统只定义常态，这里按页面需要补齐） */
.nm-btn:disabled,
.nm-btn:disabled:hover {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.my-shell__inner {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: clamp(20px, 2.6vw, 40px);
  align-items: start;
}

/* ---------------- 侧栏 ---------------- */
.my-aside {
  position: sticky;
  top: 104px;
  display: grid;
  gap: 14px;
}

.my-id__top {
  display: flex;
  align-items: center;
  gap: 14px;
}

.my-id__avatar {
  width: 54px;
  height: 54px;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
  flex-shrink: 0;
}

.my-id__copy {
  min-width: 0;
}

.my-id__name {
  margin-top: 2px;
  font-size: 1.0625rem;
  font-weight: 720;
  letter-spacing: -0.02em;
  color: var(--nm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-id__phone {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.my-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--nm-line);
}

.my-stats__item {
  display: grid;
  gap: 2px;
  justify-items: center;
  text-align: center;
}

.my-stats__item + .my-stats__item {
  border-left: 1px solid var(--nm-line);
}

.my-stats__num {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
  line-height: 1.2;
}

.my-stats__label {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.my-nav {
  padding: 6px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
}

.my-nav__list {
  display: grid;
  gap: 2px;
  list-style: none;
}

.my-nav__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--nm-r);
  background: transparent;
  color: var(--nm-ink-2);
  text-align: left;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    color var(--nm-dur-fast) var(--nm-ease);
}

.my-nav__item:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.my-nav__item.is-active {
  background: var(--nm-accent-soft);
  color: var(--nm-ink);
}

.my-nav__item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
}

.my-nav__ico {
  display: inline-flex;
  width: 19px;
  height: 19px;
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity var(--nm-dur-fast) var(--nm-ease);
}

.my-nav__item:hover .my-nav__ico,
.my-nav__item.is-active .my-nav__ico {
  opacity: 1;
}

.my-nav__ico :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.my-nav__text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.my-nav__label {
  font-size: var(--nm-fs-body);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.my-nav__desc {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-nav__item.is-active .my-nav__desc {
  color: var(--nm-ink-2);
}

.my-cta {
  display: grid;
  gap: 3px;
  text-decoration: none;
  background: var(--nm-surface-2);
  box-shadow: none;
}

.my-cta__title {
  margin-top: 4px;
  font-size: var(--nm-fs-body);
  font-weight: 650;
  color: var(--nm-ink);
}

.my-cta__desc {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.my-cta__arrow {
  margin-top: 6px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-accent);
}

/* ---------------- 内容区 ---------------- */
.my-main {
  min-width: 0;
}

.my-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding-bottom: 18px;
  margin-bottom: clamp(18px, 2.4vw, 28px);
  border-bottom: 1px solid var(--nm-line);
}

.my-head__copy {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.my-head__desc {
  margin-top: 0;
  font-size: var(--nm-fs-body);
}

.my-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.my-spin {
  width: 13px;
  height: 13px;
  border-radius: var(--nm-r-full);
  border: 2px solid var(--nm-line-strong);
  border-top-color: var(--nm-accent);
  animation: my-spin 0.7s linear infinite;
}

@keyframes my-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .my-shell__inner {
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 20px;
  }
}

@media (max-width: 900px) {
  .my-shell__inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .my-aside {
    position: static;
    gap: 10px;
  }

  .my-id {
    padding: 14px 16px;
  }

  .my-id__top {
    gap: 12px;
  }

  .my-id__avatar {
    width: 44px;
    height: 44px;
  }

  .my-stats {
    margin-top: 12px;
    padding-top: 12px;
  }

  /* 移动端：侧栏变成横向可滚动标签条 */
  .my-nav {
    position: sticky;
    top: 76px;
    z-index: 20;
    padding: 8px 10px;
    border-radius: var(--nm-r);
    background: var(--nm-paper);
    box-shadow: none;
    border-color: var(--nm-line);
  }

  .my-nav__list {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;
  }

  .my-nav__list::-webkit-scrollbar {
    display: none;
  }

  .my-nav__item {
    width: auto;
    padding: 8px 14px;
    border: 1px solid var(--nm-line);
    border-radius: var(--nm-r-full);
    background: var(--nm-surface);
    white-space: nowrap;
    flex-shrink: 0;
  }

  .my-nav__item.is-active::before {
    display: none;
  }

  .my-nav__desc {
    display: none;
  }

  .my-nav__label {
    font-size: var(--nm-fs-sm);
  }

  .my-cta {
    display: none;
  }

  .my-head {
    align-items: flex-start;
  }
}

@media (max-width: 560px) {
  .my-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .my-head__actions {
    width: 100%;
  }

  .my-head__actions .nm-btn {
    flex: 1;
  }
}
</style>

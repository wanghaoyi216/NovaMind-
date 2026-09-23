<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Clock,
  Reading,
  Refresh,
  TrendCharts,
  Trophy,
  WarningFilled,
} from '@element-plus/icons-vue'
import { getRecommendBest, getRecommendNew } from '@/api/course'
import { getMyLessons, getNowLearning, type LearningLesson } from '@/api/learning'
import { useUserStore } from '@/stores/user'
import type { PageResult, RecommendCourse } from '@/types'
import iconAiChat from '@/assets/icons/icon-ai-chat.svg?raw'
import iconCart from '@/assets/icons/icon-cart.svg?raw'
import iconCommunity from '@/assets/icons/icon-community.svg?raw'
import iconCourses from '@/assets/icons/icon-courses.svg?raw'
import iconGraph from '@/assets/icons/icon-graph.svg?raw'
import iconProfile from '@/assets/icons/icon-profile.svg?raw'
import sceneEmptyCourses from '@/assets/scenes/scene-empty-courses.svg'

type LoadState = 'loading' | 'ready' | 'error' | 'guest'

interface QuickTile {
  title: string
  desc: string
  icon: string
  route: string
}

interface LearningOverview {
  total: number
  finished: number
  duration: number
}

const FALLBACK_COVER = '/resource/course-covers/course_web_development_01.jpg'

const router = useRouter()
const userStore = useUserStore()

const learningState = ref<LoadState>('loading')
const learning = ref<LearningLesson | null>(null)
const overview = ref<LearningOverview | null>(null)
const recommendState = ref<LoadState>('loading')
const recommend = ref<RecommendCourse[]>([])

const tiles: QuickTile[] = [
  {
    title: '课程中心',
    desc: '按分类浏览与检索全部课程',
    icon: iconCourses,
    route: '/portal/courses',
  },
  {
    title: 'AI 助手',
    desc: '带着问题对话，边学边问',
    icon: iconAiChat,
    route: '/portal/ai-chat',
  },
  {
    title: '知识图谱',
    desc: '看知识点与学习轨迹的关系',
    icon: iconGraph,
    route: '/portal/knowledge-graph',
  },
  {
    title: '学习社区',
    desc: '提问与回答，和同学一起解决',
    icon: iconCommunity,
    route: '/portal/community',
  },
  {
    title: '购物车',
    desc: '管理待购买课程并完成结算',
    icon: iconCart,
    route: '/portal/cart',
  },
  {
    title: '我的课程',
    desc: '继续学习并查看学习记录',
    icon: iconProfile,
    route: '/portal/my/lessons',
  },
]

const displayName = computed(() => userStore.userInfo?.username || '同学')

const loggedIn = computed(() => !!userStore.token)

const progressPercent = computed(() => {
  const current = learning.value
  if (!current) return 0
  const total = Number(current.totalDuration || 0)
  const studied = Number(current.studiedDuration || 0)
  if (total <= 0) return current.finish ? 100 : 0
  return Math.min(100, Math.max(0, Math.round((studied / total) * 100)))
})

onMounted(() => {
  void loadNowLearning()
  void loadOverview()
  void loadRecommend()
})

async function loadNowLearning() {
  if (!loggedIn.value) {
    learning.value = null
    learningState.value = 'guest'
    return
  }
  learningState.value = 'loading'
  try {
    const res = (await getNowLearning()) as unknown as { data?: LearningLesson }
    learning.value = res?.data ?? null
    learningState.value = 'ready'
  } catch {
    learning.value = null
    learningState.value = 'error'
  }
}

async function loadOverview() {
  if (!loggedIn.value) {
    overview.value = null
    return
  }
  try {
    const res = (await getMyLessons({ pageNo: 1, pageSize: 50 })) as unknown as {
      data?: PageResult<LearningLesson>
    }
    const list = res?.data?.list
    if (!Array.isArray(list) || !list.length) {
      overview.value = null
      return
    }
    overview.value = {
      total: Number(res?.data?.total ?? list.length),
      finished: list.filter((item) => item.finish).length,
      duration: list.reduce((sum, item) => sum + Number(item.studiedDuration || 0), 0),
    }
  } catch {
    overview.value = null
  }
}

async function loadRecommend() {
  recommendState.value = 'loading'
  try {
    const best = (await getRecommendBest()) as unknown as { data?: RecommendCourse[] }
    if (Array.isArray(best?.data) && best.data.length) {
      recommend.value = best.data
      recommendState.value = 'ready'
      return
    }
    recommend.value = await fetchNewRecommend()
    recommendState.value = 'ready'
  } catch {
    try {
      recommend.value = await fetchNewRecommend()
      recommendState.value = 'ready'
    } catch {
      recommend.value = []
      recommendState.value = 'error'
    }
  }
}

async function fetchNewRecommend(): Promise<RecommendCourse[]> {
  const res = (await getRecommendNew()) as unknown as { data?: RecommendCourse[] }
  return Array.isArray(res?.data) ? res.data : []
}

function coverOf(course: RecommendCourse) {
  return course.coverImg || FALLBACK_COVER
}

function priceLabel(course: RecommendCourse) {
  const price = Number(course.price || 0)
  return price > 0 ? `¥${price.toFixed(2)}` : '免费'
}

function isFree(course: RecommendCourse) {
  return Number(course.price || 0) <= 0
}

function formatDuration(seconds: number) {
  const value = Number(seconds || 0)
  if (value <= 0) return '0 分钟'
  const hours = Math.floor(value / 3600)
  const minutes = Math.round((value % 3600) / 60)
  if (hours > 0) return minutes > 0 ? `${hours} 小时 ${minutes} 分` : `${hours} 小时`
  return `${minutes} 分钟`
}

function openCourse(courseId: number) {
  router.push(`/portal/course/${courseId}`)
}

/** 学习概览的三个指标，用于在概览条里统一渲染 */
const overviewItems = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { key: 'total', label: '在学课程', value: `${data.total}`, unit: '门', icon: Reading as Component },
    { key: 'finished', label: '已完成', value: `${data.finished}`, unit: '门', icon: Trophy as Component },
    {
      key: 'duration',
      label: '学习时长',
      value: formatDuration(data.duration),
      unit: '',
      icon: Clock as Component,
    },
  ]
})
</script>

<template>
  <div class="consumer-page nm-shell">
    <!-- 欢迎头部 -->
    <header class="welcome nm-card nm-card--invert">
      <div class="welcome__copy">
        <span class="nm-kicker">学习导航</span>
        <h1 class="nm-h1">你好，{{ displayName }}</h1>
        <p class="nm-lede">
          这里是你的学习控制台：接着上次的进度继续，或者从推荐课程里挑一门新的开始。
        </p>
      </div>
      <div class="welcome__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
          浏览课程
          <el-icon :size="15"><ArrowRight /></el-icon>
        </button>
        <button
          v-if="loggedIn"
          type="button"
          class="nm-btn"
          @click="router.push('/portal/my/lessons')"
        >
          我的课程
        </button>
        <button v-else type="button" class="nm-btn" @click="router.push('/portal/login')">
          登录后同步进度
        </button>
      </div>
    </header>

    <!-- 继续学习 -->
    <section class="block">
      <div class="nm-section-head block-head">
        <div class="nm-section-head__copy">
          <span class="nm-kicker">继续学习</span>
          <h2 class="nm-h3">从上次停下的地方接着看</h2>
        </div>
        <button
          v-if="learningState === 'ready' && learning"
          type="button"
          class="nm-btn nm-btn--ghost nm-btn--sm"
          @click="router.push('/portal/my/lessons')"
        >
          全部学习记录
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>

      <div v-if="learningState === 'loading'" class="continue-skeleton nm-card nm-card--pad">
        <span class="nm-skeleton sk-cover"></span>
        <div class="sk-copy">
          <span class="nm-skeleton sk-line sk-line--xs"></span>
          <span class="nm-skeleton sk-line sk-line--lg"></span>
          <span class="nm-skeleton sk-line sk-line--md"></span>
          <span class="nm-skeleton sk-line sk-line--sm"></span>
        </div>
      </div>

      <div v-else-if="learningState === 'guest'" class="nm-state">
        <span class="nm-state__icon">
          <el-icon :size="24"><Reading /></el-icon>
        </span>
        <p class="nm-state__title">登录后查看学习进度</p>
        <p class="nm-state__desc">
          登录即可同步「继续学习」、学习时长与已完成课程，换设备也不会丢进度。
        </p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/login')">
            去登录
            <el-icon :size="15"><ArrowRight /></el-icon>
          </button>
          <button type="button" class="nm-btn" @click="router.push('/portal/courses')">
            先看看课程
          </button>
        </div>
      </div>

      <div v-else-if="learningState === 'error'" class="nm-state">
        <span class="nm-state__icon">
          <el-icon :size="24"><WarningFilled /></el-icon>
        </span>
        <p class="nm-state__title">学习进度暂时取不到</p>
        <p class="nm-state__desc">学习服务未连接或网络异常，稍后重试即可，课程数据不会丢失。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary" @click="loadNowLearning()">
            <el-icon :size="15"><Refresh /></el-icon>
            重试
          </button>
        </div>
      </div>

      <div v-else-if="!learning" class="nm-state">
        <img class="state-art" :src="sceneEmptyCourses" alt="" />
        <p class="nm-state__title">还没有在学的课程</p>
        <p class="nm-state__desc">选一门感兴趣的课程开始，学习进度会自动记录在这里。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
            去选课
            <el-icon :size="15"><ArrowRight /></el-icon>
          </button>
        </div>
      </div>

      <article v-else class="nm-card nm-card--pad continue-card">
        <img class="continue__cover" :src="learning.coverImg || FALLBACK_COVER" :alt="learning.courseName" />
        <div class="continue__copy">
          <h3 class="continue__title">{{ learning.courseName }}</h3>
          <p class="continue__meta">
            <span>上次学到：{{ learning.lastSectionName || '第一章' }}</span>
            <span v-if="learning.learnDate">最近学习 {{ learning.learnDate }}</span>
          </p>
          <div class="continue__progress">
            <div class="progress-track">
              <span class="progress-fill" :style="{ width: `${progressPercent}%` }"></span>
            </div>
            <span class="progress-value nm-num">{{ progressPercent }}%</span>
          </div>
          <div class="continue__foot">
            <button
              type="button"
              class="nm-btn nm-btn--primary"
              @click="openCourse(learning.courseId)"
            >
              继续学习
              <el-icon :size="15"><ArrowRight /></el-icon>
            </button>
            <span class="continue__hint nm-num">
              已学 {{ formatDuration(learning.studiedDuration) }} / 共
              {{ formatDuration(learning.totalDuration) }}
            </span>
          </div>
        </div>
      </article>
    </section>

    <!-- 学习概览 -->
    <section v-if="overviewItems.length" class="block">
      <div class="overview nm-card nm-card--pad">
        <div v-for="item in overviewItems" :key="item.key" class="overview__item">
          <span class="overview__icon">
            <el-icon :size="15"><component :is="item.icon" /></el-icon>
          </span>
          <p class="overview__label">{{ item.label }}</p>
          <p class="overview__value nm-num">
            {{ item.value }}<em v-if="item.unit">{{ item.unit }}</em>
          </p>
        </div>
      </div>
    </section>

    <!-- 快捷入口 -->
    <section class="block">
      <div class="nm-section-head block-head">
        <div class="nm-section-head__copy">
          <span class="nm-kicker">快捷入口</span>
          <h2 class="nm-h3">常去的六个地方</h2>
        </div>
      </div>

      <div class="tile-grid">
        <button
          v-for="tile in tiles"
          :key="tile.route"
          type="button"
          class="nm-card nm-card--hover tile"
          @click="router.push(tile.route)"
        >
          <span class="tile__icon" aria-hidden="true" v-html="tile.icon" />
          <span class="tile__body">
            <span class="tile__title">{{ tile.title }}</span>
            <span class="tile__desc">{{ tile.desc }}</span>
          </span>
          <span class="tile__arrow" aria-hidden="true">
            <el-icon :size="14"><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
    </section>

    <!-- 推荐课程 -->
    <section class="block">
      <div class="nm-section-head block-head">
        <div class="nm-section-head__copy">
          <span class="nm-kicker">推荐课程</span>
          <h2 class="nm-h3">口碑最好的几门课</h2>
        </div>
        <button
          type="button"
          class="nm-btn nm-btn--ghost nm-btn--sm"
          @click="router.push('/portal/courses')"
        >
          查看全部
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>

      <div v-if="recommendState === 'loading'" class="nm-auto-grid">
        <div v-for="n in 4" :key="n" class="nm-card course-skeleton">
          <span class="nm-skeleton sk-media"></span>
          <div class="course-skeleton__body">
            <span class="nm-skeleton sk-line sk-line--lg"></span>
            <span class="nm-skeleton sk-line sk-line--sm"></span>
            <span class="nm-skeleton sk-line sk-line--md"></span>
          </div>
        </div>
      </div>

      <div v-else-if="recommendState === 'error'" class="nm-state">
        <span class="nm-state__icon">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </span>
        <p class="nm-state__title">推荐课程加载失败</p>
        <p class="nm-state__desc">推荐服务暂时不可用，可以重试，或直接去课程中心按分类浏览。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary" @click="loadRecommend()">
            <el-icon :size="15"><Refresh /></el-icon>
            重试
          </button>
          <button type="button" class="nm-btn" @click="router.push('/portal/courses')">
            去课程中心
          </button>
        </div>
      </div>

      <div v-else-if="!recommend.length" class="nm-state">
        <img class="state-art" :src="sceneEmptyCourses" alt="" />
        <p class="nm-state__title">暂时没有推荐课程</p>
        <p class="nm-state__desc">推荐池还在积累数据，先去课程中心按方向挑一门吧。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
            去选课
            <el-icon :size="15"><ArrowRight /></el-icon>
          </button>
        </div>
      </div>

      <div v-else class="nm-auto-grid">
        <article
          v-for="course in recommend"
          :key="course.id"
          class="nm-card nm-card--hover course-card"
          tabindex="0"
          role="link"
          @click="openCourse(course.id)"
          @keydown.enter="openCourse(course.id)"
        >
          <div class="course-card__media">
            <img :src="coverOf(course)" :alt="course.name" loading="lazy" />
            <span
              v-if="isFree(course)"
              class="nm-badge nm-badge--success course-card__flag"
            >
              免费
            </span>
            <span
              v-else-if="course.categoryName"
              class="nm-badge nm-badge--neutral course-card__flag"
            >
              {{ course.categoryName }}
            </span>
          </div>
          <div class="course-card__body">
            <h3 class="course-card__title">{{ course.name }}</h3>
            <p class="course-card__teacher">{{ course.teacherName || '官方教研组' }}</p>
            <div class="course-card__foot">
              <span :class="['course-card__price', 'nm-num', { 'is-free': isFree(course) }]">
                {{ priceLabel(course) }}
              </span>
              <span class="course-card__sales nm-num">{{ course.sales || 0 }} 人在学</span>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.consumer-page {
  padding-block: 12px clamp(48px, 6vw, 80px);
}

/* --- 欢迎头部 --- */
.welcome {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding: clamp(22px, 3vw, 34px);
  margin-bottom: clamp(28px, 3.6vw, 46px);
}

.welcome__copy {
  display: grid;
  gap: 10px;
  max-width: 58ch;
}

.welcome__copy h1 {
  letter-spacing: -0.03em;
}

.welcome__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* --- 区块 --- */
.block + .block {
  margin-top: clamp(34px, 4.4vw, 56px);
}

.block-head {
  align-items: flex-end;
  margin-bottom: clamp(16px, 2vw, 24px);
}

/* --- 继续学习 --- */
.continue-card {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: clamp(18px, 2.4vw, 28px);
  align-items: center;
}

.continue__cover {
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
  border-radius: var(--nm-r);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.continue__copy {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.continue__title {
  font-size: clamp(1.125rem, 1.6vw, 1.375rem);
  font-weight: 740;
  line-height: 1.35;
  color: var(--nm-ink);
}

.continue__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.continue__meta span + span::before {
  content: '·';
  margin: 0 8px;
  color: var(--nm-ink-4);
}

.continue__progress {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 420px;
}

.progress-track {
  position: relative;
  flex: 1;
  height: 6px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
  transition: width var(--nm-dur-slow) var(--nm-ease);
}

.progress-value {
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-ink);
  min-width: 42px;
  text-align: right;
}

.continue__foot {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.continue__hint {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.continue-skeleton {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: clamp(18px, 2.4vw, 28px);
  align-items: center;
}

.sk-cover {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: var(--nm-r);
}

.sk-copy {
  display: grid;
  gap: 12px;
}

.sk-line {
  height: 12px;
}

.sk-line--lg {
  width: 64%;
}

.sk-line--md {
  width: 46%;
}

.sk-line--sm {
  width: 28%;
}

.sk-line--xs {
  width: 16%;
}

/* --- 学习概览 --- */
.overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(16px, 2vw, 28px);
}

.overview__item {
  display: grid;
  gap: 6px;
  padding-left: clamp(0px, 2vw, 22px);
  border-left: 1px solid var(--nm-line);
}

.overview__item:first-child {
  padding-left: 0;
  border-left: 0;
}

.overview__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--nm-r-sm);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.overview__label {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.overview__value {
  font-size: clamp(1.25rem, 2vw, 1.6rem);
  font-weight: 760;
  line-height: 1.1;
  color: var(--nm-ink);
}

.overview__value em {
  font-style: normal;
  margin-left: 4px;
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  color: var(--nm-ink-3);
}

/* --- 快捷入口 --- */
.tile-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.tile {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  text-align: left;
  cursor: pointer;
}

.tile__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--nm-r);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
}

.tile__icon :deep(svg) {
  width: 22px;
  height: 22px;
}

.tile__body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.tile__title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.tile__desc {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.tile__arrow {
  color: var(--nm-ink-4);
  transition: transform var(--nm-dur) var(--nm-ease), color var(--nm-dur) var(--nm-ease);
}

.tile:hover .tile__arrow {
  transform: translateX(4px);
  color: var(--nm-accent);
}

/* --- 推荐课程 --- */
.course-card {
  display: grid;
  overflow: hidden;
  cursor: pointer;
}

.course-card__media {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--nm-surface-3);
}

.course-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-card__flag {
  position: absolute;
  top: 10px;
  left: 10px;
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
}

.course-card__body {
  display: grid;
  gap: 8px;
  padding: 16px 18px 18px;
}

.course-card__title {
  font-size: 0.9375rem;
  font-weight: 700;
  line-height: 1.45;
  color: var(--nm-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.course-card__teacher {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.course-card__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--nm-line);
}

.course-card__price {
  font-size: 1rem;
  font-weight: 750;
  color: var(--nm-ink);
}

.course-card__price.is-free {
  color: var(--nm-success);
  font-family: var(--nm-font);
}

.course-card__sales {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.course-skeleton {
  display: grid;
  overflow: hidden;
}

.sk-media {
  width: 100%;
  aspect-ratio: 16 / 10;
  border-radius: 0;
}

.course-skeleton__body {
  display: grid;
  gap: 10px;
  padding: 16px 18px 18px;
}

/* --- 空态插画 --- */
.state-art {
  width: 152px;
  height: auto;
  opacity: 0.9;
}

/* --- 响应式 --- */
@media (max-width: 1100px) {
  .tile-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 860px) {
  .welcome {
    flex-direction: column;
    align-items: flex-start;
  }

  .continue-card,
  .continue-skeleton {
    grid-template-columns: minmax(0, 1fr);
  }

  .overview {
    grid-template-columns: minmax(0, 1fr);
  }

  .overview__item {
    padding-left: 0;
    padding-top: 14px;
    border-left: 0;
    border-top: 1px solid var(--nm-line);
  }

  .overview__item:first-child {
    padding-top: 0;
    border-top: 0;
  }
}

@media (max-width: 620px) {
  .tile-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .tile {
    padding: 16px;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Clock,
  Collection,
  Lock,
  MagicStick,
  ShoppingCart,
  Star,
  TrendCharts,
  User,
  VideoPlay,
} from '@element-plus/icons-vue'
import {
  getCourseBaseInfo,
  getCourseCatalogs,
  getCourseTeachers,
  getRecommendBest,
} from '@/api/course'
import { checkCourseEnrolled, countLessonLearners } from '@/api/learning'
import { addToCart, enrollFreeCourse } from '@/api/trade'
import {
  buildCourseLabels,
  buildCourseSummary,
  courseMissionPanels as missionPanels,
  courseOutcomeDeck as courseOutcomes,
  decorateCourse,
  pickCourseCover,
} from '@/content/courseShowcase'
import { useRevealObserver } from '@/composables/useRevealObserver'
import { formatPrice } from '@/utils/format'
import { handleImageError } from '@/utils/imageFallback'
import { friendlyErrorMessage } from '@/utils/errorMessage'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import type {
  CatalogItem,
  CourseDetailInfo,
  RecommendCourse,
  SectionItem,
  TeacherInfo,
} from '@/types'

function onImageError(event: Event) {
  handleImageError(event, { kind: 'cover' })
}

function onAvatarError(event: Event) {
  handleImageError(event, { kind: 'avatar' })
}

/** 后端可选字段：类型里没有声明，但接口可能返回，读取前统一收口 */
type CourseDetailModel = CourseDetailInfo & {
  rating?: number | string
  level?: string
  students?: number
  totalLessons?: number
  totalHours?: number
  tags?: string[]
  description?: string
  originalPrice?: number
  originPrice?: number
  marketPrice?: number
}

type TabKey = 'intro' | 'catalog' | 'teacher' | 'reviews'

interface IncludeItem {
  label: string
  value: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'intro', label: '课程介绍' },
  { key: 'catalog', label: '目录' },
  { key: 'teacher', label: '讲师' },
  { key: 'reviews', label: '评价' },
]

const audiences = [
  '有基础语法经验、想补齐工程实践能力的学习者',
  '准备转岗或晋升，需要系统梳理知识体系的人',
  '希望用完整项目作品验证学习成果的同学',
]

const supportPoints = [
  '课程详情页支持一键发起 AI 提问，随时获取学习建议',
  '目录预览、报名流程与学习入口采用统一交互结构',
  '报名后可在学习中心跟踪进度、反复回看与复盘',
]

const teacherAvatarPool = [
  '/resource/user-avatars/avatar_teacher_male_01.jpg',
  '/resource/user-avatars/avatar_teacher_female_01.jpg',
  '/resource/user-avatars/avatar_student_male_01.jpg',
  '/resource/user-avatars/avatar_student_female_01.jpg',
]

/* ---------------- 状态 ---------------- */
const loading = ref(true)
const loadError = ref('')
const notFound = ref(false)
const course = ref<CourseDetailModel | null>(null)

const catalogsLoading = ref(true)
const catalogsError = ref('')
const catalogs = ref<CatalogItem[]>([])

const teachersLoading = ref(true)
const teachersError = ref('')
const teachers = ref<TeacherInfo[]>([])

const learnerCount = ref(0)
const isEnrolled = ref(false)
const carting = ref(false)
const enrolling = ref(false)

const relatedLoading = ref(true)
const relatedError = ref('')
const related = ref<RecommendCourse[]>([])

const activeTab = ref<TabKey>('intro')
const expandedChapters = ref<number[]>([])

/* ---------------- 派生数据 ---------------- */
const courseId = computed(() => Number(route.params.id) || 0)

const cover = computed(() => {
  const current = course.value
  if (!current) {
    return ''
  }
  return current.coverImg || pickCourseCover(current.name, current.categoryName, courseId.value)
})

const introduction = computed(() => course.value?.introduction || '')

const courseLabels = computed<string[]>(() => {
  const tags = course.value?.tags
  if (tags && tags.length) {
    return tags
  }
  return buildCourseLabels(course.value?.name ?? '', course.value?.categoryName ?? '')
})

const price = computed(() => Number(course.value?.price) || 0)

const originalPrice = computed(() => {
  const current = course.value
  if (!current) {
    return 0
  }
  const candidates = [current.originalPrice, current.originPrice, current.marketPrice]
  const hit = candidates.find((value) => typeof value === 'number' && value > price.value)
  return typeof hit === 'number' ? hit : 0
})

const discountLabel = computed(() => {
  if (!originalPrice.value || !price.value) {
    return ''
  }
  const discount = Math.round((price.value / originalPrice.value) * 100) / 10
  return discount > 0 && discount < 10 ? `${discount} 折` : ''
})

const priceLabel = computed(() => formatPrice(price.value))
const originalPriceLabel = computed(() => formatPrice(originalPrice.value, { zeroAsFree: false }))

const totalLessons = computed(
  () =>
    Number(course.value?.totalLessons) ||
    catalogs.value.reduce((sum, chapter) => sum + (chapter.children?.length ?? 0), 0)
)

const totalDuration = computed(() =>
  catalogs.value.reduce((sum, chapter) => sum + chapterDuration(chapter), 0)
)

const freeLessonCount = computed(() =>
  catalogs.value.reduce(
    (sum, chapter) => sum + (chapter.children ?? []).filter((lesson) => lesson.free).length,
    0
  )
)

const displayLearners = computed(
  () => Number(course.value?.students) || Number(course.value?.sales) || learnerCount.value || 0
)

const ratingLabel = computed(() => {
  const value = course.value?.rating
  if (value === undefined || value === null || value === '') {
    return ''
  }
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num.toFixed(1) : ''
})

const levelLabel = computed(() => String(course.value?.level || '通用'))

const includes = computed<IncludeItem[]>(() => [
  { label: '课时数', value: `${totalLessons.value} 节` },
  { label: '总时长', value: formatDuration(totalDuration.value) },
  { label: '难度', value: levelLabel.value },
  { label: '有效期', value: '不限时' },
  { label: '免费试看', value: `${freeLessonCount.value} 节` },
])

const primaryActionLabel = computed(() => {
  if (isEnrolled.value) {
    return '继续学习'
  }
  return price.value > 0 ? '加入购物车' : '立即报名'
})

const allChaptersOpen = computed(
  () => catalogs.value.length > 0 && expandedChapters.value.length === catalogs.value.length
)

const relatedCourses = computed(() =>
  related.value.map((item, index) => decorateCourse(item, index))
)

/* ---------------- 生命周期 ---------------- */
useRevealObserver('.course-detail-page .nm-reveal', { once: true, threshold: 0.06 })

onMounted(() => {
  void loadAll()
})

watch(courseId, (value, previous) => {
  if (!value || value === previous) {
    return
  }
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  void loadAll()
})

/* ---------------- 数据加载 ---------------- */
function unwrap<T>(payload: unknown): T | null {
  if (payload === null || payload === undefined) {
    return null
  }
  if (typeof payload === 'object' && 'data' in payload) {
    const value = (payload as { data?: unknown }).data
    return (value ?? null) as T | null
  }
  return payload as T
}

async function loadAll() {
  await loadCourse()
  if (!course.value) {
    return
  }
  await Promise.all([loadCatalogs(), loadTeachers(), loadSignals(), loadRelated()])
}

async function loadCourse() {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  course.value = null
  catalogs.value = []
  teachers.value = []
  related.value = []
  expandedChapters.value = []
  activeTab.value = 'intro'

  try {
    const payload: unknown = await getCourseBaseInfo(courseId.value)
    const data = unwrap<Partial<CourseDetailModel>>(payload)
    if (!data || (!data.id && !data.name)) {
      notFound.value = true
    } else {
      course.value = normalizeCourse(data)
    }
  } catch (error) {
    loadError.value = friendlyErrorMessage(error, '课程加载失败')
  } finally {
    loading.value = false
  }
}

function normalizeCourse(raw: Partial<CourseDetailModel>): CourseDetailModel {
  const name = raw.name || '未命名课程'
  const categoryName = raw.categoryName || ''

  return {
    ...raw,
    id: Number(raw.id) || courseId.value,
    name,
    coverImg: raw.coverImg || '',
    teacherName: raw.teacherName || '',
    price: Number(raw.price) || 0,
    sales: Number(raw.sales) || 0,
    categoryId: Number(raw.categoryId) || 0,
    categoryName,
    introduction:
      raw.introduction ||
      raw.description ||
      buildCourseSummary(name, categoryName, courseId.value),
    catalogs: Array.isArray(raw.catalogs) ? raw.catalogs : [],
    teachers: Array.isArray(raw.teachers) ? raw.teachers : [],
  }
}

/**
 * 把后端目录接口的真实结构规整成页面使用的 CatalogItem 树。
 *
 * 后端 `/cs/courses/{id}/catalogs` 返回的是一个对象：
 *   { id, name, coverUrl, sections: 总小节数, teacherName, teacherIcon,
 *     chapters: [{ id, index, name, sections: [{ id, name, index, type,
 *                  mediaDuration, trailer, subjectNum, hasTest, moment }] }] }
 * 页面模型则期望「章 -> children(节)」的 CatalogItem[]。此前直接把该对象当数组用，
 * 结果章节永远渲染不出来、每章都显示「0 节」。
 */
function normalizeCatalogs(payload: unknown): CatalogItem[] {
  const unwrapOnce = (v: unknown): any =>
    v && typeof v === 'object' && 'data' in (v as Record<string, unknown>)
      ? (v as Record<string, unknown>).data
      : v

  const raw = unwrapOnce(payload)
  if (!raw || typeof raw !== 'object') return []

  const record = raw as Record<string, unknown>

  // 形态 A：{ chapters: [...] }；形态 B：接口直接返回 CatalogItem[]（章里叫 children）
  const chapters = Array.isArray(record.chapters)
    ? record.chapters
    : Array.isArray(raw)
      ? (raw as unknown[])
      : []

  return chapters.map((chapter, chapterIndex) => {
    const ch = (chapter ?? {}) as Record<string, unknown>
    const sections = Array.isArray(ch.sections)
      ? ch.sections
      : Array.isArray(ch.children)
        ? ch.children
        : []
    return {
      id: Number(ch.id) || chapterIndex + 1,
      courseId: Number(record.id) || Number(courseId.value) || 0,
      name: String(ch.name ?? `第 ${chapterIndex + 1} 章`),
      orderNum: Number(ch.index) || chapterIndex + 1,
      children: (sections as unknown[]).map((section, sectionIndex) => {
        const sec = (section ?? {}) as Record<string, unknown>
        return {
          id: Number(sec.id) || 0,
          catalogId: Number(ch.id) || 0,
          name: String(sec.name ?? `第 ${sectionIndex + 1} 节`),
          orderNum: Number(sec.index) || sectionIndex + 1,
          mediaId: Number(sec.mediaId) || 0,
          // 后端字段是 mediaDuration（单位：分钟），页面模型叫 duration
          duration: Number(sec.mediaDuration) || Number(sec.duration) || 0,
          // trailer=true 表示该节可免费试看
          free: Boolean(sec.trailer ?? sec.free),
          videoType: Number(sec.type) || 0,
        } satisfies SectionItem
      }),
    } satisfies CatalogItem
  })
}

async function loadCatalogs() {
  catalogsLoading.value = true
  catalogsError.value = ''
  try {
    const payload: unknown = await getCourseCatalogs(courseId.value)
    catalogs.value = normalizeCatalogs(payload)
    if (!catalogs.value.length) {
      catalogsError.value = '课程目录暂未发布'
    }
  } catch (error) {
    catalogs.value = []
    catalogsError.value = friendlyErrorMessage(error, '目录加载失败')
  } finally {
    catalogsLoading.value = false
  }

  if (!expandedChapters.value.length && catalogs.value.length) {
    expandedChapters.value = [catalogs.value[0].id]
  }
}

async function loadTeachers() {
  teachersLoading.value = true
  teachersError.value = ''

  try {
    const payload: unknown = await getCourseTeachers(courseId.value)
    const list = unwrap<TeacherInfo[]>(payload)
    teachers.value = Array.isArray(list) ? list : []
    if (!teachers.value.length) {
      teachersError.value = '讲师信息暂未完善'
    }
  } catch (error) {
    teachers.value = []
    teachersError.value = friendlyErrorMessage(error, '讲师信息加载失败')
  } finally {
    teachersLoading.value = false
  }
}

async function loadSignals() {
  const tasks: Array<Promise<unknown>> = [countLessonLearners(courseId.value)]
  if (userStore.token) {
    tasks.push(checkCourseEnrolled(courseId.value))
  }

  const results = await Promise.allSettled(tasks)

  const countResult = results[0]
  if (countResult && countResult.status === 'fulfilled') {
    learnerCount.value = Number(unwrap<number>(countResult.value)) || 0
  }

  const enrolledResult = results[1]
  isEnrolled.value = Boolean(
    enrolledResult && enrolledResult.status === 'fulfilled'
      ? unwrap<boolean>(enrolledResult.value)
      : false
  )
}

async function loadRelated() {
  relatedLoading.value = true
  relatedError.value = ''

  try {
    const payload: unknown = await getRecommendBest()
    const list = unwrap<RecommendCourse[]>(payload)
    related.value = (Array.isArray(list) ? list : [])
      .filter((item) => Number(item.id) !== courseId.value)
      .slice(0, 3)
    if (!related.value.length) {
      relatedError.value = '暂时没有更多推荐课程'
    }
  } catch (error) {
    related.value = []
    relatedError.value = friendlyErrorMessage(error, '推荐课程加载失败')
  } finally {
    relatedLoading.value = false
  }
}

/* ---------------- 交互 ---------------- */
function goBack() {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
    return
  }
  void router.push('/portal/courses')
}

function scrollToBody() {
  if (typeof window === 'undefined') {
    return
  }
  const target = document.getElementById('course-body')
  if (!target) {
    return
  }
  const top = target.getBoundingClientRect().top + window.scrollY - 96
  window.scrollTo({ top: Math.max(top, 0), behavior: 'smooth' })
}

function openCatalog() {
  activeTab.value = 'catalog'
  scrollToBody()
}

function openAIPlanner() {
  const current = course.value
  if (!current) {
    return
  }
  void router.push({
    path: '/portal/ai-chat',
    query: {
      prompt: `请基于课程《${current.name}》为我生成学习路线、练习建议和提问清单。`,
      autostart: '1',
    },
  })
}

function isChapterOpen(id: number) {
  return expandedChapters.value.includes(id)
}

function toggleChapter(id: number) {
  expandedChapters.value = isChapterOpen(id)
    ? expandedChapters.value.filter((item) => item !== id)
    : [...expandedChapters.value, id]
}

function toggleAllChapters() {
  expandedChapters.value = allChaptersOpen.value ? [] : catalogs.value.map((chapter) => chapter.id)
}

async function handlePrimaryAction() {
  const current = course.value
  if (!current) {
    return
  }

  if (isEnrolled.value) {
    void router.push('/portal/my/lessons')
    return
  }

  if (!userStore.token) {
    void router.push({ path: '/portal/login', query: { redirect: route.fullPath } })
    return
  }

  if (price.value > 0) {
    await handleAddToCart()
    return
  }

  await handleEnrollFree()
}

async function handleAddToCart() {
  if (!course.value || carting.value) {
    return
  }
  carting.value = true
  try {
    await addToCart(courseId.value)
    ElMessage.success('已加入购物车')
  } catch {
    // 失败提示由 request 拦截器统一给出，这里不再重复弹窗
  } finally {
    carting.value = false
  }
}

async function handleBuyNow() {
  if (!course.value) {
    return
  }
  if (!userStore.token) {
    void router.push({ path: '/portal/login', query: { redirect: route.fullPath } })
    return
  }
  if (price.value <= 0) {
    await handleEnrollFree()
    return
  }

  if (carting.value) {
    return
  }
  carting.value = true
  try {
    await addToCart(courseId.value)
    void router.push('/portal/cart')
  } catch {
    // 同上：错误提示由拦截器负责
  } finally {
    carting.value = false
  }
}

async function handleEnrollFree() {
  if (!course.value || enrolling.value) {
    return
  }
  enrolling.value = true
  try {
    await enrollFreeCourse(courseId.value)
    isEnrolled.value = true
    ElMessage.success('报名成功，已加入你的学习空间')
    void router.push('/portal/my/lessons')
  } catch {
    // 同上
  } finally {
    enrolling.value = false
  }
}

/* ---------------- 展示工具 ---------------- */
function chapterDuration(chapter: CatalogItem): number {
  return (chapter.children ?? []).reduce(
    (sum, lesson) => sum + (Number(lesson.duration) || 0),
    0
  )
}

function formatDuration(seconds: number): string {
  const total = Number(seconds) || 0
  if (total <= 0) {
    return '待更新'
  }
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours > 0) {
    return minutes > 0 ? `${hours} 小时 ${minutes} 分` : `${hours} 小时`
  }
  return `${minutes} 分钟`
}

function formatLessonDuration(seconds: number): string {
  const total = Number(seconds) || 0
  if (total <= 0) {
    return '—'
  }
  if (total < 60) {
    return `${total} 秒`
  }
  const minutes = Math.round(total / 60)
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} 小时 ${rest} 分` : `${hours} 小时`
}

function lessonCountOf(chapter: CatalogItem): number {
  return (chapter.children ?? []).length
}

function teacherAvatar(teacher: TeacherInfo, index: number): string {
  if (teacher.avatar) {
    return teacher.avatar
  }
  const key = teacher.name ?? ''
  let hash = 0
  for (let i = 0; i < key.length; i += 1) {
    hash = (hash * 31 + key.charCodeAt(i)) % 997
  }
  return teacherAvatarPool[(hash + index) % teacherAvatarPool.length]
}

function lessonKey(lesson: SectionItem): string {
  return `${lesson.catalogId}-${lesson.id}`
}
</script>

<template>
  <div class="course-detail-page">
    <!-- ============ 返回 / 面包屑 ============ -->
    <div class="nm-shell detail-topbar">
      <button type="button" class="nm-btn nm-btn--ghost nm-btn--sm" @click="goBack">
        <ArrowLeft />
        返回
      </button>
      <nav class="breadcrumb" aria-label="面包屑">
        <router-link to="/portal/home">首页</router-link>
        <span aria-hidden="true">/</span>
        <router-link to="/portal/courses">课程中心</router-link>
        <template v-if="course">
          <span aria-hidden="true">/</span>
          <span class="breadcrumb__current">{{ course.name }}</span>
        </template>
      </nav>
    </div>

    <!-- ============ 加载骨架 ============ -->
    <section v-if="loading" class="nm-shell detail-skeleton" aria-hidden="true">
      <div class="nm-skeleton detail-skeleton__media" />
      <div class="detail-skeleton__copy">
        <span class="nm-skeleton detail-skeleton__line detail-skeleton__line--xs" />
        <span class="nm-skeleton detail-skeleton__line detail-skeleton__line--title" />
        <span class="nm-skeleton detail-skeleton__line" />
        <span class="nm-skeleton detail-skeleton__line detail-skeleton__line--md" />
        <span class="nm-skeleton detail-skeleton__line detail-skeleton__line--price" />
      </div>
    </section>

    <!-- ============ 加载失败 ============ -->
    <section v-else-if="loadError" class="nm-shell detail-state">
      <EmptyState
        type="error"
        title="课程信息加载失败"
        description="后端课程服务暂时不可用。可以重新加载，或先回到课程中心继续浏览其他内容。"
        @action="loadAll"
      >
        <template #action>
          <button type="button" class="nm-btn nm-btn--primary" @click="loadAll">重新加载</button>
          <button type="button" class="nm-btn" @click="router.push('/portal/courses')">
            返回课程中心
          </button>
        </template>
      </EmptyState>
    </section>

    <!-- ============ 课程不存在 ============ -->
    <section v-else-if="notFound || !course" class="nm-shell detail-state">
      <EmptyState
        type="folder"
        title="课程不存在或已下架"
        description="这门课程可能已经下架，链接也可能已经失效。回到课程中心可以继续寻找合适的课程。"
      >
        <template #action>
          <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
            返回课程中心
          </button>
          <button type="button" class="nm-btn nm-btn--ghost" @click="loadAll">重新加载</button>
        </template>
      </EmptyState>
    </section>

    <!-- ============ 课程内容 ============ -->
    <template v-else>
      <section class="detail-hero">
        <div class="nm-shell hero-grid">
          <figure class="hero-media nm-reveal">
            <img :src="cover" :alt="course.name" :data-fallback-seed="courseId" @error="onImageError" />
            <figcaption v-if="course.categoryName" class="hero-media__badge nm-badge">
              {{ course.categoryName }}
            </figcaption>
          </figure>

          <div class="hero-copy nm-reveal nm-delay-1">
            <span class="nm-kicker">Course Detail</span>
            <h1 class="nm-h1 hero-title">{{ course.name }}</h1>
            <p class="nm-lede hero-lede">{{ introduction }}</p>

            <dl class="hero-facts">
              <div class="hero-fact">
                <dt><Star />评分</dt>
                <dd class="nm-num">{{ ratingLabel || '暂无' }}</dd>
              </div>
              <div class="hero-fact">
                <dt><TrendCharts />学习人数</dt>
                <dd class="nm-num">{{ displayLearners }}</dd>
              </div>
              <div class="hero-fact">
                <dt><Collection />课时</dt>
                <dd class="nm-num">{{ totalLessons }} 节</dd>
              </div>
              <div class="hero-fact">
                <dt><Clock />时长</dt>
                <dd class="nm-num">{{ formatDuration(totalDuration) }}</dd>
              </div>
            </dl>

            <div class="hero-price">
              <strong class="nm-num hero-price__now">{{ priceLabel }}</strong>
              <s v-if="originalPrice" class="nm-num hero-price__was">{{ originalPriceLabel }}</s>
              <span v-if="discountLabel" class="nm-badge nm-badge--danger">{{ discountLabel }}</span>
              <span v-if="isEnrolled" class="nm-badge nm-badge--success">已报名</span>
            </div>

            <div class="hero-cta">
              <button
                type="button"
                class="nm-btn nm-btn--accent nm-btn--lg"
                :disabled="carting || enrolling"
                @click="handlePrimaryAction"
              >
                <ShoppingCart v-if="!isEnrolled && price > 0" />
                <VideoPlay v-else />
                {{ primaryActionLabel }}
              </button>
              <button type="button" class="nm-btn nm-btn--lg" @click="openCatalog">查看课程目录</button>
              <button type="button" class="nm-btn nm-btn--ghost nm-btn--lg" @click="openAIPlanner">
                <MagicStick />
                让 AI 规划学习路线
              </button>
            </div>

            <ul v-if="courseLabels.length" class="hero-tags">
              <li v-for="label in courseLabels" :key="label" class="nm-tag">{{ label }}</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- ============ 主体：内容 + 购买面板 ============ -->
      <section id="course-body" class="detail-body">
        <div class="nm-shell detail-grid">
          <div class="detail-main">
            <div class="tabs" role="tablist">
              <button
                v-for="tab in tabs"
                :key="tab.key"
                type="button"
                role="tab"
                class="tab"
                :class="{ 'is-active': activeTab === tab.key }"
                :aria-selected="activeTab === tab.key"
                @click="activeTab = tab.key"
              >
                {{ tab.label }}
              </button>
            </div>

            <!-- 介绍 -->
            <div v-if="activeTab === 'intro'" class="panel nm-reveal">
              <article class="nm-card nm-card--pad panel-block">
                <span class="nm-kicker">Overview</span>
                <h2 class="nm-h3">课程介绍</h2>
                <p class="panel-text">{{ introduction }}</p>
              </article>

              <div class="nm-grid nm-grid--2">
                <article class="nm-card nm-card--pad panel-block">
                  <span class="nm-kicker">Outcomes</span>
                  <h2 class="nm-h3">学完之后</h2>
                  <ul class="check-list">
                    <li v-for="item in courseOutcomes" :key="item">
                      <Check />
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </article>

                <article class="nm-card nm-card--pad panel-block">
                  <span class="nm-kicker">Audience</span>
                  <h2 class="nm-h3">适合人群</h2>
                  <ul class="check-list">
                    <li v-for="item in audiences" :key="item">
                      <Check />
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </article>
              </div>

              <div class="nm-grid nm-grid--3">
                <article v-for="panel in missionPanels" :key="panel.title" class="nm-card nm-card--pad panel-block">
                  <strong class="panel-block__title">{{ panel.title }}</strong>
                  <p class="panel-text">{{ panel.desc }}</p>
                </article>
              </div>
            </div>

            <!-- 目录 -->
            <div v-else-if="activeTab === 'catalog'" class="panel nm-reveal">
              <div v-if="catalogsLoading" class="panel-skeleton" aria-hidden="true">
                <span v-for="i in 4" :key="i" class="nm-skeleton panel-skeleton__line" />
              </div>

              <EmptyState
                v-else-if="catalogsError"
                type="data"
                title="课程目录暂不可用"
                :description="`${catalogsError}。可以重新加载目录，或先看看课程介绍与讲师信息。`"
                @action="loadCatalogs"
              >
                <template #action>
                  <button type="button" class="nm-btn nm-btn--primary" @click="loadCatalogs">
                    重新加载目录
                  </button>
                  <button type="button" class="nm-btn nm-btn--ghost" @click="activeTab = 'intro'">
                    查看课程介绍
                  </button>
                </template>
              </EmptyState>

              <template v-else>
                <div class="catalog-bar nm-card nm-card--pad">
                  <div class="catalog-bar__copy">
                    <span class="nm-kicker">Curriculum</span>
                    <h2 class="nm-h3">{{ catalogs.length }} 个章节 · {{ totalLessons }} 节课</h2>
                    <p class="panel-text">
                      共 {{ formatDuration(totalDuration) }}，其中 {{ freeLessonCount }} 节可免费试看。
                    </p>
                  </div>
                  <button type="button" class="nm-btn nm-btn--sm" @click="toggleAllChapters">
                    {{ allChaptersOpen ? '收起全部' : '展开全部' }}
                  </button>
                </div>

                <div class="chapter-list">
                  <article
                    v-for="(chapter, index) in catalogs"
                    :key="chapter.id"
                    class="chapter nm-card"
                  >
                    <button
                      type="button"
                      class="chapter__head"
                      :aria-expanded="isChapterOpen(chapter.id)"
                      @click="toggleChapter(chapter.id)"
                    >
                      <span class="chapter__index nm-num">{{ String(index + 1).padStart(2, '0') }}</span>
                      <span class="chapter__title">
                        <strong>{{ chapter.name }}</strong>
                        <span class="chapter__meta nm-num">
                          {{ lessonCountOf(chapter) }} 节 · {{ formatDuration(chapterDuration(chapter)) }}
                        </span>
                      </span>
                      <span class="chapter__chevron" :class="{ 'is-open': isChapterOpen(chapter.id) }">
                        <ArrowRight />
                      </span>
                    </button>

                    <div class="chapter__body" :class="{ 'is-open': isChapterOpen(chapter.id) }">
                      <div class="chapter__body-inner">
                        <ul class="lesson-list">
                          <li v-for="lesson in chapter.children" :key="lessonKey(lesson)" class="lesson">
                            <span class="lesson__icon" :class="{ 'is-free': lesson.free }">
                              <VideoPlay v-if="lesson.free" />
                              <Lock v-else />
                            </span>
                            <span class="lesson__name">{{ lesson.name }}</span>
                            <span class="lesson__duration nm-num">
                              {{ formatLessonDuration(lesson.duration) }}
                            </span>
                            <span
                              class="nm-badge lesson__badge"
                              :class="lesson.free ? 'nm-badge--success' : 'nm-badge--neutral'"
                            >
                              {{ lesson.free ? '免费试看' : '报名解锁' }}
                            </span>
                          </li>
                        </ul>
                      </div>
                    </div>
                  </article>
                </div>
              </template>
            </div>

            <!-- 讲师 -->
            <div v-else-if="activeTab === 'teacher'" class="panel nm-reveal">
              <div v-if="teachersLoading" class="panel-skeleton" aria-hidden="true">
                <span v-for="i in 3" :key="i" class="nm-skeleton panel-skeleton__line" />
              </div>

              <EmptyState
                v-else-if="teachersError"
                type="data"
                title="讲师信息暂不可用"
                :description="`${teachersError}。可以重新加载，或先浏览课程目录。`"
                @action="loadTeachers"
              >
                <template #action>
                  <button type="button" class="nm-btn nm-btn--primary" @click="loadTeachers">
                    重新加载讲师
                  </button>
                  <button type="button" class="nm-btn nm-btn--ghost" @click="activeTab = 'catalog'">
                    查看课程目录
                  </button>
                </template>
              </EmptyState>

              <div v-else class="nm-grid nm-grid--2">
                <article
                  v-for="(teacher, index) in teachers"
                  :key="teacher.id"
                  class="teacher nm-card nm-card--pad"
                >
                  <img class="teacher__avatar" :src="teacherAvatar(teacher, index)" :alt="teacher.name" @error="onAvatarError" />
                  <div class="teacher__copy">
                    <strong class="teacher__name">{{ teacher.name }}</strong>
                    <span class="teacher__title">{{ teacher.title || '平台讲师' }}</span>
                    <p class="panel-text">{{ teacher.intro || '这位讲师还没有补充介绍。' }}</p>
                  </div>
                </article>

                <article class="nm-card nm-card--pad panel-block">
                  <span class="nm-kicker">Support</span>
                  <h2 class="nm-h3">这门课如何与平台协同</h2>
                  <ul class="check-list">
                    <li v-for="item in supportPoints" :key="item">
                      <Check />
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                  <button type="button" class="nm-btn nm-btn--accent nm-btn--sm panel-block__action" @click="openAIPlanner">
                    交给 AI 继续拆解
                    <ArrowRight />
                  </button>
                </article>
              </div>
            </div>

            <!-- 评价 -->
            <div v-else class="panel nm-reveal">
              <article class="nm-card nm-card--pad panel-block review-summary">
                <span class="nm-kicker">Reviews</span>
                <h2 class="nm-h3">学员评价</h2>
                <div class="review-summary__grid">
                  <div class="review-summary__item">
                    <strong class="nm-num">{{ ratingLabel || '—' }}</strong>
                    <span>综合评分</span>
                  </div>
                  <div class="review-summary__item">
                    <strong class="nm-num">{{ displayLearners }}</strong>
                    <span>学习人数</span>
                  </div>
                  <div class="review-summary__item">
                    <strong class="nm-num">{{ freeLessonCount }}</strong>
                    <span>免费试看</span>
                  </div>
                </div>
              </article>

              <EmptyState
                type="data"
                title="评价明细尚未接入"
                description="课程评价接口还没有上线，这里不会展示任何编造的评价。你可以先让 AI 帮你判断这门课是否匹配你的目标。"
              >
                <template #action>
                  <button type="button" class="nm-btn nm-btn--primary" @click="openAIPlanner">
                    让 AI 帮我判断
                    <ArrowRight />
                  </button>
                  <button type="button" class="nm-btn nm-btn--ghost" @click="activeTab = 'catalog'">
                    先看课程目录
                  </button>
                </template>
              </EmptyState>
            </div>
          </div>

          <!-- 购买面板 -->
          <aside class="buy-panel nm-card nm-card--pad nm-reveal nm-delay-2">
            <div class="buy-panel__price">
              <span class="nm-kicker nm-kicker--plain">价格</span>
              <div class="buy-panel__price-row">
                <strong class="nm-num buy-panel__now">{{ priceLabel }}</strong>
                <s v-if="originalPrice" class="nm-num buy-panel__was">{{ originalPriceLabel }}</s>
                <span v-if="discountLabel" class="nm-badge nm-badge--danger">{{ discountLabel }}</span>
              </div>
              <p class="buy-panel__hint">
                {{ price > 0 ? '加入购物车后可在购物车统一结算。' : '免费课程报名后即可开始学习。' }}
              </p>
            </div>

            <div class="buy-panel__actions">
              <button
                type="button"
                class="nm-btn nm-btn--accent buy-panel__btn"
                :disabled="carting || enrolling"
                @click="handlePrimaryAction"
              >
                <ShoppingCart v-if="!isEnrolled && price > 0" />
                <VideoPlay v-else />
                {{ primaryActionLabel }}
              </button>
              <button
                type="button"
                class="nm-btn buy-panel__btn"
                :disabled="carting || enrolling"
                @click="handleBuyNow"
              >
                {{ price > 0 ? '立即购买' : '立即报名' }}
              </button>
            </div>

            <div class="buy-panel__learners">
              <User />
              <span class="nm-num">{{ displayLearners }}</span>
              <span>人已加入学习</span>
            </div>

            <div class="buy-panel__includes">
              <h3 class="nm-kicker">课程包含</h3>
              <dl class="include-list">
                <div v-for="item in includes" :key="item.label" class="include">
                  <dt>{{ item.label }}</dt>
                  <dd>{{ item.value }}</dd>
                </div>
              </dl>
            </div>

            <button type="button" class="nm-btn nm-btn--ghost nm-btn--sm buy-panel__ai" @click="openAIPlanner">
              <MagicStick />
              让 AI 规划学习路线
            </button>
          </aside>
        </div>
      </section>

      <!-- ============ 相关推荐 ============ -->
      <section class="detail-related">
        <div class="nm-shell">
          <div class="nm-section-head">
            <div class="nm-section-head__copy">
              <span class="nm-kicker">Related</span>
              <h2 class="nm-h2">相关课程推荐</h2>
              <p class="nm-muted">基于平台热门课程的推荐结果，可作为下一步的学习参考。</p>
            </div>
          </div>

          <div v-if="relatedLoading" class="nm-grid nm-grid--3" aria-hidden="true">
            <article v-for="i in 3" :key="i" class="nm-card related-skeleton">
              <div class="nm-skeleton related-skeleton__media" />
              <div class="related-skeleton__body">
                <span class="nm-skeleton related-skeleton__line" />
                <span class="nm-skeleton related-skeleton__line related-skeleton__line--sm" />
              </div>
            </article>
          </div>

          <EmptyState
            v-else-if="relatedError"
            type="data"
            title="暂时没有推荐结果"
            :description="`${relatedError}。可以重新加载，或回到课程中心自行筛选。`"
            @action="loadRelated"
          >
            <template #action>
              <button type="button" class="nm-btn nm-btn--primary" @click="loadRelated">重新加载</button>
              <button type="button" class="nm-btn nm-btn--ghost" @click="router.push('/portal/courses')">
                浏览课程中心
              </button>
            </template>
          </EmptyState>

          <div v-else class="nm-grid nm-grid--3">
            <article
              v-for="item in relatedCourses"
              :key="item.raw.id"
              class="related-card nm-card nm-card--hover"
              @click="router.push(`/portal/course/${item.raw.id}`)"
            >
              <div class="related-card__media">
                <img :src="item.cover" :alt="item.raw.name" :data-fallback-seed="item.raw.id" loading="lazy" @error="onImageError" />
              </div>
              <div class="related-card__body">
                <h3 class="related-card__title">{{ item.raw.name }}</h3>
                <p class="related-card__desc">{{ item.summary }}</p>
                <div class="related-card__foot">
                  <span class="nm-num related-card__price">
                    {{ Number(item.raw.price) > 0 ? `¥${item.raw.price}` : '免费' }}
                  </span>
                  <span class="related-card__meta">{{ item.raw.teacherName || 'Nova 导师团' }}</span>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
/* ============================================================
   顶部返回条
   ============================================================ */
.course-detail-page {
  padding-bottom: 64px;
}

.detail-topbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding-block: 20px 4px;
}

.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
}

.breadcrumb a:hover {
  color: var(--nm-accent);
}

.breadcrumb__current {
  overflow: hidden;
  max-width: 46ch;
  color: var(--nm-ink-2);
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ============================================================
   Hero
   ============================================================ */
.detail-hero {
  padding-block: clamp(18px, 2.6vw, 34px) clamp(26px, 3.4vw, 44px);
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.92fr);
  gap: clamp(24px, 3vw, 46px);
  align-items: start;
}

.hero-media {
  position: relative;
  overflow: hidden;
  margin: 0;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface-3);
  box-shadow: var(--nm-sh-2);
}

.hero-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-media__badge {
  position: absolute;
  left: 14px;
  top: 14px;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface);
  color: var(--nm-accent);
}

.hero-copy {
  display: grid;
  gap: 14px;
  align-content: start;
}

.hero-title {
  max-width: 24ch;
}

.hero-lede {
  max-width: 56ch;
}

.hero-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid var(--nm-line);
  border-bottom: 1px solid var(--nm-line);
}

.hero-fact {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.hero-fact dt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.1em;
  color: var(--nm-ink-4);
}

.hero-fact dt svg {
  width: 13px;
  height: 13px;
}

.hero-fact dd {
  font-size: var(--nm-fs-body);
  font-weight: 700;
  color: var(--nm-ink);
}

.hero-price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

.hero-price__now {
  font-size: clamp(1.7rem, 2.4vw, 2.1rem);
  font-weight: 780;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.hero-price__was {
  color: var(--nm-ink-4);
  font-size: var(--nm-fs-body);
}

.hero-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 2px 0 0;
  padding: 0;
  list-style: none;
}

/* ============================================================
   主体两栏
   ============================================================ */
.detail-body {
  border-top: 1px solid var(--nm-line);
  padding-block: clamp(26px, 3.4vw, 44px);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: clamp(22px, 2.6vw, 36px);
  align-items: start;
}

.detail-main {
  display: grid;
  gap: 18px;
  min-width: 0;
}

/* --- Tabs --- */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--nm-line);
}

.tab {
  position: relative;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-sm);
  background: transparent;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-body);
  font-weight: 650;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    color var(--nm-dur-fast) var(--nm-ease);
}

.tab:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.tab.is-active {
  background: var(--nm-accent-soft);
  border-color: var(--nm-accent-line);
  color: var(--nm-accent);
}

.panel {
  display: grid;
  gap: 16px;
}

.panel-block {
  display: grid;
  gap: 10px;
  align-content: start;
}

.panel-block h2 {
  margin-top: 2px;
}

.panel-block__title {
  font-size: var(--nm-fs-body);
  font-weight: 700;
}

.panel-block__action {
  justify-self: start;
  margin-top: 4px;
}

.panel-text {
  color: var(--nm-ink-2);
  line-height: 1.75;
}

.check-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.check-list li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: var(--nm-ink-2);
  line-height: 1.7;
}

.check-list svg {
  width: 15px;
  height: 15px;
  margin-top: 4px;
  flex: 0 0 auto;
  color: var(--nm-accent);
}

.panel-skeleton {
  display: grid;
  gap: 12px;
  padding: 24px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
}

.panel-skeleton__line {
  height: 16px;
}

.panel-skeleton__line:nth-child(2n) {
  width: 78%;
}

/* ============================================================
   目录
   ============================================================ */
.catalog-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
}

.catalog-bar__copy {
  display: grid;
  gap: 6px;
}

.chapter-list {
  display: grid;
  gap: 12px;
}

.chapter {
  overflow: hidden;
}

.chapter__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px 18px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease);
}

.chapter__head:hover {
  background: var(--nm-surface-2);
}

.chapter__index {
  color: var(--nm-ink-4);
  font-size: var(--nm-fs-sm);
  letter-spacing: 0.08em;
}

.chapter__title {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.chapter__title strong {
  font-size: var(--nm-fs-body);
  font-weight: 700;
  color: var(--nm-ink);
}

.chapter__meta {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

.chapter__chevron {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--nm-r-xs);
  color: var(--nm-ink-3);
  transition: transform var(--nm-dur) var(--nm-ease), color var(--nm-dur-fast) var(--nm-ease);
}

.chapter__chevron svg {
  width: 15px;
  height: 15px;
}

.chapter__head:hover .chapter__chevron {
  color: var(--nm-accent);
}

.chapter__chevron.is-open {
  transform: rotate(90deg);
  color: var(--nm-accent);
}

.chapter__body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows var(--nm-dur) var(--nm-ease);
}

.chapter__body.is-open {
  grid-template-rows: 1fr;
}

.chapter__body-inner {
  overflow: hidden;
}

.lesson-list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0 18px 16px;
  list-style: none;
  border-top: 1px solid var(--nm-line);
  padding-top: 10px;
}

.lesson {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
}

.lesson + .lesson {
  border-top: 1px solid var(--nm-line);
}

.lesson__icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--nm-r-xs);
  background: var(--nm-surface-3);
  color: var(--nm-ink-3);
}

.lesson__icon.is-free {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.lesson__icon svg {
  width: 14px;
  height: 14px;
}

.lesson__name {
  min-width: 0;
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
}

.lesson__duration {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
  white-space: nowrap;
}

/* ============================================================
   讲师
   ============================================================ */
.teacher {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.teacher__avatar {
  width: 72px;
  height: 72px;
  flex: 0 0 auto;
  border-radius: var(--nm-r);
  border: 1px solid var(--nm-line);
  object-fit: cover;
  background: var(--nm-surface-3);
}

.teacher__copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.teacher__name {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.teacher__title {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.1em;
  color: var(--nm-ink-3);
}

/* ============================================================
   评价
   ============================================================ */
.review-summary__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 6px;
}

.review-summary__item {
  display: grid;
  gap: 4px;
  padding: 14px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
}

.review-summary__item strong {
  font-size: 1.4rem;
  font-weight: 750;
  color: var(--nm-ink);
}

.review-summary__item span {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

/* ============================================================
   购买面板
   ============================================================ */
.buy-panel {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 18px;
}

.buy-panel__price {
  display: grid;
  gap: 8px;
}

.buy-panel__price-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

.buy-panel__now {
  font-size: 2rem;
  font-weight: 780;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.buy-panel__was {
  color: var(--nm-ink-4);
  font-size: var(--nm-fs-sm);
}

.buy-panel__hint {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
  line-height: 1.6;
}

.buy-panel__actions {
  display: grid;
  gap: 10px;
}

.buy-panel__btn {
  width: 100%;
}

.buy-panel__learners {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 11px 14px;
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
}

.buy-panel__learners svg {
  width: 15px;
  height: 15px;
  color: var(--nm-ink-4);
}

.buy-panel__learners .nm-num {
  color: var(--nm-ink);
  font-weight: 700;
}

.buy-panel__includes {
  display: grid;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--nm-line);
}

.include-list {
  display: grid;
  gap: 9px;
}

.include {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.include dt {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
}

.include dd {
  color: var(--nm-ink);
  font-size: var(--nm-fs-sm);
  font-weight: 650;
}

.buy-panel__ai {
  justify-self: stretch;
}

/* ============================================================
   相关推荐
   ============================================================ */
.detail-related {
  border-top: 1px solid var(--nm-line);
  padding-block: clamp(36px, 4.6vw, 64px);
}

.related-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
}

.related-card__media {
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-bottom: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.related-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--nm-dur-slow) var(--nm-ease);
}

.related-card:hover .related-card__media img {
  transform: scale(1.04);
}

.related-card__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px 18px;
}

.related-card__title {
  display: -webkit-box;
  overflow: hidden;
  min-height: 2.7em;
  font-size: var(--nm-fs-body);
  font-weight: 700;
  line-height: 1.35;
  color: var(--nm-ink);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.related-card__desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.related-card__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
}

.related-card__price {
  font-size: 1.05rem;
  font-weight: 750;
  color: var(--nm-ink);
}

.related-card__meta {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

.related-skeleton {
  overflow: hidden;
}

.related-skeleton__media {
  aspect-ratio: 16 / 9;
  border-radius: 0;
}

.related-skeleton__body {
  display: grid;
  gap: 10px;
  padding: 16px 18px 18px;
}

.related-skeleton__line {
  height: 16px;
  border-radius: var(--nm-r-xs);
}

.related-skeleton__line--sm {
  width: 56%;
  height: 12px;
}

/* ============================================================
   骨架 / 状态
   ============================================================ */
.detail-skeleton {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(340px, 0.92fr);
  gap: clamp(24px, 3vw, 46px);
  padding-block: clamp(18px, 2.6vw, 34px) clamp(26px, 3.4vw, 44px);
}

.detail-skeleton__media {
  aspect-ratio: 16 / 9;
  border-radius: var(--nm-r-lg);
}

.detail-skeleton__copy {
  display: grid;
  gap: 14px;
  align-content: start;
}

.detail-skeleton__line {
  height: 16px;
  border-radius: var(--nm-r-xs);
}

.detail-skeleton__line--xs {
  width: 28%;
  height: 12px;
}

.detail-skeleton__line--title {
  width: 88%;
  height: 34px;
}

.detail-skeleton__line--md {
  width: 72%;
}

.detail-skeleton__line--price {
  width: 42%;
  height: 30px;
  margin-top: 12px;
}

.detail-state {
  padding-block: clamp(28px, 4vw, 56px);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1180px) {
  .hero-grid,
  .detail-skeleton {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-title {
    max-width: none;
  }
}

@media (max-width: 1080px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .buy-panel {
    position: static;
  }
}

@media (max-width: 860px) {
  .hero-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .lesson {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .lesson__badge {
    display: none;
  }
}

@media (max-width: 640px) {
  .hero-cta {
    display: grid;
  }

  .hero-cta .nm-btn {
    width: 100%;
  }

  .review-summary__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .chapter__head {
    padding: 14px;
    gap: 10px;
  }

  .lesson-list {
    padding-inline: 14px;
  }

  .breadcrumb__current {
    max-width: 20ch;
  }
}
</style>

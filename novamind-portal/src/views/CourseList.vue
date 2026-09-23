<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Close,
  Grid,
  List,
  MagicStick,
  Search,
  ShoppingCart,
  Star,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import { getCategoryList, searchPortalCourses } from '@/api/course'
import { addToCart } from '@/api/trade'
import { decorateCourse } from '@/content/courseShowcase'
import { useRevealObserver } from '@/composables/useRevealObserver'
import { friendlyErrorMessage } from '@/utils/errorMessage'
import { formatPrice } from '@/utils/format'
import { handleImageError } from '@/utils/imageFallback'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'
import sceneEmptyCourses from '@/assets/scenes/scene-empty-courses.svg?raw'
import type { CategoryItem, CourseSimpleInfo, PageResult } from '@/types'

function onImageError(event: Event) {
  handleImageError(event, { kind: 'cover' })
}


/** 后端可选字段：类型里没有声明，但接口可能返回，读取前统一收口 */
type CourseExtra = CourseSimpleInfo & {
  rating?: number | string
  level?: string
  originalPrice?: number
  originPrice?: number
  marketPrice?: number
  linePrice?: number
}

type ActiveFilterKey = 'keyword' | 'category' | 'price' | 'level' | 'sort'

interface ActiveFilterChip {
  key: ActiveFilterKey
  label: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const quickKeywords = ['AI Agent', 'Spring Cloud', 'Vue 3', 'Docker', '微服务', 'Python']

const priceOptions = [
  { value: '', label: '全部' },
  { value: 'free', label: '免费' },
  { value: '0-100', label: '100 元内' },
  { value: '100-300', label: '100-300 元' },
  { value: '300+', label: '300 元以上' },
]

const levelOptions = [
  { value: '', label: '不限' },
  { value: '入门', label: '入门' },
  { value: '进阶', label: '进阶' },
  { value: '高级', label: '高级' },
]

const sortOptions = [
  { value: 'default', label: '综合排序' },
  { value: 'newest', label: '最新发布' },
  { value: 'popular', label: '学习最热' },
  { value: 'price-asc', label: '价格从低到高' },
  { value: 'price-desc', label: '价格从高到低' },
]

const pageSizeOptions = [12, 24, 36]

/* ---------------- 状态 ---------------- */
const loading = ref(true)
const loadError = ref('')
const courses = ref<CourseSimpleInfo[]>([])
const total = ref(0)

const categoriesLoading = ref(true)
const categoriesError = ref('')
const categories = ref<CategoryItem[]>([])

const keyword = ref('')
const selectedCategory = ref<number | undefined>(undefined)
const priceRange = ref('')
const levelFilter = ref('')
const sortBy = ref('default')

const currentPage = ref(1)
const pageSize = ref(12)
const viewMode = ref<'grid' | 'list'>('grid')
const railOpen = ref(false)
const cartingIds = ref<number[]>([])

/* ---------------- 派生数据 ---------------- */
const routeKeyword = computed(() => (typeof route.query.keyword === 'string' ? route.query.keyword : ''))

const visibleCategories = computed(() => {
  const roots = categories.value.filter((category) => category.level === 1 || !category.pid)
  const source = roots.length ? roots : categories.value
  return source.slice(0, 14)
})

const hasLevelData = computed(() => courses.value.some((course) => Boolean(extra(course).level)))

/** 难度筛选：后端参数已下发；当返回数据带 level 字段时，再补一次客户端收敛 */
const visibleCourses = computed(() => {
  if (!levelFilter.value || !hasLevelData.value) {
    return courses.value
  }
  return courses.value.filter((course) => extra(course).level === levelFilter.value)
})

const decoratedCourses = computed(() => {
  const offset = (currentPage.value - 1) * pageSize.value
  return visibleCourses.value.map((course, index) => decorateCourse(course, offset + index))
})

const heroTitle = computed(() =>
  keyword.value.trim() ? `“${keyword.value.trim()}” 的搜索结果` : '找到下一门值得投入的课'
)

const currentCategoryName = computed(() => {
  if (!selectedCategory.value) {
    return '全部分类'
  }
  return visibleCategories.value.find((category) => category.id === selectedCategory.value)?.name || '已选分类'
})

const resultSummary = computed(() => {
  if (loading.value) {
    return '正在检索课程…'
  }
  if (loadError.value) {
    return '课程数据暂时不可用'
  }
  return `共 ${total.value} 门课程`
})

const activeFilters = computed<ActiveFilterChip[]>(() => {
  const chips: ActiveFilterChip[] = []

  if (keyword.value.trim()) {
    chips.push({ key: 'keyword', label: `关键词：${keyword.value.trim()}` })
  }
  if (selectedCategory.value) {
    chips.push({ key: 'category', label: `分类：${currentCategoryName.value}` })
  }
  if (priceRange.value) {
    const label = priceOptions.find((option) => option.value === priceRange.value)?.label
    chips.push({ key: 'price', label: `价格：${label || priceRange.value}` })
  }
  if (levelFilter.value) {
    chips.push({ key: 'level', label: `难度：${levelFilter.value}` })
  }
  if (sortBy.value !== 'default') {
    const label = sortOptions.find((option) => option.value === sortBy.value)?.label
    chips.push({ key: 'sort', label: `排序：${label || sortBy.value}` })
  }

  return chips
})

const hasActiveFilters = computed(() => activeFilters.value.length > 0)

/* ---------------- 数据加载 ---------------- */
useRevealObserver('.course-list-page .nm-reveal', { once: true, threshold: 0.08 })

watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
    currentPage.value = 1
    void loadCourses()
  },
  { immediate: true }
)

onMounted(() => {
  void loadCategories()
})

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

function buildQuery(): Record<string, unknown> {
  const params: Record<string, unknown> = {
    pageNo: currentPage.value,
    pageSize: pageSize.value,
  }

  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  if (selectedCategory.value) params.categoryId = selectedCategory.value
  if (priceRange.value) params.priceRange = priceRange.value
  if (levelFilter.value) params.level = levelFilter.value
  if (sortBy.value !== 'default') params.sortBy = sortBy.value

  return params
}

async function loadCourses() {
  loading.value = true
  loadError.value = ''

  try {
    const payload: unknown = await searchPortalCourses(buildQuery())
    const page = unwrap<PageResult<CourseSimpleInfo>>(payload)
    const list = Array.isArray(page?.list) ? page.list : []
    courses.value = list
    total.value = Number(page?.total ?? list.length) || 0
  } catch (error) {
    courses.value = []
    total.value = 0
    loadError.value = friendlyErrorMessage(error, '课程数据加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categoriesLoading.value = true
  categoriesError.value = ''

  try {
    const payload: unknown = await getCategoryList()
    const list = unwrap<CategoryItem[]>(payload)
    categories.value = Array.isArray(list) ? list : []
    if (!categories.value.length) {
      categoriesError.value = '分类数据暂未就绪'
    }
  } catch (error) {
    categories.value = []
    categoriesError.value = friendlyErrorMessage(error, '分类加载失败')
  } finally {
    categoriesLoading.value = false
  }
}

/* ---------------- 交互 ---------------- */
async function applyKeyword(value: string) {
  keyword.value = value
  currentPage.value = 1

  const next = value.trim()
  if (next !== routeKeyword.value) {
    await router.push({ path: '/portal/courses', query: next ? { keyword: next } : {} })
    return
  }

  await loadCourses()
}

function removeFilter(key: ActiveFilterKey) {
  switch (key) {
    case 'keyword':
      void applyKeyword('')
      return
    case 'category':
      selectedCategory.value = undefined
      break
    case 'price':
      priceRange.value = ''
      break
    case 'level':
      levelFilter.value = ''
      break
    case 'sort':
      sortBy.value = 'default'
      break
  }

  currentPage.value = 1
  void loadCourses()
}

function clearFilters() {
  selectedCategory.value = undefined
  priceRange.value = ''
  levelFilter.value = ''
  sortBy.value = 'default'
  currentPage.value = 1
  keyword.value = ''

  if (routeKeyword.value) {
    void router.push({ path: '/portal/courses' })
    return
  }

  void loadCourses()
}

function toggleCategory(categoryId?: number) {
  selectedCategory.value = selectedCategory.value === categoryId ? undefined : categoryId
  currentPage.value = 1
  void loadCourses()
}

function togglePrice(value: string) {
  priceRange.value = priceRange.value === value ? '' : value
  currentPage.value = 1
  void loadCourses()
}

function toggleLevel(value: string) {
  levelFilter.value = levelFilter.value === value ? '' : value
  currentPage.value = 1
  void loadCourses()
}

function handleSortChange() {
  currentPage.value = 1
  void loadCourses()
}

function handlePageSizeChange() {
  currentPage.value = 1
  void loadCourses()
}

function handlePageChange(page: number) {
  currentPage.value = page
  void loadCourses()
  scrollToResults()
}

function scrollToResults() {
  if (typeof window === 'undefined') {
    return
  }
  const target = document.getElementById('course-results')
  if (!target) {
    return
  }
  const top = target.getBoundingClientRect().top + window.scrollY - 104
  window.scrollTo({ top: Math.max(top, 0), behavior: 'smooth' })
}

function goToCourse(id: number) {
  void router.push(`/portal/course/${id}`)
}

function openAIPlanner(seed?: string) {
  const target = seed || keyword.value.trim() || currentCategoryName.value
  const prompt = seed
    ? `请基于课程《${seed}》为我规划学习路线，并告诉我下一步最值得补的能力。`
    : `请围绕 ${target} 帮我规划一条更适合当前目标的学习路线。`

  void router.push({
    path: '/portal/ai-chat',
    query: { prompt, autostart: '1' },
  })
}

function isCarting(id: number) {
  return cartingIds.value.includes(id)
}

async function handleAddToCart(course: CourseSimpleInfo) {
  if (!userStore.token) {
    void router.push({ path: '/portal/login', query: { redirect: route.fullPath } })
    return
  }
  if (isCarting(course.id)) {
    return
  }

  cartingIds.value = [...cartingIds.value, course.id]
  try {
    await addToCart(course.id)
    ElMessage.success('已加入购物车')
  } catch {
    // 失败提示由 request 拦截器统一给出，这里不再重复弹窗
  } finally {
    cartingIds.value = cartingIds.value.filter((id) => id !== course.id)
  }
}

/* ---------------- 展示工具 ---------------- */
function extra(course: CourseSimpleInfo): CourseExtra {
  return course as CourseExtra
}

// 金额统一走 @/utils/format（后端单位为分）

function originalPriceOf(course: CourseSimpleInfo): string {
  const price = Number(course.price) || 0
  const candidates = [
    extra(course).originalPrice,
    extra(course).originPrice,
    extra(course).marketPrice,
    extra(course).linePrice,
  ]
  const hit = candidates.find((value) => typeof value === 'number' && value > price)
  return typeof hit === 'number' ? formatPrice(hit, { zeroAsFree: false }) : ''
}

function ratingOf(course: CourseSimpleInfo): string {
  const value = extra(course).rating
  if (value === undefined || value === null || value === '') {
    return ''
  }
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num.toFixed(1) : ''
}

function levelOf(course: CourseSimpleInfo): string {
  return String(extra(course).level || '')
}

function learnerLabel(course: CourseSimpleInfo): string {
  return `${Number(course.sales) || 0} 人学习`
}
</script>

<template>
  <div class="course-list-page">
    <!-- ============ 页头 ============ -->
    <section class="list-hero">
      <div class="nm-shell hero-shell">
        <div class="hero-copy nm-reveal">
          <span class="nm-kicker">Course Catalog</span>
          <h1 class="nm-h1 hero-title">{{ heroTitle }}</h1>
          <p class="nm-lede">
            按分类、价格与难度筛选，用真实课程配图与学习数据，快速判断哪门课值得投入时间。
          </p>

          <dl class="hero-meta">
            <div class="hero-meta__item">
              <dt class="hero-meta__label">结果</dt>
              <dd class="hero-meta__value nm-num">{{ resultSummary }}</dd>
            </div>
            <div class="hero-meta__item">
              <dt class="hero-meta__label">范围</dt>
              <dd class="hero-meta__value">{{ currentCategoryName }}</dd>
            </div>
            <div class="hero-meta__item">
              <dt class="hero-meta__label">条件</dt>
              <dd class="hero-meta__value nm-num">{{ activeFilters.length }} 项</dd>
            </div>
          </dl>
        </div>

        <form class="hero-search nm-card nm-card--pad nm-reveal nm-delay-1" @submit.prevent="applyKeyword(keyword)">
          <label class="hero-search__label" for="course-keyword">关键词检索</label>
          <div class="hero-search__row">
            <Search class="hero-search__icon" />
            <input
              id="course-keyword"
              v-model="keyword"
              class="nm-field hero-search__input"
              type="search"
              autocomplete="off"
              placeholder="AI Agent、Spring Cloud、前端工程化…"
            />
            <button type="submit" class="nm-btn nm-btn--primary">搜索</button>
          </div>
          <div class="hero-search__tags">
            <span class="nm-kicker nm-kicker--plain">高频主题</span>
            <button
              v-for="tag in quickKeywords"
              :key="tag"
              type="button"
              class="nm-chip"
              @click="applyKeyword(tag)"
            >
              {{ tag }}
            </button>
          </div>
        </form>
      </div>
    </section>

    <!-- ============ 筛选 + 结果 ============ -->
    <section class="nm-shell list-body">
      <!-- 筛选栏 -->
      <aside
        id="course-filters"
        class="filter-rail nm-card nm-card--pad nm-reveal nm-delay-1"
        :class="{ 'is-open': railOpen }"
        aria-label="课程筛选"
      >
        <header class="rail-head">
          <div>
            <span class="nm-kicker">Filters</span>
            <h2 class="nm-h3">筛选条件</h2>
          </div>
          <button
            v-if="hasActiveFilters"
            type="button"
            class="nm-btn nm-btn--ghost nm-btn--sm"
            @click="clearFilters"
          >
            清空
          </button>
        </header>

        <div class="rail-group">
          <h3 class="rail-group__title">课程分类</h3>

          <div v-if="categoriesLoading" class="rail-skeleton" aria-hidden="true">
            <span v-for="i in 5" :key="i" class="nm-skeleton rail-skeleton__line" />
          </div>

          <div v-else-if="categoriesError" class="rail-note">
            <p>{{ categoriesError }}</p>
            <button type="button" class="nm-btn nm-btn--sm" @click="loadCategories">重新加载分类</button>
          </div>

          <div v-else class="rail-list">
            <button
              type="button"
              class="rail-item"
              :class="{ 'is-active': !selectedCategory }"
              @click="toggleCategory(undefined)"
            >
              全部分类
            </button>
            <button
              v-for="category in visibleCategories"
              :key="category.id"
              type="button"
              class="rail-item"
              :class="{ 'is-active': selectedCategory === category.id }"
              @click="toggleCategory(category.id)"
            >
              {{ category.name }}
            </button>
          </div>
        </div>

        <div class="rail-group">
          <h3 class="rail-group__title">价格区间</h3>
          <div class="rail-chips">
            <button
              v-for="option in priceOptions"
              :key="option.value || 'all'"
              type="button"
              class="nm-chip"
              :class="{ 'is-active': priceRange === option.value }"
              @click="togglePrice(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="rail-group">
          <h3 class="rail-group__title">难度</h3>
          <div class="rail-chips">
            <button
              v-for="option in levelOptions"
              :key="option.value || 'any'"
              type="button"
              class="nm-chip"
              :class="{ 'is-active': levelFilter === option.value }"
              @click="toggleLevel(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="rail-group">
          <h3 class="rail-group__title">排序</h3>
          <div class="rail-chips">
            <button
              v-for="option in sortOptions"
              :key="option.value"
              type="button"
              class="nm-chip"
              :class="{ 'is-active': sortBy === option.value }"
              @click="sortBy = option.value; handleSortChange()"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <article class="rail-ai">
          <MagicStick class="rail-ai__icon" />
          <div class="rail-ai__copy">
            <strong>AI 帮你选课</strong>
            <p>说出目标与可用时间，让 AI 按你的基础排出学习路径。</p>
          </div>
          <button type="button" class="nm-btn nm-btn--accent nm-btn--sm" @click="openAIPlanner()">
            开始对话
            <ArrowRight />
          </button>
        </article>
      </aside>

      <!-- 结果区 -->
      <div id="course-results" class="results">
        <header class="results-head nm-card nm-card--pad nm-reveal nm-delay-2">
          <div class="results-head__main">
            <span class="nm-kicker">Results</span>
            <h2 class="nm-h3">{{ resultSummary }}</h2>
            <p class="results-head__sub">
              当前范围：{{ currentCategoryName }}
              <template v-if="hasActiveFilters"> · 已启用 {{ activeFilters.length }} 个条件</template>
            </p>
          </div>

          <div class="results-head__controls">
            <button
              type="button"
              class="nm-btn nm-btn--sm rail-toggle"
              :aria-expanded="railOpen"
              @click="railOpen = !railOpen"
            >
              {{ railOpen ? '收起筛选' : '筛选' }}
            </button>

            <label class="ctrl-field">
              <span class="ctrl-field__label">排序</span>
              <select v-model="sortBy" class="nm-field ctrl-field__select" @change="handleSortChange">
                <option v-for="option in sortOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>

            <label class="ctrl-field">
              <span class="ctrl-field__label">每页</span>
              <select v-model.number="pageSize" class="nm-field ctrl-field__select" @change="handlePageSizeChange">
                <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
              </select>
            </label>

            <div class="density" role="group" aria-label="结果密度">
              <button
                type="button"
                class="density__btn"
                :class="{ 'is-active': viewMode === 'grid' }"
                aria-label="卡片视图"
                @click="viewMode = 'grid'"
              >
                <Grid />
              </button>
              <button
                type="button"
                class="density__btn"
                :class="{ 'is-active': viewMode === 'list' }"
                aria-label="列表视图"
                @click="viewMode = 'list'"
              >
                <List />
              </button>
            </div>
          </div>

          <div v-if="activeFilters.length" class="active-chips">
            <button
              v-for="chip in activeFilters"
              :key="chip.key"
              type="button"
              class="active-chip"
              @click="removeFilter(chip.key)"
            >
              <span>{{ chip.label }}</span>
              <Close />
            </button>
          </div>
        </header>

        <!-- 加载骨架 -->
        <div v-if="loading" class="nm-auto-grid skeleton-grid" aria-hidden="true">
          <article v-for="i in 6" :key="i" class="nm-card skeleton-card">
            <div class="nm-skeleton skeleton-card__media" />
            <div class="skeleton-card__body">
              <span class="nm-skeleton skeleton-line skeleton-line--xs" />
              <span class="nm-skeleton skeleton-line skeleton-line--lg" />
              <span class="nm-skeleton skeleton-line skeleton-line--full" />
              <span class="nm-skeleton skeleton-line skeleton-line--md" />
            </div>
          </article>
        </div>

        <!-- 失败态 -->
        <EmptyState
          v-else-if="loadError"
          type="error"
          title="课程数据加载失败"
          description="后端课程服务暂时不可用，页面其余部分仍可正常浏览。可以重新加载，或先清空筛选条件再试一次。"
          @action="loadCourses"
        >
          <template #action>
            <button type="button" class="nm-btn nm-btn--primary" @click="loadCourses">重新加载</button>
            <button v-if="hasActiveFilters" type="button" class="nm-btn" @click="clearFilters">
              清空筛选
            </button>
            <button type="button" class="nm-btn nm-btn--ghost" @click="openAIPlanner()">
              让 AI 推荐
              <ArrowRight />
            </button>
          </template>
        </EmptyState>

        <!-- 空态 -->
        <EmptyState
          v-else-if="!decoratedCourses.length"
          type="search"
          title="没有匹配的课程"
          description="换一个关键词，或放宽价格与难度条件；也可以让 AI 根据你的目标重新推荐。"
          @action="clearFilters"
        >
          <template #illustration>
            <span class="scene-art" v-html="sceneEmptyCourses" />
          </template>
          <template #action>
            <button v-if="hasActiveFilters" type="button" class="nm-btn nm-btn--primary" @click="clearFilters">
              清空筛选
            </button>
            <button type="button" class="nm-btn" @click="openAIPlanner()">
              让 AI 推荐
              <ArrowRight />
            </button>
          </template>
        </EmptyState>

        <!-- 卡片视图 -->
        <div v-else-if="viewMode === 'grid'" class="nm-auto-grid course-grid">
          <article
            v-for="course in decoratedCourses"
            :key="course.raw.id"
            class="course-card nm-card nm-card--hover"
            :class="`cat-${course.accentToken}`"
            @click="goToCourse(course.raw.id)"
          >
            <div class="course-card__media">
              <img :src="course.cover" :alt="course.raw.name" :data-fallback-seed="course.raw.id" loading="lazy" @error="onImageError" />
              <span class="nm-badge course-card__badge">{{ course.raw.categoryName || '平台课程' }}</span>
              <div class="course-card__actions">
                <button
                  type="button"
                  class="nm-btn nm-btn--sm course-card__action"
                  @click.stop="goToCourse(course.raw.id)"
                >
                  查看详情
                </button>
                <button
                  type="button"
                  class="nm-btn nm-btn--sm nm-btn--accent course-card__action"
                  :disabled="isCarting(course.raw.id)"
                  @click.stop="handleAddToCart(course.raw)"
                >
                  <ShoppingCart />
                  {{ isCarting(course.raw.id) ? '处理中' : '加入购物车' }}
                </button>
              </div>
            </div>

            <div class="course-card__body">
              <h3 class="course-card__title">{{ course.raw.name }}</h3>
              <p class="course-card__desc">{{ course.summary }}</p>

              <div class="course-card__meta">
                <span class="course-card__meta-item">
                  <User />
                  {{ course.raw.teacherName || 'Nova 导师团' }}
                </span>
                <span class="course-card__meta-item">
                  <TrendCharts />
                  {{ learnerLabel(course.raw) }}
                </span>
                <span v-if="ratingOf(course.raw)" class="course-card__meta-item">
                  <Star />
                  {{ ratingOf(course.raw) }}
                </span>
              </div>

              <div class="course-card__foot">
                <span class="course-card__price">
                  <strong class="nm-num">{{ formatPrice(course.raw.price) }}</strong>
                  <s v-if="originalPriceOf(course.raw)" class="nm-num course-card__price-was">
                    {{ originalPriceOf(course.raw) }}
                  </s>
                </span>
                <span v-if="levelOf(course.raw)" class="nm-tag">{{ levelOf(course.raw) }}</span>
              </div>
            </div>
          </article>
        </div>

        <!-- 列表视图 -->
        <div v-else class="course-rows">
          <article
            v-for="course in decoratedCourses"
            :key="course.raw.id"
            class="course-row nm-card nm-card--hover"
            :class="`cat-${course.accentToken}`"
            @click="goToCourse(course.raw.id)"
          >
            <div class="course-row__media">
              <img :src="course.cover" :alt="course.raw.name" :data-fallback-seed="course.raw.id" loading="lazy" @error="onImageError" />
            </div>

            <div class="course-row__body">
              <span class="nm-badge course-row__badge">{{ course.raw.categoryName || '平台课程' }}</span>
              <h3 class="course-row__title">{{ course.raw.name }}</h3>
              <p class="course-row__desc">{{ course.summary }}</p>
              <div class="course-card__meta course-card__meta--row">
                <span class="course-card__meta-item">
                  <User />
                  {{ course.raw.teacherName || 'Nova 导师团' }}
                </span>
                <span class="course-card__meta-item">
                  <TrendCharts />
                  {{ learnerLabel(course.raw) }}
                </span>
                <span v-if="ratingOf(course.raw)" class="course-card__meta-item">
                  <Star />
                  {{ ratingOf(course.raw) }}
                </span>
                <span v-if="levelOf(course.raw)" class="course-card__meta-item">{{ levelOf(course.raw) }}</span>
              </div>
            </div>

            <div class="course-row__side">
              <span class="course-card__price">
                <strong class="nm-num">{{ formatPrice(course.raw.price) }}</strong>
                <s v-if="originalPriceOf(course.raw)" class="nm-num course-card__price-was">
                  {{ originalPriceOf(course.raw) }}
                </s>
              </span>
              <button
                type="button"
                class="nm-btn nm-btn--sm nm-btn--accent"
                :disabled="isCarting(course.raw.id)"
                @click.stop="handleAddToCart(course.raw)"
              >
                <ShoppingCart />
                {{ isCarting(course.raw.id) ? '处理中' : '加入购物车' }}
              </button>
              <button type="button" class="nm-btn nm-btn--sm" @click.stop="goToCourse(course.raw.id)">
                查看详情
              </button>
            </div>
          </article>
        </div>

        <el-pagination
          v-if="!loading && !loadError && total > pageSize"
          class="pagination"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :pager-count="5"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
/* ============================================================
   页面骨架
   ============================================================ */
.course-list-page {
  padding-bottom: 64px;
}

.list-hero {
  padding-block: clamp(24px, 3.4vw, 48px) clamp(22px, 3vw, 38px);
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(340px, 0.95fr);
  gap: clamp(24px, 3vw, 44px);
  align-items: end;
}

.hero-copy {
  display: grid;
  gap: 14px;
  align-content: start;
}

.hero-title {
  max-width: 20ch;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  margin-top: 4px;
  padding-top: 18px;
  border-top: 1px solid var(--nm-line);
}

.hero-meta__item {
  display: grid;
  gap: 3px;
}

.hero-meta__label {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.hero-meta__value {
  font-size: var(--nm-fs-body);
  font-weight: 650;
  color: var(--nm-ink-2);
}

/* --- 搜索 --- */
.hero-search {
  display: grid;
  gap: 14px;
  align-self: end;
}

.hero-search__label {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
}

.hero-search__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-search__icon {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  color: var(--nm-ink-4);
}

.hero-search__input {
  flex: 1 1 auto;
  min-width: 0;
}

.hero-search__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

/* ============================================================
   主体两栏
   ============================================================ */
.list-body {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: clamp(20px, 2.4vw, 32px);
  align-items: start;
}

/* --- 筛选栏 --- */
.filter-rail {
  position: sticky;
  top: 88px;
  display: grid;
  gap: 20px;
  max-height: calc(100vh - 116px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.rail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.rail-head h2 {
  margin-top: 6px;
}

.rail-group {
  display: grid;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--nm-line);
}

.rail-group__title {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
}

.rail-list {
  display: grid;
  gap: 2px;
}

.rail-item {
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-sm);
  background: transparent;
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
  font-weight: 550;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    border-color var(--nm-dur-fast) var(--nm-ease), color var(--nm-dur-fast) var(--nm-ease);
}

.rail-item:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.rail-item.is-active {
  background: var(--nm-accent-soft);
  border-color: var(--nm-accent-line);
  color: var(--nm-accent);
  font-weight: 650;
}

.rail-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rail-skeleton {
  display: grid;
  gap: 9px;
}

.rail-skeleton__line {
  height: 14px;
}

.rail-note {
  display: grid;
  justify-items: start;
  gap: 10px;
  padding: 12px;
  border: 1px dashed var(--nm-line-strong);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
}

.rail-ai {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
}

.rail-ai__icon {
  width: 32px;
  height: 32px;
  padding: 7px;
  border-radius: var(--nm-r-sm);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.rail-ai__copy strong {
  display: block;
  margin-bottom: 5px;
  font-size: var(--nm-fs-body);
}

.rail-ai__copy p {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
}

/* ============================================================
   结果区
   ============================================================ */
.results {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.results-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.results-head__main {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.results-head__sub {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
}

.results-head__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.rail-toggle {
  display: none;
}

.ctrl-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ctrl-field__label {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.ctrl-field__select {
  width: auto;
  min-width: 0;
  min-height: 36px;
  padding-right: 30px;
  font-size: var(--nm-fs-sm);
  cursor: pointer;
}

.density {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
}

.density__btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 28px;
  border: 0;
  border-radius: var(--nm-r-xs);
  background: transparent;
  color: var(--nm-ink-3);
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    color var(--nm-dur-fast) var(--nm-ease), box-shadow var(--nm-dur-fast) var(--nm-ease);
}

.density__btn svg {
  width: 15px;
  height: 15px;
}

.density__btn:hover {
  color: var(--nm-ink);
}

.density__btn.is-active {
  background: var(--nm-surface);
  color: var(--nm-ink);
  box-shadow: var(--nm-sh-1);
}

.active-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-basis: 100%;
  margin-top: 2px;
  padding-top: 14px;
  border-top: 1px solid var(--nm-line);
}

.active-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 9px 5px 12px;
  border: 1px solid var(--nm-accent-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    border-color var(--nm-dur-fast) var(--nm-ease), color var(--nm-dur-fast) var(--nm-ease);
}

.active-chip svg {
  width: 12px;
  height: 12px;
}

.active-chip:hover {
  background: var(--nm-surface-3);
  border-color: var(--nm-ink-4);
  color: var(--nm-ink);
}

/* ============================================================
   分类强调色（令牌名 → CSS 变量，页面内不写死颜色）
   ============================================================ */
.cat-accent {
  --cat: var(--nm-accent);
  --cat-soft: var(--nm-accent-soft);
}
.cat-cyan {
  --cat: var(--nm-cyan);
  --cat-soft: var(--nm-cyan-soft);
}
.cat-success {
  --cat: var(--nm-success);
  --cat-soft: var(--nm-success-soft);
}
.cat-warning {
  --cat: var(--nm-warning);
  --cat-soft: var(--nm-warning-soft);
}
.cat-danger {
  --cat: var(--nm-danger);
  --cat-soft: var(--nm-danger-soft);
}

/* ============================================================
   课程卡片（网格）
   ============================================================ */
.course-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
}

.course-card__media {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-bottom: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

/* 图片内描边：真实配图上仍保留一条细线，避免与纸底糊在一起 */
.course-card__media::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  box-shadow: inset 0 0 0 1px var(--nm-line);
  pointer-events: none;
}

.course-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--nm-dur-slow) var(--nm-ease);
}

.course-card:hover .course-card__media img {
  transform: scale(1.04);
}

.course-card__badge {
  position: absolute;
  z-index: 1;
  left: 12px;
  top: 12px;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface);
  color: var(--cat, var(--nm-accent));
}

.course-card__actions {
  position: absolute;
  z-index: 1;
  left: 12px;
  right: 12px;
  bottom: 12px;
  display: flex;
  gap: 8px;
  opacity: 0;
  transform: translateY(8px);
  transition: opacity var(--nm-dur) var(--nm-ease), transform var(--nm-dur) var(--nm-ease);
}

.course-card:hover .course-card__actions,
.course-card:focus-within .course-card__actions {
  opacity: 1;
  transform: none;
}

.course-card__action {
  flex: 1 1 0;
  min-width: 0;
  padding-inline: 10px;
}

.course-card__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px 18px;
}

.course-card__title {
  display: -webkit-box;
  overflow: hidden;
  min-height: 2.7em;
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.015em;
  color: var(--nm-ink);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.course-card__desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.course-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

.course-card__meta--row {
  margin-top: 2px;
  padding-top: 0;
  border-top: 0;
}

.course-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.course-card__meta-item svg {
  width: 13px;
  height: 13px;
  flex: 0 0 auto;
}

.course-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.course-card__price {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
}

.course-card__price strong {
  font-size: 1.25rem;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: var(--nm-ink);
}

.course-card__price-was {
  color: var(--nm-ink-4);
  font-size: var(--nm-fs-xs);
}

/* ============================================================
   课程行（列表密度）
   ============================================================ */
.course-rows {
  display: grid;
  gap: 14px;
}

.course-row {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr) 190px;
  gap: 20px;
  align-items: center;
  padding: 14px;
  cursor: pointer;
}

.course-row__media {
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--nm-r);
  box-shadow: inset 0 0 0 1px var(--nm-line);
  background: var(--nm-surface-3);
}

.course-row__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--nm-dur-slow) var(--nm-ease);
}

.course-row:hover .course-row__media img {
  transform: scale(1.04);
}

.course-row__body {
  display: grid;
  gap: 8px;
  align-content: center;
  min-width: 0;
}

.course-row__badge {
  justify-self: start;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  color: var(--cat, var(--nm-accent));
}

.course-row__title {
  font-size: 1.0625rem;
  font-weight: 700;
  line-height: 1.35;
  color: var(--nm-ink);
}

.course-row__desc {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
}

.course-row__side {
  display: grid;
  gap: 8px;
  justify-items: end;
}

/* ============================================================
   骨架
   ============================================================ */
.skeleton-card {
  overflow: hidden;
}

.skeleton-card__media {
  aspect-ratio: 16 / 9;
  border-radius: 0;
}

.skeleton-card__body {
  display: grid;
  gap: 10px;
  padding: 16px 18px 18px;
}

.skeleton-line {
  height: 12px;
  border-radius: var(--nm-r-xs);
}

.skeleton-line--xs {
  width: 32%;
  height: 10px;
}

.skeleton-line--lg {
  width: 84%;
  height: 18px;
}

.skeleton-line--full {
  width: 100%;
}

.skeleton-line--md {
  width: 62%;
}

/* ============================================================
   分页
   ============================================================ */
.pagination {
  margin-top: 6px;
  justify-content: center;
}

.pagination :deep(.el-pager li),
.pagination :deep(.btn-prev),
.pagination :deep(.btn-next) {
  min-width: 34px;
  height: 34px;
  border-radius: var(--nm-r-sm);
  background: transparent;
  color: var(--nm-ink-2);
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-sm);
}

.pagination :deep(.el-pager li:hover),
.pagination :deep(.btn-prev:hover),
.pagination :deep(.btn-next:hover) {
  color: var(--nm-accent);
  background: var(--nm-accent-soft);
}

.pagination :deep(.el-pager li.is-active) {
  background: var(--nm-ink);
  color: var(--nm-paper);
  font-weight: 700;
}

/* 线稿插画 */
.scene-art {
  display: block;
}

.scene-art :deep(svg) {
  width: 100%;
  height: auto;
}

/* ============================================================
   响应式
   ============================================================ */
@media (hover: none) {
  .course-card__actions {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 1180px) {
  .hero-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-title {
    max-width: none;
  }
}

@media (max-width: 1080px) {
  .list-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-rail {
    position: static;
    display: none;
    max-height: none;
    overflow: visible;
  }

  .filter-rail.is-open {
    display: grid;
  }

  .rail-toggle {
    display: inline-flex;
  }
}

@media (max-width: 860px) {
  .course-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .course-row__side {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-items: start;
    gap: 10px;
  }
}

@media (max-width: 640px) {
  .results-head {
    align-items: stretch;
    flex-direction: column;
  }

  .ctrl-field__label {
    display: none;
  }

  .course-card__action {
    padding-inline: 6px;
  }
}
</style>

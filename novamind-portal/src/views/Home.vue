<script setup lang="ts">
import { onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChatDotRound,
  Cpu,
  Reading,
  Search,
  ShoppingCart,
  TrendCharts,
  User,
  VideoCamera,
} from '@element-plus/icons-vue'
import { getCategoryList, getRecommendBest, getRecommendNew } from '@/api/course'
import type { CategoryItem, RecommendCourse } from '@/types'
import { consolePrompts, heroSignals, reactPhases, sceneCards } from '@/content/novamind'
import { useRevealObserver } from '@/composables/useRevealObserver'
import { formatPrice } from '@/utils/format'
import { handleImageError } from '@/utils/imageFallback'

interface ModuleCard {
  title: string
  desc: string
  route: string
  icon: Component
  query?: Record<string, string>
}

interface ShowcaseCourse {
  id: number
  title: string
  teacher: string
  cover: string
  price: number
  free: boolean
  learners: number
  rating: number
  tag: string
}

const router = useRouter()
const searchKeyword = ref('AI 应用开发')
const quickKeywords = ['Spring Cloud', '前端工程', 'AI 助手', '微服务', '数据分析']

/* ---------- 精选课程：优先真实推荐接口，失败时用真实封面兜底 ---------- */
const FALLBACK_COURSES: ShowcaseCourse[] = [
  {
    id: 1,
    title: 'AI 应用开发实战：从大模型到 Agent',
    teacher: '陈墨白',
    cover: '/resource/course-covers/course_ai_deep_learning_01.jpg',
    price: 299,
    free: false,
    learners: 4820,
    rating: 4.9,
    tag: 'AI / ML',
  },
  {
    id: 2,
    title: 'Spring Boot 3 微服务架构与治理',
    teacher: '林致远',
    cover: '/resource/course-covers/course_coding_programming_02.jpg',
    price: 259,
    free: false,
    learners: 3615,
    rating: 4.8,
    tag: '后端开发',
  },
  {
    id: 3,
    title: 'Docker 与 Kubernetes 云原生实践',
    teacher: '苏行舟',
    cover: '/resource/course-covers/course_web_development_02.jpg',
    price: 279,
    free: false,
    learners: 2940,
    rating: 4.8,
    tag: '云原生',
  },
  {
    id: 4,
    title: '现代前端工程化：Vue 3 与设计系统',
    teacher: '叶知秋',
    cover: '/resource/course-covers/course_ui_ux_design_01.jpg',
    price: 0,
    free: true,
    learners: 7310,
    rating: 4.9,
    tag: '前端开发',
  },
]

const showcase = ref<ShowcaseCourse[]>(FALLBACK_COURSES)
const showcaseLoading = ref(true)

function normalizeCourse(raw: RecommendCourse | any, index: number): ShowcaseCourse {
  const fallback = FALLBACK_COURSES[index % FALLBACK_COURSES.length]
  const price = Number(raw?.price ?? fallback.price)
  return {
    id: Number(raw?.id ?? fallback.id),
    title: String(raw?.name ?? raw?.title ?? fallback.title),
    teacher: String(raw?.teacherName ?? raw?.teacher ?? fallback.teacher),
    cover: String(raw?.coverUrl ?? raw?.coverImg ?? raw?.cover ?? fallback.cover),
    price,
    free: price <= 0,
    learners: Number(raw?.sold ?? raw?.learners ?? fallback.learners),
    rating: Number(raw?.score ?? raw?.rating ?? fallback.rating),
    tag: String(raw?.categoryName ?? raw?.category ?? fallback.tag),
  }
}

async function loadShowcase() {
  showcaseLoading.value = true
  try {
    const res: any = await getRecommendBest()
    const list = Array.isArray(res?.data) ? res.data : []
    showcase.value = list.length
      ? list.slice(0, 4).map((item: any, i: number) => normalizeCourse(item, i))
      : FALLBACK_COURSES
  } catch {
    // 推荐服务不可用时展示精选样例，页面结构保持完整
    try {
      const res: any = await getRecommendNew()
      const list = Array.isArray(res?.data) ? res.data : []
      showcase.value = list.length
        ? list.slice(0, 4).map((item: any, i: number) => normalizeCourse(item, i))
        : FALLBACK_COURSES
    } catch {
      showcase.value = FALLBACK_COURSES
    }
  } finally {
    showcaseLoading.value = false
  }
}

/* ---------- 技术方向：来自分类接口，失败时用静态方向 ---------- */
const FALLBACK_TOPICS = ['AI / ML', '前端开发', '后端开发', '云原生', '数据库', '移动端', '测试运维']
const topics = ref<string[]>(FALLBACK_TOPICS)

async function loadTopics() {
  try {
    const res: any = await getCategoryList()
    const list: CategoryItem[] = Array.isArray(res?.data) ? res.data : []
    const names = list.map((item) => item?.name).filter((n): n is string => Boolean(n))
    if (names.length) topics.value = names.slice(0, 8)
  } catch {
    topics.value = FALLBACK_TOPICS
  }
}

/* ---------- 静态内容 ---------- */
const consumerModules: ModuleCard[] = [
  { title: '全部课程', desc: '分类浏览与关键词检索', route: '/portal/courses', icon: Reading },
  { title: '购物车', desc: '集中管理待购课程', route: '/portal/cart', icon: ShoppingCart },
  {
    title: 'AI 助手',
    desc: '随时提问与学习规划',
    route: '/portal/ai-chat',
    icon: ChatDotRound,
    query: { prompt: consolePrompts[0], autostart: '1' },
  },
  { title: '学习社区', desc: '问答讨论与阶段测验', route: '/portal/community', icon: VideoCamera },
  { title: '个人中心', desc: '我的课程、订单与资料', route: '/portal/my/profile', icon: User },
]

const adminModules: ModuleCard[] = [
  { title: '课程管理', desc: '课程上下架与分类维护', route: '/admin/courses', icon: Reading },
  { title: '用户管理', desc: '学员信息与状态管理', route: '/admin/users', icon: User },
  { title: 'AI 管理', desc: '会话记录与提示词模板', route: '/admin/ai', icon: Cpu },
  { title: '数据报表', desc: '运营指标与趋势看板', route: '/admin/reports', icon: TrendCharts },
]

const splitCards = [
  {
    title: '学习平台',
    desc: '选课、学习、提问、测验，一站式完成学习全流程。',
    route: '/portal/courses',
    action: '进入学习平台',
    tags: ['浏览课程', '加入购物车', '在线学习', 'AI 答疑', '阶段测验', '个人中心'],
    invert: false,
  },
  {
    title: '管理后台',
    desc: '课程上架、用户管理、数据分析与运营配置。',
    route: '/admin',
    action: '进入管理后台',
    tags: ['课程管理', '用户管理', 'AI 管理', '学习报表', '订单分析', '运营配置'],
    invert: true,
  },
]

const testimonials = [
  {
    name: '周子扬',
    role: '后端工程师 · 3 年经验',
    avatar: '/resource/user-avatars/avatar_student_male_01.jpg',
    quote:
      '课程目录拆得很细，配合 AI 助手随时追问，原本卡了两周的微服务治理问题一次就理顺了。',
  },
  {
    name: '许清和',
    role: '前端开发 · 转型中',
    avatar: '/resource/user-avatars/avatar_student_female_01.jpg',
    quote:
      '学习路径按阶段给得很清楚，每学完一章都有测验，能真实看到自己的掌握程度。',
  },
  {
    name: '林知微',
    role: '数据方向 · 在校生',
    avatar: '/resource/user-avatars/avatar_young_man_01.png',
    quote:
      '知识图谱把零散的知识点串成了一张网，复习的时候特别直观，不用再翻一堆笔记。',
  },
]

const learningBars = [42, 58, 36, 74, 52, 88, 63]

useRevealObserver('.home-page .nm-reveal', { once: true, threshold: 0.14 })

onMounted(() => {
  loadShowcase()
  loadTopics()
})

function onImageError(event: Event) {
  handleImageError(event, { kind: 'cover' })
}

function onAvatarError(event: Event) {
  handleImageError(event, { kind: 'avatar' })
}

function openSearch() {
  const keyword = searchKeyword.value.trim()
  router.push(keyword ? { path: '/portal/courses', query: { keyword } } : '/portal/courses')
}

function openModule(module: ModuleCard) {
  router.push(module.query ? { path: module.route, query: module.query } : module.route)
}

function openAIWithPrompt(prompt: string) {
  router.push({ path: '/portal/ai-chat', query: { prompt, autostart: '1' } })
}

function openCourse(course: ShowcaseCourse) {
  router.push(`/portal/course/${course.id}`)
}

function formatLearners(count: number) {
  return count >= 10000 ? `${(count / 10000).toFixed(1)} 万` : String(count)
}
</script>

<template>
  <div class="home-page">
    <!-- ============ Hero ============ -->
    <section class="hero nm-section">
      <div class="nm-shell hero__inner">
        <div class="hero__copy">
          <span class="nm-kicker">NovaMind · 智星云学习平台</span>
          <h1 class="nm-display hero__title">
            选课 · 学习 · <span class="hero__title-accent">成长</span>
          </h1>
          <p class="nm-lede hero__lede">
            从系统课程到 AI 伴学，把零散的知识点连成一条清晰的成长路径。找到对的课，学得下去，也学得明白。
          </p>

          <div class="hero__search">
            <div class="hero__search-box">
              <el-icon class="hero__search-icon"><Search /></el-icon>
              <input
                v-model="searchKeyword"
                class="hero__search-input"
                type="search"
                aria-label="搜索课程"
                placeholder="想学什么？试试 Spring Boot、AI Agent、Docker…"
                @keyup.enter="openSearch"
              />
              <button type="button" class="nm-btn nm-btn--primary hero__search-btn" @click="openSearch">
                搜索课程
              </button>
            </div>
          </div>

          <div class="hero__chips">
            <span class="hero__chips-label">热门方向</span>
            <button
              v-for="query in quickKeywords"
              :key="query"
              type="button"
              class="nm-chip"
              @click="searchKeyword = query; openSearch()"
            >
              {{ query }}
            </button>
          </div>

          <div class="hero__actions">
            <button type="button" class="nm-btn nm-btn--primary nm-btn--lg" @click="router.push('/portal/courses')">
              开始选课
              <el-icon><ArrowRight /></el-icon>
            </button>
            <button
              type="button"
              class="nm-btn nm-btn--lg"
              @click="openAIWithPrompt(consolePrompts[0])"
            >
              <el-icon><ChatDotRound /></el-icon>
              问问 AI 助手
            </button>
          </div>

          <dl class="hero__stats">
            <div v-for="signal in heroSignals.slice(0, 3)" :key="signal.label" class="hero__stat">
              <dt class="nm-num">{{ signal.value }}</dt>
              <dd>{{ signal.label }}</dd>
            </div>
          </dl>
        </div>

        <!-- 产品视觉：真实课程封面 + 悬浮卡片 -->
        <div class="hero__visual" aria-hidden="true">
          <article class="hero__feature">
            <div class="hero__feature-media">
              <img
                src="/resource/course-covers/course_ai_deep_learning_01.jpg"
                alt=""
                data-fallback-seed="hero"
                @error="onImageError"
              />
              <span class="hero__feature-tag">AI / ML</span>
            </div>
            <div class="hero__feature-body">
              <h3>AI 应用开发实战：从大模型到 Agent</h3>
              <p class="hero__feature-meta">
                <span>陈墨白</span>
                <span class="hero__dot"></span>
                <span>4,820 人在学</span>
              </p>
              <div class="hero__feature-progress">
                <span class="hero__feature-bar"><i style="width: 68%"></i></span>
                <span class="nm-num">68%</span>
              </div>
            </div>
          </article>

          <div class="hero__float hero__float--chat">
            <span class="hero__float-head">
              <el-icon><ChatDotRound /></el-icon>
              AI 伴学
            </span>
            <p>这段依赖注入的写法能再解释一下吗？</p>
            <span class="hero__float-reply">当然，我们从 IoC 容器的生命周期说起…</span>
          </div>

          <div class="hero__float hero__float--week">
            <span class="hero__float-head">
              <el-icon><TrendCharts /></el-icon>
              本周学习
            </span>
            <div class="hero__bars">
              <i v-for="(h, i) in learningBars" :key="i" :style="{ height: `${h}%` }"></i>
            </div>
            <span class="nm-num hero__float-value">6.5 h</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 方向跑马灯 ============ -->
    <div class="topic-strip" aria-hidden="true">
      <div class="topic-strip__track">
        <span v-for="n in 2" :key="n" class="topic-strip__set">
          <template v-for="topic in topics" :key="`${n}-${topic}`">
            <span class="topic-strip__item">{{ topic }}</span>
            <span class="topic-strip__sep">—</span>
          </template>
        </span>
      </div>
    </div>

    <!-- ============ 精选课程 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">01 / 精选课程</span>
            <h2 class="nm-h2">从这些课开始</h2>
            <p class="nm-lede">由教研团队筛选的高口碑课程，配套练习与 AI 答疑。</p>
          </div>
          <router-link to="/portal/courses" class="nm-btn nm-btn--sm">
            全部课程
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </header>

        <div v-if="showcaseLoading" class="nm-auto-grid">
          <div v-for="i in 4" :key="i" class="course-skeleton">
            <div class="nm-skeleton course-skeleton__cover"></div>
            <div class="nm-skeleton course-skeleton__line"></div>
            <div class="nm-skeleton course-skeleton__line course-skeleton__line--short"></div>
          </div>
        </div>

        <div v-else class="nm-auto-grid">
          <article
            v-for="(course, i) in showcase"
            :key="course.id"
            :class="['course-card', 'nm-reveal', `nm-delay-${(i % 4) + 1}`]"
            @click="openCourse(course)"
          >
            <div class="course-card__media">
              <img
                :src="course.cover"
                :alt="course.title"
                :data-fallback-seed="course.id"
                loading="lazy"
                @error="onImageError"
              />
              <span class="nm-badge nm-badge--accent course-card__badge">{{ course.tag }}</span>
            </div>
            <div class="course-card__body">
              <h3 class="course-card__title">{{ course.title }}</h3>
              <p class="course-card__teacher">{{ course.teacher }}</p>
              <div class="course-card__foot">
                <span class="course-card__price">
                  <template v-if="course.free">
                    <em>免费</em>
                  </template>
                  <template v-else>
                    <em class="nm-num">{{ formatPrice(course.price) }}</em>
                  </template>
                </span>
                <span class="course-card__meta nm-num">
                  ★ {{ course.rating.toFixed(1) }} · {{ formatLearners(course.learners) }} 人
                </span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 学习路径 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">02 / 学习路径</span>
            <h2 class="nm-h2">四步走完一个学习闭环</h2>
            <p class="nm-lede">每一步都有对应的工具支撑，不用自己拼凑流程。</p>
          </div>
        </header>

        <ol class="path">
          <li
            v-for="(phase, i) in reactPhases"
            :key="phase.key"
            :class="['path__step', 'nm-reveal', `nm-delay-${i + 1}`]"
          >
            <span class="path__index nm-num">{{ String(i + 1).padStart(2, '0') }}</span>
            <h3 class="nm-h3">{{ phase.title }}</h3>
            <p class="path__desc">{{ phase.desc }}</p>
            <span class="path__metric">{{ phase.metric }}</span>
          </li>
        </ol>
      </div>
    </section>

    <!-- ============ 双入口 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">03 / 平台入口</span>
            <h2 class="nm-h2">学习与管理，各归其位</h2>
          </div>
        </header>

        <div class="nm-grid nm-grid--2">
          <article
            v-for="item in splitCards"
            :key="item.title"
            :class="[
              'entry-card',
              'nm-card',
              'nm-card--pad',
              'nm-reveal',
              item.invert ? 'nm-card--invert' : 'nm-card--hover',
            ]"
          >
            <h3 class="nm-h2">{{ item.title }}</h3>
            <p class="entry-card__desc">{{ item.desc }}</p>
            <div class="entry-card__tags">
              <span v-for="tag in item.tags" :key="tag" class="entry-card__tag">{{ tag }}</span>
            </div>
            <button type="button" class="nm-btn" @click="router.push(item.route)">
              {{ item.action }}
              <el-icon><ArrowRight /></el-icon>
            </button>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 功能模块 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">04 / 功能模块</span>
            <h2 class="nm-h2">常用功能，一步直达</h2>
          </div>
        </header>

        <div class="nm-auto-grid">
          <button
            v-for="(module, i) in consumerModules"
            :key="module.title"
            type="button"
            :class="['module-tile', 'nm-card', 'nm-card--hover', 'nm-reveal', `nm-delay-${(i % 4) + 1}`]"
            @click="openModule(module)"
          >
            <span class="module-tile__icon">
              <component :is="module.icon" />
            </span>
            <span class="module-tile__title">{{ module.title }}</span>
            <span class="module-tile__desc">{{ module.desc }}</span>
            <span class="module-tile__go">
              立即进入
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
        </div>

        <div class="admin-strip nm-reveal">
          <span class="admin-strip__label nm-kicker">管理后台</span>
          <div class="admin-strip__items">
            <button
              v-for="module in adminModules"
              :key="module.title"
              type="button"
              class="admin-strip__item"
              @click="openModule(module)"
            >
              <component :is="module.icon" />
              <span>{{ module.title }}</span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 学习场景 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">05 / 学习场景</span>
            <h2 class="nm-h2">覆盖学习的每个环节</h2>
          </div>
        </header>

        <div class="nm-grid nm-grid--2">
          <article
            v-for="(scene, i) in sceneCards"
            :key="scene.title"
            :class="['scene', 'nm-card', 'nm-reveal', `nm-delay-${i + 1}`]"
          >
            <div class="scene__media">
              <img :src="scene.image" :alt="scene.title" loading="lazy" @error="onImageError" />
            </div>
            <div class="scene__body">
              <span class="nm-badge nm-badge--neutral">{{ scene.badge }}</span>
              <h3 class="nm-h3">{{ scene.title }}</h3>
              <p>{{ scene.desc }}</p>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 学员评价 ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <header class="nm-section-head nm-reveal">
          <div class="nm-section-head__copy">
            <span class="nm-kicker">06 / 学员反馈</span>
            <h2 class="nm-h2">他们是这样学下来的</h2>
          </div>
        </header>

        <div class="nm-grid nm-grid--3">
          <figure
            v-for="(item, i) in testimonials"
            :key="item.name"
            :class="['quote', 'nm-card', 'nm-card--pad', 'nm-reveal', `nm-delay-${i + 1}`]"
          >
            <span class="quote__mark" aria-hidden="true">&ldquo;</span>
            <blockquote>{{ item.quote }}</blockquote>
            <figcaption>
              <img :src="item.avatar" :alt="item.name" loading="lazy" @error="onAvatarError" />
              <span>
                <strong>{{ item.name }}</strong>
                <em>{{ item.role }}</em>
              </span>
            </figcaption>
          </figure>
        </div>
      </div>
    </section>

    <!-- ============ 指标带 + 最终 CTA ============ -->
    <section class="nm-section">
      <div class="nm-shell">
        <div class="closing nm-card nm-card--invert nm-reveal">
          <div class="closing__metrics">
            <div v-for="signal in heroSignals" :key="signal.label" class="closing__metric">
              <span class="nm-num closing__value">{{ signal.value }}</span>
              <span class="closing__label">{{ signal.label }}</span>
              <span class="closing__detail">{{ signal.detail }}</span>
            </div>
          </div>

          <div class="closing__cta">
            <h2 class="nm-h1">开启你的学习之旅</h2>
            <p>选一门课，让 AI 陪你把它学完。</p>
            <div class="closing__actions">
              <button
                type="button"
                class="nm-btn nm-btn--primary nm-btn--lg"
                @click="router.push('/portal/courses')"
              >
                浏览课程
                <el-icon><ArrowRight /></el-icon>
              </button>
              <button
                type="button"
                class="nm-btn nm-btn--lg"
                @click="openAIWithPrompt(consolePrompts[0])"
              >
                AI 助手
              </button>
            </div>
            <div class="closing__prompts">
              <button
                v-for="prompt in consolePrompts.slice(0, 3)"
                :key="prompt"
                type="button"
                class="closing__prompt"
                @click="openAIWithPrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.home-page {
  padding-bottom: 8px;
}

/* ================= Hero ================= */
.hero {
  padding-block: clamp(32px, 4vw, 56px) clamp(40px, 5vw, 64px);
}

.hero__inner {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  gap: clamp(32px, 4vw, 64px);
}

.hero__title {
  margin-top: 18px;
  color: var(--nm-ink);
}

.hero__title-accent {
  position: relative;
  color: var(--nm-accent);
}

.hero__lede {
  margin-top: 18px;
  max-width: 46ch;
}

.hero__search {
  margin-top: 24px;
  max-width: 560px;
}

.hero__search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 6px 6px 16px;
  border: 1px solid var(--nm-line-strong);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  transition: border-color var(--nm-dur-fast) var(--nm-ease),
    box-shadow var(--nm-dur-fast) var(--nm-ease);
}

.hero__search-box:focus-within {
  border-color: var(--nm-accent);
  box-shadow: 0 0 0 3px var(--nm-accent-soft);
}

.hero__search-icon {
  color: var(--nm-ink-4);
  flex-shrink: 0;
}

.hero__search-input {
  flex: 1;
  min-width: 0;
  height: 42px;
  border: 0;
  background: transparent;
  color: var(--nm-ink);
  font-size: var(--nm-fs-body);
}

.hero__search-input::placeholder {
  color: var(--nm-ink-4);
}

.hero__search-input:focus {
  outline: none;
}

.hero__search-input::-webkit-search-cancel-button {
  appearance: none;
}

.hero__search-btn {
  flex-shrink: 0;
  min-height: 42px;
}

.hero__chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}

.hero__chips-label {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
  margin-right: 2px;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 40px;
  margin-top: 34px;
  padding-top: 24px;
  border-top: 1px solid var(--nm-line);
}

.hero__stat dt {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--nm-ink);
  line-height: 1.1;
}

.hero__stat dd {
  margin-top: 4px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

/* --- 产品视觉 --- */
.hero__visual {
  position: relative;
  padding: 22px 0 34px 26px;
}

.hero__feature {
  position: relative;
  z-index: 2;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-xl);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-4);
  overflow: hidden;
}

.hero__feature-media {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
}

.hero__feature-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero__feature-tag {
  position: absolute;
  right: 14px;
  top: 14px;
  padding: 4px 10px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  color: var(--nm-ink);
  font-family: var(--nm-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.hero__feature-body {
  padding: 18px 20px 20px;
}

.hero__feature-body h3 {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.hero__feature-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.hero__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--nm-ink-4);
}

.hero__feature-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.hero__feature-bar {
  flex: 1;
  height: 5px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  overflow: hidden;
}

.hero__feature-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--nm-accent);
}

/* 悬浮卡片 */
.hero__float {
  position: absolute;
  z-index: 3;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-3);
  padding: 14px 16px;
}

.hero__float-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--nm-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--nm-accent);
}

/* 悬浮卡片：只压在封面图上，不遮挡标题与进度条 */
.hero__float--chat {
  left: -16px;
  top: 30%;
  width: 244px;
}

.hero__float--chat p {
  margin-top: 10px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-2);
}

.hero__float-reply {
  display: block;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: var(--nm-r-sm);
  background: var(--nm-accent-soft);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  line-height: 1.5;
}

.hero__float--week {
  right: -16px;
  top: 4%;
  width: 152px;
}

.hero__bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 46px;
  margin-top: 12px;
}

.hero__bars i {
  flex: 1;
  border-radius: 2px 2px 0 0;
  background: var(--nm-line-strong);
}

.hero__bars i:nth-child(6) {
  background: var(--nm-accent);
}

.hero__float-value {
  display: block;
  margin-top: 8px;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--nm-ink);
}

/* ================= 方向跑马灯 ================= */
.topic-strip {
  overflow: hidden;
  border-block: 1px solid var(--nm-line);
  padding-block: 14px;
  user-select: none;
  mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
}

.topic-strip__track {
  display: flex;
  width: max-content;
  animation: topic-scroll 42s linear infinite;
}

.topic-strip:hover .topic-strip__track {
  animation-play-state: paused;
}

.topic-strip__set {
  display: inline-flex;
  align-items: center;
}

.topic-strip__item {
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-sm);
  font-weight: 550;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
  white-space: nowrap;
  padding-inline: 18px;
}

.topic-strip__sep {
  color: var(--nm-ink-4);
}

@keyframes topic-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

/* ================= 精选课程 ================= */
.course-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-1);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--nm-dur) var(--nm-ease), box-shadow var(--nm-dur) var(--nm-ease),
    border-color var(--nm-dur) var(--nm-ease);
}

.course-card:hover {
  transform: translateY(-4px);
  border-color: var(--nm-line-strong);
  box-shadow: var(--nm-sh-3);
}

.course-card__media {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--nm-surface-3);
}

.course-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s var(--nm-ease);
}

.course-card:hover .course-card__media img {
  transform: scale(1.05);
}

.course-card__badge {
  position: absolute;
  left: 12px;
  top: 12px;
  background: var(--nm-surface);
  color: var(--nm-ink-2);
}

.course-card__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 16px 18px 18px;
}

.course-card__title {
  font-size: 0.9875rem;
  font-weight: 700;
  line-height: 1.45;
  color: var(--nm-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
}

.course-card__teacher {
  margin-top: 8px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.course-card__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--nm-line);
}

.course-card__price em {
  font-style: normal;
  font-size: 1.125rem;
  font-weight: 750;
  color: var(--nm-ink);
}

.course-card__meta {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.course-skeleton__cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--nm-r-lg);
}

.course-skeleton__line {
  height: 13px;
  margin-top: 14px;
  border-radius: var(--nm-r-xs);
}

.course-skeleton__line--short {
  width: 55%;
}

/* ================= 学习路径 ================= */
.path {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  list-style: none;
  border-top: 1px solid var(--nm-line);
}

.path__step {
  position: relative;
  padding: 28px 26px 30px 0;
}

.path__step + .path__step {
  padding-left: 26px;
  border-left: 1px solid var(--nm-line);
}

.path__index {
  display: block;
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  letter-spacing: 0.16em;
  color: var(--nm-accent);
  margin-bottom: 14px;
}

.path__desc {
  margin-top: 10px;
  font-size: var(--nm-fs-sm);
  line-height: 1.7;
  color: var(--nm-ink-3);
}

.path__metric {
  display: inline-block;
  margin-top: 16px;
  padding: 4px 10px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 550;
}

/* ================= 双入口 ================= */
.entry-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.entry-card__desc {
  margin-top: 12px;
  max-width: 44ch;
}

/* 说明文字：默认墨色，反白卡内必须切到反白色阶，否则深底深字不可见 */
.entry-card:not(.nm-card--invert) .entry-card__desc {
  color: var(--nm-ink-2);
}

.entry-card.nm-card--invert .entry-card__desc {
  color: var(--nm-invert-fg-2);
}

.entry-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 20px 0 24px;
}

.entry-card__tag {
  padding: 5px 11px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 550;
}

.nm-card--invert .entry-card__tag {
  background: rgba(255, 255, 255, 0.07);
  border-color: var(--nm-invert-line);
  color: var(--nm-invert-fg-2);
}

.entry-card .nm-btn {
  margin-top: auto;
}

/* ================= 功能模块 ================= */
.module-tile {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 22px;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.module-tile__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-bottom: 12px;
  border-radius: var(--nm-r);
  background: var(--nm-surface-3);
  color: var(--nm-ink-2);
  transition: background-color var(--nm-dur) var(--nm-ease), color var(--nm-dur) var(--nm-ease);
}

.module-tile__icon svg {
  width: 19px;
  height: 19px;
}

.module-tile:hover .module-tile__icon {
  background: var(--nm-accent);
  color: var(--nm-accent-ink);
}

.module-tile__title {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.module-tile__desc {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
  line-height: 1.6;
}

.module-tile__go {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  color: var(--nm-accent);
  font-size: var(--nm-fs-sm);
  font-weight: 650;
}

.module-tile__go svg {
  width: 14px;
  height: 14px;
  transition: transform var(--nm-dur) var(--nm-ease);
}

.module-tile:hover .module-tile__go svg {
  transform: translateX(3px);
}

/* 管理后台条 */
.admin-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 28px;
  margin-top: 18px;
  padding: 18px 22px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface-2);
}

.admin-strip__label {
  flex-shrink: 0;
}

.admin-strip__items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.admin-strip__item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.admin-strip__item svg {
  width: 15px;
  height: 15px;
}

.admin-strip__item:hover {
  color: var(--nm-ink);
  border-color: var(--nm-line-strong);
  box-shadow: var(--nm-sh-2);
}

/* ================= 学习场景 ================= */
.scene {
  overflow: hidden;
}

.scene__media {
  aspect-ratio: 21 / 9;
  overflow: hidden;
  background: var(--nm-surface-3);
}

.scene__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.7s var(--nm-ease);
}

.scene:hover .scene__media img {
  transform: scale(1.04);
}

.scene__body {
  padding: 20px 22px 24px;
}

.scene__body h3 {
  margin-top: 12px;
}

.scene__body p {
  margin-top: 8px;
  font-size: var(--nm-fs-sm);
  line-height: 1.7;
  color: var(--nm-ink-3);
}

/* ================= 学员评价 ================= */
.quote {
  position: relative;
  display: flex;
  flex-direction: column;
}

.quote__mark {
  font-size: 2.75rem;
  line-height: 0.7;
  font-weight: 800;
  color: var(--nm-accent);
}

.quote blockquote {
  margin-top: 10px;
  color: var(--nm-ink-2);
  line-height: 1.75;
}

.quote figcaption {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: auto;
  padding-top: 22px;
}

.quote figcaption img {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.quote figcaption strong {
  display: block;
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-ink);
}

.quote figcaption em {
  display: block;
  font-style: normal;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* ================= 收尾 ================= */
.closing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr);
  align-items: center;
  gap: clamp(28px, 4vw, 56px);
  padding: clamp(28px, 4vw, 48px);
}

.closing__metrics {
  display: grid;
  gap: 1px;
  align-content: start;
  border: 1px solid var(--nm-invert-line);
  border-radius: var(--nm-r-lg);
  overflow: hidden;
  background: var(--nm-invert-line);
}

.closing__metric {
  padding: 18px 20px;
  background: var(--nm-invert-bg);
}

.closing__value {
  display: block;
  font-size: 1.5rem;
  font-weight: 750;
  color: var(--nm-invert-fg);
}

.closing__label {
  display: block;
  margin-top: 2px;
  font-size: var(--nm-fs-sm);
  font-weight: 650;
  color: var(--nm-invert-fg);
}

.closing__detail {
  display: block;
  margin-top: 6px;
  font-size: var(--nm-fs-xs);
  line-height: 1.6;
  color: var(--nm-invert-fg-2);
}

.closing__cta h2 {
  color: var(--nm-invert-fg);
}

.closing__cta > p {
  margin-top: 12px;
  color: var(--nm-invert-fg-2);
}

.closing__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.closing__prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid var(--nm-invert-line);
}

.closing__prompt {
  padding: 7px 13px;
  border: 1px solid var(--nm-invert-line);
  border-radius: var(--nm-r-full);
  background: transparent;
  color: var(--nm-invert-fg-2);
  font-size: var(--nm-fs-xs);
  cursor: pointer;
  text-align: left;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.closing__prompt:hover {
  border-color: var(--nm-invert-fg);
  color: var(--nm-invert-fg);
  background: rgba(255, 255, 255, 0.07);
}

/* ================= 响应式 ================= */
@media (max-width: 1180px) {
  .hero__inner {
    grid-template-columns: minmax(0, 1fr);
    gap: 44px;
  }

  .hero__visual {
    padding: 0 0 30px 0;
    max-width: 620px;
  }

  .hero__float--week {
    right: 0;
  }

  .path {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .path__step:nth-child(3) {
    border-left: none;
    padding-left: 0;
  }

  .path__step:nth-child(n + 3) {
    border-top: 1px solid var(--nm-line);
  }

  .closing {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .hero {
    padding-top: 16px;
  }

  .hero__stats {
    gap: 14px 28px;
  }

  .hero__stat dt {
    font-size: 1.35rem;
  }

  .hero__float {
    display: none;
  }

  .hero__visual {
    padding-bottom: 0;
  }

  .path {
    grid-template-columns: minmax(0, 1fr);
  }

  .path__step {
    padding: 22px 0;
  }

  .path__step + .path__step {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid var(--nm-line);
  }

  .admin-strip {
    align-items: flex-start;
  }

  .closing {
    padding: 22px 18px;
  }

  .hero__actions .nm-btn {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .topic-strip__track {
    animation: none;
  }
}
</style>

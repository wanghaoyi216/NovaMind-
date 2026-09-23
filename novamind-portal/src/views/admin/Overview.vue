<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  ArrowRight,
  Cpu,
  DataLine,
  Refresh,
  Reading,
  TrendCharts,
  User,
  Warning,
} from '@element-plus/icons-vue'
import { getAllSessions } from '@/api/aigc'
import { getCategoryList, searchPortalCourses } from '@/api/course'
import type { ApiResponse, CategoryItem, CourseSimpleInfo, PageResult } from '@/types'
import type { ChatSessionHistoryGroups, ChatSessionSummary } from '@/types/aigc'
import { formatYuan as money } from '@/utils/format'

interface Kpi {
  key: string
  label: string
  value: number
  prefix: string
  suffix: string
  delta: number
  series: number[]
  valueLive: boolean
  hint: string
}

interface ActivityItem {
  id: string
  title: string
  meta: string
  time: string
  kind: 'course' | 'user' | 'ai' | 'report'
  live: boolean
}

const router = useRouter()

const COVERS = [
  'course_java_programming_01.jpg',
  'course_microservices_01.jpg',
  'course_ai_deep_learning_01.jpg',
  'course_vue_web_development_01.jpg',
  'course_ui_ux_design_01.jpg',
  'course_data_analysis_01.jpg',
  'course_docker_kubernetes_01.jpg',
  'course_python_programming_01.jpg',
  'course_cloud_computing_01.jpg',
  'course_database_design_01.jpg',
  'course_mobile_development_01.jpg',
  'course_linux_admin_01.jpg',
]

const loading = ref(true)
const serviceOnline = ref(true)
const courses = ref<CourseSimpleInfo[]>([])
const courseTotal = ref(0)
const categories = ref<CategoryItem[]>([])
const sessions = ref<ChatSessionSummary[]>([])

const TREND_DAYS = 14
const TREND_W = 720
const TREND_H = 190

const trendSeries = ref<number[]>([])
const trendLabels = ref<string[]>([])

/* ---------- 折线 / 面积路径 ---------- */
function buildLine(series: number[], width: number, height: number, pad = 14) {
  const max = Math.max(...series, 1)
  const min = Math.min(...series, 0)
  const span = max - min || 1
  const step = series.length > 1 ? width / (series.length - 1) : width
  const points = series.map((value, index) => ({
    x: index * step,
    y: pad + (1 - (value - min) / span) * (height - pad * 2),
  }))
  const line = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
    .join(' ')
  return { line, area: `${line} L${width} ${height} L0 ${height} Z`, min, max }
}

function buildSpark(series: number[], width = 140, height = 40) {
  return buildLine(series, width, height, 6)
}

/* ---------- 演示序列（接口未提供趋势数据） ---------- */
function demoTrend(days = TREND_DAYS, seed = 7, base = 420, wave = 160): number[] {
  return Array.from({ length: days }, (_, index) => {
    const drift = index * (seed + 3)
    const wobble = Math.sin((index + seed) * 0.9) * wave * 0.5
    const pulse = Math.cos(index * 1.7 + seed) * wave * 0.28
    return Math.max(40, Math.round(base + drift + wobble + pulse))
  })
}

const kpis = computed<Kpi[]>(() => [
  {
    key: 'courses',
    label: '课程总数',
    value: courseTotal.value,
    prefix: '',
    suffix: '门',
    delta: 6.4,
    series: demoTrend(12, 3, 60, 22),
    valueLive: serviceOnline.value && courseTotal.value > 0,
    hint: '在架课程存量',
  },
  {
    key: 'students',
    label: '注册学员',
    value: 24860,
    prefix: '',
    suffix: '人',
    delta: 3.1,
    series: demoTrend(12, 5, 120, 40),
    valueLive: false,
    hint: '累计注册账号',
  },
  {
    key: 'sessions',
    label: 'AI 会话',
    value: sessions.value.length,
    prefix: '',
    suffix: '条',
    delta: 12.8,
    series: demoTrend(12, 9, 80, 30),
    valueLive: serviceOnline.value && sessions.value.length > 0,
    hint: '历史会话累计',
  },
  {
    key: 'revenue',
    label: '本月营收',
    value: 386420,
    prefix: '¥',
    suffix: '',
    delta: -2.4,
    series: demoTrend(12, 11, 140, 46),
    valueLive: false,
    hint: '含退款冲抵',
  },
])

const hasSample = computed(() => kpis.value.some((item) => !item.valueLive))

const axisLabels = computed(() => sampleEvenly(trendLabels.value, 7))

const trend = computed(() => {
  const series = trendSeries.value.length ? trendSeries.value : demoTrend()
  const chart = buildLine(series, TREND_W, TREND_H, 16)
  return {
    ...chart,
    series,
    labels: trendLabels.value,
    peak: Math.max(...series),
    low: Math.min(...series),
    average: Math.round(series.reduce((sum, value) => sum + value, 0) / series.length),
  }
})

const recentCourses = computed(() => courses.value.slice(0, 6))

const activities = computed<ActivityItem[]>(() => {
  const list: ActivityItem[] = []
  sessions.value.slice(0, 4).forEach((session) => {
    list.push({
      id: `ai-${session.sessionId}`,
      title: `AI 会话「${session.title || '未命名会话'}」有新消息`,
      meta: '智能助手',
      time: session.updateTime || '刚刚',
      kind: 'ai',
      live: true,
    })
  })
  courses.value.slice(0, 3).forEach((course) => {
    list.push({
      id: `course-${course.id}`,
      title: `课程「${course.name}」进入推荐池`,
      meta: course.categoryName || '未分类',
      time: '今日',
      kind: 'course',
      live: true,
    })
  })
  if (!list.length) {
    return [
      { id: 'demo-1', title: '课程「Vue 3 企业级实战」销量突破 1,200', meta: '前端开发', time: '10 分钟前', kind: 'course', live: false },
      { id: 'demo-2', title: '新学员注册高峰：今日新增 168 人', meta: '用户增长', time: '32 分钟前', kind: 'user', live: false },
      { id: 'demo-3', title: 'AI 会话「学习计划拆解」被收藏 42 次', meta: '智能助手', time: '1 小时前', kind: 'ai', live: false },
      { id: 'demo-4', title: '《数据报表》周报已生成', meta: '数据服务', time: '3 小时前', kind: 'report', live: false },
    ]
  }
  return list.slice(0, 7)
})

const feedIsSample = computed(() => activities.value.every((item) => !item.live))

function coverFor(course: CourseSimpleInfo, index: number): string {
  if (course.coverImg) return course.coverImg
  return `/resource/course-covers/${COVERS[index % COVERS.length]}`
}

// 金额统一走 @/utils/format（后端单位为分）

function compact(value: number): string {
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return value.toLocaleString('zh-CN')
}

/* 坐标轴只保留少量均匀采样标签，避免窄屏下标签挤压导致横向溢出 */
function sampleEvenly<T>(items: T[], max: number): T[] {
  if (items.length <= max) return items
  const step = (items.length - 1) / (max - 1)
  return Array.from({ length: max }, (_, index) => items[Math.round(index * step)])
}

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return (payload as ApiResponse<T>).data ?? null
  }
  return (payload as T) ?? null
}

function sessionList(payload: unknown): ChatSessionSummary[] {
  const groups = unwrap<ChatSessionHistoryGroups>(payload)
  if (!groups || typeof groups !== 'object' || Array.isArray(groups)) return []
  return Object.keys(groups).flatMap((key) => groups[key] ?? [])
}

function sampleCourses(): CourseSimpleInfo[] {
  return [
    { id: 1, name: 'Vue 3 企业级项目实战', coverImg: '', teacherName: '王思远', price: 199, sales: 1268, categoryId: 1, categoryName: '前端开发' },
    { id: 2, name: 'Spring Cloud 微服务架构', coverImg: '', teacherName: '李文博', price: 299, sales: 986, categoryId: 2, categoryName: '后端开发' },
    { id: 3, name: 'AI 应用开发实战', coverImg: '', teacherName: '张亦然', price: 259, sales: 1432, categoryId: 3, categoryName: '人工智能' },
    { id: 4, name: 'Docker 与 Kubernetes 运维', coverImg: '', teacherName: '陈默', price: 349, sales: 742, categoryId: 4, categoryName: '云原生' },
    { id: 5, name: '数据分析与可视化', coverImg: '', teacherName: '赵晴', price: 229, sales: 658, categoryId: 5, categoryName: '数据分析' },
    { id: 6, name: 'UI/UX 设计体系', coverImg: '', teacherName: '孙宁', price: 179, sales: 524, categoryId: 6, categoryName: '设计' },
  ]
}

function sampleSessions(): ChatSessionSummary[] {
  return [
    { sessionId: 'demo-s1', title: '课程下单建议', updateTime: dayjs().subtract(20, 'minute').format('YYYY-MM-DD HH:mm') },
    { sessionId: 'demo-s2', title: '学习计划拆解', updateTime: dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm') },
    { sessionId: 'demo-s3', title: '考试训练模式', updateTime: dayjs().subtract(1, 'day').format('YYYY-MM-DD HH:mm') },
  ]
}

function buildTrendAxis() {
  const today = dayjs()
  trendLabels.value = Array.from({ length: TREND_DAYS }, (_, index) =>
    today.subtract(TREND_DAYS - 1 - index, 'day').format('MM-DD')
  )
  trendSeries.value = demoTrend()
}

async function loadAll() {
  loading.value = true
  const [courseRes, categoryRes, sessionRes] = await Promise.allSettled([
    searchPortalCourses({ pageNo: 1, pageSize: 6 }),
    getCategoryList(),
    getAllSessions(),
  ])

  let online = false

  if (courseRes.status === 'fulfilled') {
    const page = unwrap<PageResult<CourseSimpleInfo>>(courseRes.value.data)
    courses.value = page?.list ?? []
    courseTotal.value = page?.total ?? courses.value.length
    online = true
  }

  if (categoryRes.status === 'fulfilled') {
    categories.value = unwrap<CategoryItem[]>(categoryRes.value.data) ?? []
    online = true
  }

  if (sessionRes.status === 'fulfilled') {
    sessions.value = sessionList(sessionRes.value)
    online = true
  }

  serviceOnline.value = online

  if (!courses.value.length) {
    courses.value = sampleCourses()
    if (!online) courseTotal.value = 128
  }
  if (!categories.value.length) {
    categories.value = [
      { id: 1, pid: 0, name: '前端开发', icon: '', level: 1 },
      { id: 2, pid: 0, name: '后端开发', icon: '', level: 1 },
      { id: 3, pid: 0, name: '人工智能', icon: '', level: 1 },
      { id: 4, pid: 0, name: '云原生', icon: '', level: 1 },
    ]
  }
  if (!sessions.value.length) sessions.value = sampleSessions()

  buildTrendAxis()
  loading.value = false
}

onMounted(loadAll)
</script>

<template>
  <div class="overview">
    <header class="page-head">
      <div class="page-head__copy">
        <span class="nm-kicker">运营总览</span>
        <h1 class="nm-h1">今天，平台在发生什么</h1>
        <p class="nm-lede">
          课程、学员、AI 会话与营收的核心指标，以及最近的课程与动态。
        </p>
      </div>
      <div class="page-head__acts">
        <button type="button" class="nm-btn nm-btn--sm" :disabled="loading" @click="loadAll">
          <el-icon :size="14"><Refresh /></el-icon>
          <span>刷新数据</span>
        </button>
        <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="router.push('/admin/reports')">
          <span>查看报表</span>
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>
    </header>

    <div v-if="!loading && !serviceOnline" class="notice notice--warn" role="status">
      <el-icon :size="16"><Warning /></el-icon>
      <p>
        后台服务当前不可达（课程 / 分类 / 会话接口均请求失败），本页数字为
        <strong>演示数据</strong>，仅用于展示界面结构，不代表真实经营情况。
      </p>
      <button type="button" class="nm-btn nm-btn--sm" @click="loadAll">重试</button>
    </div>

    <div v-else-if="!loading && hasSample" class="notice" role="status">
      <el-icon :size="16"><DataLine /></el-icon>
      <p>
        课程与 AI 会话指标来自接口实时数据；带「样例」标记的指标暂无对应后台接口，为演示数据。
      </p>
    </div>

    <section class="kpi-grid" aria-label="核心指标">
      <template v-if="loading">
        <article v-for="n in 4" :key="`kpi-skeleton-${n}`" class="nm-card nm-card--pad kpi">
          <div class="nm-skeleton" style="width: 76px; height: 12px"></div>
          <div class="nm-skeleton" style="width: 132px; height: 34px; margin-top: 14px"></div>
          <div class="nm-skeleton" style="width: 100%; height: 40px; margin-top: 16px"></div>
        </article>
      </template>
      <template v-else>
        <article v-for="item in kpis" :key="item.key" class="nm-card nm-card--pad nm-card--hover kpi">
          <div class="kpi__top">
            <span class="nm-kicker nm-kicker--plain kpi__label">{{ item.label }}</span>
            <span v-if="!item.valueLive" class="nm-badge nm-badge--neutral">样例</span>
            <span v-else class="nm-badge nm-badge--success">实时</span>
          </div>

          <p class="kpi__value nm-num">
            <span v-if="item.prefix" class="kpi__affix">{{ item.prefix }}</span>
            <span>{{ item.value > 9999 ? compact(item.value) : money(item.value) }}</span>
            <span v-if="item.suffix" class="kpi__affix">{{ item.suffix }}</span>
          </p>

          <div class="kpi__row">
            <span :class="['delta', item.delta >= 0 ? 'delta--up' : 'delta--down']">
              {{ item.delta >= 0 ? '▲' : '▼' }} {{ Math.abs(item.delta).toFixed(1) }}%
            </span>
            <span class="kpi__hint">{{ item.hint }}</span>
          </div>

          <svg
            class="spark"
            :viewBox="`0 0 140 40`"
            preserveAspectRatio="none"
            aria-hidden="true"
            focusable="false"
          >
            <defs>
              <linearGradient :id="`ov-spark-${item.key}`" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" :stop-color="item.delta >= 0 ? 'var(--nm-accent)' : 'var(--nm-danger)'" stop-opacity="0.26" />
                <stop offset="1" stop-color="var(--nm-accent)" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path :d="buildSpark(item.series).area" :fill="`url(#ov-spark-${item.key})`" />
            <path
              :d="buildSpark(item.series).line"
              fill="none"
              :stroke="item.delta >= 0 ? 'var(--nm-accent)' : 'var(--nm-danger)'"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
              vector-effect="non-scaling-stroke"
            />
          </svg>

          <p class="kpi__foot">近 12 日走势 · 样例序列</p>
        </article>
      </template>
    </section>

    <div class="split">
      <section class="nm-card panel">
        <header class="panel__head">
          <div>
            <h2 class="nm-h3">最近课程</h2>
            <p class="nm-muted panel__sub">
              按更新时间取最新的 6 门课程 · 覆盖 {{ categories.length }} 个分类
            </p>
          </div>
          <button type="button" class="nm-btn nm-btn--ghost nm-btn--sm" @click="router.push('/admin/courses')">
            <span>全部课程</span>
            <el-icon :size="13"><ArrowRight /></el-icon>
          </button>
        </header>

        <div v-if="loading" class="rows">
          <div v-for="n in 5" :key="`row-skeleton-${n}`" class="row-skeleton">
            <div class="nm-skeleton" style="width: 52px; height: 36px"></div>
            <div class="nm-skeleton" style="height: 12px; flex: 1"></div>
            <div class="nm-skeleton" style="width: 68px; height: 12px"></div>
          </div>
        </div>

        <div v-else class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">课程</th>
                <th scope="col">分类</th>
                <th scope="col">讲师</th>
                <th scope="col" class="is-num">价格</th>
                <th scope="col" class="is-num">销量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(course, index) in recentCourses" :key="course.id">
                <td>
                  <div class="cell-course">
                    <img class="thumb" :src="coverFor(course, index)" :alt="course.name" loading="lazy" />
                    <span class="cell-course__name">{{ course.name }}</span>
                  </div>
                </td>
                <td><span class="nm-tag">{{ course.categoryName || '未分类' }}</span></td>
                <td class="nm-muted">{{ course.teacherName || '待分配' }}</td>
                <td class="is-num nm-num">{{ money(course.price) }}</td>
                <td class="is-num nm-num">{{ money(course.sales) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="nm-card panel">
        <header class="panel__head">
          <div>
            <h2 class="nm-h3">近期动态</h2>
            <p class="nm-muted panel__sub">
              {{ feedIsSample ? '服务不可达，以下为演示动态' : '来自会话与课程接口的最新事件' }}
            </p>
          </div>
          <span v-if="feedIsSample" class="nm-badge nm-badge--neutral">样例</span>
        </header>

        <div v-if="loading" class="feed">
          <div v-for="n in 5" :key="`feed-skeleton-${n}`" class="feed__item">
            <span class="nm-skeleton" style="width: 26px; height: 26px; border-radius: 999px"></span>
            <div class="feed__copy">
              <div class="nm-skeleton" style="height: 11px; width: 78%"></div>
              <div class="nm-skeleton" style="height: 10px; width: 42%; margin-top: 7px"></div>
            </div>
          </div>
        </div>

        <ul v-else class="feed">
          <li v-for="item in activities" :key="item.id" class="feed__item">
            <span :class="['feed__dot', `feed__dot--${item.kind}`]" aria-hidden="true">
              <el-icon :size="13">
                <Cpu v-if="item.kind === 'ai'" />
                <Reading v-else-if="item.kind === 'course'" />
                <User v-else-if="item.kind === 'user'" />
                <TrendCharts v-else />
              </el-icon>
            </span>
            <div class="feed__copy">
              <p class="feed__title">{{ item.title }}</p>
              <p class="feed__meta nm-muted">
                <span>{{ item.meta }}</span>
                <span aria-hidden="true">·</span>
                <span class="nm-num">{{ item.time }}</span>
              </p>
            </div>
          </li>
        </ul>
      </section>
    </div>

    <section class="nm-card panel">
      <header class="panel__head">
        <div>
          <h2 class="nm-h3">近 14 日访问趋势</h2>
          <p class="nm-muted panel__sub">接口未提供趋势聚合，以下为演示序列</p>
        </div>
        <div class="chart-legend">
          <span class="nm-badge nm-badge--neutral">样例</span>
          <span class="nm-num chart-legend__stat">峰值 {{ compact(trend.peak) }}</span>
          <span class="nm-num chart-legend__stat">均值 {{ compact(trend.average) }}</span>
        </div>
      </header>

      <div v-if="loading" class="nm-skeleton" style="height: 190px"></div>
      <div v-else class="chart">
        <svg
          class="chart__svg"
          :viewBox="`0 0 ${TREND_W} ${TREND_H}`"
          preserveAspectRatio="none"
          role="img"
          aria-label="近 14 日访问趋势（演示序列）"
        >
          <defs>
            <linearGradient id="ov-trend-area" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="var(--nm-accent)" stop-opacity="0.22" />
              <stop offset="1" stop-color="var(--nm-accent)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <line v-for="n in 4" :key="`grid-${n}`" class="chart__grid" x1="0" :y1="(TREND_H / 4) * (n - 1)" :x2="TREND_W" :y2="(TREND_H / 4) * (n - 1)" vector-effect="non-scaling-stroke" />
          <path :d="trend.area" fill="url(#ov-trend-area)" />
          <path class="chart__line" :d="trend.line" vector-effect="non-scaling-stroke" />
        </svg>
        <div class="chart__axis">
          <span v-for="label in axisLabels" :key="label" class="nm-num">{{ label }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------------- 页头 ---------------- */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.page-head__copy {
  display: grid;
  gap: 10px;
}

.page-head__acts {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* ---------------- 提示条 ---------------- */
.notice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--nm-line);
  border-left: 3px solid var(--nm-accent);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
}

.notice--warn {
  border-left-color: var(--nm-warning);
  background: var(--nm-warning-soft);
  color: var(--nm-warning);
}

.notice p {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
}

.notice strong {
  font-weight: 700;
}

.notice .el-icon {
  flex-shrink: 0;
}

/* ---------------- KPI ---------------- */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kpi__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.kpi__label {
  color: var(--nm-ink-3);
}

.kpi__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: clamp(1.6rem, 2.4vw, 2.1rem);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.kpi__affix {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--nm-ink-3);
}

.kpi__row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: var(--nm-r-full);
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
}

.delta--up {
  background: var(--nm-success-soft);
  color: var(--nm-success);
}

.delta--down {
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
}

.kpi__hint {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.spark {
  width: 100%;
  height: 40px;
}

.kpi__foot {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

/* ---------------- 分栏 ---------------- */
.split {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 16px;
}

.panel {
  padding: clamp(16px, 2vw, 22px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.panel__sub {
  margin-top: 4px;
  font-size: var(--nm-fs-sm);
}

/* ---------------- 表格 ---------------- */
.table-wrap {
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--nm-fs-sm);
}

.table th,
.table td {
  padding: 11px 10px;
  text-align: left;
  border-bottom: 1px solid var(--nm-line);
  white-space: nowrap;
}

.table th {
  font-family: var(--nm-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.table tbody tr:last-child td {
  border-bottom: 0;
}

.table tbody tr:hover td {
  background: var(--nm-surface-2);
}

.table .is-num {
  text-align: right;
}

.cell-course {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
}

.thumb {
  width: 52px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--nm-r-xs);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.cell-course__name {
  font-weight: 600;
  color: var(--nm-ink);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rows {
  display: grid;
  gap: 10px;
}

.row-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ---------------- 动态 ---------------- */
.feed {
  display: grid;
  gap: 4px;
  list-style: none;
}

.feed__item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--nm-line);
}

.feed__item:last-child {
  border-bottom: 0;
}

.feed__dot {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  color: var(--nm-ink-3);
}

.feed__dot--ai {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.feed__dot--course {
  background: var(--nm-cyan-soft);
  color: var(--nm-cyan);
}

.feed__dot--user {
  background: var(--nm-success-soft);
  color: var(--nm-success);
}

.feed__copy {
  flex: 1;
  min-width: 0;
}

.feed__title {
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink);
  line-height: 1.5;
}

.feed__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: var(--nm-fs-xs);
}

/* ---------------- 图表 ---------------- */
.chart {
  display: grid;
  gap: 8px;
}

.chart__svg {
  width: 100%;
  height: 190px;
  overflow: visible;
}

.chart__grid {
  stroke: var(--nm-line);
  stroke-width: 1;
  stroke-dasharray: 3 5;
}

.chart__line {
  fill: none;
  stroke: var(--nm-accent);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.chart__axis {
  display: flex;
  gap: 2px;
  font-size: 10px;
  color: var(--nm-ink-4);
}

.chart__axis > span {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-legend__stat {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1180px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .split {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-head__acts {
    width: 100%;
  }

  .page-head__acts .nm-btn {
    flex: 1;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { DataLine, Reading, Refresh, Warning } from '@element-plus/icons-vue'
import { getAllSessions } from '@/api/aigc'
import { getCategoryList, searchPortalCourses } from '@/api/course'
import type { ApiResponse, CategoryItem, CourseSimpleInfo, PageResult } from '@/types'
import type { ChatSessionHistoryGroups, ChatSessionSummary } from '@/types/aigc'
import { formatYuan as money } from '@/utils/format'

interface ReportRow {
  id: number
  name: string
  coverImg: string
  categoryName: string
  price: number
  sales: number
  amount: number
}

interface MetricTile {
  key: string
  label: string
  value: string
  hint: string
  live: boolean
}

interface BarItem {
  id: number
  name: string
  sales: number
  x: number
  y: number
  width: number
  height: number
  highlight: boolean
}

const COVERS = [
  'course_java_programming_01.jpg',
  'course_microservices_01.jpg',
  'course_ai_deep_learning_01.jpg',
  'course_web_development_01.jpg',
  'course_ui_ux_design_01.jpg',
  'course_data_analysis_01.jpg',
  'course_docker_kubernetes_01.jpg',
  'course_python_programming_01.jpg',
  'course_cloud_computing_01.jpg',
  'course_database_design_01.jpg',
  'course_mobile_development_01.jpg',
  'course_linux_admin_01.jpg',
]

const CATEGORY_COLORS = [
  'var(--nm-accent)',
  'var(--nm-cyan)',
  'var(--nm-success)',
  'var(--nm-warning)',
  'var(--nm-ink-3)',
]

const TREND_W = 720
const TREND_H = 210
const BAR_W = 720
const BAR_H = 240
const BAR_BASE = BAR_H - 26
const TOP_N = 8
const TABLE_LIMIT = 20

const loading = ref(true)
const demoMode = ref(false)
const rows = ref<ReportRow[]>([])
const categories = ref<CategoryItem[]>([])
const sessions = ref<ChatSessionSummary[]>([])

const dateRange = ref<[string, string] | null>([
  dayjs().subtract(13, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])

const trendDays = computed(() => {
  const range = dateRange.value
  if (!range || !range[0] || !range[1]) return 14
  const diff = dayjs(range[1]).diff(dayjs(range[0]), 'day') + 1
  if (!Number.isFinite(diff)) return 14
  return Math.min(Math.max(diff, 3), 60)
})

const trendLabels = computed(() => {
  const start = dateRange.value?.[0] ? dayjs(dateRange.value[0]) : dayjs().subtract(trendDays.value - 1, 'day')
  return Array.from({ length: trendDays.value }, (_, index) => start.add(index, 'day').format('MM-DD'))
})

/* 坐标轴只保留少量均匀采样标签，避免长区间在窄屏挤压出横向滚动 */
const trendAxis = computed(() => sampleEvenly(trendLabels.value, 8))

/* 接口未提供按日聚合，趋势为演示序列，长度与所选区间一致 */
const trendSeries = computed(() => {
  const days = trendDays.value
  const seed = (days % 9) + 3
  return Array.from({ length: days }, (_, index) => {
    const drift = index * (seed + 4)
    const wobble = Math.sin((index + seed) * 0.8) * 90
    const pulse = Math.cos(index * 1.6 + seed) * 46
    return Math.max(60, Math.round(380 + drift + wobble + pulse))
  })
})

const trend = computed(() => {
  const series = trendSeries.value
  const width = TREND_W
  const height = TREND_H
  const pad = 16
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
  return {
    line,
    area: `${line} L${width} ${height} L0 ${height} Z`,
    peak: max,
    average: Math.round(series.reduce((sum, value) => sum + value, 0) / series.length),
    total: series.reduce((sum, value) => sum + value, 0),
  }
})

const totalSales = computed(() => rows.value.reduce((sum, row) => sum + row.sales, 0))
const totalAmount = computed(() => rows.value.reduce((sum, row) => sum + row.amount, 0))
const averagePrice = computed(() => (totalSales.value ? Math.round(totalAmount.value / totalSales.value) : 0))

const metrics = computed<MetricTile[]>(() => [
  {
    key: 'courses',
    label: '课程总数',
    value: `${rows.value.length}`,
    hint: demoMode.value ? '演示数据' : '来自课程搜索接口',
    live: !demoMode.value,
  },
  {
    key: 'categories',
    label: '覆盖分类',
    value: `${categories.value.length}`,
    hint: categories.value.length ? '来自分类接口' : '接口未返回',
    live: categories.value.length > 0,
  },
  {
    key: 'sales',
    label: '累计销量',
    value: totalSales.value.toLocaleString('zh-CN'),
    hint: '已加载课程销量合计',
    live: !demoMode.value,
  },
  {
    key: 'amount',
    label: '累计销售额',
    value: `¥${totalAmount.value.toLocaleString('zh-CN')}`,
    hint: '按 价格 × 销量 估算',
    live: !demoMode.value,
  },
  {
    key: 'aov',
    label: '平均客单价',
    value: `¥${averagePrice.value.toLocaleString('zh-CN')}`,
    hint: '销售额 / 销量',
    live: !demoMode.value,
  },
  {
    key: 'sessions',
    label: 'AI 会话',
    value: `${sessions.value.length}`,
    hint: sessions.value.length ? '来自会话接口' : '接口未返回',
    live: sessions.value.length > 0,
  },
])

const topCourses = computed(() => [...rows.value].sort((a, b) => b.sales - a.sales).slice(0, TOP_N))

const barItems = computed<BarItem[]>(() => {
  const items = topCourses.value
  const max = Math.max(...items.map((item) => item.sales), 1)
  const slot = BAR_W / Math.max(items.length, 1)
  const width = slot * 0.52
  return items.map((item, index) => {
    const height = Math.max(((item.sales / max) * (BAR_BASE - 16)), 2)
    return {
      id: item.id,
      name: item.name,
      sales: item.sales,
      x: index * slot + (slot - width) / 2,
      y: BAR_BASE - height,
      width,
      height,
      highlight: index === 0,
    }
  })
})

const categoryBreakdown = computed(() => {
  const bucket = new Map<string, number>()
  rows.value.forEach((row) => {
    const key = row.categoryName || '未分类'
    bucket.set(key, (bucket.get(key) ?? 0) + row.sales)
  })
  const total = Array.from(bucket.values()).reduce((sum, value) => sum + value, 0) || 1
  return Array.from(bucket, ([name, sales]) => ({ name, sales, percent: (sales / total) * 100 }))
    .sort((a, b) => b.sales - a.sales)
    .slice(0, 8)
    .map((item, index) => ({ ...item, color: CATEGORY_COLORS[index % CATEGORY_COLORS.length] }))
})

const tableRows = computed(() => [...rows.value].sort((a, b) => b.amount - a.amount).slice(0, TABLE_LIMIT))

const rangeLabel = computed(() => {
  const range = dateRange.value
  if (!range || !range[0] || !range[1]) return '未选择区间'
  return `${range[0]} 至 ${range[1]} · 共 ${trendDays.value} 天`
})

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return (payload as ApiResponse<T>).data ?? null
  }
  return (payload as T) ?? null
}

function coverFor(row: ReportRow, index: number): string {
  if (row.coverImg) return row.coverImg
  return `/resource/course-covers/${COVERS[index % COVERS.length]}`
}

// 金额统一走 @/utils/format（后端单位为分）

function compact(value: number): string {
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return value.toLocaleString('zh-CN')
}

function sampleEvenly<T>(items: T[], max: number): T[] {
  if (items.length <= max) return items
  const step = (items.length - 1) / (max - 1)
  return Array.from({ length: max }, (_, index) => items[Math.round(index * step)])
}

function toRow(course: CourseSimpleInfo): ReportRow {
  const price = course.price ?? 0
  const sales = course.sales ?? 0
  return {
    id: course.id,
    name: course.name,
    coverImg: course.coverImg || '',
    categoryName: course.categoryName || '未分类',
    price,
    sales,
    amount: price * sales,
  }
}

function sampleRows(): ReportRow[] {
  const base: CourseSimpleInfo[] = [
    { id: 201, name: 'Vue 3 企业级项目实战', coverImg: '', teacherName: '', price: 199, sales: 1268, categoryId: 1, categoryName: '前端开发' },
    { id: 202, name: 'Spring Cloud 微服务架构', coverImg: '', teacherName: '', price: 299, sales: 986, categoryId: 2, categoryName: '后端开发' },
    { id: 203, name: 'AI 应用开发实战', coverImg: '', teacherName: '', price: 259, sales: 1432, categoryId: 3, categoryName: '人工智能' },
    { id: 204, name: 'Docker 与 Kubernetes 运维', coverImg: '', teacherName: '', price: 349, sales: 742, categoryId: 4, categoryName: '云原生' },
    { id: 205, name: '数据分析与可视化', coverImg: '', teacherName: '', price: 229, sales: 658, categoryId: 5, categoryName: '数据分析' },
    { id: 206, name: 'UI/UX 设计体系', coverImg: '', teacherName: '', price: 179, sales: 524, categoryId: 6, categoryName: '设计' },
    { id: 207, name: 'Python 数据分析入门', coverImg: '', teacherName: '', price: 159, sales: 812, categoryId: 5, categoryName: '数据分析' },
    { id: 208, name: 'MySQL 数据库设计与优化', coverImg: '', teacherName: '', price: 219, sales: 430, categoryId: 7, categoryName: '数据库' },
    { id: 209, name: 'Linux 服务器运维实战', coverImg: '', teacherName: '', price: 189, sales: 366, categoryId: 4, categoryName: '云原生' },
    { id: 210, name: '移动端跨平台开发', coverImg: '', teacherName: '', price: 239, sales: 288, categoryId: 8, categoryName: '移动端' },
  ]
  return base.map(toRow)
}

function unwrapSessions(payload: unknown): ChatSessionSummary[] {
  const map: ChatSessionHistoryGroups | null =
    payload && typeof payload === 'object' && 'code' in payload && 'data' in payload
      ? ((payload as ApiResponse<ChatSessionHistoryGroups>).data ?? null)
      : (payload as ChatSessionHistoryGroups)
  if (!map || typeof map !== 'object' || Array.isArray(map)) return []
  return Object.keys(map).flatMap((key) => map[key] ?? [])
}

async function loadReports() {
  loading.value = true
  const [courseRes, categoryRes, sessionRes] = await Promise.allSettled([
    searchPortalCourses({ pageNo: 1, pageSize: 50 }),
    getCategoryList(),
    getAllSessions(),
  ])

  let online = false

  if (courseRes.status === 'fulfilled') {
    const page = unwrap<PageResult<CourseSimpleInfo>>(courseRes.value.data)
    const list = page?.list ?? []
    if (list.length) {
      rows.value = list.map(toRow)
      online = true
    }
  }
  if (categoryRes.status === 'fulfilled') {
    categories.value = unwrap<CategoryItem[]>(categoryRes.value.data) ?? []
    online = true
  }
  if (sessionRes.status === 'fulfilled') {
    sessions.value = unwrapSessions(sessionRes.value)
    online = true
  }

  demoMode.value = !online || !rows.value.length
  if (!rows.value.length) rows.value = sampleRows()
  loading.value = false
}

onMounted(loadReports)
</script>

<template>
  <div class="reports">
    <header class="page-head">
      <div class="page-head__copy">
        <span class="nm-kicker">数据报表</span>
        <h1 class="nm-h1">经营数据报表</h1>
        <p class="nm-lede">按时间区间查看学习趋势、课程销量与分类结构。</p>
      </div>
      <div class="page-head__acts">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
          class="range-picker"
        />
        <button type="button" class="nm-btn nm-btn--sm" :disabled="loading" @click="loadReports">
          <el-icon :size="14"><Refresh /></el-icon>
          <span>刷新</span>
        </button>
      </div>
    </header>

    <div v-if="demoMode" class="notice" role="status">
      <el-icon :size="16"><Warning /></el-icon>
      <p>
        后台服务不可达（课程 / 分类 / 会话接口请求失败），本页指标与图表为
        <strong>演示数据</strong>，仅用于展示报表结构，不代表真实经营情况。
      </p>
      <button type="button" class="nm-btn nm-btn--sm" @click="loadReports">重试</button>
    </div>

    <section class="metrics" aria-label="指标概览">
      <template v-if="loading">
        <article v-for="n in 6" :key="`metric-skeleton-${n}`" class="nm-card nm-card--pad metric">
          <div class="nm-skeleton" style="width: 70px; height: 11px"></div>
          <div class="nm-skeleton" style="width: 110px; height: 26px; margin-top: 12px"></div>
        </article>
      </template>
      <template v-else>
        <article v-for="item in metrics" :key="item.key" class="nm-card nm-card--pad metric">
          <div class="metric__top">
            <span class="nm-kicker nm-kicker--plain metric__label">{{ item.label }}</span>
            <span :class="['nm-badge', item.live ? 'nm-badge--success' : 'nm-badge--neutral']">
              {{ item.live ? '实时' : '样例' }}
            </span>
          </div>
          <p class="metric__value nm-num">{{ item.value }}</p>
          <p class="metric__hint">{{ item.hint }}</p>
        </article>
      </template>
    </section>

    <section class="nm-card panel">
      <header class="panel__head">
        <div>
          <h2 class="nm-h3">学习趋势</h2>
          <p class="nm-muted panel__sub">
            {{ rangeLabel }} · 接口未提供按日聚合，趋势为演示序列
          </p>
        </div>
        <div class="legend">
          <span class="nm-badge nm-badge--neutral">样例</span>
          <span class="nm-num legend__stat">峰值 {{ compact(trend.peak) }}</span>
          <span class="nm-num legend__stat">均值 {{ compact(trend.average) }}</span>
        </div>
      </header>

      <div v-if="loading" class="nm-skeleton" style="height: 210px"></div>
      <div v-else class="chart">
        <svg
          class="chart__svg"
          :viewBox="`0 0 ${TREND_W} ${TREND_H}`"
          preserveAspectRatio="none"
          role="img"
          :aria-label="`学习趋势（演示序列），${rangeLabel}`"
        >
          <defs>
            <linearGradient id="rp-trend-area" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="var(--nm-accent)" stop-opacity="0.22" />
              <stop offset="1" stop-color="var(--nm-accent)" stop-opacity="0" />
            </linearGradient>
          </defs>
          <line
            v-for="n in 4"
            :key="`rp-grid-${n}`"
            class="chart__grid"
            x1="0"
            :y1="(TREND_H / 4) * (n - 1)"
            :x2="TREND_W"
            :y2="(TREND_H / 4) * (n - 1)"
            vector-effect="non-scaling-stroke"
          />
          <path :d="trend.area" fill="url(#rp-trend-area)" />
          <path class="chart__line" :d="trend.line" vector-effect="non-scaling-stroke" />
        </svg>
        <div class="chart__axis chart__axis--trend">
          <span v-for="label in trendAxis" :key="label" class="nm-num">{{ label }}</span>
        </div>
      </div>
    </section>

    <div class="split">
      <section class="nm-card panel">
        <header class="panel__head">
          <div>
            <h2 class="nm-h3">课程销量 Top {{ TOP_N }}</h2>
            <p class="nm-muted panel__sub">按已加载课程的销量排序</p>
          </div>
          <span :class="['nm-badge', demoMode ? 'nm-badge--neutral' : 'nm-badge--success']">
            {{ demoMode ? '样例' : '实时' }}
          </span>
        </header>

        <div v-if="loading" class="nm-skeleton" style="height: 240px"></div>
        <div v-else-if="!barItems.length" class="nm-state">
          <span class="nm-state__icon"><el-icon :size="24"><Reading /></el-icon></span>
          <p class="nm-state__title">暂无销量数据</p>
          <p class="nm-state__desc">课程接口未返回可统计的销量字段。</p>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="loadReports">重新加载</button>
          </div>
        </div>
        <div v-else class="chart">
          <svg
            class="chart__svg chart__svg--bars"
            :viewBox="`0 0 ${BAR_W} ${BAR_H}`"
            preserveAspectRatio="none"
            role="img"
            aria-label="课程销量排行"
          >
            <line
              v-for="n in 4"
              :key="`bar-grid-${n}`"
              class="chart__grid"
              x1="0"
              :y1="(BAR_BASE / 4) * (n - 1)"
              :x2="BAR_W"
              :y2="(BAR_BASE / 4) * (n - 1)"
              vector-effect="non-scaling-stroke"
            />
            <line class="chart__base" x1="0" :y1="BAR_BASE" :x2="BAR_W" :y2="BAR_BASE" vector-effect="non-scaling-stroke" />
            <rect
              v-for="bar in barItems"
              :key="bar.id"
              :x="bar.x"
              :y="bar.y"
              :width="bar.width"
              :height="bar.height"
              :fill="bar.highlight ? 'var(--nm-cyan)' : 'var(--nm-accent)'"
              :opacity="bar.highlight ? 1 : 0.78"
            />
          </svg>
          <div class="chart__axis chart__axis--bars">
            <span v-for="bar in barItems" :key="`label-${bar.id}`" class="bar-label">
              <span class="bar-label__name">{{ bar.name }}</span>
              <span class="nm-num bar-label__value">{{ compact(bar.sales) }}</span>
            </span>
          </div>
        </div>
      </section>

      <section class="nm-card panel">
        <header class="panel__head">
          <div>
            <h2 class="nm-h3">分类销量占比</h2>
            <p class="nm-muted panel__sub">按课程分类聚合销量</p>
          </div>
          <span :class="['nm-badge', demoMode ? 'nm-badge--neutral' : 'nm-badge--success']">
            {{ demoMode ? '样例' : '实时' }}
          </span>
        </header>

        <div v-if="loading" class="nm-skeleton" style="height: 200px"></div>
        <ul v-else class="breakdown">
          <li v-for="item in categoryBreakdown" :key="item.name" class="breakdown__row">
            <div class="breakdown__head">
              <span class="breakdown__name">{{ item.name }}</span>
              <span class="nm-num breakdown__value">
                {{ compact(item.sales) }} · {{ item.percent.toFixed(1) }}%
              </span>
            </div>
            <div class="breakdown__track">
              <div
                class="breakdown__fill"
                :style="{ width: `${Math.max(item.percent, 2)}%`, background: item.color }"
              ></div>
            </div>
          </li>
        </ul>
      </section>
    </div>

    <section class="nm-card panel">
      <header class="panel__head">
        <div>
          <h2 class="nm-h3">明细数据</h2>
          <p class="nm-muted panel__sub">
            按销售额排序，展示前 <span class="nm-num">{{ TABLE_LIMIT }}</span> 门课程
          </p>
        </div>
        <span :class="['nm-badge', demoMode ? 'nm-badge--neutral' : 'nm-badge--success']">
          {{ demoMode ? '样例' : '实时' }}
        </span>
      </header>

      <div v-if="loading" class="rows">
        <div v-for="n in 6" :key="`report-skeleton-${n}`" class="row-skeleton">
          <div class="nm-skeleton" style="width: 56px; height: 36px"></div>
          <div class="nm-skeleton" style="height: 12px; flex: 2"></div>
          <div class="nm-skeleton" style="height: 12px; width: 72px"></div>
        </div>
      </div>

      <div v-else-if="!tableRows.length" class="nm-state">
        <span class="nm-state__icon"><el-icon :size="24"><DataLine /></el-icon></span>
        <p class="nm-state__title">暂无明细数据</p>
        <p class="nm-state__desc">没有可统计的课程记录，可重试拉取接口数据。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="loadReports">重新加载</button>
        </div>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th scope="col">课程</th>
              <th scope="col">分类</th>
              <th scope="col" class="is-num">价格</th>
              <th scope="col" class="is-num">销量</th>
              <th scope="col" class="is-num">销售额</th>
              <th scope="col">占比</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in tableRows" :key="row.id">
              <td>
                <div class="cell-course">
                  <img class="thumb" :src="coverFor(row, index)" :alt="row.name" loading="lazy" />
                  <span class="cell-course__name">{{ row.name }}</span>
                </div>
              </td>
              <td><span class="nm-tag">{{ row.categoryName }}</span></td>
              <td class="is-num nm-num">{{ money(row.price) }}</td>
              <td class="is-num nm-num">{{ money(row.sales) }}</td>
              <td class="is-num nm-num">{{ money(row.amount) }}</td>
              <td>
                <div class="share">
                  <div class="share__track">
                    <div
                      class="share__fill"
                      :style="{ width: `${totalAmount ? Math.max((row.amount / totalAmount) * 100, 1) : 1}%` }"
                    ></div>
                  </div>
                  <span class="nm-num share__value">
                    {{ totalAmount ? ((row.amount / totalAmount) * 100).toFixed(1) : '0.0' }}%
                  </span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.reports {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

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
  align-items: center;
}

.range-picker {
  width: 264px;
}

.notice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--nm-line);
  border-left: 3px solid var(--nm-warning);
  border-radius: var(--nm-r);
  background: var(--nm-warning-soft);
  color: var(--nm-warning);
  font-size: var(--nm-fs-sm);
}

.notice p {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
}

.notice .el-icon {
  flex-shrink: 0;
}

/* ---------------- 指标 ---------------- */
.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
  gap: 14px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric__label {
  color: var(--nm-ink-3);
}

.metric__value {
  font-size: clamp(1.3rem, 1.8vw, 1.6rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.metric__hint {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

/* ---------------- 面板 ---------------- */
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

.legend {
  display: flex;
  align-items: center;
  gap: 12px;
}

.legend__stat {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* ---------------- 图表 ---------------- */
.chart {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.chart__svg {
  width: 100%;
  height: 210px;
  overflow: visible;
}

.chart__svg--bars {
  height: 240px;
}

.chart__grid {
  stroke: var(--nm-line);
  stroke-width: 1;
  stroke-dasharray: 3 5;
}

.chart__base {
  stroke: var(--nm-line-strong);
  stroke-width: 1;
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
  gap: 4px;
  font-size: 10px;
  color: var(--nm-ink-4);
}

.chart__axis--trend > span {
  flex: 1 1 0;
  min-width: 0;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
}

.chart__axis--bars {
  align-items: flex-start;
}

.bar-label {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
  text-align: center;
  justify-items: center;
}

.bar-label__name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--nm-ink-3);
}

.bar-label__value {
  color: var(--nm-ink-4);
}

/* ---------------- 分类占比 ---------------- */
.breakdown {
  display: grid;
  gap: 13px;
  list-style: none;
}

.breakdown__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.breakdown__name {
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink);
}

.breakdown__value {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.breakdown__track {
  height: 8px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  overflow: hidden;
}

.breakdown__fill {
  height: 100%;
  border-radius: inherit;
  transition: width var(--nm-dur-slow) var(--nm-ease);
}

/* ---------------- 表格 ---------------- */
.split {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 16px;
}

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
  padding: 12px 10px;
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
  border-bottom-color: var(--nm-line-strong);
}

.table tbody tr:hover td {
  background: var(--nm-surface-2);
}

.table tbody tr:last-child td {
  border-bottom: 0;
}

.table .is-num {
  text-align: right;
}

.cell-course {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 230px;
}

.thumb {
  width: 56px;
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

.share {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 132px;
}

.share__track {
  flex: 1;
  height: 6px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  overflow: hidden;
}

.share__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--nm-accent);
}

.share__value {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
  min-width: 44px;
  text-align: right;
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

@media (max-width: 1180px) {
  .split {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-head__acts {
    width: 100%;
  }

  .range-picker {
    width: 100%;
  }
}
</style>

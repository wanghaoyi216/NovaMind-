<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteLesson, getMyLessons, getNowLearning } from '@/api/learning'
import type { LearningLesson } from '@/api/learning'
import sceneEmptyCourses from '@/assets/scenes/scene-empty-courses.svg?raw'

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

type FilterKey = 'all' | 'learning' | 'finished'

const router = useRouter()
const page = inject<MyPageContext | null>(MY_PAGE_CONTEXT, null)

const loading = ref(false)
const failed = ref(false)
const lessons = ref<LearningLesson[]>([])
const nowLearning = ref<LearningLesson | null>(null)
const filter = ref<FilterKey>('all')
const deletingId = ref<number | null>(null)

const filters: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'learning', label: '在学' },
  { key: 'finished', label: '已完成' },
]

/** 后端未返回封面时的真实图库兜底 */
const fallbackCovers = [
  '/resource/course-covers/course_web_development_01.jpg',
  '/resource/course-covers/course_spring_boot_01.jpg',
  '/resource/course-covers/course_ai_deep_learning_01.jpg',
  '/resource/course-covers/course_python_programming_01.jpg',
  '/resource/course-covers/course_microservices_01.jpg',
  '/resource/course-covers/course_ui_ux_design_01.jpg',
]

const counts = computed(() => ({
  all: lessons.value.length,
  learning: lessons.value.filter((item) => !item.finish).length,
  finished: lessons.value.filter((item) => item.finish).length,
}))

const visibleLessons = computed(() => {
  if (filter.value === 'learning') return lessons.value.filter((item) => !item.finish)
  if (filter.value === 'finished') return lessons.value.filter((item) => item.finish)
  return lessons.value
})

function coverOf(lesson: LearningLesson) {
  if (lesson.coverImg) return lesson.coverImg
  return fallbackCovers[Math.abs(lesson.courseId || 0) % fallbackCovers.length]
}

function onCoverError(event: Event, lesson: LearningLesson) {
  const target = event.target as HTMLImageElement | null
  if (!target) return
  const fallback = fallbackCovers[Math.abs(lesson.courseId || 0) % fallbackCovers.length]
  if (target.getAttribute('src') === fallback) return
  target.setAttribute('src', fallback)
}

function progressOf(lesson: LearningLesson) {
  const total = lesson.totalDuration || 0
  if (!total) return lesson.finish ? 100 : 0
  const pct = Math.round(((lesson.studiedDuration || 0) / total) * 100)
  return Math.min(100, Math.max(lesson.finish ? 100 : 1, pct))
}

function formatDuration(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds || 0))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  if (hours > 0) return minutes > 0 ? `${hours} 小时 ${minutes} 分` : `${hours} 小时`
  return `${minutes} 分钟`
}

function formatStudyDay(value: string) {
  if (!value) return '暂无记录'
  const stamp = new Date(String(value).replace(/-/g, '/')).getTime()
  if (Number.isNaN(stamp)) return value
  const days = Math.floor((Date.now() - stamp) / 86400000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days} 天前`
  return value.slice(0, 10)
}

function goCourse(lesson: LearningLesson) {
  router.push(`/portal/course/${lesson.courseId}`)
}

async function load() {
  loading.value = true
  failed.value = false
  const refreshAction = page?.actions.find((action) => action.key === 'refresh')
  if (refreshAction) refreshAction.loading = true
  try {
    const res: any = await getMyLessons({ pageNo: 1, pageSize: 60 })
    const list: LearningLesson[] = Array.isArray(res?.data?.list) ? res.data.list : []
    lessons.value = list
    nowLearning.value = list.find((item) => !item.finish) || null
    void loadNowLearning()
  } catch {
    lessons.value = []
    nowLearning.value = null
    failed.value = true
  } finally {
    loading.value = false
    if (refreshAction) refreshAction.loading = false
  }
}

/** 继续学习卡单独取「最近在学」，失败时沿用列表里的第一条在学记录 */
async function loadNowLearning() {
  try {
    const res: any = await getNowLearning()
    if (res?.data && !res.data.finish) nowLearning.value = res.data
  } catch {
    // 保持列表推导出的在学课程，不额外打扰用户
  }
}

async function handleDelete(lesson: LearningLesson) {
  try {
    await ElMessageBox.confirm(
      `删除后「${lesson.courseName}」的学习记录将不再展示，确定删除吗？`,
      '删除学习记录',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  deletingId.value = lesson.courseId
  try {
    await deleteLesson(lesson.courseId)
    lessons.value = lessons.value.filter((item) => item.courseId !== lesson.courseId)
    if (nowLearning.value?.courseId === lesson.courseId) {
      nowLearning.value = lessons.value.find((item) => !item.finish) || null
    }
    page?.refreshStats()
    ElMessage.success('已删除学习记录')
  } catch {
    ElMessage.error('删除失败，请稍后再试')
  } finally {
    deletingId.value = null
  }
}

if (page) {
  page.title = '我的课程'
  page.description = '继续未完成的进度，或回顾已经学完的课程。'
  page.actions = [
    {
      key: 'refresh',
      label: '刷新',
      variant: 'ghost',
      loading: false,
      onClick: () => {
        void load()
      },
    },
  ]
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="ls">
    <!-- 继续学习 -->
    <section v-if="loading || nowLearning" class="ls-hero">
      <div v-if="loading && !nowLearning" class="ls-hero__skeleton nm-card">
        <div class="nm-skeleton ls-hero__skeleton-cover" />
        <div class="ls-hero__skeleton-body">
          <div class="nm-skeleton" style="width: 92px; height: 12px" />
          <div class="nm-skeleton" style="width: 62%; height: 26px" />
          <div class="nm-skeleton" style="width: 44%; height: 14px" />
          <div class="nm-skeleton" style="width: 100%; height: 8px" />
          <div class="nm-skeleton" style="width: 148px; height: 46px" />
        </div>
      </div>

      <article v-else-if="nowLearning" class="ls-hero__card nm-card">
        <div class="ls-hero__media">
          <img
            :src="coverOf(nowLearning)"
            :alt="nowLearning.courseName"
            loading="lazy"
            @error="onCoverError($event, nowLearning)"
          />
        </div>
        <div class="ls-hero__body">
          <p class="nm-kicker">CONTINUE</p>
          <h2 class="ls-hero__title nm-h2">{{ nowLearning.courseName }}</h2>
          <p class="ls-hero__meta">
            <span class="nm-muted">上次学到</span>
            <span class="ls-hero__section">{{ nowLearning.lastSectionName || '第 1 节' }}</span>
            <span class="ls-hero__dot" aria-hidden="true">·</span>
            <span class="nm-muted">{{ formatStudyDay(nowLearning.learnDate) }}</span>
          </p>

          <div class="ls-progress">
            <div class="ls-progress__head">
              <span class="nm-muted">学习进度</span>
              <span class="nm-num ls-progress__pct">{{ progressOf(nowLearning) }}%</span>
            </div>
            <div class="ls-track">
              <span class="ls-track__fill" :style="{ width: `${progressOf(nowLearning)}%` }" />
            </div>
            <p class="ls-progress__foot nm-num">
              已学 {{ formatDuration(nowLearning.studiedDuration) }} / 共
              {{ formatDuration(nowLearning.totalDuration) }}
            </p>
          </div>

          <div class="ls-hero__actions">
            <button type="button" class="nm-btn nm-btn--accent nm-btn--lg" @click="goCourse(nowLearning)">
              继续学习
            </button>
            <button type="button" class="nm-btn nm-btn--ghost" @click="router.push('/portal/courses')">
              去课程中心
            </button>
          </div>
        </div>
      </article>
    </section>

    <!-- 过滤 -->
    <div class="ls-bar">
      <div class="ls-tabs" role="tablist" aria-label="课程筛选">
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
          <span class="nm-num ls-tabs__count">{{ counts[item.key] }}</span>
        </button>
      </div>
      <p class="ls-bar__hint nm-muted nm-num">
        共 {{ counts.all }} 门 · 已完成 {{ counts.finished }}
      </p>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading" class="ls-grid" aria-busy="true">
      <div v-for="index in 6" :key="index" class="ls-card nm-card">
        <div class="nm-skeleton ls-card__cover" />
        <div class="ls-card__body">
          <div class="nm-skeleton" style="width: 76%; height: 18px" />
          <div class="nm-skeleton" style="width: 52%; height: 13px" />
          <div class="nm-skeleton" style="width: 100%; height: 7px" />
          <div class="nm-skeleton" style="width: 120px; height: 34px" />
        </div>
      </div>
    </div>

    <!-- 错误态 -->
    <div v-else-if="failed" class="nm-state">
      <span class="nm-state__icon" aria-hidden="true">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M12 8v5" stroke-linecap="round" />
          <circle cx="12" cy="16.4" r="0.9" fill="currentColor" stroke="none" />
          <path d="M12 3.6 21 19.2H3Z" stroke-linejoin="round" />
        </svg>
      </span>
      <p class="nm-state__title">课程数据暂时取不到</p>
      <p class="nm-state__desc">学习服务可能还没启动，稍后重试即可；你的学习进度不会丢失。</p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="load">重新加载</button>
        <button type="button" class="nm-btn nm-btn--ghost" @click="router.push('/portal/courses')">
          去课程中心
        </button>
      </div>
    </div>

    <!-- 空态 -->
    <div v-else-if="!visibleLessons.length" class="nm-state">
      <span class="ls-state__scene" aria-hidden="true" v-html="sceneEmptyCourses" />
      <p class="nm-state__title">
        {{ filter === 'all' ? '还没有课程' : filter === 'learning' ? '没有在学课程' : '还没有学完的课程' }}
      </p>
      <p class="nm-state__desc">
        {{
          filter === 'finished'
            ? '学完一门课后，它会出现在这里，方便你随时回顾。'
            : '从课程中心挑一门感兴趣的课，学习进度会自动同步到这里。'
        }}
      </p>
      <div class="nm-state__actions">
        <button type="button" class="nm-btn nm-btn--primary" @click="router.push('/portal/courses')">
          去选课
        </button>
      </div>
    </div>

    <!-- 课程网格 -->
    <div v-else class="ls-grid">
      <article
        v-for="lesson in visibleLessons"
        :key="lesson.id ?? lesson.courseId"
        class="ls-card nm-card nm-card--hover"
      >
        <button type="button" class="ls-card__media" @click="goCourse(lesson)">
          <img
            :src="coverOf(lesson)"
            :alt="lesson.courseName"
            loading="lazy"
            @error="onCoverError($event, lesson)"
          />
          <span v-if="lesson.finish" class="ls-card__done nm-badge nm-badge--success">已完成</span>
          <span v-else class="ls-card__pct nm-badge nm-badge--neutral nm-num">
            {{ progressOf(lesson) }}%
          </span>
        </button>

        <div class="ls-card__body">
          <h3 class="ls-card__title">{{ lesson.courseName }}</h3>
          <p class="ls-card__meta">
            <span class="ls-card__meta-label nm-muted">上次学到</span>
            <span class="ls-card__meta-value">{{ lesson.lastSectionName || '第 1 节' }}</span>
          </p>
          <p class="ls-card__date nm-muted nm-num">
            {{ formatStudyDay(lesson.learnDate) }} · {{ formatDuration(lesson.studiedDuration) }}
          </p>

          <div class="ls-track ls-track--sm">
            <span class="ls-track__fill" :style="{ width: `${progressOf(lesson)}%` }" />
          </div>

          <div class="ls-card__actions">
            <button type="button" class="nm-btn nm-btn--sm nm-btn--primary" @click="goCourse(lesson)">
              {{ lesson.finish ? '重新学习' : '继续学习' }}
            </button>
            <button
              type="button"
              class="nm-btn nm-btn--sm nm-btn--ghost ls-card__del"
              :disabled="deletingId === lesson.courseId"
              @click="handleDelete(lesson)"
            >
              {{ deletingId === lesson.courseId ? '删除中…' : '删除' }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.ls {
  display: grid;
  gap: clamp(20px, 2.4vw, 30px);
}

/* 禁用态（设计系统只定义常态，这里按页面需要补齐） */
.nm-btn:disabled,
.nm-btn:disabled:hover {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ---------------- 继续学习 ---------------- */
.ls-hero__card {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  overflow: hidden;
}

.ls-hero__media {
  position: relative;
  min-height: 240px;
  background: var(--nm-surface-3);
}

.ls-hero__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ls-hero__media::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 42%,
    color-mix(in srgb, var(--nm-surface) 82%, transparent) 100%
  );
  pointer-events: none;
}

.ls-hero__body {
  display: grid;
  align-content: center;
  gap: 12px;
  padding: clamp(22px, 2.6vw, 34px);
}

.ls-hero__title {
  margin-top: 2px;
}

.ls-hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  font-size: var(--nm-fs-sm);
}

.ls-hero__section {
  font-weight: 620;
  color: var(--nm-ink-2);
}

.ls-hero__dot {
  color: var(--nm-ink-4);
}

.ls-progress {
  display: grid;
  gap: 7px;
  margin-top: 4px;
  max-width: 460px;
}

.ls-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: var(--nm-fs-sm);
}

.ls-progress__pct {
  font-weight: 700;
  color: var(--nm-ink);
}

.ls-progress__foot {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.ls-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.ls-hero__skeleton {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  overflow: hidden;
}

.ls-hero__skeleton-cover {
  min-height: 240px;
  border-radius: 0;
}

.ls-hero__skeleton-body {
  display: grid;
  align-content: center;
  gap: 14px;
  padding: clamp(22px, 2.6vw, 34px);
}

/* 进度条（自绘，完全跟随令牌） */
.ls-track {
  position: relative;
  height: 8px;
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-3);
  overflow: hidden;
}

.ls-track--sm {
  height: 6px;
}

.ls-track__fill {
  display: block;
  height: 100%;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
  transition: width var(--nm-dur-slow) var(--nm-ease);
}

/* ---------------- 过滤条 ---------------- */
.ls-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.ls-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ls-tabs__count {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.nm-chip.is-active .ls-tabs__count {
  color: var(--nm-paper);
}

[data-theme='dark'] .nm-chip.is-active .ls-tabs__count {
  color: var(--nm-accent-ink);
}

.ls-bar__hint {
  font-size: var(--nm-fs-xs);
}

/* ---------------- 课程网格 ---------------- */
.ls-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(auto-fill, minmax(268px, 1fr));
}

.ls-card {
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.ls-card__media {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  padding: 0;
  border: 0;
  background: var(--nm-surface-3);
  cursor: pointer;
  overflow: hidden;
}

.ls-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--nm-dur-slow) var(--nm-ease);
}

.ls-card:hover .ls-card__media img {
  transform: scale(1.035);
}

.ls-card__done,
.ls-card__pct {
  position: absolute;
  top: 10px;
  right: 10px;
}

.ls-card__pct {
  background: color-mix(in srgb, var(--nm-surface) 88%, transparent);
  color: var(--nm-ink-2);
}

.ls-card__body {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 16px;
}

.ls-card__title {
  font-size: 1rem;
  font-weight: 660;
  letter-spacing: -0.015em;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ls-card__meta {
  display: flex;
  gap: 6px;
  font-size: var(--nm-fs-sm);
  min-width: 0;
}

.ls-card__meta-label {
  flex-shrink: 0;
}

.ls-card__meta-value {
  color: var(--nm-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ls-card__date {
  font-size: var(--nm-fs-xs);
}

.ls-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.ls-card__del:hover {
  color: var(--nm-danger);
}

.ls-card__cover {
  aspect-ratio: 16 / 9;
  border-radius: 0;
}

/* ---------------- 状态 ---------------- */
.ls-state__scene {
  display: block;
  width: 168px;
  max-width: 60%;
}

.ls-state__scene :deep(svg) {
  width: 100%;
  height: auto;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .ls-hero__card,
  .ls-hero__skeleton {
    grid-template-columns: minmax(0, 1fr);
  }

  .ls-hero__media,
  .ls-hero__skeleton-cover {
    min-height: 168px;
    aspect-ratio: 16 / 9;
  }

  .ls-hero__media::after {
    background: linear-gradient(
      180deg,
      transparent 55%,
      color-mix(in srgb, var(--nm-surface) 82%, transparent) 100%
    );
  }
}

@media (max-width: 560px) {
  .ls-hero__actions .nm-btn {
    flex: 1;
  }

  .ls-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

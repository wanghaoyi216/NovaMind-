<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Edit, Plus, Refresh, Search, View, Warning } from '@element-plus/icons-vue'
import {
  checkBeforeUpShelf,
  downShelf,
  getCategoryList,
  getCourseBaseInfo,
  searchPortalCourses,
  upShelf,
} from '@/api/course'
import { friendlyErrorMessage } from '@/utils/errorMessage'
import type { ApiResponse, CategoryItem, CourseDetailInfo, CourseSimpleInfo, PageResult } from '@/types'
import { formatYuan as money } from '@/utils/format'

type ShelfStatus = 'ON' | 'OFF'

interface CourseRow {
  id: number
  name: string
  coverImg: string
  teacherName: string
  price: number
  sales: number
  categoryId: number
  categoryName: string
  status: ShelfStatus
  draft: boolean
}

interface EditorForm {
  id: number
  name: string
  categoryId: number
  teacherName: string
  price: number
  sales: number
}

const route = useRoute()

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

const PAGE_SIZE = 8
const FETCH_SIZE = 60

const loading = ref(true)
const demoMode = ref(false)
const rows = ref<CourseRow[]>([])
const categories = ref<CategoryItem[]>([])
const keyword = ref('')
const categoryFilter = ref<number | undefined>(undefined)
const statusFilter = ref<string>('')
const currentPage = ref(1)
const acting = ref<number | null>(null)
const tableRef = ref<HTMLElement | null>(null)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailRow = ref<CourseRow | null>(null)
const detail = ref<CourseDetailInfo | null>(null)
const detailFallback = ref(false)

const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editorSaving = ref(false)
const form = reactive<EditorForm>({
  id: 0,
  name: '',
  categoryId: 0,
  teacherName: '',
  price: 0,
  sales: 0,
})

let draftSeq = 0

const categoryOptions = computed(() => {
  const seen = new Map<number, string>()
  rows.value.forEach((row) => {
    if (!seen.has(row.categoryId)) seen.set(row.categoryId, row.categoryName || '未分类')
  })
  return Array.from(seen, ([id, name]) => ({ id, name }))
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const catKey = categoryFilter.value ? String(categoryFilter.value) : ''
  const statusKey = statusFilter.value || ''
  return rows.value.filter((row) => {
    if (kw && !`${row.name}${row.teacherName}${row.categoryName}`.toLowerCase().includes(kw)) return false
    if (catKey && String(row.categoryId) !== catKey) return false
    if (statusKey && row.status !== statusKey) return false
    return true
  })
})

const paged = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const onShelfCount = computed(() => rows.value.filter((row) => row.status === 'ON').length)
const editorTitle = computed(() => (editorMode.value === 'create' ? '新建课程' : '编辑课程'))

watch([keyword, categoryFilter, statusFilter], () => {
  currentPage.value = 1
})

watch(
  () => route.query.keyword,
  (value) => {
    if (typeof value === 'string' && value !== keyword.value) keyword.value = value
  },
  { immediate: true }
)

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return (payload as ApiResponse<T>).data ?? null
  }
  return (payload as T) ?? null
}

function reason(error: unknown): string {
  if (error instanceof Error && error.message) return friendlyErrorMessage(error)
  return '服务暂不可达'
}

function coverFor(row: CourseRow, index: number): string {
  if (row.coverImg) return row.coverImg
  return `/resource/course-covers/${COVERS[index % COVERS.length]}`
}


function defaultStatus(index: number): ShelfStatus {
  return index % 5 === 3 ? 'OFF' : 'ON'
}

function toRow(course: CourseSimpleInfo, index: number): CourseRow {
  return {
    id: course.id,
    name: course.name,
    coverImg: course.coverImg || '',
    teacherName: course.teacherName || '',
    price: course.price ?? 0,
    sales: course.sales ?? 0,
    categoryId: course.categoryId ?? 0,
    categoryName: course.categoryName || '未分类',
    status: defaultStatus(index),
    draft: false,
  }
}

function sampleRows(): CourseRow[] {
  const base: CourseSimpleInfo[] = [
    { id: 101, name: 'Vue 3 企业级项目实战', coverImg: '', teacherName: '王思远', price: 199, sales: 1268, categoryId: 1, categoryName: '前端开发' },
    { id: 102, name: 'Spring Cloud 微服务架构', coverImg: '', teacherName: '李文博', price: 299, sales: 986, categoryId: 2, categoryName: '后端开发' },
    { id: 103, name: 'AI 应用开发实战', coverImg: '', teacherName: '张亦然', price: 259, sales: 1432, categoryId: 3, categoryName: '人工智能' },
    { id: 104, name: 'Docker 与 Kubernetes 运维', coverImg: '', teacherName: '陈默', price: 349, sales: 742, categoryId: 4, categoryName: '云原生' },
    { id: 105, name: '数据分析与可视化', coverImg: '', teacherName: '赵晴', price: 229, sales: 658, categoryId: 5, categoryName: '数据分析' },
    { id: 106, name: 'UI/UX 设计体系', coverImg: '', teacherName: '孙宁', price: 179, sales: 524, categoryId: 6, categoryName: '设计' },
    { id: 107, name: 'Python 数据分析入门', coverImg: '', teacherName: '周宁', price: 159, sales: 812, categoryId: 5, categoryName: '数据分析' },
    { id: 108, name: 'MySQL 数据库设计与优化', coverImg: '', teacherName: '吴桐', price: 219, sales: 430, categoryId: 7, categoryName: '数据库' },
    { id: 109, name: 'Linux 服务器运维实战', coverImg: '', teacherName: '郑航', price: 189, sales: 366, categoryId: 4, categoryName: '云原生' },
    { id: 110, name: '移动端跨平台开发', coverImg: '', teacherName: '林悦', price: 239, sales: 288, categoryId: 8, categoryName: '移动端' },
  ]
  return base.map((course, index) => toRow(course, index))
}

async function loadCourses() {
  loading.value = true
  try {
    const res = await searchPortalCourses({ pageNo: 1, pageSize: FETCH_SIZE })
    const page = unwrap<PageResult<CourseSimpleInfo>>(res.data)
    const list = page?.list ?? []
    if (list.length) {
      rows.value = list.map((course, index) => toRow(course, index))
      demoMode.value = false
    } else {
      rows.value = sampleRows()
      demoMode.value = true
    }
  } catch {
    rows.value = sampleRows()
    demoMode.value = true
  }
  currentPage.value = 1
  loading.value = false
  void loadCategories()
}

async function loadCategories() {
  try {
    const res = await getCategoryList()
    categories.value = unwrap<CategoryItem[]>(res.data) ?? []
  } catch {
    categories.value = []
  }
}

async function confirmAction(message: string, title: string, confirmText: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

async function onUpShelf(row: CourseRow) {
  if (row.status === 'ON') return
  if (!(await confirmAction(`确认上架「${row.name}」？上架前会先执行校验。`, '上架确认', '确认上架'))) return
  const previous = row.status
  row.status = 'ON'
  acting.value = row.id
  try {
    await checkBeforeUpShelf(row.id)
    await upShelf(row.id)
    ElMessage.success(`「${row.name}」已上架`)
  } catch (error) {
    row.status = previous
    ElMessage.error(`上架失败，状态已回滚：${reason(error)}`)
  } finally {
    acting.value = null
  }
}

async function onDownShelf(row: CourseRow) {
  if (row.status === 'OFF') return
  if (!(await confirmAction(`确认下架「${row.name}」？下架后学员端将不可见。`, '下架确认', '确认下架'))) return
  const previous = row.status
  row.status = 'OFF'
  acting.value = row.id
  try {
    await downShelf(row.id)
    ElMessage.success(`「${row.name}」已下架`)
  } catch (error) {
    row.status = previous
    ElMessage.error(`下架失败，状态已回滚：${reason(error)}`)
  } finally {
    acting.value = null
  }
}

async function openDetail(row: CourseRow) {
  detailRow.value = row
  detail.value = null
  detailFallback.value = false
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getCourseBaseInfo(row.id)
    const info = unwrap<CourseDetailInfo>(res.data)
    if (info && info.name) {
      detail.value = info
    } else {
      detailFallback.value = true
    }
  } catch {
    detailFallback.value = true
  }
  detailLoading.value = false
}

function openEditor(row?: CourseRow) {
  if (row) {
    editorMode.value = 'edit'
    form.id = row.id
    form.name = row.name
    form.categoryId = row.categoryId
    form.teacherName = row.teacherName
    form.price = row.price
    form.sales = row.sales
  } else {
    editorMode.value = 'create'
    form.id = 0
    form.name = ''
    form.categoryId = categoryOptions.value[0]?.id ?? 0
    form.teacherName = ''
    form.price = 0
    form.sales = 0
  }
  editorOpen.value = true
}

function saveEditor() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写课程名称')
    return
  }
  editorSaving.value = true
  const categoryName =
    categoryOptions.value.find((item) => item.id === form.categoryId)?.name ||
    categories.value.find((item) => item.id === form.categoryId)?.name ||
    '未分类'

  if (editorMode.value === 'edit') {
    const target = rows.value.find((item) => item.id === form.id)
    if (target) {
      target.name = form.name.trim()
      target.categoryId = form.categoryId
      target.categoryName = categoryName
      target.teacherName = form.teacherName.trim()
      target.price = Number(form.price) || 0
      target.sales = Number(form.sales) || 0
    }
    ElMessage.success('已更新本地展示数据（课程更新接口未接入，未持久化）')
  } else {
    draftSeq += 1
    rows.value.unshift({
      id: -draftSeq,
      name: form.name.trim(),
      coverImg: '',
      teacherName: form.teacherName.trim() || '待分配',
      price: Number(form.price) || 0,
      sales: Number(form.sales) || 0,
      categoryId: form.categoryId,
      categoryName,
      status: 'OFF',
      draft: true,
    })
    currentPage.value = 1
    ElMessage.warning('已加入草稿列表（课程创建接口未接入，未持久化）')
  }
  editorSaving.value = false
  editorOpen.value = false
}

function resetFilters() {
  keyword.value = ''
  categoryFilter.value = undefined
  statusFilter.value = ''
}

function onPageChange() {
  tableRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(loadCourses)
</script>

<template>
  <div class="courses">
    <header class="page-head">
      <div class="page-head__copy">
        <span class="nm-kicker">课程管理</span>
        <h1 class="nm-h1">课程库</h1>
        <p class="nm-lede">检索、上架与维护平台课程。上下架操作会调用课程服务接口。</p>
      </div>
      <div class="page-head__acts">
        <button type="button" class="nm-btn nm-btn--sm" :disabled="loading" @click="loadCourses">
          <el-icon :size="14"><Refresh /></el-icon>
          <span>刷新</span>
        </button>
        <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="openEditor()">
          <el-icon :size="14"><Plus /></el-icon>
          <span>新建课程</span>
        </button>
      </div>
    </header>

    <div v-if="demoMode" class="notice" role="status">
      <el-icon :size="16"><Warning /></el-icon>
      <p>
        课程服务不可达（<span class="nm-num">GET /ss/courses/portal</span> 请求失败），下表为
        <strong>演示数据</strong>，仅用于展示界面结构。
      </p>
      <button type="button" class="nm-btn nm-btn--sm" @click="loadCourses">重试</button>
    </div>

    <section ref="tableRef" class="nm-card panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          class="toolbar__search"
          placeholder="搜索课程名称 / 讲师 / 分类"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="categoryFilter" class="toolbar__select" placeholder="全部分类" clearable>
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="statusFilter" class="toolbar__select" placeholder="全部状态" clearable>
          <el-option label="已上架" value="ON" />
          <el-option label="已下架" value="OFF" />
        </el-select>
        <div class="toolbar__spacer"></div>
        <p class="toolbar__meta nm-muted">
          共 <span class="nm-num">{{ filtered.length }}</span> 门 · 已上架
          <span class="nm-num">{{ onShelfCount }}</span> 门
        </p>
      </div>

      <p class="hint nm-muted">
        上下架状态为本地维护（课程接口未返回该字段），操作会调用真实接口并在失败时回滚。
      </p>

      <div v-if="loading" class="rows">
        <div v-for="n in 6" :key="`course-skeleton-${n}`" class="row-skeleton">
          <div class="nm-skeleton" style="width: 62px; height: 40px"></div>
          <div class="nm-skeleton" style="height: 12px; flex: 2"></div>
          <div class="nm-skeleton" style="height: 12px; flex: 1"></div>
          <div class="nm-skeleton" style="height: 12px; width: 72px"></div>
        </div>
      </div>

      <div v-else-if="!filtered.length" class="nm-state">
        <span class="nm-state__icon"><el-icon :size="24"><Search /></el-icon></span>
        <p class="nm-state__title">没有符合条件的课程</p>
        <p class="nm-state__desc">
          {{ rows.length ? '换个关键词或清空筛选条件再试一次。' : '课程服务暂未返回数据，可重试拉取。' }}
        </p>
        <div class="nm-state__actions">
          <button v-if="rows.length" type="button" class="nm-btn nm-btn--sm" @click="resetFilters">
            清空筛选
          </button>
          <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="loadCourses">重新加载</button>
        </div>
      </div>

      <template v-else>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">课程</th>
                <th scope="col">分类</th>
                <th scope="col">讲师</th>
                <th scope="col" class="is-num">价格</th>
                <th scope="col" class="is-num">销量</th>
                <th scope="col">状态</th>
                <th scope="col" class="is-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in paged" :key="row.id">
                <td>
                  <div class="cell-course">
                    <img class="thumb" :src="coverFor(row, index)" :alt="row.name" loading="lazy" />
                    <span class="cell-course__copy">
                      <span class="cell-course__name">{{ row.name }}</span>
                      <span class="cell-course__id nm-num">
                        {{ row.draft ? 'DRAFT' : `#${row.id}` }}
                        <span v-if="row.draft" class="nm-badge nm-badge--warning">草稿</span>
                      </span>
                    </span>
                  </div>
                </td>
                <td><span class="nm-tag">{{ row.categoryName }}</span></td>
                <td class="nm-muted">{{ row.teacherName || '待分配' }}</td>
                <td class="is-num nm-num">{{ money(row.price) }}</td>
                <td class="is-num nm-num">{{ money(row.sales) }}</td>
                <td>
                  <span :class="['nm-badge', row.status === 'ON' ? 'nm-badge--success' : 'nm-badge--neutral']">
                    {{ row.status === 'ON' ? '已上架' : '已下架' }}
                  </span>
                </td>
                <td class="is-actions">
                  <div class="row-actions">
                    <button
                      v-if="row.status === 'OFF'"
                      type="button"
                      class="link-btn"
                      :disabled="acting === row.id || row.draft"
                      @click="onUpShelf(row)"
                    >
                      <el-icon :size="13"><ArrowUp /></el-icon>
                      <span>上架</span>
                    </button>
                    <button
                      v-else
                      type="button"
                      class="link-btn link-btn--danger"
                      :disabled="acting === row.id || row.draft"
                      @click="onDownShelf(row)"
                    >
                      <el-icon :size="13"><ArrowDown /></el-icon>
                      <span>下架</span>
                    </button>
                    <button type="button" class="link-btn" @click="openEditor(row)">
                      <el-icon :size="13"><Edit /></el-icon>
                      <span>编辑</span>
                    </button>
                    <button type="button" class="link-btn" @click="openDetail(row)">
                      <el-icon :size="13"><View /></el-icon>
                      <span>查看</span>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel__foot">
          <p class="nm-muted panel__foot-note">
            最多加载前 <span class="nm-num">{{ FETCH_SIZE }}</span> 门课程，筛选与分页在本地完成。
          </p>
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="PAGE_SIZE"
            :total="filtered.length"
            layout="prev, pager, next"
            background
            @current-change="onPageChange"
          />
        </div>
      </template>
    </section>

    <el-drawer v-model="detailOpen" :title="detailRow?.name || '课程详情'" size="min(480px, 92vw)" direction="rtl">
      <div v-if="detailLoading" class="drawer-rows">
        <div v-for="n in 5" :key="`detail-skeleton-${n}`" class="nm-skeleton" style="height: 46px"></div>
      </div>

      <div v-else class="detail">
        <img
          v-if="detailRow"
          class="detail__cover"
          :src="detail?.coverImg || coverFor(detailRow, 0)"
          :alt="detailRow.name"
        />

        <div v-if="detailFallback" class="notice notice--inline" role="status">
          <el-icon :size="15"><Warning /></el-icon>
          <p>详情接口不可达，以下为列表字段。</p>
        </div>

        <dl class="detail__list">
          <div>
            <dt>分类</dt>
            <dd>{{ detail?.categoryName || detailRow?.categoryName || '未分类' }}</dd>
          </div>
          <div>
            <dt>讲师</dt>
            <dd>{{ detail?.teacherName || detailRow?.teacherName || '待分配' }}</dd>
          </div>
          <div>
            <dt>价格</dt>
            <dd class="nm-num">{{ money(detail?.price ?? detailRow?.price ?? 0) }}</dd>
          </div>
          <div>
            <dt>销量</dt>
            <dd class="nm-num">{{ money(detail?.sales ?? detailRow?.sales ?? 0) }}</dd>
          </div>
          <div>
            <dt>章节</dt>
            <dd class="nm-num">{{ detail?.catalogs?.length ?? 0 }} 章</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>
              <span :class="['nm-badge', detailRow?.status === 'ON' ? 'nm-badge--success' : 'nm-badge--neutral']">
                {{ detailRow?.status === 'ON' ? '已上架' : '已下架' }}
              </span>
            </dd>
          </div>
        </dl>

        <div v-if="detail?.introduction" class="detail__intro">
          <p class="nm-kicker nm-kicker--plain">课程介绍</p>
          <p>{{ detail.introduction }}</p>
        </div>

        <div v-if="detail?.teachers?.length" class="detail__intro">
          <p class="nm-kicker nm-kicker--plain">讲师团队</p>
          <ul class="detail__teachers">
            <li v-for="teacher in detail.teachers" :key="teacher.id">
              <strong>{{ teacher.name }}</strong>
              <span class="nm-muted">{{ teacher.title }}</span>
            </li>
          </ul>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="editorOpen" :title="editorTitle" size="min(440px, 92vw)" direction="rtl">
      <div class="editor">
        <label class="editor__field">
          <span class="editor__label">课程名称</span>
          <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="60" show-word-limit />
        </label>
        <label class="editor__field">
          <span class="editor__label">分类</span>
          <el-select v-model="form.categoryId" placeholder="选择分类">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </label>
        <label class="editor__field">
          <span class="editor__label">讲师</span>
          <el-input v-model="form.teacherName" placeholder="讲师姓名" />
        </label>
        <div class="editor__grid">
          <label class="editor__field">
            <span class="editor__label">价格（元）</span>
            <el-input-number v-model="form.price" :min="0" :step="10" controls-position="right" />
          </label>
          <label class="editor__field">
            <span class="editor__label">销量</span>
            <el-input-number v-model="form.sales" :min="0" :step="10" controls-position="right" />
          </label>
        </div>

        <p class="hint nm-muted">课程创建 / 更新接口未接入，提交仅更新本地展示数据，不会写入后端。</p>

        <div class="editor__acts">
          <button type="button" class="nm-btn nm-btn--sm" @click="editorOpen = false">取消</button>
          <button
            type="button"
            class="nm-btn nm-btn--primary nm-btn--sm"
            :disabled="editorSaving"
            @click="saveEditor"
          >
            保存
          </button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.courses {
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

.notice--inline {
  padding: 10px 12px;
}

.notice p {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
}

.notice .el-icon {
  flex-shrink: 0;
}

.panel {
  padding: clamp(16px, 2vw, 22px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-margin-top: 84px;
}

/* ---------------- 工具栏 ---------------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar__search {
  width: min(300px, 100%);
}

.toolbar__select {
  width: 168px;
}

.toolbar__spacer {
  flex: 1;
  min-width: 0;
}

.toolbar__meta {
  font-size: var(--nm-fs-sm);
}

.hint {
  font-size: var(--nm-fs-xs);
  line-height: 1.6;
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

.table .is-actions {
  text-align: right;
}

.cell-course {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 250px;
}

.thumb {
  width: 62px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--nm-r-xs);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.cell-course__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.cell-course__name {
  font-weight: 600;
  color: var(--nm-ink);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-course__id {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: var(--nm-ink-4);
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  justify-content: flex-end;
}

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  border: 0;
  border-radius: var(--nm-r-xs);
  background: transparent;
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.link-btn:hover:not(:disabled) {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.link-btn--danger:hover:not(:disabled) {
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
}

.link-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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

.panel__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
}

.panel__foot-note {
  font-size: var(--nm-fs-xs);
}

/* ---------------- 抽屉 ---------------- */
.drawer-rows {
  display: grid;
  gap: 10px;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail__cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: var(--nm-r);
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.detail__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail__list > div {
  padding: 10px 12px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
}

.detail__list dt {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.detail__list dd {
  margin-top: 4px;
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink);
}

.detail__intro {
  display: grid;
  gap: 8px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-2);
  line-height: 1.7;
}

.detail__teachers {
  display: grid;
  gap: 8px;
  list-style: none;
}

.detail__teachers li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  font-size: var(--nm-fs-sm);
}

/* ---------------- 表单 ---------------- */
.editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor__field {
  display: grid;
  gap: 6px;
}

/* Element Plus 的下拉与数字输入默认宽度固定，这里让它们铺满表单格 */
.editor__field :deep(.el-select),
.editor__field :deep(.el-input-number) {
  width: 100%;
}

.editor__label {
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  color: var(--nm-ink-2);
}

.editor__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.editor__acts {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
}

@media (max-width: 760px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar__search,
  .toolbar__select {
    width: 100%;
  }

  .detail__list,
  .editor__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

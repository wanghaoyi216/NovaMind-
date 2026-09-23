<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Clock,
  Download,
  Key,
  Lock,
  Reading,
  Refresh,
  Search,
  Timer,
  TrendCharts,
  Unlock,
  User,
  View,
  Warning,
} from '@element-plus/icons-vue'
import { getUserInfo } from '@/api/auth'
import type { ApiResponse, UserInfo } from '@/types'

type UserRole = 'STUDENT' | 'TEACHER' | 'OPERATOR' | 'ADMIN'
type UserStatus = 'ACTIVE' | 'DISABLED'

interface UserRow {
  id: number
  username: string
  phone: string
  avatar: string
  role: UserRole
  registeredAt: string
  status: UserStatus
  courseCount: number
  studyHours: number
  finishRate: number
  lastActive: string
}

const ROLE_META: Record<UserRole, { label: string; badge: string }> = {
  STUDENT: { label: '学员', badge: 'nm-badge--accent' },
  TEACHER: { label: '讲师', badge: 'nm-badge--success' },
  OPERATOR: { label: '运营', badge: 'nm-badge--warning' },
  ADMIN: { label: '管理员', badge: 'nm-badge--danger' },
}

const ROLE_OPTIONS: { label: string; value: UserRole }[] = [
  { label: '学员', value: 'STUDENT' },
  { label: '讲师', value: 'TEACHER' },
  { label: '运营', value: 'OPERATOR' },
  { label: '管理员', value: 'ADMIN' },
]

const AVATARS = [
  'avatar_student_male_01.jpg',
  'avatar_student_female_01.jpg',
  'avatar_teacher_male_01.jpg',
  'avatar_teacher_female_01.jpg',
  'avatar_young_man_01.png',
  'avatar_businessman_phone_01.jpg',
  'avatar_child_student_01.jpg',
  'avatar_older_man_01.png',
  'avatar_animal_dog_01.png',
  'avatar_medical_pills_01.jpg',
  'avatar_doctor_vaccine_01.jpg',
  'avatar_business_partners_01.jpg',
]

const PAGE_SIZE = 8

const loading = ref(true)
const accountLoading = ref(true)
const account = ref<UserInfo | null>(null)
const accountError = ref(false)

const rows = ref<UserRow[]>([])
const keyword = ref('')
const roleFilter = ref<string>('')
const statusFilter = ref<string>('')
const currentPage = ref(1)
const tableRef = ref<HTMLElement | null>(null)

const detailOpen = ref(false)
const detailRow = ref<UserRow | null>(null)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const roleKey = roleFilter.value || ''
  const statusKey = statusFilter.value || ''
  return rows.value.filter((row) => {
    if (kw && !`${row.username}${row.phone}`.toLowerCase().includes(kw)) return false
    if (roleKey && row.role !== roleKey) return false
    if (statusKey && row.status !== statusKey) return false
    return true
  })
})

const paged = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const activeCount = computed(() => rows.value.filter((row) => row.status === 'ACTIVE').length)

const ringDash = computed(() => {
  const rate = Math.min(Math.max(detailRow.value?.finishRate ?? 0, 0), 100)
  return `${((rate / 100) * 326.7).toFixed(1)} 326.7`
})

watch([keyword, roleFilter, statusFilter], () => {
  currentPage.value = 1
})

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return (payload as ApiResponse<T>).data ?? null
  }
  return (payload as T) ?? null
}

function avatarFor(index: number): string {
  return `/resource/user-avatars/${AVATARS[index % AVATARS.length]}`
}

function maskPhone(phone: string): string {
  const value = (phone || '').trim()
  if (!value) return '未绑定'
  if (value.length < 7) return value
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}

function roleLabel(role: UserRole): string {
  return ROLE_META[role].label
}

function statusLabel(status: UserStatus): string {
  return status === 'ACTIVE' ? '正常' : '已禁用'
}

function sampleRows(): UserRow[] {
  const seeds: { name: string; phone: string; role: UserRole; days: number; status: UserStatus }[] = [
    { name: '王思远', phone: '13800138001', role: 'STUDENT', days: 128, status: 'ACTIVE' },
    { name: '李文博', phone: '13900139002', role: 'STUDENT', days: 96, status: 'ACTIVE' },
    { name: '张亦然', phone: '13700137003', role: 'TEACHER', days: 264, status: 'ACTIVE' },
    { name: '赵晴', phone: '13600136004', role: 'OPERATOR', days: 312, status: 'ACTIVE' },
    { name: '陈默', phone: '13500135005', role: 'STUDENT', days: 41, status: 'DISABLED' },
    { name: '孙宁', phone: '13400134006', role: 'TEACHER', days: 208, status: 'ACTIVE' },
    { name: '周宁', phone: '13300133007', role: 'STUDENT', days: 18, status: 'ACTIVE' },
    { name: '吴桐', phone: '13200132008', role: 'STUDENT', days: 7, status: 'ACTIVE' },
    { name: '郑航', phone: '13100131009', role: 'ADMIN', days: 402, status: 'ACTIVE' },
    { name: '林悦', phone: '13000130010', role: 'STUDENT', days: 63, status: 'DISABLED' },
    { name: '徐清', phone: '15900159011', role: 'STUDENT', days: 152, status: 'ACTIVE' },
    { name: '何洲', phone: '15800158012', role: 'TEACHER', days: 187, status: 'ACTIVE' },
    { name: '孟凡', phone: '15700157013', role: 'STUDENT', days: 29, status: 'ACTIVE' },
    { name: '许安然', phone: '15600156014', role: 'OPERATOR', days: 74, status: 'ACTIVE' },
  ]
  return seeds.map((seed, index) => ({
    id: 9000 + index,
    username: seed.name,
    phone: seed.phone,
    avatar: avatarFor(index),
    role: seed.role,
    registeredAt: dayjs().subtract(seed.days, 'day').format('YYYY-MM-DD'),
    status: seed.status,
    courseCount: 3 + ((index * 7) % 12),
    studyHours: 12 + ((index * 13) % 96),
    finishRate: 32 + ((index * 17) % 64),
    lastActive: dayjs().subtract((index % 9) + 1, 'hour').format('MM-DD HH:mm'),
  }))
}

async function loadAccount() {
  accountLoading.value = true
  accountError.value = false
  try {
    const res = await getUserInfo()
    const info = unwrap<UserInfo>(res.data)
    if (info && info.username) {
      account.value = info
    } else {
      accountError.value = true
    }
  } catch {
    account.value = null
    accountError.value = true
  }
  accountLoading.value = false
}

function loadUsers() {
  loading.value = true
  rows.value = sampleRows()
  currentPage.value = 1
  loading.value = false
}

function refreshAll() {
  loadUsers()
  void loadAccount()
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

async function toggleStatus(row: UserRow) {
  const next: UserStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = next === 'ACTIVE' ? '启用' : '禁用'
  if (!(await confirmAction(`确认${action}用户「${row.username}」？`, `${action}确认`, `确认${action}`))) return
  row.status = next
  ElMessage.warning(`已${action}「${row.username}」：用户管理接口未接入，仅更新本地演示状态`)
}

async function resetPassword(row: UserRow) {
  if (!(await confirmAction(`确认为「${row.username}」重置密码？`, '重置密码', '确认重置'))) return
  ElMessage.warning(`「${row.username}」的密码重置接口未接入，未实际执行任何操作`)
}

function openDetail(row: UserRow) {
  detailRow.value = row
  detailOpen.value = true
}

function csvCell(value: string | number): string {
  return `"${String(value).replace(/"/g, '""')}"`
}

function exportCsv() {
  if (!filtered.value.length) {
    ElMessage.warning('当前筛选结果为空，没有可导出的记录')
    return
  }
  const header = ['用户名', '手机号', '角色', '注册时间', '状态', '已购课程', '学习时长(h)']
  const lines = filtered.value.map((row) => [
    row.username,
    maskPhone(row.phone),
    roleLabel(row.role),
    row.registeredAt,
    statusLabel(row.status),
    row.courseCount,
    row.studyHours,
  ])
  const csv = [header, ...lines].map((cols) => cols.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `novamind-users-${dayjs().format('YYYYMMDD-HHmm')}.csv`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${lines.length} 条记录（演示数据）`)
}

function resetFilters() {
  keyword.value = ''
  roleFilter.value = ''
  statusFilter.value = ''
}

function onPageChange() {
  tableRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  loadUsers()
  void loadAccount()
})
</script>

<template>
  <div class="users">
    <header class="page-head">
      <div class="page-head__copy">
        <span class="nm-kicker">用户管理</span>
        <h1 class="nm-h1">学员与账号</h1>
        <p class="nm-lede">检索账号、查看学习档案，并处理启用、禁用与密码重置。</p>
      </div>
      <div class="page-head__acts">
        <button type="button" class="nm-btn nm-btn--sm" @click="refreshAll">
          <el-icon :size="14"><Refresh /></el-icon>
          <span>刷新</span>
        </button>
        <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="exportCsv">
          <el-icon :size="14"><Download /></el-icon>
          <span>导出</span>
        </button>
      </div>
    </header>

    <section class="nm-card nm-card--quiet account">
      <template v-if="accountLoading">
        <div class="nm-skeleton" style="width: 42px; height: 42px; border-radius: 999px"></div>
        <div class="account__copy">
          <div class="nm-skeleton" style="height: 12px; width: 120px"></div>
          <div class="nm-skeleton" style="height: 10px; width: 180px; margin-top: 8px"></div>
        </div>
      </template>

      <template v-else-if="account">
        <img
          class="account__avatar"
          :src="account.avatar || '/resource/user-avatars/avatar_teacher_male_01.jpg'"
          :alt="account.username"
        />
        <div class="account__copy">
          <p class="account__name">
            {{ account.username }}
            <span class="nm-badge nm-badge--success">实时</span>
          </p>
          <p class="nm-muted account__meta">
            <span class="nm-num">{{ maskPhone(account.phone) }}</span>
            <span aria-hidden="true">·</span>
            <span>来源 <span class="nm-num">GET /us/users/me</span></span>
          </p>
        </div>
      </template>

      <template v-else>
        <span class="account__icon"><el-icon :size="18"><Warning /></el-icon></span>
        <div class="account__copy">
          <p class="account__name">当前登录账号不可达</p>
          <p class="nm-muted account__meta">用户服务请求失败，账号信息无法展示。</p>
        </div>
        <button type="button" class="nm-btn nm-btn--sm" @click="loadAccount">重试</button>
      </template>
    </section>

    <div class="notice" role="status">
      <el-icon :size="16"><Warning /></el-icon>
      <p>
        后端未提供管理端用户列表接口，下表为<strong>演示数据</strong>；启用 / 禁用、重置密码仅在本地生效，不会写入后端。
      </p>
    </div>

    <section ref="tableRef" class="nm-card panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          class="toolbar__search"
          placeholder="搜索用户名 / 手机号"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="roleFilter" class="toolbar__select" placeholder="全部角色" clearable>
          <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="statusFilter" class="toolbar__select" placeholder="全部状态" clearable>
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <div class="toolbar__spacer"></div>
        <p class="toolbar__meta nm-muted">
          共 <span class="nm-num">{{ filtered.length }}</span> 人 · 正常
          <span class="nm-num">{{ activeCount }}</span> 人
        </p>
      </div>

      <div v-if="loading" class="rows">
        <div v-for="n in 6" :key="`user-skeleton-${n}`" class="row-skeleton">
          <div class="nm-skeleton" style="width: 36px; height: 36px; border-radius: 999px"></div>
          <div class="nm-skeleton" style="height: 12px; flex: 2"></div>
          <div class="nm-skeleton" style="height: 12px; flex: 1"></div>
          <div class="nm-skeleton" style="height: 12px; width: 72px"></div>
        </div>
      </div>

      <div v-else-if="!filtered.length" class="nm-state">
        <span class="nm-state__icon"><el-icon :size="24"><User /></el-icon></span>
        <p class="nm-state__title">没有匹配的账号</p>
        <p class="nm-state__desc">调整关键词或清空筛选条件后再试一次。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--sm" @click="resetFilters">清空筛选</button>
          <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="refreshAll">重新加载</button>
        </div>
      </div>

      <template v-else>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">用户</th>
                <th scope="col">手机号</th>
                <th scope="col">角色</th>
                <th scope="col">注册时间</th>
                <th scope="col">状态</th>
                <th scope="col" class="is-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in paged" :key="row.id">
                <td>
                  <div class="cell-user">
                    <img class="avatar" :src="row.avatar" :alt="row.username" loading="lazy" />
                    <span class="cell-user__copy">
                      <span class="cell-user__name">{{ row.username }}</span>
                      <span class="cell-user__id nm-num">UID {{ row.id }}</span>
                    </span>
                  </div>
                </td>
                <td class="nm-num">{{ maskPhone(row.phone) }}</td>
                <td><span :class="['nm-badge', ROLE_META[row.role].badge]">{{ roleLabel(row.role) }}</span></td>
                <td class="nm-muted nm-num">{{ row.registeredAt }}</td>
                <td>
                  <span :class="['nm-badge', row.status === 'ACTIVE' ? 'nm-badge--success' : 'nm-badge--neutral']">
                    {{ statusLabel(row.status) }}
                  </span>
                </td>
                <td class="is-actions">
                  <div class="row-actions">
                    <button type="button" class="link-btn" @click="openDetail(row)">
                      <el-icon :size="13"><View /></el-icon>
                      <span>详情</span>
                    </button>
                    <button
                      type="button"
                      :class="['link-btn', row.status === 'ACTIVE' ? 'link-btn--danger' : '']"
                      @click="toggleStatus(row)"
                    >
                      <el-icon :size="13">
                        <Unlock v-if="row.status !== 'ACTIVE'" />
                        <Lock v-else />
                      </el-icon>
                      <span>{{ row.status === 'ACTIVE' ? '禁用' : '启用' }}</span>
                    </button>
                    <button type="button" class="link-btn" @click="resetPassword(row)">
                      <el-icon :size="13"><Key /></el-icon>
                      <span>重置密码</span>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="panel__foot">
          <p class="nm-muted panel__foot-note">演示数据共 {{ rows.length }} 条，筛选与分页在本地完成。</p>
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

    <el-drawer v-model="detailOpen" :title="detailRow?.username || '用户详情'" size="min(460px, 92vw)" direction="rtl">
      <div v-if="detailRow" class="profile">
        <header class="profile__head">
          <img class="profile__avatar" :src="detailRow.avatar" :alt="detailRow.username" />
          <div class="profile__id">
            <p class="profile__name">{{ detailRow.username }}</p>
            <p class="nm-muted nm-num">{{ maskPhone(detailRow.phone) }} · UID {{ detailRow.id }}</p>
            <div class="profile__tags">
              <span :class="['nm-badge', ROLE_META[detailRow.role].badge]">{{ roleLabel(detailRow.role) }}</span>
              <span :class="['nm-badge', detailRow.status === 'ACTIVE' ? 'nm-badge--success' : 'nm-badge--neutral']">
                {{ statusLabel(detailRow.status) }}
              </span>
            </div>
          </div>
        </header>

        <div class="profile__ring">
          <svg viewBox="0 0 120 120" class="ring" role="img" aria-label="完课率">
            <circle class="ring__track" cx="60" cy="60" r="52" />
            <circle class="ring__bar" cx="60" cy="60" r="52" :stroke-dasharray="ringDash" />
          </svg>
          <div class="profile__ring-copy">
            <p class="nm-num profile__ring-value">{{ detailRow.finishRate }}%</p>
            <p class="nm-muted">完课率</p>
          </div>
        </div>

        <dl class="profile__stats">
          <div>
            <dt><el-icon :size="13"><Reading /></el-icon><span>已购课程</span></dt>
            <dd class="nm-num">{{ detailRow.courseCount }}</dd>
          </div>
          <div>
            <dt><el-icon :size="13"><Timer /></el-icon><span>学习时长</span></dt>
            <dd class="nm-num">{{ detailRow.studyHours }} h</dd>
          </div>
          <div>
            <dt><el-icon :size="13"><TrendCharts /></el-icon><span>完课率</span></dt>
            <dd class="nm-num">{{ detailRow.finishRate }}%</dd>
          </div>
          <div>
            <dt><el-icon :size="13"><Clock /></el-icon><span>最近活跃</span></dt>
            <dd class="nm-num">{{ detailRow.lastActive }}</dd>
          </div>
        </dl>

        <dl class="profile__list">
          <div>
            <dt>注册时间</dt>
            <dd class="nm-num">{{ detailRow.registeredAt }}</dd>
          </div>
          <div>
            <dt>账号来源</dt>
            <dd>手机号注册</dd>
          </div>
        </dl>

        <p class="hint nm-muted">学习统计为演示数据，后端未提供用户档案接口。</p>

        <div class="profile__acts">
          <button
            type="button"
            :class="['nm-btn', 'nm-btn--sm', detailRow.status === 'ACTIVE' ? 'nm-btn--primary' : '']"
            @click="toggleStatus(detailRow)"
          >
            {{ detailRow.status === 'ACTIVE' ? '禁用账号' : '启用账号' }}
          </button>
          <button type="button" class="nm-btn nm-btn--sm" @click="resetPassword(detailRow)">重置密码</button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.users {
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

/* ---------------- 当前账号 ---------------- */
.account {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
}

.account__avatar {
  width: 42px;
  height: 42px;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
}

.account__icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-full);
  background: var(--nm-warning-soft);
  color: var(--nm-warning);
}

.account__copy {
  flex: 1;
  min-width: 0;
}

.account__name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--nm-fs-body);
  font-weight: 700;
  color: var(--nm-ink);
}

.account__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: var(--nm-fs-xs);
  flex-wrap: wrap;
}

/* ---------------- 提示条 ---------------- */
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

/* ---------------- 面板 ---------------- */
.panel {
  padding: clamp(16px, 2vw, 22px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-margin-top: 84px;
}

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

.table .is-actions {
  text-align: right;
}

.cell-user {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 180px;
}

.avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.cell-user__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cell-user__name {
  font-weight: 600;
  color: var(--nm-ink);
}

.cell-user__id {
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

.link-btn:hover {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.link-btn--danger:hover {
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
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

/* ---------------- 详情抽屉 ---------------- */
.profile {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile__head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.profile__avatar {
  width: 58px;
  height: 58px;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
}

.profile__id {
  min-width: 0;
}

.profile__name {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.profile__tags {
  display: flex;
  gap: 6px;
  margin-top: 7px;
}

.profile__ring {
  position: relative;
  display: grid;
  place-items: center;
  padding: 8px 0;
}

.ring {
  width: 132px;
  height: 132px;
  transform: rotate(-90deg);
}

.ring__track {
  fill: none;
  stroke: var(--nm-surface-3);
  stroke-width: 10;
}

.ring__bar {
  fill: none;
  stroke: var(--nm-accent);
  stroke-width: 10;
  stroke-linecap: round;
  transition: stroke-dasharray var(--nm-dur-slow) var(--nm-ease);
}

.profile__ring-copy {
  position: absolute;
  text-align: center;
}

.profile__ring-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--nm-ink);
  line-height: 1.1;
}

.profile__ring-copy .nm-muted {
  font-size: var(--nm-fs-xs);
}

.profile__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.profile__stats > div {
  padding: 11px 13px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
}

.profile__stats dt {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.profile__stats dd {
  margin-top: 5px;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--nm-ink);
}

.profile__list {
  display: grid;
  gap: 8px;
}

.profile__list > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  font-size: var(--nm-fs-sm);
}

.profile__list dt {
  color: var(--nm-ink-3);
}

.profile__list dd {
  font-weight: 600;
  color: var(--nm-ink);
}

.hint {
  font-size: var(--nm-fs-xs);
  line-height: 1.6;
}

.profile__acts {
  display: flex;
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

  .account {
    flex-wrap: wrap;
  }
}
</style>

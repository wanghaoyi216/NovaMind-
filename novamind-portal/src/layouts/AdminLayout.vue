<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Cpu,
  DataLine,
  Menu,
  Moon,
  Reading,
  Search,
  Sunny,
  SwitchButton,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import SystemIcon from '@/components/SystemIcon.vue'
import { getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { applyTheme, getTheme, toggleTheme, type ThemeMode } from '@/utils/theme'
import type { ApiResponse, UserInfo } from '@/types'

interface AdminNavItem {
  path: string
  label: string
  icon: Component
  exact?: boolean
}

interface AdminNavGroup {
  label: string
  items: AdminNavItem[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const navGroups: AdminNavGroup[] = [
  {
    label: '运营',
    items: [
      { path: '/admin', label: '总览', icon: DataLine, exact: true },
      { path: '/admin/reports', label: '数据报表', icon: TrendCharts },
    ],
  },
  {
    label: '教学',
    items: [{ path: '/admin/courses', label: '课程管理', icon: Reading }],
  },
  {
    label: '用户与智能',
    items: [
      { path: '/admin/users', label: '用户管理', icon: User },
      { path: '/admin/ai', label: 'AI 管理', icon: Cpu },
    ],
  },
]

const navOpen = ref(false)
const searchKeyword = ref('')
const theme = ref<ThemeMode>(getTheme())
const isNarrow = ref(false)

let media: MediaQueryList | null = null

const pageTitle = computed(() => (route.meta.title as string | undefined) || '管理后台')

const themeLabel = computed(() => (theme.value === 'dark' ? '切换到浅色主题' : '切换到深色主题'))

const adminName = computed(() => userStore.userInfo?.username || '管理员')
const adminAvatar = computed(
  () => userStore.userInfo?.avatar || '/resource/user-avatars/avatar_businessman_phone_01.jpg'
)

function isActive(item: AdminNavItem): boolean {
  if (item.exact) return route.path === item.path
  return route.path === item.path || route.path.startsWith(`${item.path}/`)
}

function onToggleTheme() {
  theme.value = toggleTheme()
}

function onSearch() {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  navOpen.value = false
  router.push({ path: '/admin/courses', query: { keyword } })
}

function onLogout() {
  userStore.logout()
  ElMessage.success('已退出管理后台')
  router.push('/portal/login')
}

function syncNarrow(event: MediaQueryList | MediaQueryListEvent) {
  isNarrow.value = event.matches
  if (!event.matches) navOpen.value = false
}

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === 'object' && 'code' in payload && 'data' in payload) {
    return (payload as ApiResponse<T>).data ?? null
  }
  return (payload as T) ?? null
}

async function loadAdmin() {
  if (!localStorage.getItem('nova_token') || userStore.userInfo) return
  try {
    const res = await getUserInfo()
    const info = unwrap<UserInfo>(res.data)
    if (info && info.username) userStore.setInfo(info)
  } catch {
    // 后台服务不可达时保持占位身份，不打断导航
  }
}

watch(
  () => route.fullPath,
  () => {
    navOpen.value = false
  }
)

onMounted(() => {
  applyTheme(getTheme())
  theme.value = getTheme()
  media = window.matchMedia('(max-width: 1024px)')
  syncNarrow(media)
  media.addEventListener('change', syncNarrow)
  loadAdmin()
})

onUnmounted(() => {
  media?.removeEventListener('change', syncNarrow)
  media = null
})
</script>

<template>
  <div class="admin">
    <aside :class="['admin__side', { 'is-open': navOpen }]" aria-label="管理后台导航">
      <router-link to="/admin" class="brand">
        <span class="brand__mark" aria-hidden="true">
          <SystemIcon name="brand" :size="26" />
        </span>
        <span class="brand__copy">
          <strong>NovaMind</strong>
          <em>管理后台</em>
        </span>
      </router-link>

      <nav class="nav">
        <div v-for="group in navGroups" :key="group.label" class="nav__group">
          <p class="nav__label">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="['nav__item', { 'is-active': isActive(item) }]"
          >
            <el-icon class="nav__ico" :size="17"><component :is="item.icon" /></el-icon>
            <span class="nav__text">{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <div class="side-user">
        <img class="side-user__avatar" :src="adminAvatar" alt="" />
        <span class="side-user__meta">
          <strong>{{ adminName }}</strong>
          <em>超级管理员</em>
        </span>
        <button type="button" class="side-user__out" title="退出登录" @click="onLogout">
          <el-icon :size="15"><SwitchButton /></el-icon>
        </button>
      </div>
    </aside>

    <button
      v-if="navOpen && isNarrow"
      type="button"
      class="admin__scrim"
      aria-label="关闭导航"
      @click="navOpen = false"
    ></button>

    <div class="admin__main">
      <header class="topbar">
        <button
          v-if="isNarrow"
          type="button"
          class="topbar__menu"
          aria-label="打开导航"
          @click="navOpen = true"
        >
          <el-icon :size="17"><Menu /></el-icon>
        </button>

        <nav class="crumbs" aria-label="面包屑">
          <span class="crumbs__root">管理后台</span>
          <el-icon class="crumbs__sep" :size="12"><ArrowRight /></el-icon>
          <strong class="crumbs__now">{{ pageTitle }}</strong>
        </nav>

        <form class="search" role="search" @submit.prevent="onSearch">
          <el-icon class="search__ico" :size="15"><Search /></el-icon>
          <input
            v-model="searchKeyword"
            class="search__input"
            type="search"
            placeholder="搜索课程…"
            aria-label="搜索课程"
          />
          <kbd class="search__hint">Enter</kbd>
        </form>

        <div class="topbar__acts">
          <button
            type="button"
            class="icon-btn"
            :title="themeLabel"
            :aria-label="themeLabel"
            @click="onToggleTheme"
          >
            <el-icon :size="16">
              <Sunny v-if="theme === 'dark'" />
              <Moon v-else />
            </el-icon>
          </button>
          <router-link to="/portal/home" class="back-link">
            <span>返回前台</span>
            <el-icon :size="14"><ArrowRight /></el-icon>
          </router-link>
        </div>
      </header>

      <main class="admin__content">
        <div class="admin__inner">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.admin {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--nm-paper);
}

/* ---------------- 侧栏 ---------------- */
.admin__side {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 26px;
  padding: 22px 16px 18px;
  background: var(--nm-surface);
  border-right: 1px solid var(--nm-line);
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 6px;
  border-radius: var(--nm-r);
}

.brand__mark {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-sm);
  background: var(--nm-ink);
  color: var(--nm-paper);
  transition: transform var(--nm-dur) var(--nm-ease-spring);
}

.brand:hover .brand__mark {
  transform: rotate(-7deg) scale(1.04);
}

.brand__copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  min-width: 0;
}

.brand__copy strong {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.brand__copy em {
  font-style: normal;
  font-family: var(--nm-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
}

.nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
}

.nav__group {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav__label {
  padding: 0 10px 6px;
  font-family: var(--nm-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.nav__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 11px;
  border-radius: var(--nm-r-sm);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-body);
  font-weight: 600;
  transition: background-color var(--nm-dur-fast) var(--nm-ease),
    color var(--nm-dur-fast) var(--nm-ease);
}

.nav__item::before {
  content: '';
  position: absolute;
  left: -16px;
  top: 50%;
  width: 3px;
  height: 0;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
  transform: translateY(-50%);
  transition: height var(--nm-dur) var(--nm-ease);
}

.nav__item:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.nav__item.is-active {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.nav__item.is-active::before {
  height: 18px;
}

.nav__ico {
  flex-shrink: 0;
}

.nav__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-user {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
}

.side-user__avatar {
  width: 34px;
  height: 34px;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
}

.side-user__meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  min-width: 0;
}

.side-user__meta strong {
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-user__meta em {
  font-style: normal;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.side-user__out {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-xs);
  background: transparent;
  color: var(--nm-ink-3);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.side-user__out:hover {
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
}

/* ---------------- 主区 ---------------- */
.admin__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: var(--nm-z-float);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  height: 64px;
  padding: 0 clamp(16px, 2.4vw, 30px);
  background: var(--nm-surface);
  border-bottom: 1px solid var(--nm-line);
}

.topbar__menu,
.icon-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.topbar__menu:hover,
.icon-btn:hover {
  border-color: var(--nm-line-strong);
  color: var(--nm-ink);
  transform: translateY(-1px);
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.crumbs__root {
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
  white-space: nowrap;
}

.crumbs__sep {
  color: var(--nm-ink-4);
  flex-shrink: 0;
}

.crumbs__now {
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search {
  justify-self: end;
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(320px, 100%);
  height: 36px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  transition: border-color var(--nm-dur-fast) var(--nm-ease),
    box-shadow var(--nm-dur-fast) var(--nm-ease);
}

.search:focus-within {
  border-color: var(--nm-accent-line);
  box-shadow: 0 0 0 3px var(--nm-accent-soft);
}

.search__ico {
  color: var(--nm-ink-4);
  flex-shrink: 0;
}

.search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink);
}

.search__input::placeholder {
  color: var(--nm-ink-4);
}

.search__input::-webkit-search-cancel-button {
  display: none;
}

.search__hint {
  flex-shrink: 0;
  padding: 1px 6px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-xs);
  font-family: var(--nm-mono);
  font-size: 10px;
  color: var(--nm-ink-4);
}

.topbar__acts {
  display: flex;
  align-items: center;
  gap: 10px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 13px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink-2);
  white-space: nowrap;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.back-link:hover {
  border-color: var(--nm-accent-line);
  color: var(--nm-accent);
  background: var(--nm-accent-soft);
}

.back-link:hover .el-icon {
  transform: translateX(2px);
}

.back-link .el-icon {
  transition: transform var(--nm-dur-fast) var(--nm-ease);
}

.admin__content {
  flex: 1;
  min-width: 0;
  padding: clamp(20px, 2.6vw, 34px) clamp(16px, 2.4vw, 30px) 64px;
}

.admin__inner {
  max-width: 1240px;
  margin-inline: auto;
}

.admin__scrim {
  position: fixed;
  inset: 0;
  z-index: var(--nm-z-drawer);
  border: 0;
  padding: 0;
  background: color-mix(in srgb, var(--nm-ink) 46%, transparent);
  cursor: pointer;
}

/* ---------------- 窄屏：侧栏转为抽屉 ---------------- */
@media (max-width: 1024px) {
  .admin {
    grid-template-columns: minmax(0, 1fr);
  }

  .admin__side {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: calc(var(--nm-z-drawer) + 1);
    width: 272px;
    max-width: 84vw;
    height: 100vh;
    transform: translateX(-101%);
    box-shadow: var(--nm-sh-4);
    transition: transform var(--nm-dur) var(--nm-ease);
  }

  .admin__side.is-open {
    transform: none;
  }

  .topbar {
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: 12px;
  }

  .search {
    display: none;
  }
}

@media (max-width: 640px) {
  .crumbs__root,
  .crumbs__sep {
    display: none;
  }

  .back-link span {
    display: none;
  }

  .back-link {
    width: 34px;
    height: 34px;
    padding: 0;
    justify-content: center;
    border-radius: var(--nm-r-sm);
  }
}
</style>

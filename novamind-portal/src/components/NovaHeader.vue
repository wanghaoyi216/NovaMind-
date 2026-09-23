<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Menu, Moon, Sunny } from '@element-plus/icons-vue'
import { getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { applyTheme, getTheme, toggleTheme, type ThemeMode } from '@/utils/theme'
import iconHome from '@/assets/icons/icon-home.svg?raw'
import iconCourses from '@/assets/icons/icon-courses.svg?raw'
import iconAiChat from '@/assets/icons/icon-ai-chat.svg?raw'
import iconGraph from '@/assets/icons/icon-graph.svg?raw'
import iconCommunity from '@/assets/icons/icon-community.svg?raw'
import iconCart from '@/assets/icons/icon-cart.svg?raw'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const scrolled = ref(false)
const theme = ref<ThemeMode>(getTheme())
const drawerOpen = ref(false)

const navItems = [
  { path: '/portal/home', label: '首页', icon: iconHome },
  { path: '/portal/courses', label: '课程中心', icon: iconCourses },
  { path: '/portal/ai-chat', label: 'AI 助手', icon: iconAiChat },
  { path: '/portal/knowledge-graph', label: '知识图谱', icon: iconGraph },
  { path: '/portal/community', label: '学习社区', icon: iconCommunity },
  { path: '/portal/cart', label: '购物车', icon: iconCart },
]

const themeLabel = computed(() => (theme.value === 'dark' ? '切换到浅色主题' : '切换到深色主题'))

function onScroll() {
  scrolled.value = window.scrollY > 8
}

function isActive(path: string) {
  if (path === '/portal/home') {
    return route.path === '/portal/home'
  }
  return route.path.startsWith(path)
}

function goAndClose(path: string) {
  drawerOpen.value = false
  router.push(path)
}

function onToggleTheme() {
  theme.value = toggleTheme()
}

async function checkLogin() {
  const token = localStorage.getItem('nova_token')
  if (!token || userStore.token) return
  try {
    const res: any = await getUserInfo()
    if (res.data) {
      userStore.setToken(token)
      userStore.setInfo(res.data)
    }
  } catch {
    // 顶栏保持可用即可
  }
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已安全退出')
  router.push('/portal/home')
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
  // 主题已在入口处落定，这里仅同步本地状态
  applyTheme(getTheme())
  theme.value = getTheme()
  checkLogin()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <header :class="['nm-header', { 'is-scrolled': scrolled }]">
    <div class="nm-header__inner">
      <router-link to="/portal/home" class="nm-brand" aria-label="NovaMind 智星云首页">
        <span class="nm-brand__mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" width="22" height="22" fill="none">
            <path
              d="M16 3.5c1.1 8.3 4.2 11.4 12.5 12.5C20.2 17.1 17.1 20.2 16 28.5 14.9 20.2 11.8 17.1 3.5 16 11.8 14.9 14.9 11.8 16 3.5Z"
              fill="currentColor"
            />
          </svg>
        </span>
        <span class="nm-brand__copy">
          <strong>NovaMind</strong>
          <em>智星云学习平台</em>
        </span>
      </router-link>

      <nav class="nm-nav" aria-label="主导航">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="['nm-nav__item', { 'is-active': isActive(item.path) }]"
        >
          <span class="nm-nav__ico" aria-hidden="true" v-html="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="nm-header__actions">
        <button
          type="button"
          class="nm-icon-btn"
          :title="themeLabel"
          :aria-label="themeLabel"
          @click="onToggleTheme"
        >
          <el-icon :size="17">
            <Sunny v-if="theme === 'dark'" />
            <Moon v-else />
          </el-icon>
        </button>

        <template v-if="userStore.token">
          <el-dropdown trigger="click" placement="bottom-end">
            <button type="button" class="nm-user">
              <img
                :src="userStore.userInfo?.avatar || '/resource/user-avatars/avatar_student_male_01.jpg'"
                alt=""
                class="nm-user__avatar"
              />
              <span class="nm-user__name">{{ userStore.userInfo?.username || '学员' }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/portal/my/lessons')">我的课程</el-dropdown-item>
                <el-dropdown-item @click="router.push('/portal/my/orders')">我的订单</el-dropdown-item>
                <el-dropdown-item @click="router.push('/portal/my/coupons')">我的优惠券</el-dropdown-item>
                <el-dropdown-item @click="router.push('/portal/my/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <button
            type="button"
            class="nm-btn nm-btn--ghost nm-btn--sm nm-login"
            @click="router.push('/portal/login')"
          >
            登录
          </button>
          <button
            type="button"
            class="nm-btn nm-btn--primary nm-btn--sm"
            @click="router.push('/portal/register')"
          >
            免费注册
          </button>
        </template>

        <button
          type="button"
          class="nm-icon-btn nm-menu-btn"
          aria-label="打开导航菜单"
          @click="drawerOpen = true"
        >
          <el-icon :size="18"><Menu /></el-icon>
        </button>
      </div>
    </div>

    <el-drawer v-model="drawerOpen" title="导航菜单" direction="rtl" size="80%" class="nm-nav-drawer">
      <nav class="nm-drawer-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          :class="['nm-drawer-link', { 'is-active': isActive(item.path) }]"
          @click="goAndClose(item.path)"
        >
          <span class="nm-drawer-link__main">
            <span class="nm-nav__ico" aria-hidden="true" v-html="item.icon" />
            <span>{{ item.label }}</span>
          </span>
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </nav>

      <hr class="nm-divider nm-drawer-divider" />

      <div class="nm-drawer-nav">
        <template v-if="userStore.token">
          <button type="button" class="nm-drawer-link" @click="goAndClose('/portal/my/lessons')">
            <span>我的课程</span>
            <el-icon :size="14"><ArrowRight /></el-icon>
          </button>
          <button type="button" class="nm-drawer-link" @click="goAndClose('/portal/my/orders')">
            <span>我的订单</span>
            <el-icon :size="14"><ArrowRight /></el-icon>
          </button>
          <button type="button" class="nm-drawer-link" @click="goAndClose('/portal/my/profile')">
            <span>个人中心</span>
            <el-icon :size="14"><ArrowRight /></el-icon>
          </button>
          <button
            type="button"
            class="nm-btn nm-drawer-logout"
            @click="drawerOpen = false; handleLogout()"
          >
            退出登录
          </button>
        </template>
        <template v-else>
          <div class="nm-drawer-auth">
            <button type="button" class="nm-btn" @click="goAndClose('/portal/login')">登录</button>
            <button
              type="button"
              class="nm-btn nm-btn--primary"
              @click="goAndClose('/portal/register')"
            >
              免费注册
            </button>
          </div>
        </template>
      </div>
    </el-drawer>
  </header>
</template>

<style lang="scss" scoped>
.nm-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: var(--nm-z-header);
  padding: 12px var(--nm-gutter) 0;
}

.nm-header__inner {
  max-width: var(--nm-shell);
  margin-inline: auto;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 20px;
  height: 64px;
  padding: 0 14px 0 18px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-2);
  transition: box-shadow var(--nm-dur) var(--nm-ease), border-color var(--nm-dur) var(--nm-ease);
}

.nm-header.is-scrolled .nm-header__inner {
  box-shadow: var(--nm-sh-3);
  border-color: var(--nm-line-strong);
}

/* --- 品牌 --- */
.nm-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.nm-brand__mark {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r);
  background: var(--nm-ink);
  color: var(--nm-paper);
  transition: transform var(--nm-dur) var(--nm-ease-spring);
}

[data-theme='dark'] .nm-brand__mark {
  color: #0b0e14;
}

.nm-brand:hover .nm-brand__mark {
  transform: rotate(-8deg) scale(1.05);
}

.nm-brand__copy {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  min-width: 0;
}

.nm-brand__copy strong {
  font-size: 15.5px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--nm-ink);
}

.nm-brand__copy em {
  font-style: normal;
  font-size: 10.5px;
  letter-spacing: 0.14em;
  color: var(--nm-ink-3);
}

/* --- 主导航 --- */
.nm-nav {
  justify-self: center;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.nm-nav__item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 13px;
  border-radius: var(--nm-r-full);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  white-space: nowrap;
  transition: color var(--nm-dur-fast) var(--nm-ease),
    background-color var(--nm-dur-fast) var(--nm-ease);
}

.nm-nav__item:hover {
  color: var(--nm-ink);
  background: var(--nm-surface-3);
}

.nm-nav__item.is-active {
  color: var(--nm-ink);
  background: var(--nm-surface-3);
}

.nm-nav__item.is-active::after {
  content: '';
  position: absolute;
  left: 13px;
  right: 13px;
  bottom: 2px;
  height: 2px;
  border-radius: 2px;
  background: var(--nm-accent);
}

.nm-nav__ico {
  display: inline-flex;
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  opacity: 0.85;

  :deep(svg) {
    width: 100%;
    height: 100%;
    display: block;
  }
}

.nm-nav__item.is-active .nm-nav__ico {
  opacity: 1;
}

/* --- 右侧操作 --- */
.nm-header__actions {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.nm-icon-btn {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.nm-icon-btn:hover {
  color: var(--nm-ink);
  border-color: var(--nm-line-strong);
  background: var(--nm-surface-3);
}

.nm-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 12px 3px 3px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.nm-user:hover {
  border-color: var(--nm-line-strong);
  box-shadow: var(--nm-sh-2);
}

.nm-user__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.nm-user__name {
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink);
  max-width: 8ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nm-menu-btn {
  display: none;
}

/* --- 抽屉 --- */
.nm-drawer-nav {
  display: grid;
  gap: 6px;
}

.nm-drawer-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 13px 14px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.nm-drawer-link:hover {
  color: var(--nm-ink);
  border-color: var(--nm-line-strong);
}

.nm-drawer-link.is-active {
  color: var(--nm-ink);
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
}

.nm-drawer-link__main {
  display: inline-flex;
  align-items: center;
  gap: 10px;

  .nm-nav__ico {
    width: 18px;
    height: 18px;
    opacity: 1;
  }
}

.nm-drawer-divider {
  margin: 16px 0;
}

.nm-drawer-auth {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.nm-drawer-logout {
  width: 100%;
  margin-top: 8px;
  color: var(--nm-danger);
  border-color: var(--nm-danger-soft);
}

.nm-drawer-logout:hover {
  background: var(--nm-danger-soft);
  border-color: var(--nm-danger);
  color: var(--nm-danger);
}

@media (max-width: 1180px) {
  .nm-nav {
    display: none;
  }

  .nm-header__inner {
    grid-template-columns: auto auto;
    justify-content: space-between;
  }

  .nm-menu-btn {
    display: grid;
  }
}

@media (max-width: 768px) {
  .nm-header {
    padding: 8px 12px 0;
  }

  .nm-header__inner {
    height: 56px;
    padding: 0 10px 0 12px;
    gap: 10px;
  }

  .nm-brand__copy em {
    display: none;
  }

  .nm-login {
    display: none;
  }

  .nm-user__name {
    display: none;
  }

  .nm-user {
    padding: 3px;
  }
}
</style>

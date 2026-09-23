<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Lock, MagicStick, Phone, Reading, TrendCharts, Warning } from '@element-plus/icons-vue'
import { getUserInfo, login } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { LoginRequest, UserInfo } from '@/types'

interface Highlight {
  title: string
  desc: string
  icon: Component
}

const REMEMBER_KEY = 'nova_remember_account'
const PHONE_RE = /^1[3-9]\d{9}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
const OFFLINE_HINT =
  '本地开发环境需要后端网关（端口 10010）已启动才能完成登录；服务未启动时你可以先浏览课程内容。'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const highlights: Highlight[] = [
  { title: '系统化课程', desc: '按方向与难度拆解的学习路径，学完即可落地', icon: Reading },
  { title: 'AI 伴学', desc: '随时追问知识点，生成讲解、例题与复习计划', icon: MagicStick },
  { title: '进度可见', desc: '学习记录自动同步，换设备也不会丢失', icon: TrendCharts },
]

const trust: { value: string; label: string }[] = [
  { value: '120+', label: '门系统课程' },
  { value: '4.9', label: '学员综合评分' },
  { value: '24h', label: 'AI 伴学在线' },
]

const formState = reactive({
  account: '',
  password: '',
  remember: false,
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMsg = ref('')
const errorHint = ref('')
const forgotNotice = ref(false)

/** 视频背景在“减少动效”偏好下不播放，退回静态照片 */
const reduceMotion = ref(false)
const videoFailed = ref(false)
const posterSrc = '/media/control-room.jpg'
let motionQuery: MediaQueryList | null = null

function syncMotionPreference(event?: MediaQueryListEvent) {
  reduceMotion.value = event ? event.matches : (motionQuery?.matches ?? false)
}

type ValidatorCallback = (error?: string | Error) => void

function validateAccount(_rule: unknown, value: unknown, callback: ValidatorCallback) {
  const account = typeof value === 'string' ? value.trim() : ''
  if (!account) {
    callback(new Error('请输入手机号或邮箱'))
    return
  }
  if (!PHONE_RE.test(account) && !EMAIL_RE.test(account)) {
    callback(new Error('手机号需为 11 位数字，或填写有效邮箱'))
    return
  }
  callback()
}

const rules: FormRules = {
  account: [{ validator: validateAccount, trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
}

const canSubmit = computed(() => !loading.value)

/** 只接受站内路径，避免 redirect 参数被用来做跳转钓鱼 */
function resolveRedirect(): string {
  const raw = route.query.redirect
  const target = Array.isArray(raw) ? raw[0] : raw
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return '/portal/home'
}

function pickToken(raw: unknown): string {
  if (typeof raw === 'string') return raw
  if (!raw || typeof raw !== 'object') return ''
  const outer = raw as Record<string, unknown>
  if (typeof outer.token === 'string') return outer.token
  const data = outer.data
  if (typeof data === 'string') return data
  if (data && typeof data === 'object') {
    const inner = data as Record<string, unknown>
    if (typeof inner.token === 'string') return inner.token
  }
  return ''
}

function pickUserInfo(raw: unknown): UserInfo | null {
  if (!raw || typeof raw !== 'object') return null
  const outer = raw as Record<string, unknown>
  const candidate = (
    outer.data && typeof outer.data === 'object' ? outer.data : outer
  ) as Record<string, unknown>
  if (typeof candidate.username !== 'string' && typeof candidate.id !== 'number') return null
  return candidate as unknown as UserInfo
}

function describeError(err: unknown): { message: string; hint: string } {
  const raw = err instanceof Error ? err.message : ''
  if (/timeout/i.test(raw)) {
    return { message: '登录请求超时，服务响应过慢，请稍后重试。', hint: OFFLINE_HINT }
  }
  if (/network error|failed to fetch|econnrefused|err_connection/i.test(raw)) {
    return { message: '无法连接到服务器，登录服务当前不可用。', hint: OFFLINE_HINT }
  }
  if (/status code 5\d\d/i.test(raw)) {
    return { message: '服务暂时不可用，登录接口返回了错误。', hint: OFFLINE_HINT }
  }
  if (/status code 4\d\d/i.test(raw)) {
    return { message: '请求被拒绝，请确认账号信息是否正确。', hint: '' }
  }
  if (raw) return { message: raw, hint: '' }
  return { message: '登录失败，请稍后重试。', hint: '' }
}

function onForgotPassword() {
  forgotNotice.value = true
  errorMsg.value = ''
  errorHint.value = ''
}

async function handleSubmit() {
  if (loading.value) return
  errorMsg.value = ''
  errorHint.value = ''
  forgotNotice.value = false

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const account = formState.account.trim()
  let redirectTo = ''

  loading.value = true
  try {
    const payload: LoginRequest = {
      type: 1,
      password: formState.password,
      rememberMe: formState.remember,
      // 后端 LoginFormDTO 只有 cellPhone / username 两个账号字段，没有 phone：
      // 输入像手机号就走 cellPhone，否则按用户名提交。
      ...(PHONE_RE.test(account) ? { cellPhone: account } : { username: account }),
    }
    const raw: unknown = await login(payload)
    const token = pickToken(raw)
    if (!token) {
      errorMsg.value = '服务未返回登录凭证，请稍后再试。'
      return
    }

    userStore.setToken(token)

    // 用户信息拉取失败不应阻断登录流程
    try {
      const infoRaw: unknown = await getUserInfo()
      const info = pickUserInfo(infoRaw)
      if (info) userStore.setInfo(info)
    } catch {
      // 顶栏会在后续请求中自行补齐
    }

    if (formState.remember) {
      localStorage.setItem(REMEMBER_KEY, account)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }

    redirectTo = resolveRedirect()
  } catch (err) {
    const described = describeError(err)
    errorMsg.value = described.message
    errorHint.value = described.hint
  } finally {
    loading.value = false
  }

  if (redirectTo) {
    ElMessage.success('登录成功')
    router.push(redirectTo)
  }
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncMotionPreference()
  motionQuery.addEventListener('change', syncMotionPreference)

  const remembered = localStorage.getItem(REMEMBER_KEY)
  if (remembered) {
    formState.account = remembered
    formState.remember = true
  }
})

onUnmounted(() => {
  motionQuery?.removeEventListener('change', syncMotionPreference)
})
</script>

<template>
  <div class="auth">
    <aside class="auth__brand">
      <div class="auth__media" aria-hidden="true">
        <video
          v-if="!reduceMotion && !videoFailed"
          class="auth__video"
          src="/media/login-bg.mp4"
          :poster="posterSrc"
          muted
          autoplay
          loop
          playsinline
          preload="metadata"
          @error="videoFailed = true"
        ></video>
        <img v-else class="auth__still" :src="posterSrc" alt="" />
      </div>
      <div class="auth__scrim" aria-hidden="true"></div>

      <div class="auth__brand-inner">
        <div class="auth__lockup">
          <span class="auth__mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="20" height="20" fill="none">
              <path
                d="M16 3.5c1.1 8.3 4.2 11.4 12.5 12.5C20.2 17.1 17.1 20.2 16 28.5 14.9 20.2 11.8 17.1 3.5 16 11.8 14.9 14.9 11.8 16 3.5Z"
                fill="currentColor"
              />
            </svg>
          </span>
          <span class="auth__lockup-copy">
            <strong>NovaMind</strong>
            <em>智星云学习平台</em>
          </span>
        </div>

        <div class="auth__pitch">
          <p class="nm-kicker auth__kicker">Learning Cloud</p>
          <h2 class="auth__headline">把课程、练习与 AI 伴学放在同一条路径上</h2>
          <p class="auth__lede">
            从系统化课程到随时可追问的 AI 助手，智星云把学习路径、进度与反馈连成一条线。
          </p>

          <ul class="auth__props">
            <li v-for="item in highlights" :key="item.title" class="auth__prop">
              <span class="auth__prop-ico" aria-hidden="true">
                <el-icon :size="15"><component :is="item.icon" /></el-icon>
              </span>
              <span class="auth__prop-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ item.desc }}</span>
              </span>
            </li>
          </ul>
        </div>

        <ul class="auth__trust">
          <li v-for="item in trust" :key="item.label">
            <strong class="nm-num">{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </li>
        </ul>
      </div>
    </aside>

    <section class="auth__panel">
      <div class="auth__panel-inner">
        <div class="auth__panel-brand">
          <span class="auth__mark auth__mark--ink" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="18" height="18" fill="none">
              <path
                d="M16 3.5c1.1 8.3 4.2 11.4 12.5 12.5C20.2 17.1 17.1 20.2 16 28.5 14.9 20.2 11.8 17.1 3.5 16 11.8 14.9 14.9 11.8 16 3.5Z"
                fill="currentColor"
              />
            </svg>
          </span>
          <span class="auth__panel-brand-name">NovaMind 智星云</span>
        </div>

        <p class="nm-kicker auth__form-kicker">Account</p>
        <h1 class="nm-h1 auth__title">欢迎回来</h1>
        <p class="auth__sub">登录后继续你的学习进度与 AI 对话记录。</p>

        <div v-if="errorMsg" class="auth__alert" role="alert">
          <el-icon class="auth__alert-ico" :size="17"><Warning /></el-icon>
          <div class="auth__alert-body">
            <strong>登录未成功</strong>
            <p>{{ errorMsg }}</p>
            <p v-if="errorHint" class="auth__alert-hint">{{ errorHint }}</p>
          </div>
        </div>

        <div v-else-if="forgotNotice" class="auth__note" role="status">
          <strong>暂未开放自助找回密码</strong>
          <p>
            演示环境没有接入短信与邮件通道，请联系管理员重置密码，或先使用其他账号登录。
          </p>
        </div>

        <el-form
          ref="formRef"
          class="auth__form"
          :model="formState"
          :rules="rules"
          label-position="top"
          hide-required-asterisk
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="手机号 / 邮箱" prop="account">
            <el-input
              v-model="formState.account"
              size="large"
              placeholder="请输入手机号或邮箱"
              autocomplete="username"
              :prefix-icon="Phone"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="formState.password"
              type="password"
              size="large"
              placeholder="请输入登录密码"
              autocomplete="current-password"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <div class="auth__row">
            <el-checkbox v-model="formState.remember">记住我</el-checkbox>
            <button type="button" class="auth__link" @click="onForgotPassword">忘记密码？</button>
          </div>

          <button
            type="submit"
            class="nm-btn nm-btn--primary nm-btn--lg auth__submit"
            :disabled="!canSubmit"
          >
            <span v-if="loading" class="auth__spinner" aria-hidden="true"></span>
            <span>{{ loading ? '正在登录…' : '登录' }}</span>
          </button>
        </el-form>

        <p class="auth__switch">
          还没有账号？
          <router-link class="auth__switch-link" to="/portal/register">立即注册</router-link>
        </p>
        <p class="auth__legal">登录即表示同意平台服务条款与隐私政策。</p>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.auth {
  display: grid;
  grid-template-columns: minmax(0, 52fr) minmax(0, 48fr);
  min-height: calc(100vh - 88px);
  background: var(--nm-paper);
}

/* ---------- 左：品牌面板 ---------- */
.auth__brand {
  position: relative;
  display: flex;
  overflow: hidden;
  background: var(--nm-invert-bg);
  color: var(--nm-invert-fg);
  isolation: isolate;
}

.auth__media {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.auth__video,
.auth__still {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth__scrim {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--nm-invert-bg) 86%, transparent) 0%,
      color-mix(in srgb, var(--nm-invert-bg) 34%, transparent) 74%,
      transparent 100%
    ),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--nm-invert-bg) 58%, transparent) 0%,
      color-mix(in srgb, var(--nm-invert-bg) 74%, transparent) 52%,
      color-mix(in srgb, var(--nm-invert-bg) 93%, transparent) 100%
    );
}

.auth__brand-inner {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: clamp(28px, 4vw, 56px);
  padding: clamp(28px, 3.6vw, 60px);
}

.auth__lockup {
  display: flex;
  align-items: center;
  gap: 10px;
}

.auth__mark {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-sm);
  border: 1px solid var(--nm-invert-line);
  background: color-mix(in srgb, var(--nm-invert-fg) 10%, transparent);
  color: var(--nm-invert-fg);
}

.auth__mark--ink {
  width: 32px;
  height: 32px;
  border-color: var(--nm-line);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.auth__lockup-copy {
  display: grid;
  line-height: 1.25;
}

.auth__lockup-copy strong {
  font-size: 1.0625rem;
  font-weight: 750;
  letter-spacing: -0.01em;
  color: var(--nm-invert-fg);
}

.auth__lockup-copy em {
  font-style: normal;
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.04em;
  color: var(--nm-invert-fg-2);
}

.auth__pitch {
  display: grid;
  gap: 16px;
  max-width: 30rem;
  margin-top: auto;
  margin-bottom: auto;
}

.auth__kicker {
  color: var(--nm-invert-fg-2);
}

.auth__kicker::before {
  background: var(--nm-invert-fg-2);
}

.auth__headline {
  font-size: clamp(1.6rem, 2.5vw, 2.35rem);
  font-weight: 780;
  line-height: 1.16;
  letter-spacing: -0.026em;
  color: var(--nm-invert-fg);
}

.auth__lede {
  font-size: 1rem;
  line-height: 1.72;
  color: var(--nm-invert-fg-2);
  max-width: 34ch;
}

.auth__props {
  display: grid;
  gap: 14px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.auth__prop {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.auth__prop-ico {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-xs);
  border: 1px solid var(--nm-invert-line);
  color: var(--nm-invert-fg);
}

.auth__prop-copy {
  display: grid;
  gap: 2px;
}

.auth__prop-copy strong {
  font-size: var(--nm-fs-body);
  font-weight: 650;
  color: var(--nm-invert-fg);
}

.auth__prop-copy span {
  font-size: var(--nm-fs-sm);
  line-height: 1.55;
  color: var(--nm-invert-fg-2);
}

.auth__trust {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(18px, 3vw, 40px);
  margin: 0;
  padding-top: clamp(18px, 2.4vw, 28px);
  border-top: 1px solid var(--nm-invert-line);
  list-style: none;
}

.auth__trust li {
  display: grid;
  gap: 2px;
}

.auth__trust strong {
  font-size: 1.375rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--nm-invert-fg);
}

.auth__trust span {
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.02em;
  color: var(--nm-invert-fg-2);
}

/* ---------- 右：表单 ---------- */
.auth__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(28px, 3.6vw, 60px) clamp(20px, 3.2vw, 56px);
  background: var(--nm-surface);
  border-left: 1px solid var(--nm-line);
}

.auth__panel-inner {
  width: 100%;
  max-width: 400px;
}

.auth__panel-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: clamp(22px, 3vw, 34px);
}

.auth__panel-brand-name {
  font-size: var(--nm-fs-sm);
  font-weight: 650;
  letter-spacing: 0.01em;
  color: var(--nm-ink-2);
}

.auth__form-kicker {
  margin-bottom: 10px;
}

.auth__title {
  margin-bottom: 10px;
}

.auth__sub {
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-body);
  margin-bottom: clamp(20px, 2.6vw, 28px);
}

.auth__alert,
.auth__note {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 15px;
  margin-bottom: 20px;
  border-radius: var(--nm-r);
  border: 1px solid var(--nm-danger-soft);
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
}

.auth__alert-ico {
  flex: none;
  margin-top: 2px;
}

.auth__alert-body,
.auth__note {
  display: grid;
  gap: 3px;
}

.auth__alert-body strong,
.auth__note strong {
  font-size: var(--nm-fs-sm);
  font-weight: 700;
}

.auth__alert-body p,
.auth__note p {
  font-size: var(--nm-fs-sm);
  line-height: 1.6;
}

.auth__alert-hint {
  opacity: 0.86;
}

.auth__note {
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.auth__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 2px 0 22px;
}

.auth__link {
  border: 0;
  background: none;
  padding: 0;
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-accent);
  cursor: pointer;
}

.auth__link:hover {
  color: var(--nm-accent-hover);
  text-decoration: underline;
}

.auth__submit {
  width: 100%;
}

.auth__submit:disabled {
  cursor: progress;
  opacity: 0.78;
  transform: none;
  box-shadow: none;
}

.auth__spinner {
  width: 15px;
  height: 15px;
  border-radius: var(--nm-r-full);
  border: 2px solid currentColor;
  border-top-color: transparent;
  animation: auth-spin 0.7s linear infinite;
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

.auth__switch {
  margin-top: 22px;
  text-align: center;
  font-size: var(--nm-fs-body);
  color: var(--nm-ink-3);
}

.auth__switch-link {
  font-weight: 650;
  color: var(--nm-accent);
  margin-left: 2px;
}

.auth__switch-link:hover {
  color: var(--nm-accent-hover);
  text-decoration: underline;
}

.auth__legal {
  margin-top: 10px;
  text-align: center;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

/* ---------- Element Plus 表单令牌化 ---------- */
.auth__form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 6px;
    font-size: var(--nm-fs-sm);
    font-weight: 650;
    line-height: 1.4;
    color: var(--nm-ink-2);
  }

  :deep(.el-input__wrapper) {
    min-height: 46px;
    padding: 0 14px;
    border-radius: var(--nm-r);
    background: var(--nm-surface);
  }

  :deep(.el-input__inner) {
    font-size: var(--nm-fs-body);
    color: var(--nm-ink);
  }

  :deep(.el-input__prefix) {
    color: var(--nm-ink-4);
  }

  :deep(.el-form-item__error) {
    font-size: var(--nm-fs-xs);
    padding-top: 3px;
  }

  :deep(.el-checkbox__label) {
    font-size: var(--nm-fs-sm);
    color: var(--nm-ink-2);
  }
}

/* ---------- 响应式 ---------- */
@media (max-width: 900px) {
  .auth {
    grid-template-columns: minmax(0, 1fr);
  }

  .auth__brand {
    min-height: 220px;
  }

  .auth__brand-inner {
    gap: 18px;
    padding: 24px var(--nm-gutter) 26px;
  }

  .auth__pitch {
    gap: 10px;
    max-width: none;
  }

  .auth__lede,
  .auth__props,
  .auth__trust {
    display: none;
  }

  .auth__headline {
    font-size: 1.35rem;
    max-width: 26ch;
  }

  .auth__panel {
    border-left: 0;
    border-top: 1px solid var(--nm-line);
    align-items: flex-start;
    padding: 28px var(--nm-gutter) 36px;
  }

  .auth__panel-inner {
    max-width: 460px;
    margin-inline: auto;
  }

  .auth__panel-brand {
    display: none;
  }
}

@media (max-width: 768px) {
  .auth {
    min-height: calc(100vh - 172px);
  }

  .auth__brand {
    min-height: 176px;
  }
}
</style>

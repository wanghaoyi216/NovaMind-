<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Iphone, Key, Lock, MagicStick, Reading, TrendCharts, Warning } from '@element-plus/icons-vue'
import { register } from '@/api/auth'
import type { RegisterRequest } from '@/types'

interface Highlight {
  title: string
  desc: string
  icon: Component
}

const PHONE_RE = /^1[3-9]\d{9}$/
const CODE_RE = /^\d{6}$/
const OFFLINE_HINT =
  '本地开发环境需要后端服务（网关 10010）已启动才能完成注册；服务未启动时你可以先浏览课程内容。'

const router = useRouter()

const highlights: Highlight[] = [
  { title: '免费开始', desc: '核心课程开放试听，无需付费即可上手', icon: Reading },
  { title: '个性化路径', desc: '依据目标与基础生成推荐学习顺序', icon: MagicStick },
  { title: '学习档案', desc: '笔记、练习与进度长期沉淀在一个账号里', icon: TrendCharts },
]

const trust: { value: string; label: string }[] = [
  { value: '120+', label: '门系统课程' },
  { value: '4.9', label: '学员综合评分' },
  { value: '新人', label: '注册课程包' },
]

const formState = reactive({
  phone: '',
  code: '',
  password: '',
  confirmPassword: '',
  agreement: false,
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const countdown = ref(0)
const errorMsg = ref('')
const errorHint = ref('')
const codeNotice = ref('')

let countdownTimer: number | null = null

const reduceMotion = ref(false)
const videoFailed = ref(false)
const posterSrc = '/media/control-room.jpg'
let motionQuery: MediaQueryList | null = null

function syncMotionPreference(event?: MediaQueryListEvent) {
  reduceMotion.value = event ? event.matches : (motionQuery?.matches ?? false)
}

type ValidatorCallback = (error?: string | Error) => void

function validateConfirm(_rule: unknown, value: unknown, callback: ValidatorCallback) {
  const entered = typeof value === 'string' ? value : ''
  if (!entered) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (entered !== formState.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

function validateAgreement(_rule: unknown, value: unknown, callback: ValidatorCallback) {
  if (value !== true) {
    callback(new Error('请阅读并同意服务条款与隐私政策'))
    return
  }
  callback()
}

const rules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_RE, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: CODE_RE, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
  agreement: [{ validator: validateAgreement, trigger: 'change' }],
}

const codeDisabled = computed(() => countdown.value > 0)
const codeLabel = computed(() => (countdown.value > 0 ? `${countdown.value}s 后重发` : '获取验证码'))

watch(
  () => formState.password,
  () => {
    if (formState.confirmPassword) {
      formRef.value?.validateField('confirmPassword').catch(() => false)
    }
  }
)

function startCountdown() {
  countdown.value = 60
  if (countdownTimer !== null) window.clearInterval(countdownTimer)
  countdownTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = 0
      if (countdownTimer !== null) {
        window.clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

function sendCode() {
  if (countdown.value > 0) return
  const phone = formState.phone.trim()
  if (!PHONE_RE.test(phone)) {
    codeNotice.value = ''
    errorMsg.value = '请先填写正确的 11 位手机号，再获取验证码。'
    errorHint.value = ''
    formRef.value?.validateField('phone').catch(() => false)
    return
  }
  errorMsg.value = ''
  errorHint.value = ''
  codeNotice.value =
    '演示环境暂未接入短信通道，未真正发送短信；这里仅演示倒计时，验证码请输入任意 6 位数字。'
  startCountdown()
}

function describeError(err: unknown): { message: string; hint: string } {
  const raw = err instanceof Error ? err.message : ''
  if (/timeout/i.test(raw)) {
    return { message: '注册请求超时，服务响应过慢，请稍后重试。', hint: OFFLINE_HINT }
  }
  if (/network error|failed to fetch|econnrefused|err_connection/i.test(raw)) {
    return { message: '无法连接到服务器，注册服务当前不可用。', hint: OFFLINE_HINT }
  }
  if (/status code 5\d\d/i.test(raw)) {
    return { message: '服务暂时不可用，注册接口返回了错误。', hint: OFFLINE_HINT }
  }
  if (/status code 4\d\d/i.test(raw)) {
    return { message: '请求被拒绝，请确认手机号是否已被注册。', hint: '' }
  }
  if (raw) return { message: raw, hint: '' }
  return { message: '注册失败，请稍后重试。', hint: '' }
}

async function handleSubmit() {
  if (loading.value) return
  errorMsg.value = ''
  errorHint.value = ''

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  let succeeded = false
  loading.value = true
  try {
    const payload: RegisterRequest = {
      // 后端 StudentFormDTO 的字段名是 cellPhone，不是 phone
      cellPhone: formState.phone.trim(),
      password: formState.password,
      code: formState.code.trim(),
    }
    await register(payload)
    succeeded = true
  } catch (err) {
    const described = describeError(err)
    errorMsg.value = described.message
    errorHint.value = described.hint
  } finally {
    loading.value = false
  }

  if (succeeded) {
    ElMessage.success('注册成功，请使用新账号登录')
    router.push('/portal/login')
  }
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  syncMotionPreference()
  motionQuery.addEventListener('change', syncMotionPreference)
})

onUnmounted(() => {
  motionQuery?.removeEventListener('change', syncMotionPreference)
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
    countdownTimer = null
  }
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
          <p class="nm-kicker auth__kicker">Get Started</p>
          <h2 class="auth__headline">从一个账号开始，搭起你的技能地图</h2>
          <p class="auth__lede">
            注册后即可开始学习核心课程，AI 助手会依据你的目标给出学习顺序与练习建议。
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
        <h1 class="nm-h1 auth__title">创建账号</h1>
        <p class="auth__sub">填写手机号与验证码，即可开始你的学习路径。</p>

        <div v-if="errorMsg" class="auth__alert" role="alert">
          <el-icon class="auth__alert-ico" :size="17"><Warning /></el-icon>
          <div class="auth__alert-body">
            <strong>注册未成功</strong>
            <p>{{ errorMsg }}</p>
            <p v-if="errorHint" class="auth__alert-hint">{{ errorHint }}</p>
          </div>
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
          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model="formState.phone"
              size="large"
              maxlength="11"
              placeholder="请输入 11 位手机号"
              autocomplete="tel"
              :prefix-icon="Iphone"
            />
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="auth__code-row">
              <el-input
                v-model="formState.code"
                size="large"
                maxlength="6"
                placeholder="请输入 6 位验证码"
                autocomplete="one-time-code"
                :prefix-icon="Key"
              />
              <button
                type="button"
                class="nm-btn auth__code-btn"
                :disabled="codeDisabled"
                @click="sendCode"
              >
                {{ codeLabel }}
              </button>
            </div>
          </el-form-item>

          <div v-if="codeNotice" class="auth__note" role="status">
            <strong>短信通道未接入</strong>
            <p>{{ codeNotice }}</p>
          </div>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="formState.password"
              type="password"
              size="large"
              placeholder="请设置 6 位以上密码"
              autocomplete="new-password"
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="formState.confirmPassword"
              type="password"
              size="large"
              placeholder="请再次输入密码"
              autocomplete="new-password"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item prop="agreement" class="auth__agree">
            <el-checkbox v-model="formState.agreement">
              我已阅读并同意平台的
              <span class="auth__agree-term">《服务条款》</span>
              与
              <span class="auth__agree-term">《隐私政策》</span>
            </el-checkbox>
          </el-form-item>

          <button
            type="submit"
            class="nm-btn nm-btn--primary nm-btn--lg auth__submit"
            :disabled="loading"
          >
            <span v-if="loading" class="auth__spinner" aria-hidden="true"></span>
            <span>{{ loading ? '正在注册…' : '注册' }}</span>
          </button>
        </el-form>

        <p class="auth__switch">
          已有账号？
          <router-link class="auth__switch-link" to="/portal/login">立即登录</router-link>
        </p>
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

.auth__code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.auth__code-btn {
  height: 46px;
  min-width: 116px;
  padding: 0 14px;
  font-size: var(--nm-fs-sm);
}

.auth__code-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
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

.auth__agree-term {
  color: var(--nm-accent);
  font-weight: 600;
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

  :deep(.el-checkbox) {
    align-items: flex-start;
    height: auto;
    white-space: normal;
  }

  :deep(.el-checkbox__input) {
    margin-top: 2px;
  }

  :deep(.el-checkbox__label) {
    font-size: var(--nm-fs-sm);
    line-height: 1.6;
    white-space: normal;
    color: var(--nm-ink-2);
  }

  .auth__agree {
    margin-bottom: 20px;
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

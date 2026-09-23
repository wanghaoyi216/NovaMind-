<script setup lang="ts">
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserInfo } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types'

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

/** 后端用户信息可能带扩展字段，这里补可选声明 */
interface ProfileUser extends UserInfo {
  nickname?: string
  createTime?: string
  signature?: string
  gender?: number
  birthday?: string
}

type ErrorKey = 'nickname' | 'birthday' | 'signature'

const userStore = useUserStore()
const page = inject<MyPageContext | null>(MY_PAGE_CONTEXT, null)

const loading = ref(false)
const loadFailed = ref(false)
const saving = ref(false)
const editing = ref(false)
const avatarVisible = ref(false)
const passwordVisible = ref(false)
const passwordSaving = ref(false)

const fallbackAvatar = '/resource/user-avatars/avatar_student_male_01.jpg'

const avatarOptions = [
  '/resource/user-avatars/avatar_student_male_01.jpg',
  '/resource/user-avatars/avatar_student_female_01.jpg',
  '/resource/user-avatars/avatar_young_man_01.png',
  '/resource/user-avatars/avatar_child_student_01.jpg',
  '/resource/user-avatars/avatar_teacher_male_01.jpg',
  '/resource/user-avatars/avatar_teacher_female_01.jpg',
  '/resource/user-avatars/avatar_businessman_phone_01.jpg',
  '/resource/user-avatars/avatar_business_partners_01.jpg',
  '/resource/user-avatars/avatar_doctor_vaccine_01.jpg',
  '/resource/user-avatars/avatar_older_man_01.png',
  '/resource/user-avatars/avatar_smartphone_blank_01.jpg',
  '/resource/user-avatars/avatar_animal_dog_01.png',
]

const directions = [
  '前端开发',
  '后端开发',
  '微服务架构',
  '人工智能',
  '数据分析',
  '移动开发',
  '云计算',
  'UI / UX 设计',
  '数据库',
  '测试与运维',
]

const goals = ['每天 30 分钟', '每周 3 小时', '系统进阶', '求职冲刺']

const genders = [
  { value: 1, label: '男' },
  { value: 2, label: '女' },
  { value: 0, label: '保密' },
]

const form = reactive({
  nickname: '',
  gender: 0,
  birthday: '',
  signature: '',
  avatar: '',
})

const prefs = reactive({
  directions: [] as string[],
  goal: '',
})

const errors = reactive<Record<ErrorKey, string>>({
  nickname: '',
  birthday: '',
  signature: '',
})

const password = reactive({ current: '', next: '', confirm: '' })
const passwordError = ref('')

const meta = reactive({
  phone: '',
  memberSince: '',
  studentNo: '',
})

const snapshot = ref('')

const displayAvatar = computed(() => form.avatar || userStore.userInfo?.avatar || fallbackAvatar)
const displayName = computed(() => form.nickname || userStore.userInfo?.username || '学员用户')
const maskedPhone = computed(() => {
  const phone = meta.phone
  if (!phone) return '未绑定手机号'
  return phone.length >= 11 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
})

const dirty = computed(() => JSON.stringify({ form, prefs }) !== snapshot.value)

function takeSnapshot() {
  snapshot.value = JSON.stringify({ form, prefs })
}

function formatDate(value: string) {
  if (!value) return '—'
  const stamp = new Date(value).getTime()
  if (Number.isNaN(stamp)) return value.slice(0, 10)
  const date = new Date(stamp)
  const pad = (input: number) => String(input).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function applyUser(user: ProfileUser) {
  form.nickname = user.nickname || user.username || ''
  form.avatar = user.avatar || ''
  form.gender = typeof user.gender === 'number' ? user.gender : 0
  form.birthday = user.birthday ? formatDate(user.birthday) : ''
  form.signature = user.signature || ''
  meta.phone = user.phone || ''
  meta.memberSince = user.createTime ? formatDate(user.createTime) : ''
  meta.studentNo = user.id ? `NM-${String(user.id).padStart(6, '0')}` : '—'
  takeSnapshot()
}

function validate() {
  errors.nickname = ''
  errors.birthday = ''
  errors.signature = ''

  const nickname = form.nickname.trim()
  if (!nickname) errors.nickname = '请填写昵称'
  else if (nickname.length < 2 || nickname.length > 20) errors.nickname = '昵称需要 2-20 个字符'

  if (form.birthday) {
    const stamp = new Date(form.birthday.replace(/-/g, '/')).getTime()
    if (Number.isNaN(stamp)) errors.birthday = '日期格式不正确'
    else if (stamp > Date.now()) errors.birthday = '生日不能晚于今天'
    else if (stamp < new Date('1900/01/01').getTime()) errors.birthday = '请选择 1900 年之后的日期'
  }

  if (form.signature.length > 60) errors.signature = '个性签名最多 60 个字符'

  return !errors.nickname && !errors.birthday && !errors.signature
}

function toggleDirection(item: string) {
  if (!editing.value) return
  const index = prefs.directions.indexOf(item)
  if (index >= 0) prefs.directions.splice(index, 1)
  else prefs.directions.push(item)
}

function pickAvatar(path: string) {
  form.avatar = path
  avatarVisible.value = false
  ElMessage.success('已选择新头像，保存后生效')
}

function cancelEdit() {
  if (!snapshot.value) return
  const restored = JSON.parse(snapshot.value) as { form: typeof form; prefs: typeof prefs }
  Object.assign(form, restored.form)
  Object.assign(prefs, restored.prefs)
  errors.nickname = ''
  errors.birthday = ''
  errors.signature = ''
  editing.value = false
}

async function save() {
  if (!validate()) {
    ElMessage.warning('请先修正表单中的问题')
    return
  }
  saving.value = true
  // 资料保存接口尚未接入：这里完成本地落库 + 反馈，保证交互闭环
  await new Promise((resolve) => setTimeout(resolve, 500))
  const base: UserInfo =
    userStore.userInfo ??
    ({
      id: 0,
      username: form.nickname,
      phone: meta.phone,
      avatar: form.avatar,
      userType: 1,
      token: userStore.token,
    } as UserInfo)
  userStore.setInfo({
    ...base,
    username: form.nickname,
    phone: meta.phone,
    avatar: form.avatar || base.avatar,
  })
  saving.value = false
  editing.value = false
  takeSnapshot()
  ElMessage.success('资料已保存')
}

function submitPassword() {
  passwordError.value = ''
  if (!password.current || !password.next) {
    passwordError.value = '请填写原密码与新密码'
    return
  }
  if (password.next.length < 8) {
    passwordError.value = '新密码至少 8 位'
    return
  }
  if (password.next !== password.confirm) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  setTimeout(() => {
    passwordSaving.value = false
    ElMessage.warning('密码修改接口尚未接入，本次未提交到服务端')
  }, 500)
}

function notWired(label: string) {
  ElMessage.info(`${label}尚未接入，敬请期待`)
}

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getUserInfo()
    if (res?.data) {
      userStore.setInfo(res.data as UserInfo)
      applyUser(res.data as ProfileUser)
    } else {
      applyUser((userStore.userInfo ?? {}) as ProfileUser)
    }
  } catch {
    loadFailed.value = true
    applyUser((userStore.userInfo ?? {}) as ProfileUser)
  } finally {
    loading.value = false
  }
}

if (page) {
  page.title = '个人中心'
  page.description = '资料、账号安全与学习偏好，改动会同步到你的学习档案。'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="pf">
    <!-- 资料头卡 -->
    <section class="pf-hero nm-card">
      <div v-if="loading" class="pf-hero__skeleton">
        <div class="nm-skeleton" style="width: 84px; height: 84px; border-radius: var(--nm-r-full)" />
        <div class="pf-hero__skeleton-copy">
          <div class="nm-skeleton" style="width: 180px; height: 22px" />
          <div class="nm-skeleton" style="width: 240px; height: 14px" />
          <div class="nm-skeleton" style="width: 150px; height: 14px" />
        </div>
      </div>

      <div v-else class="pf-hero__inner">
        <div class="pf-avatar">
          <img :src="displayAvatar" :alt="displayName" />
          <button
            type="button"
            class="pf-avatar__edit"
            aria-label="更换头像"
            @click="avatarVisible = true"
          >
            更换头像
          </button>
        </div>

        <div class="pf-hero__copy">
          <p class="nm-kicker">PROFILE</p>
          <h2 class="nm-h2 pf-hero__name">{{ displayName }}</h2>
          <p class="pf-hero__meta">
            <span class="nm-num">{{ maskedPhone }}</span>
            <span class="nm-badge nm-badge--success">已绑定手机</span>
          </p>
          <p class="pf-hero__sub nm-muted nm-num">
            学员编号 {{ meta.studentNo }} · 加入时间 {{ meta.memberSince }}
          </p>
        </div>

        <div class="pf-hero__actions">
          <button
            v-if="!editing"
            type="button"
            class="nm-btn nm-btn--primary"
            @click="editing = true"
          >
            编辑资料
          </button>
          <template v-else>
            <button type="button" class="nm-btn nm-btn--ghost" @click="cancelEdit">退出编辑</button>
          </template>
        </div>
      </div>

      <p v-if="loadFailed" class="pf-notice">
        <span class="pf-notice__dot" aria-hidden="true" />
        未能从服务端加载最新资料，当前展示本地缓存内容。
        <button type="button" class="pf-notice__link" @click="load">重试</button>
      </p>
    </section>

    <div class="pf-grid">
      <div class="pf-col">
        <!-- 基本资料 -->
        <section class="pf-card nm-card nm-card--pad">
          <header class="pf-card__head">
            <div>
              <p class="nm-kicker">BASIC</p>
              <h3 class="nm-h3">基本资料</h3>
            </div>
            <span v-if="editing" class="nm-badge nm-badge--accent">编辑中</span>
          </header>

          <div class="pf-field">
            <label class="pf-label" for="pf-nickname">昵称</label>
            <input
              id="pf-nickname"
              v-model="form.nickname"
              class="nm-field"
              type="text"
              maxlength="20"
              placeholder="给自己起个名字"
              :disabled="!editing"
              :aria-invalid="!!errors.nickname"
              @input="errors.nickname = ''"
            />
            <p v-if="errors.nickname" class="pf-error">{{ errors.nickname }}</p>
          </div>

          <div class="pf-field">
            <span class="pf-label">性别</span>
            <div class="pf-chips">
              <button
                v-for="item in genders"
                :key="item.value"
                type="button"
                :class="['nm-chip', { 'is-active': form.gender === item.value }]"
                :disabled="!editing"
                @click="form.gender = item.value"
              >
                {{ item.label }}
              </button>
            </div>
          </div>

          <div class="pf-field">
            <label class="pf-label" for="pf-birthday">生日</label>
            <input
              id="pf-birthday"
              v-model="form.birthday"
              class="nm-field pf-field--date"
              type="date"
              :disabled="!editing"
              :aria-invalid="!!errors.birthday"
              @input="errors.birthday = ''"
            />
            <p v-if="errors.birthday" class="pf-error">{{ errors.birthday }}</p>
          </div>

          <div class="pf-field">
            <label class="pf-label" for="pf-signature">个性签名</label>
            <textarea
              id="pf-signature"
              v-model="form.signature"
              class="nm-field pf-textarea"
              rows="3"
              maxlength="80"
              placeholder="写一句学习宣言，会显示在社区主页"
              :disabled="!editing"
              :aria-invalid="!!errors.signature"
              @input="errors.signature = ''"
            />
            <p class="pf-counter nm-muted nm-num">
              {{ form.signature.length }} / 60
              <span v-if="errors.signature" class="pf-error pf-error--inline">{{ errors.signature }}</span>
            </p>
          </div>
        </section>

        <!-- 学习偏好 -->
        <section class="pf-card nm-card nm-card--pad">
          <header class="pf-card__head">
            <div>
              <p class="nm-kicker">PREFERENCE</p>
              <h3 class="nm-h3">学习偏好</h3>
            </div>
            <span class="nm-tag nm-num">已选 {{ prefs.directions.length }} 个方向</span>
          </header>

          <div class="pf-field">
            <span class="pf-label">感兴趣的方向</span>
            <p class="pf-hint nm-muted">用于首页与课程中心推荐，可多选。</p>
            <div class="pf-chips">
              <button
                v-for="item in directions"
                :key="item"
                type="button"
                :class="['nm-chip', { 'is-active': prefs.directions.includes(item) }]"
                :disabled="!editing"
                @click="toggleDirection(item)"
              >
                {{ item }}
              </button>
            </div>
          </div>

          <div class="pf-field">
            <span class="pf-label">学习目标</span>
            <div class="pf-chips">
              <button
                v-for="item in goals"
                :key="item"
                type="button"
                :class="['nm-chip', { 'is-active': prefs.goal === item }]"
                :disabled="!editing"
                @click="prefs.goal = prefs.goal === item ? '' : item"
              >
                {{ item }}
              </button>
            </div>
          </div>
        </section>
      </div>

      <div class="pf-col">
        <!-- 账号安全 -->
        <section class="pf-card nm-card nm-card--pad">
          <header class="pf-card__head">
            <div>
              <p class="nm-kicker">SECURITY</p>
              <h3 class="nm-h3">账号安全</h3>
            </div>
          </header>

          <ul class="pf-sec">
            <li class="pf-sec__row">
              <div class="pf-sec__copy">
                <p class="pf-sec__label">手机号</p>
                <p class="pf-sec__value nm-num">{{ maskedPhone }}</p>
              </div>
              <div class="pf-sec__side">
                <span class="nm-badge nm-badge--success">已绑定</span>
                <button type="button" class="nm-btn nm-btn--sm nm-btn--ghost" @click="notWired('短信验证')">
                  更换
                </button>
              </div>
            </li>

            <li class="pf-sec__row">
              <div class="pf-sec__copy">
                <p class="pf-sec__label">登录密码</p>
                <p class="pf-sec__value nm-muted">建议 90 天更换一次</p>
              </div>
              <div class="pf-sec__side">
                <span class="nm-badge nm-badge--neutral">安全</span>
                <button
                  type="button"
                  class="nm-btn nm-btn--sm nm-btn--ghost"
                  @click="passwordVisible = true"
                >
                  修改
                </button>
              </div>
            </li>

            <li class="pf-sec__row">
              <div class="pf-sec__copy">
                <p class="pf-sec__label">微信</p>
                <p class="pf-sec__value nm-muted">绑定后可扫码登录</p>
              </div>
              <div class="pf-sec__side">
                <span class="nm-badge nm-badge--neutral">未绑定</span>
                <button type="button" class="nm-btn nm-btn--sm nm-btn--ghost" @click="notWired('微信绑定')">
                  去绑定
                </button>
              </div>
            </li>

            <li class="pf-sec__row">
              <div class="pf-sec__copy">
                <p class="pf-sec__label">邮箱</p>
                <p class="pf-sec__value nm-muted">用于接收课程与订单通知</p>
              </div>
              <div class="pf-sec__side">
                <span class="nm-badge nm-badge--neutral">未绑定</span>
                <button type="button" class="nm-btn nm-btn--sm nm-btn--ghost" @click="notWired('邮箱绑定')">
                  去绑定
                </button>
              </div>
            </li>
          </ul>
        </section>

        <section class="pf-card nm-card nm-card--pad pf-card--quiet">
          <header class="pf-card__head">
            <div>
              <p class="nm-kicker">ABOUT</p>
              <h3 class="nm-h3">数据与隐私</h3>
            </div>
          </header>
          <ul class="pf-notes">
            <li>资料与头像的保存接口尚未接入，改动暂存于本地，刷新后回到服务端数据。</li>
            <li>短信验证、第三方账号绑定同样处于待接入状态。</li>
            <li>学习记录与订单数据来自各微服务，仅用于展示学习进度。</li>
          </ul>
        </section>
      </div>
    </div>

    <!-- 保存操作条 -->
    <div class="pf-savebar nm-card nm-card--pad">
      <p class="pf-savebar__hint">
        <template v-if="editing && dirty">
          <span class="pf-notice__dot" aria-hidden="true" />
          有未保存的改动
        </template>
        <template v-else-if="editing">编辑中，暂无改动</template>
        <template v-else>点击「编辑资料」开始修改</template>
      </p>
      <div class="pf-savebar__actions">
        <button type="button" class="nm-btn nm-btn--ghost" :disabled="!editing" @click="cancelEdit">
          取消
        </button>
        <button
          type="button"
          class="nm-btn nm-btn--primary"
          :disabled="!editing || saving"
          @click="save"
        >
          <span v-if="saving" class="pf-spin" aria-hidden="true" />
          {{ saving ? '保存中…' : '保存修改' }}
        </button>
      </div>
    </div>

    <!-- 更换头像 -->
    <el-dialog v-model="avatarVisible" title="更换头像" width="440px" align-center>
      <div class="pf-avatars">
        <button
          v-for="item in avatarOptions"
          :key="item"
          type="button"
          :class="['pf-avatars__item', { 'is-active': displayAvatar === item }]"
          @click="pickAvatar(item)"
        >
          <img :src="item" alt="" loading="lazy" />
        </button>
      </div>
      <p class="pf-hint nm-muted">当前为内置头像库，上传自定义头像需要对象存储服务支持。</p>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="420px" align-center>
      <div class="pf-field">
        <label class="pf-label" for="pf-pwd-current">当前密码</label>
        <input
          id="pf-pwd-current"
          v-model="password.current"
          class="nm-field"
          type="password"
          placeholder="请输入当前密码"
        />
      </div>
      <div class="pf-field">
        <label class="pf-label" for="pf-pwd-next">新密码</label>
        <input
          id="pf-pwd-next"
          v-model="password.next"
          class="nm-field"
          type="password"
          placeholder="至少 8 位"
        />
      </div>
      <div class="pf-field">
        <label class="pf-label" for="pf-pwd-confirm">确认新密码</label>
        <input
          id="pf-pwd-confirm"
          v-model="password.confirm"
          class="nm-field"
          type="password"
          placeholder="再次输入新密码"
        />
      </div>
      <p v-if="passwordError" class="pf-error">{{ passwordError }}</p>
      <template #footer>
        <button type="button" class="nm-btn nm-btn--ghost" @click="passwordVisible = false">
          取消
        </button>
        <button type="button" class="nm-btn nm-btn--primary" :disabled="passwordSaving" @click="submitPassword">
          <span v-if="passwordSaving" class="pf-spin" aria-hidden="true" />
          {{ passwordSaving ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.pf {
  display: grid;
  gap: clamp(18px, 2.2vw, 26px);
}

/* 禁用态（设计系统只定义常态，这里按页面需要补齐） */
.nm-btn:disabled,
.nm-btn:disabled:hover {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ---------------- 资料头卡 ---------------- */
.pf-hero {
  padding: clamp(20px, 2.4vw, 30px);
}

.pf-hero__inner {
  display: flex;
  align-items: center;
  gap: clamp(18px, 2.2vw, 28px);
  flex-wrap: wrap;
}

.pf-avatar {
  position: relative;
  width: 92px;
  height: 92px;
  flex-shrink: 0;
}

.pf-avatar img {
  width: 100%;
  height: 100%;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.pf-avatar__edit {
  position: absolute;
  left: 50%;
  bottom: -10px;
  transform: translateX(-50%);
  padding: 4px 12px;
  border: 1px solid var(--nm-line-strong);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: var(--nm-sh-2);
  transition: color var(--nm-dur-fast) var(--nm-ease),
    border-color var(--nm-dur-fast) var(--nm-ease);
}

.pf-avatar__edit:hover {
  color: var(--nm-accent);
  border-color: var(--nm-accent-line);
}

.pf-hero__copy {
  display: grid;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.pf-hero__name {
  margin-top: 2px;
}

.pf-hero__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-2);
}

.pf-hero__sub {
  font-size: var(--nm-fs-xs);
}

.pf-hero__actions {
  display: flex;
  gap: 10px;
}

.pf-hero__skeleton {
  display: flex;
  align-items: center;
  gap: 24px;
}

.pf-hero__skeleton-copy {
  display: grid;
  gap: 10px;
}

.pf-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--nm-line);
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.pf-notice__dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: var(--nm-r-full);
  background: var(--nm-warning);
  flex-shrink: 0;
}

.pf-notice__link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--nm-accent);
  font-weight: 600;
  cursor: pointer;
}

/* ---------------- 分栏 ---------------- */
.pf-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.pf-col {
  display: grid;
  gap: 18px;
}

.pf-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--nm-line);
}

.pf-card--quiet {
  background: var(--nm-surface-2);
  box-shadow: none;
}

/* ---------------- 表单 ---------------- */
.pf-field {
  display: grid;
  gap: 7px;
  margin-bottom: 16px;
}

.pf-field:last-child {
  margin-bottom: 0;
}

.pf-label {
  font-size: var(--nm-fs-sm);
  font-weight: 620;
  color: var(--nm-ink-2);
}

.pf-hint {
  font-size: var(--nm-fs-xs);
}

.pf-textarea {
  padding: 12px 14px;
  min-height: 88px;
  resize: vertical;
  line-height: 1.65;
}

.pf-field--date {
  max-width: 240px;
}

.pf-counter {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--nm-fs-xs);
}

.pf-error {
  color: var(--nm-danger);
  font-size: var(--nm-fs-xs);
  font-weight: 550;
}

.pf-error--inline {
  margin-left: auto;
}

.pf-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pf-chips .nm-chip:disabled {
  cursor: default;
  opacity: 0.72;
}

.pf-chips .nm-chip:disabled:hover {
  border-color: var(--nm-line);
  color: var(--nm-ink-2);
  background: var(--nm-surface);
}

.pf-chips .nm-chip.is-active:disabled:hover {
  border-color: var(--nm-ink);
  background: var(--nm-ink);
  color: var(--nm-paper);
}

[data-theme='dark'] .pf-chips .nm-chip.is-active:disabled:hover {
  color: var(--nm-accent-ink);
}

.nm-field:disabled {
  background: var(--nm-surface-2);
  color: var(--nm-ink-3);
  cursor: default;
}

/* ---------------- 账号安全 ---------------- */
.pf-sec {
  display: grid;
  list-style: none;
}

.pf-sec__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 0;
}

.pf-sec__row + .pf-sec__row {
  border-top: 1px solid var(--nm-line);
}

.pf-sec__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.pf-sec__label {
  font-size: var(--nm-fs-body);
  font-weight: 620;
  color: var(--nm-ink);
}

.pf-sec__value {
  font-size: var(--nm-fs-xs);
}

.pf-sec__side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.pf-notes {
  display: grid;
  gap: 10px;
  list-style: none;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
  line-height: 1.7;
}

.pf-notes li {
  position: relative;
  padding-left: 16px;
}

.pf-notes li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 5px;
  height: 5px;
  border-radius: var(--nm-r-full);
  background: var(--nm-ink-4);
}

/* ---------------- 保存条 ---------------- */
.pf-savebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  position: sticky;
  bottom: 16px;
  background: var(--nm-surface);
  box-shadow: var(--nm-sh-3);
}

.pf-savebar__hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-3);
}

.pf-savebar__actions {
  display: flex;
  gap: 10px;
}

.pf-spin {
  width: 13px;
  height: 13px;
  border-radius: var(--nm-r-full);
  border: 2px solid var(--nm-line-strong);
  border-top-color: var(--nm-accent);
  animation: pf-spin 0.7s linear infinite;
}

@keyframes pf-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------------- 头像选择 ---------------- */
.pf-avatars {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.pf-avatars__item {
  padding: 0;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
  overflow: hidden;
  cursor: pointer;
  transition: border-color var(--nm-dur-fast) var(--nm-ease),
    transform var(--nm-dur-fast) var(--nm-ease);
}

.pf-avatars__item img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.pf-avatars__item:hover {
  transform: translateY(-2px);
  border-color: var(--nm-accent-line);
}

.pf-avatars__item.is-active {
  border-color: var(--nm-accent);
  box-shadow: 0 0 0 2px var(--nm-accent-soft);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1100px) {
  .pf-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 700px) {
  .pf-hero__inner {
    gap: 16px;
  }

  .pf-avatar {
    width: 76px;
    height: 76px;
  }

  .pf-hero__actions {
    width: 100%;
  }

  .pf-hero__actions .nm-btn {
    flex: 1;
  }

  .pf-sec__row {
    align-items: flex-start;
  }

  .pf-avatars {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .pf-savebar {
    position: static;
  }

  .pf-savebar__actions {
    width: 100%;
  }

  .pf-savebar__actions .nm-btn {
    flex: 1;
  }
}
</style>

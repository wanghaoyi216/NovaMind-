<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Check,
  CopyDocument,
  Delete,
  Document,
  EditPen,
  MagicStick,
  Refresh,
  Search,
  User,
  Warning,
} from '@element-plus/icons-vue'
import {
  deleteSessionHistory,
  getAllSessions,
  getChatTemplates,
  getSessionHistory,
  updateSessionTitle,
} from '@/api/aigc'
import { friendlyErrorMessage } from '@/utils/errorMessage'
import type { ApiResponse } from '@/types'
import type { ChatHistoryMessage, ChatSessionHistoryGroups, ChatSessionSummary, PromptTemplateMap } from '@/types/aigc'

interface SessionItem {
  sessionId: string
  title: string
  updateTime: string
  messageCount: number | null
  live: boolean
}

interface SessionGroup {
  key: string
  label: string
  items: SessionItem[]
}

interface TemplateCard {
  key: keyof PromptTemplateMap
  label: string
  desc: string
  title: string
  content: string
}

type TabKey = 'sessions' | 'templates'

const TEMPLATE_META: { key: keyof PromptTemplateMap; label: string; desc: string }[] = [
  { key: 'associationalWord', label: '联想词', desc: '围绕主题扩展相关概念与关键词' },
  { key: 'helpedWrite', label: '帮我写', desc: '按给定主题生成内容初稿' },
  { key: 'continuedWrite', label: '续写', desc: '延续已有段落继续撰写' },
  { key: 'polish', label: '润色', desc: '在不改变语义的前提下优化表达' },
  { key: 'streamline', label: '精简', desc: '压缩冗余表述，保留核心信息' },
]

const COUNT_PROBE_LIMIT = 6

const tab = ref<TabKey>('sessions')

const sessionLoading = ref(true)
const sessionDemo = ref(false)
const sessions = ref<SessionItem[]>([])
const sessionKeyword = ref('')

const templateLoading = ref(true)
const templateDemo = ref(false)
const templates = ref<TemplateCard[]>([])
const copiedKey = ref<string>('')

const drawerOpen = ref(false)
const threadLoading = ref(false)
const threadError = ref(false)
const threadDemo = ref(false)
const thread = ref<ChatHistoryMessage[]>([])
const activeSession = ref<SessionItem | null>(null)
const draftTitle = ref('')
const savingTitle = ref(false)

const groups = computed<SessionGroup[]>(() => {
  const kw = sessionKeyword.value.trim().toLowerCase()
  const filtered = sessions.value.filter((item) =>
    kw ? `${item.title}`.toLowerCase().includes(kw) : true
  )
  const bucket = new Map<string, SessionItem[]>()
  filtered.forEach((item) => {
    const stamp = item.updateTime ? dayjs(item.updateTime) : null
    const key = stamp && stamp.isValid() ? stamp.format('YYYY-MM-DD') : '未知日期'
    const list = bucket.get(key)
    if (list) list.push(item)
    else bucket.set(key, [item])
  })
  return Array.from(bucket, ([key, items]) => ({
    key,
    label: formatGroupDate(key),
    items: items.sort((a, b) => (a.updateTime < b.updateTime ? 1 : -1)),
  })).sort((a, b) => (a.key < b.key ? 1 : -1))
})

const messageTotal = computed(() => sessions.value.reduce((sum, item) => sum + (item.messageCount ?? 0), 0))

const threadCount = computed(() => thread.value.length)

function isWrapped<T>(value: unknown): value is ApiResponse<T> {
  return !!value && typeof value === 'object' && 'code' in value && 'data' in value
}

function unwrap<T>(payload: unknown): T | null {
  if (isWrapped<T>(payload)) {
    return payload.data ?? null
  }
  return (payload as T) ?? null
}

function reason(error: unknown): string {
  if (error instanceof Error && error.message) return friendlyErrorMessage(error)
  return '服务暂不可达'
}

function formatGroupDate(key: string): string {
  const stamp = dayjs(key)
  if (!stamp.isValid()) return key
  const today = dayjs().startOf('day')
  if (stamp.isSame(today, 'day')) return '今天'
  if (stamp.isSame(today.subtract(1, 'day'), 'day')) return '昨天'
  return stamp.format('MM 月 DD 日')
}

function formatTime(value: string): string {
  const stamp = dayjs(value)
  return stamp.isValid() ? stamp.format('HH:mm') : value || '—'
}

function isUserMessage(message: ChatHistoryMessage): boolean {
  return String(message.type).toUpperCase() === 'USER'
}

function demoSessionGroups(): SessionItem[] {
  const now = dayjs()
  return [
    { sessionId: 'demo-1', title: '课程下单建议', updateTime: now.subtract(24, 'minute').format('YYYY-MM-DD HH:mm'), messageCount: 8, live: false },
    { sessionId: 'demo-2', title: '学习计划拆解', updateTime: now.subtract(3, 'hour').format('YYYY-MM-DD HH:mm'), messageCount: 12, live: false },
    { sessionId: 'demo-3', title: '考试训练模式怎么用', updateTime: now.subtract(1, 'day').format('YYYY-MM-DD HH:mm'), messageCount: 5, live: false },
    { sessionId: 'demo-4', title: '帮我规划 Java 学习路线', updateTime: now.subtract(1, 'day').subtract(2, 'hour').format('YYYY-MM-DD HH:mm'), messageCount: 16, live: false },
    { sessionId: 'demo-5', title: '微服务拆分咨询', updateTime: now.subtract(2, 'day').format('YYYY-MM-DD HH:mm'), messageCount: 9, live: false },
    { sessionId: 'demo-6', title: '优惠券使用规则', updateTime: now.subtract(4, 'day').format('YYYY-MM-DD HH:mm'), messageCount: 4, live: false },
  ]
}

const DEMO_TEMPLATES: Record<keyof PromptTemplateMap, string> = {
  associationalWord: '请围绕「{主题}」联想 10 个高度相关的概念或关键词，按相关性排序，并简述每个词与本主题的关系。',
  helpedWrite: '请以「{主题}」为题写一篇结构完整的文章，包含引言、3 个核心论点与结论，语言专业、面向初学者。',
  continuedWrite: '请延续下面的内容继续撰写，保持原有语气、人称与叙述节奏，补足后续 2 个自然段：\n\n{已有内容}',
  polish: '请润色下面的文本：保持原意与信息量不变，修正语病与冗余，使表达更清晰流畅，并保持原有格式。\n\n{原文}',
  streamline: '请精简下面的文本：删除重复与冗余表述，保留全部关键信息，压缩到原文 60% 以内的字数。\n\n{原文}',
}

function demoThread(): ChatHistoryMessage[] {
  return [
    { type: 'USER', content: '我想系统学习 Java 后端开发，应该从哪里开始？' },
    { type: 'ASSISTANT', content: '建议按「语言基础 → 框架 → 工程能力」三步走：先掌握 Java 语法与集合、并发基础，再进入 Spring Boot 与 MyBatis，最后补齐微服务、容器化与可观测性。' },
    { type: 'USER', content: '大概需要多久？每天两小时的话。' },
    { type: 'ASSISTANT', content: '每天两小时的情况下，基础部分约 6 周，框架与工程化约 10 周。建议每两周完成一个可运行的小项目来固化知识。' },
  ]
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const payload = await getAllSessions()
    const map: ChatSessionHistoryGroups | null = isWrapped<ChatSessionHistoryGroups>(payload)
      ? (payload.data ?? null)
      : (payload as ChatSessionHistoryGroups)
    const list: SessionItem[] = []
    if (map && typeof map === 'object' && !Array.isArray(map)) {
      Object.keys(map).forEach((key) => {
        const bucket: ChatSessionSummary[] = map[key] ?? []
        bucket.forEach((session) => {
          list.push({
            sessionId: session.sessionId,
            title: session.title || '未命名会话',
            updateTime: session.updateTime || '',
            messageCount: null,
            live: true,
          })
        })
      })
    }
    if (list.length) {
      sessions.value = list
      sessionDemo.value = false
    } else {
      sessions.value = demoSessionGroups()
      sessionDemo.value = true
    }
  } catch {
    sessions.value = demoSessionGroups()
    sessionDemo.value = true
  }
  sessionLoading.value = false
  if (!sessionDemo.value) void loadMessageCounts()
}

async function loadMessageCounts() {
  const targets = sessions.value.slice(0, COUNT_PROBE_LIMIT)
  const results = await Promise.allSettled(
    targets.map(async (item) => {
      const payload = await getSessionHistory(item.sessionId)
      const history = unwrap<ChatHistoryMessage[]>(payload)
      return { id: item.sessionId, count: Array.isArray(history) ? history.length : 0 }
    })
  )
  results.forEach((result) => {
    if (result.status === 'fulfilled') {
      const target = sessions.value.find((item) => item.sessionId === result.value.id)
      if (target) target.messageCount = result.value.count
    }
  })
}

async function loadTemplates() {
  templateLoading.value = true
  try {
    const payload = await getChatTemplates()
    const map = unwrap<PromptTemplateMap>(payload)
    const cards = TEMPLATE_META.map((meta) => ({
      key: meta.key,
      label: meta.label,
      desc: meta.desc,
      title: meta.label,
      content: (map?.[meta.key] as string | undefined) || '',
    })).filter((card) => card.content)
    if (cards.length) {
      templates.value = cards
      templateDemo.value = false
    } else {
      templates.value = buildDemoTemplates()
      templateDemo.value = true
    }
  } catch {
    templates.value = buildDemoTemplates()
    templateDemo.value = true
  }
  templateLoading.value = false
}

function buildDemoTemplates(): TemplateCard[] {
  return TEMPLATE_META.map((meta) => ({
    key: meta.key,
    label: meta.label,
    desc: meta.desc,
    title: meta.label,
    content: DEMO_TEMPLATES[meta.key],
  }))
}

async function openThread(item: SessionItem) {
  activeSession.value = item
  draftTitle.value = item.title
  thread.value = []
  threadError.value = false
  threadDemo.value = false
  drawerOpen.value = true
  threadLoading.value = true

  if (!item.live) {
    thread.value = demoThread()
    threadDemo.value = true
    threadLoading.value = false
    return
  }

  try {
    const payload = await getSessionHistory(item.sessionId)
    const history = unwrap<ChatHistoryMessage[]>(payload)
    thread.value = Array.isArray(history) ? history : []
  } catch {
    threadError.value = true
  }
  threadLoading.value = false
}

async function reloadThread() {
  if (!activeSession.value) return
  const current = activeSession.value
  await openThread(current)
}

async function renameSession(item: SessionItem) {
  let next = ''
  try {
    const result = await ElMessageBox.prompt('输入新的会话标题', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: item.title,
      inputPlaceholder: '会话标题',
      inputValidator: (value: string) => (value && value.trim() ? true : '标题不能为空'),
    })
    next = result.value.trim()
  } catch {
    return
  }
  if (!next || next === item.title) return
  const previous = item.title
  item.title = next
  if (activeSession.value?.sessionId === item.sessionId) draftTitle.value = next
  try {
    await updateSessionTitle(item.sessionId, next)
    ElMessage.success('会话标题已更新')
  } catch (error) {
    item.title = previous
    if (activeSession.value?.sessionId === item.sessionId) draftTitle.value = previous
    ElMessage.error(`重命名失败，标题已回滚：${reason(error)}`)
  }
}

async function saveDraftTitle() {
  const item = activeSession.value
  if (!item) return
  const next = draftTitle.value.trim()
  if (!next) {
    ElMessage.warning('标题不能为空')
    return
  }
  if (next === item.title) return
  savingTitle.value = true
  const previous = item.title
  item.title = next
  try {
    await updateSessionTitle(item.sessionId, next)
    ElMessage.success('会话标题已更新')
  } catch (error) {
    item.title = previous
    draftTitle.value = previous
    ElMessage.error(`重命名失败，标题已回滚：${reason(error)}`)
  }
  savingTitle.value = false
}

async function removeSession(item: SessionItem) {
  try {
    await ElMessageBox.confirm(`确认删除会话「${item.title}」？该操作不可撤销。`, '删除确认', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteSessionHistory(item.sessionId)
    sessions.value = sessions.value.filter((session) => session.sessionId !== item.sessionId)
    if (activeSession.value?.sessionId === item.sessionId) drawerOpen.value = false
    ElMessage.success('会话已删除')
  } catch (error) {
    ElMessage.error(`删除失败：${reason(error)}`)
  }
}

async function copyTemplate(card: TemplateCard) {
  const ok = await writeClipboard(card.content)
  if (ok) {
    copiedKey.value = card.key
    ElMessage.success(`已复制「${card.title}」模板`)
    window.setTimeout(() => {
      if (copiedKey.value === card.key) copiedKey.value = ''
    }, 1800)
  } else {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

async function writeClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 回退到 execCommand
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', 'readonly')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}

function refreshActive() {
  if (tab.value === 'sessions') {
    void loadSessions()
  } else {
    void loadTemplates()
  }
}

onMounted(() => {
  void loadSessions()
  void loadTemplates()
})
</script>

<template>
  <div class="ai">
    <header class="page-head">
      <div class="page-head__copy">
        <span class="nm-kicker">AI 管理</span>
        <h1 class="nm-h1">智能助手运营</h1>
        <p class="nm-lede">查看学员会话记录，维护提示词模板。会话操作会调用智能服务接口。</p>
      </div>
      <div class="page-head__acts">
        <button type="button" class="nm-btn nm-btn--sm" @click="refreshActive">
          <el-icon :size="14"><Refresh /></el-icon>
          <span>刷新</span>
        </button>
      </div>
    </header>

    <div class="tabs" role="tablist">
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'sessions'"
        :class="['nm-chip', { 'is-active': tab === 'sessions' }]"
        @click="tab = 'sessions'"
      >
        <el-icon :size="14"><ChatDotRound /></el-icon>
        <span>会话记录</span>
        <span v-if="!sessionLoading" class="nm-num tab-count">{{ sessions.length }}</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'templates'"
        :class="['nm-chip', { 'is-active': tab === 'templates' }]"
        @click="tab = 'templates'"
      >
        <el-icon :size="14"><MagicStick /></el-icon>
        <span>提示词模板</span>
        <span v-if="!templateLoading" class="nm-num tab-count">{{ templates.length }}</span>
      </button>
    </div>

    <!-- 会话记录 -->
    <template v-if="tab === 'sessions'">
      <div v-if="sessionDemo" class="notice" role="status">
        <el-icon :size="16"><Warning /></el-icon>
        <p>
          智能服务不可达（<span class="nm-num">GET /ais/session/history</span> 请求失败），以下会话为
          <strong>演示数据</strong>，仅用于展示界面结构。
        </p>
        <button type="button" class="nm-btn nm-btn--sm" @click="loadSessions">重试</button>
      </div>

      <section class="nm-card panel">
        <div class="toolbar">
          <el-input
            v-model="sessionKeyword"
            class="toolbar__search"
            placeholder="搜索会话标题"
            clearable
            :prefix-icon="Search"
          />
          <div class="toolbar__spacer"></div>
          <p class="toolbar__meta nm-muted">
            会话 <span class="nm-num">{{ sessions.length }}</span> 条 · 已统计消息
            <span class="nm-num">{{ messageTotal }}</span> 条
          </p>
        </div>

        <div v-if="sessionLoading" class="rows">
          <div v-for="n in 5" :key="`session-skeleton-${n}`" class="row-skeleton">
            <div class="nm-skeleton" style="width: 34px; height: 34px; border-radius: 999px"></div>
            <div class="nm-skeleton" style="height: 12px; flex: 1"></div>
            <div class="nm-skeleton" style="height: 12px; width: 60px"></div>
          </div>
        </div>

        <div v-else-if="!groups.length" class="nm-state">
          <span class="nm-state__icon"><el-icon :size="24"><ChatDotRound /></el-icon></span>
          <p class="nm-state__title">{{ sessions.length ? '没有匹配的会话' : '暂无会话记录' }}</p>
          <p class="nm-state__desc">
            {{ sessions.length ? '换个关键词再试一次。' : '智能服务暂未返回会话数据，可重试拉取。' }}
          </p>
          <div class="nm-state__actions">
            <button v-if="sessions.length" type="button" class="nm-btn nm-btn--sm" @click="sessionKeyword = ''">
              清空搜索
            </button>
            <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="loadSessions">重新加载</button>
          </div>
        </div>

        <div v-else class="groups">
          <div v-for="group in groups" :key="group.key" class="group">
            <header class="group__head">
              <span class="nm-kicker nm-kicker--plain">{{ group.label }}</span>
              <span class="group__line"></span>
              <span class="nm-num group__count">{{ group.items.length }} 条</span>
            </header>

            <ul class="group__list">
              <li v-for="item in group.items" :key="item.sessionId" class="session">
                <button type="button" class="session__main" @click="openThread(item)">
                  <span class="session__ico" aria-hidden="true">
                    <el-icon :size="15"><ChatDotRound /></el-icon>
                  </span>
                  <span class="session__copy">
                    <span class="session__title">{{ item.title }}</span>
                    <span class="session__meta nm-muted">
                      <span class="nm-num">{{ formatTime(item.updateTime) }}</span>
                      <span aria-hidden="true">·</span>
                      <span class="nm-num">
                        {{ item.messageCount === null ? '消息数待统计' : `${item.messageCount} 条消息` }}
                      </span>
                      <span v-if="item.live" class="nm-badge nm-badge--success">实时</span>
                      <span v-else class="nm-badge nm-badge--neutral">样例</span>
                    </span>
                  </span>
                </button>
                <div class="session__acts">
                  <button type="button" class="link-btn" @click="renameSession(item)">
                    <el-icon :size="13"><EditPen /></el-icon>
                    <span>重命名</span>
                  </button>
                  <button type="button" class="link-btn link-btn--danger" @click="removeSession(item)">
                    <el-icon :size="13"><Delete /></el-icon>
                    <span>删除</span>
                  </button>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </section>
    </template>

    <!-- 提示词模板 -->
    <template v-else>
      <div v-if="templateDemo" class="notice" role="status">
        <el-icon :size="16"><Warning /></el-icon>
        <p>
          模板接口不可达（<span class="nm-num">GET /ais/chat/templates</span> 请求失败），以下模板为
          <strong>演示内容</strong>。
        </p>
        <button type="button" class="nm-btn nm-btn--sm" @click="loadTemplates">重试</button>
      </div>

      <div v-if="templateLoading" class="nm-auto-grid">
        <div v-for="n in 5" :key="`tpl-skeleton-${n}`" class="nm-card nm-card--pad">
          <div class="nm-skeleton" style="height: 14px; width: 40%"></div>
          <div class="nm-skeleton" style="height: 60px; margin-top: 14px"></div>
        </div>
      </div>

      <div v-else-if="!templates.length" class="nm-state">
        <span class="nm-state__icon"><el-icon :size="24"><Document /></el-icon></span>
        <p class="nm-state__title">暂无提示词模板</p>
        <p class="nm-state__desc">模板接口未返回内容，可重试拉取。</p>
        <div class="nm-state__actions">
          <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="loadTemplates">重新加载</button>
        </div>
      </div>

      <div v-else class="nm-auto-grid">
        <article v-for="card in templates" :key="card.key" class="nm-card nm-card--pad nm-card--hover tpl">
          <header class="tpl__head">
            <span class="nm-badge nm-badge--accent">{{ card.label }}</span>
            <span class="nm-num tpl__key">{{ card.key }}</span>
          </header>

          <label class="tpl__field">
            <span class="tpl__label">模板标题（仅本地编辑）</span>
            <el-input v-model="card.title" maxlength="30" />
          </label>

          <p class="nm-muted tpl__desc">{{ card.desc }}</p>

          <pre class="tpl__body">{{ card.content }}</pre>

          <footer class="tpl__foot">
            <button type="button" class="nm-btn nm-btn--sm" @click="copyTemplate(card)">
              <el-icon :size="13">
                <Check v-if="copiedKey === card.key" />
                <CopyDocument v-else />
              </el-icon>
              <span>{{ copiedKey === card.key ? '已复制' : '复制提示词' }}</span>
            </button>
          </footer>
        </article>
      </div>
    </template>

    <!-- 会话详情 -->
    <el-drawer v-model="drawerOpen" size="min(520px, 92vw)" direction="rtl">
      <template #header>
        <div class="drawer-head">
          <span class="nm-kicker nm-kicker--plain">会话详情</span>
          <strong class="drawer-head__title">{{ activeSession?.title || '未命名会话' }}</strong>
        </div>
      </template>

      <div v-if="activeSession" class="thread">
        <div class="thread__bar">
          <el-input v-model="draftTitle" class="thread__input" maxlength="40" placeholder="会话标题" />
          <button
            type="button"
            class="nm-btn nm-btn--primary nm-btn--sm"
            :disabled="savingTitle"
            @click="saveDraftTitle"
          >
            保存标题
          </button>
        </div>

        <p class="nm-muted thread__meta">
          <span class="nm-num">{{ activeSession.sessionId }}</span>
          <span aria-hidden="true">·</span>
          <span class="nm-num">{{ activeSession.updateTime || '无更新时间' }}</span>
          <span aria-hidden="true">·</span>
          <span class="nm-num">{{ threadCount }} 条消息</span>
        </p>

        <div v-if="threadDemo" class="notice notice--inline" role="status">
          <el-icon :size="15"><Warning /></el-icon>
          <p>这是演示会话，对话内容为样例数据，用于展示消息线程版式。</p>
        </div>

        <div v-if="threadLoading" class="thread__rows">
          <div v-for="n in 4" :key="`thread-skeleton-${n}`" class="nm-skeleton" style="height: 58px"></div>
        </div>

        <div v-else-if="threadError" class="nm-state">
          <span class="nm-state__icon"><el-icon :size="24"><Warning /></el-icon></span>
          <p class="nm-state__title">消息加载失败</p>
          <p class="nm-state__desc">会话详情请求失败（<span class="nm-num">GET /ais/session/{{ activeSession?.sessionId }}</span>）。</p>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary nm-btn--sm" @click="reloadThread">重试</button>
          </div>
        </div>

        <div v-else-if="!thread.length" class="nm-state">
          <span class="nm-state__icon"><el-icon :size="24"><ChatDotRound /></el-icon></span>
          <p class="nm-state__title">该会话暂无消息</p>
          <p class="nm-state__desc">接口返回了空的消息列表，可能是会话刚创建或已被清理。</p>
        </div>

        <ol v-else class="thread__list">
          <li
            v-for="(message, index) in thread"
            :key="`msg-${index}`"
            :class="['bubble', isUserMessage(message) ? 'bubble--user' : 'bubble--ai']"
          >
            <span class="bubble__who">
              <el-icon :size="12">
                <User v-if="isUserMessage(message)" />
                <MagicStick v-else />
              </el-icon>
              <span>{{ isUserMessage(message) ? '学员' : '助手' }}</span>
            </span>
            <p class="bubble__body">{{ message.content }}</p>
          </li>
        </ol>

        <div class="thread__acts">
          <button type="button" class="nm-btn nm-btn--sm" @click="renameSession(activeSession)">重命名</button>
          <button type="button" class="nm-btn nm-btn--sm" @click="removeSession(activeSession)">删除会话</button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.ai {
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

/* ---------------- 分段标签 ---------------- */
.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tab-count {
  font-size: 11px;
  opacity: 0.7;
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

/* ---------------- 面板与工具栏 ---------------- */
.panel {
  padding: clamp(16px, 2vw, 22px);
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.toolbar__spacer {
  flex: 1;
  min-width: 0;
}

.toolbar__meta {
  font-size: var(--nm-fs-sm);
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

/* ---------------- 会话分组 ---------------- */
.groups {
  display: grid;
  gap: 22px;
}

.group {
  display: grid;
  gap: 8px;
}

.group__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group__line {
  flex: 1;
  height: 1px;
  background: var(--nm-line);
}

.group__count {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.group__list {
  display: grid;
  gap: 6px;
  list-style: none;
}

.session {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px 6px 6px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
  transition: border-color var(--nm-dur-fast) var(--nm-ease),
    background-color var(--nm-dur-fast) var(--nm-ease);
}

.session:hover {
  border-color: var(--nm-line-strong);
  background: var(--nm-surface);
}

.session__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 6px;
  border: 0;
  border-radius: var(--nm-r-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.session__ico {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-sm);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.session__copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.session__title {
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  color: var(--nm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--nm-fs-xs);
  flex-wrap: wrap;
}

.session__acts {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
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

/* ---------------- 模板卡 ---------------- */
.tpl {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tpl__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tpl__key {
  font-size: 10px;
  color: var(--nm-ink-4);
}

.tpl__field {
  display: grid;
  gap: 5px;
}

.tpl__label {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.tpl__desc {
  font-size: var(--nm-fs-sm);
}

.tpl__body {
  flex: 1;
  padding: 12px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  line-height: 1.75;
  color: var(--nm-ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 190px;
  overflow-y: auto;
}

.tpl__foot {
  display: flex;
  justify-content: flex-end;
}

/* ---------------- 抽屉与消息线程 ---------------- */
.drawer-head {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.drawer-head__title {
  font-size: var(--nm-fs-body);
  font-weight: 700;
  color: var(--nm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.thread__bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.thread__input {
  flex: 1;
  min-width: 0;
}

.thread__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--nm-fs-xs);
  flex-wrap: wrap;
}

.thread__rows {
  display: grid;
  gap: 10px;
}

.thread__list {
  display: grid;
  gap: 10px;
  list-style: none;
}

.bubble {
  display: grid;
  gap: 5px;
  padding: 11px 13px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
}

.bubble--user {
  background: var(--nm-accent-soft);
  border-color: var(--nm-accent-line);
}

.bubble__who {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--nm-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
}

.bubble--user .bubble__who {
  color: var(--nm-accent);
}

.bubble__body {
  font-size: var(--nm-fs-sm);
  line-height: 1.7;
  color: var(--nm-ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.thread__acts {
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

  .toolbar__search {
    width: 100%;
  }

  .session {
    flex-wrap: wrap;
  }

  .thread__bar {
    flex-wrap: wrap;
  }
}
</style>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import gsap from 'gsap'
import MarkdownRenderer from '@/components/chat/MarkdownRenderer.vue'
import ToolResultCard from '@/components/chat/ToolResultCard.vue'
import sceneEmptyChat from '@/assets/scenes/scene-empty-chat.svg?raw'
import {
  ArrowRight,
  ChatLineRound,
  CircleCheck,
  CopyDocument,
  Cpu,
  Delete,
  EditPen,
  List,
  MagicStick,
  Plus,
  RefreshRight,
  Search,
  Service,
  Timer,
  Loading as LoadingIcon,
  Tools,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSession,
  deleteSessionHistory,
  getAllSessions,
  getChatTemplates,
  getHotSessions,
  getSessionHistory,
  stopChat,
  updateSessionTitle,
} from '@/api/aigc'
import { consolePrompts } from '@/content/novamind'
import { useRevealObserver } from '@/composables/useRevealObserver'
import type {
  AssistantSessionBootstrap,
  ChatHistoryMessage,
  ChatSessionHistoryGroups,
  ChatSessionSummary,
  PromptTemplateMap,
  SessionExample,
} from '@/types/aigc'

interface ReactStep {
  id: string
  type: 'thought' | 'action' | 'observation' | 'metrics'
  title: string
  content: string
  timestamp: number
  expanded: boolean
  icon: Component
}

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reactSteps: ReactStep[]
  metrics?: {
    tokensUsed: number
    latency: number
    toolsUsed: string[]
  }
  timestamp: number
}

interface TemplateDeckItem {
  key: keyof PromptTemplateMap
  title: string
  label: string
  desc: string
  content: string
}

interface StreamEvent {
  eventType?: number
  eventName?: string
  eventData?: unknown
  content?: string
  metadata?: Record<string, unknown>
}

const templateBlueprints: Array<Omit<TemplateDeckItem, 'content'>> = [
  {
    key: 'associationalWord',
    title: '知识点联想',
    label: '关联拓展',
    desc: '围绕当前学习主题，自动生成相关知识点和延伸方向。',
  },
  {
    key: 'helpedWrite',
    title: '学习摘要',
    label: '内容提炼',
    desc: '将课程内容或学习笔记整理成清晰的结构化摘要。',
  },
  {
    key: 'continuedWrite',
    title: '深入追问',
    label: '进阶学习',
    desc: '基于已有知识自动生成进阶问题，推动深度理解。',
  },
  {
    key: 'polish',
    title: '作业批改',
    label: '智能审校',
    desc: '提交你的作业或代码，AI 会给出优化建议和改进方向。',
  },
  {
    key: 'streamline',
    title: '重点提炼',
    label: '高效复习',
    desc: '将长篇教材或视频文字稿压缩成重点明确的复习笔记。',
  },
]

const defaultSessionDescription =
  '你可以在这里提问、复用历史会话或使用模板快速开始学习。'

const route = useRoute()
const messages = ref<ChatMessage[]>([])
const input = ref('')
const loading = ref(false)
const creatingSession = ref(false)
const bootLoading = ref(true)
const historyLoading = ref(false)
const sessionGroups = ref<ChatSessionHistoryGroups>({})
const hotExamples = ref<SessionExample[]>([])
const templates = ref<PromptTemplateMap | null>(null)
const bootstrap = ref<AssistantSessionBootstrap | null>(null)
const currentSessionId = ref('')
const currentSessionTitle = ref('新对话')
const currentSessionDescription = ref(defaultSessionDescription)
const messageContainer = ref<HTMLElement | null>(null)
const showReactPanel = ref(true)
const animatedMsgIds = ref(new Set<string>())
const currentReactSteps = ref<ReactStep[]>([])
const isStreaming = ref(false)
const lastAutostartKey = ref('')
const streamMetrics = reactive({
  tokensUsed: 0,
  startTime: 0,
  toolsUsed: [] as string[],
})

const currentPrompt = computed(() => String(route.query.prompt || ''))
const historySections = computed(() =>
  Object.entries(sessionGroups.value)
    .map(([label, sessions]) => ({
      label,
      sessions: sessions || [],
    }))
    .filter((section) => section.sessions.length > 0)
)
const templateCards = computed<TemplateDeckItem[]>(() =>
  templateBlueprints
    .map((item) => ({
      ...item,
      content: templates.value?.[item.key] || '',
    }))
    .filter((item) => item.content)
)
const activeExamples = computed(() => bootstrap.value?.examples?.length ? bootstrap.value.examples : hotExamples.value)
const sessionCount = computed(() =>
  historySections.value.reduce((sum, section) => sum + section.sessions.length, 0)
)

const latestAssistantMessage = computed(() =>
  [...messages.value].reverse().find((message) => message.role === 'assistant')
)
const isLastAssistantStreaming = computed(() => {
  if (!isStreaming.value || messages.value.length === 0) return false
  const last = messages.value[messages.value.length - 1]
  return last.role === 'assistant' && loading.value
})
const panelSteps = computed(() =>
  isStreaming.value
    ? currentReactSteps.value
    : latestAssistantMessage.value?.reactSteps || []
)
const panelTodoItems = computed(() => {
  const steps = isStreaming.value
    ? currentReactSteps.value
    : latestAssistantMessage.value?.reactSteps || []
  return steps.map((step, index) => {
    let title = step.title
    if (step.type === 'thought') title = `分析：${step.content.slice(0, 32)}${step.content.length > 32 ? '...' : ''}`
    else if (step.type === 'action') title = `执行：${step.title}`
    else if (step.type === 'observation') title = `观察：${step.content.slice(0, 32)}${step.content.length > 32 ? '...' : ''}`
    return {
      id: `${step.id}-${index}`,
      title,
      done: !isStreaming.value || index < steps.length - 1,
    }
  })
})
const panelTokenUsage = computed(() => {
  const used = Math.max(
    streamMetrics.tokensUsed,
    latestAssistantMessage.value?.metrics?.tokensUsed || 0
  )
  const capacity = 16000
  const percentage = Math.min(100, Math.max(1, Math.round((used / capacity) * 100)))
  const webSearch = Math.round(percentage * 0.34)
  return {
    percentage,
    webSearch,
    other: Math.max(0, percentage - webSearch),
  }
})

useRevealObserver('.ai-chat-page .reveal', { once: false, threshold: 0.16 })

watch(
  () => [route.query.prompt, route.query.autostart] as const,
  async ([promptValue, autostart]) => {
    const prompt = typeof promptValue === 'string' ? promptValue.trim() : ''
    if (!prompt) {
      return
    }

    input.value = prompt
    await nextTick()

    if (autostart === '1') {
      const key = `${route.fullPath}|${prompt}`
      if (lastAutostartKey.value === key) {
        return
      }
      lastAutostartKey.value = key
      startDraftSession(false)
      await sendMessage()
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await loadConsoleData()
})

async function loadConsoleData() {
  bootLoading.value = true

  const [templateResult, hotResult, historyResult] = await Promise.allSettled([
    getChatTemplates(),
    getHotSessions(6),
    getAllSessions(),
  ])

  if (templateResult.status === 'fulfilled') {
    templates.value = templateResult.value
  }

  if (hotResult.status === 'fulfilled') {
    hotExamples.value = hotResult.value || []
  }

  if (historyResult.status === 'fulfilled') {
    sessionGroups.value = historyResult.value || {}
  }

  const shouldAutostart =
    route.query.autostart === '1' &&
    typeof route.query.prompt === 'string' &&
    route.query.prompt.trim().length > 0

  if (!shouldAutostart && !currentSessionId.value) {
    const firstSession = historySections.value[0]?.sessions[0]
    if (firstSession) {
      await switchSession(firstSession)
    }
  }

  bootLoading.value = false
}

function startDraftSession(clearInput = true) {
  currentSessionId.value = ''
  currentSessionTitle.value = '新对话'
  currentSessionDescription.value = defaultSessionDescription
  bootstrap.value = null
  messages.value = []
  currentReactSteps.value = []
  if (clearInput) {
    input.value = ''
  }
}

async function createFreshSession(refreshList = true) {
  creatingSession.value = true

  try {
    const session = await createSession(4)
    currentSessionId.value = session.sessionId
    currentSessionTitle.value = session.title || '新对话'
    currentSessionDescription.value = session.describe || defaultSessionDescription
    bootstrap.value = session
    messages.value = []

    if (refreshList) {
      await refreshSessionGroups(session.sessionId)
    }

    return session.sessionId
  } finally {
    creatingSession.value = false
  }
}

async function ensureSession() {
  if (currentSessionId.value) {
    return currentSessionId.value
  }

  return createFreshSession(false)
}

async function refreshSessionGroups(focusSessionId?: string) {
  const groups = await getAllSessions()
  sessionGroups.value = groups || {}

  if (focusSessionId) {
    const target = findSessionById(focusSessionId)
    if (target) {
      currentSessionTitle.value = target.title || currentSessionTitle.value
    }
  }

  if (currentSessionId.value && !findSessionById(currentSessionId.value)) {
    startDraftSession(false)
  }
}

function findSessionById(sessionId: string) {
  return historySections.value
    .flatMap((section) => section.sessions)
    .find((session) => session.sessionId === sessionId)
}

function buildMessageParamsSteps(params?: Record<string, unknown>) {
  if (!params) {
    return []
  }

  return Object.entries(params).map(([key, value]) => ({
    id: makeId(`param-${key}`),
    type: 'observation' as const,
    title: `参数 · ${key}`,
    content: toDisplayText(value),
    timestamp: Date.now(),
    expanded: false,
    icon: View,
  }))
}

function mapHistoryMessage(message: ChatHistoryMessage, index: number): ChatMessage {
  return {
    id: makeId(`history-${index}`),
    role: resolveRole(message.type),
    content: message.content || '',
    reactSteps: buildMessageParamsSteps(message.params),
    timestamp: Date.now() + index,
  }
}

function resolveRole(type: ChatHistoryMessage['type']) {
  const value = String(type).toUpperCase()
  if (value === 'USER' || value === '1') {
    return 'user' as const
  }
  return 'assistant' as const
}

async function newChat() {
  if (loading.value || creatingSession.value) {
    return
  }

  startDraftSession()
  await createFreshSession(true)
  await nextTick()
  scrollToBottom()
}

async function switchSession(session: ChatSessionSummary) {
  if (!session.sessionId) {
    return
  }

  currentSessionId.value = session.sessionId
  currentSessionTitle.value = session.title || '历史会话'
  currentSessionDescription.value = '已加载历史对话，可以继续围绕这个话题深入探讨。'
  bootstrap.value = null
  historyLoading.value = true

  try {
    const history = await getSessionHistory(session.sessionId)
    messages.value = history.map(mapHistoryMessage)
  } catch {
    messages.value = []
    ElMessage.error('对话记录加载失败')
  } finally {
    historyLoading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function renameSession(session?: ChatSessionSummary) {
  const targetId = session?.sessionId || currentSessionId.value
  const targetTitle = session?.title || currentSessionTitle.value

  if (!targetId) {
    return
  }

  try {
    const promptResult = await ElMessageBox.prompt('给对话起个标题方便回顾', '重命名对话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: targetTitle,
      inputPattern: /\S+/,
      inputErrorMessage: '标题不能为空',
    })

    const nextTitle = promptResult.value.trim()
    await updateSessionTitle(targetId, nextTitle)

    if (targetId === currentSessionId.value) {
      currentSessionTitle.value = nextTitle
    }

    await refreshSessionGroups(targetId)
    ElMessage.success('标题已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('重命名失败')
    }
  }
}

async function removeSession(session?: ChatSessionSummary) {
  const targetId = session?.sessionId || currentSessionId.value
  if (!targetId) {
    return
  }

  try {
    await ElMessageBox.confirm('删除后将无法恢复这条对话记录。', '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await deleteSessionHistory(targetId)
    await refreshSessionGroups()

    if (targetId === currentSessionId.value) {
      const nextSession = historySections.value[0]?.sessions[0]
      if (nextSession) {
        await switchSession(nextSession)
      } else {
        startDraftSession()
      }
    }

    ElMessage.success('对话已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除失败')
    }
  }
}

async function launchPrompt(prompt: string) {
  input.value = prompt
  await nextTick()
  await sendMessage()
}

async function launchExample(example: SessionExample) {
  const prompt = example.describe?.trim() || example.title
  await launchPrompt(prompt)
}

function applyTemplate(template: TemplateDeckItem) {
  const seed = input.value.trim() || currentPrompt.value || '请在这里输入你的主题或问题'
  input.value = template.content.includes('$input')
    ? template.content.replace(/\$input/g, seed)
    : `${template.content}\n${seed}`
}

async function stopStreamingMission() {
  if (!currentSessionId.value || !isStreaming.value) {
    return
  }

  try {
    await stopChat(currentSessionId.value)
    ElMessage.success('已停止生成')
  } catch {
    ElMessage.error('停止失败，请稍后重试')
  }
}

async function sendMessage() {
  if (!input.value.trim() || loading.value) {
    return
  }

  const question = input.value.trim()
  input.value = ''

  const userMessage: ChatMessage = {
    id: makeId('user'),
    role: 'user',
    content: question,
    reactSteps: [],
    timestamp: Date.now(),
  }

  messages.value.push(userMessage)
  loading.value = true
  isStreaming.value = true
  currentReactSteps.value = []
  streamMetrics.tokensUsed = 0
  streamMetrics.startTime = Date.now()
  streamMetrics.toolsUsed = []

  const assistantMessage: ChatMessage = {
    id: makeId('assistant'),
    role: 'assistant',
    content: '',
    reactSteps: [],
    timestamp: Date.now(),
  }
  messages.value.push(assistantMessage)

  await nextTick()
  scrollToBottom()

  try {
    const sessionId = await ensureSession()
    const response = await fetch('/api/ais/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: localStorage.getItem('nova_token') || '',
      },
      body: JSON.stringify({
        question,
        sessionId,
      }),
    })

    if (!response.ok || !response.body) {
      throw new Error('AI service is unavailable')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentStep: ReactStep | null = null

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data:')) {
          continue
        }

        const payload = line.slice(5).trim()
        if (!payload || payload === '[DONE]') {
          continue
        }

        const parsed = safeParseJson(payload) as StreamEvent | null
        if (!parsed) {
          assistantMessage.content += payload
          continue
        }

        const finalizeStep = () => {
          if (currentStep) {
            assistantMessage.reactSteps.push(currentStep)
            currentStep = null
          }
        }

        switch (parsed.eventType) {
          case 1101:
            finalizeStep()
            currentStep = createReactStep(
              'thought',
              parsed.eventName || 'AI 思考中',
              toDisplayText(parsed.eventData),
              Cpu,
              true
            )
            currentReactSteps.value = [...assistantMessage.reactSteps, currentStep]
            break
          case 1102: {
            finalizeStep()
            const actionPayload = toRecord(parsed.eventData)
            currentStep = createReactStep(
              'action',
              parsed.eventName || String(actionPayload.tool || '执行工具'),
              String(actionPayload.input || toDisplayText(parsed.eventData)),
              Tools,
              true
            )
            streamMetrics.toolsUsed.push(currentStep.title)
            currentReactSteps.value = [...assistantMessage.reactSteps, currentStep]
            break
          }
          case 1103:
          case 1105:
          case 1106:
            finalizeStep()
            currentStep = createReactStep(
              'observation',
              parsed.eventName || '观察结果',
              toDisplayText(parsed.eventData),
              View,
              parsed.eventType === 1103
            )
            currentReactSteps.value = [...assistantMessage.reactSteps, currentStep]
            break
          case 1104: {
            const metricPayload = toRecord(parsed.eventData)
            streamMetrics.tokensUsed =
              Number(metricPayload.tokens || metricPayload.tokenUsage || streamMetrics.tokensUsed) ||
              streamMetrics.tokensUsed
            if (Array.isArray(metricPayload.tools)) {
              metricPayload.tools.forEach((tool) => streamMetrics.toolsUsed.push(String(tool)))
            }
            break
          }
          case 1001: {
            const text =
              typeof parsed.eventData === 'string'
                ? parsed.eventData
                : typeof parsed.content === 'string'
                  ? parsed.content
                  : ''
            if (text) {
              assistantMessage.content += text
              streamMetrics.tokensUsed += text.length
            }
            break
          }
          case 1002:
            finalizeStep()
            currentReactSteps.value = [...assistantMessage.reactSteps]
            break
          case 1003: {
            finalizeStep()
            const paramPayload = toRecord(parsed.eventData)
            Object.entries(paramPayload).forEach(([key, value]) => {
              assistantMessage.reactSteps.push(
                createReactStep('observation', `参数 · ${key}`, toDisplayText(value), View, false)
              )
            })
            currentReactSteps.value = [...assistantMessage.reactSteps]
            break
          }
          case 1500:
            finalizeStep()
            assistantMessage.reactSteps.push(
              createReactStep('observation', parsed.eventName || '服务反馈', toDisplayText(parsed.eventData), View, true)
            )
            currentReactSteps.value = [...assistantMessage.reactSteps]
            break
          default:
            break
        }

        await nextTick()
        scrollToBottom()
      }
    }

    if (currentStep) {
      assistantMessage.reactSteps.push(currentStep)
    }

    assistantMessage.metrics = {
      tokensUsed: streamMetrics.tokensUsed,
      latency: Date.now() - streamMetrics.startTime,
      toolsUsed: [...new Set(streamMetrics.toolsUsed)],
    }

    if (!assistantMessage.content.trim()) {
      assistantMessage.content = assistantMessage.reactSteps.length
        ? '本轮主要结果已经沉淀在上方 ReAct 轨迹里，你可以继续追问让它收敛成更明确的最终答案。'
        : 'AI 服务没有返回可展示文本，请确认后端服务是否正常运行。'
    }
  } catch {
    assistantMessage.content =
      'NovaMind 暂时没有从 AI 服务收到完整响应。请确认网关和 `novamind-aigc` 服务已经启动，然后再重试。'
    ElMessage.error('AI 对话暂时不可用')
  } finally {
    loading.value = false
    isStreaming.value = false
    currentReactSteps.value = []
    if (currentSessionId.value) {
      await refreshSessionGroups(currentSessionId.value)
    }
    await nextTick()
    scrollToBottom()
  }
}

function toggleStep(step: ReactStep) {
  step.expanded = !step.expanded
}

function copyContent(content: string) {
  if (!content || !navigator.clipboard) {
    return
  }
  navigator.clipboard.writeText(content)
  ElMessage.success('已复制到剪贴板')
}

async function regenerateFrom(index: number) {
  const previousUser = [...messages.value.slice(0, index)].reverse().find((message) => message.role === 'user')
  if (!previousUser) {
    return
  }
  input.value = previousUser.content
  await nextTick()
  await sendMessage()
}

function createReactStep(
  type: ReactStep['type'],
  title: string,
  content: string,
  icon: Component,
  expanded: boolean
): ReactStep {
  return {
    id: makeId(type),
    type,
    title,
    content,
    timestamp: Date.now(),
    expanded,
    icon,
  }
}

/* ReAct 步骤取色：统一走设计系统语义令牌，随主题自动翻转 */
function getStepColor(type: ReactStep['type']) {
  switch (type) {
    case 'thought':
      return 'var(--nm-cyan)'
    case 'action':
      return 'var(--nm-accent)'
    case 'observation':
      return 'var(--nm-accent)'
    case 'metrics':
      return 'var(--nm-ink-3)'
    default:
      return 'var(--nm-cyan)'
  }
}

function getStepBg(type: ReactStep['type']) {
  switch (type) {
    case 'thought':
      return 'var(--nm-cyan-soft)'
    case 'action':
      return 'var(--nm-accent-soft)'
    case 'observation':
      return 'var(--nm-accent-soft)'
    case 'metrics':
      return 'var(--nm-surface-3)'
    default:
      return 'var(--nm-cyan-soft)'
  }
}

function formatStepDuration(step: ReactStep, index: number, steps: ReactStep[]) {
  const next = steps[index + 1]
  const ms = Math.max(3000, (next?.timestamp || Date.now()) - step.timestamp)
  const minute = Math.floor(ms / 60000)
  const second = Math.floor((ms % 60000) / 1000)
  return minute > 0 ? `${minute}m ${second}s` : `${second}s`
}

function formatSessionTime(value = '') {
  if (!value) {
    return '刚刚更新'
  }
  return value.replace('T', ' ').slice(5, 16)
}

function isObservationResult(step: ReactStep): boolean {
  return step.type === 'observation' && safeParseJson(step.content) !== null
}

function toDisplayText(payload: unknown) {
  if (payload === null || payload === undefined) {
    return ''
  }

  if (typeof payload === 'string') {
    const parsed = safeParseJson(payload)
    if (parsed) {
      return JSON.stringify(parsed, null, 2)
    }
    return payload
  }

  return JSON.stringify(payload, null, 2)
}

function toRecord(payload: unknown) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    return payload as Record<string, unknown>
  }

  if (typeof payload === 'string') {
    const parsed = safeParseJson(payload)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
  }

  return {}
}

function safeParseJson(payload: string) {
  try {
    return JSON.parse(payload)
  } catch {
    return null
  }
}

function makeId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function scrollToBottom() {
  if (messageContainer.value) {
    messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  }
}

watch(() => messages.value.length, (newLen, oldLen) => {
  if (newLen <= oldLen) return
  nextTick(() => {
    messages.value.slice(oldLen).forEach(msg => {
      if (animatedMsgIds.value.has(msg.id)) return
      animatedMsgIds.value = new Set([...animatedMsgIds.value, msg.id])
      const el = messageContainer.value?.querySelector(`[data-msg-id="${msg.id}"]`)
      if (el) {
        gsap.from(el, {
          opacity: 0,
          y: 16,
          duration: 0.35,
          ease: 'power2.out',
          clearProps: 'all',
        })
      }
    })
  })
})
</script>

<template>
  <div class="ai-chat-page">
    <div class="page-shell">
      <aside class="chat-sidebar glass-panel subtle-ring reveal" v-loading="bootLoading">
        <div class="sidebar-top">
          <div>
            <span class="sidebar-kicker">NovaMind 智能助手</span>
            <h2>AI 学习伴侣</h2>
          </div>
          <el-button type="primary" class="sidebar-new-btn" :loading="creatingSession" @click="newChat">
            <Plus />
            新对话
          </el-button>
        </div>

        <div class="sidebar-live-card">
          <div class="live-label">
            <span class="live-dot"></span>
            <span>{{ isStreaming ? '正在生成回答...' : '随时可以提问' }}</span>
          </div>
          <strong>{{ currentSessionTitle }}</strong>
          <p>{{ currentSessionDescription }}</p>
        </div>

        <div class="sidebar-section">
          <div class="section-block-header">
            <span>热门推荐</span>
            <Service />
          </div>
          <div v-if="hotExamples.length === 0" class="session-empty">
            开始对话后，热门问题会出现在这里。
          </div>
          <div v-else class="quick-launch-list">
            <button
              v-for="example in hotExamples"
              :key="`${example.title}-${example.describe}`"
              type="button"
              class="quick-launch-card"
              @click="launchExample(example)"
            >
              <strong>{{ example.title }}</strong>
              <span>{{ example.describe }}</span>
            </button>
          </div>
        </div>

        <div class="sidebar-section">
          <div class="section-block-header">
            <span>历史对话</span>
            <List />
          </div>
          <div v-if="historySections.length === 0" class="session-empty">
            历史对话会按时间分组显示，方便你随时回来继续。
          </div>
          <div v-else class="history-rail">
            <div v-for="group in historySections" :key="group.label" class="history-group">
              <span class="history-label">{{ group.label }}</span>
              <button
                v-for="session in group.sessions"
                :key="session.sessionId"
                type="button"
                :class="['session-item', { active: currentSessionId === session.sessionId }]"
                @click="switchSession(session)"
              >
                <div class="session-main">
                  <ChatLineRound class="session-icon" />
                  <div class="session-copy">
                    <strong>{{ session.title }}</strong>
                    <span>{{ formatSessionTime(session.updateTime) }}</span>
                  </div>
                </div>
                <div class="session-actions">
                  <el-button text size="small" @click.stop="renameSession(session)">
                    <EditPen />
                  </el-button>
                  <el-button text size="small" @click.stop="removeSession(session)">
                    <Delete />
                  </el-button>
                </div>
              </button>
            </div>
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <section class="chat-hero glass-panel subtle-ring reveal reveal-delay-1">
          <div class="chat-hero-copy">
            <span class="chat-hero-kicker">AI Learning Partner</span>
            <h1>你的专属学习助手</h1>
            <p>
              随时提问、复用历史记录、使用学习模板——让 AI 成为你的学习伙伴。
            </p>
          </div>

          <div class="chat-hero-metrics">
            <div class="hero-metric-card">
              <Search />
              <span>当前状态</span>
              <strong>{{ currentPrompt || '自由提问' }}</strong>
            </div>
            <div class="hero-metric-card">
              <Service />
              <span>学习工具</span>
              <strong>{{ templateCards.length }} 模板 / {{ hotExamples.length }} 推荐</strong>
            </div>
            <div class="hero-metric-card">
              <List />
              <span>历史</span>
              <strong>{{ sessionCount }} 条对话记录</strong>
            </div>
          </div>
        </section>

<section class="chat-board glass-panel subtle-ring reveal reveal-delay-3">
          <div class="board-header">
            <div class="board-title">
              <Cpu class="board-title-icon" />
              <div>
                <strong>对话区域</strong>
                <span>{{ currentSessionId ? '上下文已接通' : '新对话，随时开始' }}</span>
              </div>
            </div>
            <div class="board-actions">
              <div class="run-state" :class="{ active: isStreaming }">
                <span class="live-dot"></span>
                <span>{{ isStreaming ? '生成中' : currentSessionId ? '就绪' : '等待提问' }}</span>
              </div>
              <el-button v-if="currentSessionId" text @click="renameSession()">
                <EditPen />
              </el-button>
              <el-button v-if="currentSessionId" text @click="removeSession()">
                <Delete />
              </el-button>
              <el-switch
                v-model="showReactPanel"
                active-text="展示推理过程"
                inactive-text="只看答案"
              />
            </div>
          </div>

          <div class="board-workspace" v-loading="historyLoading">
            <div ref="messageContainer" class="message-container">
              <template v-if="messages.length === 0">
              <div class="empty-state">
                <div class="empty-core">
                  <span class="empty-scene" v-html="sceneEmptyChat" />
                </div>
                <h2>{{ currentSessionId ? '继续提问' : '开始你的学习之旅' }}</h2>
                <p>
                  暂无对话，去探索一下星海吧 —— 从下方热门推荐开始，或直接输入你的问题。
                </p>
                <div class="empty-grid">
                  <button
                    v-for="example in activeExamples"
                    :key="`${example.title}-${example.describe}`"
                    type="button"
                    class="empty-card"
                    @click="launchExample(example)"
                  >
                    <ArrowRight />
                    <div>
                      <strong>{{ example.title }}</strong>
                      <span>{{ example.describe }}</span>
                    </div>
                  </button>
                </div>
              </div>
              </template>

              <template v-else>
              <div
                v-for="(msg, idx) in messages"
                :key="msg.id"
                :class="['message-row', msg.role]"
                :data-msg-id="msg.id"
              >
                <div class="message-avatar">
                  <div v-if="msg.role === 'assistant'" class="assistant-avatar">
                    <MagicStick />
                  </div>
                  <div v-else class="user-avatar">U</div>
                </div>

                <div class="message-body">
                  <div v-if="msg.role === 'user'" class="user-bubble">
                    {{ msg.content }}
                  </div>

                  <template v-else>
                    <div v-if="showReactPanel && msg.reactSteps.length > 0" class="react-panel">
                      <div class="react-header">
                        <div class="react-header-left">
                          <ArrowRight class="react-header-icon" />
                          <strong>推理过程</strong>
                          <span>{{ msg.reactSteps.length }} 步</span>
                        </div>
                        <div class="react-header-metrics" v-if="msg.metrics">
                          <span class="metric-pill">
                            <Timer />
                            {{ msg.metrics.latency }}ms
                          </span>
                          <span class="metric-pill">
                            <Tools />
                            {{ msg.metrics.toolsUsed.length }} 次调用
                          </span>
                          <span class="metric-pill metric-font">
                            {{ msg.metrics.tokensUsed }} tok
                          </span>
                        </div>
                      </div>

                      <div class="react-step-list">
                        <article
                          v-for="(step, stepIndex) in msg.reactSteps"
                          :key="step.id"
                          class="react-step"
                          :class="{ expanded: step.expanded, [step.type]: true }"
                          @click="toggleStep(step)"
                        >
                          <div class="step-connector">
                            <div v-if="stepIndex > 0" class="step-line-top"></div>
                            <div
                              class="step-dot"
                              :style="{ background: getStepColor(step.type) }"
                            >
                              <component :is="step.icon" class="step-dot-icon" />
                            </div>
                            <div v-if="stepIndex < msg.reactSteps.length - 1" class="step-line-bottom"></div>
                          </div>
                          <div class="step-panel">
                            <div class="step-top">
                              <strong>{{ step.title }}</strong>
                              <div class="step-meta">
                                <span class="step-time metric-font">{{ formatStepDuration(step, stepIndex, msg.reactSteps) }}</span>
                                <span
                                  class="step-tag metric-font"
                                  :style="{ background: getStepBg(step.type), color: getStepColor(step.type) }"
                                >
                                  {{ step.type }}
                                </span>
                                <component
                                  :is="loading && idx === messages.length - 1 && stepIndex === msg.reactSteps.length - 1 ? LoadingIcon : CircleCheck"
                                  :class="[
                                    'step-status-icon',
                                    { spinning: loading && idx === messages.length - 1 && stepIndex === msg.reactSteps.length - 1 },
                                  ]"
                                />
                              </div>
                            </div>
                            <div v-show="step.expanded" class="step-content">
                              <ToolResultCard
                                v-if="step.type === 'action'"
                                :name="step.title"
                                :arguments="step.content"
                                :content="'{}'"
                              />
                              <ToolResultCard
                                v-else-if="isObservationResult(step)"
                                :name="step.title"
                                :arguments="'{}'"
                                :content="step.content"
                              />
                              <pre v-else>{{ step.content }}</pre>
                            </div>
                          </div>
                        </article>
                      </div>
                    </div>

                    <div v-if="msg.content" class="answer-panel" :class="{ streaming: isLastAssistantStreaming && idx === messages.length - 1 }">
                      <div class="answer-top">
                        <MagicStick class="answer-icon" />
                        <span>回答</span>
                        <span v-if="isLastAssistantStreaming && idx === messages.length - 1" class="stream-badge">流式</span>
                      </div>
                      <div class="answer-content" :class="{ 'streaming-text': isLastAssistantStreaming && idx === messages.length - 1 }">
                        <MarkdownRenderer :content="msg.content" />
                      </div>
                      <span v-if="isLastAssistantStreaming && idx === messages.length - 1" class="streaming-cursor"></span>
                    </div>

                    <div class="message-actions">
                      <el-tooltip content="复制回答" placement="top">
                        <el-button text size="small" @click="copyContent(msg.content)">
                          <CopyDocument />
                        </el-button>
                      </el-tooltip>
                      <el-tooltip content="重新生成" placement="top">
                        <el-button text size="small" @click="regenerateFrom(idx)">
                          <RefreshRight />
                        </el-button>
                      </el-tooltip>
                    </div>
                  </template>
                </div>
              </div>

              <div v-if="loading" class="message-row assistant loading-row">
                <div class="message-avatar">
                  <div class="assistant-avatar loading">
                    <MagicStick />
                  </div>
                </div>
                <div class="message-body">
                  <div v-if="currentReactSteps.length > 0 && showReactPanel" class="loading-panel">
                    <div v-for="step in currentReactSteps" :key="step.id" class="loading-step">
                      <div class="loading-dot" :style="{ background: getStepColor(step.type) }">
                        <component :is="step.icon" class="step-dot-icon" />
                      </div>
                      <div class="loading-step-content">
                        <span class="loading-step-title">{{ step.title }}</span>
                        <span class="loading-step-status">进行中...</span>
                      </div>
                      <div class="loading-step-pulse" :style="{ background: getStepColor(step.type) }"></div>
                    </div>
                    <div class="loading-tail">
                      <span class="loading-tail-dot"></span>
                      <span class="loading-tail-dot"></span>
                      <span class="loading-tail-dot"></span>
                    </div>
                  </div>
                  <div v-else class="typing-panel">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
              </template>
            </div>

            <aside class="trae-side-panel">
              <article class="side-card orchestration-card">
                <div class="side-title-row">
                  <div class="side-title-left">
                    <Cpu class="side-title-icon" />
                    <strong>任务编排</strong>
                  </div>
                  <span class="side-badge">{{ panelTodoItems.length }} 项</span>
                </div>

                <div v-if="!panelTodoItems.length && !isStreaming" class="orchestration-empty">
                  <div class="empty-dot-pulse">
                    <span></span><span></span><span></span>
                  </div>
                  <p>等待任务编排...</p>
                </div>

                <div v-else class="task-orchestrator">
                  <TransitionGroup name="task-fade" tag="div" class="task-list">
                    <div
                      v-for="(item, idx) in panelTodoItems"
                      :key="item.id"
                      class="task-row"
                      :class="{ active: !item.done, done: item.done }"
                    >
                      <div class="task-indicator">
                        <div v-if="!item.done" class="task-spinner"></div>
                        <div v-else class="task-check"><CircleCheck /></div>
                      </div>
                      <div class="task-content">
                        <span class="task-title">{{ item.title }}</span>
                        <span class="task-subtitle">{{ item.done ? '已完成' : '进行中...' }}</span>
                      </div>
                      <div class="task-index">#{{ idx + 1 }}</div>
                    </div>
                  </TransitionGroup>
                  <div v-if="isStreaming" class="task-divider"></div>
                  <div v-if="isStreaming" class="task-stream-hint">
                    <span class="stream-pulse"></span>
                    正在思考下一步...
                  </div>
                </div>
              </article>

              <article class="side-card" v-if="panelSteps.length > 0">
                <div class="side-title-row">
                  <div class="side-title-left">
                    <List class="side-title-icon" />
                    <strong>推理时间线</strong>
                  </div>
                  <span class="side-badge">{{ panelSteps.length }} 步</span>
                </div>

                <div class="react-timeline">
                  <div
                    v-for="(step, idx) in panelSteps.slice(0, 12)"
                    :key="step.id"
                    class="timeline-item"
                    :class="[step.type, { 'timeline-last': idx === Math.min(panelSteps.length, 12) - 1 }]"
                  >
                    <div class="timeline-node">
                      <div class="timeline-dot" :style="{ background: getStepColor(step.type) }">
                        <component :is="step.icon" class="step-dot-icon" />
                      </div>
                      <div v-if="idx < Math.min(panelSteps.length, 12) - 1" class="timeline-line" :style="{ background: getStepBg(step.type) }"></div>
                    </div>
                    <div class="timeline-body">
                      <span class="timeline-title">{{ step.title }}</span>
                      <span class="timeline-duration">{{ formatStepDuration(step, idx, panelSteps) }}</span>
                    </div>
                  </div>
                  <div v-if="panelSteps.length > 12" class="timeline-more">
                    +{{ panelSteps.length - 12 }} 更多步骤
                  </div>
                </div>
              </article>

              <article class="side-card metrics-card">
                <div class="side-title-row">
                  <div class="side-title-left">
                    <Timer class="side-title-icon" />
                    <strong>会话统计</strong>
                  </div>
                </div>

                <div class="metrics-grid">
                  <div class="metrics-cell">
                    <span class="metrics-value">{{ panelTokenUsage.percentage }}%</span>
                    <span class="metrics-label">上下文</span>
                  </div>
                  <div class="metrics-cell">
                    <span class="metrics-value">{{ streamMetrics.toolsUsed.length || (latestAssistantMessage?.metrics?.toolsUsed.length || 0) }}</span>
                    <span class="metrics-label">工具调用</span>
                  </div>
                  <div class="metrics-cell">
                    <span class="metrics-value">{{ streamMetrics.tokensUsed || (latestAssistantMessage?.metrics?.tokensUsed || 0) }}</span>
                    <span class="metrics-label">Token</span>
                  </div>
                </div>

                <div class="context-bar-wrapper">
                  <div class="context-bar">
                    <div class="context-seg search" :style="{ width: `${panelTokenUsage.webSearch}%` }"></div>
                    <div class="context-seg other" :style="{ width: `${panelTokenUsage.other}%` }"></div>
                  </div>
                  <div class="context-legend">
                    <span><i class="search"></i> 检索</span>
                    <span><i class="other"></i> 对话</span>
                  </div>
                </div>
              </article>
            </aside>
          </div>

          <div class="chat-input-area">
            <div class="composer-topline">
              <span>{{ currentSessionId ? '当前对话已接通上下文' : '发送第一条消息后将自动创建新对话' }}</span>
              <button
                v-if="isStreaming"
                type="button"
                class="stop-link"
                @click="stopStreamingMission"
              >
                停止生成
              </button>
            </div>
            <div class="input-shell">
              <el-input
                v-model="input"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 6 }"
                placeholder="输入你的问题，AI 学习伴侣随时为你解答..."
                @keydown.enter.exact.prevent="sendMessage"
              />
              <div class="composer-actions">
                <el-button
                  v-if="templateCards[0]"
                  class="ghost-btn"
                  @click="applyTemplate(templateCards[0])"
                >
                  <Search />
                  模板
                </el-button>
                <el-button
                  type="primary"
                  class="send-btn"
                  :disabled="!input.trim() || loading"
                  @click="sendMessage"
                >
                  发送
                  <ArrowRight />
                </el-button>
              </div>
            </div>
            <p class="input-hint">
              Enter 发送 · Shift + Enter 换行
            </p>
          </div>
        </section>

        <section class="template-section reveal reveal-delay-2">
          <div class="section-title-row">
            <div>
              <span class="section-kicker">Templates</span>
              <h2>学习模板</h2>
            </div>
            <span class="section-copy">套用模板快速发起提问，让每一次学习都更有方向。</span>
          </div>

          <div class="template-grid">
            <article
              v-for="template in templateCards"
              :key="template.key"
              class="template-card glass-panel subtle-ring"
            >
              <div class="template-card-top">
                <span class="template-label">{{ template.label }}</span>
                <strong>{{ template.title }}</strong>
              </div>
              <p>{{ template.desc }}</p>
              <code>{{ template.content.split('\n')[0] }}</code>
              <el-button text class="template-action" @click="applyTemplate(template)">
                使用此模板
                <ArrowRight />
              </el-button>
            </article>
          </div>
        </section>

        <section class="prompt-rail reveal reveal-delay-3">
          <button
            v-for="prompt in consolePrompts"
            :key="prompt"
            type="button"
            class="prompt-chip"
            @click="launchPrompt(prompt)"
          >
            {{ prompt }}
          </button>
        </section>

        
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>

.ai-chat-page {
  position: relative;
  padding: 20px 20px 24px;
  min-height: calc(100vh - 118px);
  background:
    radial-gradient(ellipse 44% 36% at 16% 10%, var(--nm-accent-soft), transparent 66%),
    radial-gradient(ellipse 40% 32% at 86% 18%, var(--nm-cyan-soft), transparent 64%),
    var(--nm-paper);
}

.page-shell {
  position: relative;
  z-index: 1;
  max-width: 1480px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 22px;
}

.chat-sidebar {
  position: sticky;
  top: 108px;
  align-self: start;
  padding: 22px;
  border-radius: 30px;
  max-height: calc(100vh - 132px);
  overflow: auto;
}

.sidebar-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.sidebar-kicker,
.section-kicker,
.chat-hero-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--nm-accent);
}

.sidebar-top h2 {
  font-size: 28px;
}

.sidebar-new-btn {
  border-radius: 999px;
  height: 42px;
  padding: 0 16px;
}

.sidebar-live-card {
  margin-top: 18px;
  padding: 16px;
  border-radius: 22px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.sidebar-live-card strong {
  display: block;
  font-size: 16px;
  margin-bottom: 8px;
}

.live-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--nm-ink-2);
  margin-bottom: 12px;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--nm-accent);
  box-shadow: 0 0 0 6px var(--nm-accent-soft);
  animation: pulse 2s ease-in-out infinite;
}

.sidebar-live-card p {
  color: var(--nm-ink-3);
  font-size: 13px;
}

.react-phase-stack,
.quick-launch-list,
.sidebar-stats,
.template-grid {
  display: grid;
  gap: 10px;
}

.react-phase-stack {
  margin-top: 18px;
}

.mini-phase {
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.mini-phase strong {
  display: block;
  font-size: 13px;
  margin-bottom: 4px;
}

.mini-phase span {
  color: var(--nm-ink-3);
  font-size: 12px;
}

.sidebar-section {
  margin-top: 20px;
}

.section-block-header,
.section-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.section-block-header {
  margin-bottom: 10px;
  color: var(--nm-ink-2);
  font-size: 13px;
}

.section-block-header :deep(svg) {
  width: 16px;
  height: 16px;
  color: var(--nm-accent);
}

.quick-launch-card,
.session-item {
  width: 100%;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  cursor: pointer;
  transition: all var(--nm-dur) var(--nm-ease);
}

.quick-launch-card {
  padding: 14px;
  border-radius: 18px;
  text-align: left;
  color: var(--nm-ink-2);
}

.quick-launch-card:hover,
.session-item:hover,
.session-item.active {
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
  color: var(--nm-ink);
}

.quick-launch-card strong {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
}

.quick-launch-card span {
  color: var(--nm-ink-3);
  font-size: 12px;
  line-height: 1.6;
}

.history-rail {
  display: grid;
  gap: 14px;
}

.history-group {
  display: grid;
  gap: 8px;
}

.history-label {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--nm-ink-3);
}

.session-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 18px;
}

.session-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.session-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.session-copy {
  min-width: 0;
}

.session-copy strong,
.session-copy span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-copy strong {
  font-size: 13px;
}

.session-copy span {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.session-actions {
  display: inline-flex;
  align-items: center;
  opacity: 0;
  transition: opacity var(--nm-dur-fast) var(--nm-ease);
}

.session-item:hover .session-actions,
.session-item.active .session-actions {
  opacity: 1;
}

.session-empty {
  padding: 14px;
  border-radius: 18px;
  background: var(--nm-surface-2);
  border: 1px dashed var(--nm-line-strong);
  color: var(--nm-ink-3);
  font-size: 12px;
  line-height: 1.7;
}

.stat-box {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 18px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.stat-box :deep(svg) {
  width: 36px;
  height: 36px;
  padding: 10px;
  border-radius: 12px;
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.stat-box strong {
  display: block;
  font-size: 16px;
  color: var(--nm-ink);
}

.stat-box span {
  color: var(--nm-ink-3);
  font-size: 12px;
}

.chat-main {
  min-width: 0;
  display: grid;
  gap: 18px;
}

.chat-hero,
.chat-board {
  border-radius: 30px;
}

.chat-hero {
  padding: 22px 26px;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}

.chat-hero-copy h1 {
  font-size: clamp(30px, 3.6vw, 44px);
  line-height: 1.08;
  margin-bottom: 10px;
}

.chat-hero-copy p,
.section-copy {
  color: var(--nm-ink-2);
}

.chat-hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-metric-card {
  padding: 13px 14px;
  border-radius: 22px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.hero-metric-card :deep(svg) {
  width: 18px;
  height: 18px;
  color: var(--nm-accent);
  margin-bottom: 10px;
}

.hero-metric-card span {
  display: block;
  color: var(--nm-ink-3);
  font-size: 12px;
  margin-bottom: 4px;
}

.hero-metric-card strong {
  display: block;
  font-size: 14px;
  color: var(--nm-ink);
  line-height: 1.5;
}

.template-section {
  content-visibility: auto;
  contain-intrinsic-size: 380px;
}

.section-title-row h2 {
  font-size: clamp(28px, 3vw, 38px);
}

.template-grid {
  margin-top: 16px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.template-card {
  padding: 18px;
  border-radius: 24px;
}

.template-card-top {
  margin-bottom: 12px;
}

.template-label {
  display: inline-flex;
  margin-bottom: 6px;
  color: var(--nm-accent);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.template-card strong {
  display: block;
  font-size: 18px;
}

.template-card p {
  color: var(--nm-ink-2);
  font-size: 13px;
  line-height: 1.7;
  min-height: 66px;
}

.template-card code {
  display: block;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--nm-surface-3);
  border: 1px solid var(--nm-line);
  color: var(--nm-ink-2);
  font-size: 12px;
  line-height: 1.6;
  margin: 14px 0 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.template-action {
  padding: 0;
  color: var(--nm-accent);
}

.prompt-rail {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.prompt-chip {
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  color: var(--nm-ink-2);
  padding: 10px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition: all var(--nm-dur) var(--nm-ease);
}

.prompt-chip:hover {
  transform: translateY(-2px);
  border-color: var(--nm-accent-line);
  color: var(--nm-ink);
}

.chat-board {
  padding: 22px;
  content-visibility: auto;
  contain-intrinsic-size: 920px;
  background: var(--nm-surface);
  border: 1px solid var(--nm-line);
}

.board-workspace {
  margin-top: 14px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 14px;
}

.board-header,
.react-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.board-header {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--nm-line);
}

.board-title {
  display: flex;
  gap: 12px;
  align-items: center;
}

.board-title-icon {
  width: 42px;
  height: 42px;
  padding: 10px;
  border-radius: 14px;
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.board-title strong {
  display: block;
  font-size: 18px;
}

.board-title span {
  display: block;
  color: var(--nm-ink-3);
  font-size: 12px;
}

.board-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.run-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 999px;
  background: var(--nm-accent-soft);
  color: var(--nm-ink-2);
  font-size: 12px;
}

.run-state.active {
  border: 1px solid var(--nm-cyan);
}

.metric-pill :deep(svg) {
  width: 14px;
  height: 14px;
}

.message-container {
  max-height: calc(100vh - 360px);
  overflow-y: auto;
  padding: 12px 4px;
}

.trae-side-panel {
  display: grid;
  gap: 12px;
  align-content: start;
  position: sticky;
  top: 0;
  height: fit-content;
}

.side-card {
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  backdrop-filter: blur(12px);
  border-radius: 18px;
  padding: 16px;
  transition: all var(--nm-dur) var(--nm-ease);
}

.side-card + .side-card {
  margin-top: 14px;
}

.orchestration-card {
  border-color: var(--nm-cyan-soft);
}

.side-title-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.side-title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.side-title-icon {
  width: 16px;
  height: 16px;
  color: var(--nm-accent);
}

.side-title-row strong {
  font-size: 14px;
  color: var(--nm-ink);
}

.side-title-row span,
.side-badge {
  font-size: 11px;
  color: var(--nm-ink-3);
  background: var(--nm-surface-3);
  padding: 2px 10px;
  border-radius: 999px;
}

.side-badge {
  background: var(--nm-cyan-soft);
  color: var(--nm-cyan);
  font-weight: 600;
}

.orchestration-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  color: var(--nm-ink-3);
  font-size: 13px;
}

.empty-dot-pulse {
  display: flex;
  gap: 6px;
}

.empty-dot-pulse span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nm-ink-3);
  animation: pulse 1.4s ease-in-out infinite;
}

.empty-dot-pulse span:nth-child(2) { animation-delay: 0.2s; }
.empty-dot-pulse span:nth-child(3) { animation-delay: 0.4s; }

.task-orchestrator {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-row {
  display: grid;
  grid-template-columns: 28px 1fr 28px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--nm-surface-3);
  border: 1px solid var(--nm-line);
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.task-row.active {
  background: var(--nm-cyan-soft);
  border-color: var(--nm-cyan);
  box-shadow: 0 2px 12px var(--nm-cyan-soft);
}

.task-row.done {
  opacity: 0.65;
}

.task-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
}

.task-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--nm-cyan-soft);
  border-top-color: var(--nm-cyan);
  border-radius: 50%;
  animation: rotateSlow 0.8s linear infinite;
}

.task-check {
  color: var(--nm-success);
  display: flex;
}

.task-check :deep(svg) {
  width: 16px;
  height: 16px;
}

.task-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.task-title {
  font-size: 13px;
  color: var(--nm-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.task-subtitle {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.task-index {
  font-size: 11px;
  color: var(--nm-ink-3);
  font-variant-numeric: tabular-nums;
  text-align: right;
  font-family: var(--nm-mono);
}

.task-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--nm-cyan-soft), transparent);
  margin: 6px 0;
}

.task-stream-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: var(--nm-ink-3);
  font-size: 12px;
}

.stream-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nm-cyan);
  animation: pulse 1s ease-in-out infinite;
}

.task-fade-enter-active,
.task-fade-leave-active {
  transition: all 0.3s ease;
}

.task-fade-enter-from {
  opacity: 0;
  transform: translateX(-12px);
}

.task-fade-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

.react-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 4px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 12px;
  min-height: 44px;
}

.timeline-node {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.timeline-dot {
  width: 24px;
  height: 24px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  z-index: 1;
}

.timeline-dot :deep(svg) {
  width: 12px;
  height: 12px;
  color: var(--nm-accent-ink);
}

.timeline-line {
  width: 2px;
  flex: 1;
  min-height: 24px;
  border-radius: 2px;
}

.timeline-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 0;
  min-height: 24px;
}

.timeline-title {
  font-size: 12px;
  color: var(--nm-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.timeline-duration {
  font-size: 10px;
  color: var(--nm-ink-3);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.timeline-more {
  text-align: center;
  font-size: 11px;
  color: var(--nm-ink-3);
  padding: 8px 0;
}

.metrics-card {
  border-color: var(--nm-cyan-soft);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.metrics-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px;
  border-radius: 14px;
  background: var(--nm-surface-3);
  border: 1px solid var(--nm-line);
}

.metrics-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--nm-ink);
  font-variant-numeric: tabular-nums;
}

.metrics-label {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.context-bar-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.context-bar {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: var(--nm-surface-3);
  overflow: hidden;
  display: flex;
}

.context-seg {
  height: 100%;
}

.context-seg.search {
  background: var(--nm-accent);
}

.context-seg.other {
  background: var(--nm-ink-3);
}

.context-legend {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--nm-ink-2);
}

.context-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  margin-right: 6px;
}

.context-legend i.search {
  background: var(--nm-accent);
}

.context-legend i.other {
  background: var(--nm-ink-3);
}

.empty-state {
  max-width: 920px;
  margin: 24px auto 12px;
  text-align: center;
}

.empty-core {
  margin-bottom: 20px;
}

/* AI 待命星海插画（scene-empty-chat.svg，内联 SMIL 动画） */
.empty-scene {
  display: block;
  width: 220px;
  max-width: 60%;
  margin: 0 auto;

  :deep(svg) {
    width: 100%;
    height: auto;
    display: block;
  }
}

.empty-state h2 {
  font-size: 34px;
  margin-bottom: 10px;
}

.empty-state p {
  color: var(--nm-ink-2);
  max-width: 720px;
  margin: 0 auto 24px;
}

.empty-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.empty-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  text-align: left;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
  color: var(--nm-ink-2);
  cursor: pointer;
  transition: all var(--nm-dur) var(--nm-ease);
}

.empty-card:hover {
  transform: translateY(-3px);
  color: var(--nm-ink);
  border-color: var(--nm-accent-line);
}

.empty-card :deep(svg) {
  width: 18px;
  height: 18px;
  color: var(--nm-accent);
  flex-shrink: 0;
}

.empty-card strong {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
}

.empty-card span {
  color: var(--nm-ink-3);
  font-size: 12px;
  line-height: 1.6;
}

.message-row {
  display: flex;
  gap: 16px;
  max-width: 1100px;
  margin: 0 auto 28px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.assistant-avatar,
.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.assistant-avatar {
  background: var(--nm-brand-gradient);
  box-shadow: var(--nm-sh-accent);
}

.assistant-avatar :deep(svg) {
  width: 18px;
  height: 18px;
  color: var(--nm-accent-ink);
}

.assistant-avatar.loading {
  animation: pulse 1.5s ease-in-out infinite;
}

.user-avatar {
  background: var(--nm-accent-soft);
  border: 1px solid var(--nm-accent-line);
  color: var(--nm-accent-hover);
  font-weight: 700;
}

.message-body {
  flex: 1;
  min-width: 0;
}

.user-bubble {
  margin-left: auto;
  max-width: min(78%, 700px);
  padding: 16px 18px;
  border-radius: 22px 22px 8px 22px;
  background: var(--nm-accent);
  border: 1px solid var(--nm-accent);
  color: var(--nm-accent-ink);
  box-shadow: var(--nm-sh-accent);
}

.user-bubble :deep(a) {
  color: var(--nm-accent-ink);
  text-decoration: underline;
}

.react-panel {
  padding: 18px;
  border-radius: 24px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
  backdrop-filter: blur(10px) saturate(160%);
  -webkit-backdrop-filter: blur(10px) saturate(160%);
  box-shadow: var(--nm-sh-2);
}

.react-header {
  margin-bottom: 18px;
}

.react-header-left {
  display: flex;
  gap: 10px;
  align-items: center;
}

.react-header-left strong {
  font-size: 16px;
}

.react-header-left span {
  color: var(--nm-ink-3);
  font-size: 12px;
}

.react-header-icon {
  width: 16px;
  height: 16px;
  color: var(--nm-accent);
}

.react-header-metrics,
.message-actions,
.composer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.react-step-list {
  display: grid;
  gap: 4px;
  padding: 4px 0;
}

.react-step {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 14px;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.react-step:hover .step-panel {
  border-color: var(--nm-accent-line);
  box-shadow: var(--nm-sh-2);
}

.react-step:hover .step-dot {
  transform: scale(1.08);
}

.step-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-line-top,
.step-line-bottom {
  width: 2px;
  flex: 1;
  min-height: 8px;
  border-radius: 2px;
  background: var(--nm-line);
}

.step-line-bottom {
  min-height: 12px;
}

.step-dot {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  box-shadow: 0 0 0 4px var(--nm-surface-2);
  transition: transform var(--nm-dur) var(--nm-ease);
  z-index: 1;
}

.step-dot-icon {
  width: 18px;
  height: 18px;
  color: var(--nm-accent-ink);
}

.step-panel {
  padding: 14px 16px;
  border-radius: 18px;
  background: var(--nm-surface);
  border: 1px solid var(--nm-line);
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.react-step.expanded .step-panel {
  border-color: var(--nm-accent-line);
}

.step-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.step-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.step-time {
  color: var(--nm-ink-3);
  font-size: 11px;
}

.step-status-icon {
  width: 14px;
  height: 14px;
  color: var(--nm-success);
}

.step-status-icon.spinning {
  color: var(--nm-cyan);
  animation: rotateSlow 1.2s linear infinite;
}

.step-top strong {
  font-size: 14px;
}

.step-tag {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.step-content {
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
  color: var(--nm-ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
}

.answer-panel {
  margin-top: 14px;
  padding: 20px;
  border-radius: 24px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
  transition: all var(--nm-dur) var(--nm-ease);
  position: relative;
  min-height: 60px;
}

.answer-panel.streaming {
  border-color: var(--nm-cyan);
  box-shadow: 0 0 0 1px var(--nm-cyan-soft), 0 4px 20px var(--nm-cyan-soft);
}

.answer-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--nm-accent);
  font-weight: 700;
}

.stream-badge {
  font-size: 10px;
  font-weight: 500;
  color: var(--nm-cyan);
  background: var(--nm-cyan-soft);
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.02em;
  animation: pulse 2s ease-in-out infinite;
}

.answer-icon {
  width: 16px;
  height: 16px;
}

.answer-content {
  color: var(--nm-ink);
  line-height: 1.75;
}

.answer-content code {
  padding: 2px 6px;
  border-radius: 8px;
  background: var(--nm-accent-soft);
  font-family: var(--nm-mono);
  font-size: 0.9em;
}

.streaming-text {
  border-right: 2px solid var(--nm-cyan);
  animation: cursorBlink 0.8s step-end infinite;
}

.streaming-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: var(--nm-cyan);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: cursorBlink 0.8s step-end infinite;
}

.loading-panel {
  padding: 18px;
  border-radius: 24px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.loading-step {
  display: grid;
  grid-template-columns: 30px 1fr 8px;
  gap: 12px;
  align-items: center;
  color: var(--nm-ink-2);
  position: relative;
}

.loading-step + .loading-step {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--nm-line);
}

.loading-dot {
  width: 30px;
  height: 30px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse 1.5s ease-in-out infinite;
}

.loading-step-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.loading-step-title {
  font-size: 13px;
  color: var(--nm-ink);
}

.loading-step-status {
  font-size: 11px;
  color: var(--nm-ink-3);
}

.loading-step-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: pulse 1s ease-in-out infinite;
}

.loading-tail {
  display: flex;
  gap: 5px;
  padding: 12px 0 0 42px;
}

.loading-tail-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--nm-ink-3);
  animation: pulse 1.4s ease-in-out infinite;
}

.loading-tail-dot:nth-child(2) { animation-delay: 0.2s; }
.loading-tail-dot:nth-child(3) { animation-delay: 0.4s; }

.typing-panel {
  display: inline-flex;
  gap: 6px;
  padding: 14px 18px;
  border-radius: 18px;
  background: var(--nm-surface-2);
  border: 1px solid var(--nm-line);
}

.typing-panel span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--nm-ink-3);
  animation: pulse 1.4s ease-in-out infinite;
}

.typing-panel span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-panel span:nth-child(3) {
  animation-delay: 0.4s;
}

.chat-input-area {
  /* 吸底：对话面板超出一屏时输入框钉在视口底部，900px 首屏内即可见可输入 */
  position: sticky;
  bottom: 0;
  z-index: 6;
  margin-top: 14px;
  padding: 14px 16px;
  border-top: 1px solid var(--nm-line);
  border-radius: 20px 20px 0 0;
  background: var(--nm-surface);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: var(--nm-sh-3);
}

.composer-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
  color: var(--nm-ink-3);
  font-size: 12px;
}

.stop-link {
  border: 0;
  background: transparent;
  color: var(--nm-cyan);
  cursor: pointer;
}

.input-shell {
  display: grid;
  gap: 12px;
}

.composer-actions {
  justify-content: flex-end;
}

.ghost-btn {
  border-radius: 999px;
  background: var(--nm-surface);
  border-color: var(--nm-line-strong);
  color: var(--nm-ink);
  transition: all var(--nm-dur-fast) var(--nm-ease);

  &:hover {
    background: var(--nm-surface-3);
    border-color: var(--nm-accent-line);
    color: var(--nm-accent);
    box-shadow: var(--nm-sh-2);
  }
}

.send-btn {
  border-radius: 999px;
  padding: 0 22px;
}

.input-shell :deep(.el-textarea__inner) {
  min-height: 58px !important;
  padding: 16px 18px;
  border-radius: 20px;
  backdrop-filter: blur(8px);
  transition: border-color var(--nm-dur-fast) var(--nm-ease), box-shadow var(--nm-dur-fast) var(--nm-ease);
}

.input-shell :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--nm-accent) inset, 0 0 0 3px var(--nm-accent-soft) !important;
}

.input-hint {
  margin-top: 10px;
  color: var(--nm-ink-3);
  font-size: 12px;
  text-align: center;
}

@media (max-width: 1360px) {
  .page-shell {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    position: relative;
    top: auto;
    max-height: none;
  }

  .chat-hero,
  .template-grid {
    grid-template-columns: 1fr;
  }

  .board-workspace {
    grid-template-columns: 1fr;
  }

  .trae-side-panel {
    position: relative;
  }
}

@media (max-width: 900px) {
  .ai-chat-page {
    padding: 0 10px 18px;
  }

  .chat-hero-metrics {
    grid-template-columns: 1fr;
  }

  .board-header,
  .react-header,
  .section-title-row,
  .composer-topline {
    flex-direction: column;
    align-items: flex-start;
  }

  .empty-grid {
    grid-template-columns: 1fr;
  }

  .message-row {
    gap: 10px;
  }

  .react-step {
    grid-template-columns: 40px 1fr;
  }

  .step-dot {
    width: 36px;
    height: 36px;
    border-radius: 12px;
  }

  .chat-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .composer-actions {
    justify-content: stretch;
  }

  .ghost-btn,
  .send-btn {
    width: 100%;
  }

  .prompt-rail,
  .message-actions {
    flex-direction: column;
  }
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 旧 global.scss 被合并后丢失的本地动画，补回以保证流式/等待指示器仍在动 */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

@keyframes rotateSlow {
  to { transform: rotate(360deg); }
}
</style>

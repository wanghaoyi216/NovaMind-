<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  ChatDotRound,
  ChatLineSquare,
  Close,
  EditPen,
  MagicStick,
  Refresh,
  Star,
  StarFilled,
  View,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

type LoadState = 'loading' | 'ready' | 'error'
type TopicKey = 'latest' | 'hot' | 'unanswered' | 'solved'

interface DiscussionAuthor {
  name: string
  avatar: string
  role: string
}

interface Discussion {
  id: number
  title: string
  excerpt: string
  author: DiscussionAuthor
  category: string
  createdAt: number
  replies: number
  views: number
  likes: number
  solved: boolean
  liked: boolean
}

interface Contributor {
  name: string
  avatar: string
  role: string
  score: number
}

interface HotTag {
  label: string
  count: number
}

const AVATAR_BASE = '/resource/user-avatars/'
const DEFAULT_AVATAR = `${AVATAR_BASE}avatar_student_male_01.jpg`

const router = useRouter()
const userStore = useUserStore()

const state = ref<LoadState>('loading')
const discussions = ref<Discussion[]>([])
const activeTopic = ref<TopicKey>('latest')
const activeCategory = ref('全部')
const activeTag = ref('')

const composerOpen = ref(false)
const draftError = ref('')
const draft = reactive({
  title: '',
  category: '后端架构',
  body: '',
})

const topics: Array<{ key: TopicKey; label: string; hint: string }> = [
  { key: 'latest', label: '最新', hint: '按发布时间排序' },
  { key: 'hot', label: '热门', hint: '按互动热度排序' },
  { key: 'unanswered', label: '待解答', hint: '还没有被采纳的解答' },
  { key: 'solved', label: '已解决', hint: '已经有结论的讨论' },
]

const categories = ['全部', '前端工程', '后端架构', 'AI 应用', '云原生', '职业成长']

const contributors: Contributor[] = [
  {
    name: '周老师',
    avatar: `${AVATAR_BASE}avatar_teacher_male_01.jpg`,
    role: '讲师',
    score: 428,
  },
  {
    name: '林晓',
    avatar: `${AVATAR_BASE}avatar_student_female_01.jpg`,
    role: '版主',
    score: 316,
  },
  {
    name: '陈默',
    avatar: `${AVATAR_BASE}avatar_student_male_01.jpg`,
    role: '助教',
    score: 274,
  },
  {
    name: '苏芮',
    avatar: `${AVATAR_BASE}avatar_teacher_female_01.jpg`,
    role: '讲师',
    score: 208,
  },
  {
    name: '许扬',
    avatar: `${AVATAR_BASE}avatar_young_man_01.png`,
    role: '学员',
    score: 165,
  },
]

const hotTags: HotTag[] = [
  { label: '网关鉴权', count: 42 },
  { label: '提示工程', count: 37 },
  { label: '设计令牌', count: 28 },
  { label: 'Nacos', count: 24 },
  { label: '知识图谱', count: 19 },
  { label: '面试准备', count: 16 },
]

/**
 * 后端暂无社区服务，讨论内容来自本地内容层。
 * 这里仍保留完整的异步加载路径（loading / ready / error），
 * 后续把 loadDiscussions 换成真实接口即可，页面无需改动。
 */
function loadDiscussions(): Discussion[] {
  const now = Date.now()
  const minutes = (value: number) => now - value * 60 * 1000
  const seed: Array<Omit<Discussion, 'createdAt' | 'liked'> & { minutesAgo: number }> = [
    {
      id: 1,
      title: '网关鉴权通过了，但下游服务拿不到 user-info 请求头，怎么排查？',
      excerpt:
        '本地用 AccountAuthFilter 放行后，user-service 里 UserContext.getUser() 一直返回 null。已经确认 token 能解析成功，是不是请求头在转发时被丢掉了？',
      author: { name: '陈默', avatar: `${AVATAR_BASE}avatar_student_male_01.jpg`, role: '助教' },
      category: '后端架构',
      minutesAgo: 12,
      replies: 8,
      views: 214,
      likes: 12,
      solved: false,
    },
    {
      id: 2,
      title: '「Spring Cloud 微服务全链路实践」第三章的熔断为什么推荐 fallbackFactory？',
      excerpt:
        '课程里把 fallback 换成了 fallbackFactory，说是能拿到异常原因。想确认一下在生产环境里这样会不会掩盖真实错误。',
      author: { name: '王讲师', avatar: `${AVATAR_BASE}avatar_teacher_male_01.jpg`, role: '讲师' },
      category: '后端架构',
      minutesAgo: 44,
      replies: 5,
      views: 132,
      likes: 7,
      solved: true,
    },
    {
      id: 3,
      title: 'AI 应用的提示词模板应该放在后端还是前端维护？',
      excerpt:
        '我们现在把模板放在前端常量里，改一次文案就要发版。放到后端配置中心更灵活，但调试成本会变高，大家怎么权衡？',
      author: { name: '林晓', avatar: `${AVATAR_BASE}avatar_student_female_01.jpg`, role: '版主' },
      category: 'AI 应用',
      minutesAgo: 76,
      replies: 11,
      views: 356,
      likes: 24,
      solved: false,
    },
    {
      id: 4,
      title: 'Vue 3 项目把 Element Plus 换成设计令牌，踩到了哪些坑？',
      excerpt:
        '把 el-* 的 CSS 变量映射到自己的语义令牌后，弹层和抽屉的圆角还是旧值，最后发现要覆盖 .el-popper。整理一下完整清单。',
      author: { name: '苏芮', avatar: `${AVATAR_BASE}avatar_teacher_female_01.jpg`, role: '讲师' },
      category: '前端工程',
      minutesAgo: 128,
      replies: 6,
      views: 178,
      likes: 15,
      solved: true,
    },
    {
      id: 5,
      title: 'Docker Compose 起 Nacos 之后服务注册不上，网络别名该怎么配？',
      excerpt:
        '宿主机能访问 8848，容器里却连不上。怀疑是 bridge 网络里没有把 novamind-nacos 作为别名暴露给其他服务，求一份可用的 compose 片段。',
      author: { name: '许扬', avatar: `${AVATAR_BASE}avatar_young_man_01.png`, role: '学员' },
      category: '云原生',
      minutesAgo: 186,
      replies: 9,
      views: 264,
      likes: 9,
      solved: false,
    },
    {
      id: 6,
      title: 'prePlaceOrder 返回的 orderIds 是购物车 id 还是订单 id？',
      excerpt:
        '下单链路里先调 prePlaceOrder 再调 placeOrder，两个接口的参数看起来是同一批 id，但语义好像不一样，文档里没写清楚。',
      author: { name: '陈默', avatar: `${AVATAR_BASE}avatar_student_male_01.jpg`, role: '助教' },
      category: '后端架构',
      minutesAgo: 312,
      replies: 3,
      views: 96,
      likes: 4,
      solved: true,
    },
    {
      id: 7,
      title: '零基础转行前端，先啃 HTML/CSS 还是直接上框架？',
      excerpt:
        '看了课程中心的路线图，建议先打基础。但身边同学都在直接学 Vue，想知道哪条路能更快找到第一份工作。',
      author: { name: '许扬', avatar: `${AVATAR_BASE}avatar_young_man_01.png`, role: '学员' },
      category: '职业成长',
      minutesAgo: 430,
      replies: 21,
      views: 640,
      likes: 38,
      solved: false,
    },
    {
      id: 8,
      title: '知识图谱里 USER / INTERACTION / MEDIA 三类节点应该怎么建模？',
      excerpt:
        '现在把用户、AI 交互、课程资源都塞进同一张图，查询时很难区分。是否应该给关系加类型而不是给节点加类型？',
      author: { name: '周老师', avatar: `${AVATAR_BASE}avatar_teacher_male_01.jpg`, role: '讲师' },
      category: 'AI 应用',
      minutesAgo: 540,
      replies: 4,
      views: 121,
      likes: 6,
      solved: false,
    },
    {
      id: 9,
      title: '深色主题下课程配图和插画的对比度怎么调？',
      excerpt:
        '浅色主题下很好看的封面，切到深色后边缘发灰。除了加一层描边，还有没有更系统的处理方式？',
      author: { name: '林晓', avatar: `${AVATAR_BASE}avatar_student_female_01.jpg`, role: '版主' },
      category: '前端工程',
      minutesAgo: 1440,
      replies: 7,
      views: 203,
      likes: 11,
      solved: true,
    },
    {
      id: 10,
      title: 'Kubernetes 入门课的 Ingress 示例能在本地跑通吗？',
      excerpt:
        '没有云厂商的负载均衡，本地 kind 集群里 Ingress 一直 pending，课程里的示例需要额外装 controller 吗？',
      author: { name: '苏芮', avatar: `${AVATAR_BASE}avatar_teacher_female_01.jpg`, role: '讲师' },
      category: '云原生',
      minutesAgo: 1560,
      replies: 2,
      views: 88,
      likes: 3,
      solved: false,
    },
    {
      id: 11,
      title: '学完微服务之后该刷哪些题？求一份可执行的复习路线',
      excerpt:
        '课程知识点很多但不成体系，想按「网关 → 认证 → 事务 → 消息」的顺序整理一份复习清单，欢迎补充。',
      author: { name: '陈默', avatar: `${AVATAR_BASE}avatar_student_male_01.jpg`, role: '助教' },
      category: '职业成长',
      minutesAgo: 2880,
      replies: 15,
      views: 412,
      likes: 19,
      solved: false,
    },
    {
      id: 12,
      title: '怎么用 AI 助手把一章内容拆成每周可执行的学习计划？',
      excerpt:
        '试过直接让它排计划，结果太笼统。后来把课时时长和小测一起喂进去效果明显变好，分享下我用的提示词。',
      author: { name: '周老师', avatar: `${AVATAR_BASE}avatar_teacher_male_01.jpg`, role: '讲师' },
      category: 'AI 应用',
      minutesAgo: 4320,
      replies: 5,
      views: 156,
      likes: 13,
      solved: true,
    },
  ]

  return seed.map(({ minutesAgo, ...item }) => ({
    ...item,
    createdAt: minutes(minutesAgo),
    liked: false,
  }))
}

const filteredDiscussions = computed(() => {
  const list = discussions.value.filter((item) => {
    if (activeCategory.value !== '全部' && item.category !== activeCategory.value) return false
    if (activeTag.value) {
      const haystack = `${item.title}${item.excerpt}${item.category}`
      if (!haystack.includes(activeTag.value)) return false
    }
    if (activeTopic.value === 'unanswered') return !item.solved
    if (activeTopic.value === 'solved') return item.solved
    return true
  })

  if (activeTopic.value === 'hot') {
    return [...list].sort((a, b) => heatOf(b) - heatOf(a))
  }
  return [...list].sort((a, b) => b.createdAt - a.createdAt)
})

const totalReplies = computed(() =>
  discussions.value.reduce((sum, item) => sum + item.replies, 0)
)

const hasFilter = computed(
  () => activeTopic.value !== 'latest' || activeCategory.value !== '全部' || !!activeTag.value
)

const draftTitleLeft = computed(() => 60 - draft.title.length)

onMounted(() => {
  void loadFeed()
})

async function loadFeed() {
  state.value = 'loading'
  try {
    // 本地内容层：保留一帧异步间隙，让骨架屏与真实接口形态一致
    await new Promise((resolve) => window.setTimeout(resolve, 260))
    discussions.value = loadDiscussions()
    state.value = 'ready'
  } catch {
    discussions.value = []
    state.value = 'error'
  }
}

function heatOf(item: Discussion) {
  return item.likes * 3 + item.replies * 4 + item.views / 12
}

function relativeTime(timestamp: number) {
  const diff = Date.now() - timestamp
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(timestamp).toLocaleDateString('zh-CN')
}

function roleBadgeClass(role: string) {
  if (role === '讲师') return 'nm-badge--accent'
  if (role === '助教') return 'nm-badge--success'
  if (role === '版主') return 'nm-badge--warning'
  return 'nm-badge--neutral'
}

function toggleLike(item: Discussion) {
  item.liked = !item.liked
  item.likes += item.liked ? 1 : -1
}

function resetFilters() {
  activeTopic.value = 'latest'
  activeCategory.value = '全部'
  activeTag.value = ''
}

function selectTag(label: string) {
  activeTag.value = activeTag.value === label ? '' : label
  activeCategory.value = '全部'
  activeTopic.value = 'latest'
}

function openComposer(source?: Discussion) {
  draftError.value = ''
  if (source) {
    draft.title = source.title.startsWith('Re: ') ? source.title : `Re: ${source.title}`
    draft.category = source.category
    draft.body = ''
  } else {
    draft.title = ''
    draft.category = '后端架构'
    draft.body = ''
  }
  composerOpen.value = true
}

function submitDraft() {
  const title = draft.title.trim()
  const body = draft.body.trim()

  if (title.length < 6) {
    draftError.value = '标题至少 6 个字，让人一眼看懂你在问什么'
    return
  }
  if (body.length < 10) {
    draftError.value = '正文至少 10 个字，补充背景能让解答更准确'
    return
  }

  const name = userStore.userInfo?.username || '我'
  discussions.value = [
    {
      id: Date.now(),
      title,
      excerpt: body.length > 96 ? `${body.slice(0, 96)}…` : body,
      author: {
        name,
        avatar: userStore.userInfo?.avatar || DEFAULT_AVATAR,
        role: '学员',
      },
      category: draft.category,
      createdAt: Date.now(),
      replies: 0,
      views: 0,
      likes: 0,
      solved: false,
      liked: false,
    },
    ...discussions.value,
  ]

  composerOpen.value = false
  draftError.value = ''
  activeTopic.value = 'latest'
  activeCategory.value = '全部'
  ElMessage.success('讨论已发布')
}

function askAI(item: Discussion) {
  router.push({
    path: '/portal/ai-chat',
    query: {
      prompt: `请围绕这个社区问题给出清晰的解答：${item.title}`,
      autostart: '1',
    },
  })
}
</script>

<template>
  <div class="community-page nm-shell">
    <header class="community-head">
      <div class="community-head__copy">
        <span class="nm-kicker">学习社区</span>
        <h1 class="nm-h1">和同学一起把问题解决掉</h1>
        <p class="nm-lede">
          课程疑问、项目踩坑、学习方法都可以在这里提问。提问越具体，得到解答的速度越快。
        </p>
      </div>
      <div class="community-head__actions">
        <span class="head-stat">
          <strong class="nm-num">{{ discussions.length }}</strong>
          <em>讨论</em>
        </span>
        <span class="head-stat">
          <strong class="nm-num">{{ totalReplies }}</strong>
          <em>回答</em>
        </span>
        <button type="button" class="nm-btn nm-btn--primary" @click="openComposer()">
          <el-icon :size="15"><EditPen /></el-icon>
          发起讨论
        </button>
      </div>
    </header>

    <div class="filter-bar">
      <div class="topic-strip" role="tablist" aria-label="讨论排序">
        <button
          v-for="topic in topics"
          :key="topic.key"
          type="button"
          role="tab"
          :aria-selected="activeTopic === topic.key"
          :class="['topic-btn', { 'is-active': activeTopic === topic.key }]"
          :title="topic.hint"
          @click="activeTopic = topic.key"
        >
          {{ topic.label }}
        </button>
      </div>
      <div class="category-strip">
        <button
          v-for="category in categories"
          :key="category"
          type="button"
          :class="['nm-chip', { 'is-active': activeCategory === category }]"
          @click="activeCategory = category"
        >
          {{ category }}
        </button>
        <button
          v-if="activeTag"
          type="button"
          class="nm-chip is-active"
          title="清除标签筛选"
          @click="activeTag = ''"
        >
          #{{ activeTag }}
          <el-icon :size="12"><Close /></el-icon>
        </button>
      </div>
    </div>

    <div class="community-body">
      <section class="community-main">
        <!-- 加载骨架 -->
        <div v-if="state === 'loading'" class="feed-skeleton" aria-busy="true" aria-label="正在加载讨论">
          <div v-for="n in 4" :key="n" class="sk-card">
            <div class="sk-head">
              <span class="nm-skeleton sk-avatar"></span>
              <span class="sk-lines">
                <span class="nm-skeleton sk-line sk-line--sm"></span>
                <span class="nm-skeleton sk-line sk-line--xs"></span>
              </span>
            </div>
            <span class="nm-skeleton sk-line sk-line--lg"></span>
            <span class="nm-skeleton sk-line sk-line--md"></span>
            <span class="nm-skeleton sk-line sk-line--xs"></span>
          </div>
        </div>

        <!-- 错误态 -->
        <div v-else-if="state === 'error'" class="nm-state">
          <span class="nm-state__icon">
            <el-icon :size="24"><WarningFilled /></el-icon>
          </span>
          <p class="nm-state__title">讨论列表加载失败</p>
          <p class="nm-state__desc">内容服务暂时不可用，可以重试一次；已发布的内容不会丢失。</p>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary" @click="loadFeed()">
              <el-icon :size="15"><Refresh /></el-icon>
              重试
            </button>
          </div>
        </div>

        <!-- 空态 -->
        <div v-else-if="!filteredDiscussions.length" class="nm-state">
          <span class="nm-state__icon">
            <el-icon :size="24"><ChatLineSquare /></el-icon>
          </span>
          <p class="nm-state__title">
            {{ hasFilter ? '这个筛选条件下还没有讨论' : '还没有人发起讨论' }}
          </p>
          <p class="nm-state__desc">
            {{
              hasFilter
                ? '换一个话题或分类看看，也可以直接发起一条新的讨论。'
                : '把你的第一个问题写下来，同学和讲师都会看到。'
            }}
          </p>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary" @click="openComposer()">
              <el-icon :size="15"><EditPen /></el-icon>
              发起讨论
            </button>
            <button v-if="hasFilter" type="button" class="nm-btn" @click="resetFilters()">
              清除筛选
            </button>
          </div>
        </div>

        <!-- 讨论列表 -->
        <ul v-else class="feed-list">
          <li v-for="item in filteredDiscussions" :key="item.id">
            <article class="nm-card nm-card--hover discussion-card">
              <header class="dc-head">
                <img class="dc-avatar" :src="item.author.avatar" :alt="item.author.name" />
                <div class="dc-author">
                  <p class="dc-name">
                    <span>{{ item.author.name }}</span>
                    <span :class="['nm-badge', roleBadgeClass(item.author.role)]">
                      {{ item.author.role }}
                    </span>
                  </p>
                  <p class="dc-sub">
                    <span class="nm-tag dc-category">{{ item.category }}</span>
                    <span>{{ relativeTime(item.createdAt) }}</span>
                  </p>
                </div>
                <span v-if="item.solved" class="nm-badge nm-badge--success">已解决</span>
              </header>

              <h3 class="dc-title">{{ item.title }}</h3>
              <p class="dc-excerpt">{{ item.excerpt }}</p>

              <footer class="dc-foot">
                <div class="dc-meta">
                  <span class="dc-stat" title="回答数">
                    <el-icon :size="14"><ChatDotRound /></el-icon>
                    <span class="nm-num">{{ item.replies }}</span>
                  </span>
                  <span class="dc-stat" title="浏览数">
                    <el-icon :size="14"><View /></el-icon>
                    <span class="nm-num">{{ item.views }}</span>
                  </span>
                  <button
                    type="button"
                    :class="['dc-stat', 'dc-like', { 'is-liked': item.liked }]"
                    :aria-pressed="item.liked"
                    title="点赞"
                    @click="toggleLike(item)"
                  >
                    <el-icon :size="14">
                      <StarFilled v-if="item.liked" />
                      <Star v-else />
                    </el-icon>
                    <span class="nm-num">{{ item.likes }}</span>
                  </button>
                </div>
                <div class="dc-actions">
                  <button type="button" class="nm-btn nm-btn--ghost nm-btn--sm" @click="askAI(item)">
                    <el-icon :size="14"><MagicStick /></el-icon>
                    AI 解答
                  </button>
                  <button type="button" class="nm-btn nm-btn--sm" @click="openComposer(item)">
                    回答
                    <el-icon :size="14"><ArrowRight /></el-icon>
                  </button>
                </div>
              </footer>
            </article>
          </li>
        </ul>
      </section>

      <aside class="community-rail">
        <div class="nm-card nm-card--pad rail-card">
          <span class="nm-kicker">活跃贡献者</span>
          <ul class="contributor-list">
            <li v-for="person in contributors" :key="person.name" class="contributor">
              <img class="contributor__avatar" :src="person.avatar" :alt="person.name" />
              <div class="contributor__copy">
                <p class="contributor__name">{{ person.name }}</p>
                <p class="contributor__role">{{ person.role }}</p>
              </div>
              <span class="contributor__score nm-num">{{ person.score }}</span>
            </li>
          </ul>
        </div>

        <div class="nm-card nm-card--pad rail-card">
          <span class="nm-kicker">热门标签</span>
          <div class="tag-cloud">
            <button
              v-for="tag in hotTags"
              :key="tag.label"
              type="button"
              :class="['tag-item', { 'is-active': activeTag === tag.label }]"
              @click="selectTag(tag.label)"
            >
              <span>{{ tag.label }}</span>
              <span class="tag-item__count nm-num">{{ tag.count }}</span>
            </button>
          </div>
        </div>

        <div class="nm-card nm-card--pad nm-card--quiet rail-card rail-card--rules">
          <span class="nm-kicker">提问小贴士</span>
          <ul class="rules">
            <li>标题写清场景，例如「网关鉴权后下游拿不到 user-info」。</li>
            <li>附上报错日志、版本号与已经试过的方案。</li>
            <li>问题解决后把结论补充回帖，方便后来人检索。</li>
          </ul>
        </div>
      </aside>
    </div>

    <el-dialog
      v-model="composerOpen"
      title="发起讨论"
      width="min(560px, 92vw)"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="composer">
        <label class="composer-field">
          <span class="composer-label">
            标题
            <em class="nm-num">{{ draftTitleLeft }}</em>
          </span>
          <el-input
            v-model="draft.title"
            maxlength="60"
            placeholder="一句话说清你的问题，例如：Nacos 注册不上该怎么排查？"
          />
        </label>

        <label class="composer-field">
          <span class="composer-label">分类</span>
          <el-select v-model="draft.category" placeholder="选择分类">
            <el-option
              v-for="category in categories.filter((item) => item !== '全部')"
              :key="category"
              :label="category"
              :value="category"
            />
          </el-select>
        </label>

        <label class="composer-field">
          <span class="composer-label">正文</span>
          <el-input
            v-model="draft.body"
            type="textarea"
            :rows="6"
            maxlength="800"
            show-word-limit
            placeholder="补充背景、报错信息和你已经尝试过的方案，解答会更准确。"
          />
        </label>

        <p v-if="draftError" class="composer-error">{{ draftError }}</p>
      </div>

      <template #footer>
        <div class="composer-footer">
          <button type="button" class="nm-btn" @click="composerOpen = false">取消</button>
          <button type="button" class="nm-btn nm-btn--primary" @click="submitDraft()">发布讨论</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.community-page {
  padding-block: 12px clamp(48px, 6vw, 80px);
}

/* --- 页头 --- */
.community-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: clamp(18px, 2.2vw, 26px);
  border-bottom: 1px solid var(--nm-line);
}

.community-head__copy {
  display: grid;
  gap: 10px;
  max-width: 62ch;
}

.community-head__actions {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.head-stat {
  display: grid;
  gap: 2px;
  text-align: right;
}

.head-stat strong {
  font-size: 1.35rem;
  font-weight: 750;
  line-height: 1;
  color: var(--nm-ink);
}

.head-stat em {
  font-style: normal;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* --- 筛选条 --- */
.filter-bar {
  display: grid;
  gap: 14px;
  padding-block: clamp(16px, 2vw, 22px);
  margin-bottom: clamp(18px, 2.4vw, 26px);
  border-bottom: 1px solid var(--nm-line);
}

.topic-strip {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-2);
  width: fit-content;
  max-width: 100%;
  overflow-x: auto;
}

.topic-btn {
  padding: 7px 18px;
  border: 0;
  border-radius: var(--nm-r-full);
  background: transparent;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-sm);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.topic-btn:hover {
  color: var(--nm-ink);
}

.topic-btn.is-active {
  background: var(--nm-ink);
  color: var(--nm-paper);
}

.category-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* --- 两栏 --- */
.community-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  gap: clamp(20px, 2.6vw, 32px);
  align-items: start;
}

.community-main {
  min-width: 0;
}

/* --- 讨论卡 --- */
.feed-list {
  list-style: none;
  display: grid;
  gap: 14px;
}

.discussion-card {
  padding: clamp(16px, 1.9vw, 22px);
  display: grid;
  gap: 12px;
}

.dc-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dc-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.dc-author {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 3px;
}

.dc-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-ink);
}

.dc-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.dc-category {
  padding: 2px 9px;
  font-size: 11px;
}

.dc-title {
  font-size: 1.0625rem;
  font-weight: 720;
  line-height: 1.45;
  color: var(--nm-ink);
  transition: color var(--nm-dur-fast) var(--nm-ease);
}

.discussion-card:hover .dc-title {
  color: var(--nm-accent);
}

.dc-excerpt {
  font-size: var(--nm-fs-sm);
  line-height: 1.7;
  color: var(--nm-ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--nm-line);
}

.dc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dc-stat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-full);
  background: transparent;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
}

.dc-like {
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.dc-like:hover {
  background: var(--nm-surface-3);
  color: var(--nm-ink);
}

.dc-like.is-liked {
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.dc-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* --- 右栏 --- */
.community-rail {
  position: sticky;
  top: 96px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  display: grid;
  gap: 16px;
  min-width: 0;
}

.rail-card {
  display: grid;
  gap: 16px;
}

.contributor-list {
  list-style: none;
  display: grid;
  gap: 14px;
}

.contributor {
  display: flex;
  align-items: center;
  gap: 12px;
}

.contributor__avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--nm-r-full);
  object-fit: cover;
  border: 1px solid var(--nm-line);
  background: var(--nm-surface-3);
}

.contributor__copy {
  flex: 1;
  min-width: 0;
}

.contributor__name {
  font-size: var(--nm-fs-sm);
  font-weight: 650;
  color: var(--nm-ink);
}

.contributor__role {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.contributor__score {
  font-size: var(--nm-fs-sm);
  font-weight: 700;
  color: var(--nm-accent);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface);
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-xs);
  font-weight: 550;
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.tag-item:hover {
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.tag-item__count {
  color: var(--nm-ink-4);
  font-size: 11px;
}

.tag-item:hover .tag-item__count {
  color: var(--nm-accent);
}

.tag-item.is-active {
  border-color: var(--nm-ink);
  background: var(--nm-ink);
  color: var(--nm-paper);
}

.tag-item.is-active .tag-item__count {
  color: var(--nm-paper);
}

.rail-card--rules {
  border-style: dashed;
}

.rules {
  list-style: none;
  display: grid;
  gap: 10px;
}

.rules li {
  position: relative;
  padding-left: 16px;
  font-size: var(--nm-fs-xs);
  line-height: 1.65;
  color: var(--nm-ink-3);
}

.rules li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.62em;
  width: 6px;
  height: 1px;
  background: var(--nm-accent);
}

/* --- 骨架 --- */
.feed-skeleton {
  display: grid;
  gap: 14px;
}

.sk-card {
  display: grid;
  gap: 12px;
  padding: clamp(16px, 1.9vw, 22px);
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-surface);
}

.sk-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sk-avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--nm-r-full);
}

.sk-lines {
  display: grid;
  gap: 8px;
  flex: 1;
}

.sk-line {
  height: 12px;
}

.sk-line--lg {
  width: 72%;
}

.sk-line--md {
  width: 88%;
}

.sk-line--sm {
  width: 40%;
}

.sk-line--xs {
  width: 22%;
}

/* --- 发布弹窗 --- */
.composer {
  display: grid;
  gap: 18px;
}

.composer-field {
  display: grid;
  gap: 8px;
}

.composer-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: var(--nm-fs-sm);
  font-weight: 650;
  color: var(--nm-ink-2);
}

.composer-label em {
  font-style: normal;
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  color: var(--nm-ink-4);
}

.composer-error {
  padding: 10px 14px;
  border-radius: var(--nm-r-sm);
  background: var(--nm-danger-soft);
  color: var(--nm-danger);
  font-size: var(--nm-fs-sm);
}

.composer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.composer :deep(.el-select) {
  width: 100%;
}

/* --- 响应式 --- */
@media (max-width: 1024px) {
  .community-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .community-rail {
    position: static;
    max-height: none;
    overflow: visible;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .community-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .head-stat {
    text-align: left;
  }

  .community-rail {
    grid-template-columns: minmax(0, 1fr);
  }

  .dc-foot {
    flex-direction: column;
    align-items: flex-start;
  }

  .dc-actions {
    width: 100%;
  }
}
</style>

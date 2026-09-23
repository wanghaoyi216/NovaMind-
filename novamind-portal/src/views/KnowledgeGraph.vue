<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { DataSet } from 'vis-data'
import { Network } from 'vis-network'
import type { Edge, Node, Options } from 'vis-network'
import {
  Aim,
  FullScreen,
  Link,
  Refresh,
  Search,
  WarningFilled,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import {
  getGraphStatus,
  getGraphVisualization,
  type GraphData,
  type GraphNode,
  type GraphStatus,
} from '@/api/graph'

interface NodeTypeMeta {
  key: string
  label: string
  /** 语义令牌名，画布与图例都从这里取色 */
  colorToken: string
  softToken: string
  shape: string
  size: number
}

interface Palette {
  ink: string
  ink3: string
  surface: string
  surface3: string
  line: string
  lineStrong: string
  accent: string
  cyan: string
  warning: string
  success: string
}

interface ConnectionItem {
  key: string
  id: string
  label: string
  type: string
  relation: string
}

const NODE_TYPES: NodeTypeMeta[] = [
  {
    key: 'USER',
    label: '学员节点',
    colorToken: '--nm-accent',
    softToken: '--nm-accent-soft',
    shape: 'star',
    size: 30,
  },
  {
    key: 'INTERACTION',
    label: '互动节点',
    colorToken: '--nm-cyan',
    softToken: '--nm-cyan-soft',
    shape: 'dot',
    size: 22,
  },
  {
    key: 'MEDIA',
    label: '资源节点',
    colorToken: '--nm-warning',
    softToken: '--nm-warning-soft',
    shape: 'diamond',
    size: 20,
  },
  {
    key: 'PREFERENCE',
    label: '偏好节点',
    colorToken: '--nm-success',
    softToken: '--nm-success-soft',
    shape: 'square',
    size: 18,
  },
]

const OTHER_TYPE: NodeTypeMeta = {
  key: 'OTHER',
  label: '其他节点',
  colorToken: '--nm-ink-3',
  softToken: '--nm-surface-3',
  shape: 'dot',
  size: 18,
}

const containerRef = shallowRef<HTMLDivElement>()

const loading = ref(true)
const failed = ref(false)
const enabled = ref(false)
const statusInfo = ref<GraphStatus | null>(null)
const graphData = ref<GraphData>({ nodes: [], edges: [] })
const demoMode = ref(false)
const searchTerm = ref('')
const hiddenTypes = ref<string[]>([])
const selectedId = ref('')
const zoomLevel = ref(1)

let network: Network | null = null
let nodesDataSet: DataSet<Node> | null = null
let edgesDataSet: DataSet<Edge> | null = null
let themeObserver: MutationObserver | null = null

const typeById = computed(() => {
  const map = new Map<string, string>()
  graphData.value.nodes.forEach((node) => map.set(node.id, node.type))
  return map
})

const nodeById = computed(() => {
  const map = new Map<string, GraphNode>()
  graphData.value.nodes.forEach((node) => map.set(node.id, node))
  return map
})

const presentTypes = computed<NodeTypeMeta[]>(() => {
  const keys = Array.from(new Set(graphData.value.nodes.map((node) => node.type)))
  return keys.map((key) => typeMetaOf(key))
})

const legendTypes = computed<NodeTypeMeta[]>(() =>
  presentTypes.value.length ? presentTypes.value : NODE_TYPES
)

const showCanvas = computed(
  () => !loading.value && enabled.value && graphData.value.nodes.length > 0
)

const viewState = computed<'loading' | 'canvas' | 'empty' | 'disabled'>(() => {
  if (loading.value) return 'loading'
  if (showCanvas.value) return 'canvas'
  if (enabled.value) return 'empty'
  return 'disabled'
})

const searchActive = computed(() => searchTerm.value.trim().length > 0)

const matchedIds = computed(() => {
  const term = searchTerm.value.trim().toLowerCase()
  if (!term) return []
  return graphData.value.nodes
    .filter((node) => `${node.label}${node.type}`.toLowerCase().includes(term))
    .map((node) => node.id)
})

const selectedNode = computed<GraphNode | null>(() => {
  if (!selectedId.value) return null
  return nodeById.value.get(selectedId.value) ?? null
})

const connections = computed<ConnectionItem[]>(() => {
  const current = selectedId.value
  if (!current) return []
  return graphData.value.edges
    .filter((edge) => edge.from === current || edge.to === current)
    .map((edge) => {
      const otherId = edge.from === current ? edge.to : edge.from
      const node = nodeById.value.get(otherId)
      if (!node) return null
      return {
        key: `${node.id}-${edge.label || 'link'}-${otherId === edge.from ? 'in' : 'out'}`,
        id: node.id,
        label: node.label,
        type: node.type,
        relation: edge.label || '关联',
      }
    })
    .filter((item): item is ConnectionItem => item !== null)
})

const visibleTypeCount = computed(
  () => legendTypes.value.filter((type) => !hiddenTypes.value.includes(type.key)).length
)

onMounted(() => {
  void loadGraph()
  themeObserver = new MutationObserver(() => {
    refreshTheme()
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
})

onUnmounted(() => {
  themeObserver?.disconnect()
  themeObserver = null
  destroyNetwork()
})

watch([showCanvas, graphData], async ([visible]) => {
  if (visible) {
    await nextTick()
    buildNetwork()
    return
  }
  destroyNetwork()
})

watch([hiddenTypes, matchedIds], () => {
  applyNodeStyles()
})

/* ------------------------------------------------------------
   主题取色：所有画布颜色都从 design-system 的语义令牌读取，
   因此浅色 / 深色主题切换后画布与图例同步变化。
   ------------------------------------------------------------ */
function readToken(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function readPalette(): Palette {
  return {
    ink: readToken('--nm-ink'),
    ink3: readToken('--nm-ink-3'),
    surface: readToken('--nm-surface'),
    surface3: readToken('--nm-surface-3'),
    line: readToken('--nm-line'),
    lineStrong: readToken('--nm-line-strong') || readToken('--nm-line'),
    accent: readToken('--nm-accent'),
    cyan: readToken('--nm-cyan'),
    warning: readToken('--nm-warning'),
    success: readToken('--nm-success'),
  }
}

function tokenValue(token: string, palette: Palette) {
  if (token === '--nm-ink') return palette.ink
  if (token === '--nm-ink-3') return palette.ink3
  if (token === '--nm-accent') return palette.accent
  if (token === '--nm-cyan') return palette.cyan
  if (token === '--nm-warning') return palette.warning
  if (token === '--nm-success') return palette.success
  if (token === '--nm-surface-3') return palette.surface3
  return palette.lineStrong
}

function typeMetaOf(key: string): NodeTypeMeta {
  return NODE_TYPES.find((type) => type.key === key) ?? { ...OTHER_TYPE, key, label: key }
}

function typeLabelOf(key: string) {
  return typeMetaOf(key).label
}

function typeColorOf(key: string, palette: Palette) {
  return tokenValue(typeMetaOf(key).colorToken, palette)
}

/* ------------------------------------------------------------
   数据加载
   ------------------------------------------------------------ */
async function loadGraph() {
  loading.value = true
  failed.value = false
  selectedId.value = ''
  hiddenTypes.value = []
  searchTerm.value = ''

  try {
    const status = await getGraphStatus()
    statusInfo.value = status
    enabled.value = !!status?.enabled
    if (!enabled.value) {
      graphData.value = { nodes: [], edges: [] }
      return
    }
    const data = await getGraphVisualization()
    graphData.value = {
      nodes: Array.isArray(data?.nodes) ? data.nodes : [],
      edges: Array.isArray(data?.edges) ? data.edges : [],
    }
    demoMode.value = false
  } catch {
    failed.value = true
    enabled.value = false
    statusInfo.value = null
    graphData.value = { nodes: [], edges: [] }
  } finally {
    loading.value = false
  }
}

function loadDemoGraph() {
  graphData.value = demoGraphData()
  demoMode.value = true
  failed.value = false
  enabled.value = true
  loading.value = false
  hiddenTypes.value = []
  selectedId.value = ''
  searchTerm.value = ''
}

function demoGraphData(): GraphData {
  const nodes: GraphNode[] = [
    { id: 'u1', label: '学员 · 我', type: 'USER' },
    { id: 'i1', label: '提问：网关鉴权排查', type: 'INTERACTION' },
    { id: 'i2', label: '提问：提示词模板', type: 'INTERACTION' },
    { id: 'i3', label: '对话：学习计划拆解', type: 'INTERACTION' },
    { id: 'i4', label: '测验：微服务基础', type: 'INTERACTION' },
    { id: 'm1', label: '课程：Spring Cloud 微服务', type: 'MEDIA' },
    { id: 'm2', label: '课程：AI 应用与提示工程', type: 'MEDIA' },
    { id: 'm3', label: '课程：Vue 3 组件化实战', type: 'MEDIA' },
    { id: 'm4', label: '文档：网关鉴权设计说明', type: 'MEDIA' },
    { id: 'm5', label: '视频：Nacos 配置中心', type: 'MEDIA' },
    { id: 'p1', label: '偏好：后端架构', type: 'PREFERENCE' },
    { id: 'p2', label: '偏好：AI 应用', type: 'PREFERENCE' },
    { id: 'p3', label: '偏好：前端工程', type: 'PREFERENCE' },
    { id: 'p4', label: '偏好：每日 40 分钟', type: 'PREFERENCE' },
  ]
  const edges = [
    { from: 'u1', to: 'i1', label: '提问' },
    { from: 'u1', to: 'i2', label: '提问' },
    { from: 'u1', to: 'i3', label: '对话' },
    { from: 'u1', to: 'i4', label: '测验' },
    { from: 'i1', to: 'm1', label: '关联' },
    { from: 'i2', to: 'm2', label: '关联' },
    { from: 'i3', to: 'm2', label: '关联' },
    { from: 'i4', to: 'm1', label: '关联' },
    { from: 'u1', to: 'm1', label: '学习' },
    { from: 'u1', to: 'm2', label: '学习' },
    { from: 'u1', to: 'm3', label: '浏览' },
    { from: 'm1', to: 'm4', label: '资料' },
    { from: 'm1', to: 'm5', label: '资料' },
    { from: 'u1', to: 'p1', label: '偏好' },
    { from: 'u1', to: 'p2', label: '偏好' },
    { from: 'u1', to: 'p3', label: '偏好' },
    { from: 'u1', to: 'p4', label: '偏好' },
  ]
  return { nodes, edges }
}

/* ------------------------------------------------------------
   画布构建
   ------------------------------------------------------------ */
function edgeIdOf(from: string, to: string, index: number) {
  return `${from}->${to}#${index}`
}

function buildOptions(palette: Palette): Options {
  const fontColor = palette.ink || undefined
  return {
    autoResize: true,
    nodes: {
      shape: 'dot',
      borderWidth: 2,
      borderWidthSelected: 4,
      font: {
        size: 12,
        color: fontColor,
        strokeWidth: 3,
        strokeColor: palette.surface || undefined,
      },
    },
    edges: {
      arrows: { to: { enabled: true, scaleFactor: 0.55 } },
      color: {
        color: palette.lineStrong || undefined,
        highlight: palette.accent || undefined,
        hover: palette.accent || undefined,
      },
      font: {
        size: 10,
        color: palette.ink3 || undefined,
        strokeWidth: 3,
        strokeColor: palette.surface || undefined,
        align: 'middle',
      },
      width: 1,
      smooth: { enabled: true, type: 'curvedCW', roundness: 0.16 },
    },
    physics: {
      solver: 'forceAtlas2Based',
      forceAtlas2Based: {
        gravitationalConstant: -48,
        centralGravity: 0.006,
        springLength: 170,
        springConstant: 0.02,
        damping: 0.42,
      },
      stabilization: { iterations: 220, fit: true },
    },
    interaction: {
      hover: true,
      tooltipDelay: 180,
      zoomView: true,
      dragView: true,
    },
    layout: { improvedLayout: true },
  }
}

function buildNetwork() {
  const container = containerRef.value
  if (!container) return

  destroyNetwork()

  const palette = readPalette()
  container.style.background = palette.surface

  nodesDataSet = new DataSet<Node>(
    graphData.value.nodes.map((node) => {
      const meta = typeMetaOf(node.type)
      return {
        id: node.id,
        label: node.label,
        group: node.type,
        shape: meta.shape,
        size: meta.size,
        title: `${node.label}（${typeLabelOf(node.type)}）`,
        borderWidth: 2,
        color: {
          background: typeColorOf(node.type, palette),
          border: palette.surface,
          highlight: { background: typeColorOf(node.type, palette), border: palette.ink },
        },
        font: { color: palette.ink || undefined, size: 12 },
      }
    })
  )

  edgesDataSet = new DataSet<Edge>(
    graphData.value.edges.map((edge, index) => ({
      id: edgeIdOf(edge.from, edge.to, index),
      from: edge.from,
      to: edge.to,
      label: edge.label,
      title: edge.label,
    }))
  )

  network = new Network(
    container,
    { nodes: nodesDataSet, edges: edgesDataSet },
    buildOptions(palette)
  )

  network.on('selectNode', (params: { nodes: Array<string | number> }) => {
    const first = params?.nodes?.[0]
    selectedId.value = first === undefined || first === null ? '' : String(first)
  })

  network.on('deselectNode', () => {
    selectedId.value = ''
  })

  network.on('zoom', () => {
    zoomLevel.value = Number(network?.getScale() ?? 1)
  })

  network.once('stabilizationIterationsDone', () => {
    network?.setOptions({ physics: { enabled: false } })
    network?.fit({ animation: { duration: 320, easingFunction: 'easeInOutQuad' } })
  })

  applyNodeStyles()
}

function destroyNetwork() {
  network?.destroy()
  network = null
  nodesDataSet = null
  edgesDataSet = null
  zoomLevel.value = 1
}

/** 类型筛选 + 搜索高亮：只更新节点/边的样式，保留已有布局位置 */
function applyNodeStyles() {
  if (!nodesDataSet || !edgesDataSet) return

  const palette = readPalette()
  const searching = searchActive.value
  const matched = new Set(matchedIds.value)

  nodesDataSet.update(
    graphData.value.nodes.map((node) => {
      const meta = typeMetaOf(node.type)
      const isMatch = matched.has(node.id)
      const dimmed = searching && !isMatch
      const baseColor = typeColorOf(node.type, palette)

      return {
        id: node.id,
        hidden: hiddenTypes.value.includes(node.type),
        size: dimmed ? Math.max(8, meta.size * 0.66) : isMatch ? meta.size * 1.4 : meta.size,
        borderWidth: isMatch ? 4 : 2,
        color: dimmed
          ? {
              background: palette.surface3,
              border: palette.line,
              highlight: { background: palette.surface3, border: palette.line },
            }
          : {
              background: baseColor,
              border: isMatch ? palette.ink : palette.surface,
              highlight: { background: baseColor, border: palette.ink },
            },
        font: {
          color: (dimmed ? palette.ink3 : palette.ink) || undefined,
          size: 12,
        },
      }
    })
  )

  edgesDataSet.update(
    graphData.value.edges.map((edge, index) => {
      const hidden =
        hiddenTypes.value.includes(typeById.value.get(edge.from) ?? '') ||
        hiddenTypes.value.includes(typeById.value.get(edge.to) ?? '')
      return {
        id: edgeIdOf(edge.from, edge.to, index),
        hidden,
        color: {
          color: palette.lineStrong || undefined,
          highlight: palette.accent || undefined,
          hover: palette.accent || undefined,
        },
      }
    })
  )
}

function refreshTheme() {
  const palette = readPalette()
  if (containerRef.value) {
    containerRef.value.style.background = palette.surface
  }
  if (network) {
    network.setOptions(buildOptions(palette))
  }
  applyNodeStyles()
}

/* ------------------------------------------------------------
   交互
   ------------------------------------------------------------ */
function toggleType(key: string) {
  hiddenTypes.value = hiddenTypes.value.includes(key)
    ? hiddenTypes.value.filter((item) => item !== key)
    : [...hiddenTypes.value, key]
}

function showAllTypes() {
  hiddenTypes.value = []
}

function focusNode(id: string) {
  selectedId.value = id
  network?.selectNodes([id])
  network?.focus(id, {
    scale: 1.15,
    animation: { duration: 380, easingFunction: 'easeInOutQuad' },
  })
}

function clearSelection() {
  selectedId.value = ''
  network?.unselectAll()
}

function zoomIn() {
  if (!network) return
  network.moveTo({
    scale: Math.min(network.getScale() * 1.25, 3),
    animation: { duration: 220, easingFunction: 'easeInOutQuad' },
  })
}

function zoomOut() {
  if (!network) return
  network.moveTo({
    scale: Math.max(network.getScale() * 0.8, 0.2),
    animation: { duration: 220, easingFunction: 'easeInOutQuad' },
  })
}

function fitView() {
  network?.fit({ animation: { duration: 340, easingFunction: 'easeInOutQuad' } })
}

function clearSearch() {
  searchTerm.value = ''
}
</script>

<template>
  <div class="graph-page nm-shell">
    <header class="graph-head">
      <div class="graph-head__copy">
        <span class="nm-kicker">知识图谱</span>
        <h1 class="nm-h1">学习关系图谱</h1>
        <p class="nm-lede">
          把课程、AI 交互与学习偏好连成一张网，看看知识之间是怎么长出来的。
        </p>
      </div>
      <div class="graph-head__stats">
        <div class="head-stat">
          <strong class="nm-num">{{ graphData.nodes.length }}</strong>
          <span>节点</span>
        </div>
        <div class="head-stat">
          <strong class="nm-num">{{ graphData.edges.length }}</strong>
          <span>关系</span>
        </div>
        <div class="head-stat">
          <strong class="nm-num">{{ legendTypes.length }}</strong>
          <span>类型</span>
        </div>
      </div>
    </header>

    <div class="graph-layout">
      <section class="graph-stage">
        <!-- 工具条 -->
        <div v-if="viewState === 'canvas'" class="graph-toolbar nm-card">
          <div class="search-box">
            <el-input
              v-model="searchTerm"
              :prefix-icon="Search"
              placeholder="搜索节点名称，例如「网关」"
              clearable
              @clear="clearSearch"
            />
            <span v-if="searchActive" class="search-result">
              <template v-if="matchedIds.length">
                匹配 <strong class="nm-num">{{ matchedIds.length }}</strong> 个节点
              </template>
              <template v-else>没有匹配的节点</template>
            </span>
          </div>

          <div class="zoom-group">
            <button type="button" class="zoom-btn" title="缩小" @click="zoomOut()">
              <el-icon :size="16"><ZoomOut /></el-icon>
            </button>
            <span class="zoom-value nm-num">{{ Math.round(zoomLevel * 100) }}%</span>
            <button type="button" class="zoom-btn" title="放大" @click="zoomIn()">
              <el-icon :size="16"><ZoomIn /></el-icon>
            </button>
            <button type="button" class="zoom-btn" title="适应画布" @click="fitView()">
              <el-icon :size="16"><FullScreen /></el-icon>
            </button>
            <button
              type="button"
              class="zoom-btn"
              title="重新加载图谱"
              @click="loadGraph()"
            >
              <el-icon :size="16"><Refresh /></el-icon>
            </button>
          </div>
        </div>

        <!-- 加载骨架 -->
        <div v-if="viewState === 'loading'" class="nm-card graph-canvas">
          <div class="canvas-skeleton">
            <span class="nm-skeleton sk-node sk-node--a"></span>
            <span class="nm-skeleton sk-node sk-node--b"></span>
            <span class="nm-skeleton sk-node sk-node--c"></span>
            <span class="nm-skeleton sk-node sk-node--d"></span>
            <span class="nm-skeleton sk-node sk-node--e"></span>
            <span class="nm-skeleton sk-line"></span>
          </div>
        </div>

        <!-- 未启用 / 连接失败 -->
        <div v-else-if="viewState === 'disabled'" class="nm-state graph-disabled">
          <span class="nm-state__icon">
            <el-icon :size="24"><WarningFilled /></el-icon>
          </span>
          <p class="nm-state__title">
            {{ failed ? '图谱服务暂时连不上' : '图谱服务尚未启用' }}
          </p>
          <p class="nm-state__desc">
            {{
              failed
                ? '没有取到图谱状态，通常是后端服务或网络不可用。重试即可，也可以先用示例数据看看图谱的呈现方式。'
                : '后端图数据库（Neo4j）还没有就绪，因此暂时没有可视化的学习网络。你仍然可以先了解图谱包含的节点类型与交互方式。'
            }}
          </p>

          <div class="disabled-legend">
            <span
              v-for="type in legendTypes"
              :key="type.key"
              class="legend-chip"
              :style="{
                color: `var(${type.colorToken})`,
                backgroundColor: `var(${type.softToken})`,
              }"
            >
              {{ type.label }}
            </span>
          </div>

          <div class="disabled-hints">
            <span>拖拽平移</span>
            <span>滚轮缩放</span>
            <span>点击查看关联</span>
            <span>搜索高亮节点</span>
          </div>

          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary" @click="loadGraph()">
              <el-icon :size="15"><Refresh /></el-icon>
              重试
            </button>
            <button type="button" class="nm-btn" @click="loadDemoGraph()">预览示例图谱</button>
          </div>

          <p v-if="statusInfo?.database" class="disabled-meta nm-num">
            目标库：{{ statusInfo.database }}
          </p>
        </div>

        <!-- 已启用但没有数据 -->
        <div v-else-if="viewState === 'empty'" class="nm-state graph-disabled">
          <span class="nm-state__icon">
            <el-icon :size="24"><Link /></el-icon>
          </span>
          <p class="nm-state__title">图谱里还没有节点</p>
          <p class="nm-state__desc">
            图数据库已连接，但还没有写入你的学习行为。多学几节课、和 AI 助手聊几轮，节点就会慢慢长出来。
          </p>
          <div class="disabled-legend">
            <span
              v-for="type in legendTypes"
              :key="type.key"
              class="legend-chip"
              :style="{
                color: `var(${type.colorToken})`,
                backgroundColor: `var(${type.softToken})`,
              }"
            >
              {{ type.label }}
            </span>
          </div>
          <div class="nm-state__actions">
            <button type="button" class="nm-btn nm-btn--primary" @click="loadGraph()">
              <el-icon :size="15"><Refresh /></el-icon>
              重新加载
            </button>
            <button type="button" class="nm-btn" @click="loadDemoGraph()">预览示例图谱</button>
          </div>
        </div>

        <!-- 画布 -->
        <div v-else class="nm-card graph-canvas">
          <div ref="containerRef" class="graph-canvas__inner"></div>
          <span v-if="demoMode" class="nm-badge nm-badge--warning graph-canvas__flag">
            示例数据
          </span>
        </div>
      </section>

      <aside class="graph-side">
        <!-- 图例 / 类型筛选 -->
        <div class="nm-card nm-card--pad side-card">
          <div class="side-card__head">
            <span class="nm-kicker">节点类型</span>
            <button
              v-if="hiddenTypes.length"
              type="button"
              class="side-reset"
              @click="showAllTypes()"
            >
              全部显示
            </button>
          </div>
          <p class="side-hint">点击图例可以隐藏 / 显示该类型，当前显示 {{ visibleTypeCount }} 类。</p>
          <ul class="legend-list">
            <li v-for="type in legendTypes" :key="type.key">
              <button
                type="button"
                :class="['legend-item', { 'is-off': hiddenTypes.includes(type.key) }]"
                @click="toggleType(type.key)"
              >
                <span
                  class="legend-dot"
                  :style="{ backgroundColor: `var(${type.colorToken})` }"
                ></span>
                <span class="legend-label">{{ type.label }}</span>
                <span class="legend-count nm-num">
                  {{ graphData.nodes.filter((node) => node.type === type.key).length }}
                </span>
              </button>
            </li>
          </ul>
        </div>

        <!-- 选中节点详情 -->
        <div class="nm-card nm-card--pad side-card">
          <span class="nm-kicker">节点详情</span>

          <template v-if="selectedNode">
            <div class="node-head">
              <span
                class="legend-dot legend-dot--lg"
                :style="{ backgroundColor: `var(${typeMetaOf(selectedNode.type).colorToken})` }"
              ></span>
              <div class="node-head__copy">
                <h3 class="node-title">{{ selectedNode.label }}</h3>
                <p class="node-type">{{ typeLabelOf(selectedNode.type) }}</p>
              </div>
            </div>

            <p class="node-id nm-num">ID · {{ selectedNode.id }}</p>

            <div class="node-connections">
              <span class="nm-kicker nm-kicker--plain">
                关联节点（{{ connections.length }}）
              </span>
              <ul v-if="connections.length" class="connection-list">
                <li v-for="item in connections" :key="item.key">
                  <button type="button" class="connection" @click="focusNode(item.id)">
                    <span
                      class="legend-dot legend-dot--sm"
                      :style="{ backgroundColor: `var(${typeMetaOf(item.type).colorToken})` }"
                    ></span>
                    <span class="connection__label">{{ item.label }}</span>
                    <span class="connection__rel">{{ item.relation }}</span>
                  </button>
                </li>
              </ul>
              <p v-else class="side-hint">这个节点目前没有关联关系。</p>
            </div>

            <button type="button" class="nm-btn nm-btn--ghost nm-btn--sm" @click="clearSelection()">
              取消选中
            </button>
          </template>

          <p v-else class="side-hint">
            在图谱中点击任意节点，这里会显示它的类型、ID 与全部关联节点。
          </p>

          <hr class="nm-divider" />

          <ul class="hint-list">
            <li><el-icon :size="13"><Aim /></el-icon>拖拽平移画布，滚轮缩放</li>
            <li><el-icon :size="13"><Search /></el-icon>搜索会在画布上高亮匹配节点</li>
            <li><el-icon :size="13"><Link /></el-icon>点击关联节点可跳转并聚焦</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.graph-page {
  padding-block: 12px clamp(48px, 6vw, 80px);
}

/* --- 页头 --- */
.graph-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: clamp(18px, 2.2vw, 26px);
  border-bottom: 1px solid var(--nm-line);
  margin-bottom: clamp(20px, 2.6vw, 30px);
}

.graph-head__copy {
  display: grid;
  gap: 10px;
  max-width: 60ch;
}

.graph-head__stats {
  display: flex;
  gap: 10px;
}

.head-stat {
  min-width: 84px;
  padding: 10px 14px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r);
  background: var(--nm-surface-2);
  text-align: center;
}

.head-stat strong {
  display: block;
  font-size: 1.25rem;
  font-weight: 750;
  line-height: 1.2;
  color: var(--nm-ink);
}

.head-stat span {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* --- 布局 --- */
.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 312px;
  gap: clamp(18px, 2.4vw, 28px);
  align-items: start;
}

.graph-stage {
  min-width: 0;
  display: grid;
  gap: 14px;
}

/* --- 工具条 --- */
.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  flex-wrap: wrap;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 240px;
}

.search-box :deep(.el-input) {
  max-width: 340px;
}

.search-result {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
  white-space: nowrap;
}

.search-result strong {
  color: var(--nm-ink);
}

.zoom-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-full);
  background: var(--nm-surface-2);
}

.zoom-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-full);
  background: transparent;
  color: var(--nm-ink-2);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.zoom-btn:hover:not(:disabled) {
  background: var(--nm-surface);
  border-color: var(--nm-line);
  color: var(--nm-accent);
}

.zoom-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.zoom-value {
  min-width: 48px;
  text-align: center;
  font-size: var(--nm-fs-xs);
  font-weight: 650;
  color: var(--nm-ink-3);
}

/* --- 画布 --- */
.graph-canvas {
  position: relative;
  overflow: hidden;
  min-height: 560px;
}

.graph-canvas__inner {
  width: 100%;
  height: clamp(520px, 62vh, 680px);
  background: var(--nm-surface);
}

.graph-canvas__flag {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}

.canvas-skeleton {
  position: relative;
  height: clamp(520px, 62vh, 680px);
  background: var(--nm-surface-2);
}

.sk-node {
  position: absolute;
  border-radius: var(--nm-r-full);
}

.sk-node--a {
  width: 44px;
  height: 44px;
  left: 46%;
  top: 44%;
}

.sk-node--b {
  width: 30px;
  height: 30px;
  left: 22%;
  top: 26%;
}

.sk-node--c {
  width: 30px;
  height: 30px;
  left: 72%;
  top: 30%;
}

.sk-node--d {
  width: 26px;
  height: 26px;
  left: 30%;
  top: 70%;
}

.sk-node--e {
  width: 26px;
  height: 26px;
  left: 68%;
  top: 68%;
}

.sk-line {
  position: absolute;
  left: 20%;
  right: 20%;
  bottom: 16%;
  height: 8px;
  border-radius: var(--nm-r-full);
}

/* --- 未启用态 --- */
.graph-disabled {
  min-height: 420px;
  gap: 12px;
}

.disabled-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 6px;
}

.legend-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border-radius: var(--nm-r-full);
  font-size: var(--nm-fs-xs);
  font-weight: 650;
}

.disabled-hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.disabled-hints span {
  padding: 5px 12px;
  border: 1px dashed var(--nm-line-strong);
  border-radius: var(--nm-r-full);
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

.disabled-meta {
  margin-top: 4px;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

/* --- 侧栏 --- */
.graph-side {
  position: sticky;
  top: 96px;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  display: grid;
  gap: 16px;
  min-width: 0;
}

.side-card {
  display: grid;
  gap: 14px;
}

.side-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.side-reset {
  border: 0;
  background: transparent;
  color: var(--nm-accent);
  font-size: var(--nm-fs-xs);
  font-weight: 650;
  cursor: pointer;
}

.side-reset:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.side-hint {
  font-size: var(--nm-fs-xs);
  line-height: 1.7;
  color: var(--nm-ink-3);
}

.legend-list,
.connection-list,
.hint-list {
  list-style: none;
  display: grid;
  gap: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.legend-item:hover {
  border-color: var(--nm-line-strong);
  background: var(--nm-surface-2);
}

.legend-item.is-off {
  opacity: 0.45;
}

.legend-item.is-off .legend-label {
  text-decoration: line-through;
}

.legend-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: var(--nm-r-full);
}

.legend-dot--lg {
  width: 14px;
  height: 14px;
}

.legend-dot--sm {
  width: 8px;
  height: 8px;
}

.legend-label {
  flex: 1;
  text-align: left;
  font-size: var(--nm-fs-sm);
  color: var(--nm-ink-2);
}

.legend-count {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.node-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-head__copy {
  min-width: 0;
}

.node-title {
  font-size: 1rem;
  font-weight: 720;
  line-height: 1.35;
  color: var(--nm-ink);
  word-break: break-word;
}

.node-type {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

.node-id {
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-4);
}

.node-connections {
  display: grid;
  gap: 10px;
}

.connection {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-radius: var(--nm-r-sm);
  background: var(--nm-surface-2);
  cursor: pointer;
  transition: all var(--nm-dur-fast) var(--nm-ease);
}

.connection:hover {
  border-color: var(--nm-accent-line);
  background: var(--nm-accent-soft);
}

.connection__label {
  flex: 1;
  min-width: 0;
  text-align: left;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection__rel {
  font-size: 11px;
  color: var(--nm-ink-4);
}

.hint-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--nm-fs-xs);
  color: var(--nm-ink-3);
}

/* --- 响应式 --- */
@media (max-width: 1024px) {
  .graph-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .graph-side {
    position: static;
    max-height: none;
    overflow: visible;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .graph-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .graph-head__stats {
    width: 100%;
  }

  .head-stat {
    flex: 1;
    min-width: 0;
  }

  .graph-side {
    grid-template-columns: minmax(0, 1fr);
  }

  .graph-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-box {
    min-width: 0;
  }

  .search-box :deep(.el-input) {
    max-width: none;
  }

  .zoom-group {
    align-self: flex-start;
  }

  .graph-canvas {
    min-height: 420px;
  }

  .graph-canvas__inner,
  .canvas-skeleton {
    height: 420px;
  }
}
</style>

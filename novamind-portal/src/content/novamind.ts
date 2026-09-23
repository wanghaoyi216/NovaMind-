export interface HeroSignal {
  value: string
  label: string
  detail: string
}

export interface ReactPhase {
  key: string
  title: string
  label: string
  metric: string
  desc: string
}

export interface SceneCard {
  badge: string
  title: string
  desc: string
  image: string
}

export const reactPhases: ReactPhase[] = [
  {
    key: 'discover',
    title: '浏览课程',
    label: '查找合适的课程',
    metric: '课程检索 + 分类筛选',
    desc: '通过关键词或分类快速定位感兴趣的课程。',
  },
  {
    key: 'choose',
    title: '加入购物车',
    label: '比较后决策',
    metric: '价格、评价对比',
    desc: '将候选课程加入购物车，集中比较再购买。',
  },
  {
    key: 'learn',
    title: '开始学习',
    label: '课程 + AI 协同',
    metric: '课程学习 + AI 辅导',
    desc: '边学边问，AI 助手实时解答疑惑。',
  },
  {
    key: 'review',
    title: '巩固提升',
    label: '测验与复盘',
    metric: '学习记录 + 阶段测验',
    desc: '通过测验和问答巩固所学知识。',
  },
]

export const heroSignals: HeroSignal[] = [
  {
    value: '120+',
    label: '精选课程',
    detail: '覆盖前端、后端、AI、云原生与数据库等方向',
  },
  {
    value: '4.9',
    label: '学员评分',
    detail: '高质量课程，完课率与好评率持续领先',
  },
  {
    value: 'AI',
    label: '智能助手',
    detail: '7x24 小时学习答疑与个性化推荐',
  },
  {
    value: '全链路',
    label: '学习闭环',
    detail: '选课、学习、测验、社区互动一站式完成',
  },
]

export const sceneCards: SceneCard[] = [
  {
    badge: '课程中心',
    title: '发现优质课程',
    desc: '分类浏览、关键词搜索、价格筛选，快速定位适合你的课程。',
    image: '/resource/banners/banner_students_studying_01.jpg',
  },
  {
    badge: 'AI 助手',
    title: '智能学习伙伴',
    desc: '随时提问、获取学习建议、定制个性化学习路径。',
    image: '/resource/course-covers/course_ai_deep_learning_01.jpg',
  },
  {
    badge: '管理后台',
    title: '运营管理工具',
    desc: '课程上下架、用户管理、数据报表，全方位运营支持。',
    image: '/resource/business/biz_team_leader_01.jpg',
  },
  {
    badge: '学习社区',
    title: '互动与测验',
    desc: '问答讨论、阶段测验，巩固所学知识。',
    image: '/resource/education/edu_online_learning_01.jpg',
  },
]

export const consolePrompts = [
  '我正在学习编程，请帮我推荐适合初学者的课程和学习路线',
  '帮我分析这几门课程的学习顺序，从基础到进阶该如何安排',
  '把我的学习目标拆解为每周计划，包含课程、练习和小测验',
  '我刚学完一章内容，请给我出几道题检验掌握程度',
]

export const marqueeKeywords = [
  '课程检索',
  '购物车',
  '下单支付',
  'AI交互',
  '评论问答',
  '考试训练',
  '用户中心',
  '数据报表',
]

import type { CourseSimpleInfo } from '@/types'

/**
 * 分类主题。
 * 注意：accent / softAccent 的值是 CSS 变量表达式（令牌引用），不是字面色值，
 * 因此浅色与深色主题都会自动跟随 design-system.scss 的语义令牌。
 */
export interface CourseTheme {
  accent: string
  softAccent: string
  glow: string
  label: string
}

/**
 * 分类强调色令牌名。
 * 页面据此引用 `var(--nm-<token>)` / `var(--nm-<token>-soft)`，
 * 保证浅色与深色主题都由 design-system 统一控制，页面内不写死颜色。
 */
export type CourseAccentToken = 'accent' | 'cyan' | 'success' | 'warning' | 'danger'

export interface DecoratedCourse<T> {
  raw: T
  cover: string
  summary: string
  labels: string[]
  theme: CourseTheme
  accentToken: CourseAccentToken
}

/** 真实课程封面池：public/resource/course-covers/
 *  注意：该目录下另有 16 个文件扩展名是 .jpg/.png，内容却是保存下来的 HTML 页面，
 *  浏览器无法解码（例如 course_spring_boot_01.jpg、course_docker_kubernetes_01.jpg）。
 *  这里只保留已确认可正常解码的封面，避免列表出现破图。 */
export const courseCoverPool = [
  '/resource/course-covers/course_ai_deep_learning_01.jpg',
  '/resource/course-covers/course_ui_ux_design_01.jpg',
  '/resource/course-covers/course_coding_programming_02.jpg',
  '/resource/course-covers/course_web_development_02.jpg',
  '/resource/course-covers/course_mobile_development_01.jpg',
  '/resource/course-covers/course_javascript_programming_01.png',
  '/resource/course-covers/course_html5_frontend_01.png',
  '/resource/course-covers/course_css3_styling_01.png',
  '/resource/course-covers/course_mobile_app_dev_01.png',
  '/resource/course-covers/course_computer_science_01.png',
  '/resource/course-covers/course_computer_basics_01.png',
] as const

const mediaPool = {
  ai: courseCoverPool[0],
  code: courseCoverPool[2],
  city: courseCoverPool[3],
  galaxy: courseCoverPool[0],
  skyline: courseCoverPool[3],
  wireframe: courseCoverPool[1],
  desk: courseCoverPool[4],
  control: courseCoverPool[9],
} as const

/** 关键词 → 封面。顺序即优先级，越具体的规则越靠前。 */
const coverRules: Array<{ pattern: RegExp; cover: string }> = [
  { pattern: /deep\s?learning|深度学习|神经网络/, cover: courseCoverPool[0] },
  { pattern: /machine\s?learning|机器学习|模型|大模型|llm|agent|人工智能|ai\b/, cover: courseCoverPool[0] },
  { pattern: /vue|react|前端|frontend|webpack|vite|web\b/, cover: courseCoverPool[3] },
  { pattern: /javascript|typescript|es6|js\b|ts\b|node/, cover: courseCoverPool[5] },
  { pattern: /html5|html\b/, cover: courseCoverPool[6] },
  { pattern: /css3|css\b|scss|sass|样式/, cover: courseCoverPool[7] },
  { pattern: /ui|ux|交互|视觉|设计|design/, cover: courseCoverPool[1] },
  { pattern: /flutter|android|ios|移动端|mobile|小程序/, cover: courseCoverPool[4] },
  { pattern: /app\b|应用开发/, cover: courseCoverPool[8] },
  { pattern: /算法|数据结构|计算机科学|computer\s?science|编译原理/, cover: courseCoverPool[9] },
  { pattern: /入门|基础|计算机基础|办公|通识/, cover: courseCoverPool[10] },
  { pattern: /编程|代码|code|programming|全栈|rust|go\b|java\b|jdk|jvm|python|spring|微服务|docker|k8s|kubernetes|云原生|数据库|mysql|redis|sql|linux|数据分析|运维|测试/, cover: courseCoverPool[2] },
]

/** 由令牌名构造主题，页面可直接用于 --accent / --soft-accent 这类自定义属性 */
function themeFor(token: CourseAccentToken, label: string): CourseTheme {
  return {
    accent: `var(--nm-${token})`,
    softAccent: `var(--nm-${token}-soft)`,
    glow: 'var(--nm-sh-accent)',
    label,
  }
}

const categoryThemes: Record<string, CourseTheme> = {
  'AI/ML': themeFor('cyan', 'ReAct / Agent'),
  '前端开发': themeFor('accent', 'Motion / UI'),
  '后端开发': themeFor('warning', 'Service / Runtime'),
  '云原生': themeFor('cyan', 'Cloud / Deploy'),
  '数据库': themeFor('success', 'Data / Query'),
  '移动端': themeFor('danger', 'App / Device'),
}

const categoryAccentTokens: Record<string, CourseAccentToken> = {
  'AI/ML': 'cyan',
  '人工智能': 'cyan',
  '前端开发': 'accent',
  '后端开发': 'warning',
  '云原生': 'cyan',
  '数据库': 'success',
  '移动端': 'danger',
}

const courseSummaryFragments = [
  '从原理到项目实战，系统掌握核心知识点。',
  '理论与实践结合，在项目中掌握工程化方法。',
  '注重动手能力，每个章节都配有实战练习。',
  '循序渐进的学习路径，从入门到进阶全面提升。',
]

export const courseMissionPanels = [
  {
    title: '智能推荐',
    desc: '结合分类、热度和价格，帮你快速定位合适课程。',
  },
  {
    title: 'AI 辅助',
    desc: '学习过程中随时提问，AI 助手实时答疑解惑。',
  },
  {
    title: '学练结合',
    desc: '边学边练，配套测验巩固知识掌握。',
  },
]

export const courseOutcomeDeck = [
  '从入门到进阶，形成完整技术栈学习路径',
  '理论与实战并重，真正提升工程能力',
  '社区互动与 AI 辅助，学习不再孤单',
]

export function getCourseTheme(categoryName = '', keyword = ''): CourseTheme {
  if (categoryThemes[categoryName]) {
    return categoryThemes[categoryName]
  }

  const source = `${categoryName} ${keyword}`.toLowerCase()
  if (source.includes('ai') || source.includes('agent')) {
    return categoryThemes['AI/ML']
  }
  if (source.includes('vue') || source.includes('react') || source.includes('front')) {
    return categoryThemes['前端开发']
  }
  if (source.includes('spring') || source.includes('java') || source.includes('node')) {
    return categoryThemes['后端开发']
  }
  if (source.includes('docker') || source.includes('cloud') || source.includes('k8')) {
    return categoryThemes['云原生']
  }
  if (source.includes('mysql') || source.includes('redis') || source.includes('sql')) {
    return categoryThemes['数据库']
  }
  return themeFor('accent', 'Learning Track')
}

/** 分类强调色（令牌名，供页面引用 var(--nm-*) 使用） */
export function getCourseAccentToken(categoryName = '', keyword = ''): CourseAccentToken {
  if (categoryAccentTokens[categoryName]) {
    return categoryAccentTokens[categoryName]
  }

  const source = `${categoryName} ${keyword}`.toLowerCase()
  if (/ai|agent|模型|machine|智能/.test(source)) return 'cyan'
  if (/vue|react|前端|front|ui|ux|设计/.test(source)) return 'accent'
  if (/spring|java|后端|node|服务|架构/.test(source)) return 'warning'
  if (/docker|k8s|kubernetes|云|cloud|devops|运维/.test(source)) return 'cyan'
  if (/mysql|redis|sql|数据|database|分析/.test(source)) return 'success'
  if (/移动|mobile|flutter|app|android|ios|游戏/.test(source)) return 'danger'
  return 'accent'
}

/** 确定性封面分配：先按关键词命中，未命中时按索引在封面池中轮转 */
export function pickCourseCover(name = '', categoryName = '', index = 0): string {
  const source = `${name} ${categoryName}`.toLowerCase()

  for (const rule of coverRules) {
    if (rule.pattern.test(source)) {
      return rule.cover
    }
  }

  const safeIndex = Number.isFinite(index) ? Math.abs(Math.trunc(index)) : 0
  return courseCoverPool[safeIndex % courseCoverPool.length]
}

/** @deprecated 保留兼容：请优先使用 pickCourseCover */
export function pickCourseMedia(name = '', categoryName = '', index = 0): string {
  const source = `${name} ${categoryName}`.toLowerCase()

  if (source.includes('ai') || source.includes('agent') || source.includes('模型')) {
    return mediaPool.ai
  }
  if (source.includes('vue') || source.includes('react') || source.includes('前端')) {
    return mediaPool.wireframe
  }
  if (source.includes('spring') || source.includes('java') || source.includes('微服务')) {
    return mediaPool.city
  }
  if (source.includes('docker') || source.includes('k8') || source.includes('云原生')) {
    return mediaPool.skyline
  }
  if (source.includes('mysql') || source.includes('redis') || source.includes('sql') || source.includes('数据')) {
    return mediaPool.control
  }
  if (source.includes('mobile') || source.includes('flutter') || source.includes('移动')) {
    return mediaPool.desk
  }
  if (source.includes('go') || source.includes('rust') || source.includes('node') || source.includes('python')) {
    return mediaPool.code
  }

  return Object.values(mediaPool)[index % Object.values(mediaPool).length]
}

export function buildCourseLabels(name = '', categoryName = ''): string[] {
  const source = `${name} ${categoryName}`.toLowerCase()
  const labels = new Set<string>()

  if (source.includes('ai') || source.includes('agent')) {
    labels.add('智能体实践')
    labels.add('推理链路')
  }
  if (source.includes('vue') || source.includes('react') || source.includes('前端')) {
    labels.add('界面工程')
    labels.add('动效系统')
  }
  if (source.includes('spring') || source.includes('java') || source.includes('微服务')) {
    labels.add('服务架构')
    labels.add('模块协同')
  }
  if (source.includes('docker') || source.includes('k8') || source.includes('云')) {
    labels.add('运行时部署')
  }
  if (source.includes('mysql') || source.includes('redis') || source.includes('sql')) {
    labels.add('数据性能')
  }

  labels.add(categoryName || '平台课程')
  return Array.from(labels).slice(0, 4)
}

export function buildCourseSummary(name = '', categoryName = '', index = 0): string {
  const theme = getCourseTheme(categoryName, name)
  return `${theme.label} · ${courseSummaryFragments[index % courseSummaryFragments.length]}`
}

/** 为课程补齐封面 / 摘要 / 标签 / 分类强调色（全部确定性，无随机） */
export function decorateCourse<T extends Partial<CourseSimpleInfo>>(
  course: T,
  index = 0
): DecoratedCourse<T> {
  const record = course as T & { introduction?: string; description?: string }
  return {
    raw: course,
    cover: course.coverImg || pickCourseCover(course.name, course.categoryName, index),
    summary:
      record.introduction ||
      record.description ||
      buildCourseSummary(course.name, course.categoryName, index),
    labels: buildCourseLabels(course.name, course.categoryName),
    theme: getCourseTheme(course.categoryName, course.name),
    accentToken: getCourseAccentToken(course.categoryName, course.name),
  }
}

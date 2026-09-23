/**
 * 图片兜底。
 *
 * 背景：`Resources/course-covers/` 下有 16 个文件虽然叫 `.jpg`，内容其实是
 * 保存下来的 HTML 页面（不是图片），MinIO 仍会按扩展名返回 `image/jpeg`，
 * 于是浏览器渲染成破图。数据库里课程 2000001 的 coverUrl 正好指向其中之一。
 *
 * 因此前端不能假设 URL 一定能渲染：任何封面加载失败都要降级为
 * 「同主题的可用封面 → 内联 SVG 占位图」，绝不出现浏览器的破图图标。
 */

/** 已确认可正常解码的本地课程封面（用于替换坏图）。 */
export const SAFE_COVERS = [
  '/resource/course-covers/course_ai_deep_learning_01.jpg',
  '/resource/course-covers/course_ui_ux_design_01.jpg',
  '/resource/course-covers/course_coding_programming_02.jpg',
  '/resource/course-covers/course_web_development_02.jpg',
  '/resource/course-covers/course_mobile_development_01.jpg',
  '/resource/course-covers/course_computer_science_01.png',
  '/resource/course-covers/course_computer_basics_01.png',
  '/resource/course-covers/course_javascript_programming_01.png',
  '/resource/course-covers/course_html5_frontend_01.png',
  '/resource/course-covers/course_css3_styling_01.png',
  '/resource/course-covers/course_mobile_app_dev_01.png',
] as const

/** 已确认可正常解码的头像。 */
export const SAFE_AVATARS = [
  '/resource/user-avatars/avatar_student_male_01.jpg',
  '/resource/user-avatars/avatar_student_female_01.jpg',
  '/resource/user-avatars/avatar_teacher_male_01.jpg',
  '/resource/user-avatars/avatar_teacher_female_01.jpg',
  '/resource/user-avatars/avatar_young_man_01.png',
  '/resource/user-avatars/avatar_child_student_01.jpg',
] as const

/** 内联 SVG 占位图：纯 data URI，不依赖任何外部资源，永不失败。 */
export function placeholderImage(label = 'NovaMind'): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
<rect width="640" height="360" fill="#e9ebef"/>
<path d="M320 132c4.4 33.2 16.8 45.6 50 50-33.2 4.4-45.6 16.8-50 50-4.4-33.2-16.8-45.6-50-50 33.2-4.4 45.6-16.8 50-50Z" fill="#99a1ad" opacity="0.55"/>
<text x="320" y="252" text-anchor="middle" font-family="monospace" font-size="18" letter-spacing="3" fill="#6a7280">${label}</text>
</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/** 依据给定种子稳定地挑一张可用封面，避免同一张图反复出现。 */
export function pickSafeCover(seed?: string | number): string {
  const raw = String(seed ?? '')
  let hash = 0
  for (let i = 0; i < raw.length; i += 1) {
    hash = (hash * 31 + raw.charCodeAt(i)) % 100000
  }
  return SAFE_COVERS[hash % SAFE_COVERS.length]
}

/**
 * `<img @error>` 处理器：第一次失败换成可用封面，再失败换成内联占位图。
 * 用 `data-fallback` 记录状态，杜绝无限循环。
 */
export function handleImageError(event: Event, options: { kind?: 'cover' | 'avatar' } = {}): void {
  const img = event.target as HTMLImageElement | null
  if (!img) return

  const stage = img.dataset.fallbackStage

  if (stage === 'cover') {
    img.dataset.fallbackStage = 'placeholder'
    img.src = placeholderImage(options.kind === 'avatar' ? 'NovaMind' : 'COURSE')
    return
  }

  if (stage === 'placeholder') return

  img.dataset.fallbackStage = 'cover'
  if (options.kind === 'avatar') {
    img.src = SAFE_AVATARS[0]
  } else {
    img.src = pickSafeCover(img.dataset.fallbackSeed || img.alt || img.src)
  }
}

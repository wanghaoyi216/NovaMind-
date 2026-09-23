/**
 * 主题管理：单一入口，避免各组件各自读写 data-theme 造成状态漂移。
 *
 * 设计约束：浅色为默认主题。仅在用户显式选择过深色时才启用深色，
 * 不再依据 prefers-color-scheme 自动切换——此前自动切换会让浅色纸面
 * 与深色文字令牌同时生效，导致整站文字近乎不可见。
 */
export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'nova_theme'

export function getStoredTheme(): ThemeMode | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw === 'dark' || raw === 'light' ? raw : null
}

export function getTheme(): ThemeMode {
  return getStoredTheme() ?? 'light'
}

export function applyTheme(mode: ThemeMode) {
  const root = document.documentElement
  if (mode === 'dark') {
    root.setAttribute('data-theme', 'dark')
  } else {
    root.removeAttribute('data-theme')
  }
  root.style.colorScheme = mode
  localStorage.setItem(STORAGE_KEY, mode)
}

export function toggleTheme(): ThemeMode {
  const next: ThemeMode = getTheme() === 'dark' ? 'light' : 'dark'
  applyTheme(next)
  return next
}

/** 应用启动时调用一次：把已保存的主题落到 <html> 上。 */
export function applyInitialTheme() {
  applyTheme(getTheme())
}

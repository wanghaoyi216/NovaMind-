import { describe, it, expect, beforeEach } from 'vitest'
import { applyTheme, getStoredTheme, getTheme, toggleTheme } from '@/utils/theme'

describe('主题工具（utils/theme）', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('未设置时默认浅色，且不写入 data-theme', () => {
    expect(getStoredTheme()).toBeNull()
    expect(getTheme()).toBe('light')

    applyTheme('light')
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
    expect(document.documentElement.style.colorScheme).toBe('light')
  })

  it('applyTheme(dark) 会落定 data-theme 并持久化', () => {
    applyTheme('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(localStorage.getItem('nova_theme')).toBe('dark')
    expect(getTheme()).toBe('dark')
  })

  it('toggleTheme 在两种主题间切换并同步 DOM', () => {
    applyTheme('light')
    expect(toggleTheme()).toBe('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')

    expect(toggleTheme()).toBe('light')
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false)
  })

  it('忽略 localStorage 中的非法值，回退到浅色', () => {
    localStorage.setItem('nova_theme', 'solarized')
    expect(getStoredTheme()).toBeNull()
    expect(getTheme()).toBe('light')
  })
})

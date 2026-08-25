/**
 * useTheme  -  全站主题状态(单例 composable)
 *
 * 设计:
 * - 模块级 ref:多个组件 useTheme() 拿到同一个状态,跨页面持久
 * - initTheme() 幂等,启动时由 main.ts 调用一次(此时还没渲染任何组件,避免主题闪)
 * - 切换走 toggleTheme(),同时写 localStorage + 改 documentElement[data-theme]
 * - theme 用 readonly() 暴露,组件不能直接改值,只能调 toggle
 *
 * 优先级:
 *   1. localStorage['tm_landing_theme']  (用户上次的明确选择)
 *   2. window.matchMedia('(prefers-color-scheme: dark)')  (系统偏好)
 *   3. 上面都没有 → 默认 light
 */

import { ref, readonly, type DeepReadonly, type Ref } from 'vue'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'tm_landing_theme'

// 模块级单例 ref
const themeRef = ref<Theme>('light')
let initialized = false

function readSystemPreference(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyToDom(t: Theme): void {
  document.documentElement.setAttribute('data-theme', t)
}

function persist(t: Theme): void {
  localStorage.setItem(STORAGE_KEY, t)
}

/**
 * 启动时调用一次(在 main.ts 里、createApp 之后立即调)。
 * 幂等:多次调用不会重复覆盖用户上一次的明确选择。
 */
export function initTheme(): void {
  if (initialized) return
  initialized = true

  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark' || stored === 'light') {
    themeRef.value = stored
  } else {
    themeRef.value = readSystemPreference()
  }
  applyToDom(themeRef.value)
}

/**
 * 用户从 UI 触发主题切换(比如 nav 里的 toggle)。
 * 立即写 DOM + localStorage,ref 值在组件里是 readonly 的所以只能通过这个函数改。
 */
export function toggleTheme(): void {
  themeRef.value = themeRef.value === 'light' ? 'dark' : 'light'
  applyToDom(themeRef.value)
  persist(themeRef.value)
}

/**
 * 组件里 useTheme() 拿到的主题状态。
 * theme 是 readonly ref,改值请走 toggleTheme()。
 */
export function useTheme(): {
  theme: DeepReadonly<Ref<Theme>>
  toggleTheme: () => void
} {
  return {
    theme: readonly(themeRef),
    toggleTheme,
  }
}
/**
 * useChartPalette —— 把 CSS 变量读成 Chart.js 能吃的具体色值
 *
 * 为什么需要这层:Chart.js 在 canvas 上画图,不认 CSS 变量,
 * 必须拿到 `#4caf50` 这种具体色值。主题(token)定义在 theme.css 的
 * `:root[data-theme='...']` 上,所以:
 *   1. 从 documentElement 的 computedStyle 读变量
 *   2. 监听 useTheme().theme 变化 → 重新读一遍
 *
 * 用法:在 BaseChart 里 ref 持有,palette 变化时重建图表。
 */

import { ref, watch, type Ref } from 'vue'
import { useTheme } from '@/composables/useTheme'

/** 图表用到的全部色值 —— 与 theme.css 的 token 一一对应 */
export interface ChartPalette {
  /** --color-accent:主色(绿) */
  accent: string
  /** --color-emphasis:强调色(琥珀) */
  emphasis: string
  /** --color-text:正文色 */
  text: string
  /** --color-text-muted:次要文字 */
  textMuted: string
  /** --color-text-soft:弱化文字(网格标签) */
  textSoft: string
  /** --color-border:网格线 */
  border: string
  /** --color-card:卡片底(canvas 背景) */
  card: string
  /** --color-bg-alt:浅底(bar 轨道等) */
  bgAlt: string
  /** 危险色 —— Element Plus danger 系,项目里用于错误提示 */
  danger: string
}

function cssVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

/** 从 DOM 读当前主题下的调色板 */
export function readChartPalette(): ChartPalette {
  return {
    accent: cssVar('--color-accent', '#4caf50'),
    emphasis: cssVar('--color-emphasis', '#ffc107'),
    text: cssVar('--color-text', '#212121'),
    textMuted: cssVar('--color-text-muted', '#616161'),
    textSoft: cssVar('--color-text-soft', '#9e9e9e'),
    border: cssVar('--color-border', '#e8e5dc'),
    card: cssVar('--color-card', '#ffffff'),
    bgAlt: cssVar('--color-bg-alt', '#f5f5f5'),
    danger: '#f56c6c',
  }
}

/**
 * 响应式调色板 —— theme 变化时自动重读 CSS 变量。
 *
 * 注意:读 DOM 是副作用,必须在 watch 回调里做(不能在 computed 里),
 * 否则 computed 的纯度被破坏且可能拿到主题切换前的旧值。
 */
export function useChartPalette(): Ref<ChartPalette> {
  const { theme } = useTheme()
  const palette = ref<ChartPalette>(readChartPalette())

  watch(theme, () => {
    // data-theme 属性已由 useTheme.toggleTheme 同步写入,
    // 这里下一个微任务再读,确保拿到新主题的变量值
    requestAnimationFrame(() => {
      palette.value = readChartPalette()
    })
  })

  return palette
}

/**
 * useChartPalette —— 把 CSS 变量读成 Chart.js 能吃的具体色值
 *
 * 为什么需要这层:Chart.js 在 canvas 上画图,不认 CSS 变量,
 * 必须拿到 `#337a3e` 这种具体色值。主题(token)定义在 theme.css 的
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
  /** --color-accent:主色(墨绿) */
  accent: string
  /** --color-emphasis:强调色(品牌琥珀,图表第二序列) */
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
  /** 危险色 —— 读 --el-color-danger,与 Element Plus 的 danger 阶保持一致 */
  danger: string
  /** 分类色板 —— 多序列图(饼/堆叠)按顺序取,保证同色系不撞色 */
  series: string[]
  /** accent 的 rgb 分量("51, 122, 62")—— 渐变填充用 */
  accentRgb: string
  /** emphasis 的 rgb 分量 —— 渐变填充用 */
  emphasisRgb: string
}

function cssVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

/**
 * #rgb / #rrggbb → "r, g, b"
 *
 * 渐变填充要的是 `rgba(r, g, b, a)`,所以这里把 hex 拆成 rgb 三元组。
 * 拆不出来(比如变量本身写的是 color-mix)就原样返回,调用方拼 rgba 会失败,
 * 所以解析失败时回落到一个安全值。
 */
export function hexToRgbTriplet(hex: string, fallback = '51, 122, 62'): string {
  const m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim())
  if (!m) return fallback
  let h = m[1]
  if (h.length === 3) h = h.split('').map((c) => c + c).join('')
  const n = parseInt(h, 16)
  return `${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}`
}

/** 从 DOM 读当前主题下的调色板 */
export function readChartPalette(): ChartPalette {
  // 兜底值跟 theme.css 保持一致(墨绿体系),不要用 Material 旧色
  const accent = cssVar('--color-accent', '#337a3e')
  const emphasis = cssVar('--color-emphasis', '#b87a12')
  return {
    accent,
    emphasis,
    text: cssVar('--color-text', '#212121'),
    textMuted: cssVar('--color-text-muted', '#5f5b52'),
    textSoft: cssVar('--color-text-soft', '#746f62'),
    border: cssVar('--color-border', '#e6e0d2'),
    card: cssVar('--color-card', '#ffffff'),
    bgAlt: cssVar('--color-bg-alt', '#f0ebe0'),
    danger: cssVar('--el-color-danger', '#b5432f'),
    // 分类色板:墨绿 → 琥珀 → 青 → 苔绿 → 紫
    // 与 theme.css 第 1.5 节的辅助色相同源,明暗两套都在 WCAG AA 以上
    series: [
      accent,
      emphasis,
      cssVar('--hue-teal', '#4a7c8c'),
      cssVar('--hue-moss', '#5c8a4a'),
      cssVar('--hue-violet', '#8e24aa'),
    ],
    accentRgb: hexToRgbTriplet(accent, '51, 122, 62'),
    emphasisRgb: hexToRgbTriplet(emphasis, '184, 122, 18'),
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

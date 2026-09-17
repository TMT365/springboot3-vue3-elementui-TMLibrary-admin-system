/**
 * Chart.js 全局类型声明
 *
 * Chart.js 走 CDN 引入(见 index.html),不是 npm 依赖 —— 所以没有
 * `import { Chart } from 'chart.js'` 可用,运行时从 `window.Chart` 拿。
 *
 * ⚠️ 本文件是 .d.ts(纯类型),**不能出现运行时代码/常量导出**
 *    —— 之前踩过 `.d.ts` 里放 export const 导致 Vite "Failed to resolve import" 的坑。
 *
 * 这里只声明本项目实际用到的 Chart.js 表面(构造 / destroy / update / data / options),
 * 不做完整类型建模 —— 完整类型需要装 chart.js 包,与"走 CDN"的取舍冲突。
 */

/** Chart.js 构造参数:type + data + options(够用即可,不做完整建模) */
export interface ChartJsConfig {
  type: 'line' | 'bar' | 'doughnut' | 'pie'
  data: ChartJsData
  options?: Record<string, unknown>
  plugins?: unknown[]
}

export interface ChartJsData {
  labels: (string | number)[]
  datasets: ChartJsDataset[]
}

export interface ChartJsDataset {
  label?: string
  data: (number | null)[]
  [key: string]: unknown
}

/** Chart 实例 —— 本项目只用到这几个方法 */
export interface ChartJsInstance {
  data: ChartJsData
  options: Record<string, unknown>
  update(mode?: string): void
  destroy(): void
  resize(): void
}

/** window.Chart 构造函数(静态成员只声明用到的那几个) */
export interface ChartJsConstructor {
  new (canvas: HTMLCanvasElement, config: ChartJsConfig): ChartJsInstance
}

declare global {
  interface Window {
    /** CDN 未加载时为 undefined —— 使用方必须先判断 */
    Chart?: ChartJsConstructor
  }
}

export {}

/**
 * 图表外部提示框 —— 把 Chart.js 默认的 canvas 提示框换成 HTML。
 *
 * 为什么不用 Chart.js 自带的提示框:
 *   自带的是**画在 canvas 上**的一块矩形,样式只能靠 options.plugins.tooltip
 *   里的颜色/字体配置写死 —— 想让它跟页面卡片共用同一套设计令牌
 *   (--color-card / --color-border / --radius-md / 阴影),做不到:
 *   canvas 里没有 CSS 变量,主题切换后还得重建图表才能换色。
 *
 * external 提示框就是一块普通 DOM:
 *   - 样式写在 theme.css 里,跟全站共用令牌 → 明暗切换零成本
 *   - 字体走 --font-sans / --font-display,和页面正文一致
 *   - 数值用 tabular-nums,多行对齐不跳
 *   - 圆角、描边、阴影和卡片完全一致,像"从页面里长出来的一块"
 *
 * 数据来源不变:Chart.js 仍然会先把 options.plugins.tooltip.callbacks
 * 跑一遍,再把结果交给 external 处理器 —— 所以"文案怎么写"还在
 * dashboardCharts.ts 里(那里才知道 ¥139.00 / 12 笔这种格式),
 * 这里只负责"怎么摆"。
 *
 * 行内用 \t 分隔「标签」和「数值」:回调返回 "销售额\t¥139.00",
 * 提示框里标签靠左、数值靠右(tabular-nums 对齐)。
 */

/** 一个数据点 —— 只取画圆点需要的字段 */
interface TipDataPoint {
  dataset?: {
    label?: string
    borderColor?: unknown
    backgroundColor?: unknown
    hoverBackgroundColor?: unknown
  }
  element?: { options?: { backgroundColor?: unknown } }
}

/**
 * 一行数据的容器 —— Chart.js 的 tooltip.body 是**对象数组**,不是字符串数组。
 *
 * 每个 body item 形如 { lines: string[], dataset, dataIndex, element }:
 * 一个数据点可以产出多行(比如"售出 12 件 / 销售额 ¥123.00"),
 * 所以要按 lines 摊平,再和 dataPoints 对齐取颜色。
 */
interface TipBodyItem {
  lines?: string[]
  dataset?: TipDataPoint['dataset']
  element?: TipDataPoint['element']
}

/** Chart.js 传给 external 处理器的上下文(只声明用到的字段) */
interface TipContext {
  chart: { canvas: HTMLCanvasElement }
  tooltip: {
    opacity: number
    title?: string[]
    body?: (string | TipBodyItem)[]
    dataPoints?: TipDataPoint[]
    caretX: number
    caretY: number
  }
}

/** 兜底圆点色 —— 取不到 dataset 颜色时用墨绿 */
const FALLBACK_DOT = '#337a3e'

/**
 * 圆点颜色:从数据点里捞出第一个能用的颜色值。
 *
 * 顺序有讲究:
 *   1. element.options.backgroundColor —— 圆环的每个扇区颜色在这里
 *      (dataset.backgroundColor 是整个数组,不是单个颜色)
 *   2. dataset.backgroundColor —— 纯色背景的柱子
 *   3. dataset.borderColor —— 折线的线色
 *   4. dataset.hoverBackgroundColor —— 渐变背景的柱子(backgroundColor 是函数)
 *
 * 可以传多个来源,按传入顺序优先(先看 body item 自带的,再退回 dataPoint)。
 */
function dotColor(...sources: (TipDataPoint | undefined)[]): string {
  const candidates: unknown[] = []
  for (const s of sources) {
    if (!s) continue
    candidates.push(
      s.element?.options?.backgroundColor,
      s.dataset?.backgroundColor,
      s.dataset?.borderColor,
      s.dataset?.hoverBackgroundColor,
    )
  }
  for (const c of candidates) {
    if (typeof c === 'string' && c.trim()) return c
  }
  return FALLBACK_DOT
}

/** HTML 转义 —— 提示框里可能有书名,不能直接拼 innerHTML */
function esc(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 拼出提示框的 HTML */
function renderHtml(title: string, rows: { label: string; value: string; color: string }[]): string {
  const head = title ? `<div class="chart-tip-title">${esc(title)}</div>` : ''
  const body = rows
    .map(
      (r) =>
        `<div class="chart-tip-row">` +
        `<i class="chart-tip-dot" style="background:${esc(r.color)}"></i>` +
        (r.label ? `<span class="chart-tip-label">${esc(r.label)}</span>` : '') +
        `<span class="chart-tip-value">${esc(r.value)}</span>` +
        `</div>`,
    )
    .join('')
  return `<div class="chart-tip-inner">${head}${body}</div>`
}

/**
 * Chart.js 的 external tooltip 处理器。
 *
 * 挂在 options.plugins.tooltip.external 上。Chart.js 仍然会先跑 callbacks
 * 生成文案,再把 tooltip 模型交给这里。
 */
export function externalTooltip(context: TipContext): void {
  const { chart, tooltip } = context
  const host = chart.canvas.parentElement
  if (!host) return

  // 提示框元素挂在 canvas 所在的 .chart-body 里(relative 定位),
  // 反复 hover 复用同一个节点,不会越点越多
  let el = host.querySelector<HTMLElement>('.chart-tip')
  if (!el) {
    el = document.createElement('div')
    el.className = 'chart-tip'
    host.appendChild(el)
  }

  const points = tooltip.dataPoints ?? []

  // 显隐不靠 tooltip.opacity:
  //   Chart.js 的提示框有淡入淡出动画,opacity 会先停在 0 再升上来,
  //   拿它当"要不要显示"的开关,提示框会在动画开始前就被藏掉。
  //   改看"有没有数据点":有就显示,没有就藏,淡入淡出交给 CSS 过渡。
  const items = tooltip.body ?? []
  if (points.length === 0 && items.length === 0) {
    el.style.opacity = '0'
    return
  }

  // body 是对象数组,一个 item 可能含多行(lines),要摊平:
  //   "售出\t12 件" + "销售额\t¥139.00" 来自同一个 item(同一根柱子、同一个颜色)
  const rows: { label: string; value: string; color: string }[] = []
  items.forEach((item, i) => {
    const isString = typeof item === 'string'
    const lines = isString ? [item as string] : (item.lines ?? [])
    const dp = points[Math.min(i, points.length - 1)]
    const color = dotColor(
      isString ? undefined : { dataset: item.dataset, element: item.element },
      dp,
    )
    for (const line of lines) {
      const raw = String(line ?? '')
      const tab = raw.indexOf('\t')
      rows.push(
        tab >= 0
          ? { label: raw.slice(0, tab), value: raw.slice(tab + 1), color }
          : { label: '', value: raw, color },
      )
    }
  })

  el.innerHTML = renderHtml((tooltip.title ?? []).join(' '), rows)
  el.style.opacity = '1'

  // 先归位再量尺寸,量完再摆 —— 免得上一帧的位置影响本次的宽度
  el.style.left = '0px'
  el.style.top = '0px'
  const w = el.offsetWidth
  const h = el.offsetHeight
  const hostW = host.clientWidth

  // caret 是鼠标位置:提示框底边抬到 caret 上方 12px,
  // 左右居中对齐 caret,并夹在容器内(贴边时不出血)
  let x = tooltip.caretX
  x = Math.min(Math.max(x, w / 2 + 6), Math.max(hostW - w / 2 - 6, w / 2 + 6))
  const y = Math.max(tooltip.caretY - 12, h + 8)

  el.style.left = `${x}px`
  el.style.top = `${y}px`
}

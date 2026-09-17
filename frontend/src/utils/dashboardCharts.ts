/**
 * 仪表盘图表配置 —— 4 个纯函数,输入 (数据, 调色板) 输出 Chart.js config
 *
 * 纯函数的好处:不依赖 Vue 响应式,主题切换时用新 palette 重新调用一次即可,
 * BaseChart 只负责生命周期,不含任何业务配色逻辑。
 *
 * 视觉规范(与其他页面保持一致的 editorial 风格):
 *   - 字体跟项目走('Manrope' 正文 / system-ui 兜底)
 *   - 网格线极淡,只画横向,不画刻度线
 *   - 折线 tension 0.35 平滑,tooltip 卡片化
 *   - 渐变填充用 accent 色,透明度从上到下衰减
 */

import type { ChartPalette } from '@/composables/useChartPalette'
import type { ChartJsConfig } from '@/types/chartjs'
import type { BookSalesItem, DailyCountItem, DailySalesItem, StatusCountItem } from '@/types/api'

const FONT_FAMILY = "'Manrope', system-ui, -apple-system, 'Segoe UI', sans-serif"
const SERIF_FAMILY = "'DM Serif Display', Georgia, serif"

/**
 * 构建时的视口信息 —— 由 BaseChart 传入。
 *
 * Chart.js 的 options 是"一次性"的:生成之后不会因为容器变窄自己调字号/抽稀刻度。
 * 375px 的手机上如果还按桌面参数画,会出现:日期标签挤成一团、双 Y 轴吃掉大半个宽度、
 * 书名标签把绘图区压到只剩一条。所以窄屏要换一套参数重新 build。
 */
export interface ChartBuildContext {
  /** 视口 ≤ 640px */
  narrow: boolean
}

/** "2026-09-17" → "09-17"(x 轴空间有限,年份省掉) */
function shortDate(iso: string): string {
  return iso.slice(5)
}

/** ¥ 千分位 —— tooltip 用 */
function money(n: number): string {
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** 竖直线性渐变 —— 折线填充用。chartArea 首帧还没算出来,返回透明兜底 */
function verticalGradient(rgb: string, fromAlpha: number, toAlpha: number) {
  return (ctx: { chart: { ctx: CanvasRenderingContext2D; chartArea?: { top: number; bottom: number } } }) => {
    const { chart } = ctx
    const area = chart.chartArea
    if (!area) return 'transparent'
    const g = chart.ctx.createLinearGradient(0, area.top, 0, area.bottom)
    g.addColorStop(0, `rgba(${rgb}, ${fromAlpha})`)
    g.addColorStop(1, `rgba(${rgb}, ${toAlpha})`)
    return g
  }
}

/** accent 绿的 rgb 分量 —— 与 theme.css 的 #4caf50 / #66bb6a 对齐 */
const ACCENT_RGB = '76, 175, 80'
/** 琥珀强调色 rgb */
const EMPHASIS_RGB = '255, 193, 7'

/** 各图共用的 tooltip 样式 —— 卡片化,和页面卡片一致的圆角与描边 */
function tooltipStyle(p: ChartPalette) {
  return {
    backgroundColor: p.card,
    titleColor: p.text,
    bodyColor: p.textMuted,
    borderColor: p.border,
    borderWidth: 1,
    padding: 12,
    cornerRadius: 10,
    boxPadding: 6,
    usePointStyle: true,
    titleFont: { family: SERIF_FAMILY, size: 13, weight: 700 as const },
    bodyFont: { family: FONT_FAMILY, size: 12 },
    displayColors: true,
  }
}

/** 各图共用的直角坐标系刻度样式(narrow 时字号和内边距各收一档) */
function axisStyle(p: ChartPalette, ctx: ChartBuildContext) {
  return {
    grid: { color: p.border, drawTicks: false, drawBorder: false },
    border: { display: false },
    ticks: {
      color: p.textSoft,
      font: { family: FONT_FAMILY, size: ctx.narrow ? 10 : 11 },
      padding: ctx.narrow ? 4 : 8,
    },
  }
}

/**
 * **数值轴**的刻度(日期 / 金额 / 数量这类连续刻度)—— 窄屏抽稀。
 *
 * 30 个 "09-17" 在桌面上排得开,在手机上会叠字;Chart.js 默认会旋转标签来塞,
 * 但旋转后要占掉三四十像素高度,把折线压扁。这里直接限制到 5 个、禁止旋转。
 *
 * 注意别用在**类目轴**上(横向条形图的 Y 轴是书名,抽稀等于把书藏起来)。
 */
function valueAxisTicks(p: ChartPalette, ctx: ChartBuildContext) {
  const base = axisStyle(p, ctx).ticks
  return ctx.narrow ? { ...base, maxTicksLimit: 5, maxRotation: 0, autoSkip: true } : base
}

/* ============================================================
 * 1. 销量趋势 —— 折线,双 Y 轴(左:订单数 / 右:销售额)
 * ============================================================ */
export function buildSalesTrendConfig(
  data: DailySalesItem[],
  p: ChartPalette,
  ctx: ChartBuildContext,
): ChartJsConfig {
  return {
    type: 'line',
    data: {
      labels: (data as DailySalesItem[]).map((d) => shortDate(d.date)),
      datasets: [
        {
          label: '销售额',
          data: data.map((d) => d.amount),
          yAxisID: 'yAmount',
          borderColor: p.accent,
          backgroundColor: verticalGradient(ACCENT_RGB, 0.28, 0.02),
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: p.accent,
          pointHoverBorderColor: p.card,
          pointHoverBorderWidth: 2,
        },
        {
          label: '订单数',
          data: data.map((d) => d.orders),
          yAxisID: 'yOrders',
          borderColor: p.emphasis,
          backgroundColor: verticalGradient(EMPHASIS_RGB, 0.18, 0.02),
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          borderDash: [6, 4],
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: p.emphasis,
          pointHoverBorderColor: p.card,
          pointHoverBorderWidth: 2,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: {
            color: p.textMuted,
            usePointStyle: true,
            pointStyle: 'circle',
            boxWidth: 8,
            boxHeight: 8,
            padding: ctx.narrow ? 10 : 16,
            font: { family: FONT_FAMILY, size: ctx.narrow ? 11 : 12 },
          },
        },
        tooltip: {
          ...tooltipStyle(p),
          callbacks: {
            label: (ctx: { dataset: { label?: string }; parsed: { y: number } }) =>
              ctx.dataset.label === '销售额'
                ? ` 销售额 ${money(ctx.parsed.y)}`
                : ` 订单 ${ctx.parsed.y} 笔`,
          },
        },
      },
      scales: {
        x: { ...axisStyle(p, ctx), ticks: valueAxisTicks(p, ctx) },
        yAmount: {
          ...axisStyle(p, ctx),
          position: 'left',
          beginAtZero: true,
          ticks: {
            ...valueAxisTicks(p, ctx),
            callback: (v: number) => `¥${v}`,
          },
        },
        yOrders: {
          ...axisStyle(p, ctx),
          position: 'right',
          beginAtZero: true,
          grid: { drawOnChartArea: false }, // 双轴不重复画网格线
          ticks: { ...valueAxisTicks(p, ctx), precision: 0 },
        },
      },
    },
  }
}

/* ============================================================
 * 2. 最近新增用户 —— 折线 + 渐变填充
 * ============================================================ */
export function buildNewUsersConfig(
  data: DailyCountItem[],
  p: ChartPalette,
  ctx: ChartBuildContext,
): ChartJsConfig {
  return {
    type: 'line',
    data: {
      labels: (data as DailyCountItem[]).map((d) => shortDate(d.date)),
      datasets: [
        {
          label: '新增用户',
          data: data.map((d) => d.count),
          borderColor: p.accent,
          backgroundColor: verticalGradient(ACCENT_RGB, 0.3, 0.02),
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: p.accent,
          pointHoverBorderColor: p.card,
          pointHoverBorderWidth: 2,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltipStyle(p),
          callbacks: {
            label: (ctx: { parsed: { y: number } }) => ` 新增 ${ctx.parsed.y} 人`,
          },
        },
      },
      scales: {
        x: { ...axisStyle(p, ctx), ticks: valueAxisTicks(p, ctx) },
        y: {
          ...axisStyle(p, ctx),
          beginAtZero: true,
          ticks: { ...valueAxisTicks(p, ctx), precision: 0 },
        },
      },
    },
  }
}

/* ============================================================
 * 3. 每本书销量 Top —— 横向条形(书名长,横着放得下)
 * ============================================================ */
export function buildBookSalesConfig(
  data: BookSalesItem[],
  p: ChartPalette,
  ctx: ChartBuildContext,
): ChartJsConfig {
  // 书名标签是这一屏最吃宽度的地方:手机上 14 个字 ≈ 绘图区的一半,
  // 剩下那点宽度画不出对比。窄屏砍到 7 个字,完整书名仍在 tooltip 里。
  const maxLabelChars = ctx.narrow ? 7 : 14
  return {
    type: 'bar',
    data: {
      labels: (data as BookSalesItem[]).map((d) =>
        d.title.length > maxLabelChars ? `${d.title.slice(0, maxLabelChars)}…` : d.title,
      ),
      datasets: [
        {
          label: '销量',
          data: data.map((d) => d.quantity),
          backgroundColor: verticalGradient(ACCENT_RGB, 0.85, 0.55),
          hoverBackgroundColor: p.accent,
          borderRadius: 6,
          borderSkipped: false,
          barThickness: 16,
        },
      ],
    },
    options: {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltipStyle(p),
          callbacks: {
            title: (items: { dataIndex: number }[]) => {
              const row = (data as BookSalesItem[])[items[0]?.dataIndex ?? 0]
              return row ? `${row.title} · ${row.author}` : ''
            },
            label: (ctx: { dataIndex: number; parsed: { x: number } }) => {
              const row = (data as BookSalesItem[])[ctx.dataIndex]
              return [` 售出 ${ctx.parsed.x} 件`, ` 销售额 ${money(row?.amount ?? 0)}`]
            },
          },
        },
      },
      scales: {
        x: {
          ...axisStyle(p, ctx),
          beginAtZero: true,
          ticks: { ...valueAxisTicks(p, ctx), precision: 0 },
        },
        y: {
          ...axisStyle(p, ctx),
          grid: { display: false }, // 横向条形不需要竖网格
          ticks: {
            ...axisStyle(p, ctx).ticks,
            // 类目轴:每本书都得显示,不能抽稀(这里**不能**用 valueAxisTicks)
            autoSkip: false,
            font: { family: FONT_FAMILY, size: ctx.narrow ? 10 : 11 },
          },
        },
      },
    },
  }
}

/* ============================================================
 * 4. 订单状态分布 —— 圆环
 *
 * 后端只返回有数据的状态,这里补齐 4 种(缺的补 0),
 * 否则图例会随数据"跳来跳去"。
 * ============================================================ */
const ORDER_STATUS_META: { code: number; label: string; pick: (p: ChartPalette) => string }[] = [
  { code: 1, label: '已支付', pick: (p) => p.accent },
  { code: 0, label: '待支付', pick: (p) => p.emphasis },
  { code: 2, label: '已取消', pick: (p) => p.textSoft },
  { code: 3, label: '超时取消', pick: (p) => p.danger },
]

export function buildOrderStatusConfig(
  data: StatusCountItem[],
  p: ChartPalette,
  ctx: ChartBuildContext,
): ChartJsConfig {
  const byCode = new Map((data as StatusCountItem[]).map((d) => [d.status, d.count]))
  const rows = ORDER_STATUS_META.map((m) => ({
    label: m.label,
    count: byCode.get(m.code) ?? 0,
    color: m.pick(p),
  }))
  const total = rows.reduce((s, r) => s + r.count, 0)

  return {
    type: 'doughnut',
    data: {
      labels: rows.map((r) => r.label),
      datasets: [
        {
          data: rows.map((r) => r.count),
          backgroundColor: rows.map((r) => r.color),
          hoverBackgroundColor: rows.map((r) => r.color),
          borderColor: p.card,
          borderWidth: 3,
          hoverOffset: 6,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: {
            color: p.textMuted,
            usePointStyle: true,
            pointStyle: 'circle',
            boxWidth: 8,
            boxHeight: 8,
            // 4 条图例在手机上会折成两三行,收一档字号和内边距,别把圆环挤没了
            padding: ctx.narrow ? 9 : 14,
            font: { family: FONT_FAMILY, size: ctx.narrow ? 11 : 12 },
          },
        },
        tooltip: {
          ...tooltipStyle(p),
          callbacks: {
            label: (ctx: { label?: string; parsed: number }) => {
              const pct = total > 0 ? ((ctx.parsed / total) * 100).toFixed(1) : '0.0'
              return ` ${ctx.label} ${ctx.parsed} 笔(${pct}%)`
            },
          },
        },
      },
    },
  }
}

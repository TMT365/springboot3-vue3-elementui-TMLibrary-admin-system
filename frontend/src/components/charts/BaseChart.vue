<script setup lang="ts">
/**
 * BaseChart —— Chart.js 通用外壳(卡片 + canvas + 生命周期)
 *
 * 职责:
 *   - 卡片头部(标题 / 副标题)
 *   - 状态:骨架屏 / 空态 / 图表 / CDN 不可用降级
 *   - Chart.js 实例生命周期:创建 → (数据或主题变化)重建 → 卸载销毁
 *
 * 配置由外部传入:`build(data, palette)` 是个**纯函数**,返回 Chart.js config。
 * 之所以传函数而不是传 config 对象:主题切换 / 数据刷新时需要用新 palette 重新生成,
 * 传死对象就没法响应式重建了。
 *
 * 重建策略:直接 destroy + new,而不是改 chart.data 再 update()
 *   —— 配置里含渐变填充(需要 chartArea),重建最省心且不会有残留状态。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useChartPalette, type ChartPalette } from '@/composables/useChartPalette'
import { useMediaQuery } from '@/composables/useMediaQuery'
import type { ChartBuildContext } from '@/utils/dashboardCharts'
import type { ChartJsConfig, ChartJsInstance } from '@/types/chartjs'

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    /** 传给 build 的原始数据(仪表盘各图自己的行数据) */
    data: unknown
    /** 用数据 + 调色板 + 视口信息生成 Chart.js 配置的纯函数 */
    build: (data: never, palette: ChartPalette, ctx: ChartBuildContext) => ChartJsConfig
    loading?: boolean
    /** 无数据时展示的文案(空串表示不启用空态) */
    emptyText?: string
    /** canvas 高度(px) */
    height?: number
  }>(),
  {
    subtitle: '',
    loading: false,
    emptyText: '暂无数据',
    height: 260,
  },
)

const palette = useChartPalette()

/**
 * 视口信息 —— 传给 build,让配置能按窄屏换一套参数(字号/刻度密度/标签截断)。
 * 手机横竖屏切换、拖窗口跨过断点时会变,变了就重建(见下面的 watch)。
 * 断点 600px 与本组件样式里的 @media 保持一致(全站约定:600 = 手机,900 = 平板/抽屉)。
 */
const isNarrow = useMediaQuery('(max-width: 600px)')
const buildCtx = computed<ChartBuildContext>(() => ({ narrow: isNarrow.value }))

const canvasRef = ref<HTMLCanvasElement | null>(null)
let chart: ChartJsInstance | null = null

/**
 * CDN 是否可用 —— 脚本没加载出来时为 false,展示降级文案。
 *
 * 用 ref + 轮询而不是 computed:window.Chart 不是响应式的,computed 只会在
 * 依赖变化时重算,脚本晚于组件挂载加载的话图表会永远卡在降级态。
 * index.html 里 script 没有 defer(理论上先于 Vue 挂载),这里轮询是兜底。
 */
const chartLibReady = ref(typeof window !== 'undefined' && !!window.Chart)
let libPollTimer: ReturnType<typeof setInterval> | null = null

function stopLibPoll(): void {
  if (libPollTimer) {
    clearInterval(libPollTimer)
    libPollTimer = null
  }
}

if (!chartLibReady.value) {
  let tries = 0
  libPollTimer = setInterval(() => {
    if (window.Chart) {
      chartLibReady.value = true
      stopLibPoll()
    } else if (++tries > 50) {
      // 约 5 秒还没等到 → 认定 CDN 不可用,停止轮询(保留降级文案)
      stopLibPoll()
    }
  }, 100)
}

/** 数据是否为空(空串空态文案 = 不启用空态判断) */
const isEmpty = computed(() => {
  if (!props.emptyText) return false
  return !Array.isArray(props.data) || props.data.length === 0
})

/**
 * 能不能画:库在 + 有数据。
 *
 * 注意这里**不含 loading** —— canvas 是常驻 DOM 的(加载中用遮罩盖住,
 * 不再 v-show 隐藏)。原因:canvas display:none 时 Chart.js 拿到的
 * 尺寸可能是 0×0,而且父容器高度是固定的,遮罩切换不会触发它的
 * ResizeObserver,图表会永久停在 0 尺寸不显示。
 */
const canRender = computed(() => chartLibReady.value && !isEmpty.value)

/** 是否需要盖遮罩(加载中 / 库没加载出来 / 没数据) */
const overlayKind = computed<'loading' | 'no-lib' | 'empty' | null>(() => {
  if (props.loading) return 'loading'
  if (!chartLibReady.value) return 'no-lib'
  if (isEmpty.value) return 'empty'
  return null
})

function destroyChart(): void {
  chart?.destroy()
  chart = null
}

function renderChart(): void {
  destroyChart()
  if (!canRender.value || !canvasRef.value || !window.Chart) return
  const config = props.build(props.data as never, palette.value, buildCtx.value)
  chart = new window.Chart(canvasRef.value, config)
}

onMounted(renderChart)
onBeforeUnmount(() => {
  destroyChart()
  stopLibPoll()
})

/* 数据变化(刷新按钮 / 重新拉取)→ 重建 */
watch(() => props.data, renderChart)
/* 主题切换 → palette 变 → 用新配色重建 */
watch(palette, renderChart)
/* 跨过窄屏断点(手机横竖屏 / 拖窗口)→ 用窄屏那套参数重建 */
watch(isNarrow, () => requestAnimationFrame(renderChart))
/* loading 结束 / 空态切换 → canvas 重新挂载,需要重建 */
watch(canRender, (ok) => {
  if (ok) {
    // 等一帧:确保父组件本次渲染已把 canvas 布局出来,Chart.js 才量得到尺寸
    requestAnimationFrame(renderChart)
  } else {
    destroyChart()
  }
})
</script>

<template>
  <article class="chart-card">
    <header class="chart-head">
      <h3 class="chart-title">{{ title }}</h3>
      <p v-if="subtitle" class="chart-sub">{{ subtitle }}</p>
    </header>

    <div class="chart-body" :style="{ height: `${height}px` }">
      <!-- canvas 常驻 DOM:Chart.js 在 display:none 下量不到尺寸,
           加载中/空态用遮罩盖住,而不是隐藏 canvas -->
      <canvas ref="canvasRef" class="chart-canvas" />

      <div v-if="overlayKind === 'loading'" class="chart-overlay">
        <el-skeleton :rows="3" animated class="chart-skeleton" />
      </div>

      <div v-else-if="overlayKind === 'no-lib'" class="chart-overlay chart-fallback">
        <el-icon><WarningFilled /></el-icon>
        <span>图表库未加载(/vendor/chart.umd.min.js 打不开)</span>
      </div>

      <div v-else-if="overlayKind === 'empty'" class="chart-overlay chart-fallback">
        <el-icon><DataLine /></el-icon>
        <span>{{ emptyText }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.chart-card {
  background: var(--color-card);
  border-radius: 14px;
  /* 与仪表盘其他卡片同一套分层阴影 —— 亮色靠两层投影,
     暗色靠压边,见 theme.css 顶部说明 */
  box-shadow:
    var(--shadow-card),
    0 0 0 1px var(--color-border);
  padding: 20px 20px 12px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chart-head {
  margin-bottom: 12px;
}

.chart-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  margin: 0 0 2px;
  color: var(--color-text);
}

.chart-sub {
  font-size: 12px;
  color: var(--color-text-soft);
  margin: 0;
}

.chart-body {
  position: relative;
  min-width: 0;
}

.chart-canvas {
  display: block;
  width: 100% !important;
  height: 100% !important;
}

.chart-skeleton {
  padding: 8px 4px;
}

/* 遮罩层 —— 盖住 canvas(加载中 / 库没加载 / 空态),卡片底色不透明 */
.chart-overlay {
  position: absolute;
  inset: 0;
  background: var(--color-card);
}

/* 空态 / 降级 —— 居中提示,不撑破卡片 */
.chart-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-text-soft);
  font-size: 13px;
  text-align: center;
  padding: 16px;
}

.chart-fallback .el-icon {
  font-size: 24px;
}

/* 窄屏:内边距收紧,把宽度让给 canvas —— 双轴折线图在手机上本来就挤 */
@media (max-width: 600px) {
  .chart-card {
    padding: 14px 12px 8px;
  }

  .chart-title {
    font-size: 15px;
  }
}
</style>

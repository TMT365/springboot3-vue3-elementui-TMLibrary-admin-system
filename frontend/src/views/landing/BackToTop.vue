<script setup lang="ts">
/**
 * BackToTop  -  滚动出现、点击回顶,外圈带阅读进度环
 *
 * <h2>为什么是"进度环"而不是一个光秃秃的箭头</h2>
 * <p>回顶按钮只回答"点我"一个问题,却占了右下角一块长期可见的位置。
 * 套一圈进度环之后,它顺带回答了"这页我看到哪了" —— 同样的面积多给一个信息,
 * 长页面(Landing 有 8 个区块)尤其明显。</p>
 *
 * <h2>性能:滚动里不读 scrollHeight</h2>
 * <p>算进度要拿"可滚动总高度",而 <code>scrollHeight</code> 是**布局属性** ——
 * 在 scroll 回调里读它会强制同步布局(布局抖动),而这个回调每帧都可能触发。
 * 所以拆成两条路:</p>
 * <ul>
 *   <li><b>尺寸变化</b> → ResizeObserver / resize 事件里算一次,缓存起来</li>
 *   <li><b>滚动</b> → 只读 <code>window.scrollY</code>(不触发布局),拿缓存做除法</li>
 * </ul>
 * <p>再加一层 rAF 节流:scroll 事件的触发频率高于屏幕刷新率,不节流就是白算几十次。</p>
 *
 * <h2>位置</h2>
 * <p>不写死 right/bottom —— 走 theme.css 的 --fab-* token,叠在 FeedbackFab
 * 上方(第 2 层)。这两个件以前撞在同一个角上,见 theme.css §1.4。</p>
 */
import { ref, onMounted, onUnmounted } from 'vue'

/** 与模板里 <circle r="20"> 对应 —— 改半径这里要一起改 */
const RADIUS = 20
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/** 滚动超过一屏才出现 —— 刚进页面就杵个按钮出来是噪音 */
const SHOW_AFTER_VIEWPORTS = 1

const visible = ref(false)
/** 0..1,喂给 CSS 的 --b2t-p */
const progress = ref(0)

/** 可滚动高度缓存 —— 只在尺寸变化时重算,不在 scroll 里读 */
let maxScroll = 0
/** rAF 句柄,兼作"本帧已排队"的标记 */
let frame = 0

function measure() {
  maxScroll = Math.max(
    document.documentElement.scrollHeight - window.innerHeight,
    0,
  )
}

function update() {
  frame = 0
  const y = window.scrollY
  visible.value = y > window.innerHeight * SHOW_AFTER_VIEWPORTS
  progress.value = maxScroll > 0 ? Math.min(y / maxScroll, 1) : 0
}

function onScroll() {
  if (frame) return
  frame = requestAnimationFrame(update)
}

/** 视口缩放会改 maxScroll,但 body 尺寸不一定变,所以 resize 要单独听 */
function onResize() {
  measure()
  onScroll()
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  // 观察 body 而不是 documentElement:内容增高时 body 的盒子必然变,
  // 是这里唯一需要重算 maxScroll 的时机
  resizeObserver = new ResizeObserver(onResize)
  resizeObserver.observe(document.body)

  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)

  measure()
  update()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
  // 已经排队的帧要撤掉 —— 否则组件销毁后还会写一次 ref
  if (frame) cancelAnimationFrame(frame)
})

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <transition name="b2t-fade">
    <button
      v-if="visible"
      class="back-to-top"
      type="button"
      :style="{ '--b2t-p': progress, '--b2t-c': CIRCUMFERENCE }"
      aria-label="返回顶部"
      title="返回顶部"
      @click="scrollTop"
    >
      <!--
        进度环。viewBox 固定 44×44、尺寸交给 CSS(100%),
        这样改了 --fab-size 环会跟着缩放,不用重算 r 和周长。
        aria-hidden:纯装饰,读屏念它没意义。
      -->
      <svg class="b2t-ring" viewBox="0 0 44 44" aria-hidden="true">
        <circle class="b2t-ring-track" cx="22" cy="22" :r="RADIUS" />
        <circle class="b2t-ring-bar" cx="22" cy="22" :r="RADIUS" />
      </svg>

      <el-icon class="b2t-icon"><Top /></el-icon>
    </button>
  </transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  /* 第 2 层:叠在 FeedbackFab 正上方 */
  right: var(--fab-edge);
  bottom: var(--fab-bottom-2);
  z-index: 50;

  width: var(--fab-size);
  height: var(--fab-size);
  display: grid;
  place-items: center;
  padding: 0;

  /* 颜色全部走 token —— 原来写死 rgba(250,248,243),暗色下是个白疙瘩 */
  background: var(--color-card);
  color: var(--color-text-muted);
  border: none;
  border-radius: 50%;
  cursor: pointer;

  /* 和站点其它卡片同一套"抬升"语言:外阴影定义高度,内高光定义受光棱边 */
  box-shadow: var(--shadow-card), var(--edge-highlight);
  transition:
    background 220ms ease,
    color 220ms ease,
    transform 220ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 220ms ease;
}

.back-to-top:hover {
  background: var(--color-accent);
  color: #fff;
  transform: translateY(-3px);
  box-shadow: var(--shadow-card-hover), var(--edge-highlight);
}

/* 按下时"压回去" —— translateY(0) 而不是保留 -3px,手感上才是真的按下去了 */
.back-to-top:active {
  transform: translateY(0) scale(0.94);
}

.back-to-top:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

/* ---------- 进度环 ---------- */
.b2t-ring {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  /* 从 12 点方向起画 —— SVG 圆的起点在 3 点方向,不转的话进度看着是歪的 */
  transform: rotate(-90deg);
}

.b2t-ring-track,
.b2t-ring-bar {
  fill: none;
  stroke-width: 2.5;
}

.b2t-ring-track {
  stroke: var(--color-border);
}

.b2t-ring-bar {
  stroke: var(--color-accent);
  stroke-linecap: round;
  /* --b2t-c = 2πr,由 JS 用同一个 RADIUS 算出来内联下发 ——
     半径只在一个地方定义,不会出现"改了 r 忘了改周长"。
     兜底值 = r20 的周长:万一内联样式没生效,环退化成"进度 0"而不是满圈。 */
  stroke-dasharray: var(--b2t-c, 125.66);
  /* --b2t-p 是 0..1 的无单位数 */
  stroke-dashoffset: calc(var(--b2t-c, 125.66) * (1 - var(--b2t-p, 0)));
}

/* hover 时整颗变绿,绿环压在绿底上等于消失 —— 所以环反白。
   进度信息没丢:白环在绿底上对比度比原来的绿环还高。 */
.back-to-top:hover .b2t-ring-track {
  stroke: rgba(255, 255, 255, 0.3);
}

.back-to-top:hover .b2t-ring-bar {
  stroke: #fff;
}

.b2t-icon {
  position: relative; /* 压在环之上 */
  font-size: 18px;
}

/* ---------- 出入场 ---------- */
.b2t-fade-enter-active,
.b2t-fade-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.b2t-fade-enter-from,
.b2t-fade-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.9);
}

/* 触摸设备没有 hover,进页面时给个常态反馈就好 */
@media (hover: none) {
  .back-to-top:active {
    background: var(--color-accent);
    color: #fff;
  }
}
</style>

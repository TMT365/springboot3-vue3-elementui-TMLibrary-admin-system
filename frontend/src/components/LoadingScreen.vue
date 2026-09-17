<script setup lang="ts">
/**
 * LoadingScreen  -  全局页面加载指示器
 *
 * 挂载在 App.vue 根组件,所有路由刷新时都会播一次(每次刷新都播,不做 session 去重)。
 * 加载期间 App.vue 用 v-if 隐藏 router-view,播完调 loadingStore.finish() 放行。
 *
 * 视觉:
 * - 屏幕中央 div 包裹,上下左右 padding 适中
 * - 3D 立体感圆环 + 内部 "Loading..." 文本(圆点逐个出现)
 * - 圆环下方 3 个圆点,从左到右循环高亮
 *
 * 时序(三段):
 *   1. 挂载 → 等 window.load(资源全就绪)
 *   2. 再多停 AFTER_LOAD_MS,避免"刚画完就消失"的仓促感
 *   3. 淡出 —— **淡出播完**(@after-leave)才调 finish() 放行路由内容
 *
 * ⚠️ 第 3 步的先后顺序是关键:早先的实现在 `visible = false` 的**同一时刻**
 *    就调了 finish(),于是内容立刻出现、而遮罩还在淡出 —— 看起来就是
 *    "页面先出来了,loading 还在"。必须等 after-leave。
 *
 * 另外设了最短展示时长:缓存命中时 window.load 几乎立刻触发,
 * 不设下限动画只闪 ~200ms,看起来像页面抽了一下,不像"加载中"。
 */
import { ref, onMounted } from 'vue'
import { useLoadingStore } from '@/stores/loading'

/** 动画最短展示时长(毫秒)—— 低于这个时长就不开始淡出 */
const MIN_VISIBLE_MS = 700
/** window.load 之后再停留多久才开始淡出 */
const AFTER_LOAD_MS = 200

const loadingStore = useLoadingStore()
const visible = ref(true)
const mountedAt = Date.now()

onMounted(() => {
  const beginFadeOut = () => {
    const elapsed = Date.now() - mountedAt
    const delay = Math.max(0, MIN_VISIBLE_MS - elapsed)
    setTimeout(() => {
      // 只负责开始淡出;放行内容交给模板上的 @after-leave
      visible.value = false
    }, delay)
  }

  // 等 window.load 页面真正加载完(资源全就绪)再收工
  if (document.readyState === 'complete') {
    setTimeout(beginFadeOut, AFTER_LOAD_MS)
  } else {
    window.addEventListener('load', () => setTimeout(beginFadeOut, AFTER_LOAD_MS), { once: true })
  }
})
</script>

<template>
  <!-- @after-leave:淡出动画彻底播完才放行路由内容,
       否则会出现"内容已渲染 + 遮罩还在淡出"的重叠 -->
  <transition name="loading-fade" @after-leave="loadingStore.finish()">
    <div v-if="visible" class="loading-screen" aria-hidden="true">
      <div class="loader">
        <div class="spinner">
          <div class="spinner-ring" />
          <span class="spinner-text"
            >Loading<span class="text-dot" /><span class="text-dot" /><span
              class="text-dot"
          /></span>
        </div>
        <div class="dots" role="presentation">
          <span class="dot" />
          <span class="dot" />
          <span class="dot" />
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ============================================================
 * 容器  -  全屏 fixed,flex 居中
 * ============================================================ */
.loading-screen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

/* ============================================================
 * Loader  -  包裹 div,padding 留呼吸,纵向 flex 排 spinner + dots
 * ============================================================ */
.loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 36px;
  padding: 40px;
}

/* ============================================================
 * Spinner  -  3D 立体感圆环 + 内部 Loading 文本
 *
 * 亮色:圆盘是白的,白色内高光压在白卡片上等于没有 —— 所以厚度
 *       只能靠「上沿内阴影 + 下沿内阴影 + 加重的双层外投影」来做。
 *   inset 0 3px 10px  -  上沿内阴影(盘面厚度上缘)
 *   inset 0 -3px 8px   -  下沿内阴影(盘面厚度下缘)
 *   0 24px 48px        -  主投影(远)
 *   0 8px 16px         -  接触投影(近)
 *   0 0 0 1px          -  accent 微光描边
 * ============================================================ */
.spinner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: var(--color-card);
  box-shadow:
    inset 0 3px 10px rgba(17, 25, 40, 0.10),
    inset 0 -3px 8px rgba(17, 25, 40, 0.05),
    0 24px 48px rgba(17, 25, 40, 0.20),
    0 8px 16px rgba(17, 25, 40, 0.10),
    0 0 0 1px rgba(76, 175, 80, 0.14);
}

:root[data-theme='dark'] .spinner {
  box-shadow:
    inset 0 3px 10px rgba(0, 0, 0, 0.3),
    inset 0 -2px 8px rgba(255, 255, 255, 0.04),
    0 24px 48px rgba(0, 0, 0, 0.5),
    0 8px 16px rgba(0, 0, 0, 0.3),
    0 0 0 1px rgba(102, 187, 106, 0.12);
}

.spinner-ring {
  position: absolute;
  inset: 14px;
  border-radius: 50%;
  border: 7px solid var(--color-text-soft);
  border-top-color: var(--color-accent);
  animation: spin 0.8s linear infinite;
  pointer-events: none;
}

.spinner-text {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1;
  color: var(--color-text-muted);
  text-transform: uppercase;
  white-space: nowrap;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.6);
  /* 视觉微调:Manrope 在 13px 时字形中线略偏下,补 1px 让 LOADING 文本看上去更居中 */
  position: relative;
  top: 1px;
}

:root[data-theme='dark'] .spinner-text {
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.4);
}

/* "Loading..." 文字内的点循环  -  从左到右逐个出现 */
.text-dot {
  display: inline-block;
  width: 4px;
  opacity: 0;
  animation: text-dot 1.5s ease-in-out infinite;
}

.text-dot:nth-of-type(2) {
  animation-delay: 0.2s;
}
.text-dot:nth-of-type(3) {
  animation-delay: 0.4s;
}

@keyframes text-dot {
  0%,
  80%,
  100% {
    opacity: 0;
  }
  40% {
    opacity: 1;
  }
}

/* ============================================================
 * Dots  -  圆环下方 3 个独立圆点,1.5s 循环,高亮相位错开 0.5s
 * ============================================================ */
.dots {
  display: flex;
  gap: 12px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-text-soft);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 2px 4px rgba(0, 0, 0, 0.1);
  animation: dot-pulse 1.5s ease-in-out infinite;
}

:root[data-theme='dark'] .dot {
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 2px 4px rgba(0, 0, 0, 0.3);
}

.dot:nth-child(2) {
  animation-delay: 0.5s;
}
.dot:nth-child(3) {
  animation-delay: 1s;
}

@keyframes dot-pulse {
  0%,
  60%,
  100% {
    background: var(--color-text-soft);
    transform: scale(1);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.4),
      0 2px 4px rgba(0, 0, 0, 0.1);
  }
  30% {
    background: var(--color-accent);
    transform: scale(1.3);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.5),
      0 0 12px rgba(76, 175, 80, 0.5),
      0 4px 8px rgba(0, 0, 0, 0.15);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ============================================================
 * Fade-out
 * ============================================================ */
.loading-fade-leave-active {
  transition: opacity 200ms ease-out;
}

.loading-fade-leave-to {
  opacity: 0;
}
</style>
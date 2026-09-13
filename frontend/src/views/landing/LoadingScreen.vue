<script setup lang="ts">
/**
 * LoadingScreen  -  页面加载指示器
 *
 * 视觉:
 * - 屏幕中央 div 包裹,上下左右 padding 适中
 * - 3D 立体感圆环 + 内部 "Loading..." 文本(圆点逐个出现)
 * - 圆环下方 3 个圆点,从左到右循环高亮
 *
 * 时长:
 * - 固定 5s 后开始淡出,不依赖页面加载事件
 *
 * 一次性:
 * - sessionStorage['tm_landing_loaded'],session 内只放一次
 * - 关 tab 重开会再播一次;同 tab 刷新不再播
 */
import { ref, onMounted } from 'vue'
import { useLoadingStore } from '@/stores/loading'

const STORAGE_KEY = 'tm_landing_loaded'
const loadingStore = useLoadingStore()

const alreadyPlayed = sessionStorage.getItem(STORAGE_KEY) === '1'
const visible = ref(!alreadyPlayed)

// 已 session 播过 → 跳过动画,直接告诉 store 进入主体内容
if (alreadyPlayed) {
  loadingStore.skip()
}

onMounted(() => {
  if (alreadyPlayed) return

  const onFinish = () => {
    visible.value = false
    loadingStore.finish()
    sessionStorage.setItem(STORAGE_KEY, '1')
  }

  // ============================================================
  // 开发环境:固定 5s(注释掉下面这块以切换到生产模式)
  // ============================================================
  setTimeout(onFinish, 5000)

  /*
  // ============================================================
  // 生产环境:等 window.load 页面真正加载完
  // ============================================================
  if (document.readyState === 'complete') {
    setTimeout(onFinish, 200)
  } else {
    window.addEventListener('load', () => setTimeout(onFinish, 200), { once: true })
  }
  */
})
</script>

<template>
  <transition name="loading-fade">
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
 * 多层 box-shadow 营造「浮起 + 内嵌 + 微光」3D 感:
 *   inset 0 3px 8px  -  上方凹陷阴影
 *   inset 0 -2px 6px -  下方高光
 *   0 20px 40px      -  主投影
 *   0 6px 12px       -  近投影
 *   0 0 0 1px        -  accent 微光描边
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
    inset 0 3px 10px rgba(0, 0, 0, 0.06),
    inset 0 -2px 8px rgba(255, 255, 255, 0.6),
    0 24px 48px rgba(0, 0, 0, 0.14),
    0 8px 16px rgba(0, 0, 0, 0.08),
    0 0 0 1px rgba(76, 175, 80, 0.1);
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
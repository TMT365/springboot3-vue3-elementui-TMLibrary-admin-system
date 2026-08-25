<script setup lang="ts">
/**
 * LoadingScreen  -  首屏加载动画
 *
 * - 绿色背景覆盖层
 * - 项目名 + 进度条
 * - 应用 ready 后 200ms 淡出
 * - v2 §7-#8:session 内只放一次,用 sessionStorage 标记
 *   (浏览器 tab 关掉重开会重新播放)
 */
import { ref, onMounted } from 'vue'

const STORAGE_KEY = 'tm_landing_loaded'

const alreadyPlayed = sessionStorage.getItem(STORAGE_KEY) === '1'
const visible = ref(!alreadyPlayed)
const progress = ref(0)

onMounted(() => {
  // 已经放过 → 直接结束,什么都不做
  if (alreadyPlayed) {
    progress.value = 100
    return
  }

  // 模拟加载进度(纯展示)
  const tick = setInterval(() => {
    progress.value = Math.min(100, progress.value + 8 + Math.random() * 12)
    if (progress.value >= 100) {
      clearInterval(tick)
      finishLoading()
    }
  }, 80)

  // 兜底:最多 2 秒后强制隐藏
  setTimeout(() => {
    progress.value = 100
    clearInterval(tick)
    finishLoading()
  }, 2000)
})

function finishLoading(): void {
  setTimeout(() => {
    visible.value = false
    sessionStorage.setItem(STORAGE_KEY, '1')
  }, 200)
}
</script>

<template>
  <transition name="loading-fade">
    <div v-if="visible" class="loading-screen" aria-hidden="true">
      <div class="loading-inner">
        <div class="loading-logo">T</div>
        <div class="loading-name">TMLibrary</div>
        <div class="loading-bar">
          <div class="loading-bar-fill" :style="{ width: progress + '%' }" />
        </div>
        <div class="loading-tag">正在准备您的图书馆…</div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.loading-screen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  background: #4caf50;
  color: #faf8f3;
}

.loading-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.loading-logo {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  background: #faf8f3;
  color: #4caf50;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 400;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.loading-name {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 28px;
  font-weight: 400;
  letter-spacing: 0.02em;
}

.loading-bar {
  width: 220px;
  height: 3px;
  background: rgba(250, 248, 243, 0.25);
  border-radius: 2px;
  overflow: hidden;
}

.loading-bar-fill {
  height: 100%;
  background: #faf8f3;
  border-radius: 2px;
  transition: width 120ms ease-out;
}

.loading-tag {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 13px;
  opacity: 0.85;
  letter-spacing: 0.02em;
}

.loading-fade-leave-active {
  transition: opacity 200ms ease-out;
}

.loading-fade-leave-to {
  opacity: 0;
}
</style>
<script setup lang="ts">
/**
 * LoadingScreen  -  全局页面加载指示器
 *
 * 挂载在 App.vue 根组件,所有路由刷新时都会播一次(每次刷新都播,不做 session 去重)。
 * 加载期间 App.vue 用 v-if 隐藏 router-view,播完调 loadingStore.finish() 放行。
 *
 * 视觉(与全站 AppSkeleton 骨架屏统一):
 * - 全屏一个淡淡的纸色遮罩 + 轻微毛玻璃,像"页面正在被拼起来"
 * - 中央一块品牌徽记(墨绿书本 glyph,和空状态同一个图形语言)
 * - 下方三条"内容条"用和列表骨架屏同一个 shimmer 动画,明暗自动跟随令牌
 * - 顶部一行小字提示"正在加载"
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
      <div class="loader" role="status" aria-label="正在加载页面">
        <span class="brand-glyph" aria-hidden="true" />
        <span class="loader-label">正在加载</span>
        <div class="loader-bars">
          <span class="loader-bar" />
          <span class="loader-bar" style="width: 72%" />
          <span class="loader-bar" style="width: 46%" />
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ============================================================
 * 容器  -  全屏 fixed,一个淡淡的纸色遮罩 + 轻微毛玻璃。
 * 用 --color-bg 而不是纯白:切到暗色主题时遮罩也随之变暗,不会闪白。
 * ============================================================ */
.loading-screen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--color-bg) 92%, transparent);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

/* ============================================================
 * Loader  -  纵向排:徽记 → 提示 → 三条内容条
 * ============================================================ */
.loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px;
  width: min(320px, 82vw);
}

/* 品牌徽记 —— 墨绿书本 glyph,和空状态、落地页 logo 同一个图形语言。
 *   mask 里的 SVG 就是 theme.css 空状态那枚书的描边路径(这里复刻一份,
 *   尺寸缩小到 44px)。stroke 用 %23000,经 mask 后只剩字形,靠 background 上色。 */
.brand-glyph {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23000' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 6.5C12 5 10.9 4 9.4 4H4.6A1.6 1.6 0 0 0 3 5.6v12.8A1.6 1.6 0 0 0 4.6 20h4.8c1.5 0 2.6 1 2.6 2.2 0-1.2 1.1-2.2 2.6-2.2h4.8a1.6 1.6 0 0 0 1.6-1.6V5.6A1.6 1.6 0 0 0 19.4 4h-4.8C13.1 4 12 5 12 6.5z'/%3E%3Cpath d='M12 6.5V22.2'/%3E%3C/svg%3E") no-repeat center / 24px;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23000' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M12 6.5C12 5 10.9 4 9.4 4H4.6A1.6 1.6 0 0 0 3 5.6v12.8A1.6 1.6 0 0 0 4.6 20h4.8c1.5 0 2.6 1 2.6 2.2 0-1.2 1.1-2.2 2.6-2.2h4.8a1.6 1.6 0 0 0 1.6-1.6V5.6A1.6 1.6 0 0 0 19.4 4h-4.8C13.1 4 12 5 12 6.5z'/%3E%3Cpath d='M12 6.5V22.2'/%3E%3C/svg%3E") no-repeat center / 24px;
}

/* 提示文字 */
.loader-label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--color-text-muted);
}

/* 三条"内容条" —— 和 AppSkeleton 同一个 shimmer 动画(theme.css 里的
 *   @keyframes app-skeleton-shimmer),同一套 paper 色,观感完全统一。 */
.loader-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
}

.loader-bar {
  height: 12px;
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-alt);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-border) 55%, transparent);
  overflow: hidden;
  position: relative;
}

.loader-bar::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(
    100deg,
    transparent 20%,
    color-mix(in srgb, var(--color-card) 55%, transparent) 50%,
    transparent 80%
  );
  animation: app-skeleton-shimmer 1.5s ease-in-out infinite;
  animation-delay: var(--skeleton-delay, 0ms);
}

/* ============================================================
 * Fade-out  -  淡出播完才放行路由内容(见模板 @after-leave)
 * ============================================================ */
.loading-fade-leave-active {
  transition: opacity 200ms ease-out;
}

.loading-fade-leave-to {
  opacity: 0;
}
</style>
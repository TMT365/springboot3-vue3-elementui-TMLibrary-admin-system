<script setup lang="ts">
/**
 * 404  -  editorial 风格,跟 Landing 视觉对齐
 *
 * - 大号 404 (DM Serif Display 96px,accent 色)
 * - 一句俏皮文案
 * - 回首页 CTA + 回退上一页(如有历史)
 */
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  router.push('/')
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<template>
  <div class="not-found">
    <div class="not-found-inner">
      <span class="not-found-tag">404</span>
      <h1 class="not-found-title">这里没有书架</h1>
      <p class="not-found-desc">
        你要找的页面不在我们的馆藏里。<br />
        要不要回到主展厅看看?
      </p>
      <div class="not-found-actions">
        <button class="cta-primary" @click="goHome">
          回首页
        </button>
        <button class="cta-ghost" @click="goBack">返回上一页</button>
      </div>
    </div>

    <div class="not-found-deco" aria-hidden="true">
      <div class="deco-circle deco-circle-1" />
      <div class="deco-circle deco-circle-2" />
    </div>
  </div>
</template>

<style scoped>
.not-found {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--color-bg, #faf8f3);
  color: var(--color-text, #212121);
  overflow: hidden;
  padding: 24px;
}

.not-found-inner {
  position: relative;
  z-index: 2;
  max-width: 520px;
  text-align: center;
}

.not-found-tag {
  display: inline-block;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(80px, 16vw, 128px);
  font-weight: 400;
  line-height: 1;
  color: var(--color-accent, #4caf50);
  letter-spacing: -0.04em;
  margin-bottom: 16px;
}

.not-found-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(24px, 4vw, 32px);
  font-weight: 400;
  letter-spacing: -0.02em;
  margin: 0 0 16px;
}

.not-found-desc {
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-text-muted, #616161);
  margin: 0 0 36px;
}

.not-found-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  justify-content: center;
}

.cta-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  background: var(--color-accent, #4caf50);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  border: none;
  border-radius: 22px;
  cursor: pointer;
  font-family: inherit;
  transition: background 200ms ease, transform 200ms ease;
}

.cta-primary:hover {
  background: var(--color-accent-hover, #43a047);
  transform: translateY(-1px);
}

.cta-arrow {
  transition: transform 200ms ease;
}

.cta-primary:hover .cta-arrow {
  transform: translateX(2px);
}

.cta-ghost {
  padding: 12px 24px;
  background: transparent;
  color: var(--color-text, #212121);
  font-size: 15px;
  font-weight: 500;
  border: 1px solid var(--color-border, #e8e5dc);
  border-radius: 22px;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 200ms ease, color 200ms ease;
}

.cta-ghost:hover {
  border-color: var(--color-accent, #4caf50);
  color: var(--color-accent, #4caf50);
}

.not-found-deco {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(76, 175, 80, 0.12) 0%,
    transparent 70%
  );
}

.deco-circle-1 {
  width: 360px;
  height: 360px;
  top: -100px;
  right: -120px;
}

.deco-circle-2 {
  width: 300px;
  height: 300px;
  bottom: -80px;
  left: -100px;
}
</style>
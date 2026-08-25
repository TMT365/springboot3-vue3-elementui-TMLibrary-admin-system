<script setup lang="ts">
/**
 * BackToTop  -  滚动出现、点击回顶
 *
 * - 滚动距离 > 1 个视口高度后显示
 * - 平滑滚动回顶部
 */
import { ref, onMounted, onUnmounted } from 'vue'

const visible = ref(false)

function onScroll() {
  visible.value = window.scrollY > window.innerHeight
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <transition name="b2t-fade">
    <button
      v-if="visible"
      class="back-to-top"
      aria-label="返回顶部"
      title="返回顶部"
      @click="scrollTop"
    >
      ↑
    </button>
  </transition>
</template>

<style scoped>
.back-to-top {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 50;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  background: rgba(250, 248, 243, 0.85);
  color: var(--color-accent, #4caf50);
  border: 1.5px solid var(--color-accent, #4caf50);
  border-radius: 50%;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: background 200ms ease, color 200ms ease, transform 200ms ease;
}

.back-to-top:hover {
  background: var(--color-accent, #4caf50);
  color: #fff;
  transform: translateY(-2px);
}

.b2t-fade-enter-active,
.b2t-fade-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.b2t-fade-enter-from,
.b2t-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .back-to-top {
    right: 16px;
    bottom: 16px;
    width: 40px;
    height: 40px;
    font-size: 16px;
  }
}
</style>
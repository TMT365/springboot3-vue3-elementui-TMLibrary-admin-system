/**
 * 全局加载状态  -  决定路由内容是否渲染
 *
 * 用法(App.vue 里):
 *   <LoadingScreen />                          <!-- 始终挂载,自己切换 store -->
 *   <router-view v-if="!store.isLoading" />    <!-- 加载期间不渲染,避免内容闪一下 -->
 *
 * 状态切换由 LoadingScreen 触发:动画播完(window.load + 200ms)→ finish()
 *
 * 默认 true(正在加载):即使 LoadingScreen 因任何原因没挂上,
 * 也不会让路由内容提前渲染出来(防御性)。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLoadingStore = defineStore('loading', () => {
  const isLoading = ref<boolean>(true)

  /** 动画播完 → 放行路由内容 */
  function finish(): void {
    isLoading.value = false
  }

  return { isLoading, finish }
})

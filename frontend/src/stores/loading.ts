/**
 * 全局加载状态  -  决定 Landing 的主体内容是否渲染
 *
 * 用法:
 *   <LoadingScreen />           <!-- 始终挂载,自己切换 store -->
 *   <main v-if="!store.isLoading">...</main>
 *
 * 状态切换由 LoadingScreen 触发:
 * - 已 session 播过一次(sessionStorage 命中)→ 立即 skip()
 * - 正常流程:动画结束 → finish()
 *
 * 默认 true(正在加载),这样即使 LoadingScreen 因任何原因没挂上,
 * 也不会让 Landing 内容提前渲染出来(防御性)。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLoadingStore = defineStore('loading', () => {
  const isLoading = ref<boolean>(true)

  /** 正常结束:动画播完,主体内容显示 */
  function finish(): void {
    isLoading.value = false
  }

  /** 跳过:已 session 播过,直接进入主体内容 */
  function skip(): void {
    isLoading.value = false
  }

  return { isLoading, finish, skip }
})

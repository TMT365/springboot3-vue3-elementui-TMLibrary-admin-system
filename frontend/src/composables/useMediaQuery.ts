/**
 * useMediaQuery —— 把 CSS 媒体查询暴露成响应式 ref
 *
 * 用途:布局需要"按屏幕宽度切换组件 prop"时(CSS 做不到)—— 典型场景是
 * Element Plus 表单的 label-position:窄屏要改成 top(标签占满一行),
 * 否则固定 label-width 会把输入区挤到没法用。
 *
 * 用法:
 *   const isNarrow = useMediaQuery('(max-width: 600px)')
 *   <el-form :label-position="isNarrow ? 'top' : 'right'" label-width="80px">
 *
 * 说明:
 *   - 监听在 onMounted 里注册(保证 window 存在),卸载时清理
 *   - 初始化前返回 false(桌面优先),挂载后立即纠正
 */

import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let mql: MediaQueryList | null = null

  const update = (): void => {
    if (mql) matches.value = mql.matches
  }

  onMounted(() => {
    mql = window.matchMedia(query)
    update()
    mql.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    mql?.removeEventListener('change', update)
  })

  return matches
}

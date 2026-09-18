/**
 * 浏览须知弹窗  -  登录成功后弹出,每次登录一次
 *
 * <h2>为什么状态放在内存里,而不是 localStorage / sessionStorage</h2>
 * 需求是「**每次登录**都弹一遍」,这个语义只有内存 ref 能准确表达:
 *   - localStorage:用户看过一次后被永久记住 → 变成「只弹一次」,不对。
 *   - sessionStorage:刷新 / 重开标签页都还在 → 用户当天第一次进来是登录,
 *     之后每次刷新都要再吃一遍弹窗 —— 那是「每开一个页面弹一次」,骚扰。
 *   - 内存 ref:登录这个动作发生时调 open(),刷新页面归零。
 *     用户刷新时并没有"重新登录",所以不该再弹;下次真登录时自然又弹。
 *
 * 反过来说,如果哪天要改成「每天第一次登录弹一次」,就在这里加一层
 * localStorage 的日期戳(存 YYYY-MM-DD,与今天不等才 open),组件不用动。
 *
 * 弹窗本体在 components/NoticeDialog.vue,挂在 App.vue 上(全局),
 * 所以登录后跳转到哪一页都会正常浮在上面。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNoticeStore = defineStore('notice', () => {
  const visible = ref(false)

  function open(): void {
    visible.value = true
  }

  function close(): void {
    visible.value = false
  }

  return { visible, open, close }
})

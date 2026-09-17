/**
 * IP 封禁提示 —— 状态 + 类型,只服务「风控封禁弹窗」这一条链路
 *
 * 为什么类型就近放在这里而不是 types/api.d.ts:
 *   IpBanInfo 只被 store / IpBanDialog / request.ts 三处使用,
 *   属于风控这一个功能的内部契约,跟着功能走更聚合。
 *
 * 为什么用 store 而不是直接弹 ElMessageBox:
 *   1. 弹窗要"尽量大"且带图标排版,定制化程度高,组件更好维护
 *   2. request.ts 是普通模块(不在组件树里),拿不到组件实例,
 *      只能通过共享状态通知挂在 App.vue 上的弹窗组件
 *
 * 触发链路:
 *   后端 IpRiskControlFilter 返回 429 + IpBanInfo
 *     → request.ts 识别 code === 429
 *       → useIpBanStore().show(info)
 *         → IpBanDialog 显示
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 封禁详情 —— 对应后端 dto/response/IpBanInfo.java */
export interface IpBanInfo {
  /** 被封禁的 IP */
  ip: string
  /** 触发原因摘要(如「60 秒内请求 151 次,超过阈值 150 次」) */
  reason: string
  /** 触发瞬间的请求数 */
  hitCount: number
  /** 封禁开始时间("yyyy-MM-ddTHH:mm:ss") */
  bannedAt: string
  /** 解封时间 */
  expiresAt: string
  /** 剩余秒数 —— 后端算好给的,前端不用处理时区 */
  remainingSeconds: number
}

export const useIpBanStore = defineStore('ipBan', () => {
  const visible = ref(false)
  const info = ref<IpBanInfo | null>(null)

  /**
   * 显示封禁弹窗。
   * 重复触发(用户关掉后又发了一次请求)只更新数据,不引起弹窗闪动。
   */
  function show(payload: IpBanInfo | null): void {
    if (!payload) return
    info.value = payload
    visible.value = true
  }

  function close(): void {
    visible.value = false
  }

  return { visible, info, show, close }
})

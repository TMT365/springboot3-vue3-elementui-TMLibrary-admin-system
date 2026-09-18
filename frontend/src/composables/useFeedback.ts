import type { FeedbackCategory, FeedbackPriority, FeedbackStatus } from '@/types/api'

/**
 * 反馈的展示元数据 —— 状态/分类/优先级到"中文文案 + 颜色"的映射。
 *
 * <p>抽出来的原因:三个页面(我的列表 / 管理员列表 / 详情)都要画同一套 chip,
 * 各写一份必然漂移(比如详情页把 RESOLVED 画成蓝的、列表画成绿的)。</p>
 */

/** 状态 —— 和后端 TINYINT 一一对应 */
export const FEEDBACK_STATUS = {
  0: { label: '待处理', type: 'info' as const, color: '#909399' },
  1: { label: '处理中', type: 'warning' as const, color: '#e6a23c' },
  2: { label: '已解决', type: 'success' as const, color: '#4caf50' },
  3: { label: '已关闭', type: 'info' as const, color: '#909399' },
} satisfies Record<FeedbackStatus, { label: string; type: 'info' | 'warning' | 'success' | 'danger'; color: string }>

/** 分类 —— 用不同颜色区分"是 bug 还是建议",扫一眼就能分类 */
export const FEEDBACK_CATEGORY = {
  BUG: { label: '缺陷', icon: 'WarningFilled', color: '#f56c6c' },
  FEATURE: { label: '建议', icon: 'MagicStick', color: '#9c27b0' },
  QUESTION: { label: '疑问', icon: 'QuestionFilled', color: '#2196f3' },
  OTHER: { label: '其他', icon: 'MoreFilled', color: '#909399' },
} satisfies Record<FeedbackCategory, { label: string; icon: string; color: string }>

/** 优先级 */
export const FEEDBACK_PRIORITY = {
  0: { label: '低', type: 'info' as const },
  1: { label: '普通', type: 'info' as const },
  2: { label: '高', type: 'warning' as const },
  3: { label: '紧急', type: 'danger' as const },
} satisfies Record<FeedbackPriority, { label: string; type: 'info' | 'warning' | 'danger' }>

/** 状态码 → 元数据(带兜底,防止后端加了新状态前端白屏) */
export function statusMeta(status: number) {
  return FEEDBACK_STATUS[status as FeedbackStatus] ?? FEEDBACK_STATUS[0]
}

/** 分类码 → 元数据 */
export function categoryMeta(category: string) {
  return FEEDBACK_CATEGORY[category as FeedbackCategory] ?? FEEDBACK_CATEGORY.OTHER
}

/** 优先级码 → 元数据 */
export function priorityMeta(priority: number) {
  return FEEDBACK_PRIORITY[priority as FeedbackPriority] ?? FEEDBACK_PRIORITY[1]
}

/**
 * 后端返回的是 LocalDateTime 的 ISO 串("2026-09-18T09:52:11")。
 * 拆成日期 + 时刻两段,时刻用等宽数字 —— 和订单列表一个处理方式。
 */
export function splitDateTime(s: string | null | undefined): { date: string; time: string } | null {
  if (!s) return null
  const [date, time] = s.replace('T', ' ').split(' ')
  return { date: date ?? '', time: (time ?? '').slice(0, 8) }
}

/**
 * 相对时间("3 分钟前") —— 反馈列表里比绝对时间戳更好扫。
 * 超过 7 天就退回绝对日期,因为"30 天前"不如"08-19"直观。
 */
export function relativeTime(s: string | null | undefined): string {
  const parts = splitDateTime(s)
  if (!parts) return '-'
  const then = new Date(`${parts.date}T${parts.time}`).getTime()
  if (Number.isNaN(then)) return `${parts.date} ${parts.time}`

  const diffMin = Math.floor((Date.now() - then) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay} 天前`
  return parts.date
}

/** 角色码 → 气泡上的身份标签 */
export function roleLabel(role: number): string {
  if (role === 2) return '老板'
  if (role === 1) return '管理员'
  return '我'
}

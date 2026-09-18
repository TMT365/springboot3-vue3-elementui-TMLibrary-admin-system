/**
 * 反馈工单 API —— 对齐 backend FeedbackController。
 *
 * 全部端点都在 /api/feedbacks/** 下,该前缀**不在** JwtAuthFilter 白名单里,
 * 所以每一个都要求登录(这是需求:"必须登录才能反馈")。
 *
 * 端点(6 个,与后端一一对应):
 *   POST   /api/feedbacks/mine              提交反馈
 *   GET    /api/feedbacks/mine              我的反馈列表(分页)
 *   GET    /api/feedbacks/{id}              详情 + 回复列表
 *   POST   /api/feedbacks/{id}/reply        追述 / 回复
 *   PATCH  /api/feedbacks/{id}/status       改状态/优先级(ADMIN/BOSS)
 *   GET    /api/feedbacks/all               全部反馈(ADMIN/BOSS)
 */

import { http } from '@/utils/request'
import type {
  FeedbackCreateRequest,
  FeedbackReplyRequest,
  FeedbackStatusRequest,
  FeedbackSummary,
  FeedbackView,
  PageResult,
} from '@/types/api'

const FEEDBACK_PATH = {
  MINE: '/api/feedbacks/mine',
  ALL: '/api/feedbacks/all',
  ROOT: '/api/feedbacks',
} as const

export const feedbackApi = {
  /** POST /api/feedbacks/mine —— 提交,返回新反馈 id */
  create: (body: FeedbackCreateRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: FEEDBACK_PATH.MINE,
      data: body,
    }),

  /** GET /api/feedbacks/mine —— 我的反馈(只看自己提的) */
  myList: (page = 1, size = 20): Promise<PageResult<FeedbackSummary>> =>
    http<PageResult<FeedbackSummary>>({
      method: 'GET',
      url: FEEDBACK_PATH.MINE,
      params: { page, size },
    }),

  /** GET /api/feedbacks/all —— 管理员看全部,支持状态/分类过滤 */
  allList: (params: {
    status?: number
    category?: string
    page?: number
    size?: number
  } = {}): Promise<PageResult<FeedbackSummary>> =>
    http<PageResult<FeedbackSummary>>({
      method: 'GET',
      url: FEEDBACK_PATH.ALL,
      params: { page: 1, size: 20, ...params },
    }),

  /** GET /api/feedbacks/{id} —— 详情 + 回复列表(一次拿全) */
  detail: (id: number): Promise<FeedbackView> =>
    http<FeedbackView>({
      method: 'GET',
      url: `${FEEDBACK_PATH.ROOT}/${id}`,
    }),

  /** POST /api/feedbacks/{id}/reply —— 追述/回复 */
  reply: (id: number, body: FeedbackReplyRequest): Promise<void> =>
    http<void>({
      method: 'POST',
      url: `${FEEDBACK_PATH.ROOT}/${id}/reply`,
      data: body,
    }),

  /** PATCH /api/feedbacks/{id}/status —— 改状态/优先级(仅管理员) */
  changeStatus: (id: number, body: FeedbackStatusRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `${FEEDBACK_PATH.ROOT}/${id}/status`,
      data: body,
    }),
}

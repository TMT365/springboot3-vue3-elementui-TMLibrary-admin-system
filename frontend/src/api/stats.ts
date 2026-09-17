/**
 * Stats 相关 API —— 目前只有仪表盘一个端点
 *
 * 鉴权:需要 Bearer(不在 JwtAuthFilter 白名单);后端还要求 ADMIN / BOSS
 */

import { http } from '@/utils/request'
import type { DashboardStatsResponse } from '@/types/api'

export const statsApi = {
  /**
   * GET /api/stats/dashboard?days=30
   *
   * @param days 统计窗口天数,后端夹到 [7, 90],默认 30
   */
  dashboard: (days = 30): Promise<DashboardStatsResponse> =>
    http<DashboardStatsResponse>({
      method: 'GET',
      url: '/api/stats/dashboard',
      params: { days },
    }),
}

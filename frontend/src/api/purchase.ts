/**
 * Purchase 相关 API  -  4 个端点
 *
 * 鉴权:全部需要 Bearer;下单时 userId 从 token 拿,DTO 不带
 */

import { http } from '@/utils/request'
import type { PurchaseRequest, PurchaseResponse } from '@/types/api'

export const purchaseApi = {
  /** POST /api/purchases  -  返回新订单 id */
  create: (body: PurchaseRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: '/api/purchases',
      data: body,
    }),

  /** GET /api/purchases/{id}  -  订单详情(本人或 BOSS) */
  getById: (id: number): Promise<PurchaseResponse> =>
    http<PurchaseResponse>({
      method: 'GET',
      url: `/api/purchases/${id}`,
    }),

  /** DELETE /api/purchases/{id}  -  取消订单并退库存(本人) */
  cancel: (id: number): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `/api/purchases/${id}`,
    }),

  /** PATCH /api/purchases/{id}/pay?paymentMethod= */
  pay: (id: number, paymentMethod: string = 'DEFAULT'): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/purchases/${id}/pay`,
      params: { paymentMethod },
    }),
}
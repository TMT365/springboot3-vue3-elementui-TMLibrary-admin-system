/**
 * Purchase 相关 API  -  4 个端点(对齐 API §5)
 *
 * 鉴权:全部需要 Bearer;下单时 userId 从 token 拿,DTO 不带
 *
 * 路径参数:orderNumber 是 Snowflake ID(Long),后端序列化为字符串,
 *  前端一律用 string 透传,不要再当 number 处理。
 */

import { http } from '@/utils/request'
import type { OrderListItem, PageResult, PurchaseRequest, PurchaseResponse } from '@/types/api'

export const purchaseApi = {
  /** POST /api/purchases  -  返回新订单 id(API §5.1) */
  create: (body: PurchaseRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: '/api/purchases',
      data: body,
    }),

  /**
   * GET /api/purchases?page=&size=  -  全量订单分页(管理端,仅 ADMIN / BOSS)
   * 后端按 id 倒序返回,每行只有订单主体、不含明细
   */
  listOrders: (page = 1, size = 10): Promise<PageResult<OrderListItem>> =>
    http<PageResult<OrderListItem>>({
      method: 'GET',
      url: '/api/purchases',
      params: { page, size },
    }),

  /** GET /api/purchases/{orderNumber}  -  订单详情(API §5.2,仅订单所有者) */
  getById: (orderNumber: string): Promise<PurchaseResponse> =>
    http<PurchaseResponse>({
      method: 'GET',
      url: `/api/purchases/${orderNumber}`,
    }),

  /** DELETE /api/purchases/{orderNumber}  -  取消订单并释放预占(API §5.3) */
  cancel: (orderNumber: string): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `/api/purchases/${orderNumber}`,
    }),

  /**
   * PATCH /api/purchases/{orderNumber}/pay?paymentMethod=  -  付款扣 DB 库存(API §5.4)
   *
   * paymentMethod 传 PaymentMethod 枚举的 code(WECHAT / ALIPAY / QQ)。
   * 这里**不给默认值** —— 以前默认 'DEFAULT' 是个占位值,后端现在会把它当非法值拒掉(400);
   * 而且支付方式本来就该由用户在弹窗里明确选,不该有个隐式默认。
   */
  pay: (orderNumber: string, paymentMethod: string): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/purchases/${orderNumber}/pay`,
      params: { paymentMethod },
    }),
}
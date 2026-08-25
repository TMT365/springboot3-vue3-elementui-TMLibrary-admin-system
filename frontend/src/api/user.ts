/**
 * User 相关 API  -  8 个端点
 *
 * 鉴权:全部需要 Bearer(register/login 后端虽匿名,但前端 P4 暂不开放注册入口)
 * 后端响应:User 实体含 passwordHash 等敏感字段,这里返回类型用 UserDto 屏蔽,
 *         并通过 mapSafeUser() 兜底运行时剥离
 */

import { http } from '@/utils/request'
import { mapSafeUser } from '@/utils/safeUser'
import type {
  LoginRequest,
  LoginResponse,
  PageResult,
  PurchaseResponse,
  UserDeleteRequest,
  UserDto,
  UserPasswordRequest,
  UserRegisterRequest,
  UserSearchRequest,
  UserUpdatedRequest,
} from '@/types/api'

export const userApi = {
  /** POST /api/users/register  -  当前被 JWT 过滤器挡住(白名单缺失),P4 不开放注册入口 */
  register: (body: UserRegisterRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: '/api/users/register',
      data: body,
    }),

  /** POST /api/users/login  -  同上,不在 JWT 白名单,前端不调,仅类型完整 */
  login: (body: LoginRequest): Promise<LoginResponse> =>
    http<LoginResponse>({
      method: 'POST',
      url: '/api/users/login',
      data: body,
    }),

  /** GET /api/users/list  -  多条件查询 */
  list: (query: UserSearchRequest): Promise<PageResult<UserDto>> =>
    http<PageResult<UserDto>>({
      method: 'GET',
      url: '/api/users/list',
      params: query,
    }).then((page) => ({
      total: page.total,
      data: page.data.map(mapSafeUser),
    })),

  /** GET /api/users/{id} */
  getById: (id: number): Promise<UserDto> =>
    http<UserDto>({
      method: 'GET',
      url: `/api/users/${id}`,
    }).then(mapSafeUser),

  /** PATCH /api/users/{id}  -  更新用户(请求体里的 id 字段会被后端忽略,以 URL 为准) */
  update: (id: number, body: UserUpdatedRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/users/${id}`,
      data: body,
    }),

  /** DELETE /api/users/{id}  -  需要密码二次确认 */
  delete: (id: number, body: UserDeleteRequest): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `/api/users/${id}`,
      data: body,
    }),

  /** PATCH /api/users/{id}/password  -  仅本人可改 */
  changePassword: (id: number, body: UserPasswordRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/users/${id}/password`,
      data: body,
    }),

  /** GET /api/users/{id}/purchases  -  本人或 BOSS 可查 */
  listPurchases: (id: number): Promise<PurchaseResponse[]> =>
    http<PurchaseResponse[]>({
      method: 'GET',
      url: `/api/users/${id}/purchases`,
    }),
}
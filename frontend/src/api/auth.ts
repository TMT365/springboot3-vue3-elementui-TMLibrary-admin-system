/**
 * 鉴权相关 API  -  登录 + 注册
 *
 * 路径约定:
 *   POST /api/auth/login      -  AuthController 唯一匿名端点(LoginResponse: token + username + role)
 *   POST /api/users/register  -  UserController 注册端点(P5 白名单已通)
 */

import { http } from '@/utils/request'
import type { LoginRequest, LoginResponse, UserRegisterRequest } from '@/types/api'

export const authApi = {
  /** POST /api/auth/login  -  唯一匿名端点 */
  login: (body: LoginRequest): Promise<LoginResponse> =>
    http<LoginResponse>({
      method: 'POST',
      url: '/api/auth/login',
      data: body,
    }),

  /** POST /api/users/register  -  注册新用户,返回新用户 id */
  register: (body: UserRegisterRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: '/api/users/register',
      data: body,
    }),
}
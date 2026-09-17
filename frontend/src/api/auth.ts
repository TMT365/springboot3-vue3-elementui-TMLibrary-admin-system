/**
 * 鉴权相关 API  -  登录 / 注册 / 验证码 / 登出
 *
 * 路径(API §1.3 白名单):
 *   POST /api/captcha/login?uuid=<uuid>     -  登录验证码(返回 JSON)
 *   POST /api/captcha/register?uuid=<uuid>  -  注册验证码(返回 JSON,新增)
 *   POST /api/users/register                -  注册(需 captcha + uuid)
 *   POST /api/users/login                   -  登录(需 captcha + uuid)
 *   POST /api/users/logout                  -  登出
 *
 * 验证码响应(2026-09):从 image/png 二进制改为 JSON 对象,内含
 *  base64 data URI 和过期时间戳,前端可以做倒计时。
 */

import { http } from '@/utils/request'
import type {
  CaptchaResponse,
  GetCaptchaRequest,
  LoginRequest,
  LoginResponse,
  LogoutResponse,
  UserRegisterRequest,
} from '@/types/api'

export const authApi = {
  /**
   * POST /api/captcha/login?uuid=<uuid>
   * 响应:Result<CaptchaResponse> —— 走 http() 解包,Result.data 是 CaptchaResponse
   */
  loginCaptcha: (uuid: string, body: GetCaptchaRequest): Promise<CaptchaResponse> =>
    http<CaptchaResponse>({
      method: 'POST',
      url: `/api/captcha/login?uuid=${encodeURIComponent(uuid)}`,
      data: body,
    }),

  /**
   * POST /api/captcha/register?uuid=<uuid>  -  注册验证码(2026-09 新增)
   * Redis 路径跟 login 分开:tmlibrary:captcha:register:{uuid}:code
   * 登录 captcha 不能拿去注册,反之亦然。
   */
  registerCaptcha: (uuid: string, body: GetCaptchaRequest): Promise<CaptchaResponse> =>
    http<CaptchaResponse>({
      method: 'POST',
      url: `/api/captcha/register?uuid=${encodeURIComponent(uuid)}`,
      data: body,
    }),

  /** POST /api/users/register  -  白名单,注册新用户,返回新用户 id */
  register: (body: UserRegisterRequest): Promise<number> =>
    http<number>({
      method: 'POST',
      url: '/api/users/register',
      data: body,
    }),

  /** POST /api/users/login  -  需 captcha + uuid(API §2.3) */
  login: (body: LoginRequest): Promise<LoginResponse> =>
    http<LoginResponse>({
      method: 'POST',
      url: '/api/users/login',
      data: body,
    }),

  /** POST /api/users/logout  -  写 jti 黑名单(API §2.4) */
  logout: (): Promise<LogoutResponse> =>
    http<LogoutResponse>({
      method: 'POST',
      url: '/api/users/logout',
    }),
}

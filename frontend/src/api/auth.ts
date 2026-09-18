/**
 * 鉴权相关 API  -  登录 / 注册 / 验证码 / 登出
 *
 * 路径(API §1.3 白名单):
 *   POST /api/captcha/login?uuid=<uuid>     -  登录验证码(返回 JSON)
 *   POST /api/captcha/register?uuid=<uuid>  -  注册验证码(返回 JSON,新增)
 *   POST /api/users/register                -  注册(需 captcha + uuid)
 *   POST /api/users/login                   -  登录(需 captcha + uuid)
 *   POST /api/users/logout                  -  登出
 *   POST /api/users/forgot-password         -  申请密码重置(公开,永远 200)
 *   POST /api/users/reset-password          -  凭令牌重置密码(公开,会 400)
 *
 * 验证码响应(2026-09):从 image/png 二进制改为 JSON 对象,内含
 *  base64 data URI 和过期时间戳,前端可以做倒计时。
 */

import { http } from '@/utils/request'
import type {
  CaptchaResponse,
  ForgotPasswordRequest,
  GetCaptchaRequest,
  LoginRequest,
  LoginResponse,
  LogoutResponse,
  ResetPasswordRequest,
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

  /**
   * POST /api/users/forgot-password  -  申请密码重置(公开)
   *
   * ⚠️ 后端**永远返回 200**:邮箱没注册 / 命中多个账号 / 正常发送,三种情况
   *    响应完全一样(Service 内部静默处理,只在日志里区分)。这是防用户枚举的
   *    设计,不是 bug —— 所以调用方**不能**用"成功与否"推断邮箱是否存在,
   *    UI 文案对已注册 / 未注册必须一模一样。
   *    真正的失败只有网络错误 / 被限流这类,由 request.ts 统一 toast。
   */
  forgotPassword: (email: string): Promise<void> =>
    http<void>({
      method: 'POST',
      url: '/api/users/forgot-password',
      data: { email } satisfies ForgotPasswordRequest,
    }),

  /**
   * POST /api/users/reset-password  -  凭邮件里的令牌重置密码(公开)
   *
   * 这个**会失败**:令牌无效 / 过期 / 已用过,或者新密码与原密码相同,
   * 后端返回 400 且 msg 写明原因。错误壳由 request.ts 解包成 ApiError
   * (msg 即后端文案),页面原样展示给用户 —— 用户必须知道到底改没改成。
   */
  resetPassword: (token: string, newPassword: string): Promise<void> =>
    http<void>({
      method: 'POST',
      url: '/api/users/reset-password',
      data: { token, newPassword } satisfies ResetPasswordRequest,
    }),
}

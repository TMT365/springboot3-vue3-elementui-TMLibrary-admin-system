/**
 * HTTP 单一入口  -  所有 API 模块都走 `http<T>()`
 *
 * 职责:
 * 1. 注入 Authorization 头(request interceptor)
 * 2. 解包 Result<T> 壳 → caller 拿到干净的 T
 * 3. 401 处理:清 store + 跳 /login?redirect=...(防并发重入)
 * 4. 业务错误:后端返回真实 HTTP 状态码,此处解包响应体 → ElMessage.error + 抛 ApiError
 * 5. 网络错误:ElMessage.error + 抛 ApiError
 *
 * 单向依赖避免环:request.ts → router + stores/user;stores/user.ts 不引 request.ts
 *
 * 类型约定:`http<T>(config)` 返回 `Promise<T>`,T 即后端 Result.data 的类型。
 */

import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import { useIpBanStore, type IpBanInfo } from '@/stores/ipBan'
import type { Result } from '@/types/api'


export class ApiError extends Error {
  constructor(
    public code: number,
    public msg: string,
  ) {
    super(msg)
    this.name = 'ApiError'
  }
}

const requestInstance: AxiosInstance = axios.create({
  timeout: 15000,
})

// ============== Request 拦截器:自动塞 Token ==============
requestInstance.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.set('Authorization', `Bearer ${userStore.token}`)
  } 
  return config
})

// ============== 认证类接口:401 是"这次没通过",不是"登录态过期" ==============
/**
 * 这几个接口本来就是**未登录时**调用的(它们自己在鉴权白名单里)。
 * 它们返回 401 表示"你这次提交的凭据不对",而不是"你的登录态失效了"。
 *
 * <h2>为什么必须区分</h2>
 * 不区分的话,登录密码输错会走进 handle401():清 store → 跳 /login →
 * 抛一个写死的 `ApiError(401, '未登录')`,**顺手把后端真正的原因丢掉**,
 * 而且 handle401 里没有 ElMessage —— 结果就是**一个字都不弹**,
 * 用户点了登录只看到页面闪一下,完全不知道发生了什么。
 *
 * 后端对"用户不存在"和"密码错误"故意返回同一句文案(防账号枚举),
 * 那句文案本身是对的,只是以前根本传不到用户眼前。
 */
const AUTH_ENDPOINTS: readonly string[] = [
  '/api/users/login',
  '/api/users/register',
  '/api/users/forgot-password',
  '/api/users/reset-password',
]

function isAuthEndpoint(url: string | undefined): boolean {
  if (!url) return false
  return AUTH_ENDPOINTS.some((p) => url.startsWith(p))
}

// ============== 401 重定向防并发 ==============
let isRedirecting = false

function handle401(): never {
  if (!isRedirecting) {
    isRedirecting = true
    const userStore = useUserStore()
    userStore.logout()
    const target = router.currentRoute.value.fullPath
    router
      .push(`/login?redirect=${encodeURIComponent(target)}`)
      .finally(() => {
        setTimeout(() => {
          isRedirecting = false
        }, 500)
      })
  }
  throw new ApiError(401, '未登录')
}

// ============== Result<T> 解包 ==============
function unwrap<T>(body: Result<T> | unknown, authEndpoint = false): T {
  if (body && typeof body === 'object' && 'code' in body) {
    const result = body as Result<T>
    if (result.code === 200) return result.data as T
    if (result.code === 401) {
      /* 认证类接口:401 = 本次凭据不对 → 当普通业务错误处理(弹后端文案、不登出、不跳转) */
      if (authEndpoint) {
        ElMessage.error(result.msg || '用户名或密码错误')
        throw new ApiError(result.code, result.msg)
      }
      return handle401()
    }
    // 429 = IP 被风控封禁:弹大尺寸封禁弹窗(挂在 App.vue 上的 IpBanDialog),
    // 不再叠一个 toast —— 弹窗本身已经把信息说清了
    if (result.code === 429) {
      useIpBanStore().show(result.data as IpBanInfo | null)
      throw new ApiError(result.code, result.msg)
    }
    ElMessage.error(result.msg || '请求失败')
    throw new ApiError(result.code, result.msg)
  }
  // 防御:后端返回非 Result 壳(目前没有,P5 联调期间可能出现)
  return body as T
}

// ============== 单一 HTTP 入口 ==============
/**
 * API 函数统一走这个。返回 `Promise<T>`(已解包),`T` 即后端 `Result.data` 的类型。
 *
 * @example
 *   async function getUser(id: number) {
 *     return http<UserDto>({ method: 'GET', url: `/api/users/${id}` })
 *   }
 */
export async function http<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  const authEndpoint = isAuthEndpoint(config.url)
  try {
    const resp = await requestInstance.request<Result<T>>(config)
    return unwrap<T>(resp.data, authEndpoint)
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status
      const body = err.response?.data
      // 后端已改为返回真实 HTTP 状态码,错误响应体仍是统一的 Result 壳。
      // 交给 unwrap 处理:它会展示 result.msg、处理 401、并抛出带业务码的 ApiError。
      if (body && typeof body === 'object' && 'code' in body) {
        return unwrap<T>(body, authEndpoint)
      }
      // 过滤器写的错误是裸 Result 壳之外的场景(理论上不会走到)
      if (status === 401) {
        if (authEndpoint) {
          ElMessage.error('用户名或密码错误')
          throw new ApiError(401, '用户名或密码错误')
        }
        return handle401()
      }
      ElMessage.error(err.message || '网络错误')
      throw new ApiError(status ?? 0, err.message)
    }
    if (err instanceof ApiError) throw err
    ElMessage.error('未知错误')
    throw new ApiError(0, String(err))
  }
}

export default requestInstance
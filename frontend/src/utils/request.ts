/**
 * HTTP 单一入口  -  所有 API 模块都走 `http<T>()`
 *
 * 职责:
 * 1. 注入 Authorization 头(request interceptor)
 * 2. 解包 Result<T> 壳 → caller 拿到干净的 T
 * 3. 401 处理:清 store + 跳 /login?redirect=...(防并发重入)
 * 4. 业务错误:ElMessage.error + 抛 ApiError
 * 5. 网络错误:ElMessage.error + 抛 ApiError
 *
 * 单向依赖避免环:request.ts → router + stores/user;stores/user.ts 不引 request.ts
 *
 * 类型约定:`http<T>(config)` 返回 `Promise<T>`,T 即后端 Result.data 的类型。
 */

import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import router from '@/router'
import { useUserStore } from '@/stores/user'
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
function unwrap<T>(body: Result<T> | unknown): T {
  if (body && typeof body === 'object' && 'code' in body) {
    const result = body as Result<T>
    if (result.code === 200) return result.data as T
    if (result.code === 401) return handle401()
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
  try {
    const resp = await requestInstance.request<Result<T>>(config)
    return unwrap<T>(resp.data)
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status
      // Filter 写的 401 是裸 HTTP 401,没有 Result 壳
      if (status === 401) return handle401()
      ElMessage.error(err.message || '网络错误')
      throw new ApiError(status ?? 0, err.message)
    }
    if (err instanceof ApiError) throw err
    ElMessage.error('未知错误')
    throw new ApiError(0, String(err))
  }
}

export default requestInstance
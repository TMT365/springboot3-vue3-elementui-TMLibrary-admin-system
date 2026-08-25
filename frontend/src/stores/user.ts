/**
 * 用户状态  -  token / username / role / userId + 持久化
 *
 * token 存 localStorage,store init 时读回(刷新页面保留登录态)
 *
 * userId / username / role 不单独持久化  -  全部从 JWT payload 解码出来,
 * JWT 由后端 JwtService 用 HS256 签发,claims 含 { uid, sub (username), role }。
 * 好处:刷新页面 / 多 tab 同步时不需要额外请求就能拿到完整身份。
 */

import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserRole } from '@/types/api'

const TOKEN_KEY = 'tm_admin_token'

function loadToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function saveToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

/**
 * 解码 JWT payload(只解析中间那段 base64url,不做签名校验  -  服务端 JwtAuthFilter 已经校验过)
 * 后端 claim 约定: uid (number), sub (username), role ('USER' | 'ADMIN' | 'BOSS')
 */
function decodeJwtPayload(token: string): {
  uid?: number
  sub?: string
  role?: string
} | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    // base64url → base64
    const b64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64))
  } catch {
    return null
  }
}

function initFromToken(token: string | null): {
  username: string
  role: UserRole | ''
  userId: number | null
} {
  if (!token) return { username: '', role: '', userId: null }
  const claims = decodeJwtPayload(token)
  return {
    username: claims?.sub ?? '',
    role: (claims?.role as UserRole) ?? '',
    userId: claims?.uid ?? null,
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(loadToken())
  // 初始化时从 JWT 还原 username / role / userId,刷新页面也能保留
  const initial = initFromToken(token.value)
  const username = ref<string>(initial.username)
  const role = ref<UserRole | ''>(initial.role)
  const userId = ref<number | null>(initial.userId)

  const isAuthenticated = computed(() => !!token.value)
  // ADMIN 和 BOSS 都视为管理端可访问用户管理页
  const isAdmin = computed(() => role.value === 'ADMIN' || role.value === 'BOSS')

  function setLogin(payload: { token: string; username: string; role: UserRole }): void {
    token.value = payload.token
    username.value = payload.username
    role.value = payload.role
    // 从 JWT 拿 uid  -  后端 LoginResponse 不带 id,直接解析 token 更快
    const claims = decodeJwtPayload(payload.token)
    userId.value = claims?.uid ?? null
    saveToken(payload.token)
  }

  function logout(): void {
    token.value = null
    username.value = ''
    role.value = ''
    userId.value = null
    saveToken(null)
  }

  return { token, username, role, userId, isAuthenticated, isAdmin, setLogin, logout }
})
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
import { authApi } from '@/api/auth'
import type { LogoutResponse, UserRole } from '@/types/api'

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
 * 后端 claim 约定: uid (number), sub (username), role (Integer code)
 *
 * ⚠️ role 是 Integer code(0/1/2),不是枚举名 —— 见 backend JwtService.issue 的
 *    `.claim("role", user.getRole())`,user.getRole() 是 Integer。
 *    这两处的类型断言必须走 normalizeRole(),否则运行时会拿到数字。
 */
/**
 * base64url → UTF-8 字符串。
 *
 * <h2>为什么不能直接 atob() 之后就 JSON.parse</h2>
 * {@code atob()} 返回的是「<b>一个字符 = 一个字节</b>」的 Latin-1 字符串,而 JWT
 * 里的 payload 是 <b>UTF-8</b> 编码的 JSON。两者对不上时,非 ASCII 字符会被逐字节
 * 当成 Latin-1 解读,中文直接变成乱码:
 * <pre>
 *   {"sub":"张三"}  →  atob 解出  "å¼ ä¸‰"
 * </pre>
 * 这就是「中文用户名在顶栏显示乱码」的根因 —— 而且只乱在顶栏:
 * 登录成功时那句「欢迎,张三」用的是<b>响应体</b>(axios 走 JSON.parse,UTF-8 正确),
 * 顶栏用的是 <b>JWT</b>(走这里),所以同一个用户名两处显示不一样。
 *
 * 正确做法:先还原成字节数组,再按 UTF-8 解码。
 * TextDecoder 从 2016 年起就是各浏览器基线能力(Vue 3 本身要求的环境都满足),
 * 比 crypto.randomUUID 那种还挑安全上下文的 API 稳得多。
 */
function base64UrlDecode(input: string): string {
  const b64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(b64)
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

function decodeJwtPayload(token: string): {
  uid?: number
  sub?: string
  role?: unknown
} | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    return JSON.parse(base64UrlDecode(payload))
  } catch {
    return null
  }
}

/** 后端 UserRole 枚举的 Integer code 映射(对应 backend UserRole.java) */
const ROLE_CODE_TO_NAME: Record<number, UserRole> = {
  0: 'USER',
  1: 'ADMIN',
  2: 'BOSS',
}

/**
 * 归一化 role —— 后端两处返回的形状不同,前端必须都认:
 *   - LoginResponse.role → 字符串枚举名("ADMIN",Jackson 序列化 enum)
 *   - JWT claim role      → Integer code(1,JwtService 直接放 user.getRole())
 *
 * 不归一化的后果:登录后 isAdmin 为 true,页面一刷新(store 从 JWT 重建)
 * isAdmin 变 false —— 下拉菜单的「进入管理后台」消失,路由守卫还会把
 * 管理员从 /admin/* 踢回 /user/books。
 */
function normalizeRole(raw: unknown): UserRole | '' {
  if (raw === 'USER' || raw === 'ADMIN' || raw === 'BOSS') return raw
  if (typeof raw === 'number' && raw in ROLE_CODE_TO_NAME) return ROLE_CODE_TO_NAME[raw]
  return ''
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
    role: normalizeRole(claims?.role),
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
    // 走 normalizeRole 兜底:LoginResponse 给字符串,但万一后端改动/类型漂移也能接住
    role.value = normalizeRole(payload.role)
    // 从 JWT 拿 uid  -  后端 LoginResponse 不带 id,直接解析 token 更快
    const claims = decodeJwtPayload(payload.token)
    userId.value = claims?.uid ?? null
    saveToken(payload.token)
  }

  /**
   * 只改展示用的用户名。
   *
   * <p>用户在「个人中心 设置」里改了用户名后,顶栏还显示旧名字 —— 因为 username 是登录时
   * 从 JWT 里解出来的,改库不会动 token。这里让 UI 立刻跟上;真正的 token 换发要等下次登录。</p>
   */
  function setUsername(name: string): void {
    username.value = name
  }

  /** 同步清本地态 —— 401 / 调试 / 兜底场景用,不会调后端 */
  function logout(): void {
    token.value = null
    username.value = ''
    role.value = ''
    userId.value = null
    saveToken(null)
  }

  /**
   * 异步登出 —— 调后端注销 token(jti 黑名单),再清本地态。
   * 后端失败也不阻塞清本地(token 已无效的话再调也没用,反而死循环)。
   * 返回后端响应,UI 可拿 redirectUrl 决定跳哪里。
   */
  async function signOut(): Promise<LogoutResponse | null> {
    let resp: LogoutResponse | null = null
    try {
      resp = await authApi.logout()
    } catch {
      // 吞掉 —— token 已失效 / 网络断,本地该清还得清
    }
    logout()
    return resp
  }

  return { token, username, role, userId, isAuthenticated, isAdmin, setLogin, setUsername, logout, signOut }
})
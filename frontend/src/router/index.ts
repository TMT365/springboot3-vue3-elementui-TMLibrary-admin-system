/**
 * 路由守卫:鉴权 + 角色控制
 *
 * - public 页面直接放行(/login、404)
 * - 未登录访问受保护路由 → /login?redirect=...
 * - meta.admin 路由要求 role 是 ADMIN 或 BOSS,否则回 /dashboard
 */

import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { routes } from './routes'

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
/*
*用户点击 <router-link> 或 router.push('/xxx')
↓
┌─ beforeRouteLeave（当前组件，离开前）── 可以阻止
├─ beforeEach（全局前置守卫）── 可以阻止
├─ beforeRouteUpdate（如果组件复用）
├─ beforeEnter（路由配置里的守卫）
├─ 异步组件加载（如果有）
├─ beforeRouteEnter（目标组件，进入前）
└─ beforeResolve（全局，组件解析后）── 最后一次阻止机会 ↓
导航确认（地址栏 URL 变了）
↓
┌─ afterEach（全局，无返回值意义）
↓
DOM 更新
↓
组件 mounted
*/

router.beforeEach((to) => {
  const userStore = useUserStore()

  // 公开页放行
  if (to.meta.public) return true

  // 未登录 → 跳登录,带 redirect
  if (!userStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // admin 路由要求 ADMIN / BOSS  -  普通用户踢到 /user/books
  if (to.meta.admin && !userStore.isAdmin) {
    return { path: '/user/books' }
  }

  return true
})

/**
 * 同步 document.title  -  meta.title 决定了页面顶栏文案,
 * 但浏览器标签页标题以前永远是「TMLibrary  -  图书管理系统」(写死在 index.html)。
 * 这里在每次路由切换后覆盖一次,默认 fallback 保持原值。
 */
router.afterEach((to) => {
  const pageTitle = to.meta.title as string | undefined
  document.title = pageTitle ? `${pageTitle} · TMLibrary` : 'TMLibrary  -  图书管理系统'
})

export default router
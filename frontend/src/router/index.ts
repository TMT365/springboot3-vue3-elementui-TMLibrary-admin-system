/**
 * 路由守卫:鉴权 + 角色控制
 *
 * - public 页面直接放行(/login、404)
 * - 未登录访问受保护路由 → /login?redirect=...
 * - meta.admin 路由要求 role 是 ADMIN 或 BOSS,否则回 /dashboard
 */

import { createRouter, createWebHistory } from 'vue-router'
import { nextTick } from 'vue'
import { useUserStore } from '@/stores/user'
import { announce } from '@/utils/announcer'
import { routes } from './routes'

export const router = createRouter({
  history: createWebHistory(),
  routes,
  /**
   * 换页时回到顶部。
   *
   * 之前没有 scrollBehavior:SPA 换页不会自动复位,
   * 在长列表(图书列表 / 订单列表)底部点进详情页,
   * 新页面会停在半空中,看起来像"没加载出来"。
   */
  scrollBehavior(to, _from, savedPosition) {
    // 浏览器前进 / 后退:恢复原来的位置
    if (savedPosition) return savedPosition
    // 带锚点(/#contact):滚到锚点,并让开顶部导航条的高度
    if (to.hash) return { el: to.hash, top: 72, behavior: 'smooth' }
    return { top: 0 }
  },
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
 * 把焦点移到主内容区(main landmark)。
 *
 * 为什么要专门做这件事:
 * SPA 换页时地址栏变了、页面内容换了,但浏览器的焦点还留在刚点过的那个链接上。
 * 键盘用户按完 Enter 之后,焦点还停在一个已经"不在这一页"的元素上 ——
 * 读屏软件也不会主动念新页面,于是换页变成一次静默的跳转。
 * 把焦点交给 <main> 之后,读屏会从头念新页面的内容,用户才跟得上。
 *
 * retry:布局组件是导航确认之后才挂载的,首屏那一次可能还看不到 <main>,
 * 所以找不到就再等一帧重试几次(最多 5 帧 ≈ 80ms)。
 */
function focusMain(attempt = 0): void {
  const main = document.querySelector<HTMLElement>('main, [role="main"]')
  if (main) {
    // <main> 默认不可聚焦,加上 tabindex="-1" 才能用 JS 移焦点
    // (-1 表示"程序可以聚焦,Tab 不会停在它上面")
    if (!main.hasAttribute('tabindex')) main.setAttribute('tabindex', '-1')
    // preventScroll:滚动位置交给上面的 scrollBehavior 管,这里只管焦点
    main.focus({ preventScroll: true })
    return
  }
  if (attempt < 5) {
    requestAnimationFrame(() => focusMain(attempt + 1))
  }
}

/**
 * 同步 document.title  -  meta.title 决定了页面顶栏文案,
 * 但浏览器标签页标题以前永远是「TMLibrary  -  图书管理系统」(写死在 index.html)。
 * 这里在每次路由切换后覆盖一次,默认 fallback 保持原值。
 *
 * 顺带做两件 SPA 无障碍的事:
 *   1. 把焦点移到主内容区 —— 不然读屏用户按完链接后焦点还留在旧链接上,
 *      他不知道页面已经换了(焦点看不见,读屏也不会自动念新页面)
 *   2. 用 aria-live 播报"已进入 XX 页"
 */
router.afterEach(async (to) => {
  const pageTitle = to.meta.title as string | undefined
  document.title = pageTitle ? `${pageTitle} · TMLibrary` : 'TMLibrary  -  图书管理系统'

  // 等新页面的 DOM 落地,主内容区才存在
  await nextTick()
  focusMain()

  announce(pageTitle ? `已进入${pageTitle}` : '已进入首页')
})

export default router
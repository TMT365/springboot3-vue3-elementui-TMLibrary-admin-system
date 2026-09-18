/**
 * 路由表  -  业务路由都在 AdminLayout 下,/login 是公开页
 *
 * meta 约定:
 * - title: 顶栏标题 / 菜单名
 * - public: 公开页(跳过鉴权守卫)
 * - admin: 仅 ADMIN / BOSS 可访问
 *
 */

import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/views/landing/Landing.vue'),
    meta: { public: true, title: 'TMLibrary  -  图书管理系统' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true, title: '注册' },
  },
  // 忘记 / 重置密码都是公开页 —— 用户就是因为登不进来才走这两页,
  // 标了 public 才能跳过守卫(没标的话会被弹回 /login,自己咬自己)
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { public: true, title: '忘记密码' },
  },
  {
    // 令牌在 query 上:邮件链接 = /reset-password?token=<43字符 Base64URL>
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/views/auth/ResetPassword.vue'),
    meta: { public: true, title: '重置密码' },
  },
  {
    path: '/mall',
    component: () => import('@/layouts/MallLayout.vue'),
    meta: { public: true },
    children: [
      {
        path: '',
        name: 'mall-home',
        component: () => import('@/views/mall/Mall.vue'),
        meta: { title: '书卷商城' },
      },
      {
        path: 'category/:catId/:subId?',
        name: 'mall-category',
        component: () => import('@/views/mall/Mall.vue'),
        meta: { title: '分类浏览' },
      },
    ],
  },
  {
    path: '/admin',
    redirect: '/admin/dashboard',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', admin: true },
      },
      {
        path: 'books',
        name: 'book-list',
        component: () => import('@/views/book/List.vue'),
        meta: { title: '图书列表', admin: true },
      },
      {
        path: 'books/new',
        name: 'book-new',
        component: () => import('@/views/book/Edit.vue'),
        meta: { title: '新增图书', admin: true },
      },
      {
        // 用 isbn 而不是自增 id:后端 PATCH/DELETE/详情全部以 isbn 为自然键,
        // 路由参数跟后端对齐就不用再为"id → isbn"多打一次接口
        path: 'books/:isbn/edit',
        name: 'book-edit',
        component: () => import('@/views/book/Edit.vue'),
        meta: { title: '编辑图书', admin: true },
      },
      {
        path: 'purchases',
        name: 'purchase-list',
        component: () => import('@/views/purchase/List.vue'),
        meta: { title: '订单列表', admin: true },
      },
      {
        path: 'purchases/new',
        name: 'purchase-new',
        component: () => import('@/views/purchase/Create.vue'),
        meta: { title: '下单', admin: true },
      },
      {
        path: 'users',
        name: 'user-manage',
        component: () => import('@/views/user/Manage.vue'),
        meta: { title: '用户管理', admin: true },
      },
      {
        path: 'feedback',
        name: 'admin-feedback',
        component: () => import('@/views/feedback/AdminList.vue'),
        meta: { title: '反馈管理', admin: true },
      },
    ],
  },
  {
    path: '/user',
    redirect: '/user/books',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'books',
        name: 'user-books',
        component: () => import('@/views/user/UserBooks.vue'),
        meta: { title: '图书浏览' },
      },
      {
        path: 'cart',
        name: 'user-cart',
        component: () => import('@/views/user/UserCart.vue'),
        meta: { title: '购物车' },
      },
      {
        path: 'profile',
        name: 'user-profile',
        component: () => import('@/views/user/UserProfile.vue'),
        meta: { title: '个人信息' },
      },
      {
        path: 'my-orders',
        name: 'user-orders',
        component: () => import('@/views/user/UserOrders.vue'),
        meta: { title: '我的订单' },
      },
      {
        path: 'settings',
        name: 'user-settings',
        component: () => import('@/views/user/UserSettings.vue'),
        meta: { title: '设置' },
      },
      // ---------------------------------------------------------------
      // 「我的反馈」—— 挂进个人中心(2026-09 从顶层 /feedback/mine 挪进来)。
      //
      // 为什么挪:看自己的反馈是个人事务,跟购物车 / 我的订单是同一类东西,
      // 该跟它们待在一个菜单里。原来它是顶层路由,在导航上跟"商城""个人中心"
      // 平级,占了一个和它的分量不匹配的位置。
      //
      // 为什么**详情页**仍留在顶层(/feedback/:id)—— 见下面那段注释。
      // ---------------------------------------------------------------
      {
        path: 'feedback',
        name: 'user-feedback',
        component: () => import('@/views/feedback/MyList.vue'),
        meta: { title: '我的反馈' },
      },
    ],
  },
  // ---------------------------------------------------------------
  // 反馈详情 —— 保持独立路由,不挂在 /user 下。
  // 原因:管理员也要打开同一个详情页,挂在 /user 下会让
  // "管理员访问 /user/feedback/1" 看起来像越权,而且布局会跟着 UserLayout 走。
  //
  // 注意这个路由**没有** meta.public —— 守卫里"没标 public 就是要登录",
  // 正好对上"必须登录才能反馈"的需求。
  // ---------------------------------------------------------------
  {
    path: '/feedback/:id',
    name: 'feedback-detail',
    component: () => import('@/views/feedback/Detail.vue'),
    // 具体权限(提交人本人 or ADMIN/BOSS)由后端强制,前端不做路由级判断 ——
    // 前端拦不住直接调接口,前面拦一道只会让人误以为"前端就是权限边界"
    meta: { title: '反馈详情' },
  },
  // 老路径兜底:改版前「我的反馈」在 /feedback/mine,用户的书签 / 浏览器历史
  // 里可能还有。直接 404 太糙,重定向到新位置。
  // 注意必须放在 /feedback/:id **之前**?不用 —— vue-router 静态段优先于动态段,
  // /feedback/mine 会先匹配到这条 redirect,不会被 :id 抢走。
  {
    path: '/feedback/mine',
    redirect: '/user/feedback',
  },
  {
    path: '/journey',
    name: 'journey',
    component: () => import('@/views/journey/Index.vue'),
    // 公开 —— 学习经历是给访客/面试官看的,不该要求登录
    meta: { public: true, title: '学习经历' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]
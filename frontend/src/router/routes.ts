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
        path: 'books/:id(\\d+)/edit',
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
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]
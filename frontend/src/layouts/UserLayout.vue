<script setup lang="ts">
/**
 * UserLayout  -  普通用户 / 管理员都可见的用户区 layout
 *
 * 与 AdminLayout 的区别:
 * - 配色更轻(白底 + accent 绿,而不是深蓝侧栏)
 * - sidebar 只放用户视角的导航:图书浏览 / 个人信息 / 我的订单
 * - ADMIN / BOSS 顶栏额外加一个「进入后台」快捷入口
 *
 * 路由:父路径 /user,所有子路由都套在这个 layout 下
 */

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapsed = ref(false)
const isMobile = ref(false)
const isMobileMenuOpen = ref(false)

const pageTitle = computed<string>(() => (route.meta.title as string) || '我的空间')

const roleLabel = computed<string>(() => {
  switch (userStore.role) {
    case 'BOSS':
      return '老板'
    case 'ADMIN':
      return '管理员'
    case 'USER':
      return '普通用户'
    default:
      return '游客'
  }
})

const userNavItems = [
  { index: '/user/cart', title: '购物车', icon: 'ShoppingCart' },
  { index: '/user/profile', title: '个人信息', icon: 'UserFilled' },
  { index: '/user/my-orders', title: '我的订单', icon: 'List' },
  { index: '/user/settings', title: '设置', icon: 'Setting' },
]

function syncViewport(): void {
  isMobile.value = window.matchMedia('(max-width: 900px)').matches
  if (!isMobile.value) {
    isMobileMenuOpen.value = false
  }
}

function toggleSidebar(): void {
  if (isMobile.value) {
    isMobileMenuOpen.value = !isMobileMenuOpen.value
    return
  }
  isCollapsed.value = !isCollapsed.value
}

function closeMobileMenu(): void {
  if (isMobile.value) isMobileMenuOpen.value = false
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认注销登录?', '提示', {
      type: 'warning',
      confirmButtonText: '注销',
      cancelButtonText: '取消',
    })
    userStore.logout()
    ElMessage.success('已注销')
    await router.push('/')
  } catch {
    /* 用户点取消 */
  }
}

async function goAdmin(): Promise<void> {
  await router.push('/admin/dashboard')
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  document.body.style.overflow = ''
})

watch(isMobileMenuOpen, (open) => {
  if (!isMobile.value) {
    document.body.style.overflow = ''
    return
  }
  document.body.style.overflow = open ? 'hidden' : ''
})
</script>

<template>
  <el-container
    class="user-layout"
    :class="{
      'is-collapsed': isCollapsed,
      'is-mobile': isMobile,
      'is-mobile-menu-open': isMobileMenuOpen,
    }"
  >
    <div
      v-if="isMobile"
      class="sidebar-mask"
      :class="{ 'is-open': isMobileMenuOpen }"
      @click="closeMobileMenu"
    />

    <el-aside class="sidebar">
      <router-link to="/" class="logo-link" @click="closeMobileMenu">
        <span class="logo-mark">T</span>
        <span v-show="!isCollapsed || isMobile" class="logo-text">TMLibrary</span>
      </router-link>

      <nav class="nav">
        <router-link
          v-for="item in userNavItems"
          :key="item.index"
          :to="item.index"
          replace
          class="nav-link"
          active-class="is-active"
          @click="closeMobileMenu"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span v-show="!isCollapsed || isMobile">{{ item.title }}</span>
        </router-link>
      </nav>

      <div v-show="!isCollapsed || isMobile" class="sidebar-footer">
        <span class="footer-hint">图书管理系统</span>
        <span class="footer-hint">v 0.0.1</span>
      </div>

    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar-left">
          <el-button
            circle
            text
            class="menu-btn"
            :aria-label="isMobile ? '打开菜单' : (isCollapsed ? '展开侧栏' : '折叠侧栏')"
            @click="toggleSidebar"
          >
            <span class="menu-lines" aria-hidden="true" />
          </el-button>
          <div class="page-title">{{ pageTitle }}</div>
        </div>
        <div class="topbar-actions">
          <el-button
            v-if="userStore.isAdmin"
            type="primary"
            size="small"
            @click="goAdmin"
          >
            进入管理后台
          </el-button>
          <el-dropdown trigger="click" @command="handleLogout">
            <span class="user-trigger">
              <el-icon><UserFilled /></el-icon>
              <span>{{ userStore.username || '游客' }}</span>
              <el-tag size="small" type="success" effect="light">
                {{ roleLabel }}
              </el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  注销
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view @click="closeMobileMenu" />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
  background:
    radial-gradient(circle at 8% 8%, rgba(76, 175, 80, 0.18), transparent 42%),
    radial-gradient(circle at 92% 14%, rgba(97, 223, 137, 0.13), transparent 36%),
    var(--color-bg);
  --sidebar-width: 240px;
  --sidebar-collapsed-width: 90px;
}

.sidebar {
  position: fixed;
  top: 16px;
  left: 16px;
  bottom: 16px;
  width: var(--sidebar-width);
  z-index: 30;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.42);
  backdrop-filter: blur(16px) saturate(135%);
  -webkit-backdrop-filter: blur(16px) saturate(135%);
  border-radius: 22px;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 22px 48px rgba(17, 25, 40, 0.14),
    0 1px 0 rgba(255, 255, 255, 0.75) inset;
  transition:
    width 220ms cubic-bezier(0.2, 0, 0, 1),
    transform 220ms cubic-bezier(0.2, 0, 0, 1);
  overflow: hidden;
}

.logo-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 24px;
  text-decoration: none;
  color: var(--color-text);
  border-radius: 0;
  transition: opacity 150ms ease;
}
.logo-link:hover {
  opacity: 0.78;
}
.logo-link:focus-visible {
  outline: none;
  box-shadow: inset 2px 0 0 var(--color-accent);
}

.logo-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 18px;
  border-radius: 8px;
}

.logo-text {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 12px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  color: var(--color-text-muted);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: background-color 150ms ease, color 150ms ease, box-shadow 150ms ease;
}

.nav-link:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.nav-link:focus-visible {
  outline: none;
  box-shadow: inset 3px 0 0 var(--color-accent);
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.nav-link.is-active {
  background: rgba(76, 175, 80, 0.14);
  color: var(--color-accent);
  font-weight: 600;
}

.sidebar-footer {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px solid var(--color-border);
}

.footer-hint {
  font-size: 11px;
  color: var(--color-text-soft);
  letter-spacing: 0.04em;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72px;
  margin: 16px 16px 0 calc(var(--sidebar-width) + 32px);
  padding: 0 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(12px) saturate(130%);
  -webkit-backdrop-filter: blur(12px) saturate(130%);
  box-shadow: 0 10px 28px rgba(17, 25, 40, 0.09);
  transition: margin-left 220ms cubic-bezier(0.2, 0, 0, 1);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-btn {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: var(--color-text);
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(17, 25, 40, 0.12);
  box-shadow:
    0 6px 16px rgba(17, 25, 40, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.menu-lines {
  position: relative;
  width: 16px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
  display: inline-block;
}

.menu-lines::before,
.menu-lines::after {
  content: '';
  position: absolute;
  left: 0;
  width: 16px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
}

.menu-lines::before {
  top: -5px;
}

.menu-lines::after {
  top: 5px;
}

.menu-btn:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateY(-1px);
}

.menu-btn:active {
  transform: translateY(0) scale(0.96);
}

.menu-btn:focus-visible {
  outline: none;
  box-shadow:
    0 0 0 2px var(--color-accent),
    0 6px 16px rgba(17, 25, 40, 0.16);
}

.page-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.015em;
  text-wrap: balance;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  color: var(--color-text-muted);
  font-size: 14px;
  box-shadow:
    inset 0 0 0 1px transparent,
    0 1px 2px rgba(0, 0, 0, 0.04);
  transition:
    background-color 200ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 200ms cubic-bezier(0.2, 0, 0, 1),
    color 200ms cubic-bezier(0.2, 0, 0, 1),
    transform 200ms cubic-bezier(0.2, 0, 0, 1);
}

.user-trigger:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
  box-shadow:
    inset 0 0 0 1px var(--color-border),
    0 2px 6px rgba(0, 0, 0, 0.06);
}

.user-trigger:active {
  transform: scale(0.96);
}

.user-trigger:focus-visible {
  outline: none;
  box-shadow:
    inset 0 0 0 1px var(--color-border),
    0 0 0 2px var(--color-accent);
}

:deep(.el-dropdown-menu) {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.08),
    0 16px 40px rgba(0, 0, 0, 0.06);
  padding: 4px;
}

:deep(.el-dropdown-menu__item) {
  border-radius: 6px;
  margin: 0;
  transition: background-color 150ms ease;
}

:deep(.el-dropdown-menu__item:not(.is-disabled):hover) {
  background: var(--color-bg-alt);
  color: var(--color-accent);
}

.main-content {
  margin-left: calc(var(--sidebar-width) + 32px);
  padding: 24px 16px 16px;
  transition: margin-left 220ms cubic-bezier(0.2, 0, 0, 1);
}

.is-collapsed .sidebar {
  width: var(--sidebar-collapsed-width);
}

.is-collapsed .topbar {
  margin-left: calc(var(--sidebar-collapsed-width) + 32px);
}

.is-collapsed .main-content {
  margin-left: calc(var(--sidebar-collapsed-width) + 32px);
}

.is-collapsed .nav-link {
  justify-content: center;
}

.sidebar-mask {
  display: none;
}

@media (max-width: 900px) {
  .sidebar {
    top: 12px;
    left: 12px;
    bottom: 12px;
    width: min(80vw, 300px);
    transform: translateX(calc(-100% - 20px));
  }

  .sidebar-mask {
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(12, 18, 30, 0.36);
    backdrop-filter: blur(2px);
    opacity: 0;
    pointer-events: none;
    transition: opacity 180ms ease;
  }

  .sidebar-mask.is-open {
    opacity: 1;
    pointer-events: auto;
  }

  .is-mobile-menu-open .sidebar {
    transform: translateX(0);
  }

  .topbar {
    margin: 12px 12px 0;
    height: 64px;
  }

  .page-title {
    font-size: 16px;
  }

  .topbar-actions {
    gap: 10px;
  }

  .user-trigger {
    padding: 6px 10px;
  }

  .main-content {
    margin-left: 0;
    padding: 16px 12px 12px;
  }

  .is-collapsed .main-content,
  .is-collapsed .topbar {
    margin-left: 12px;
  }
}

:root[data-theme='dark'] .sidebar,
:root[data-theme='dark'] .topbar {
  background: rgba(30, 30, 30, 0.56);
  border-color: rgba(255, 255, 255, 0.08);
}

:root[data-theme='dark'] .menu-btn {
  background: rgba(46, 46, 46, 0.92);
  border-color: rgba(255, 255, 255, 0.14);
  color: #f5f5f5;
  box-shadow:
    0 6px 16px rgba(0, 0, 0, 0.36),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
</style>
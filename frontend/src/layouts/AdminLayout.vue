<script setup lang="ts">
/**
 * AdminLayout  -  后台主布局(Chrome)
 *
 * v2 升级(现代化 + 高级感):
 * - 侧栏折叠:220px ↔ 64px,localStorage 持久化('tm_admin_sidebar')
 * - 顶栏玻璃化:backdrop-filter: blur(14px) saturate(180%) + 半透 bg
 * - 面包屑替代单 page-title:从 route.matched 构建,可点击跳转
 * - 顶栏右侧:折叠按钮 + 主题 toggle + 用户下拉
 *
 * 布局结构:
 *   ┌────────────────────────────────────────────────┐
 *   │ <el-aside :width>          <el-container>      │
 *   │   .logo                      <el-header>       │
 *   │   <SidebarMenu>              breadcrumb |       │
 *   │                              toggle+theme+user │
 *   │                              ──────────────    │
 *   │                              <el-main>          │
 *   │                              <router-view>     │
 *   └────────────────────────────────────────────────┘
 */

import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter, type RouteLocationMatched } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import SidebarMenu from '@/components/SidebarMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { theme, toggleTheme } = useTheme()

/* ============================================================
 * 侧栏折叠状态  -  localStorage 持久化(跨会话保留用户偏好)
 * ============================================================ */
const SIDEBAR_KEY = 'tm_admin_sidebar'
const sidebarCollapsed = ref<boolean>(false)

onMounted(() => {
  const stored = localStorage.getItem(SIDEBAR_KEY)
  if (stored === '1') sidebarCollapsed.value = true
})

watch(sidebarCollapsed, (v) => {
  localStorage.setItem(SIDEBAR_KEY, v ? '1' : '0')
})

function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

/* ============================================================
 * 面包屑  -  从 route.matched 构建
 *   - 过滤掉没 title 的路由(layout 父路由)
 *   - 在最前面补一个「后台」首页入口(始终可点 → /admin/dashboard)
 *   - 末项不可点击(就是当前页)
 * ============================================================ */
interface Crumb {
  title: string
  path: string
  isCurrent: boolean
}

const breadcrumbs = computed<Crumb[]>(() => {
  const crumbs: Crumb[] = [
    { title: '后台', path: '/admin/dashboard', isCurrent: false },
  ]
  // 路由匹配的层级,跳过 layout 父路由(没有 title 或 title 是 'TMLibrary')
  const matched: RouteLocationMatched[] = route.matched.filter(
    (r) => r.meta?.title && r.meta.title !== 'TMLibrary',
  )
  matched.forEach((r, idx) => {
    crumbs.push({
      title: r.meta.title as string,
      // 末项不需要跳转,其他用其 path(动态参数会被原样保留,如 :id  -  跳转会保留当前 ID)
      path: r.path || route.path,
      isCurrent: idx === matched.length - 1,
    })
  })
  return crumbs
})

/* ============================================================
 * 用户角色
 * ============================================================ */
const roleLabel = computed<string>(() => {
  switch (userStore.role) {
    case 'BOSS': return '老板'
    case 'ADMIN': return '管理员'
    case 'USER': return '普通用户'
    default: return '未知'
  }
})

const roleTagType = computed<'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined>(() => {
  switch (userStore.role) {
    case 'BOSS': return 'danger'
    case 'ADMIN': return 'warning'
    case 'USER': return 'info'
    default: return undefined
  }
})

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认注销登录?', '提示', {
      type: 'warning',
      confirmButtonText: '注销',
      cancelButtonText: '取消',
    })
    userStore.logout()
    ElMessage.success('已注销')
    await router.push('/login')
  } catch {
    
  }
}

async function handleCommand(command: string): Promise<void> {
  if (command === 'logout') {
    await handleLogout()
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 侧栏:动态宽度 + 折叠 transition -->
    <el-aside
      :width="sidebarCollapsed ? '64px' : '220px'"
      class="sidebar"
      :class="{ 'is-collapsed': sidebarCollapsed }"
    >
      <router-link to="/admin/dashboard" replace class="logo" aria-label="后台首页">
        <span class="logo-mark">T</span>
        <span v-show="!sidebarCollapsed" class="logo-text">TMLibrary</span>
      </router-link>
      <SidebarMenu :collapsed="sidebarCollapsed" />
    </el-aside>

    <el-container>
      <!-- 顶栏:玻璃化 + 面包屑 + 操作区 -->
      <el-header class="topbar">
        <!-- 面包屑 -->
        <nav class="breadcrumb" aria-label="面包屑导航">
          <ol class="crumb-list">
            <li
              v-for="(crumb, i) in breadcrumbs"
              :key="i"
              class="crumb"
              :class="{ 'is-current': crumb.isCurrent }"
            >
              <router-link
                v-if="!crumb.isCurrent"
                :to="crumb.path"
                replace
                class="crumb-link"
              >
                {{ crumb.title }}
              </router-link>
              <span v-else class="crumb-current" aria-current="page">
                {{ crumb.title }}
              </span>
              <span
                v-if="i < breadcrumbs.length - 1"
                class="crumb-sep"
                aria-hidden="true"
              >/</span>
            </li>
          </ol>
        </nav>

        <!-- 操作区:折叠 / 主题 / 用户 -->
        <div class="topbar-actions">
          <button
            class="icon-btn"
            :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
            :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleSidebar"
          >
            <el-icon>
              <Fold v-if="!sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </button>

          <button
            class="icon-btn"
            :title="theme === 'light' ? '切换到夜间模式' : '切换到日间模式'"
            :aria-label="theme === 'light' ? '切换到夜间模式' : '切换到日间模式'"
            @click="toggleTheme"
          >
            <el-icon>
              <Moon v-if="theme === 'light'" />
              <Sunny v-else />
            </el-icon>
          </button>

          <el-dropdown @command="handleCommand" trigger="click">
            <span class="user-trigger">
              <span class="avatar-mini" aria-hidden="true">
                {{ (userStore.username || '?').slice(0, 1).toUpperCase() }}
              </span>
              <span class="user-name">{{ userStore.username || '游客' }}</span>
              <el-tag v-if="userStore.role" :type="roleTagType" size="small" effect="light">
                {{ roleLabel }}
              </el-tag>
              <el-icon class="caret"><ArrowDown /></el-icon>
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
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
}

/* ============================================================
 * 侧栏
 * ============================================================ */
.sidebar {
  background: var(--color-bg);
  color: var(--color-text);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  
  transition: width 280ms cubic-bezier(0.4, 0, 0.2, 1);
  overflow-x: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
  
  transition: padding 280ms cubic-bezier(0.4, 0, 0.2, 1);
}

.is-collapsed .logo {
  padding: 0;
  justify-content: center;
}

.logo:focus-visible {
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
  flex-shrink: 0;}

.logo-text {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;  white-space: nowrap;
  overflow: hidden;
}

/* ============================================================
 * 顶栏  -  玻璃化(backdrop-filter blur + saturate)
 * ============================================================ */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: rgba(250, 248, 243, 0.72);
  -webkit-backdrop-filter: blur(14px) saturate(180%);
  backdrop-filter: blur(14px) saturate(180%);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 10;
  transition: background 200ms ease, border-color 200ms ease;
}

:root[data-theme='dark'] .topbar {
  background: rgba(26, 26, 26, 0.72);
}


.breadcrumb {
  display: flex;
  align-items: center;
  min-width: 0;
}

.crumb-list {
  display: flex;
  align-items: center;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 14px;
  flex-wrap: nowrap;
  overflow: hidden;
}

.crumb {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.crumb-link {
  color: var(--color-text-muted);
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 150ms ease, color 150ms ease;
  font-weight: 500;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.crumb-link:hover {
  background: var(--color-bg-alt);
  color: var(--color-accent);
}

.crumb-link:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent);
}

.crumb-current {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--color-text);
  padding: 4px 8px;}

.crumb-sep {
  color: var(--color-text-soft);
  margin: 0 2px;
  font-weight: 400;
  user-select: none;
}


.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}


.icon-btn {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-muted);
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  transition: color 150ms ease, border-color 150ms ease, background-color 150ms ease;
}

.icon-btn:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
  background: var(--color-bg-alt);
}

.icon-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent);
}

.icon-btn:active {
  transform: scale(0.96);
}


.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
  /* 多层阴影(替代 1px border,更自然) */
  box-shadow:
    inset 0 0 0 1px transparent,
    0 1px 2px rgba(0, 0, 0, 0.04);
  /* transition 只列属性:背景 + 阴影 + scale + caret 都用统一曲线 */
  transition:
    background-color 200ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 200ms cubic-bezier(0.2, 0, 0, 1),
    color 200ms cubic-bezier(0.2, 0, 0, 1);
}

.user-trigger:hover {
  background: var(--color-bg-alt);
  color: var(--color-accent);
  box-shadow:
    inset 0 0 0 1px var(--color-border),
    0 2px 6px rgba(0, 0, 0, 0.06);
}

/* 按压反馈 - scale(0.96) 给「点到了」感觉 */
.user-trigger:active {
  transform: scale(0.96);
}

/* focus-visible:键盘焦点环,无障碍 */
.user-trigger:focus-visible {
  outline: none;
  box-shadow:
    inset 0 0 0 1px var(--color-border),
    0 0 0 2px var(--color-accent);
}

.avatar-mini {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 14px;
  font-weight: 400;
  border-radius: 50%;
  flex-shrink: 0;
  /* 头像 hover 时跟着按钮一起微微缩放 */
  transition: transform 200ms cubic-bezier(0.2, 0, 0, 1);
}

.user-trigger:hover .avatar-mini {
  transform: scale(1.08);
}

.user-name {
  font-weight: 500;
  /* 等宽数字防用户名长度变化抖动 */
  font-variant-numeric: tabular-nums;
}

/* caret 箭头 - 默认朝下,dropdown 打开时旋转 180° */
.caret {
  font-size: 12px;
  color: var(--color-text-soft);
  transition: transform 220ms cubic-bezier(0.2, 0, 0, 1);
}

:deep(.el-dropdown.is-active) .caret,
:deep(.el-dropdown:focus-within) .caret {
  transform: rotate(180deg);
}

/* dropdown 弹出层:多阴影 + 圆角 */
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

/* ============================================================
 * 主内容区
 * ============================================================ */
.main-content {
  background: var(--color-bg-alt);
  padding: 28px 28px 48px;
}

/* ============================================================
 * el-menu overrides  -  适配折叠模式
 * (折叠时 el-menu 自带只显示 icon 的逻辑,这里只微调样式)
 * ============================================================ */
.sidebar :deep(.el-menu) {
  border-right: none;
  background: transparent;
}

.sidebar :deep(.el-menu-item) {
  color: var(--color-text-muted);
  transition: background-color 180ms ease, color 180ms ease;
}

.sidebar :deep(.el-menu-item:hover) {
  background-color: var(--color-bg-alt);
  color: var(--color-text);
}

.sidebar :deep(.el-menu-item:focus-visible) {
  outline: none;
  box-shadow: inset 3px 0 0 var(--color-accent);
  background-color: var(--color-bg-alt);
  color: var(--color-text);
}

.sidebar :deep(.el-menu-item.is-active) {
  background-color: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-weight: 600;
}


.is-collapsed :deep(.el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

/* ============================================================
 * 响应式  -  移动端:侧栏自动收起,面包屑可滚动
 * ============================================================ */
@media (max-width: 768px) {
  .topbar {
    padding: 0 16px;
  }

  .crumb-list {
    overflow-x: auto;
    scrollbar-width: none;
  }

  .crumb-list::-webkit-scrollbar {
    display: none;
  }

  .main-content {
    padding: 16px 16px 32px;
  }

  
  .sidebar {
    width: 64px !important;
  }

  .logo {
    padding: 0;
    justify-content: center;
  }

  .logo-text {
    display: none;
  }

  .topbar-actions .user-name,
  .topbar-actions .el-tag {
    display: none;
  }
}
</style>
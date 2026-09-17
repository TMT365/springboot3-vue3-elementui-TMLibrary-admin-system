<script setup lang="ts">
/**
 * MallLayout  -  商城公共 layout(v2 玻璃态悬浮侧栏)
 *
 * 结构:
 *   TopBar   [☰ toggle] [T logo + Mall] [🔍 搜索]        [我的▼ / 登录]
 *   ┌──────┬────────────────────────────────────────────┐
 *   │ Side │                                            │
 *   │ bar  │     <router-view />                        │
 *   │      │                                            │
 *   └──────┴────────────────────────────────────────────┘
 *   [移动端 sidebar 改为悬浮浮层,点击 backdrop 关闭]
 *
 * Sidebar 多级菜单  -  用 el-menu + el-sub-menu 实现
 * 折叠态 64px,展开态 240px(桌面);移动端固定浮层
 *
 * 公开页:任何人都能访问(未登录也能逛商城);登录后右上角「我的」展开 profile 菜单
 */

import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { bookApi } from '@/api/book'
import { highlightParts, useBookSuggest } from '@/composables/useBookSuggest'
import type { BookCategoryNode, BookSuggestion } from '@/types/api'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { theme, toggleTheme } = useTheme()

/**
 * sidebar 折叠状态
 * 桌面默认展开,mobile 默认折叠(浮层)
 */
const sidebarCollapsed = ref(false)

/**
 * 是否移动端(<= 900px)
 * 用于切换 sidebar 行为:桌面 = 64px 折叠,移动端 = 完全浮层
 */
const isMobile = ref(false)
const MOBILE_BREAKPOINT = 900

function updateIsMobile(): void {
  if (typeof window === 'undefined') return
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
  // 切到移动端时强制收起(因为移动端模式是浮层)
  if (isMobile.value) sidebarCollapsed.value = true
}

function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function closeSidebar(): void {
  sidebarCollapsed.value = true
}

/**
 * 当前激活的菜单项(用于 el-menu :default-active)
 * 优先级:二级菜单 > 一级父菜单(没二级时)
 * 路由形如 /mall/category/{catId}/{subId?}
 */
const activeMenuKey = computed<string | null>(() => {
  const m = /^\/mall\/category\/([^/]+)(?:\/([^/]+))?/.exec(route.path)
  if (!m) return null
  const catId = m[1]
  const subId = m[2]
  return subId ? `${catId}-${subId}` : catId
})

/**
 * 路由切换时关闭移动端浮层(用户点 nav item 后自动收起)
 */
watch(
  () => route.path,
  () => {
    if (isMobile.value) closeSidebar()
  },
)

/**
 * 多级分类  -  来自 GET /api/books/categories(免登录可读)
 *
 * 以前这里写死了一份 8 大类 29 小类的数组,和数据库完全脱节:
 * 后台新建的分类商城看不见,商城点分类后端也筛不了书。
 * 现在整棵树由后端给,后台建完分类刷新即见。
 */
const categoryTree = ref<BookCategoryNode[]>([])
const categoryLoading = ref<boolean>(true)
const categoryError = ref<boolean>(false)

async function loadCategories(): Promise<void> {
  categoryLoading.value = true
  categoryError.value = false
  try {
    categoryTree.value = await bookApi.listCategories()
  } catch {
    categoryError.value = true
  } finally {
    categoryLoading.value = false
  }
}

/** 大类没配图标(后台新建的)时给个兜底,免得出个空洞 */
function iconOf(cat: BookCategoryNode): string {
  return cat.icon || 'Collection'
}

function selectCategory(catId: number, subId?: number): void {
  // 同 MallLayout 内切换,replace 不污染历史栈
  router.replace(`/mall/category/${catId}${subId ? `/${subId}` : ''}`)
  // 移动端选完分类自动收起浮层
  if (isMobile.value) closeSidebar()
}

/** 「全部图书」菜单项的 index —— activeMenuKey 为 null(不在分类路由上)时它高亮 */
const ALL_BOOKS_KEY = 'all'

/** 取消分类筛选,回到商城首页的全量列表 */
function goAllBooks(): void {
  router.replace('/mall')
  if (isMobile.value) closeSidebar()
}

/**
 * 监听窗口宽度变化(响应式断点)
 */
let resizeHandler: (() => void) | null = null
onMounted(() => {
  void loadCategories()
  if (typeof window === 'undefined') return
  updateIsMobile()
  resizeHandler = updateIsMobile
  window.addEventListener('resize', resizeHandler)
})
onUnmounted(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
})

async function handleCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push('/user/profile')
  } else if (command === 'orders') {
    await router.push('/user/my-orders')
  } else if (command === 'admin') {
    await router.push('/admin/dashboard')
  } else if (command === 'login') {
    await router.push('/login')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认注销登录?', '提示', {
        type: 'warning',
        confirmButtonText: '注销',
        cancelButtonText: '取消',
      })
      userStore.logout()
      ElMessage.success('已注销')
      // 注销后仍在 MallLayout(只是从登录态切回公开态),replace 不留 logout 痕迹
      await router.replace('/mall')
    } catch {
      
    }
  }
}

/** 顶栏搜索框的内容 */
const searchText = ref<string>('')

const { fetchSuggestions } = useBookSuggest()

/**
 * 搜索 —— 结果状态放在 URL 上(`/mall?q=xxx`)。
 *
 * 好处:顶栏这个框和手机端那个框共用同一个数据源,不用互相传事件;
 * 刷新、分享、前进后退都能保持结果。
 *
 * 注意会把分类清掉:点搜索的语义是"在全馆里找",而不是"在当前分类里找"。
 */
function onSearch(): void {
  const q = searchText.value.trim()
  router.replace({ path: '/mall', query: q ? { q } : {} })
}

/**
 * 选中候选词的时间戳。
 *
 * el-autocomplete 里"↑↓ 选中后回车"会走 @select(keydown 触发),
 * 而 @keyup.enter 也会跟着冒泡上来 —— 两个都跑的话,后者会用输入框里的
 * 原始文字覆盖掉用户刚选的那条。用一个很短的时间窗把这种情况挡掉。
 */
let lastSelectAt = 0

// 参数类型跟着 EP 走(el-autocomplete 的 @select 声明是 Record<string, any>),
// 声明成 BookSuggestion 会因为"函数参数逆变"报错
function onSelectSuggestion(item: Record<string, unknown>): void {
  lastSelectAt = Date.now()
  searchText.value = String(item.text ?? '')
  onSearch()
}

function onSearchEnter(): void {
  if (Date.now() - lastSelectAt < 300) return
  onSearch()
}
</script>

<template>
  <el-container
    class="mall-layout"
    :class="{
      'is-collapsed': sidebarCollapsed,
      'is-mobile': isMobile,
      'menu-open': !sidebarCollapsed && isMobile,
    }"
  >
    <!-- 移动端浮层遮罩 - 点击关闭 sidebar -->
    <div
      v-if="isMobile && !sidebarCollapsed"
      class="sidebar-backdrop"
      @click="closeSidebar"
    />

    <!-- TopBar -->
    <el-header class="topbar">
      <div class="topbar-left">
        <button
          class="sidebar-toggle"
          :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          :aria-expanded="!sidebarCollapsed"
          @click="toggleSidebar"
        >
          <!-- 桌面端 = Fold/Expand 图标,移动端 = 汉堡 / X 切换 -->
          <el-icon v-if="isMobile">
            <Close v-if="!sidebarCollapsed" /><Menu v-else />
          </el-icon>
          <el-icon v-else>
            <Fold v-if="!sidebarCollapsed" /><Expand v-else />
          </el-icon>
        </button>
        <router-link to="/mall" replace class="brand">
          <span class="brand-mark">T</span>
          <span class="brand-text">
            <span class="brand-name">TMLibrary</span>
            <span class="brand-sub">MALL · 书卷商城</span>
          </span>
        </router-link>
      </div>

      <div class="search">
        <!-- el-autocomplete 而不是 el-input:自带下拉 + ↑↓/Esc 键盘导航 +
             :debounce 节流(不用自己写 setTimeout) -->
        <el-autocomplete
          v-model="searchText"
          :fetch-suggestions="fetchSuggestions"
          :debounce="280"
          :trigger-on-focus="false"
          value-key="text"
          placeholder="搜索 书名 / 作者 / ISBN"
          size="large"
          clearable
          popper-class="book-suggest-popper"
          class="search-autocomplete"
          @select="onSelectSuggestion"
          @keyup.enter="onSearchEnter"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #default="{ item }">
            <div class="sug">
              <el-icon class="sug-icon">
                <User v-if="item.type === 'AUTHOR'" />
                <Postcard v-else-if="item.type === 'ISBN'" />
                <Reading v-else />
              </el-icon>
              <span class="sug-text">
                <template
                  v-for="(part, i) in highlightParts(item.text, searchText)"
                  :key="i"
                >
                  <em v-if="part.hit" class="sug-hit">{{ part.text }}</em>
                  <template v-else>{{ part.text }}</template>
                </template>
              </span>
              <span v-if="item.hot > 0" class="sug-hot">销量 {{ item.hot }}</span>
            </div>
          </template>
        </el-autocomplete>
      </div>

      <div class="topbar-right">
        <button
          class="sidebar-toggle"
          :title="theme === 'light' ? '切换到夜间模式' : '切换到日间模式'"
          :aria-label="theme === 'light' ? '切换到夜间模式' : '切换到日间模式'"
          @click="toggleTheme"
        >
          <el-icon>
            <Moon v-if="theme === 'light'" />
            <Sunny v-else />
          </el-icon>
        </button>
        <template v-if="userStore.isAuthenticated">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-trigger">
              <span class="avatar-mini">
                {{ userStore.username.slice(0, 1).toUpperCase() }}
              </span>
              <span class="user-name">{{ userStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><UserFilled /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="orders">
                  <el-icon><List /></el-icon>
                  我的订单
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin" divided>
                  <el-icon><Setting /></el-icon>
                  进入管理后台
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  注销
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" round size="default" @click="handleCommand('login')">
            登录
          </el-button>
        </template>
      </div>
    </el-header>

    <!-- Sidebar -->
    <el-aside
      class="sidebar"
      :width="isMobile ? '240px' : (sidebarCollapsed ? '64px' : '240px')"
    >
      <div class="sidebar-inner">
        <div class="sidebar-head">
          <span v-if="!sidebarCollapsed" class="sidebar-title">图书分类</span>
          <span v-else class="sidebar-title-mini">类</span>
        </div>

        <el-menu
          class="category-menu"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          :default-active="activeMenuKey ?? ALL_BOOKS_KEY"
          background-color="transparent"
          text-color="var(--color-text-muted)"
          active-text-color="var(--color-accent)"
          unique-opened
        >
          <!-- 「全部图书」——没有它,从侧栏点进任何分类后就回不到全量列表了 -->
          <el-menu-item :index="ALL_BOOKS_KEY" @click="goAllBooks">
            <el-icon><Grid /></el-icon>
            <span class="cat-label">全部图书</span>
          </el-menu-item>

          <!-- 分类加载中:占位骨架,避免侧栏空一下又蹦出来 -->
          <div v-if="categoryLoading" class="cat-placeholder">
            <el-skeleton :rows="5" animated />
          </div>

          <div v-else-if="categoryError" class="cat-placeholder is-error">
            <el-icon><WarningFilled /></el-icon>
            <span>分类加载失败</span>
            <el-button link type="primary" size="small" @click="loadCategories">重试</el-button>
          </div>

          <template v-else>
            <el-sub-menu
              v-for="cat in categoryTree"
              :key="cat.id"
              :index="String(cat.id)"
            >
              <template #title>
                <el-icon><component :is="iconOf(cat)" /></el-icon>
                <span class="cat-label">{{ cat.name }}</span>
                <span v-if="!sidebarCollapsed" class="cat-count">{{ cat.bookCount }}</span>
              </template>
              <el-menu-item
                v-for="sub in (cat.children ?? [])"
                :key="sub.id"
                :index="`${cat.id}-${sub.id}`"
                @click="selectCategory(cat.id, sub.id)"
              >
                <span class="cat-label">{{ sub.name }}</span>
                <span class="cat-count">{{ sub.bookCount }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>

        <div v-if="!sidebarCollapsed" class="sidebar-promo">
          <p class="promo-tag">本周精选</p>
          <p class="promo-title">《深入理解计算机系统》</p>
          <p class="promo-price">¥128</p>
        </div>
      </div>
    </el-aside>

    <!-- Main content -->
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.mall-layout {
  min-height: 100vh;
  background: var(--color-bg);
}


.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 64px;
  padding: 0 24px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-border);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sidebar-toggle {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color 150ms ease, border-color 150ms ease;
}

.sidebar-toggle:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 20px;
  border-radius: 8px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-name {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);}

.brand-sub {
  font-size: 10px;
  color: var(--color-text-soft);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  margin-top: 2px;
}

.search {
  flex: 1;
  max-width: 520px;
}

.search-autocomplete {
  width: 100%;
}

.search :deep(.el-input__wrapper) {
  background: var(--color-card);
  border-radius: 999px;
  padding: 4px 16px;
  box-shadow: 0 0 0 1px var(--color-border);
}

.search :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--color-accent);
}

.search :deep(.el-input__inner) {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 14px;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  cursor: pointer;
  color: var(--color-text);
  transition: background 150ms ease;
}

.user-trigger:hover {
  background: var(--color-bg-alt);
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
  border-radius: 50%;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
}



/* =============================================================
 * Sidebar  -  玻璃态悬浮浮层
 * 多层 box-shadow + backdrop-filter blur + saturate,
 * 让 sidebar 看起来像漂在 main 上方(macOS dock 感)
 * 移动端默认 translateX(-100%) 隐藏在屏外,点 backdrop 关闭
 * ============================================================= */
.sidebar {
  position: fixed;
  left: 0;
  top: 64px;
  bottom: 0;
  width: 240px;
  z-index: 9;
  /* 亮色:暖米白玻璃 = --color-bg(#faf8f3)+ 透明度 */
  background: rgba(250, 248, 243, 0.78);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  backdrop-filter: blur(20px) saturate(180%);
  /* 白描边在浅底上不可见 → 淡墨描边定义边界(暗色由下方 dark 块覆盖) */
  border-right: 1px solid rgba(17, 25, 40, 0.07);
  /* 多层阴影 - 桌面端浮起感 */
  box-shadow:
    4px 0 12px rgba(17, 25, 40, 0.05),
    16px 0 40px rgba(17, 25, 40, 0.08),
    32px 0 80px rgba(17, 25, 40, 0.05);
  overflow: hidden;
  transition: transform 320ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 320ms cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;
}

/* 暗色:侧栏底色必须跟着换 —— 之前没有 dark 覆盖,奶白面板上压着
   --color-text(#f5f5f5 近白)的标题,直接看不见;边框也用亮棱边 */
:root[data-theme='dark'] .sidebar {
  background: rgba(26, 26, 26, 0.82);
  border-right-color: rgba(255, 255, 255, 0.08);
  box-shadow:
    4px 0 12px rgba(0, 0, 0, 0.32),
    16px 0 40px rgba(0, 0, 0, 0.26),
    32px 0 80px rgba(0, 0, 0, 0.18);
}

.is-collapsed .sidebar {
  transform: translateX(-100%);
  /* 折叠时阴影跟滑出 - 不留鬼影 */
  box-shadow: none;
}

/* =============================================================
 * Backdrop - 移动端 sidebar 展开时遮罩 + 点击关闭
 * ============================================================= */
.sidebar-backdrop {
  position: fixed;
  inset: 64px 0 0 0;
  z-index: 8;
  background: rgba(0, 0, 0, 0.32);
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
  animation: backdropFadeIn 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes backdropFadeIn {
  from { opacity: 0; backdrop-filter: blur(0); -webkit-backdrop-filter: blur(0); }
  to   { opacity: 1; }
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.sidebar-head {
  padding: 20px 20px 12px;
  border-bottom: 1px solid var(--color-border);
}

.sidebar-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--color-text);}

.sidebar-title-mini {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-accent);
  display: block;
  text-align: center;
}

.category-menu {
  flex: 1;
  border-right: none;
  padding: 8px 0;
  overflow-y: auto;
}


.category-menu :deep(.el-menu-item),
.category-menu :deep(.el-sub-menu__title) {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 14px;
  font-weight: 500;
  height: 42px;
  line-height: 42px;
  margin: 2px 8px;
  border-radius: 8px;
  
  transition:
    background-color 220ms cubic-bezier(0.4, 0, 0.2, 1),
    color 220ms cubic-bezier(0.4, 0, 0.2, 1),
    padding-left 220ms cubic-bezier(0.4, 0, 0.2, 1);
}


.category-menu :deep(.el-menu-item:hover),
.category-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--color-bg-alt);
  color: var(--color-text);
}

.category-menu :deep(.el-menu-item:not(.is-active):hover) {
  padding-left: 20px; 
}


.category-menu :deep(.el-menu-item:hover .el-icon),
.category-menu :deep(.el-sub-menu__title:hover .el-icon) {
  transform: translateX(2px);
}


.category-menu :deep(.el-menu-item .el-icon),
.category-menu :deep(.el-sub-menu__title .el-icon) {
  transition: transform 220ms cubic-bezier(0.4, 0, 0.2, 1);
}


.category-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-weight: 600;
  position: relative;
}

.category-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
  transition: opacity 220ms ease;
}


.category-menu :deep(.el-menu .el-menu .el-menu-item) {
  font-size: 13px;
  height: 36px;
  line-height: 36px;
  padding-left: 48px;
  color: var(--color-text-muted);
}

.category-menu :deep(.el-menu .el-menu .el-menu-item.is-active) {
  background-color: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-weight: 600;
  padding-left: 52px; 
}


.category-menu :deep(.el-sub-menu__title .el-sub-menu__icon-arrow) {
  transition: transform 280ms cubic-bezier(0.4, 0, 0.2, 1);
}

.category-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title .el-sub-menu__icon-arrow) {
  transform: rotateZ(90deg);
}


.category-menu :deep(.el-menu--collapse) .el-sub-menu__title span:not(.el-sub-menu__icon-arrow) {
  display: none;
}


.category-menu :deep(.el-menu) {
  --el-transition-duration: 320ms;
  --el-transition-function-ease-in-out-bezier: cubic-bezier(0.4, 0, 0.2, 1);
}

/* ============================================================
 * 分类项:名字吃掉剩余宽度 + 数量靠右
 * ============================================================ */
.cat-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-count {
  margin-left: auto;
  align-self: center;
  height: 18px;
  line-height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--color-bg-alt);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 11.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
  transition:
    color 220ms cubic-bezier(0.4, 0, 0.2, 1),
    background-color 220ms cubic-bezier(0.4, 0, 0.2, 1);
}

/* 父级标题里的数字要让开右侧的展开箭头(箭头是绝对定位的) */
.category-menu :deep(.el-sub-menu__title) .cat-count {
  margin-right: 14px;
}

.category-menu :deep(.el-menu-item:hover) .cat-count,
.category-menu :deep(.el-menu-item.is-active) .cat-count,
.category-menu :deep(.el-sub-menu__title:hover) .cat-count {
  color: var(--color-accent);
  background: rgba(76, 175, 80, 0.12);
}

/* 分类加载中 / 失败 */
.cat-placeholder {
  padding: 14px 20px;
}

.cat-placeholder.is-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 20px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.sidebar-promo {
  margin: auto 16px 20px;
  padding: 16px;
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.08) 0%, rgba(76, 175, 80, 0.02) 100%);
  border: 1px dashed var(--color-accent);
  border-radius: 12px;
  text-align: center;
  transition: opacity 200ms ease;
}

.promo-tag {
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent);
  font-weight: 700;
  margin: 0 0 6px;
}

.promo-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 13px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--color-text);
  line-height: 1.3;}

.promo-price {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-accent);
  margin: 0;}


.is-collapsed .sidebar-promo {
  display: none;
}




.main {
  background: var(--color-bg);
  
  padding: 32px 32px 32px calc(240px + 24px);
  min-height: calc(100vh - 64px);
  transition: padding-left 320ms cubic-bezier(0.4, 0, 0.2, 1);
}

.is-collapsed .main {
  
  padding-left: 24px;
}


.is-collapsed .sidebar-title {
  display: none;
}

/* =============================================================
 * Responsive - 移动端(<= 900px)
 * - sidebar 浮层模式 + backdrop 遮罩
 * - main 全宽,无 sidebar padding
 * - 搜索框隐藏(空间不够)
 * - topbar padding 缩小
 * ============================================================= */
@media (max-width: 900px) {
  .sidebar {
    /* 移动端浮层模式 - 默认 translateX(-100%) 隐藏在屏外 */
    transform: translateX(-100%);
    box-shadow: none;
  }
  /* 移动端展开时(menu-open class) - 滑入 */
  .is-mobile.menu-open .sidebar {
    transform: translateX(0);
    /* 展开后阴影变深,提示用户它在前面 */
    box-shadow:
      4px 0 24px rgba(0, 0, 0, 0.10),
      24px 0 64px rgba(0, 0, 0, 0.08);
  }
  .main {
    padding: 16px;
    transition: none;
  }
  .search {
    display: none;
  }
  .topbar {
    padding: 0 16px;
    gap: 12px;
  }
  .topbar-right {
    gap: 8px;
  }
  /* 移动端 sidebar-toggle 强制显示(桌面默认隐藏) */
  .sidebar-toggle {
    display: grid !important;
  }
}

/* =============================================================
 * 桌面端 - 默认隐藏 sidebar-toggle(避免冗余),
 * 侧栏常驻可见(只是 64px / 240px 切换)
 * ============================================================= */
@media (min-width: 901px) {
  /* 桌面端 sidebar 始终可见,只是宽度变 */
  .main {
    padding-left: calc(240px + 24px);
  }
  .is-collapsed .main {
    padding-left: 24px;
  }
  .sidebar-toggle {
    display: grid;
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
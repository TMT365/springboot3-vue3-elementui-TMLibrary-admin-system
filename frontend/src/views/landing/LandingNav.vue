<script setup lang="ts">
/**
 * LandingNav  -  顶部 sticky 导航
 *
 * - Logo + 项目名(左)
 * - 主导航链接 + 下拉子菜单(中)
 * - 主题切换 + 登录/进入后台(右)
 * - 移动端汉堡菜单
 *
 * 主题状态走 useTheme composable(单例),不在这里维护 local ref。
 * v2 §7-#11:从 LandingNav 局部 state 升级到全站 composable,
 * 切到 admin 再回 / 不会丢主题。
 */
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'

const userStore = useUserStore()
const { theme, toggleTheme } = useTheme()

interface NavChild {
  label: string
  anchor: string
}
interface NavItem {
  label: string
  anchor?: string
  children?: NavChild[]
}

const navItems: NavItem[] = [
  { label: '首页', anchor: '#home' },
  {
    label: '功能介绍',
    children: [
      { label: '图书管理', anchor: '#feature-books' },
      { label: '订单管理', anchor: '#feature-orders' },
      { label: '用户管理', anchor: '#feature-users' },
    ],
  },
  {
    label: '使用方法',
    children: [
      { label: '快速开始', anchor: '#quick-start' },
      { label: '常见问题', anchor: '#faq' },
    ],
  },
  { label: '联系方式', anchor: '#contact' },
  { label: '学习经历', anchor: '#learning-log' },
]

const mobileOpen = ref(false)

function scrollToAnchor(anchor: string) {
  mobileOpen.value = false
  const el = document.querySelector(anchor)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <nav class="landing-nav" :class="{ 'mobile-open': mobileOpen }">
    <div class="nav-inner">
      <a class="logo" href="#home" @click.prevent="scrollToAnchor('#home')">
        <span class="logo-mark">T</span>
        <span class="logo-text">TMLibrary</span>
      </a>

      <button
        class="hamburger"
        :aria-expanded="mobileOpen"
        aria-label="切换菜单"
        @click="mobileOpen = !mobileOpen"
      >
        <span /><span /><span />
      </button>

      <ul class="nav-links" :class="{ open: mobileOpen }">
        <li
          v-for="item in navItems"
          :key="item.label"
          class="nav-item"
          :class="{ 'has-children': !!item.children }"
        >
          <a
            v-if="item.anchor"
            :href="item.anchor"
            class="nav-link"
            @click.prevent="scrollToAnchor(item.anchor)"
          >
            {{ item.label }}
          </a>

          <template v-else-if="item.children">
            <span class="nav-link nav-parent">{{ item.label }}</span>
            <ul class="nav-dropdown">
              <li v-for="child in item.children" :key="child.label">
                <a
                  :href="child.anchor"
                  @click.prevent="scrollToAnchor(child.anchor)"
                >
                  {{ child.label }}
                </a>
              </li>
            </ul>
          </template>
        </li>
      </ul>

      <div class="nav-actions">
        <label class="theme-toggle" :title="theme === 'light' ? '切换到夜间模式' : '切换到日间模式'">
          <input
            type="checkbox"
            :checked="theme === 'dark'"
            @change="toggleTheme"
          />
          <span class="toggle-track">
            <span class="toggle-thumb" />
          </span>
        </label>

        <router-link
          v-if="userStore.isAuthenticated"
          to="/user/books"
          class="admin-link"
        >
          我的空间
        </router-link>
        <router-link v-else to="/login" class="login-link">登录</router-link>

        <router-link
          v-if="!userStore.isAuthenticated"
          to="/register"
          class="register-link"
        >注册</router-link>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.landing-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(250, 248, 243, 0.78);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--color-border, #e8e5dc);
  transition: background 200ms ease;
}

:root[data-theme='dark'] .landing-nav {
  background: rgba(26, 26, 26, 0.78);
}

.nav-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 28px;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}

.logo-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--color-accent, #4caf50);
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 18px;
  border-radius: 8px;
}

.logo-text {
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 19px;
  letter-spacing: -0.01em;
}

.hamburger {
  display: none;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  background: transparent;
  border: none;
  cursor: pointer;
}

.hamburger span {
  width: 22px;
  height: 2px;
  background: var(--color-text, #212121);
  border-radius: 2px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.nav-item {
  position: relative;
}

.nav-link {
  display: block;
  padding: 8px 14px;
  color: var(--color-text, #212121);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  transition: background 150ms ease;
  cursor: pointer;
}

.nav-link:hover {
  background: var(--color-bg-alt, #f5f5f5);
  color: var(--color-accent, #4caf50);
}

.has-children > .nav-link::after {
  content: '▾';
  margin-left: 6px;
  font-size: 10px;
  opacity: 0.6;
}

.nav-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 160px;
  padding: 6px;
  margin: 0;
  list-style: none;
  background: var(--color-bg, #faf8f3);
  border: 1px solid var(--color-border, #e8e5dc);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-4px);
  transition: opacity 150ms ease, transform 150ms ease, visibility 150ms;
}

.has-children:hover > .nav-dropdown,
.has-children:focus-within > .nav-dropdown {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.nav-dropdown a {
  display: block;
  padding: 8px 12px;
  font-size: 14px;
  color: var(--color-text, #212121);
  text-decoration: none;
  border-radius: 4px;
}

.nav-dropdown a:hover {
  background: var(--color-bg-alt, #f5f5f5);
  color: var(--color-accent, #4caf50);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.theme-toggle {
  position: relative;
  display: inline-block;
  cursor: pointer;
}

.theme-toggle input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.toggle-track {
  display: block;
  width: 44px;
  height: 24px;
  background: var(--color-border, #e8e5dc);
  border-radius: 999px;
  position: relative;
  transition: background 200ms ease;
}

.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: transform 200ms ease;
}

.theme-toggle input:checked + .toggle-track {
  background: var(--color-accent, #4caf50);
}

.theme-toggle input:checked + .toggle-track .toggle-thumb {
  transform: translateX(20px);
}

.login-link,
.admin-link {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  border-radius: 6px;
  transition: all 150ms ease;
}

.login-link {
  background: var(--color-accent, #4caf50);
  color: #fff;
}

.login-link:hover {
  background: var(--color-accent-hover, #43a047);
}

.admin-link {
  color: var(--color-text, #212121);
  border: 1px solid var(--color-border, #e8e5dc);
}

.admin-link:hover {
  border-color: var(--color-accent, #4caf50);
  color: var(--color-accent, #4caf50);
}

/* 注册按钮  -  描边 + 绿色字,跟登录的实心绿形成层级 */
.register-link {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  border-radius: 6px;
  color: var(--color-accent, #4caf50);
  background: transparent;
  border: 1px solid var(--color-accent, #4caf50);
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}

.register-link:hover {
  background: var(--color-accent, #4caf50);
  color: #fff;
}

@media (max-width: 900px) {
  .hamburger {
    display: flex;
    order: 3;
  }
  .nav-links {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 12px;
    background: var(--color-bg, #faf8f3);
    border-bottom: 1px solid var(--color-border, #e8e5dc);
    transform: translateY(-8px);
    opacity: 0;
    pointer-events: none;
    transition: opacity 200ms ease, transform 200ms ease;
  }
  .nav-links.open {
    transform: translateY(0);
    opacity: 1;
    pointer-events: auto;
  }
  .nav-item {
    width: 100%;
  }
  .nav-link {
    padding: 12px;
  }
  .nav-dropdown {
    position: static;
    opacity: 1;
    visibility: visible;
    transform: none;
    box-shadow: none;
    border: none;
    padding-left: 12px;
    background: transparent;
  }
  .has-children:hover > .nav-dropdown,
  .has-children:focus-within > .nav-dropdown {
    transform: none;
  }
  .nav-actions {
    order: 2;
  }
}
</style>
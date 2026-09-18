<script setup lang="ts">
/**
 * LandingNav  -  顶部 sticky 导航
 *
 * - Logo + 项目名(左)
 * - 主导航链接 + 下拉子菜单(中)
 * - 主题切换 + 登录/进入后台(右)
 * - 移动端汉堡菜单
 *
 * v3 改造:
 *   - 把指示器从「每个 nav-link 的 ::after 伪元素」解耦成「ul 容器里的共享滑块」,
 *     FLIP 动画跨 link 平滑位移。
 *   - 状态从 DOM class 改成响应式 ref:pinned(click 锁定) 和 hovered(鼠标悬停),
 *     sliderTarget = hovered ?? pinned,鼠标预览结束后恢复点击锁定项。
 *   - 下拉子项改用左侧绿色竖条(::before 高度过渡),与水平滑块视觉呼应但实现独立。
 *   - 移动端隐藏滑块,改用 .is-active 类做静态高亮(列布局不需要平滑位移)。
 */
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
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
  { label: '联系方式', anchor: '#contact' },
  { label: '学习经历', anchor: '#learning-log' },
  // 登录 / 注册入口 —— ≤480px 时导航右侧的「注册」按钮会被隐藏,
  // 这一项是窄屏访客在首页找到入口的路,所以排在「项目结构」之前
  { label: '登录注册', anchor: '#get-started' },
  // 放最后 —— 这一项面向"想看代码 / 想参与"的人,是导航里最"深"的一层
  { label: '项目结构', anchor: '#project-structure' },
]

const mobileOpen = ref(false)

/** 点击后锁定的顶层导航项和子项 */
const pinnedItem = ref<NavItem | null>(null)
const pinnedChild = ref<{ parent: NavItem; child: NavChild } | null>(null)

/** 当前鼠标悬停的顶层导航项和子项,移出整个导航区域后清空 */
const hoveredItem = ref<NavItem | null>(null)
const hoveredChild = ref<{ parent: NavItem; child: NavChild } | null>(null)

/** 鼠标悬停时预览当前项,离开后恢复点击锁定项 */
const sliderTarget = computed<NavItem | null>(() => {
  return hoveredItem.value ?? pinnedItem.value
})

/** 子项边框同样优先跟随鼠标,离开后恢复点击锁定项 */
const activeChild = computed<{ parent: NavItem; child: NavChild } | null>(() => {
  return hoveredChild.value ?? pinnedChild.value
})

/* ---------------- DOM refs ---------------- */

const ulRef = ref<HTMLUListElement | null>(null)
const itemRefs = new Map<String, HTMLLIElement>()
const sliderStyle = ref({
  transform: 'translate(0px, 0px)',
  width: '0px',
  height: '0px',
  opacity: '0',
})

function setItemRef(item: NavItem, el: unknown) {
  // 先用label作为key,因为anchor可能不存在,或者重复(比如父子都指向#home)，后面可以改成用label+anchor的组合key
  // 或是使用Symbol来唯一标识每个item，或是data-attribute来存储唯一标识符
  if (el) itemRefs.set(item.label, el as HTMLLIElement)
  else itemRefs.delete(item.label)

}

/* ---------------- 交互事件 ---------------- */

function onEnterItem(item: NavItem) {
  hoveredItem.value = item
  hoveredChild.value = null
}

function onEnterChild(parent: NavItem, child: NavChild) {
  hoveredItem.value = parent
  hoveredChild.value = { parent, child }
}

function onNavLeave() {
  hoveredItem.value = null
  hoveredChild.value = null
}

function onClickItem(item: NavItem) {
  pinnedItem.value = item
  pinnedChild.value = null
}

function onClickChild(parent: NavItem, child: NavChild) {
  pinnedItem.value = parent
  pinnedChild.value = { parent, child }
}

function scrollToAnchor(anchor: string) {
  mobileOpen.value = false
  const el = document.querySelector(anchor)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/* ---------------- 滑块定位 ---------------- */

function moveSlider() {
  const ul = ulRef.value;
  if (!ul) return

  const target = sliderTarget.value
  if (!target) {
    sliderStyle.value = { ...sliderStyle.value, opacity: '0' }
    return
  }

  // computed 计算出来的值是Proxy代理响应式，直接和 Map 的 key 比较会失败，所以要用 target.label 来获取对应的 li 元素
  const li = itemRefs.get(target.label)

  if (!li) return

  const ulRect = ul.getBoundingClientRect()
  const liRect = li.getBoundingClientRect()
  const newX = liRect.left - ulRect.left
  const newY = liRect.top - ulRect.top
  const newW = liRect.width
  const newH = liRect.height
  sliderStyle.value = {
    transform: `translate(${newX}px, ${newY}px)`,
    width: `${newW}px`,
    height: `${newH}px`,
    opacity: '1',
  }
}

/* ---------------- 监听 & 生命周期 ---------------- */

watch(sliderTarget, () => {
  moveSlider();
}, { flush: 'post' })

let resizeObserver: ResizeObserver | null = null
onMounted(() => {
  if (ulRef.value) {
    resizeObserver = new ResizeObserver(moveSlider)
    resizeObserver.observe(ulRef.value)
  }
  nextTick(moveSlider)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})
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

      <ul
        ref="ulRef"
        class="nav-links"
        :class="{ open: mobileOpen }"
        @mouseleave="onNavLeave"
      >
        <span
          class="nav-slider"
          :style="sliderStyle"
          aria-hidden="true"
        />

        <li
          v-for="item in navItems"
          :key="item.label"
          class="nav-item"
          :class="{
            'has-children': !!item.children,
            'is-active': sliderTarget === item
          }"
          :ref="(el) => setItemRef(item, el)"
          @mouseenter="onEnterItem(item)"
        >
          <a
            v-if="item.anchor"
            :href="item.anchor"
            class="nav-link"
            @click.prevent="onClickItem(item); scrollToAnchor(item.anchor)"
          >
            {{ item.label }}
          </a>

          <template v-else-if="item.children">
            <span class="nav-link nav-parent" @click="onClickItem(item)">
              {{ item.label }}
            </span>
            <ul class="nav-dropdown">
              <li
                v-for="child in item.children"
                :key="child.label"
                class="nav-dropdown-item"
                :class="{
                  'is-pinned':
                    activeChild?.parent.label === item.label &&
                    activeChild?.child.label === child.label
                }"
                @mouseenter="onEnterChild(item, child)"
              >
                <a
                  :href="child.anchor"
                  @click.prevent="onClickChild(item, child); scrollToAnchor(child.anchor)"
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
  max-width: 100vw;
  margin: 0 0;
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
  position: relative; /* 滑块的定位上下文 */
  display: flex;
  align-items: center;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
}

/* 共享滑块 —— ul 里的唯一元素,跨 link 平滑位移 */
.nav-slider {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  background: var(--color-accent, #4caf50);
  border-radius: 30px;
  pointer-events: none;
  z-index: 0;
  will-change: transform, width, height;
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.28);
  transition:
    transform 280ms cubic-bezier(0.22, 0.61, 0.36, 1),
    width 220ms ease,
    height 220ms ease,
    opacity 180ms ease;
}

.nav-item {
  position: relative;
  list-style: none;
}

.nav-link {
  position: relative;
  z-index: 1; /* 文字在滑块之上 */
  display: block;
  padding: 8px 14px;
  color: var(--color-text, #212121);
  text-decoration: none;
  font-size: 18px;
  font-weight: 500;
  border-radius: 30px;
  cursor: pointer;
  transition: color 180ms ease;
}

/* 滑块在该 nav-item 时文字反白 */
.nav-item.is-active > .nav-link {
  color: #fff;
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

.nav-dropdown-item {
  list-style: none;
}

.nav-dropdown-item a {
  position: relative;
  display: block;
  padding: 8px 12px 8px 16px;
  font-size: 14px;
  color: var(--color-text, #212121);
  text-decoration: none;
  border-radius: 4px;
  transition: color 150ms ease, background 150ms ease;
}

/* 左侧绿色竖条 —— pinned 时从 0 长到 60% 高度 */
.nav-dropdown-item a::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 50%;
  width: 3px;
  height: 0;
  background: var(--color-accent, #4caf50);
  border-radius: 2px;
  transform: translateY(-50%);
  transition: height 220ms cubic-bezier(0.4, 0, 0.2, 1);
}

.nav-dropdown-item a:hover {
  background: var(--color-bg-alt, #f5f5f5);
}

/* 子项被点击选中 —— 绿色文字 + 绿色竖条 */
.nav-dropdown-item.is-pinned a {
  color: var(--color-accent, #4caf50);
}

.nav-dropdown-item.is-pinned a::before {
  height: 60%;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  /* 跟前面的 logo + nav-links 拉开:auto margin 吸收剩余空间,
     让 logo + ul 挤在左边,.nav-actions 推至最右。
     对桌面端生效;移动端 nav-links 走 position: absolute,
     视觉上 logo 左 / nav-actions + 汉堡挤在右,也合理。 */
  margin-left: auto;
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
    /* 展开的菜单是一层"浮"在页面上的面板,得让人看出它是浮的。
       原来用 --color-bg —— 而页面底色也是 --color-bg,两者**一模一样**,
       加上没有投影、没有圆角,展开后就是"一块和背景同色的区域 + 一条分割线",
       完全没有层次(浅色下尤其明显:米白压米白)。
       改成 --color-card(浅色 #fff / 暗色 #1f1f1f)比页面底色亮一档,
       再补上圆角和向下的投影,和桌面端 .nav-dropdown 的处理保持一致。 */
    background: var(--color-card, #fff);
    border-bottom: 1px solid var(--color-border, #e8e5dc);
    border-radius: 0 0 16px 16px;
    box-shadow: 0 18px 32px -14px var(--color-shadow-strong, rgba(17, 25, 40, 0.18));
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
  /* 移动端隐藏滑块,直接用 .is-active 类做静态高亮 */
  .nav-slider {
    display: none;
  }
  .nav-item {
    width: 100%;
  }
  .nav-link {
    padding: 12px;
  }
  /* 移动端高亮用纯背景色 */
  .nav-item.is-active > .nav-link {
    background: var(--color-accent, #4caf50);
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

/* 窄屏手机(≤480):顶栏一行放不下 logo + 主题开关 + 登录 + 注册 + 汉堡
   (≈430px > 375px),而 .landing 是 overflow-x: hidden —— 溢出的部分会被直接裁掉,
   最坏情况把汉堡按钮切掉、菜单打不开。这里收掉「注册」(登录页里有注册入口)、
   缩小 logo 和间距,把整行压回 375px 以内。 */
@media (max-width: 480px) {
  .nav-inner {
    padding: 10px 14px;
    gap: 10px;
  }

  .register-link {
    display: none;
  }

  .nav-actions {
    gap: 8px;
  }

  .logo-text {
    font-size: 15px;
  }

  .login-link {
    padding: 6px 10px;
  }
}
</style>

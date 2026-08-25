<script setup lang="ts">
/**
 * SidebarMenu  -  后台侧栏菜单
 *
 * v2 升级:支持折叠模式(el-menu :collapse)
 * - collapsed=true → 只显示 icon
 * - collapsed=false → 显示 icon + 文字,左侧 3px accent 高亮
 * - 当前路由高亮:最长前缀匹配(支持 /books/123/edit 高亮 /books)
 *
 * 用法:
 *   <SidebarMenu :collapsed="sidebarCollapsed" />
 */

import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  collapsed?: boolean
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  index: string
  title: string
  icon: string
  admin?: boolean
}

const allItems: MenuItem[] = [
  { index: '/admin/dashboard', title: '仪表盘', icon: 'Odometer' },
  { index: '/admin/books', title: '图书列表', icon: 'Reading' },
  { index: '/admin/purchases', title: '订单列表', icon: 'List' },
  { index: '/admin/users', title: '用户管理', icon: 'UserFilled', admin: true },
]

const items = computed<MenuItem[]>(() =>
  allItems.filter((item) => !item.admin || userStore.isAdmin),
)

/**
 * 高亮算法  -  最长前缀匹配
 *
 * 对当前 route.path,在 items 里找最长且满足下列条件的 index:
 *   path === idx                                  ← 精确命中
 *   path.startsWith(idx + '/')                    ← idx 是 path 的祖先段
 *
 * 关键点:必须 `+ '/'`,否则 `/admin/users` 会被 `/admin/usersExtra` 误命中
 */
const activeIndex = computed<string>(() => {
  const path = route.path
  const candidates = items.value.map((i) => i.index).sort((a, b) => b.length - a.length)
  return candidates.find((idx) => path === idx || path.startsWith(idx + '/')) || path
})

/**
 * 侧栏点击 → 同 AdminLayout 内切换,用 replace 不污染历史栈
 * (layout 切换由 Landing / MallLayout / UserLayout 顶栏触发,那些仍用 push)
 */
function handleSelect(idx: string): void {
  if (idx !== route.path) router.replace(idx)
}
</script>

<template>
  <el-menu
    :default-active="activeIndex"
    class="sidebar-menu"
    :collapse="props.collapsed"
    :collapse-transition="false"
    @select="handleSelect"
  >
    <el-menu-item
      v-for="item in items"
      :key="item.index"
      :index="item.index"
    >
      <el-icon><component :is="item.icon" /></el-icon>
      <template #title>{{ item.title }}</template>
    </el-menu-item>
  </el-menu>
</template>

<style scoped>
.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
  /* 折叠过渡(el-menu 自带) */
}

.sidebar-menu :deep(.el-menu-item) {
  font-size: 14px;
  margin: 4px 8px;
  border-radius: 8px;
  height: 44px;
  line-height: 44px;
}
</style>
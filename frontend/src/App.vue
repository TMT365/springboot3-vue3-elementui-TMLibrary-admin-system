<script setup lang="ts">
/**
 * 根组件  -  全局 LoadingScreen + <router-view />
 *
 * 加载动画原先只挂在 Landing(首页),现在提到根组件:
 * 任何路由下刷新页面都先播一次动画,播完才渲染路由内容。
 *
 * 两个角色的分工:
 *   - LoadingScreen:负责视觉,等 window.load 后自己淡出并调 store.finish()
 *   - App.vue:加载期间用 v-if 挡住 router-view,避免"内容先闪一下再播动画"
 */
import { onMounted } from 'vue'
import { useLoadingStore } from '@/stores/loading'
import { useIpBanStore } from '@/stores/ipBan'
import LoadingScreen from '@/components/LoadingScreen.vue'
import IpBanDialog from '@/components/IpBanDialog.vue'

const loadingStore = useLoadingStore()
const ipBanStore = useIpBanStore()

/* ============================================================
 * ⚠️ 临时:风控弹窗预览开关(视觉确认完成后删除这段)
 *
 * 用法:任意页面 URL 后面加 ?demo-ban=1,例如
 *   http://127.0.0.1:5173/?demo-ban=1
 *   http://127.0.0.1:5173/admin/dashboard?demo-ban=1
 *
 * 只在带参数时触发,不影响正常使用;刷新页面可反复弹出。
 * ============================================================ */
onMounted(() => {
  if (!new URLSearchParams(window.location.search).has('demo-ban')) return

  const pad = (n: number): string => String(n).padStart(2, '0')
  const fmt = (d: Date): string =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

  const bannedAt = new Date()
  const expiresAt = new Date(bannedAt.getTime() + (23 * 3600 + 53 * 60) * 1000)

  ipBanStore.show({
    ip: '203.0.113.88',
    reason: '60 秒内请求 214 次,超过阈值 150 次',
    hitCount: 214,
    bannedAt: fmt(bannedAt),
    expiresAt: fmt(expiresAt),
    remainingSeconds: 23 * 3600 + 53 * 60,
  })
})
</script>

<template>
  <LoadingScreen />
  <router-view v-if="!loadingStore.isLoading" />

  <!-- IP 风控封禁弹窗 —— 由 request.ts 收到 429 时触发,见 stores/ipBan.ts -->
  <IpBanDialog />
</template>

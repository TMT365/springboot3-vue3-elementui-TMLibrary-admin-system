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
import NoticeDialog from '@/components/NoticeDialog.vue'
import FeedbackFab from '@/components/FeedbackFab.vue'
// Element Plus 简体中文语言包。
//
// 为什么需要它:项目用 unplugin-vue-components 按需引入 EP 组件,
// 没有 app.use(ElementPlus, { locale }),于是 EP 一律用英文默认语言包 ——
// 分页器上写着 "Total 12 / 10/page / Go to",下拉是 "No data",
// 日期选择器是 "Select date"。中文站点里混着英文,一眼就看出没配 locale。
//
// 用 el-config-provider 而不是 app.use(ElementPlus):
// 后者会把整个 EP 注册成全局组件(和按需引入互相打脸,包体翻倍);
// el-config-provider 只是往下传一份语言配置,组件仍然按需引入。
import zhCn from 'element-plus/es/locale/lang/zh-cn'

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
  <!--
    el-config-provider 把 Element Plus 的语言包统一切成简体中文
    (分页器、下拉空态、日期选择、消息框的默认文案都走它)。
    必须放在最外层 —— 它靠 provide 往下传,被包住的组件才拿得到。
  -->
  <el-config-provider :locale="zhCn">
    <LoadingScreen />
    <router-view v-if="!loadingStore.isLoading" />

    <!-- IP 风控封禁弹窗 —— 由 request.ts 收到 429 时触发,见 stores/ipBan.ts -->
    <IpBanDialog />

    <!--
      浏览须知 —— 登录成功后由 Login.vue 调 noticeStore.open() 触发(见 stores/notice.ts)。
      挂全局而不是挂在登录页:登录成功后马上就会 router.push 走人,弹窗要跟着用户
      到落地页(商城 / 后台)继续显示,挂在登录页会在跳转的一瞬间被卸载掉。
    -->
    <NoticeDialog />

    <!--
      反馈入口 —— 挂全局而不是某个 layout 里:
      用户遇到问题时可能在任何一页(商城 / 订单 / 后台),挂在这里就不用
      "先找到反馈入口再描述问题"。未登录时点击会引导去登录并带回跳地址。
    -->
    <FeedbackFab />
  </el-config-provider>
</template>

<script setup lang="ts">
/**
 * IpBanDialog —— 「访问已被限制」风控封禁弹窗
 *
 * 挂在 App.vue 上,由 useIpBanStore 驱动(见 stores/ipBan.ts 的链路说明)。
 *
 * 视觉:
 *   - 居中的大尺寸弹窗(min(680px, 92vw),手机上也不溢出)
 *   - 顶部大号警告图标 + 标题 + 说明
 *   - 4 行信息(IP / 原因 / 封禁时间 / 解封时间),每行一个 El 图标
 *   - 剩余时间实时倒计时(后端给 remainingSeconds,前端每秒 -1,不依赖本地时钟)
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useIpBanStore } from '@/stores/ipBan'
import { formatDateTime } from '@/utils/format'

const store = useIpBanStore()

/** 倒计时秒数 —— 从后端给的 remainingSeconds 开始,每秒递减 */
const remaining = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

watch(
  () => store.visible,
  (open) => {
    if (open) {
      remaining.value = store.info?.remainingSeconds ?? 0
      startTimer()
    } else {
      stopTimer()
    }
  },
)

function startTimer(): void {
  stopTimer()
  timer = setInterval(() => {
    if (remaining.value > 0) {
      remaining.value -= 1
    } else {
      stopTimer()
    }
  }, 1000)
}

function stopTimer(): void {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onBeforeUnmount(stopTimer)

/** 秒 → "23 小时 59 分" / "59 分 30 秒" / "30 秒" */
const humanRemaining = computed<string>(() => {
  const s = remaining.value
  if (s <= 0) return '即将解封'
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h} 小时 ${m} 分`
  if (m > 0) return `${m} 分 ${sec} 秒`
  return `${sec} 秒`
})

const visible = computed({
  get: () => store.visible,
  set: (v: boolean) => {
    if (!v) store.close()
  },
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="min(680px, 92vw)"
    align-center
    :close-on-click-modal="false"
    class="ip-ban-dialog"
    aria-label="访问已被限制"
  >
    <div class="ban-body">
      <!-- 顶部大图标 —— 圆形警示徽章 -->
      <div class="ban-badge" aria-hidden="true">
        <el-icon :size="46"><WarnTriangleFilled /></el-icon>
      </div>

      <h2 class="ban-title">访问已被限制</h2>
      <p class="ban-sub">
        系统检测到该 IP 存在异常高频访问,已触发安全风控并临时封禁
      </p>

      <!-- 信息行 -->
      <dl class="ban-info">
        <div class="info-row">
          <el-icon class="row-icon"><Location /></el-icon>
          <dt>IP 地址</dt>
          <dd class="is-mono">{{ store.info?.ip ?? ' - ' }}</dd>
        </div>
        <div class="info-row">
          <el-icon class="row-icon"><Warning /></el-icon>
          <dt>触发原因</dt>
          <dd>{{ store.info?.reason ?? ' - ' }}</dd>
        </div>
        <div class="info-row">
          <el-icon class="row-icon"><Clock /></el-icon>
          <dt>封禁时间</dt>
          <dd>{{ formatDateTime(store.info?.bannedAt) }}</dd>
        </div>
        <div class="info-row">
          <el-icon class="row-icon"><Unlock /></el-icon>
          <dt>解封时间</dt>
          <dd>{{ formatDateTime(store.info?.expiresAt) }}</dd>
        </div>
      </dl>

      <!-- 倒计时 -->
      <div class="ban-countdown">
        <el-icon class="cd-icon"><Timer /></el-icon>
        <div class="cd-text">
          <span class="cd-label">剩余封禁时间</span>
          <span class="cd-value">{{ humanRemaining }}</span>
        </div>
      </div>

      <p class="ban-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>如果是正常访问被误判,请联系管理员人工解封</span>
      </p>
    </div>

    <template #footer>
      <el-button type="primary" size="large" class="ban-ok" @click="store.close()">
        我知道了
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ban-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 4px 8px 0;
}

/* ============================================================
 * 警示徽章 —— 圆形 + 呼吸动画
 *
 * 三层叠加做出"心跳"感(全用 CSS,无 JS):
 *   1. 徽章本体:scale 1 → 1.045 缓慢起伏 + 外圈光晕同步扩散/收缩
 *   2. ::before / ::after:两层光环向外扩散并淡出(相位错开 1.3s),
 *      像雷达波一圈圈推开
 *
 * 配色走 CSS 变量 → 亮暗两套主题共用同一组 keyframes,
 * 暗色下把红色调亮、透明度加大(见文件末尾的 dark 覆盖)。
 * 项目已有全局 prefers-reduced-motion 规则会把这些动画压到 0.01ms,无障碍自动覆盖。
 * ============================================================ */
.ban-badge {
  position: relative;
  width: 88px;
  height: 88px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  /* 纯亮红 #ff0000 —— 一个 RGB 变量驱动图标 / 底色 / 光晕 / 光环,
     以后想调色只改 --badge-rgb 这一处。
     底色浓度是关键:15% 会让整块看成"淡粉",红色被稀释掉;
     拉到 30% 才是"红"。 */
  --badge-rgb: 255, 0, 0;
  --badge-bg: rgba(var(--badge-rgb), 0.30);
  --badge-glow: rgba(var(--badge-rgb), 0.26);
  --badge-ring: rgba(var(--badge-rgb), 0.85);
  background: var(--badge-bg);
  color: rgb(var(--badge-rgb));
  /* 底部留 26px:光环最大扩到 1.55 倍,别糊到下面的标题上 */
  margin-bottom: 26px;
  animation: badge-breathe 2.6s ease-in-out infinite;
}

.ban-badge::before,
.ban-badge::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid var(--badge-ring);
  pointer-events: none;
  animation: badge-ripple 2.6s ease-out infinite;
}

/* 第二层错开半个周期 —— 一圈还没散完下一圈已经开始,视觉上连绵不断 */
.ban-badge::after {
  animation-delay: 1.3s;
}

@keyframes badge-breathe {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 8px var(--badge-glow);
  }
  50% {
    transform: scale(1.045);
    box-shadow: 0 0 0 16px var(--badge-glow);
  }
}

@keyframes badge-ripple {
  0% {
    transform: scale(1);
    opacity: 0.75;
  }
  70%,
  100% {
    /* 扩散到 1.55 倍 + 完全透明,后面保持透明避免"闪回" */
    transform: scale(1.55);
    opacity: 0;
  }
}

/* 图标本身跟着徽章微微起伏 —— 比徽章再快半拍,看起来更"活" */
.ban-badge .el-icon {
  animation: badge-icon-pulse 2.6s ease-in-out infinite;
}

@keyframes badge-icon-pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.08);
  }
}

.ban-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 10px;
  color: var(--color-text);
}

.ban-sub {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-muted);
  margin: 0 0 26px;
  max-width: 460px;
}

/* 信息表 —— 两列对齐,行间用极淡分隔线 */
.ban-info {
  width: 100%;
  margin: 0 0 20px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--color-bg-alt);
}

.info-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
}

.info-row:last-child {
  border-bottom: none;
}

.row-icon {
  color: var(--color-text-soft);
  flex-shrink: 0;
}

.info-row dt {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-soft);
}

.info-row dd {
  margin: 0;
  font-size: 13.5px;
  color: var(--color-text);
  word-break: break-all;
}

/* IP 用等宽数字,和列表页风格一致 */
.info-row dd.is-mono {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

/* 倒计时条 —— 强调块,和上面的信息表拉开层级 */
.ban-countdown {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.10) 0%, rgba(76, 175, 80, 0.04) 100%);
  border: 1px solid rgba(76, 175, 80, 0.22);
  margin-bottom: 18px;
}

.cd-icon {
  font-size: 26px;
  color: var(--color-accent);
}

.cd-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.cd-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.cd-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.ban-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--color-text-soft);
  margin: 0;
}

.ban-ok {
  min-width: 160px;
}

/* ============================================================
 * 暗色适配
 *
 * 问题:这套配色是照亮色定的 —— 深底上整体发灰,
 * 面板底色(--color-bg-alt #212121)和弹窗卡片(#1f1f1f)几乎同色,
 * 标签文字用 --color-text-soft(#757575)在深底上更是糊成一片。
 * 这里把徽章红色调亮、面板提亮一档、标签换成 muted 级亮度。
 * 动画的 keyframes 不用改 —— 配色走 CSS 变量,两套主题共用。
 * ============================================================ */
:root[data-theme='dark'] .ban-badge {
  /* 纯红 #ff0000 在深底上会发闷 → 提亮成 #ff3b30(饱和度不掉,亮度更高);
     底色浓度同步加大,暗色里整块才是"亮红"而不是"暗红" */
  --badge-rgb: 255, 59, 48;
  --badge-bg: rgba(var(--badge-rgb), 0.34);
  --badge-glow: rgba(var(--badge-rgb), 0.30);
  --badge-ring: rgba(var(--badge-rgb), 0.9);
}

:root[data-theme='dark'] .ban-info {
  /* 面板比卡片亮一档,整块才立得住 */
  background: #262626;
  border-color: #3a3a3a;
}

:root[data-theme='dark'] .info-row {
  border-bottom-color: #343434;
}

/* 标签/图标:soft(#757575)→ muted(#bdbdbd) */
:root[data-theme='dark'] .info-row dt,
:root[data-theme='dark'] .row-icon {
  color: var(--color-text-muted);
}

:root[data-theme='dark'] .info-row dd {
  /* 内容值再亮一档,和信息标签拉开层级 */
  color: var(--color-text);
}

:root[data-theme='dark'] .ban-countdown {
  background: linear-gradient(
    135deg,
    rgba(102, 187, 106, 0.18) 0%,
    rgba(102, 187, 106, 0.06) 100%
  );
  border-color: rgba(102, 187, 106, 0.38);
}

:root[data-theme='dark'] .cd-label,
:root[data-theme='dark'] .ban-tip {
  color: var(--color-text-muted);
}

/* 窄屏:标题和徽章缩一档 */
@media (max-width: 480px) {
  .ban-badge {
    width: 68px;
    height: 68px;
    margin-bottom: 14px;
  }

  .ban-title {
    font-size: 22px;
  }

  .info-row {
    padding: 10px 12px;
  }

  .info-row dt {
    width: 68px;
  }
}
</style>

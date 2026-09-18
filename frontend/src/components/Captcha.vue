<script setup lang="ts">
/**
 * Captcha  -  验证码组件(可复用)
 *
 * 用在登录页 / 注册页:
 *   <Captcha
 *     v-model="form.captcha"
 *     :username="form.username"
 *     type="login"
 *   />
 *
 * 行为:
 *   1. mount 立即拉一次 —— 进登录/注册页就能看到图,不依赖任何 prop
 *   2. 用户点验证码图 → 重新拉
 *   3. 倒计时归零 → 自动换一张
 *   4. 加载失败 → toast 提示
 *
 * 注意:captcha 不再绑 username。原版有这个绑定是想着"防止极端情况 A 的图被 B
 * 用",但实际上后端 captcha value 里仍存 username,提交时做 defense-in-depth 校验,
 * 这层防御没丢,只是挪到了服务端 —— 前端没必要再因为用户名变了就换图。
 *
 * UI:
 *   - 输入框全宽
 *   - 下面一行:[验证码图(hover 出「换一张」tooltip)] [倒计时圆 + 「剩余」]
 *   - 鼠标悬停在图上,浏览器原生 title 弹出「换一张」(替代原来显眼的文字链)
 *   - 没有显式的「换一张」按钮 —— 减少视觉噪音,点图 / hover 看提示就够
 *
 * 倒计时:
 *   - 数字:剩余秒数(每秒更新,归零后 0s 表示过期)
 *   - 圆形:SVG <circle> 的 stroke-dashoffset 用 CSS 动画从 0 → 圆周长
 *     (180s linear forwards),圆形从满到空 drain 出去
 *   - <circle :key> 改 key 强制重渲染,动画从 0 重新开始
 */

import { onBeforeUnmount, ref } from 'vue'
import { authApi } from '@/api/auth'

/**
 * 生成 captcha 请求的 uuid —— 三层兜底,适应各种运行环境。
 *
 * <h2>为什么不直接用 crypto.randomUUID()</h2>
 * 那个 API 在以下场景会抛 TypeError("crypto.randomUUID is not a function"):
 *   1. 非 HTTPS / 非 localhost 环境 + 老浏览器 ——
 *      Chrome 92+/Firefox 95+ 在 http:// 上 Crypto Subtle 不可用。
 *   2. IE(虽然项目已不考虑,但用户的浏览器由不得你选)
 *   3. 极个别 webview/嵌入式浏览器
 *
 * <h2>三层兜底策略</h2>
 *   ① 原生 crypto.randomUUID —— 主流浏览器 + localhost/HTTPS,带标准 v4 + RFC 4122 标记
 *   ② 手搓 v4 用 crypto.getRandomValues —— 退一步,只要 crypto 还能用(非安全上下文也能 getRandomValues)
 *   ③ Math.random —— 最后一搏,够"唯一"就行(captcha 的 uuid 不参与安全校验,
 *      唯一作用是让前端拿到一个 32 位串给后端做 Redis key 的命名空间,
 *      重复概率 1/2^122 可以忽略)
 */
function generateUuid(): string {
  // ① 优先走原生 —— 标准实现,带 -4xxx-y 版本与变体位,合规
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // ② crypto 还能 getRandomValues(非安全上下文也可用)—— 手搓 v4 UUID
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    // 版本位 v4:第 7 字节高 4 位 = 0100 → (bytes[6] & 0x0f) | 0x40
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    // 变体位 RFC 4122:第 9 字节高 2 位 = 10 → (bytes[8] & 0x3f) | 0x80
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex: string[] = []
    for (let i = 0; i < 16; i++) {
      hex.push(bytes[i].toString(16).padStart(2, '0'))
    }
    return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
  }
  // ③ 兜底:Math.random 拼一个形似 uuid 的串 —— 不是真 v4,但 captcha 这场景够用
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

interface Props {
  /** v-model 双向绑定的 captcha 输入框内容 */
  modelValue: string
  /** 用途,决定调 /api/captcha/login 还是 /api/captcha/register */
  type: 'login' | 'register'
}
const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uuid = ref<string>(generateUuid())
const captchaUrl = ref<string>('')
/* 后端 expiresAt 是 string(避免 JS Number 精度风险),内部转 number 做倒计时计算 */
const expiresAt = ref<number>(0)
const captchaLoading = ref(false)
let reqId = 0

/* ----- 倒计时参数 ----- */
/** 后端 TTL = 3 分钟(180 秒),跟后端 CaptchaServiceImpl.CAPTCHA_TTL_MINUTES 一致 */
const TTL_SECONDS = 180
/** SVG 半径,周长 = 2 * π * 24 ≈ 150.8 */
const RADIUS = 24
const CIRCUMFERENCE = 2 * Math.PI * RADIUS
const secondsLeft = ref<number>(TTL_SECONDS)
/** 让 <circle> 强制重渲染的 key —— key 变化就重新挂载,CSS 动画从 0 开始 */
const circleKey = ref(0)

/* 实时把剩余秒数同步到 view —— requestAnimationFrame 比 setInterval 更跟手 */
let rafId: number | null = null
function tick(): void {
  if (expiresAt.value > 0) {
    const remaining = Math.max(0, Math.ceil((expiresAt.value - Date.now()) / 1000))
    secondsLeft.value = remaining

    /* 倒计时归零 → 自动换一张
     * 后端 Redis 的 TTL 与倒计时同步,归零意味着旧图已失效,不换白不换。
     * expiresAt 先置 0:① 避免下一帧重复触发;② loadCaptcha 成功后会写入新的 expiresAt。 */
    if (remaining <= 0) {
      expiresAt.value = 0
      refreshCaptcha()
    }
  }
  rafId = requestAnimationFrame(tick)
}

async function loadCaptcha(): Promise<void> {
  const myId = ++reqId
  captchaLoading.value = true
  try {
    const api = props.type === 'login' ? authApi.loginCaptcha : authApi.registerCaptcha
    /* 请求体里不再带 username —— captcha 不再绑 username(见组件顶部注释)。
     * 后端 GetCaptchaRequest 的 username 字段保留(提交时仍要存进 value 做防御),
     * 这里发空串占位就行,后端不会再拿它做 key。 */
    const resp = await api(uuid.value, { username: '' })
    if (myId !== reqId) return
    captchaUrl.value = resp.image
    /* 后端传 String,这里转 number 给 setInterval / animation 用 */
    expiresAt.value = Number(resp.expiresAt) || 0
    secondsLeft.value = TTL_SECONDS
    circleKey.value++ /* 触发 CSS 动画重置 */
    /* 重新拉就清空用户已输入的 captcha,避免新图跟旧字符不匹配 */
    emit('update:modelValue', '')
  } catch (err) {
    if (myId !== reqId) return
    const detail = err instanceof Error ? err.message : '未知错误'
    ElMessage.error(`验证码加载失败: ${detail}`)
    // eslint-disable-next-line no-console
    console.error('[captcha] load failed', err)
  } finally {
    if (myId === reqId) captchaLoading.value = false
  }
}

function refreshCaptcha(): void {
  uuid.value = generateUuid()
  void loadCaptcha()
}

/* 启动 rAF 倒计时 tick —— 即便没 captcha 也要让视图响应式更新 */
rafId = requestAnimationFrame(tick)

/*
 * mount 立即拉一次验证码图 —— Captcha 不再绑 username,触发时机只有两个:
 *   ① 组件 mount(进登录/注册页时立即拉)
 *   ② 用户点验证码图(显式 refresh)
 *   ③ 倒计时归零(自动换一张)
 *
 * 之前绑 username 的写法存在三个真问题:
 *   1. 用户进登录页 → username 是空 → 验证码不加载 → 用户得先输完用户名才能看到图
 *   2. 用户每次敲一个字符 → 400ms debounce 再发一次请求,纯浪费
 *   3. 把 username 编进 Redis key,中文用户名会带进 key 字符串,
 *      对运维的 KEYS / SCAN 都不友好
 *
 * 真正的"用户名绑定"语义由后端守住:captcha value 里的 username 字段在提交时
 * 仍做 defense-in-depth 校验(见 AuthServiceImpl / UserManagementServiceImpl),
 * 即使前端不再主动换,服务端也认。
 */
void loadCaptcha()

onBeforeUnmount(() => {
  if (rafId != null) cancelAnimationFrame(rafId)
})

/* expose 给父组件(Login/Register)拿 uuid 和强制 refresh */
defineExpose({
  uuid,
  refresh: refreshCaptcha,
})
</script>

<template>
  <div class="captcha-block">
    <!-- 输入框:全宽 -->
    <el-input
      :model-value="modelValue"
      placeholder="请输入图中字符"
      maxlength="4"
      clearable
      class="captcha-input"
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />

    <!-- 第二行:图片(左) + 倒计时圆(右) ——「换一张」是图片的 title tooltip,不再单独占位 -->
    <div class="captcha-row">
      <div
        class="captcha-img-wrap"
        :class="{ 'is-loading': captchaLoading, 'is-empty': !captchaUrl }"
        :title="captchaUrl ? '换一张' : ''"
        @click="refreshCaptcha()"
      >
        <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" class="captcha-img" />
        <span v-else class="captcha-placeholder">{{ captchaLoading ? '加载中…' : '暂无图像' }}</span>
      </div>

      <!-- 倒计时圆形 SVG —— 仅 captcha 加载后显示(没有 captcha 显示一个空圆 180s 误导) -->
      <div
        v-if="captchaUrl"
        class="countdown"
        :class="{ 'is-expired': secondsLeft <= 0 }"
      >
        <svg viewBox="0 0 60 60" width="48" height="48" aria-hidden="true">
          <!-- 底圈:浅灰实心圆 -->
          <circle
            class="cd-bg"
            cx="30" cy="30" :r="RADIUS"
            fill="none"
            stroke-width="4"
          />
          <!-- 进度圈:从顶部开始 drain -->
          <circle
            :key="circleKey"
            class="cd-progress"
            cx="30" cy="30" :r="RADIUS"
            fill="none"
            stroke-width="4"
            :stroke-dasharray="CIRCUMFERENCE"
            :stroke-dashoffset="0"
            :style="{ animationDuration: `${TTL_SECONDS}s` }"
          />
          <!-- 中央秒数 -->
          <text
            x="30" y="36"
            text-anchor="middle"
            class="cd-text"
          >{{ secondsLeft }}s</text>
        </svg>
        <span class="countdown-label">{{ secondsLeft <= 0 ? '已过期' : '剩余' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.captcha-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.captcha-input {
  width: 100%;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.captcha-img-wrap {
  width: 130px;
  height: 50px;
  display: grid;
  place-items: center;
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  user-select: none;
  flex: 0 0 auto;
  transition: opacity 200ms ease;
}

.captcha-img-wrap.is-loading {
  opacity: 0.6;
  cursor: wait;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.captcha-placeholder {
  font-size: 12px;
  color: var(--color-text-soft);
}

/* 倒计时圆 + 右侧标签(横排) */
.countdown {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  user-select: none;
}

.countdown svg {
  display: block;
  /* 让进度圈从 12 点钟方向开始 drain */
  transform: rotate(-90deg);
}

/* 底圈 */
.cd-bg {
  stroke: var(--color-border);
}

/* 进度圈:动画从 stroke-dashoffset 0 → CIRCUMFERENCE(全长)
   视觉上就是从满圆到空圆 linear 漏出去 */
.cd-progress {
  stroke: var(--color-accent);
  stroke-linecap: round;
  animation-name: captcha-drain;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

@keyframes captcha-drain {
  from { stroke-dashoffset: 0; }
  to   { stroke-dashoffset: v-bind('CIRCUMFERENCE'); }
}

/* 中央秒数 —— SVG 里 text 默认从顶部 baseline 算,需要单独抵消旋转 */
.cd-text {
  font-size: 14px;
  font-weight: 600;
  fill: var(--color-text);
  /* SVG 整体被旋转了 -90deg,text 也跟着转了,得反向转回来 */
  transform: rotate(90deg);
  transform-origin: 30px 30px;
}

.countdown-label {
  font-size: 11px;
  color: var(--color-text-soft);
}

/* 过期态:把圆和文字变红 */
.countdown.is-expired .cd-progress {
  animation: none;
  stroke-dashoffset: 0;
  stroke: var(--color-text-soft);
}
.countdown.is-expired .cd-text {
  fill: var(--color-text-soft);
}
.countdown.is-expired .countdown-label {
  color: var(--color-text-soft);
}

/* 暗色模式下颜色调一下 */
:root[data-theme='dark'] .cd-bg {
  stroke: var(--color-border);
}
:root[data-theme='dark'] .countdown.is-expired .cd-progress,
:root[data-theme='dark'] .countdown.is-expired .cd-text {
  fill: var(--color-text-soft);
  stroke: var(--color-text-soft);
}

/* 占位态(还没拉 captcha)—— 不要看起来像可点击的图 */
.captcha-img-wrap.is-empty {
  cursor: default;
}
.captcha-img-wrap.is-empty:hover {
  border-color: var(--color-border); /* 不变色,提示不可交互 */
}

/* =============================================================
 * 窄屏(手机)—— 双重保险,保证横向永不溢出
 *
 * 上层页面已经会在窄屏把表单标签切到顶部(整行 ~279px 可用),
 * 这里再做一层:元素缩一档 + 允许换行,即使外层没切也装得下。
 *   116(图) + 12(gap) + 74(圆 40 + 间距 + "剩余") ≈ 202px
 * ============================================================= */
@media (max-width: 480px) {
  .captcha-row {
    /* 真的装不下时换行,而不是撑破卡片 */
    flex-wrap: wrap;
  }

  .captcha-img-wrap {
    width: 116px;
    height: 44px;
  }

  .countdown svg {
    width: 40px;
    height: 40px;
  }
}
</style>

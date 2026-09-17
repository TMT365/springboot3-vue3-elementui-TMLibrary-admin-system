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
 *   1. 页面打开不拉 —— 等用户输入 username 才开始拉(避免拿到一张绑空 username 的图)
 *   2. watch username:400ms debounce → 重生 uuid + 重新拉
 *   3. username 清空 → 撤销已加载的 captcha(绑了旧 username 也用不上了)
 *   4. 失败 / 用户点图 → 重新拉
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

import { onBeforeUnmount, ref, watch } from 'vue'
import { authApi } from '@/api/auth'

interface Props {
  /** v-model 双向绑定的 captcha 输入框内容 */
  modelValue: string
  /** 用户名变化触发刷新(后端 captcha 跟 username 绑定) */
  username: string
  /** 用途,决定调 /api/captcha/login 还是 /api/captcha/register */
  type: 'login' | 'register'
}
const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const uuid = ref<string>(crypto.randomUUID())
const captchaUrl = ref<string>('')
/* 后端 expiresAt 是 string(避免 JS Number 精度风险),内部转 number 做倒计时计算 */
const expiresAt = ref<number>(0)
const captchaLoading = ref(false)
let reqId = 0
let debounceTimer: ReturnType<typeof setTimeout> | null = null

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
     * expiresAt 先置 0:① 避免下一帧重复触发;② loadCaptcha 成功后会写入新的 expiresAt。
     * username 为空时不触发(没有 username 拉了也用不了)。 */
    if (remaining <= 0 && props.username) {
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
    const resp = await api(uuid.value, { username: props.username })
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
  uuid.value = crypto.randomUUID()
  void loadCaptcha()
}

/* 启动 rAF 倒计时 tick —— 即便没 captcha 也要让视图响应式更新 */
rafId = requestAnimationFrame(tick)

/* username 变化 → debounce 400ms → 重新拉;username 清空 → 撤销 captcha */
watch(
  () => props.username,
  (val) => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    if (!val) {
      /* username 清空 → 撤销 captcha(图片 / 倒计时全清)
       * 注意:image 是 base64 data URI,不是 objectURL,不需要 revoke */
      captchaUrl.value = ''
      expiresAt.value = 0
      secondsLeft.value = TTL_SECONDS
      emit('update:modelValue', '')
      return
    }
    debounceTimer = setTimeout(() => {
      uuid.value = crypto.randomUUID()
      void loadCaptcha()
    }, 400)
  },
)

onBeforeUnmount(() => {
  if (rafId != null) cancelAnimationFrame(rafId)
  if (debounceTimer) clearTimeout(debounceTimer)
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
        :aria-disabled="!props.username"
        @click="props.username && refreshCaptcha()"
      >
        <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" class="captcha-img" />
        <span v-else-if="!props.username" class="captcha-placeholder">请先输入用户名</span>
        <span v-else class="captcha-placeholder">加载中…</span>
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

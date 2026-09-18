<script setup lang="ts">
/**
 * 重置密码  -  凭邮件链接里的令牌改密(/reset-password,公开路由)
 *
 * 令牌从 query 拿:邮件里的链接是
 *   ${FRONTEND_BASE_URL}/reset-password?token=<43字符 Base64URL>
 * 用 query 而不是路径参数:令牌是凭据,放 query 后端不用为它再定义一段路由,
 * 而且 43 字符塞进 path 段在日志 / 分享场景里更容易被误当成普通路径。
 *
 * 三种状态,互斥:
 *   1. 无令牌  → 直接给「链接无效」,不渲染表单(填完再报错是浪费用户时间)
 *   2. 表单    → 新密码 + 确认密码,前端校验两次一致 + 长度 6-20
 *   3. 成功    → 提示 + 2 秒后跳 /login(延迟是给成功态一个被看见的机会,
 *                立刻跳走用户不确定到底成没成)
 *
 * 失败(400)时把后端 msg **原样**展示:令牌过期 / 已用过 / 新密码与原密码相同,
 * 三种原因的处置方式完全不同(重新申请 vs 换个密码),笼统的"重置失败"会误导。
 *
 * 卡片外观全部来自 AuthCard;第三方登录与重置密码无关,show-social 关掉。
 */

import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { ApiError } from '@/utils/request'
import AuthCard from '@/components/AuthCard.vue'

const route = useRoute()
const router = useRouter()

/**
 * query 参数的类型是 `string | null | Array<string | null>`,不是 string。
 * 直接当字符串用会在 ?token=a&token=b(或某些工具改写 URL)时拿到数组,
 * 传给后端就是 `"[object Object]"` 之类 —— 这里统一收敛成 string。
 */
const token = computed<string>(() => {
  const raw = route.query.token
  if (Array.isArray(raw)) return (raw[0] ?? '').trim()
  return (raw ?? '').trim()
})

/** 空令牌直接判为无效链接:后端会返回 400,不如前端先说清楚 */
const hasToken = computed(() => token.value.length > 0)

const form = reactive({
  newPassword: '',
  confirmPassword: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)
/** 提交成功 → 切成功态并开始倒计时跳转 */
const done = ref(false)
/** 后端 400 的 msg,原样展示 */
const errorMsg = ref('')

/** 自动跳登录的倒计时(秒),只用于文案展示 */
const REDIRECT_SECONDS = 2
const countdown = ref(REDIRECT_SECONDS)
let timer: number | undefined

function clearTimer(): void {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startRedirect(): void {
  countdown.value = REDIRECT_SECONDS
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearTimer()
      void router.push('/login')
    }
  }, 1000)
}

// 用户可能在 2 秒内手动点走(下一步 / 关标签),定时器不清理会在卸载后push
onUnmounted(clearTimer)

/** 二次确认:和 Register.vue 同一套写法(校验函数必须是 callback 风格) */
function validateConfirmPassword(
  _rule: unknown,
  value: string,
  callback: (err?: Error) => void,
): void {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules<typeof form> = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function onSubmit(): Promise<void> {
  if (!hasToken.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''
  try {
    await authApi.resetPassword(token.value, form.newPassword)
    done.value = true
    ElMessage.success('密码已重置,请用新密码登录')
    startRedirect()
  } catch (err) {
    // request.ts 已经 toast 过一次;这里再在表单里留一份 ——
    // toast 几秒就没了,而"为什么没改成功"用户需要一直看得到。
    // ApiError.msg 就是后端 Result.msg 原文,不做任何改写。
    errorMsg.value = err instanceof ApiError ? err.msg : '重置失败,请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="重置密码" subtitle="Set New Password" :show-social="false">
    <!-- ---------- 1. 无令牌:链接无效 ---------- -->
    <div v-if="!hasToken" class="rp-state" role="alert">
      <span class="rp-state-icon is-error" aria-hidden="true">
        <el-icon><CircleClose /></el-icon>
      </span>
      <p class="rp-state-title">链接无效</p>
      <p class="rp-state-text">
        这个重置链接不完整,或者已经失效。请重新申请一封重置邮件。
      </p>
      <router-link class="rp-primary" to="/forgot-password">
        重新申请重置链接
        <el-icon><ArrowRight /></el-icon>
      </router-link>
    </div>

    <!-- ---------- 2. 成功:倒计时跳登录 ---------- -->
    <div v-else-if="done" class="rp-state" role="status" aria-live="polite">
      <span class="rp-state-icon is-ok" aria-hidden="true">
        <el-icon><CircleCheck /></el-icon>
      </span>
      <p class="rp-state-title">密码已重置</p>
      <p class="rp-state-text">
        请用新密码登录。{{ countdown }} 秒后自动跳转到登录页…
      </p>
      <router-link class="rp-primary" to="/login">
        立即前往登录
        <el-icon><ArrowRight /></el-icon>
      </router-link>
    </div>

    <!-- ---------- 3. 表单 ---------- -->
    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="6-20 位"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再输入一次"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <!-- 后端 400 的原因原文(令牌过期 / 与原密码相同…),原样展示 -->
      <p v-if="errorMsg" class="rp-error" role="alert">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ errorMsg }}</span>
      </p>

      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
          class="auth-submit"
        >
          重置密码
        </el-button>
      </el-form-item>
    </el-form>

    <template #footer>
      <router-link class="rp-foot-link" to="/login">返回登录</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
/* =============================================================
 * 状态块(链接无效 / 重置成功共用)
 * 颜色全部走 var(--color-*),亮暗两套自动成立
 * ============================================================= */
.rp-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.rp-state-icon {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border-radius: 50%;
  font-size: 23px;
  line-height: 1;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
}

/* 失败态用 EP 的危险色变量,不写死红色 —— 暗色下 EP 自己会调亮 */
.rp-state-icon.is-error {
  background: color-mix(in srgb, var(--el-color-danger) 12%, transparent);
  color: var(--el-color-danger);
  /* color-mix 不被支持时退回纯色描边,至少不是一块突兀的实底 */
  border: 1px solid transparent;
}

.rp-state-title {
  margin: 0 0 8px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 400;
  color: var(--color-text);
}

.rp-state-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

/* 状态块里的主行动点:胶囊按钮,和 ForgotPassword 的成功态一致 */
.rp-primary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 20px;
  padding: 10px 22px;
  border-radius: 20px;
  background: var(--color-card);
  color: var(--color-accent);
  box-shadow:
    0 0 0 1px var(--color-border),
    0 3px 10px var(--color-shadow);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  transition: box-shadow 200ms ease, transform 200ms ease;
}

.rp-primary:hover {
  transform: translateY(-1px);
  box-shadow:
    0 0 0 1px var(--color-accent),
    0 8px 18px var(--color-shadow);
}

.rp-primary .el-icon {
  transition: transform 200ms ease;
}

.rp-primary:hover .el-icon {
  transform: translateX(3px);
}

/* =============================================================
 * 表单里的错误提示  -  后端 msg 原文
 * 不放进 el-alert:那是"页面级通告"的体量,这里只需要一行贴身说明
 * ============================================================= */
.rp-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 14px;
  padding: 9px 12px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--el-color-danger) 10%, transparent);
  color: var(--el-color-danger);
  font-size: 12.5px;
  line-height: 1.6;
}

.rp-error .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 14px;
}

/* 底部插槽链接 —— AuthCard 的 .auth-foot 已给了字号和颜色 */
.rp-foot-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.rp-foot-link:hover {
  text-decoration: underline;
}
</style>

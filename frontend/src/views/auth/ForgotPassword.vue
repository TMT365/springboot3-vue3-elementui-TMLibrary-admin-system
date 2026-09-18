<script setup lang="ts">
/**
 * 忘记密码  -  申请重置链接(/forgot-password,公开路由)
 *
 * 流程:
 *   1. 用户填注册时用的邮箱 → POST /api/users/forgot-password {email}
 *   2. 后端**永远返回 200**,成功 → 切到成功态(不再是表单)
 *   3. 用户去邮箱点链接 → 前端 /reset-password?token=...(见 ResetPassword.vue)
 *
 * <h2>为什么"提交成功"不等于"邮箱存在"</h2>
 * 后端对三种情况一视同仁:邮箱没注册 / 命中多个账号 / 正常发出邮件。
 * 一旦前端能区分,这个公开接口就成了批量探测"哪些邮箱在本站有账号"的工具。
 * 所以这里:
 *   - 不判断响应内容(也没什么可判断的,data 恒为 null)
 *   - 成功态话术用"如果该邮箱已注册…"开头,对两种情况都成立
 *   - 不提供"邮箱不存在?去注册"这类暗示性引导
 *
 * 卡片外观(页面底 / 圆角卡片 / 输入框 / 主按钮)全部来自 AuthCard,
 * 这一页只写表单和成功态两块内容。第三方登录跟"找回密码"无关,
 * 用 :show-social="false" 关掉。
 */

import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import AuthCard from '@/components/AuthCard.vue'

const form = reactive({
  email: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)
/** 提交成功 → 切成成功态(表单整体换掉,避免用户反复点提交) */
const sent = ref(false)

const rules: FormRules<typeof form> = {
  email: [
    { required: true, message: '请输入注册时使用的邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

/** 成功态上"换一个邮箱重试" —— 回到表单并保留已填内容方便改 */
function backToForm(): void {
  sent.value = false
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.forgotPassword(form.email)
    // 走到这里 = 后端收了;它不会告诉我们邮箱存不存在,我们也不问
    sent.value = true
  } catch {
    // 网络错误 / 被限流:request.ts 已经 toast 过,停在本页让用户重试。
    // 注意这里**不能**提示"邮箱不存在" —— 后端压根不会因为这个失败。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard
    title="忘记密码"
    subtitle="Reset Password"
    :show-social="false"
  >
    <!-- ---------- 成功态:替换整个表单 ---------- -->
    <div v-if="sent" class="fp-done" role="status" aria-live="polite">
      <span class="fp-done-icon" aria-hidden="true">
        <el-icon><Promotion /></el-icon>
      </span>

      <p class="fp-done-title">邮件已发送</p>

      <!-- 话术对"注册过"和"没注册过"必须一样,所以用"如果…已注册"起头 -->
      <p class="fp-done-text">
        如果该邮箱已注册,我们已发送重置链接,请查收(30 分钟内有效)。
      </p>

      <p class="fp-done-hint">
        没收到?先看看垃圾邮件文件夹;链接过期了可以
        <button class="fp-link" type="button" @click="backToForm">
          换一个邮箱重新申请
        </button>
        。
      </p>

      <router-link class="fp-back" to="/login">
        返回登录
        <el-icon><ArrowRight /></el-icon>
      </router-link>
    </div>

    <!-- ---------- 表单态 ---------- -->
    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="form.email"
          type="email"
          placeholder="注册时填写的邮箱"
          autocomplete="email"
          clearable
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
          class="auth-submit"
        >
          发送重置链接
        </el-button>
      </el-form-item>
    </el-form>

    <template #footer>
      <router-link class="fp-foot-link" to="/login">想起密码了?返回登录</router-link>
    </template>
  </AuthCard>
</template>

<style scoped>
/* =============================================================
 * 成功态  -  沿用卡片里既有的排版语言(居中 / serif 标题 / muted 正文)
 * 颜色一律走 var(--color-*),亮暗两套自动成立
 * ============================================================= */
.fp-done {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.fp-done-icon {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border-radius: 50%;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-size: 23px;
  line-height: 1;
}

.fp-done-title {
  margin: 0 0 8px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 400;
  color: var(--color-text);
}

.fp-done-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--color-text-muted);
}

.fp-done-hint {
  margin: 12px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--color-text-soft);
}

/* 行内文字按钮:下划线扫过,和 Landing 里的 .learning-link 是同一套语言 */
.fp-link {
  padding: 0;
  border: none;
  border-bottom: 1px solid var(--color-border);
  background: none;
  color: var(--color-accent);
  font-family: inherit;
  font-size: inherit;
  font-style: normal;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 200ms ease;
}

.fp-link:hover {
  border-bottom-color: var(--color-accent);
}

/* 主链接做成一枚胶囊按钮 —— 成功态里它是唯一的主行动点 */
.fp-back {
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

.fp-back:hover {
  transform: translateY(-1px);
  box-shadow:
    0 0 0 1px var(--color-accent),
    0 8px 18px var(--color-shadow);
}

.fp-back .el-icon {
  transition: transform 200ms ease;
}

.fp-back:hover .el-icon {
  transform: translateX(3px);
}

/* 底部插槽里的链接 —— AuthCard 的 .auth-foot 已经给了字号和颜色 */
.fp-foot-link {
  color: var(--color-accent);
  font-weight: 600;
  text-decoration: none;
}

.fp-foot-link:hover {
  text-decoration: underline;
}
</style>

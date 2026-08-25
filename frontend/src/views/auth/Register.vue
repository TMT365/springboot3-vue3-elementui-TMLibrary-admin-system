<script setup lang="ts">
/**
 * 注册页  -  P5 上线(白名单已通)
 *
 * 字段(对齐后端 UserRegisterRequest):
 *   username / password / email / phoneNumber
 * 额外:confirmPassword 二次确认(前端校验,不发到后端)
 *
 * 成功后 → ElMessage 提示 + 跳 /login
 */

import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phoneNumber: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)

/** 二次确认密码校验 */
function validateConfirmPassword(
  _rule: unknown,
  value: string,
  callback: (err?: Error) => void,
): void {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^\d{11}$/, message: '手机号必须是 11 位数字', trigger: 'blur' },
  ],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      password: form.password,
      email: form.email,
      phoneNumber: form.phoneNumber,
    })
    ElMessage.success(`注册成功!欢迎,${form.username}`)
    await router.replace('/login')
  } catch {
    // request.ts 拦截器已经 ElMessage.error,这里只 swallow
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-deco" aria-hidden="true">
      <div class="deco-circle deco-circle-1" />
      <div class="deco-circle deco-circle-2" />
    </div>

    <el-card class="register-card">
      <template #header>
        <div class="register-header">
          <span class="register-logo">T</span>
          <div>
            <div class="register-title">注册新账号</div>
            <div class="register-subtitle">TMLibrary · Join Us</div>
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="3-20 位"
            autocomplete="username"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6-20 位"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="example@domain.com"
            autocomplete="email"
            clearable
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phoneNumber">
          <el-input
            v-model="form.phoneNumber"
            placeholder="11 位数字"
            autocomplete="tel"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            class="register-submit"
          >
            注册
          </el-button>
        </el-form-item>
        <div class="register-footer">
          已有账号?<router-link to="/login" replace class="footer-link">去登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.register-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--color-bg);
  color: var(--color-text);
  overflow: hidden;
  padding: 24px;
}

.register-deco {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(76, 175, 80, 0.14) 0%,
    transparent 70%
  );
}

.deco-circle-1 {
  width: 480px;
  height: 480px;
  top: -160px;
  right: -180px;
}

.deco-circle-2 {
  width: 360px;
  height: 360px;
  bottom: -120px;
  left: -140px;
}

.register-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 460px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 12px 40px var(--color-shadow-strong);
}

.register-card :deep(.el-card__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
}

.register-card :deep(.el-card__body) {
  padding: 24px;
}

.register-header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.register-logo {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  background: var(--color-accent);
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 400;
  font-size: 22px;
  border-radius: 10px;
}

.register-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 20px;
  font-weight: 400;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.register-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-top: 2px;
}

.register-submit {
  width: 100%;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.register-submit:hover {
  background: var(--color-accent-hover);
  border-color: var(--color-accent-hover);
}

.register-footer {
  margin-top: 4px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
}

.footer-link {
  color: var(--color-accent);
  text-decoration: none;
  margin-left: 4px;
  font-weight: 500;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>
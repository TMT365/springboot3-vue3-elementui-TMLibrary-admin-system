<script setup lang="ts">
/**
 * 登录页  -  第一个真功能页
 *
 * 验证:
 * 1. Composition API(reactive / ref)
 * 2. Element Plus el-form + rules
 * 3. authApi.login → userStore.setLogin → router.push(redirect || '/dashboard')
 *
 * v2 §7-#12:视觉从 deep blue/purple gradient 改为 editorial 风格,
 *           跟 Landing 的暖米白 / 绿色基调对齐。
 *
 * 已知问题:后端 /api/users/register 不在 JWT 白名单 → 顶部 alert 提示,不放注册入口
 */

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' },
  ],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.error('参数错误，请检查表单输入')
    return
  } 

  loading.value = true
  try {
    const resp = await authApi.login({ username: form.username, password: form.password })
    userStore.setLogin(resp)
    ElMessage.success(`欢迎,${resp.username}`)
    // 登录后所有角色统一进商城(/mall) -  后续可在商城内导航到个人中心/后台
    const defaultRedirect = '/mall'
    const redirect = (route.query.redirect as string) || defaultRedirect
    await router.push(redirect)
  } catch {
    // request.ts 拦截器已经 ElMessage.error,这里只 swallow
    
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-deco" aria-hidden="true">
      <div class="deco-circle deco-circle-1" />
      <div class="deco-circle deco-circle-2" />
    </div>

    <el-card class="login-card">
      <template #header>
        <div class="login-header">
          <span class="login-logo">T</span>
          <div>
            <div class="login-title">TMLibrary</div>
            <div class="login-subtitle">后台管理系统</div>
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-submit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--color-bg);
  color: var(--color-text);
  overflow: hidden;
  padding: 24px;
}

.login-deco {
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

.login-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 420px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 12px 40px var(--color-shadow-strong);
}

.login-card :deep(.el-card__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
}

.login-card :deep(.el-card__body) {
  padding: 24px;
}

.login-header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.login-logo {
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

.login-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 20px;
  font-weight: 400;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.login-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-top: 2px;
}

.login-submit {
  width: 100%;
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.login-submit:hover {
  background: var(--color-accent-hover);
  border-color: var(--color-accent-hover);
}
</style>
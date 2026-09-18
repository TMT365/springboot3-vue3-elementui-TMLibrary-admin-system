<script setup lang="ts">
/**
 * 注册页  -  P5 上线(白名单已通)
 *
 * 字段(对齐后端 UserRegisterRequest):
 *   username / password / email / phoneNumber / captcha / uuid
 * 额外:confirmPassword 二次确认(前端校验,不发到后端)
 * captcha 复用 <Captcha> 组件(type='register'),走 /api/captcha/register
 *
 * 成功后 → ElMessage 提示 + 跳 /login
 */

import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useMediaQuery } from '@/composables/useMediaQuery'
import Captcha from '@/components/Captcha.vue'
// 圆角卡片外壳(页面底 + 卡片 + 输入框 / 主按钮 / 社交按钮样式都收在里面)
import AuthCard from '@/components/AuthCard.vue'

const router = useRouter()

/** 窄屏标签改顶部 —— 同 Login.vue,给验证码行留出整行宽度 */
const isNarrow = useMediaQuery('(max-width: 600px)')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  phoneNumber: '',
  captcha: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaRef = ref<InstanceType<typeof Captcha> | null>(null)

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
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码必须 4 位', trigger: 'blur' },
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
      captcha: form.captcha,
      uuid: captchaRef.value?.uuid ?? '',
    })
    ElMessage.success(`注册成功!欢迎,${form.username}`)
    await router.replace('/login')
  } catch {
    // request.ts 拦截器已经 ElMessage.error,这里只 swallow。
    // 不刷新 captcha —— 后端只在注册成功后才删 Redis 里的验证码,
    // 失败时用户可以重试;换图只能靠用户点击或倒计时归零。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="注册新账号" subtitle="TMLibrary · Join Us">
    <!-- 卡片外观 / 输入框 / 主按钮的样式都在 AuthCard 里,这里只管表单本身
         (form 字段 / rules / 提交逻辑都没动)。

         标签统一放输入框上方:卡片按设计稿收窄到 350px 后,右侧标签会把内容
         压到 350-70(内边距)-90(标签)= 190px,而验证码那一行
         「图 130 + 间距 12 + 倒计时 82」≈ 226px —— 横向溢出。
         原来只在 ≤600px 才切 top 的逻辑,在窄卡片上桌面上也得成立。 -->
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
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
      <el-form-item label="验证码" prop="captcha">
        <Captcha
          ref="captchaRef"
          v-model="form.captcha"
          :username="form.username"
          type="register"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
          class="auth-submit"
        >
          注册
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 底部一行:卡片页脚插槽(不在 form 内,避免回车误触提交) -->
    <template #footer>
      已有账号?<router-link to="/login" replace class="footer-link">去登录</router-link>
    </template>
  </AuthCard>
</template>

<!--
  卡片 / 输入框 / 主按钮 / 页面底的样式都在 src/components/AuthCard.vue。
  这里只挂注册页独有的东西:页脚那行的链接(插槽内容仍属本组件作用域)。
-->
<style scoped>
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
<script setup lang="ts">
/**
 * 登录页  -  第一个真功能页
 *
 * 验证:
 * 1. Composition API(reactive / ref)
 * 2. Element Plus el-form + rules
 * 3. 验证码用 <Captcha> 组件(v-model:captcha)
 * 4. authApi.login → userStore.setLogin → router.push
 *
 * 流程(API §2.3 + §8.1):
 *   1. 进入页面 → Captcha 组件 mount 立即拉 /api/captcha/login
 *   2. 输入 username → 组件 400ms debounce 重新拉(跟 username 绑定)
 *   3. POST /api/users/login {username, password, captcha, uuid}
 *   4. 失败:request.ts 已 toast;此处触发 Captcha 组件刷新
 */

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useNoticeStore } from '@/stores/notice'
import { useMediaQuery } from '@/composables/useMediaQuery'
import Captcha from '@/components/Captcha.vue'
// 圆角卡片外壳(页面底 + 卡片 + 输入框 / 主按钮 / 社交按钮样式都收在里面)
import AuthCard from '@/components/AuthCard.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

/**
 * 窄屏(手机)把表单标签改到输入框上方。
 * 原因:固定 label-width=80px 会把每行内容压到 ~199px(375px 屏),
 * 而验证码那一行是「图 130px + 倒计时圆 82px」≈ 226px,会横向溢出被裁掉。
 * 标签位置换成 top 后整行 279px 可用,验证码完整显示。
 */
const isNarrow = useMediaQuery('(max-width: 600px)')

const form = reactive({
  username: '',
  password: '',
  captcha: '',
  captchaUuid: '',
})
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaRef = ref<InstanceType<typeof Captcha> | null>(null)

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' },
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码必须 4 位', trigger: 'blur' },
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
    const resp = await authApi.login({
      username: form.username,
      password: form.password,
      captcha: form.captcha,
      uuid: captchaRef.value?.uuid ?? '',
    })
    userStore.setLogin(resp)
    /*
      浏览须知:每次登录弹一次(状态在内存里,刷新页面不会再弹)。
      在 push 之前调用 —— 弹窗挂在 App.vue 上不会随路由卸载,所以它会跟着
      用户到落地页继续显示;反过来说,如果哪天有人把弹窗挪进登录页,
      这行就得挪到 push 之后(否则跳转的一瞬间弹窗被卸载,用户根本看不见)。
    */
    noticeStore.open()
    ElMessage.success(`欢迎，${resp.username}`)
    // 登录后所有角色统一进商城(/mall) -  后续可在商城内导航到个人中心/后台
    const defaultRedirect = '/mall'
    const redirect = (route.query.redirect as string) || defaultRedirect
    await router.push(redirect)
  } catch {
    // request.ts 拦截器已 toast。
    // 不刷新 captcha —— 后端只在登录成功后才删 Redis 里的验证码,
    // 失败时用户可以用同一张图重试;换图只能靠用户点击或倒计时归零。
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthCard title="TMLibrary" subtitle="后台管理系统">
    <!-- 卡片外观 / 输入框 / 主按钮的样式都在 AuthCard 里,这里只管表单本身
         (form 字段 / rules / 提交逻辑都没动)。

         标签统一放输入框上方:卡片按设计稿收窄到 350px 后,右侧标签会把内容
         压到 350-70(内边距)-80(标签)= 200px,而验证码那一行
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

      <el-form-item label="验证码" prop="captcha">
        <Captcha
          ref="captchaRef"
          v-model="form.captcha"
          type="login"
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
          class="auth-submit"
        >
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <!--
      忘记密码入口 —— 放在卡片页脚插槽里,和注册页的「去登录」同一位置、同一套样式。
      注意它在 el-form **外面**:放表单里的话回车提交会被当成表单的一部分。
    -->
    <template #footer>
      <router-link to="/forgot-password" class="footer-link">忘记密码?</router-link>
    </template>
  </AuthCard>
</template>

<!--
  卡片 / 输入框 / 主按钮 / 页面底的样式都在 src/components/AuthCard.vue。
  这里只挂登录页独有的东西:页脚那行的链接(插槽内容仍属本组件作用域),
  与 Register.vue 的 .footer-link 保持一致。
-->
<style scoped>
.footer-link {
  color: var(--color-accent);
  text-decoration: none;
  font-weight: 500;
}

.footer-link:hover {
  text-decoration: underline;
}
</style>


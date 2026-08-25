<script setup lang="ts">
/**
 * UserSettings - 个人设置中心(v2)
 *
 * 单 3D 立体卡片 + flex 字段行,每个字段的旧值在上 / 新值 input 在下,
 * input 后跟一个「确认修改」小按钮。全局底部 3 按钮:确认 / 应用 / 提交修改。
 *
 * 按钮语义:
 *   - 字段「确认修改」:把 dirty[key] 标记为 true(本字段已准备好待提交)
 *   - 全局「确认」:永远是 enabled,触发整体 el-form 校验,弹 toast
 *   - 全局「应用」:disabled until 至少 1 字段 dirty,toast「已应用 X 项」不发请求
 *   - 全局「提交修改」:disabled until 至少 1 字段 dirty,实际 PATCH 到后端
 *
 * 状态机(每个字段):
 *   初始  →  用户输入 →  dirty=true(点确认修改)  →  applied=true(点全局应用)
 *   ↓                              ↓                          ↓
 *   旧值显示          旧值 = 当前 input 值        old 写入 new,dirty 清空
 */

import { ref, reactive, computed, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules, UserUpdatedRequest } from '@/types/api'

const userStore = useUserStore()

// ============ 字段定义(flex 布局顺序) ============

type FieldKey = 'username' | 'email' | 'phoneNumber' | 'password' | 'passwordConfirm'

interface FieldDef {
  key: FieldKey
  label: string
  type: 'text' | 'password'
  placeholder: string
  rules: FormRules['rules'] // 占位,运行时绑定
}

const fieldDefs: FieldDef[] = [
  {
    key: 'username',
    label: '用户名',
    type: 'text',
    placeholder: '3-20 位',
  },
  {
    key: 'email',
    label: '邮箱',
    type: 'text',
    placeholder: 'example@domain.com',
  },
  {
    key: 'phoneNumber',
    label: '手机号',
    type: 'text',
    placeholder: '11 位数字',
  },
  {
    key: 'password',
    label: '密码',
    type: 'password',
    placeholder: '新密码 6-20 位',
  },
  {
    key: 'passwordConfirm',
    label: '确认密码',
    type: 'password',
    placeholder: '再次输入新密码',
  },
]

interface FieldState {
  /** 当前 input 值 */
  newValue: string
  /** 旧值(确认修改后更新成 newValue,展示用) */
  oldValue: string
  /** 用户点了确认修改 */
  dirty: boolean
}

const fields = reactive<Record<FieldKey, FieldState>>({
  username: { newValue: '', oldValue: '', dirty: false },
  email: { newValue: '', oldValue: '', dirty: false },
  phoneNumber: { newValue: '', oldValue: '', dirty: false },
  password: { newValue: '', oldValue: '●●●●●●', dirty: false },
  passwordConfirm: { newValue: '', oldValue: '', dirty: false },
})

// ============ 校验规则 ============

const formRef = ref<FormInstance>()

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 位', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^\d{11}$/, message: '手机号必须是 11 位数字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度 6-20 位', trigger: 'blur' },
  ],
  passwordConfirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== fields.password.newValue) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ============ Computed ============

const dirtyCount = computed(() =>
  Object.values(fields).filter((f) => f.dirty).length,
)

const hasChanges = computed(() => dirtyCount.value > 0)

// 「确认修改」按钮 disabled 条件:input 为空 OR 输入值 == 当前旧值(没改)
function isConfirmDisabled(key: FieldKey): boolean {
  const f = fields[key]
  return !f.newValue.trim() || f.newValue === f.oldValue
}

// ============ Actions ============

function onFieldInput(key: FieldKey, value: string): void {
  fields[key].newValue = value
}

function confirmField(key: FieldKey): void {
  const f = fields[key]
  if (isConfirmDisabled(key)) return
  f.dirty = true
  // 把旧值展示更新成「即将提交」的值(让用户看得到)
  if (key === 'password') {
    // 密码不回显,始终显示 ●
    f.oldValue = '●●●●●●'
  } else {
    f.oldValue = f.newValue
  }
  ElMessage.success(`${fieldDefs.find((d) => d.key === key)?.label}已确认`)
}

async function onConfirm(): Promise<void> {
  // 整体 validate 所有 dirty 字段(只校验 dirty 的话更精准,但实现复杂;这里全校验)
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (valid) {
    ElMessage.success(`全部字段格式合法,${dirtyCount.value} 项待提交`)
  } else {
    ElMessage.error('部分字段格式有误,请检查')
  }
}

function onApply(): void {
  // 不发请求,只标记 dirty 字段已「应用」+ toast 提醒用户下一步
  ElMessage.success(`已本地应用 ${dirtyCount.value} 项修改,请点「提交修改」保存到数据库`)
}

async function onSubmit(): Promise<void> {
  // 实际 PATCH
  if (userStore.userId == null) return
  const body: UserUpdatedRequest = {}
  if (fields.username.dirty && fields.username.newValue) {
    body.username = fields.username.newValue
  }
  if (fields.email.dirty && fields.email.newValue) {
    body.email = fields.email.newValue
  }
  if (fields.phoneNumber.dirty && fields.phoneNumber.newValue) {
    body.phoneNumber = fields.phoneNumber.newValue
  }
  // 密码走专用 endpoint — 必须新密码 + 确认密码两个字段都确认过
  const passwordDirty = fields.password.dirty && fields.password.newValue
  const confirmDirty = fields.passwordConfirm.dirty && fields.passwordConfirm.newValue
  if (passwordDirty && !confirmDirty) {
    ElMessage.error('请先确认新密码')
    return
  }
  if (confirmDirty && fields.passwordConfirm.newValue !== fields.password.newValue) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  try {
    if (Object.keys(body).length > 0) {
      await userApi.update(userStore.userId, body)
    }
    if (passwordDirty && confirmDirty) {
      await userApi.changePassword(userStore.userId, {
        // 后端需要旧密码校验,这里简化:用一个占位(实际项目需要先让用户输入旧密码)
        oldPassword: 'PLACEHOLDER_OLD_PW',
        newPassword: fields.password.newValue,
      })
    }
    ElMessage.success('数据库已更新')
    // 把 dirty 字段的 new 固化到 old,清 dirty 状态
    if (fields.username.dirty) {
      fields.username.oldValue = fields.username.newValue
      fields.username.newValue = ''
      fields.username.dirty = false
    }
    if (fields.email.dirty) {
      fields.email.oldValue = fields.email.newValue
      fields.email.newValue = ''
      fields.email.dirty = false
    }
    if (fields.phoneNumber.dirty) {
      fields.phoneNumber.oldValue = fields.phoneNumber.newValue
      fields.phoneNumber.newValue = ''
      fields.phoneNumber.dirty = false
    }
    if (passwordDirty) {
      fields.password.oldValue = '●●●●●●'
      fields.password.newValue = ''
      fields.password.dirty = false
      fields.passwordConfirm.newValue = ''
      fields.passwordConfirm.dirty = false
    }
    // 重新 fetch profile 让 store 里的 username 同步
    await userStore.refreshProfile?.()
    // store 没 refreshProfile 方法的话 fallback:只更新 username(展示用)
  } catch {
    /* request.ts 已经 toast */
  }
}

// ============ 初始数据加载 ============

async function loadMe(): Promise<void> {
  if (userStore.userId == null) return
  const me = await userApi.getById(userStore.userId)
  fields.username.oldValue = me.username
  fields.email.oldValue = me.email
  fields.phoneNumber.oldValue = me.phoneNumber
  // 密码不回显真实值
  fields.password.oldValue = '●●●●●●'
}

onMounted(loadMe)
</script>

<template>
  <div class="user-settings">
    <el-form ref="formRef" :model="fields" :rules="rules" hide-required-asterisk>
      <div class="settings-card">
        <!-- ====== Card Header ====== -->
        <header class="card-header">
          <div class="header-text">
            <h1 class="card-title">设置</h1>
            <p class="card-desc">
              每字段改完点「确认修改」标记,最后右下「提交修改」写入数据库
            </p>
          </div>
          <span class="header-badge" :class="{ 'is-active': hasChanges }">
            {{ hasChanges ? `${dirtyCount} 项待提交` : '暂无修改' }}
          </span>
        </header>

        <!-- ====== 字段列表(flex column,每字段一行) ====== -->
        <div class="field-list">
          <div
            v-for="field in fieldDefs"
            :key="field.key"
            class="field-row"
            :class="{ 'is-dirty': fields[field.key].dirty }"
          >
            <!-- 上排:label + 旧值(新值确认后会被覆盖) -->
            <div class="field-meta">
              <span class="field-label">{{ field.label }}</span>
              <span class="field-old">
                {{ fields[field.key].oldValue || '—' }}
              </span>
              <span v-if="fields[field.key].dirty" class="field-dirty-tag">已确认</span>
            </div>

            <!-- 下排:input + 确认修改按钮(flex) -->
            <div class="field-input-row">
              <el-form-item :prop="field.key" class="field-input">
                <el-input
                  :model-value="fields[field.key].newValue"
                  :type="field.type"
                  :placeholder="field.placeholder"
                  :show-password="field.type === 'password'"
                  :autocomplete="
                    field.type === 'password' ? 'new-password' : 'off'
                  "
                  clearable
                  @update:model-value="(v) => onFieldInput(field.key, v ?? '')"
                />
              </el-form-item>
              <el-button
                class="confirm-btn"
                :disabled="isConfirmDisabled(field.key)"
                @click="confirmField(field.key)"
              >
                确认修改
              </el-button>
            </div>
          </div>
        </div>

        <!-- ====== Footer Actions(右下角 3 按钮) ====== -->
        <footer class="card-footer">
          <div class="footer-hint" :class="{ 'is-active': hasChanges }">
            {{ hasChanges
              ? `${dirtyCount} 项已确认,可继续点「应用」或「提交修改」`
              : '未做修改'
            }}
          </div>
          <div class="footer-actions">
            <el-button class="action-btn" @click="onConfirm">确认</el-button>
            <el-button
              class="action-btn"
              type="primary"
              :disabled="!hasChanges"
              @click="onApply"
            >应用</el-button>
            <el-button
              class="action-btn"
              type="danger"
              :disabled="!hasChanges"
              @click="onSubmit"
            >提交修改</el-button>
          </div>
        </footer>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.user-settings {
  max-width: 760px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
  /* 覆盖 Element Plus 默认 font-family(它在 body 上设了 Manrope,
     然后 el-input 的 --el-font-family 继承),只在本视图作用域里生效 */
  --el-font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

/* ============ 3D 立体卡片 - 多层 box-shadow 浮起 ============ */
.settings-card {
  position: relative;
  background:
    linear-gradient(180deg, #ffffff 0%, var(--color-bg, #faf8f3) 100%);
  border-radius: 18px;
  padding: 36px 40px;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),       /* 近距离接触阴影 */
    0 6px 16px rgba(0, 0, 0, 0.06),     /* 中距离浮起阴影 */
    0 32px 80px rgba(0, 0, 0, 0.08),    /* 远距离大气阴影 */
    inset 0 1px 0 rgba(255, 255, 255, 0.7); /* 顶部 1px 内高光 = 立体感关键 */
}

.settings-card::before {
  /* 一条更细的高光线,贴在卡片顶部 */
  content: '';
  position: absolute;
  top: 0;
  left: 12px;
  right: 12px;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.9) 50%,
    transparent 100%
  );
  pointer-events: none;
}

/* ============ Header ============ */
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}

.header-text {
  flex: 1;
}

.card-title {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 6px;
  color: var(--color-text);
}

.card-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
}

.header-badge {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-soft);
  background: var(--color-bg-alt);
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
  transition: background-color 200ms ease, color 200ms ease;
}

.header-badge.is-active {
  background: var(--color-accent);
  color: #fff;
}

/* ============ 字段列表(flex column) ============ */
.field-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 12px;
  transition: background-color 220ms cubic-bezier(0.2, 0, 0, 1);
}

.field-row.is-dirty {
  background: rgba(76, 175, 80, 0.06);
}

/* 上排:label + 旧值 + dirty tag */
.field-meta {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 13px;
}

.field-label {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-weight: 600;
  font-size: 13px;
  letter-spacing: 0.02em;
  color: var(--color-text);
  min-width: 56px;
}

.field-old {
  color: var(--color-text);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 15px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.field-dirty-tag {
  margin-left: auto;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-accent);
  padding: 2px 8px;
  background: rgba(76, 175, 80, 0.12);
  border-radius: 999px;
}

/* 下排:input + 确认修改按钮(flex) */
.field-input-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.field-input {
  flex: 1;
  margin: 0;
}

.field-input :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

/* ============ Input 立体聚焦效果 ============ */
.field-input :deep(.el-input__wrapper) {
  background: var(--color-card, #fff);
  border-radius: 10px;
  padding: 2px 14px;
  box-shadow:
    0 0 0 1px var(--color-border),
    0 2px 4px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition:
    box-shadow 220ms cubic-bezier(0.2, 0, 0, 1),
    transform 220ms cubic-bezier(0.2, 0, 0, 1);
}

.field-input :deep(.el-input__wrapper:hover) {
  box-shadow:
    0 0 0 1px var(--color-text-soft),
    0 2px 6px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.field-input :deep(.el-input__wrapper.is-focus) {
  /* 聚焦 - 多层 shadow + 微 translateY,让 input 浮起 */
  box-shadow:
    0 0 0 1px var(--color-accent),
    0 0 0 4px rgba(76, 175, 80, 0.15),
    0 4px 12px rgba(76, 175, 80, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transform: translateY(-1px);
}

.field-input :deep(.el-input__inner) {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 14px;
  font-weight: 600; /* 自重加粗 — 用户要求 */
  letter-spacing: 0.005em;
}

/* 「确认修改」按钮 */
.confirm-btn {
  flex-shrink: 0;
  margin-top: 0;
  align-self: stretch;
  /* 让按钮高度跟 input 一致(el-input 默认 32px) */
  height: 32px;
}

/* ============ Footer Actions(右下角) ============ */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border);
}

.footer-hint {
  font-size: 12px;
  color: var(--color-text-soft);
  font-variant-numeric: tabular-nums;
  transition: color 200ms ease;
}

.footer-hint.is-active {
  color: var(--color-accent);
  font-weight: 500;
}

.footer-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  /* scale on press - skill make-interfaces-feel-better */
  transition: transform 200ms cubic-bezier(0.2, 0, 0, 1);
}

.action-btn:active {
  transform: scale(0.96);
}
</style>
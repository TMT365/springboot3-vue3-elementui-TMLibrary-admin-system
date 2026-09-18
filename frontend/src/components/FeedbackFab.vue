<script setup lang="ts">
/**
 * 全局反馈入口 —— 右下角悬浮按钮 + 提交抽屉。
 *
 * <h2>为什么挂全局</h2>
 * <p>用户想反馈时,问题发生在哪一页是不确定的(商城?订单页?后台?)。
 * 挂在 App.vue 上,任何路由都能就地打开,不用先"找到反馈入口再描述问题"。</p>
 *
 * <h2>为什么用抽屉(ElDrawer)而不是弹窗</h2>
 * <p>反馈要填三个字段(分类/标题/描述),弹窗在小屏上要把内容压到很窄;
 * 抽屉从右侧滑出,手机上占满宽度、桌面上是 480px 的侧栏,两种尺寸都自然。</p>
 *
 * <h2>登录态</h2>
 * <p>未登录时按钮仍然显示(让用户知道有这个功能),点击引导去登录并带上
 * 回跳地址 —— 比"直接隐藏,用户以为没这功能"好。</p>
 */

import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { feedbackApi } from '@/api/feedback'
import { FEEDBACK_CATEGORY } from '@/composables/useFeedback'
import type { FeedbackCategory } from '@/types/api'
import type { FormInstance, FormRules } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const visible = ref<boolean>(false)
const submitting = ref<boolean>(false)
const formRef = ref<FormInstance>()

const form = reactive({
  category: 'BUG' as FeedbackCategory,
  title: '',
  body: '',
})

const rules: FormRules = {
  category: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
  title: [
    { required: true, message: '请用一句话概括问题', trigger: 'blur' },
    // 与后端 @Size(max = 120) 对齐 —— 前端先挡一道
    { max: 120, message: '标题不能超过 120 个字符', trigger: 'blur' },
  ],
  body: [
    { required: true, message: '请描述具体情况', trigger: 'blur' },
    { max: 5000, message: '描述不能超过 5000 个字符', trigger: 'blur' },
  ],
}

/** 分类下拉的选项 —— 直接来自共享元数据,不在模板里再抄一遍 */
const categoryOptions = computed(() =>
  (Object.keys(FEEDBACK_CATEGORY) as FeedbackCategory[]).map((key) => ({
    value: key,
    ...FEEDBACK_CATEGORY[key],
  })),
)

/** 登录页要带上回跳,登录完回到当前页(而不是被踢回首页) */
const loginRedirect = computed(() => route.fullPath)

function open(): void {
  if (!userStore.isAuthenticated) {
    ElMessage.info('登录后才能提交反馈')
    void router.push({ path: '/login', query: { redirect: loginRedirect.value } })
    return
  }
  visible.value = true
}

/** 关闭时清空表单 —— 否则上次填了一半的内容会留到下一次,看着像 bug */
watch(visible, (open) => {
  if (!open) {
    formRef.value?.resetFields()
    form.category = 'BUG'
  }
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const id = await feedbackApi.create({
      category: form.category,
      title: form.title.trim(),
      body: form.body.trim(),
    })
    ElMessage.success('反馈已提交,感谢!')
    visible.value = false
    // 直接跳到详情页 —— 用户马上能看到自己的反馈 + 后续回复都在这里
    await router.push(`/feedback/${id}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!--
    悬浮按钮。aria-label 是给读屏用户的 —— 图标按钮没有文字,不给标签
    屏幕阅读器只会念出 "button"。
  -->
  <button
    class="feedback-fab"
    type="button"
    aria-label="问题反馈"
    title="问题反馈"
    @click="open"
  >
    <el-icon class="fab-icon"><ChatDotRound /></el-icon>
    <span class="fab-text">反馈</span>
  </button>

  <el-drawer
    v-model="visible"
    title="问题反馈"
    direction="rtl"
    size="min(480px, 100vw)"
    :close-on-click-modal="false"
    class="feedback-drawer"
  >
    <p class="drawer-hint">
      遇到问题或者有好点子?写在这里,管理员和老板都能看到。
    </p>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="反馈类型" prop="category">
        <el-select v-model="form.category" class="full-width">
          <el-option
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          >
            <el-icon :style="{ color: opt.color }"><component :is="opt.icon" /></el-icon>
            <span class="opt-label">{{ opt.label }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="一句话概括" prop="title">
        <el-input
          v-model="form.title"
          placeholder="例如:搜索「设计」搜不到设计模式"
          maxlength="120"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="详细描述" prop="body">
        <el-input
          v-model="form.body"
          type="textarea"
          :rows="7"
          placeholder="当时在做什么?期望是什么?实际是什么?&#10;有复现步骤就更好定位了。"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交反馈</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
/* ============================================================
 * 悬浮按钮 —— 右下角固定,栈里的第 1 层
 * z-index 1000:高过页面内容,但**低于** Element Plus 的抽屉/弹窗
 * (EP 的 overlay 是 2000+),否则打开抽屉时按钮会浮在遮罩上面
 *
 * 位置/尺寸一律走 theme.css 的 --fab-* token —— 同角的 BackToTop
 * 靠 --fab-bottom-2 叠在它上方,两边共用一套值才不会错位。
 * ============================================================ */
.feedback-fab {
  position: fixed;
  right: var(--fab-edge);
  bottom: var(--fab-edge);
  z-index: 1000;

  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  /* 高度锁死成 --fab-size,和 BackToTop 的圆一样高 —— 竖排两个件才能对齐 */
  height: var(--fab-size);
  padding: 0 20px;

  background: var(--color-accent);
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 999px;
  cursor: pointer;

  box-shadow:
    0 6px 16px rgba(76, 175, 80, 0.32),
    0 2px 6px rgba(17, 25, 40, 0.16);
  transition:
    transform 220ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 220ms cubic-bezier(0.2, 0, 0, 1);
}

.feedback-fab:hover {
  transform: translateY(-2px);
  box-shadow:
    0 10px 24px rgba(76, 175, 80, 0.38),
    0 4px 10px rgba(17, 25, 40, 0.18);
}

.feedback-fab:active {
  transform: translateY(0) scale(0.97);
}

.fab-icon {
  font-size: 17px;
}

/* 暗色下按钮和背景对比够(accent 是亮绿),不用改色;
   只把阴影换成黑底,绿光晕在深色上会显得脏 */
:root[data-theme='dark'] .feedback-fab {
  box-shadow:
    0 6px 16px rgba(0, 0, 0, 0.5),
    0 2px 6px rgba(0, 0, 0, 0.35);
}

:root[data-theme='dark'] .feedback-fab:hover {
  box-shadow:
    0 10px 24px rgba(0, 0, 0, 0.58),
    0 4px 10px rgba(0, 0, 0, 0.4);
}

/* ============================================================
 * 手机(≤600):只留图标,收成和 BackToTop 一样的圆
 * —— 文字版会挡住底部操作栏
 * ============================================================ */
@media (max-width: 600px) {
  .feedback-fab {
    width: var(--fab-size);
    height: var(--fab-size);
    padding: 0;
    border-radius: 50%;
  }

  .fab-text {
    display: none;
  }

  .fab-icon {
    font-size: 20px;
  }
}

/* 抽屉内的排版 */
.drawer-hint {
  margin: 0 0 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.full-width {
  width: 100%;
}

.opt-label {
  margin-left: 8px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

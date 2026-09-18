<script setup lang="ts">
/**
 * 反馈详情 —— 工单正文 + 回复时间线 + 追述框。
 *
 * 权限(后端已强制,前端只做展示层配合):
 *   - 提交人本人 / ADMIN / BOSS 可以看
 *   - isInternal=1 的回复普通用户拿不到(后端过滤)
 *   - 只有 ADMIN/BOSS 能改状态、能发内部备注
 *
 * 气泡设计:
 *   - 自己发的 → 右侧、accent 色
 *   - 别人发的 → 左侧、灰底,带身份标签(管理员/老板)
 *   - 内部备注 → 两侧都不是,单独一条黄底横幅(只管理员可见,视觉上要"跳出对话流")
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { feedbackApi } from '@/api/feedback'
import { useUserStore } from '@/stores/user'
import { categoryMeta, priorityMeta, roleLabel, splitDateTime, statusMeta } from '@/composables/useFeedback'
import type { FeedbackStatus, FeedbackView } from '@/types/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const detail = ref<FeedbackView | null>(null)
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

const replyBody = ref<string>('')
const replyInternal = ref<boolean>(false)
const submitting = ref<boolean>(false)
const updatingStatus = ref<boolean>(false)

/** 路由参数是 string,统一转数字 */
const feedbackId = computed<number>(() => Number(route.params.id))

const isAdmin = computed<boolean>(() => userStore.isAdmin)

async function fetchDetail(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    detail.value = await feedbackApi.detail(feedbackId.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function submitReply(): Promise<void> {
  const body = replyBody.value.trim()
  if (!body) {
    ElMessage.warning('请先写点内容')
    return
  }
  submitting.value = true
  try {
    await feedbackApi.reply(feedbackId.value, {
      body,
      // 非管理员传这个字段无意义(后端会强制当 false),但传了对齐语义
      isInternal: isAdmin.value ? replyInternal.value : false,
    })
    replyBody.value = ''
    replyInternal.value = false
    ElMessage.success('已发送')
    await fetchDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(status: FeedbackStatus): Promise<void> {
  updatingStatus.value = true
  try {
    await feedbackApi.changeStatus(feedbackId.value, { status })
    ElMessage.success('状态已更新')
    await fetchDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    updatingStatus.value = false
  }
}

async function changePriority(priority: number): Promise<void> {
  updatingStatus.value = true
  try {
    await feedbackApi.changeStatus(feedbackId.value, { priority: priority as 0 | 1 | 2 | 3 })
    ElMessage.success('优先级已更新')
    await fetchDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    updatingStatus.value = false
  }
}

/** 是不是"我发的" —— 决定气泡左右。用 userId 而不是 username(用户名可变) */
function isMine(userId: number): boolean {
  return userId === userStore.userId
}

onMounted(fetchDetail)
</script>

<template>
  <div class="fb-detail">
    <header class="page-head">
      <el-button link @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </header>

    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
      <el-button link type="primary" @click="fetchDetail">重试</el-button>
    </div>

    <el-skeleton v-else-if="loading" :rows="8" animated />

    <template v-else-if="detail">
      <!-- ============ 工单正文 ============ -->
      <article class="ticket">
        <header class="ticket-head">
          <div class="ticket-meta">
            <span class="ticket-cat" :style="{ color: categoryMeta(detail.category).color }">
              <el-icon><component :is="categoryMeta(detail.category).icon" /></el-icon>
              {{ categoryMeta(detail.category).label }}
            </span>
            <span class="ticket-id">#{{ detail.id }}</span>
          </div>
          <el-tag :type="statusMeta(detail.status).type" effect="light">
            {{ statusMeta(detail.status).label }}
          </el-tag>
        </header>

        <h1 class="ticket-title">{{ detail.title }}</h1>

        <p class="ticket-byline">
          <span>{{ detail.username || '匿名' }}</span>
          <span class="sep">·</span>
          <span>{{ splitDateTime(detail.createdTime)?.date }} {{ splitDateTime(detail.createdTime)?.time }}</span>
          <template v-if="detail.resolvedTime">
            <span class="sep">·</span>
            <span class="is-resolved">
              解决于 {{ splitDateTime(detail.resolvedTime)?.date }}
            </span>
          </template>
        </p>

        <div class="ticket-body">{{ detail.body }}</div>

        <!-- 管理员工具条 -->
        <div v-if="isAdmin" class="admin-bar">
          <span class="bar-label">管理员操作</span>
          <el-select
            :model-value="detail.status"
            :loading="updatingStatus"
            size="small"
            class="bar-select"
            @change="(v: FeedbackStatus) => changeStatus(v)"
          >
            <el-option :value="0" label="待处理" />
            <el-option :value="1" label="处理中" />
            <el-option :value="2" label="已解决" />
            <el-option :value="3" label="已关闭" />
          </el-select>
          <el-select
            :model-value="detail.priority"
            :loading="updatingStatus"
            size="small"
            class="bar-select"
            @change="(v: number) => changePriority(v)"
          >
            <el-option :value="0" label="优先级:低" />
            <el-option :value="1" label="优先级:普通" />
            <el-option :value="2" label="优先级:高" />
            <el-option :value="3" label="优先级:紧急" />
          </el-select>
        </div>
      </article>

      <!-- ============ 回复时间线 ============ -->
      <section class="thread">
        <h2 class="thread-title">
          回复
          <span class="thread-count">{{ detail.replies.length }}</span>
        </h2>

        <el-empty
          v-if="detail.replies.length === 0"
          description="还没有回复"
          :image-size="80"
        />

        <div v-else class="bubbles">
          <div
            v-for="reply in detail.replies"
            :key="reply.id"
            class="bubble-row"
            :class="{ 'is-mine': isMine(reply.userId) }"
          >
            <!-- 内部备注:单独一条黄底,不参与左右气泡(它不属于对话,属于旁注) -->
            <div v-if="reply.isInternal === 1" class="internal-note">
              <el-icon><Lock /></el-icon>
              <div class="note-content">
                <p class="note-head">
                  内部备注 · {{ reply.username || '管理员' }} ·
                  {{ splitDateTime(reply.createdTime)?.date }}
                </p>
                <p class="note-body">{{ reply.body }}</p>
              </div>
            </div>

            <template v-else>
              <div class="bubble">
                <div class="bubble-head">
                  <span class="bubble-who">{{ roleLabel(reply.role) }}</span>
                  <span class="bubble-time">
                    {{ splitDateTime(reply.createdTime)?.date }}
                    {{ splitDateTime(reply.createdTime)?.time }}
                  </span>
                </div>
                <p class="bubble-body">{{ reply.body }}</p>
              </div>
            </template>
          </div>
        </div>
      </section>

      <!-- ============ 追述框 ============ -->
      <section class="composer">
        <el-input
          v-model="replyBody"
          type="textarea"
          :rows="4"
          :placeholder="isAdmin ? '回复用户,或写一条内部备注…' : '补充说明(例如:我换了个浏览器试还是一样)'"
          maxlength="5000"
          show-word-limit
        />
        <div class="composer-actions">
          <el-checkbox v-if="isAdmin" v-model="replyInternal" class="internal-check">
            仅管理员可见(内部备注)
          </el-checkbox>
          <span v-else class="composer-spacer" />
          <el-button type="primary" :loading="submitting" @click="submitReply">
            发送
          </el-button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.fb-detail {
  max-width: 860px;
  margin: 0 auto;
  padding: 6px;
  -webkit-font-smoothing: antialiased;
}

.page-head {
  margin-bottom: 14px;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
  border-radius: 10px;
  font-size: 14px;
}

/* ============================================================
 * 工单正文
 * ============================================================ */
.ticket {
  padding: 22px 26px 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 1px 3px var(--color-shadow);
}

.ticket-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.ticket-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ticket-cat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
}

.ticket-id {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 12.5px;
  color: var(--color-text-soft);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.ticket-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.3;
  margin: 0 0 8px;
  color: var(--color-text);
  text-wrap: balance;
}

.ticket-byline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--color-text-soft);
  font-variant-numeric: tabular-nums;
}

.ticket-byline .sep {
  opacity: 0.5;
}

.ticket-byline .is-resolved {
  color: var(--color-accent);
  font-weight: 500;
}

/* 正文保留换行 —— 用户写的多行描述不能被压成一段 */
.ticket-body {
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.admin-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--color-border);
}

.bar-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.bar-select {
  width: 140px;
}

/* ============================================================
 * 回复流
 * ============================================================ */
.thread {
  margin-top: 26px;
}

.thread-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  margin: 0 0 16px;
}

.thread-count {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-bg-alt);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.bubbles {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 自己发的靠右,别人的靠左 */
.bubble-row {
  display: flex;
  justify-content: flex-start;
}

.bubble-row.is-mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 78%;
  padding: 11px 15px;
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border);
  border-radius: 14px 14px 14px 4px;
}

.bubble-row.is-mine .bubble {
  background: rgba(76, 175, 80, 0.1);
  border-color: rgba(76, 175, 80, 0.28);
  border-radius: 14px 14px 4px 14px;
}

.bubble-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 5px;
}

.bubble-who {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text);
}

.bubble-row.is-mine .bubble-who {
  color: var(--color-accent);
}

.bubble-time {
  font-size: 11px;
  color: var(--color-text-soft);
  font-variant-numeric: tabular-nums;
}

.bubble-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 内部备注 —— 黄底横幅,明确"这不是对话的一部分" */
.internal-note {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 12px 15px;
  background: rgba(230, 162, 60, 0.1);
  border: 1px dashed rgba(230, 162, 60, 0.45);
  border-radius: 12px;
  color: #b88230;
}

.internal-note .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 15px;
}

.note-head {
  margin: 0 0 4px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.note-body {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-text);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* ============================================================
 * 追述框
 * ============================================================ */
.composer {
  margin-top: 26px;
  padding: 18px 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: 0 1px 3px var(--color-shadow);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.internal-check {
  font-size: 13px;
}

.composer-spacer {
  flex: 1;
}

/* ============================================================
 * 手机(≤600)
 * ============================================================ */
@media (max-width: 600px) {
  .ticket {
    padding: 18px 16px 16px;
    border-radius: 14px;
  }

  .ticket-title {
    font-size: 21px;
  }

  /* 气泡占满宽度:手机上 78% 的宽度放不下几个字 */
  .bubble {
    max-width: 90%;
  }

  .admin-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .bar-select {
    width: 100%;
  }
}
</style>

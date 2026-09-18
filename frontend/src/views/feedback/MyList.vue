<script setup lang="ts">
/**
 * 我的反馈 —— 卡片流。
 *
 * 数据源:GET /api/feedbacks/mine(后端只返回当前用户提的)
 * 卡片上不显示正文摘要,只显示标题 + 类型 + 状态 + 回复数 —— 列表是索引,
 * 正文在详情页看。
 */

import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { feedbackApi } from '@/api/feedback'
import { categoryMeta, relativeTime, statusMeta } from '@/composables/useFeedback'
import Pager from '@/components/Pager.vue'
import type { FeedbackSummary } from '@/types/api'

const router = useRouter()

const items = ref<FeedbackSummary[]>([])
const total = ref<number>(0)
const page = ref<number>(1)
const size = ref<number>(20)
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

async function fetchList(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await feedbackApi.myList(page.value, size.value)
    items.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 点卡片进详情 —— 用 row 的 id 组路由 */
function goDetail(id: number): void {
  void router.push(`/feedback/${id}`)
}

onMounted(fetchList)
</script>

<template>
  <div class="feedback-list">
    <header class="page-head">
      <div>
        <h1 class="page-title">我的反馈</h1>
        <p class="page-sub">
          共 <strong>{{ total }}</strong> 条 —— 点右下角「反馈」可以随时提新的
        </p>
      </div>
      <el-button :loading="loading" @click="fetchList">刷新</el-button>
    </header>

    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <el-skeleton v-else-if="loading && items.length === 0" :rows="5" animated />

    <el-empty v-else-if="items.length === 0" description="还没提过反馈">
      <el-button type="primary" @click="router.push('/mall')">去逛逛商城</el-button>
    </el-empty>

    <div v-else class="card-flow">
      <article
        v-for="item in items"
        :key="item.id"
        class="fb-card"
        :class="`is-status-${item.status}`"
        role="button"
        tabindex="0"
        @click="goDetail(item.id)"
        @keyup.enter="goDetail(item.id)"
      >
        <!-- 左侧状态色轨:一列卡片扫下来,状态不用读标签 -->
        <div class="fb-head">
          <span class="fb-cat" :style="{ color: categoryMeta(item.category).color }">
            <el-icon><component :is="categoryMeta(item.category).icon" /></el-icon>
            {{ categoryMeta(item.category).label }}
          </span>
          <el-tag :type="statusMeta(item.status).type" size="small" effect="light">
            {{ statusMeta(item.status).label }}
          </el-tag>
        </div>

        <h3 class="fb-title">{{ item.title }}</h3>

        <footer class="fb-foot">
          <span class="fb-time">{{ relativeTime(item.updatedTime) }}</span>
          <span v-if="item.replyCount > 0" class="fb-reply-count">
            <el-icon><ChatLineSquare /></el-icon>
            {{ item.replyCount }} 条回复
          </span>
          <span v-else class="fb-reply-count is-none">等待回复</span>
        </footer>
      </article>
    </div>

    <Pager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      @change="fetchList"
    />
  </div>
</template>

<style scoped>
.feedback-list {
  max-width: 860px;
  margin: 0 auto;
  padding: 6px;
  -webkit-font-smoothing: antialiased;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.page-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 4px;
  color: var(--color-text);
  text-wrap: balance;
}

.page-sub {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-muted);
}

.page-sub strong {
  color: var(--color-accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  margin-bottom: 16px;
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
  border-radius: 10px;
  font-size: 14px;
}

/* ============================================================
 * 卡片流
 * ============================================================ */
.card-flow {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fb-card {
  --rail: var(--color-border);
  position: relative;
  padding: 16px 20px 14px 23px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  cursor: pointer;
  overflow: hidden;
  box-shadow:
    0 1px 3px var(--color-shadow),
    0 0 0 1px transparent;
  transition:
    transform 220ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 220ms cubic-bezier(0.2, 0, 0, 1),
    border-color 220ms ease;
}

/* 状态色轨 —— 用伪元素而不是 border-left:圆角卡片上 border 会被圆角切歪 */
.fb-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--rail);
}

.fb-card.is-status-0 { --rail: #909399; }
.fb-card.is-status-1 { --rail: #e6a23c; }
.fb-card.is-status-2 { --rail: var(--color-accent); }
.fb-card.is-status-3 { --rail: #b0b3b8; }

@media (hover: hover) {
  .fb-card:hover {
    transform: translateY(-2px);
    border-color: rgba(76, 175, 80, 0.3);
    box-shadow:
      0 8px 20px var(--color-shadow),
      0 2px 6px rgba(17, 25, 40, 0.06);
  }
}

.fb-card:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent);
}

.fb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.fb-cat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 600;
}

.fb-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.4;
  margin: 0 0 10px;
  color: var(--color-text);
}

.fb-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12.5px;
  color: var(--color-text-soft);
}

.fb-time {
  font-variant-numeric: tabular-nums;
}

.fb-reply-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-accent);
  font-weight: 500;
}

.fb-reply-count.is-none {
  color: var(--color-text-soft);
  font-weight: 400;
}

@media (max-width: 600px) {
  .page-title {
    font-size: 24px;
  }

  .fb-card {
    padding: 14px 15px 12px 18px;
    border-radius: 12px;
  }

  .fb-title {
    font-size: 15px;
  }
}
</style>

<script setup lang="ts">
/**
 * 反馈管理(ADMIN / BOSS)—— 全部用户的反馈 + 状态/分类筛选。
 *
 * 和 MyList 的差异:
 *   - 数据源 GET /api/feedbacks/all(不是 /mine)
 *   - 多一行筛选器(状态 / 分类)
 *   - 卡片上多显示提交人(管理员要知道是谁提的)
 *
 * 卡片样式和 MyList 保持一致 —— 两页是同一件事的两个视角,
 * 样式漂移会让用户以为进了不同的系统。
 */

import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { feedbackApi } from '@/api/feedback'
import { categoryMeta, priorityMeta, relativeTime, statusMeta } from '@/composables/useFeedback'
import Pager from '@/components/Pager.vue'
import type { FeedbackSummary } from '@/types/api'

const router = useRouter()

const items = ref<FeedbackSummary[]>([])
const total = ref<number>(0)
const page = ref<number>(1)
const size = ref<number>(20)
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

/** 筛选条件 —— undefined 表示"不限" */
const filterStatus = ref<number | undefined>(undefined)
const filterCategory = ref<string | undefined>(undefined)

async function fetchList(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await feedbackApi.allList({
      status: filterStatus.value,
      category: filterCategory.value,
      page: page.value,
      size: size.value,
    })
    items.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 改筛选条件后回到第一页 —— 否则在第 5 页筛选出 3 条会看到空白 */
function applyFilter(): void {
  page.value = 1
  void fetchList()
}

function resetFilter(): void {
  filterStatus.value = undefined
  filterCategory.value = undefined
  applyFilter()
}

function goDetail(id: number): void {
  void router.push(`/feedback/${id}`)
}

onMounted(fetchList)
</script>

<template>
  <div class="feedback-admin">
    <header class="page-head">
      <div>
        <h1 class="page-title">反馈管理</h1>
        <p class="page-sub">
          共 <strong>{{ total }}</strong> 条用户反馈
        </p>
      </div>
      <el-button :loading="loading" @click="fetchList">刷新</el-button>
    </header>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-select
        v-model="filterStatus"
        placeholder="全部状态"
        clearable
        class="filter-item"
        @change="applyFilter"
      >
        <el-option :value="0" label="待处理" />
        <el-option :value="1" label="处理中" />
        <el-option :value="2" label="已解决" />
        <el-option :value="3" label="已关闭" />
      </el-select>

      <el-select
        v-model="filterCategory"
        placeholder="全部分类"
        clearable
        class="filter-item"
        @change="applyFilter"
      >
        <el-option value="BUG" label="缺陷" />
        <el-option value="FEATURE" label="建议" />
        <el-option value="QUESTION" label="疑问" />
        <el-option value="OTHER" label="其他" />
      </el-select>

      <el-button v-if="filterStatus !== undefined || filterCategory" link @click="resetFilter">
        清除筛选
      </el-button>
    </div>

    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <el-skeleton v-else-if="loading && items.length === 0" :rows="5" animated />

    <el-empty v-else-if="items.length === 0" description="没有符合条件的反馈" />

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
        <div class="fb-head">
          <span class="fb-cat" :style="{ color: categoryMeta(item.category).color }">
            <el-icon><component :is="categoryMeta(item.category).icon" /></el-icon>
            {{ categoryMeta(item.category).label }}
          </span>
          <div class="fb-head-right">
            <!-- 高优先级才显示徽章 —— 全显示等于没显示 -->
            <el-tag
              v-if="item.priority >= 2"
              :type="priorityMeta(item.priority).type"
              size="small"
              effect="dark"
            >
              {{ priorityMeta(item.priority).label }}
            </el-tag>
            <el-tag :type="statusMeta(item.status).type" size="small" effect="light">
              {{ statusMeta(item.status).label }}
            </el-tag>
          </div>
        </div>

        <h3 class="fb-title">{{ item.title }}</h3>

        <footer class="fb-foot">
          <span class="fb-user">
            <el-icon><User /></el-icon>
            {{ item.username || '匿名' }}
          </span>
          <span class="fb-time">{{ relativeTime(item.updatedTime) }}</span>
          <span v-if="item.replyCount > 0" class="fb-reply-count">
            <el-icon><ChatLineSquare /></el-icon>
            {{ item.replyCount }}
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
.feedback-admin {
  max-width: 960px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
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

/* 筛选条 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.filter-item {
  width: 160px;
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
 * 卡片 —— 与 MyList 同一套视觉
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
  box-shadow: 0 1px 3px var(--color-shadow);
  transition:
    transform 220ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 220ms cubic-bezier(0.2, 0, 0, 1),
    border-color 220ms ease;
}

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

.fb-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
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

.fb-user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-muted);
  font-weight: 500;
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

  .filter-item {
    width: 100%;
  }
}
</style>

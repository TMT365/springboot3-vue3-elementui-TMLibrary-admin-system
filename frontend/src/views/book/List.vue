<script setup lang="ts">
/**
 * book/List  -  图书列表(P5 接通版)
 *
 * 端点:GET /api/books/list?page=&size=
 * 后续可加:SearchBar 过滤 / 多条件搜索 / 行内编辑
 */

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { bookApi } from '@/api/book'
import { formatPrice, formatDate } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import type { BookDto, PageResult } from '@/types/api'

const router = useRouter()
const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<BookDto[]>([])
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

async function fetchBooks(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result: PageResult<BookDto> = await bookApi.list({ page: page.value, size: size.value })
    items.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function goCreate(): void {
  router.replace('/admin/books/new')
}

function goEdit(id: number): void {
  router.replace(`/admin/books/${id}/edit`)
}

onMounted(fetchBooks)
</script>

<template>
  <div class="book-list">
    <!-- 页面标题 -->
    <header class="page-head">
      <div>
        <h1 class="page-title">图书列表</h1>
        <p class="page-sub">
          共 <strong>{{ total }}</strong> 条记录
        </p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="fetchBooks">刷新</el-button>
        <el-button type="primary" @click="goCreate">
          <el-icon><Plus /></el-icon>
          <span>新增图书</span>
        </el-button>
      </div>
    </header>

    <!-- 错误状态 -->
    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-skeleton v-if="loading && items.length === 0" :rows="6" animated />

      <el-table
        v-else
        :data="items"
        empty-text="暂无图书数据,点右上角新增"
        class="premium-table"
        :row-class-name="() => 'premium-row'"
      >
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="title" label="标题" min-width="220">
          <template #default="{ row }">
            <span class="cell-title" :title="row.title">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="140" />
        <el-table-column prop="isbn" label="ISBN" width="170">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.isbn }}</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="cell-price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="90" align="right">
          <template #default="{ row }">
            <span
              class="cell-stock"
              :class="{ 'is-low': (row.stockQuantity ?? 0) <= 5 }"
            >
              {{ row.stockQuantity ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="出版日期" width="120">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDate(row.publishedDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goEdit(row.id)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" disabled>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <Pager
      v-model:page="page"
      v-model:size="size"
      :total="total"
      @change="fetchBooks"
    />
  </div>
</template>

<style scoped>
.book-list {
  max-width: 1280px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

/* ============================================================
 * 页面标题  -  跟其他 admin 页面统一
 * ============================================================ */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 4px;
  color: var(--color-text);  text-wrap: balance;
}

.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.page-sub strong {
  color: var(--color-accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.08);
  color: #f56c6c;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 14px;
}

/* ============================================================
 * 表格外壳  -  多层 shadow
 * ============================================================ */
.table-card {
  background: var(--color-card);
  border-radius: 14px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  overflow: hidden;
}

/* ============================================================
 * Premium table  -  圆角 + sticky head + tabular-nums + hover
 * ============================================================ */
.premium-table {
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-bg-alt);
  --el-table-row-hover-bg-color: var(--color-bg-alt);
  border-radius: 14px;
  overflow: hidden;
}

.premium-table :deep(.el-table__inner-wrapper) {
  border-radius: 14px;
}

.premium-table :deep(.el-table__header-wrapper) th {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  background: var(--color-bg-alt);
}

.premium-table :deep(.el-table__row) td {
  font-size: 14px;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.premium-table :deep(.el-table__row:last-child td) {
  border-bottom: none;
}

.premium-table :deep(.el-table__row:hover) td {
  background: var(--color-bg-alt);
}

/* 单元格样式 */
.cell-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);}

.cell-mono {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 13px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.cell-muted {
  font-size: 13px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.cell-price {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.cell-stock {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.cell-stock.is-low {
  color: #f56c6c;
}

/* ============================================================
 * 响应式  -  小屏:表格横向滚动
 * ============================================================ */
@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .table-card {
    border-radius: 12px;
    overflow-x: auto;
  }

  .premium-table {
    min-width: 720px;
  }
}
</style>
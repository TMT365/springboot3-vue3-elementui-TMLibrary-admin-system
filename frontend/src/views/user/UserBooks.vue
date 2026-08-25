<script setup lang="ts">
/**
 * UserBooks  -  图书展示首页(USER / ADMIN 共用,USER 看只读,ADMIN 可点编辑)
 *
 * 调用:GET /api/books/list
 * 后续可加搜索 / 排序 / 详情弹窗,先做最简列表
 */

import { ref, onMounted } from 'vue'
import { bookApi } from '@/api/book'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import type { BookDto, PageResult } from '@/types/api'

const userStore = useUserStore()
const cartStore = useCartStore()
const loading = ref(true)
const books = ref<BookDto[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const error = ref<string | null>(null)

async function fetchBooks(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result: PageResult<BookDto> = await bookApi.list({ page: page.value, size: size.value })
    books.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function formatPrice(price: string): string {
  return `¥${Number(price).toFixed(2)}`
}

function formatDate(date: string): string {
  return date ? date.slice(0, 10) : '-'
}

function onPageChange(p: number): void {
  page.value = p
  fetchBooks()
}

/**
 * 加入购物车按钮  -  根据 addItem 返回值给不同反馈:
 *   - 'added'         → 首次加入:「已加入购物车」
 *   - 'incremented'   → 已存在:+1:「数量已 +1」
 * 库存 0 时按钮禁用
 */
function handleAddToCart(book: BookDto): void {
  if ((book.stockQuantity ?? 0) <= 0) {
    ElMessage.warning('库存不足,无法加入')
    return
  }
  const result = cartStore.addItem(book)
  ElMessage.success(result === 'added' ? '已加入购物车' : '购物车中数量 +1')
}

onMounted(fetchBooks)
</script>

<template>
  <div class="user-books">
    <header class="page-head">
      <div>
        <h1 class="page-title">图书浏览</h1>
        <p class="page-sub">
          馆藏共 <strong>{{ total }}</strong> 本图书
        </p>
      </div>
      <el-button :loading="loading" @click="fetchBooks">刷新</el-button>
    </header>

    <div v-if="error" class="error-banner">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <el-skeleton v-else-if="loading" :rows="6" animated class="skeleton" />

    <el-empty v-else-if="!loading && books.length === 0" description="暂无图书" />

    <div v-else class="book-grid">
      <article v-for="book in books" :key="book.id" class="book-card">
        <div class="book-cover">
          <span class="book-cover-mark">{{ book.title.slice(0, 1) }}</span>
        </div>
        <div class="book-body">
          <h3 class="book-title">{{ book.title }}</h3>
          <p class="book-author">{{ book.author }}</p>
          <p class="book-meta">
            <span>ISBN: {{ book.isbn }}</span>
            <span>出版: {{ formatDate(book.publishedDate) }}</span>
          </p>
          <div class="book-footer">
            <span class="book-price">{{ formatPrice(book.price) }}</span>
            <span class="book-stock">
              库存 <strong>{{ book.stockQuantity ?? 0 }}</strong>
            </span>
          </div>

          <el-button
            type="primary"
            size="small"
            class="add-to-cart-btn"
            :disabled="(book.stockQuantity ?? 0) <= 0"
            @click.stop="handleAddToCart(book)"
          >
            <el-icon><ShoppingCart /></el-icon>
            <span>加入购物车</span>
          </el-button>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="total > size"
      class="pager"
      background
      layout="prev, pager, next, total"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPageChange"
    />
  </div>
</template>

<style scoped>
.user-books {
  max-width: 1200px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
  padding: 6px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
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
  /* tabular-nums:数字加粗时不抖动 */
  font-variant-numeric: tabular-nums;
}

.skeleton {
  margin-top: 16px;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.12);
  color: #f56c6c;
  border: 1px solid rgba(245, 108, 108, 0.2);
  border-radius: 12px;
  backdrop-filter: blur(10px);
  margin-bottom: 16px;
  font-size: 14px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.book-card {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(14px) saturate(125%);
  -webkit-backdrop-filter: blur(14px) saturate(125%);
  border-radius: 18px;
  box-shadow:
    0 10px 24px rgba(17, 25, 40, 0.12),
    0 2px 6px rgba(17, 25, 40, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  overflow: hidden;
  transition:
    transform 240ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 240ms cubic-bezier(0.2, 0, 0, 1);
  cursor: pointer;
}

.book-card:hover {
  transform: translateY(-4px);
  box-shadow:
    0 14px 30px rgba(17, 25, 40, 0.18),
    0 20px 40px rgba(76, 175, 80, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.book-card:active {
  transform: translateY(-2px) scale(0.99);
}

.book-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, var(--color-bg-alt) 0%, var(--color-border) 100%);
  display: grid;
  place-items: center;
  box-shadow:
    inset 0 -1px 0 rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.book-cover-mark {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 56px;
  font-weight: 700;
  color: var(--color-text-soft);
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.4);
}

.book-body {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.book-title {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 17px;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.01em;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-soft);
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.book-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

/* 「加入购物车」按钮  -  全宽,小尺寸 */
.add-to-cart-btn {
  margin-top: 12px;
  width: 100%;
  font-weight: 500;
  border-radius: 8px;
}

.add-to-cart-btn :deep(.el-icon) {
  margin-right: 4px;
}

.book-price {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.book-stock {
  font-size: 12px;
  color: var(--color-text-muted);
}

.book-stock strong {
  color: var(--color-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.pager {
  margin-top: 32px;
  justify-content: center;
}

:root[data-theme='dark'] .book-card {
  background: rgba(35, 35, 35, 0.62);
  border-color: rgba(255, 255, 255, 0.08);
}

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .book-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .book-card {
    border-radius: 12px;
  }
}
</style>
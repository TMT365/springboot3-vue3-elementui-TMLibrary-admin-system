<script setup lang="ts">
/**
 * UserOrders  -  我的订单
 *
 * 调用:GET /api/users/{id}/purchases  拿当前用户的订单列表
 * 同时调 GET /api/books/list 一次,缓存书名做 join(避免 N+1)
 *
 * 每条订单展示:订单号 / 状态 / 商品明细 / 总价
 */

import { ref, onMounted, computed } from 'vue'
import { userApi } from '@/api/user'
import { bookApi } from '@/api/book'
import { useUserStore } from '@/stores/user'
import type { BookDto, PurchaseResponse } from '@/types/api'

const userStore = useUserStore()
const orders = ref<PurchaseResponse[]>([])
const bookMap = ref<Record<number, BookDto>>({})
const loading = ref(true)
const error = ref<string | null>(null)

const statusMeta = computed<
  Record<string, { label: string; type: 'warning' | 'success' | 'info' }>
>(() => ({
  PENDING: { label: '待支付', type: 'warning' },
  PAID: { label: '已支付', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
}))

function formatPrice(s: string): string {
  return `¥${Number(s).toFixed(2)}`
}

function formatDateTime(s: string | null | undefined): string {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}

function bookTitle(bookId: number): string {
  return bookMap.value[bookId]?.title ?? `图书 #${bookId}`
}

function bookAuthor(bookId: number): string {
  return bookMap.value[bookId]?.author ?? ''
}

async function fetchOrders(): Promise<void> {
  if (userStore.userId == null) {
    error.value = '无法获取用户 ID,请重新登录'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    // 一次性拉所有书做本地缓存,避免 N+1
    const [list, mine] = await Promise.all([
      bookApi.list({ page: 1, size: 200 }),
      userApi.listPurchases(userStore.userId),
    ])
    bookMap.value = Object.fromEntries(list.data.map((b) => [b.id, b]))
    orders.value = mine
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchOrders)
</script>

<template>
  <div class="user-orders">
    <header class="page-head">
      <div>
        <h1 class="page-title">我的订单</h1>
        <p class="page-sub">
          共 <strong>{{ orders.length }}</strong> 条订单
        </p>
      </div>
      <el-button :loading="loading" @click="fetchOrders">刷新</el-button>
    </header>

    <div v-if="error" class="error-banner">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <el-skeleton v-else-if="loading" :rows="6" animated class="skeleton" />

    <el-empty
      v-else-if="!loading && orders.length === 0"
      description="暂无订单"
    />

    <div v-else class="orders-list">
      <article v-for="order in orders" :key="order.orderNumber" class="order-card">
        <header class="order-head">
          <div class="order-id">
            <span class="order-label">订单号</span>
            <span class="order-number">{{ order.orderNumber }}</span>
          </div>
          <el-tag
            v-if="statusMeta[order.status]"
            :type="statusMeta[order.status].type"
            effect="light"
            size="small"
          >
            {{ statusMeta[order.status].label }}
          </el-tag>
        </header>

        <ul class="item-list">
          <li v-for="(item, i) in order.items" :key="i" class="item">
            <div class="item-info">
              <span class="item-title">{{ bookTitle(item.bookId) }}</span>
              <span v-if="bookAuthor(item.bookId)" class="item-author">
                {{ bookAuthor(item.bookId) }}
              </span>
            </div>
            <div class="item-meta">
              <span class="item-qty">× {{ item.quantity }}</span>
              <span class="item-price">{{ formatPrice(item.price) }}</span>
              <span class="item-subtotal">{{ formatPrice(item.subtotal) }}</span>
            </div>
          </li>
        </ul>

        <footer class="order-foot">
          <span class="total-label">订单合计</span>
          <span class="total-amount">{{ formatPrice(order.totalAmount) }}</span>
        </footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.user-orders {
  max-width: 960px;
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

.orders-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.order-card {
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(14px) saturate(125%);
  -webkit-backdrop-filter: blur(14px) saturate(125%);
  border-radius: 18px;
  box-shadow:
    0 10px 24px rgba(17, 25, 40, 0.12),
    0 2px 6px rgba(17, 25, 40, 0.07);
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.order-id {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.order-label {
  font-size: 12px;
  color: var(--color-text-soft);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  flex-shrink: 0;
}

.order-number {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 13px;
  color: var(--color-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--color-border);
}

.item:last-child {
  border-bottom: none;
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.item-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-author {
  font-size: 12px;
  color: var(--color-text-soft);
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.item-qty {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-variant-numeric: tabular-nums;
}

.item-price {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-variant-numeric: tabular-nums;
}

.item-subtotal {
  font-family: 'DM Serif Display', Georgia, serif;
  font-weight: 700;
  color: var(--color-text);
  min-width: 80px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.order-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.total-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.total-amount {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

:root[data-theme='dark'] .order-card {
  background: rgba(35, 35, 35, 0.62);
  border-color: rgba(255, 255, 255, 0.08);
}

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }

  .page-title {
    font-size: 24px;
  }

  .order-card {
    padding: 16px 18px;
    border-radius: 12px;
  }

  .item {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .item-meta {
    align-self: flex-end;
  }

  .order-foot {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }

  .total-amount {
    text-align: right;
  }
}
</style>
<script setup lang="ts">
/**
 * UserCart  -  购物车视图
 *
 * 数据源:cartsStore(纯前端 mock,localStorage 持久化)
 * 结算流程:
 *   1. 用户点「结算」
 *   2. purchaseApi.create({ items: [{ bookId, quantity }, ...] })  ← 走真后端
 *   3. 清空购物车 + 跳 /user/my-orders
 *
 * 设计 follow UserBooks / UserOrders:
 *   - page-head 容器 + title + sub
 *   - error / loading / empty / list 四态
 *   - 多层 box-shadow 卡片,tabular-nums 价格,text-wrap: balance
 *   - 响应式:移动端纵向堆叠
 */

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { purchaseApi } from '@/api/purchase'
import type { PurchaseItemRequest } from '@/types/api'

const cartStore = useCartStore()
const router = useRouter()

const submitting = ref<boolean>(false)
const checkoutError = ref<string | null>(null)

function formatPrice(s: string): string {
  return `¥${Number(s).toFixed(2)}`
}

/**
 * 单项小计 = price × quantity(BigDecimal 字符串 × 整数)
 */
function itemSubtotal(price: string, quantity: number): string {
  return (Number(price) * quantity).toFixed(2)
}

/**
 * 结算  -  把 CartItem 列表映射成 PurchaseItemRequest,调真 API
 * 成功后清空购物车 + 跳我的订单(让用户立刻看到新订单)
 */
async function handleCheckout(): Promise<void> {
  if (cartStore.isEmpty || submitting.value) return
  submitting.value = true
  checkoutError.value = null
  try {
    const items: PurchaseItemRequest[] = cartStore.items.map((i) => ({
      bookId: i.bookId,
      quantity: i.quantity,
    }))
    await purchaseApi.create({ items })
    cartStore.clear()
    ElMessage.success('订单已生成,跳转我的订单')
    await router.replace('/user/my-orders')
  } catch (e) {
    checkoutError.value = e instanceof Error ? e.message : '结算失败,请重试'
  } finally {
    submitting.value = false
  }
}

/**
 * 数量变化通过 input-number 的 @change 事件
 * ElMessage 提示已经在 store / 全局请求层处理,这里只调 store
 */
function handleQuantityChange(bookId: number, value: number | undefined): void {
  cartStore.updateQuantity(bookId, value ?? 0)
}

/** 单项操作的 ElMessage 反馈  -  集中放在这里让模板简洁 */
const itemCountLabel = computed<string>(() => {
  const n = cartStore.totalItems
  return n > 0 ? `共 ${n} 件` : ''
})
</script>

<template>
  <div class="user-cart">
    <header class="page-head">
      <div>
        <h1 class="page-title">购物车</h1>
        <p class="page-sub" v-if="!cartStore.isEmpty">
          <strong>{{ itemCountLabel }}</strong> · 待结算
        </p>
      </div>
      <el-button
        v-if="!cartStore.isEmpty"
        :disabled="submitting"
        @click="cartStore.clear()"
      >
        清空购物车
      </el-button>
    </header>

    <!-- 错误状态(结算失败 / 数据损坏) -->
    <div v-if="checkoutError" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ checkoutError }}</span>
    </div>

    <!-- 空状态 -->
    <div v-if="cartStore.isEmpty" class="empty-state">
      <div class="empty-icon" aria-hidden="true">🛒</div>
      <h2 class="empty-title">购物车空空如也</h2>
      <p class="empty-desc">浏览图书,加入几本心仪的吧</p>
      <router-link to="/user/books" replace class="empty-action">
        去逛逛
      </router-link>
    </div>

    <!-- 列表 -->
    <div v-else class="cart-list">
      <article
        v-for="item in cartStore.items"
        :key="item.bookId"
        class="cart-row"
      >
        <!-- 封面 -->
        <div class="cover" aria-hidden="true">
          <span class="cover-mark">{{ item.coverMark }}</span>
        </div>

        <!-- 信息 -->
        <div class="info">
          <h3 class="book-title" :title="item.title">{{ item.title }}</h3>
          <p class="book-author">{{ item.author || '佚名' }}</p>
          <p class="book-isbn">ISBN: {{ item.isbn }}</p>
        </div>

        <!-- 单价 -->
        <div class="unit-price">
          <span class="price-label">单价</span>
          <span class="price-value">{{ formatPrice(item.price) }}</span>
        </div>

        <!-- 数量 -->
        <div class="quantity">
          <span class="qty-label">数量</span>
          <el-input-number
            :model-value="item.quantity"
            :min="0"
            :max="99"
            size="small"
            @change="(v) => handleQuantityChange(item.bookId, v ?? 0)"
          />
        </div>

        <!-- 小计 -->
        <div class="subtotal">
          <span class="sub-label">小计</span>
          <span class="sub-value">
            {{ formatPrice(itemSubtotal(item.price, item.quantity)) }}
          </span>
        </div>

        <!-- 删除 -->
        <el-button
          link
          class="remove-btn"
          :aria-label="`从购物车移除 ${item.title}`"
          @click="cartStore.removeItem(item.bookId)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </article>

      <!-- 结算条 -->
      <footer class="checkout-bar">
        <div class="checkout-summary">
          <span class="summary-label">合计</span>
          <span class="summary-value">
            {{ formatPrice(cartStore.subtotal) }}
          </span>
          <span class="summary-meta">{{ itemCountLabel }}</span>
        </div>
        <el-button
          type="primary"
          size="large"
          class="checkout-btn"
          :loading="submitting"
          :disabled="cartStore.isEmpty"
          @click="handleCheckout"
        >
          去结算
        </el-button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.user-cart {
  max-width: 1080px;
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

/* ============================================================
 * 空状态  -  居中堆叠,清晰指引
 * ============================================================ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 96px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  opacity: 0.6;
  margin-bottom: 8px;
}

.empty-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;}

.empty-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.empty-action {
  margin-top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 22px;
  background: var(--color-accent);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  border-radius: 999px;
  text-decoration: none;
  transition: background-color 180ms ease, transform 180ms ease;
}

.empty-action:hover {
  background: var(--color-accent-hover);
  transform: translateY(-1px);
}

.empty-action:active {
  transform: translateY(0) scale(0.98);
}

/* ============================================================
 * 列表  -  网格布局,封面 + 信息 + 单价 + 数量 + 小计 + 删除
 * 行内分隔 hairline,hover 浅底
 * ============================================================ */
.cart-list {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(14px) saturate(125%);
  -webkit-backdrop-filter: blur(14px) saturate(125%);
  border-radius: 18px;
  box-shadow:
    0 10px 24px rgba(17, 25, 40, 0.12),
    0 2px 6px rgba(17, 25, 40, 0.07);
  overflow: hidden;
}

.cart-row {
  display: grid;
  grid-template-columns: 72px 1fr auto auto auto 32px;
  gap: 20px;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid var(--color-border);
  transition: background-color 180ms ease;
}

.cart-row:last-of-type {
  border-bottom: none;
}

.cart-row:hover {
  background: var(--color-bg-alt);
}

.cover {
  width: 72px;
  height: 72px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--color-bg-alt) 0%, var(--color-border) 100%);
  border-radius: 10px;
  flex-shrink: 0;
}

.cover-mark {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 400;
  color: var(--color-text-soft);
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.4);
}

/* 信息 */
.info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.book-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
  letter-spacing: -0.01em;
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

.book-isbn {
  font-size: 12px;
  color: var(--color-text-soft);
  margin: 0;
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-variant-numeric: tabular-nums;
}

.unit-price,
.quantity,
.subtotal {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  min-width: 80px;
}

.price-label,
.qty-label,
.sub-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.price-value,
.sub-value {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 14px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.sub-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-accent);
}

/* 数量列居中 */
.quantity {
  align-items: center;
}

/* 删除按钮 */
.remove-btn {
  color: var(--color-text-soft);
  transition: color 150ms ease;
}

.remove-btn:hover {
  color: #f56c6c;
}

.remove-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px #f56c6c;
}

/* ============================================================
 * 结算条  -  粘在底部,大数字 + 主按钮
 * ============================================================ */
.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.46);
  border-top: 1px solid rgba(255, 255, 255, 0.52);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.checkout-summary {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.summary-label {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.summary-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.summary-meta {
  font-size: 13px;
  color: var(--color-text-soft);
}

.checkout-btn {
  padding: 0 32px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 999px;
}

:root[data-theme='dark'] .cart-list,
:root[data-theme='dark'] .checkout-bar {
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

  .cart-list {
    border-radius: 12px;
  }

  .cart-row {
    grid-template-columns: 64px 1fr auto;
    grid-template-rows: auto auto auto;
    gap: 12px;
    padding: 16px 18px;
  }

  .cover {
    grid-row: 1 / span 3;
    width: 64px;
    height: 64px;
  }

  .info {
    grid-column: 2;
    grid-row: 1 / span 2;
  }

  .unit-price { display: none; }

  .quantity {
    grid-column: 2;
    grid-row: 3;
    flex-direction: row;
    align-items: center;
    gap: 8px;
  }

  .subtotal {
    grid-column: 3;
    grid-row: 1 / span 3;
    align-items: flex-end;
  }

  .remove-btn {
    grid-column: 3;
    grid-row: 3;
    align-self: end;
  }

  .checkout-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    padding: 16px;
  }

  .checkout-summary {
    justify-content: space-between;
  }

  .checkout-btn {
    width: 100%;
  }
}
</style>
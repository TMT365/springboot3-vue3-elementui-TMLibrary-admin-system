<script setup lang="ts">
/**
 * purchase/List  -  订单列表
 *
 * P5 接通后端:目前展示表结构 + 状态样式,数据为空时友好提示
 *
 * 风格跟 book/List 完全对齐:同 .premium-table、同 page-head、同状态色
 */

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatPrice, formatDateTime } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import type { OrderStatus } from '@/types/api'

const router = useRouter()
const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<PurchaseRow[]>([])

interface PurchaseRow {
  id: number
  orderNumber: string
  status: OrderStatus
  totalAmount: string
  createdTime: string
}

/**
 * 状态 → tag type / 标签
 * - PENDING:warning(琥珀) -  待支付
 * - PAID:success(绿) -  已支付
 * - CANCELLED:info(灰) -  已取消
 */
function statusTagType(status: string): 'warning' | 'success' | 'info' {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'PAID': return 'success'
    case 'CANCELLED': return 'info'
    default: return 'info'
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待支付'
    case 'PAID': return '已支付'
    case 'CANCELLED': return '已取消'
    default: return status
  }
}

function goCreate(): void {
  router.replace('/admin/purchases/new')
}
</script>

<template>
  <div class="purchase-list">
    <header class="page-head">
      <div>
        <h1 class="page-title">订单列表</h1>
        <p class="page-sub">
          共 <strong>{{ total }}</strong> 条订单
        </p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="goCreate">
          <el-icon><Plus /></el-icon>
          <span>新建订单</span>
        </el-button>
      </div>
    </header>

    <div class="table-card">
      <el-empty
        v-if="items.length === 0"
        description="暂无订单数据,后端接通后展示"
      />

      <el-table
        v-else
        :data="items"
        class="premium-table"
        :row-class-name="() => 'premium-row'"
      >
        <el-table-column prop="orderNumber" label="订单号" min-width="200">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.orderNumber }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总额" width="130" align="right">
          <template #default="{ row }">
            <span class="cell-price">{{ formatPrice(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDateTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small">查看</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="danger"
              size="small"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <Pager v-model:page="page" v-model:size="size" :total="total" />
  </div>
</template>

<style scoped>
.purchase-list {
  max-width: 1280px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

/* 跟 book/List 完全对齐的页面头部 */
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

/* 表格外壳  -  跟 book/List 同款多层 shadow */
.table-card {
  background: var(--color-card);
  border-radius: 14px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  overflow: hidden;
}

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

/* 响应式 */
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
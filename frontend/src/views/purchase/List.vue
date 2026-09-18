<script setup lang="ts">
/**
 * purchase/List  -  订单列表(管理端)
 *
 * 调用:GET /api/purchases?page=&size=  —— 全量订单分页,仅 ADMIN / BOSS
 * 返回 OrderListItem(订单号 / 状态 / 总额 / 下单时间),不含明细
 *
 * 风格跟 book/List、user/Manage 完全对齐:同 .premium-table、同 page-head、同状态色
 *
 * 未接的部分(操作列的按钮仍是占位):
 *   - 「查看」需要订单详情页/抽屉(接口 GET /api/purchases/{orderNumber} 已有)
 *   - 「取消」后端只允许订单所有者操作,管理员取消他人订单会被 403 拦掉
 */

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatPrice, formatDateTime } from '@/utils/format'
import { purchaseApi } from '@/api/purchase'
import Pager from '@/components/Pager.vue'
import type { OrderListItem } from '@/types/api'

const router = useRouter()
const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<OrderListItem[]>([])
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

/** 拉取订单列表 —— 全量分页(后端按 id 倒序,最新在前) */
async function fetchOrders(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await purchaseApi.listOrders(page.value, size.value)
    items.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchOrders)

/**
 * 状态 → tag type / 标签
 * - PENDING:warning(琥珀) -  待支付
 * - PAID:success(绿) -  已支付
 * - CANCELLED:info(灰) -  已取消
 */
function statusTagType(status: string): 'warning' | 'success' | 'info' | 'danger' {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'PAID': return 'success'
    case 'CANCELLED': return 'info'
    case 'TIMEOUT': return 'danger'
    default: return 'info'
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待支付'
    case 'PAID': return '已支付'
    case 'CANCELLED': return '已取消'
    case 'TIMEOUT': return '超时取消'
    default: return status
  }
}

/** 支付方式 code → 中文名(后端存的是 PaymentMethod 枚举 code) */
function payMethodLabel(method: string): string {
  switch (method) {
    case 'WECHAT': return '微信支付'
    case 'ALIPAY': return '支付宝'
    case 'QQ': return 'QQ 钱包'
    default: return method
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

    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <div class="table-card">
      <el-skeleton v-if="loading && items.length === 0" :rows="6" animated />

      <el-empty
        v-else-if="!loading && items.length === 0"
        description="暂无订单"
      />

      <el-table
        v-else
        v-loading="loading"
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
        <el-table-column label="支付方式" width="110">
          <template #default="{ row }">
            <!-- 未支付 = null;加这列之前就付过的老订单也是 null(当时没记录),
                 统一显示「-」,不是渲染失败 -->
            <span v-if="row.paymentMethod" class="cell-muted">
              {{ payMethodLabel(row.paymentMethod) }}
            </span>
            <span v-else class="cell-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDateTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" width="180">
          <template #default="{ row }">
            <span class="cell-muted">
              {{ row.paidTime ? formatDateTime(row.paidTime) : '-' }}
            </span>
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

    <Pager v-model:page="page" v-model:size="size" :total="total" @change="fetchOrders" />
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
  /* 行距:15 → 20px,订单行信息少,给足呼吸感更好扫读 */
  padding: 20px 0;
}

.premium-table :deep(.el-table__header-wrapper) th {
  padding: 14px 0;
}

/* 单元格左右内边距:EP 默认 12px,列挨得近时数字容易挤在一起 */
.premium-table :deep(.el-table .cell) {
  padding: 0 16px;
}

.premium-table :deep(.el-table__row:last-child td) {
  border-bottom: none;
}

.premium-table :deep(.el-table__row:hover) td {
  background: var(--color-bg-alt);
}

/* 订单号 —— 16 位雪花 ID 连成一串,原来的通用等宽栈
   (ui-monospace → Windows 上落到 Consolas)又小又挤,数字难认。
   改用已加载的 Inter:字形清晰,配 tabular-nums 一样能列对齐。 */
.cell-mono {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 13.5px;
  font-weight: 500;
  /* 等宽数字保证多行对齐;字距拉开,长串 ID 才不糊成一团 */
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.055em;
  color: var(--color-text);
}

.cell-muted {
  font-size: 13px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 金额 —— 同订单号:换成 Inter + 等宽数字,金额列上下对齐且字形清晰 */
.cell-price {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  /* 窄屏横向滚动交给 el-table 自己(.el-scrollbar)——
     不要在外面套一层 overflow-x 容器 + 给表格 min-width:
     EP 的固定列是 position: sticky,它的吸附基准是**最近的滚动祖先**,
     即表格内部的 .el-scrollbar;外层再套一个滚动容器的话,
     内部那个永远不滚,固定列就跟着表格一起被推出屏幕,
     「操作」列要一路滑到最右才看得见。 */
  .table-card {
    border-radius: 12px;
  }
}
</style>
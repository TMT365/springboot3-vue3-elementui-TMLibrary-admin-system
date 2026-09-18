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
import { purchaseApi } from '@/api/purchase'
import { useUserStore } from '@/stores/user'
import PaymentDialog from '@/components/PaymentDialog.vue'
import type { BookDto, PurchaseResponse } from '@/types/api'

const userStore = useUserStore()
const orders = ref<PurchaseResponse[]>([])
const bookMap = ref<Record<number, BookDto>>({})
const loading = ref(true)
const error = ref<string | null>(null)

/* ------------------- 支付 ------------------- */

/** 支付方式 code → 中文名(展示用;后端存的是枚举 code) */
const PAY_LABEL: Record<string, string> = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  QQ: 'QQ 钱包',
}

/** 已支付订单显示支付方式;老订单(加这列之前付的)没有记录,显示 - */
function payMethodLabel(method: string | null): string {
  if (!method) return '-'
  return PAY_LABEL[method] ?? method
}

const payDialogVisible = ref(false)
/** 正在支付的订单;null = 弹窗没开 */
const payingOrder = ref<PurchaseResponse | null>(null)
const paying = ref(false)

function openPay(order: PurchaseResponse): void {
  payingOrder.value = order
  payDialogVisible.value = true
}

async function onPayConfirm(method: string): Promise<void> {
  const order = payingOrder.value
  if (!order) return
  paying.value = true
  try {
    await purchaseApi.pay(order.orderNumber, method)
    ElMessage.success('支付成功')
    payDialogVisible.value = false
    payingOrder.value = null
    // 重新拉一次而不是本地改状态 —— 支付会连带扣 DB 库存、翻 Redis 预占,
    // 本地手改容易和真实状态漂移(比如库存被别人抢空导致订单被自动取消)
    await fetchOrders()
  } catch {
    // request.ts 已经 toast 过具体原因(库存不足会自动关单、状态被别人抢先等),这里不重复弹
  } finally {
    paying.value = false
  }
}

// 四个状态都要有 —— 漏掉 TIMEOUT 的话超时关单的订单不显示状态标签,
// 时间轴上又只有一个"尚未支付"的灰节点,看起来像数据缺了
const statusMeta = computed<
  Record<string, { label: string; type: 'warning' | 'success' | 'info' | 'danger' }>
>(() => ({
  PENDING: { label: '待支付', type: 'warning' },
  PAID: { label: '已支付', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  TIMEOUT: { label: '超时关闭', type: 'danger' },
}))

function formatPrice(n: number): string {
  return `¥${n.toFixed(2)}`
}

/**
 * 后端给的是 ISO 串 "2026-09-16T21:36:33"(已截到秒)。
 * 拆成日期 + 时间两段分别排版:日期正常字重,时间用等宽数字 —— 一列订单扫下来
 * 秒位是对齐的,比整串一个样式好读。
 */
function splitDateTime(s: string | null | undefined): { date: string; time: string } | null {
  if (!s) return null
  const [date, time] = s.replace('T', ' ').split(' ')
  return { date: date ?? '', time: (time ?? '').slice(0, 8) }
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
      <article
        v-for="order in orders"
        :key="order.orderNumber"
        class="order-card"
        :class="`is-${order.status.toLowerCase()}`"
      >
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

        <!-- 订单生命周期:下单 → 支付。未支付时第二个节点是灰的、连线断开 ——
             一眼就能看出这单走到哪一步,不用去读状态标签 -->
        <div class="timeline">
          <div class="tl-node">
            <span class="tl-dot" aria-hidden="true" />
            <span class="tl-label">下单</span>
            <span class="tl-value">
              <span class="tl-date">{{ splitDateTime(order.createdTime)?.date ?? '-' }}</span>
              <span class="tl-time">{{ splitDateTime(order.createdTime)?.time ?? '' }}</span>
            </span>
          </div>

          <div class="tl-node" :class="{ 'is-empty': !order.paidTime }">
            <span class="tl-dot" aria-hidden="true" />
            <span class="tl-label">支付</span>
            <span v-if="order.paidTime" class="tl-value">
              <span class="tl-date">{{ splitDateTime(order.paidTime)?.date }}</span>
              <span class="tl-time">{{ splitDateTime(order.paidTime)?.time }}</span>
              <!-- 用了什么方式付的。老订单(加这列之前付的)没有记录 → 「-」,
                   不是渲染失败,是当时确实没存 -->
              <span class="tl-method">{{ payMethodLabel(order.paymentMethod) }}</span>
            </span>
            <span v-else class="tl-value is-pending">尚未支付</span>
          </div>
        </div>

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
          <!-- 只有待支付才给「去支付」;已支付/已取消/超时都不该出现这个按钮 -->
          <el-button
            v-if="order.status === 'PENDING'"
            type="primary"
            class="pay-btn"
            @click="openPay(order)"
          >
            去支付
          </el-button>
        </footer>
      </article>
    </div>

    <!-- 支付方式选择弹窗 —— 挂在列表外,避免每个订单卡片都渲染一份 -->
    <PaymentDialog
      v-model="payDialogVisible"
      :order-number="payingOrder?.orderNumber ?? ''"
      :amount="payingOrder ? formatPrice(payingOrder.totalAmount) : ''"
      :loading="paying"
      @confirm="onPayConfirm"
    />
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

/* ============================================================
 * 订单卡  -  3D 分层
 *   近景 0 2px 6px  贴地接触阴影
 *   中景 0 10px 24px 主投影
 *   远景 0 24px 48px 大气投影(hover 才出现,制造"抬起来"的纵深)
 *   inset 顶部 1px 高光 = 纸面受光,是这套 3D 语言的关键一笔
 * 左侧 3px 状态色轨:一列订单扫下来,状态不用读标签就能分辨
 * ============================================================ */
.order-card {
  --rail: var(--color-border);
  position: relative;
  background: rgba(255, 255, 255, 0.56);
  /* 亮色下白描边压在浅底上不可见 —— 换淡墨描边定义轮廓(暗色由下方 dark 块覆盖) */
  border: 1px solid rgba(17, 25, 40, 0.07);
  backdrop-filter: blur(14px) saturate(125%);
  -webkit-backdrop-filter: blur(14px) saturate(125%);
  border-radius: 18px;
  box-shadow:
    0 10px 24px rgba(17, 25, 40, 0.12),
    0 2px 6px rgba(17, 25, 40, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  padding: 20px 24px 20px 26px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: hidden;
  transition:
    transform 260ms cubic-bezier(0.2, 0, 0, 1),
    box-shadow 260ms cubic-bezier(0.2, 0, 0, 1),
    border-color 260ms ease;
}

/* 状态轨 —— 用伪元素而不是 border-left:圆角卡片上 border 会被圆角切歪 */
.order-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--rail);
  transition: background-color 220ms ease;
}

.order-card.is-paid {
  --rail: var(--color-accent);
}

.order-card.is-pending {
  --rail: #e6a23c;
}

/* 超时关闭 = 系统替你取消了,给一档更重的颜色区别于"用户主动取消" */
.order-card.is-timeout {
  --rail: #f56c6c;
}

/* hover 抬起来:接触阴影收紧 + 大气阴影铺开,配合 1px 上浮 */
@media (hover: hover) {
  .order-card:hover {
    transform: translateY(-3px);
    border-color: rgba(76, 175, 80, 0.28);
    box-shadow:
      0 16px 32px rgba(17, 25, 40, 0.16),
      0 2px 6px rgba(17, 25, 40, 0.06),
      0 28px 56px rgba(17, 25, 40, 0.08),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* ============================================================
 * 时间轴  -  下单 → 支付
 * ============================================================ */
.timeline {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 28px;
  padding: 10px 0 2px;
  /* 时间轴和明细之间用极淡的分隔,避免和下方的虚线分隔打架 */
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 14px;
}

.tl-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.tl-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.14);
}

/* 未支付:节点变灰、光晕收掉 */
.tl-node.is-empty .tl-dot {
  background: var(--color-text-soft);
  box-shadow: none;
}

/* 两个节点之间的连线(只在下单节点后面画) */
.tl-node:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 10px;
  right: -28px;
  top: 50%;
  height: 1px;
  background: var(--color-border);
}

.tl-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--color-text-soft);
  flex-shrink: 0;
}

.tl-value {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  color: var(--color-text);
}

/* 时刻用等宽数字 —— 一列订单扫下来秒位对齐 */
.tl-time {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--color-text);
}

.tl-date {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}

.tl-value.is-pending {
  color: var(--color-text-soft);
  font-style: italic;
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
  /* 全站统一:数字类信息用 Inter + tabular-nums + 一点字距,
     不用 ui-monospace —— 那套栈在不同系统上渲染差异大,和页面其它数字也对不齐 */
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 13.5px;
  color: var(--color-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
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
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
}

.item-price {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
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

/* 支付方式小标签 —— 跟在支付时间后面,弱化处理(它是补充信息,不是主体) */
.tl-method {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--color-bg-alt);
  box-shadow: 0 0 0 1px var(--color-border);
  font-size: 11.5px;
  color: var(--color-text-muted);
}

/* 去支付按钮:金额右边的主行动点,和卡片里其它次要元素拉开层级 */
.pay-btn {
  border: none;
  border-radius: 16px;
  font-weight: 600;
  background: linear-gradient(
    135deg,
    var(--color-accent) 0%,
    var(--color-accent-hover) 100%
  );
  box-shadow: 0 8px 18px -8px rgba(76, 175, 80, 0.65);
  transition:
    transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 180ms ease;
}

.pay-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 22px -8px rgba(76, 175, 80, 0.7);
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
  /* 深底上白高光要压到 0.06,否则卡片上沿发白 */
  box-shadow:
    0 10px 24px rgba(0, 0, 0, 0.42),
    0 2px 6px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

:root[data-theme='dark'] .order-card.is-paid {
  --rail: #66bb6a;
}

:root[data-theme='dark'] .order-card.is-pending {
  --rail: #e6a23c;
}

:root[data-theme='dark'] .order-card.is-timeout {
  --rail: #f56c6c;
}

@media (hover: hover) {
  :root[data-theme='dark'] .order-card:hover {
    border-color: rgba(102, 187, 106, 0.32);
    box-shadow:
      0 16px 32px rgba(0, 0, 0, 0.5),
      0 2px 6px rgba(0, 0, 0, 0.3),
      0 28px 56px rgba(0, 0, 0, 0.36),
      inset 0 1px 0 rgba(255, 255, 255, 0.08);
  }
}

:root[data-theme='dark'] .tl-time {
  color: #f0f0f0;
}

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }

  .page-title {
    font-size: 24px;
  }

  .orders-list {
    gap: 12px;
  }

  .order-card {
    padding: 15px 15px 15px 18px;
    border-radius: 14px;
  }

  /* 手机上两段时间横排会挤成一行小字 —— 改成上下两行,连线改成竖的 */
  .timeline {
    flex-direction: column;
    gap: 0;
    padding: 8px 0 12px;
  }

  .tl-node {
    padding: 5px 0;
  }

  .tl-node:not(:last-child)::after {
    left: 3px;
    right: auto;
    top: 18px;
    bottom: -5px;
    width: 1px;
    height: auto;
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

/* 手机(≤600):字号和内边距再收一档,但时间值保持 13px ——
   再小就开始糊,订单时间是要看清的信息 */
@media (max-width: 600px) {
  .order-number {
    font-size: 12.5px;
    letter-spacing: 0.02em;
  }

  .tl-label {
    font-size: 11px;
  }

  .tl-value {
    font-size: 12.5px;
    gap: 5px;
  }

  .item-title {
    font-size: 14px;
  }

  .item-meta {
    gap: 12px;
    font-size: 12.5px;
  }

  .total-amount {
    font-size: 20px;
  }
}
</style>
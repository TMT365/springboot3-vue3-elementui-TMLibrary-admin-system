<script setup lang="ts">
/**
 * Dashboard  -  后台首页(现代化概览)
 *
 * 4 张统计卡 + 最近图书表 + 快捷操作:
 *   - 馆藏图书(bookApi.list total)
 *   - 注册用户(userApi.list total,仅 ADMIN/BOSS 可见)
 *   - 我的角色(userStore.role)
 *   - 当前时间(刷新时动态显示)
 *
 * 加载:4 张卡 + 表同时拉,loading 期间整体骨架
 * 空态:最近图书表空时友好提示
 *
 * **Skills**:make-interfaces-feel-better 多层 shadow / tabular-nums / focus-visible
 *           ui-ux-pro-max 响应式 / 触达 / a11y
 *           frontend-design 大数字 + accent 克制
 */

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { bookApi } from '@/api/book'
import { userApi } from '@/api/user'
import { formatPrice, formatDate } from '@/utils/format'
import type { BookDto } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

const loading = ref<boolean>(true)
const error = ref<string | null>(null)

const bookTotal = ref<number>(0)
const userTotal = ref<number>(0)
const recentBooks = ref<BookDto[]>([])

const isAdmin = computed<boolean>(() => userStore.isAdmin)

const roleLabel = computed<string>(() => {
  switch (userStore.role) {
    case 'BOSS': return '老板'
    case 'ADMIN': return '管理员'
    case 'USER': return '普通用户'
    default: return ' - '
  }
})

/**
 * 4 张统计卡配置  -  顺序 = 显示顺序
 * isAdmin=false 时,「注册用户」卡自动隐藏(普通用户无访问权)
 */
const stats = computed(() => {
  const baseStats = [
    {
      key: 'books',
      label: '馆藏图书',
      value: bookTotal.value,
      unit: '本',
      icon: 'Reading',
      tone: 'primary',
      loading: loading.value,
    },
  ]
  if (isAdmin.value) {
    baseStats.push({
      key: 'users',
      label: '注册用户',
      value: userTotal.value,
      unit: '人',
      icon: 'UserFilled',
      tone: 'neutral',
      loading: loading.value,
    })
  }
  baseStats.push(
    {
      key: 'role',
      label: '我的角色',
      value: roleLabel.value,
      unit: '',
      icon: 'Avatar',
      tone: 'neutral',
      loading: false,
    },
    {
      key: 'time',
      label: '系统日期',
      value: currentDate.value,
      unit: '',
      icon: 'Calendar',
      tone: 'neutral',
      loading: false,
    },
  )
  return baseStats
})

/** 当前日期(打开页面时显示,刷新会更新) */
const currentDate = computed<string>(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

/* ============================================================
 * 数据加载
 * ============================================================ */
async function fetchData(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    // 同时拉图书列表和用户列表(并行)
    const requests: [Promise<unknown>, Promise<unknown>?] = [
      bookApi.list({ page: 1, size: 5 }),
      isAdmin.value ? userApi.list({ page: 1, size: 1 }) : undefined,
    ] as const
    const results = await Promise.all(requests.filter(Boolean))
    const bookResult = results[0] as { total: number; data: BookDto[] }
    bookTotal.value = bookResult.total
    recentBooks.value = bookResult.data

    if (isAdmin.value && results[1]) {
      const userResult = results[1] as { total: number }
      userTotal.value = userResult.total
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)

/* ============================================================
 * 快捷操作
 * ============================================================ */
function goTo(path: string): void {
  router.replace(path)
}
</script>

<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <header class="page-head">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-sub">
          欢迎回来,<strong>{{ userStore.username || '游客' }}</strong>
        </p>
      </div>
      <el-button :loading="loading" @click="fetchData">刷新</el-button>
    </header>

    <!-- 错误状态 -->
    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <!-- ============================================================
     * 统计卡  -  大数字 + icon + 单位
     * ============================================================ -->
    <section class="stats-grid">
      <article
        v-for="stat in stats"
        :key="stat.key"
        class="stat-card"
        :class="`tone-${stat.tone}`"
      >
        <div class="stat-icon" aria-hidden="true">
          <el-icon :size="22"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <p class="stat-label">{{ stat.label }}</p>
          <div v-if="stat.loading" class="stat-skeleton">
            <el-skeleton-item variant="h1" style="width: 60%" />
          </div>
          <p v-else class="stat-value">
            <span class="stat-number">{{ stat.value }}</span>
            <span v-if="stat.unit" class="stat-unit">{{ stat.unit }}</span>
          </p>
        </div>
      </article>
    </section>

    <!-- ============================================================
     * 快捷操作
     * ============================================================ -->
    <section class="quick-actions">
      <h2 class="section-title">快捷操作</h2>
      <div class="action-grid">
        <button class="action-card" type="button" @click="goTo('/admin/books/new')">
          <div class="action-icon">
            <el-icon :size="20"><Plus /></el-icon>
          </div>
          <div class="action-text">
            <h3 class="action-name">新增图书</h3>
            <p class="action-desc">往馆藏添加一本新书</p>
          </div>
        </button>

        <button class="action-card" type="button" @click="goTo('/admin/purchases/new')">
          <div class="action-icon">
            <el-icon :size="20"><ShoppingCart /></el-icon>
          </div>
          <div class="action-text">
            <h3 class="action-name">立即下单</h3>
            <p class="action-desc">录入一笔新订单</p>
          </div>
        </button>

        <button
          v-if="isAdmin"
          class="action-card"
          type="button"
          @click="goTo('/admin/users')"
        >
          <div class="action-icon">
            <el-icon :size="20"><UserFilled /></el-icon>
          </div>
          <div class="action-text">
            <h3 class="action-name">用户管理</h3>
            <p class="action-desc">查看与管理账号</p>
          </div>
        </button>

        <button class="action-card" type="button" @click="goTo('/user/books')">
          <div class="action-icon">
            <el-icon :size="20"><Reading /></el-icon>
          </div>
          <div class="action-text">
            <h3 class="action-name">浏览图书</h3>
            <p class="action-desc">前往用户视图逛逛</p>
          </div>
        </button>
      </div>
    </section>

    <!-- ============================================================
     * 最近图书  -  top 5
     * ============================================================ -->
    <section class="recent-books">
      <header class="section-head">
        <h2 class="section-title">最近图书</h2>
        <el-button link type="primary" @click="goTo('/admin/books')">
          查看全部
        </el-button>
      </header>

      <el-skeleton v-if="loading" :rows="3" animated />

      <el-empty
        v-else-if="!loading && recentBooks.length === 0"
        description="暂无图书数据"
      />

      <ul v-else class="recent-list">
        <li
          v-for="(book, i) in recentBooks"
          :key="book.id"
          class="recent-item"
          :style="{ '--stagger-delay': `${i * 40}ms` }"
        >
          <span class="recent-rank">{{ String(i + 1).padStart(2, '0') }}</span>
          <div class="recent-info">
            <h3 class="recent-title">{{ book.title }}</h3>
            <p class="recent-meta">
              {{ book.author }} · ISBN {{ book.isbn }} · 出版 {{ formatDate(book.publishedDate) }}
            </p>
          </div>
          <div class="recent-price">
            <span class="price-value">{{ formatPrice(book.price) }}</span>
            <span class="recent-stock">
              库存 <strong>{{ book.stockQuantity ?? 0 }}</strong>
            </span>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1280px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

/* ============================================================
 * 页面标题
 * ============================================================ */
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
 * 统计卡  -  多层 shadow、tabular-nums、hover 浮起
 * ============================================================ */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: var(--color-card);
  border-radius: 14px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  transition:
    transform 220ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 8px 20px var(--color-shadow),
    0 0 0 1px var(--color-accent);
}

/* primary 强调态  -  左边 accent 条 */
.stat-card.tone-primary {
  position: relative;
  overflow: hidden;
}
.stat-card.tone-primary::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 3px;
  background: var(--color-accent);
  border-radius: 0 2px 2px 0;
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  background: var(--color-bg-alt);
  color: var(--color-text);
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-card.tone-primary .stat-icon {
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  margin: 0 0 8px;
}

.stat-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
  line-height: 1.1;  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-number {
  font-variant-numeric: tabular-nums;
}

.stat-unit {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-muted);
}

.stat-skeleton {
  margin-top: 4px;
}

/* ============================================================
 * 快捷操作  -  4 张卡,hover 浮起
 * ============================================================ */
.quick-actions {
  margin-bottom: 32px;
}

.section-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  margin: 0 0 16px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--color-card);
  border: none;
  border-radius: 12px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
  color: inherit;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  transition:
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 200ms cubic-bezier(0.22, 1, 0.36, 1),
    background-color 200ms ease;
}

.action-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 6px 16px var(--color-shadow),
    0 0 0 1px var(--color-accent);
}

.action-card:active {
  transform: translateY(0) scale(0.99);
}

.action-card:focus-visible {
  outline: none;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 2px var(--color-accent);
}

.action-icon {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  border-radius: 10px;
  flex-shrink: 0;
}

.action-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.action-name {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.action-desc {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

/* ============================================================
 * 最近图书列表  -  数字编号 + stagger 入场
 * ============================================================ */
.recent-books {
  background: var(--color-card);
  border-radius: 14px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  padding: 24px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}

.section-head .section-title {
  margin: 0;
}

.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.recent-item {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
  /* stagger 入场  -  由 CSS animation 触发(v-reveal 全局规则覆盖在 .reveal-up) */
  animation: stagger-in 360ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--stagger-delay, 0ms);
}

.recent-item:last-child {
  border-bottom: none;
}

@keyframes stagger-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.recent-rank {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-soft);
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
}

.recent-info {
  min-width: 0;
}

.recent-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 2px;
  color: var(--color-text);  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  font-size: 12px;
  color: var(--color-text-soft);
  margin: 0;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.price-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-accent);  font-variant-numeric: tabular-nums;
}

.recent-stock {
  font-size: 12px;
  color: var(--color-text-muted);
}

.recent-stock strong {
  color: var(--color-text);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* ============================================================
 * 响应式
 * ============================================================ */
@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .stat-value {
    font-size: 22px;
  }

  .recent-books {
    padding: 16px;
  }

  .recent-item {
    grid-template-columns: 32px 1fr;
    gap: 12px;
  }

  .recent-price {
    grid-column: 1 / -1;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
  }
}
</style>
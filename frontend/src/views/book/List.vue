<script setup lang="ts">
/**
 * book/List  -  图书列表(P5 接通版)
 *
 * 端点:GET /api/books/list?page=&size=
 * 后续可加:SearchBar 过滤 / 多条件搜索 / 行内编辑
 */

import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { bookApi } from '@/api/book'
import { formatPrice, formatDate } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import { useMediaQuery } from '@/composables/useMediaQuery'
import type { BookCategoryNode, BookDto, PageResult } from '@/types/api'

const router = useRouter()
const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<BookDto[]>([])
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

/* 窄屏:「操作」列不再固定在右侧。
   EP 的固定列是 sticky,吸附在表格内部滚动容器上,390px 宽的屏上
   一根 150px 的固定列会正好压在「标题」上面,把标题切掉半个字。 */
const isNarrow = useMediaQuery('(max-width: 768px)')

// ------------------------------------------------------------
// 分类:列表只返回 categoryId,名字要靠分类树翻译
// ------------------------------------------------------------
const categories = ref<BookCategoryNode[]>([])

/** categoryId → "大类 / 小类" 的查表 —— 分类总共几十条,一次展平比每行现查便宜。
 *
 *  大类与小类分开存,是为了渲染时能给大类降一档字号:
 *  "计算机 / 系统设计" 连在一起要 140px+,拆成小字大类 + 正常小类
 *  只要 ~90px,列宽省下来的部分让给书名。 */
const categoryLabel = computed<Map<number, { parent: string; child: string }>>(() => {
  const map = new Map<number, { parent: string; child: string }>()
  for (const parent of categories.value) {
    map.set(parent.id, { parent: parent.name, child: '' })
    for (const sub of parent.children ?? []) {
      map.set(sub.id, { parent: parent.name, child: sub.name })
    }
  }
  return map
})

// 参数只取用得上的字段:el-table 的插槽 row 是 DefaultRow(索引签名),直接收 BookDto 会类型不兼容
function catParts(row: { categoryId?: number | null }): { parent: string; child: string } | null {
  if (!row.categoryId) return null
  return categoryLabel.value.get(row.categoryId) ?? null
}

function catParent(row: { categoryId?: number | null }): string {
  return catParts(row)?.parent ?? ''
}

function catChild(row: { categoryId?: number | null }): string {
  return catParts(row)?.child ?? ''
}

function catTitle(row: { categoryId?: number | null }): string {
  const parts = catParts(row)
  if (!parts) return ''
  return parts.child ? `${parts.parent} / ${parts.child}` : parts.parent
}

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

/** 分类拉失败不影响列表本身 —— 分类那列显示空即可 */
async function fetchCategories(): Promise<void> {
  try {
    categories.value = await bookApi.listCategories()
  } catch {
    categories.value = []
  }
}

function goCreate(): void {
  router.replace('/admin/books/new')
}

function goEdit(isbn: string): void {
  router.replace(`/admin/books/${encodeURIComponent(isbn)}/edit`)
}

onMounted(() => {
  void fetchBooks()
  void fetchCategories()
})
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
      <AppSkeleton v-if="loading && items.length === 0" variant="table" :rows="6" :columns="9" />

      <el-table
        v-else
        :data="items"
        empty-text="暂无图书数据,点右上角新增"
        class="premium-table"
        :row-class-name="() => 'premium-row'"
      >
        <!-- 列宽按真实数据实测校准(books 12 行:标题最长 13 字/作者 16 字符/
             ISBN 13 位/分类 9 字),并只留「标题」一列弹性(min-width),
             其余按内容收紧,空出来的余量全给标题,长标题才有地方展开 -->
        <el-table-column prop="id" label="ID" width="40" />
        <el-table-column prop="title" label="标题" min-width="195">
          <template #default="{ row }">
            <span class="cell-title" :title="row.title">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="160">
          <template #default="{ row }">
            <span class="cell-ellipsis" :title="row.author">{{ row.author }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isbn" label="ISBN" width="136">
          <template #default="{ row }">
            <span class="cell-mono" :title="row.isbn">{{ row.isbn }}</span>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="78">
          <template #default="{ row }">
            <span class="cell-price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="48" align="right">
          <template #default="{ row }">
            <span
              class="cell-stock"
              :class="{ 'is-low': (row.stockQuantity ?? 0) <= 5 }"
            >
              {{ row.stockQuantity ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="124">
          <template #default="{ row }">
            <span v-if="catTitle(row)" class="cell-cat" :title="catTitle(row)">
              <span class="cat-parent">{{ catParent(row) }}</span>
              <span v-if="catChild(row)" class="cat-child">{{ catChild(row) }}</span>
            </span>
            <span v-else class="cell-cat is-none">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="出版日期" width="108">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDate(row.publishedDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="96"
          :fixed="isNarrow ? false : 'right'"
          align="center"
        >
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goEdit(row.isbn)">
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
  background: color-mix(in srgb, var(--el-color-danger) 8%, transparent);
  color: var(--el-color-danger);
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
  /* 行距:EP 默认 padding 只有 8px 上下,密得像电子表格;
     调到 12px 让每行有呼吸,又能一屏多看两行(行高 55 → 49) */
  padding: 12px 0;
}

/* 表头同步加厚一点,视觉上压得住下面的行 */
.premium-table :deep(.el-table__header-wrapper) th {
  padding: 12px 0;
}

.premium-table :deep(.el-table__row:last-child td) {
  border-bottom: none;
}

.premium-table :deep(.el-table__row:hover) td {
  background: var(--color-bg-alt);
}

/* 单元格样式 */
/* 长文本统一省略号:之前窄屏上「标题」是被切掉半个字,
   不是优雅地收尾 —— 显示 … 才知道「后面还有」 */
.cell-title,
.cell-ellipsis,
.cell-mono,
.cell-muted,
.cell-cat {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.cell-ellipsis {
  font-size: 14px;
  color: var(--color-text);
}

/* ISBN —— 13 位数字连排,加字距才不糊成一团(同订单号的处理)。
   字距 0.02em 而不是 0.05em:0.05em 时 13 位 ISBN 要 121px,
   比列宽多出来的部分只能省略号收尾 —— 编号被切一半就没法当编号用了。 */
.cell-mono {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.cell-muted {
  font-size: 13px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.cell-price {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.cell-stock {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
}

.cell-stock.is-low {
  color: var(--el-color-danger);
}

/* 分类 —— 「大类 / 小类」,大类弱化、小类正常,扫读时先看小类。
   大类用 11px 小字,一格能塞下整条路径,还省出宽度给书名。 */
.cell-cat {
  font-size: 13px;
  color: var(--color-text-soft);
}

.cell-cat .cat-parent {
  font-size: 11px;
  color: var(--color-text-muted);
}

.cell-cat .cat-child {
  margin-left: 5px;
  color: var(--color-text);
}

.cell-cat.is-none {
  color: var(--color-text-muted);
  font-style: italic;
}

/* ============================================================
 * 响应式  -  小屏:表格横向滚动
 * ============================================================ */
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
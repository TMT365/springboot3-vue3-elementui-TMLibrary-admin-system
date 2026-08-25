<script setup lang="ts">
/**
 * Mall  -  商城首页(公开页,无需登录)
 *
 * 拉真实 book 数据;多区块呈现:banner / 快速分类 / 编辑推荐 / 新书 / 特价
 */

import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { bookApi } from '@/api/book'
import type { BookDto, PageResult } from '@/types/api'

const route = useRoute()
const loading = ref(true)
const books = ref<BookDto[]>([])
const total = ref(0)
const error = ref<string | null>(null)

// 8 个一级分类 chip  -  跟 MallLayout 侧栏保持一致
const quickCategories = [
  { id: 'lit', label: '文学小说' },
  { id: 'cs', label: '计算机' },
  { id: 'history', label: '历史人文' },
  { id: 'phil', label: '哲学思辨' },
  { id: 'art', label: '艺术设计' },
  { id: 'biz', label: '商业经管' },
  { id: 'edu', label: '教育考试' },
  { id: 'kids', label: '少儿亲子' },
]

async function fetchBooks(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result: PageResult<BookDto> = await bookApi.list({ page: 1, size: 40 })
    books.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

const featured = computed(() => books.value[0] ?? null)
const picks = computed(() => books.value.slice(0, 8))
const arrivals = computed(() => books.value.slice(8, 16))
const deals = computed(() => books.value.slice(16, 24))

function formatPrice(p: string): string {
  return `¥${Number(p).toFixed(2)}`
}

function buyNow(book: BookDto): void {
  ElMessage.success(`已加入购物车:「${book.title}」`)
}

function goCategory(id: string): void {
  ElMessage.info(`分类 ${id} 跳转  -  后续接入 /mall/category/${id}`)
}

// 当前激活的分类(从 URL path 解析出 catId,用于 quick-cats chip 高亮)
const activeCategory = computed<string | null>(() => {
  const m = /^\/mall\/category\/([^/]+)/.exec(route.path)
  return m ? m[1] : null
})

onMounted(fetchBooks)
</script>

<template>
  <div class="mall">
    <!-- ============ Hero Banner ============ -->
    <section class="hero">
      <div class="hero-grid">
        <div class="hero-text">
          <span class="eyebrow">本周精选 · Editor's Pick</span>
          <h1 class="hero-title" v-if="featured">
            {{ featured.title }}
          </h1>
          <h1 class="hero-title hero-title-placeholder" v-else>
            从书架到生活, 让阅读重新变得郑重
          </h1>
          <p class="hero-author" v-if="featured">
            <span class="by">{{ featured.author }}</span>
            <span class="sep">·</span>
            <span class="isbn">ISBN {{ featured.isbn }}</span>
          </p>
          <p class="hero-blurb">
            每一本馆藏, 都由我们亲手翻阅、挑剔、留下。
            这里是 TMLibrary 的「卷中世界」 -  - 
            从古典的灰尘味, 到算法的冷光,
            你想要的下一本书, 一直都在。
          </p>
          <div class="hero-actions">
            <button class="btn-primary" @click="featured && buyNow(featured)">
              加入购物车
            </button>
            <button class="btn-ghost">浏览全部</button>
          </div>
          <div class="hero-stats">
            <span><strong>{{ total }}</strong> 册馆藏</span>
            <span class="dot" />
            <span><strong>8</strong> 个分类</span>
            <span class="dot" />
            <span><strong>3</strong> 级权限</span>
          </div>
        </div>
        <div class="hero-visual" aria-hidden="true">
          <div class="book-stack">
            <div class="book-card-stack book-1" />
            <div class="book-card-stack book-2" />
            <div class="book-card-stack book-3" />
            <div class="book-card-stack book-4" />
          </div>
          <div class="hero-mark">卷</div>
        </div>
      </div>
    </section>

    <!-- ============ Quick categories ============ -->
    <section class="quick-cats" aria-label="快速分类">
      <div class="cat-row">
        <button
          v-for="cat in quickCategories"
          :key="cat.id"
          class="cat-chip"
          :class="{ 'is-active': activeCategory === cat.id }"
          @click="goCategory(cat.id)"
        >
          {{ cat.label }}
        </button>
      </div>
    </section>

    <!-- ============ Editor's Picks ============ -->
    <section class="section">
      <header class="section-head">
        <h2 class="section-title">编辑推荐</h2>
        <p class="section-sub">馆员手工挑选, 不只是畅销</p>
      </header>

      <div v-if="error" class="error-banner">
        <el-icon><CircleClose /></el-icon>
        <span>{{ error }}</span>
      </div>

      <el-skeleton v-else-if="loading" :rows="2" animated>
        <template #template>
          <div class="grid-skel">
            <el-skeleton-item v-for="i in 8" :key="i" variant="rect" style="height: 320px; border-radius: 14px;" />
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="picks.length === 0" class="empty-block">
        <el-empty description="暂无推荐" />
      </div>

      <div v-else class="book-grid">
        <article v-for="book in picks" :key="book.id" class="book-card">
          <div class="cover">
            <span class="cover-mark">{{ book.title.slice(0, 1) }}</span>
            <span class="cover-badge">编辑推荐</span>
          </div>
          <div class="info">
            <h3 class="title">{{ book.title }}</h3>
            <p class="author">{{ book.author }}</p>
            <div class="meta">
              <span class="price">{{ formatPrice(book.price) }}</span>
              <span class="stock">库存 {{ book.stockQuantity ?? 0 }}</span>
            </div>
            <button class="buy" @click="buyNow(book)">加入购物车</button>
          </div>
        </article>
      </div>
    </section>

    <!-- ============ Just Arrived ============ -->
    <section class="section">
      <header class="section-head">
        <h2 class="section-title">本周新书</h2>
        <p class="section-sub">新到的, 值得先翻</p>
      </header>

      <div v-if="!loading && arrivals.length > 0" class="arrivals-list">
        <article v-for="book in arrivals" :key="book.id" class="arrival-row">
          <div class="arrival-cover">
            <span>{{ book.title.slice(0, 1) }}</span>
          </div>
          <div class="arrival-info">
            <h3 class="arrival-title">{{ book.title }}</h3>
            <p class="arrival-author">{{ book.author }} · ISBN {{ book.isbn }}</p>
          </div>
          <div class="arrival-price">{{ formatPrice(book.price) }}</div>
          <button class="buy-sm" @click="buyNow(book)">加入购物车</button>
        </article>
      </div>

      <el-empty v-else-if="!loading" description="暂无新书" />
    </section>

    <!-- ============ Specials ============ -->
    <section class="section">
      <header class="section-head">
        <h2 class="section-title">限时特价</h2>
        <p class="section-sub">每周精选 · 价格直降</p>
      </header>

      <div v-if="!loading && deals.length > 0" class="deal-grid">
        <article v-for="book in deals" :key="book.id" class="deal-card">
          <div class="deal-cover">
            <span>{{ book.title.slice(0, 1) }}</span>
            <span class="deal-flag">−20%</span>
          </div>
          <div class="deal-body">
            <h3 class="deal-title">{{ book.title }}</h3>
            <p class="deal-author">{{ book.author }}</p>
            <div class="deal-prices">
              <span class="deal-now">{{ formatPrice(book.price) }}</span>
              <span class="deal-was">¥{{ (Number(book.price) * 1.25).toFixed(2) }}</span>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else-if="!loading" description="暂无特价" />
    </section>

    <!-- ============ Quote footer ============ -->
    <section class="quote">
      <blockquote>
        <p>"A reader lives a thousand lives before he dies. The man who never reads lives only one."</p>
        <cite> -  George R.R. Martin</cite>
      </blockquote>
    </section>
  </div>
</template>

<style scoped>
.mall {
  max-width: 1200px;
  margin: 0 auto;
}

/* ============ Hero ============ */
.hero {
  position: relative;
  margin-bottom: 56px;
}

.hero-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 56px;
  align-items: center;
  padding: 48px 40px;
  background: linear-gradient(
    135deg,
    var(--color-card) 0%,
    var(--color-bg-alt) 100%
  );
  border: 1px solid var(--color-border);
  border-radius: 24px;
  overflow: hidden;
}

.hero-grid::before {
  content: '';
  position: absolute;
  top: 50%;
  right: -80px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(76, 175, 80, 0.08) 0%, transparent 70%);
  pointer-events: none;
}

.eyebrow {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 20px;
}

.hero-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(36px, 4.5vw, 56px);
  font-weight: 400;
  line-height: 1.1;
  letter-spacing: -0.025em;
  color: var(--color-text);
  margin: 0 0 16px;}

.hero-title-placeholder {
  color: var(--color-text);
  max-width: 12em;
}

.hero-author {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 24px;
}

.hero-author .sep {
  color: var(--color-text-soft);
}

.hero-blurb {
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-text-muted);
  margin: 0 0 32px;
  max-width: 480px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 36px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: var(--color-accent);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  font-family: 'Manrope', system-ui, sans-serif;
  transition: background 200ms ease, transform 200ms ease;
}

.btn-primary:hover {
  background: var(--color-accent-hover);
  transform: translateY(-1px);
}

.btn-primary .arrow {
  transition: transform 200ms ease;
}

.btn-primary:hover .arrow {
  transform: translateX(3px);
}

.btn-ghost {
  padding: 14px 24px;
  background: transparent;
  color: var(--color-text);
  font-size: 15px;
  font-weight: 500;
  border: 1.5px solid var(--color-text);
  border-radius: 999px;
  cursor: pointer;
  font-family: 'Manrope', system-ui, sans-serif;
  transition: background 200ms ease, color 200ms ease;
}

.btn-ghost:hover {
  background: var(--color-text);
  color: var(--color-card);
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-soft);
}

.hero-stats strong {
  color: var(--color-accent);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  margin-right: 2px;}

.hero-stats .dot {
  width: 3px;
  height: 3px;
  background: var(--color-text-soft);
  border-radius: 50%;
}

.hero-visual {
  position: relative;
  height: 380px;
}

.book-stack {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}

.book-card-stack {
  position: absolute;
  width: 180px;
  height: 240px;
  border-radius: 6px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.6) inset,
    0 30px 60px rgba(0, 0, 0, 0.18);
}

.book-1 {
  background: linear-gradient(135deg, #f4f1ea 0%, #e8e5dc 100%);
  transform: translate(-80px, 40px) rotate(-12deg);
  z-index: 1;
}

.book-2 {
  background: linear-gradient(135deg, #fefcf8 0%, #f0ebe0 100%);
  transform: translate(-30px, -10px) rotate(-4deg);
  z-index: 2;
}

.book-3 {
  background: linear-gradient(135deg, #ffffff 0%, #faf8f3 100%);
  transform: translate(40px, -30px) rotate(6deg);
  z-index: 3;
  border-color: var(--color-accent);
}

.book-4 {
  background: linear-gradient(135deg, var(--color-accent) 0%, #2e7d32 100%);
  transform: translate(90px, 30px) rotate(15deg);
  z-index: 4;
  border: none;
}

.hero-mark {
  position: absolute;
  top: 24px;
  left: 24px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 88px;
  font-weight: 400;
  color: var(--color-accent);
  z-index: 5;  line-height: 1;
}

/* ============ Quick categories ============ */
.quick-cats {
  margin-bottom: 56px;
}

.cat-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.cat-chip {
  flex-shrink: 0;
  padding: 10px 20px;
  background: var(--color-card);
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 220ms cubic-bezier(0.4, 0, 0.2, 1);
}

.cat-chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: rgba(76, 175, 80, 0.06);
  transform: translateY(-1px);
}

.cat-chip.is-active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.3);
}

/* ============ Section (shared) ============ */
.section {
  margin-bottom: 64px;
}

.section-head {
  margin-bottom: 32px;
}

.section-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 8px;
}

.section-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(28px, 3.5vw, 40px);
  font-weight: 700;
  letter-spacing: -0.025em;
  color: var(--color-text);
  margin: 0 0 6px;}

.section-sub {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.08);
  color: #f56c6c;
  border-radius: 8px;
}

.empty-block {
  padding: 48px 0;
  text-align: center;
}

.grid-skel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

/* ============ Book grid (Editor's picks) ============ */
.book-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.book-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  overflow: hidden;
  transition: transform 250ms ease, box-shadow 250ms ease, border-color 250ms ease;
  display: flex;
  flex-direction: column;
}

.book-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 36px var(--color-shadow);
  border-color: var(--color-accent);
}

.cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, var(--color-bg-alt) 0%, var(--color-border) 100%);
  display: grid;
  place-items: center;
}

.cover-mark {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 48px;
  color: var(--color-text-soft);}

.cover-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 10px;
  background: var(--color-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: 999px;
}

.info {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;}

.author {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.meta {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 4px;
}

.price {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-accent);}

.stock {
  font-size: 11px;
  color: var(--color-text-soft);
}

.buy {
  width: 100%;
  padding: 10px 0;
  background: transparent;
  color: var(--color-accent);
  font-size: 13px;
  font-weight: 600;
  border: 1px solid var(--color-accent);
  border-radius: 8px;
  cursor: pointer;
  margin-top: 8px;
  font-family: 'Manrope', system-ui, sans-serif;
  transition: background 200ms ease, color 200ms ease;
}

.buy:hover {
  background: var(--color-accent);
  color: #fff;
}

/* ============ Arrivals list ============ */
.arrivals-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.arrival-row {
  display: grid;
  grid-template-columns: 56px 1fr auto auto;
  gap: 20px;
  align-items: center;
  padding: 14px 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  transition: border-color 200ms ease, transform 200ms ease;
}

.arrival-row:hover {
  border-color: var(--color-accent);
  transform: translateX(4px);
}

.arrival-cover {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--color-bg-alt) 0%, var(--color-border) 100%);
  border-radius: 8px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  color: var(--color-text-soft);}

.arrival-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--color-text);}

.arrival-author {
  font-size: 12px;
  color: var(--color-text-soft);
  margin: 0;
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
}

.arrival-price {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-accent);}

.buy-sm {
  padding: 8px 16px;
  background: var(--color-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-family: 'Manrope', system-ui, sans-serif;
  transition: background 200ms ease;
}

.buy-sm:hover {
  background: var(--color-accent-hover);
}

/* ============ Deals ============ */
.deal-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.deal-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  overflow: hidden;
  transition: transform 250ms ease, box-shadow 250ms ease;
  position: relative;
}

.deal-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px var(--color-shadow);
}

.deal-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, #fef7e8 0%, #fde9c8 100%);
  display: grid;
  place-items: center;
}

.deal-cover > span:first-child {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 48px;
  color: #b8860b;}

.deal-flag {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px 10px;
  background: #d9534f;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  border-radius: 999px;
}

.deal-body {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.deal-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;}

.deal-author {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.deal-prices {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 4px;
}

.deal-now {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  color: #d9534f;}

.deal-was {
  font-size: 13px;
  color: var(--color-text-soft);
  text-decoration: line-through;
}

/* ============ Quote ============ */
.quote {
  margin: 64px 0 32px;
  padding: 48px 40px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  text-align: center;
}

.quote blockquote {
  margin: 0;
}

.quote p {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(20px, 2.5vw, 28px);
  font-weight: 400;
  font-style: italic;
  line-height: 1.5;
  color: var(--color-text);
  margin: 0 0 16px;
  max-width: 720px;
  margin-left: auto;
  margin-right: auto;}

.quote cite {
  font-size: 13px;
  color: var(--color-text-soft);
  font-style: normal;
  letter-spacing: 0.04em;
}

/* ============ Responsive ============ */
@media (max-width: 1100px) {
  .book-grid,
  .deal-grid,
  .grid-skel {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 32px 24px;
  }
  .hero-visual {
    height: 240px;
  }
  .book-grid,
  .deal-grid,
  .grid-skel {
    grid-template-columns: repeat(2, 1fr);
  }
  .arrival-row {
    grid-template-columns: 48px 1fr auto;
  }
  .arrival-row .buy-sm {
    grid-column: 1 / -1;
    justify-self: end;
  }
}
</style>
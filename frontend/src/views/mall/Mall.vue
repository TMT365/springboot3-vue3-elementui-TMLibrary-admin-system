<script setup lang="ts">
/**
 * Mall  -  商城首页(公开页,无需登录)
 *
 * 拉真实 book 数据;多区块呈现:banner / 快速分类 / 编辑推荐 / 新书 / 特价
 */

import { ref, onMounted, watch, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bookApi } from '@/api/book'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { highlightParts, useBookSuggest } from '@/composables/useBookSuggest'
import { useCartStore } from '@/stores/cart'
import type { BookCategoryNode, BookDto, BookSuggestion, PageResult } from '@/types/api'

const route = useRoute()
const router = useRouter()

/**
 * 顶栏的搜索框在手机上被 MallLayout 隐藏了(那一行放不下 brand + 搜索 + 操作区),
 * 所以内容区最上面、hero 之前再放一个,断点跟 MallLayout 保持一致。
 */
const isMobile = useMediaQuery('(max-width: 900px)')
const loading = ref(true)
const books = ref<BookDto[]>([])
const total = ref(0)
/** 全量馆藏数 —— 只在"没筛分类"的那次请求里更新,免得点进分类后 Hero 上的馆藏数跟着缩水 */
const allTotal = ref(0)
const error = ref<string | null>(null)

// 一级分类 chip  -  来自后端分类树(与 MallLayout 侧栏同源)
const quickCategories = ref<BookCategoryNode[]>([])

/** 从路由解析当前分类:/mall/category/:catId/:subId? —— 有小类就用小类筛 */
const currentCategoryId = computed<number | null>(() => {
  const m = /^\/mall\/category\/(\d+)(?:\/(\d+))?/.exec(route.path)
  if (!m) return null
  return Number(m[2] ?? m[1])
})

/**
 * 当前搜索词 —— 存在 URL 的 ?q= 上(顶栏搜索框和手机端搜索框共用这一个源)。
 */
const currentKeyword = computed<string>(() => {
  const q = route.query.q
  return typeof q === 'string' ? q.trim() : ''
})

/** 有没有生效中的筛选(分类 或 搜索)—— 决定显示结果区还是默认三区块 */
const hasFilter = computed<boolean>(
  () => currentCategoryId.value !== null || currentKeyword.value !== '',
)

/** 手机端搜索框的内容 —— 跟随 URL,从顶栏搜索后切到窄屏也能看到当前词 */
const searchInput = ref<string>('')
watch(
  currentKeyword,
  (q) => {
    searchInput.value = q
  },
  { immediate: true },
)

/** 当前分类名,给筛选提示条用 */
const currentCategoryName = computed<string>(() => {
  const id = currentCategoryId.value
  if (id === null) return ''
  for (const cat of quickCategories.value) {
    if (cat.id === id) return cat.name
    const sub = (cat.children ?? []).find((s) => s.id === id)
    if (sub) return `${cat.name} / ${sub.name}`
  }
  return ''
})

async function loadCategories(): Promise<void> {
  try {
    quickCategories.value = await bookApi.listCategories()
  } catch {
    quickCategories.value = []
  }
}

async function fetchBooks(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    // 有分类就带上 categoryId(后端传大类时会连同子类一起命中),
    // 有关键字就带 keyword(后端按 书名/作者/ISBN 任一 模糊匹配),都没有就是全量
    const result: PageResult<BookDto> = await bookApi.multiSearch({
      page: 1,
      size: 40,
      categoryId: currentCategoryId.value ?? undefined,
      keyword: currentKeyword.value || undefined,
    })
    books.value = result.data
    total.value = result.total
    // 未筛选的那次顺便校准馆藏总数(增删书后不用刷新页面也能对上)
    if (currentCategoryId.value === null) allTotal.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 全站馆藏数 —— 单独拉一次(size=1 只取 total,不拉数据行)。
 * 不能只靠"未筛选那次请求"顺便记:直接从 /mall/category/2 进来时压根没发过全量请求,
 * Hero 上的馆藏数和「全部」chip 的数字就都是 0。
 */
async function fetchTotal(): Promise<void> {
  try {
    const result = await bookApi.multiSearch({ page: 1, size: 1 })
    allTotal.value = result.total
  } catch {
    // 拿不到就不显示数字,不影响浏览
  }
}

const featured = computed(() => books.value[0] ?? null)
const picks = computed(() => books.value.slice(0, 8))
const arrivals = computed(() => books.value.slice(8, 16))
const deals = computed(() => books.value.slice(16, 24))

function formatPrice(p: number): string {
  return `¥${Number(p).toFixed(2)}`
}

const cartStore = useCartStore()

/**
 * 加入购物车。
 *
 * 和 UserBooks.vue 的 handleAddToCart 保持同一套行为:
 *   - 库存 <= 0 → warning,不加
 *   - 首次加入   → 「已加入购物车」
 *   - 已在车里   → 「购物车中数量 +1」
 *
 * 之前这个方法只弹了一句「已加入购物车」就结束了,`cartStore.addItem()` 压根没被调用 ——
 * 点了等于没点,购物车永远是空的(提示还是假的)。
 */
function addToCart(book: BookDto): void {
  if ((book.stockQuantity ?? 0) <= 0) {
    ElMessage.warning('库存不足,无法加入')
    return
  }
  const result = cartStore.addItem(book)
  ElMessage.success(
    result === 'added' ? `已加入购物车:「${book.title}」` : '购物车中数量 +1',
  )
}

/** 点 chip 真的跳分类页;再点一次当前分类 = 取消筛选 */
function goCategory(cat: BookCategoryNode): void {
  if (currentCategoryId.value === cat.id) {
    router.replace('/mall')
    return
  }
  router.replace(`/mall/category/${cat.id}`)
}

function clearCategory(): void {
  router.replace('/mall')
}

/** 结果区 —— 手机端搜完要滚到它,不然结果全在 hero 下面看不见 */
const resultsRef = ref<HTMLElement | null>(null)

const { fetchSuggestions } = useBookSuggest()

/** 选中候选词后搜索 —— 并把结果滚进视野 */
async function submitSearch(): Promise<void> {
  const q = searchInput.value.trim()
  await router.replace({ path: '/mall', query: q ? { q } : {} })
  // hero 在手机上占一屏多,不滚一下的话搜完像是什么都没发生
  if (!isMobile.value) return
  await nextTick()
  resultsRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 同 MallLayout:挡住"↑↓ 选中回车"时 @select 与 @keyup.enter 双触发 */
let lastSelectAt = 0

// 参数类型跟着 EP 走(见 MallLayout 同名函数说明)
function onSelectSuggestion(item: Record<string, unknown>): void {
  lastSelectAt = Date.now()
  searchInput.value = String(item.text ?? '')
  void submitSearch()
}

function onSearchEnter(): void {
  if (Date.now() - lastSelectAt < 300) return
  void submitSearch()
}

/** 清掉分类 + 关键字,回到全量 */
function clearFilters(): void {
  searchInput.value = ''
  router.replace('/mall')
}

/** 分类或搜索词变化 → 重新拉书(同一个组件实例,不会重新 mount,watch path 抓不到 query) */
watch(
  () => route.fullPath,
  () => {
    void fetchBooks()
  },
)

onMounted(async () => {
  void fetchTotal()
  // 分类树先到,chip 的高亮/名字才有得查
  await loadCategories()
  await fetchBooks()
})
</script>

<template>
  <div class="mall">
    <!-- ============ 手机端搜索 ============
         顶栏那一行在手机上放不下搜索框(MallLayout 里 .search 是 display:none),
         所以在内容区最上面、hero 之前补一个。放在文档流里而不是绝对定位 ——
         绝对定位要么压住 hero 顶部,要么得给 hero 补 padding,滚动时还容易和
         吸顶的 topbar 打架;这里是页面第一个元素,跟着滚反而自然。 -->
    <div v-if="isMobile" class="mobile-search">
      <!-- 和顶栏同一个 composable:防抖 + 竞态防护都在里面。
           行内「搜索」按钮去掉了 —— 下拉候选 + 回车已经是更顺的路径,
           再加个按钮在窄屏上只会挤掉输入框的宽度 -->
      <el-autocomplete
        v-model="searchInput"
        :fetch-suggestions="fetchSuggestions"
        :debounce="280"
        :trigger-on-focus="false"
        value-key="text"
        placeholder="搜索 书名 / 作者 / ISBN"
        size="large"
        clearable
        popper-class="book-suggest-popper"
        class="search-autocomplete"
        @select="onSelectSuggestion"
        @keyup.enter="onSearchEnter"
        @clear="clearFilters"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #default="{ item }">
          <div class="sug">
            <el-icon class="sug-icon">
              <User v-if="item.type === 'AUTHOR'" />
              <Postcard v-else-if="item.type === 'ISBN'" />
              <Reading v-else />
            </el-icon>
            <span class="sug-text">
              <template v-for="(part, i) in highlightParts(item.text, searchInput)" :key="i">
                <em v-if="part.hit" class="sug-hit">{{ part.text }}</em>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <span v-if="item.hot > 0" class="sug-hot">销量 {{ item.hot }}</span>
          </div>
        </template>
      </el-autocomplete>
    </div>

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
            <button class="btn-primary" @click="featured && addToCart(featured)">
              加入购物车
            </button>
            <button class="btn-ghost">浏览全部</button>
          </div>
          <div class="hero-stats">
            <span><strong>{{ allTotal }}</strong> 册馆藏</span>
            <span class="dot" />
            <span><strong>{{ quickCategories.length }}</strong> 个分类</span>
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
        <!-- 「全部」置顶:没有它的话,点进某个分类后就只剩"再点一次当前 chip"这种隐形操作才能退出 -->
        <button
          class="cat-chip is-all"
          :class="{ 'is-active': !hasFilter }"
          @click="clearFilters"
        >
          全部
          <span v-if="allTotal > 0" class="chip-count">{{ allTotal }}</span>
        </button>
        <button
          v-for="cat in quickCategories"
          :key="cat.id"
          class="cat-chip"
          :class="{ 'is-active': currentCategoryId === cat.id }"
          @click="goCategory(cat)"
        >
          {{ cat.name }}
          <span class="chip-count">{{ cat.bookCount }}</span>
        </button>
      </div>
    </section>

    <!-- ============ 筛选结果(分类 或 搜索)============ -->
    <section v-if="hasFilter" ref="resultsRef" class="section">
      <header class="section-head">
        <h2 class="section-title">
          <template v-if="currentKeyword">搜索「{{ currentKeyword }}」</template>
          <template v-else>{{ currentCategoryName || '分类图书' }}</template>
        </h2>
        <p class="section-sub">
          共 <strong class="sub-count">{{ total }}</strong> 本
          <button class="link-btn" @click="clearFilters">查看全部图书</button>
        </p>
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

      <div v-else-if="books.length === 0" class="empty-block">
        <el-empty
          :description="currentKeyword ? `没有找到与「${currentKeyword}」相关的图书` : '这个分类下还没有图书'"
        />
      </div>

      <div v-else class="book-grid">
        <article v-for="book in books" :key="book.id" class="book-card">
          <div class="cover">
            <span class="cover-mark">{{ book.title.slice(0, 1) }}</span>
            <span class="cover-badge">{{ currentKeyword ? '搜索结果' : (currentCategoryName || '分类') }}</span>
          </div>
          <div class="info">
            <h3 class="title">{{ book.title }}</h3>
            <p class="author">{{ book.author }}</p>
            <div class="meta">
              <span class="price">{{ formatPrice(book.price) }}</span>
              <span class="stock">库存 {{ book.stockQuantity ?? 0 }}</span>
            </div>
            <button class="buy" @click="addToCart(book)">加入购物车</button>
          </div>
        </article>
      </div>
    </section>

    <!-- ============ 默认首页三区块(未筛分类时才显示)============ -->
    <template v-else>

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
            <button class="buy" @click="addToCart(book)">加入购物车</button>
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
          <button class="buy-sm" @click="addToCart(book)">加入购物车</button>
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

    </template>

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

/* ============ 手机端搜索(hero 之前)============ */
.mobile-search {
  margin-bottom: 18px;
}

.mobile-search .search-autocomplete {
  width: 100%;
}

.mobile-search :deep(.el-input__wrapper) {
  background: var(--color-card);
  border-radius: 12px;
  padding: 6px 14px;
  box-shadow: 0 0 0 1px var(--color-border);
}

.mobile-search :deep(.el-input__inner) {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 15px;
}

/* ============ Quick categories ============ */
.quick-cats {
  margin-bottom: 56px;
}

/* overflow-x: auto 会顺带把 overflow-y 变成 hidden(clipping),
   chip hover 时 translateY(-1px) 抬起的那 1px + 阴影就被上方区块切掉了,
   看起来像"被上面的 div 遮住"。上下留出空间即可 —— 顺便让横向滚动条不贴边。 */
.cat-row {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding: 8px 2px 10px;
  scrollbar-width: thin;
}

.cat-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 10px 20px;
  background: var(--color-card);
  color: var(--color-text);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  cursor: pointer;
  transition: all 220ms cubic-bezier(0.4, 0, 0.2, 1);
}

/* 「全部」用一条竖分隔线和后面的分类 chip 拉开,视觉上表明它不是一个分类 */
.cat-chip.is-all {
  position: relative;
  margin-right: 6px;
  font-weight: 600;
  border-color: var(--color-text-soft);
}

.cat-chip.is-all::after {
  content: '';
  position: absolute;
  right: -10px;
  top: 20%;
  bottom: 20%;
  width: 1px;
  background: var(--color-border);
}

/* chip 上的数量 */
.chip-count {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 11.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  padding: 4px 7px;
  border-radius: 999px;
  background: var(--color-bg-alt);
  color: var(--color-text-muted);
  transition: inherit;
}

.cat-chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: rgba(76, 175, 80, 0.06);
  transform: translateY(-2px);
}

.cat-chip:hover .chip-count {
  background: rgba(76, 175, 80, 0.16);
  color: var(--color-accent);
}

.cat-chip.is-active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
  box-shadow: 0 4px 12px rgba(76, 175, 80, 0.3);
}

.cat-chip.is-active .chip-count {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

/* 筛选结果标题里的「查看全部」 */
.sub-count {
  color: var(--color-accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.link-btn {
  margin-left: 10px;
  padding: 0;
  background: none;
  border: none;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-accent);
  cursor: pointer;
  border-bottom: 1px solid transparent;
  transition: border-color 200ms ease;
}

.link-btn:hover {
  border-bottom-color: var(--color-accent);
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
    gap: 24px;
    padding: 28px 20px;
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

  /* 引言块:48/40 的内边距在手机上占掉一半宽度,文字被挤成窄条 */
  .quote {
    margin: 40px 0 24px;
    padding: 28px 18px;
    border-radius: 16px;
  }

  .section {
    margin-bottom: 44px;
  }

  .section-head {
    margin-bottom: 22px;
  }

  .quick-cats {
    margin-bottom: 36px;
  }
}

/* 窄屏手机(≤480):保持两列(单列一张卡占满整屏太大),把卡片整体缩小一档 */
@media (max-width: 480px) {
  .book-grid,
  .deal-grid,
  .grid-skel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  /* ---- 书卡瘦身:封面压扁、内边距减半、字号各降一档 ---- */
  .book-card,
  .deal-card {
    border-radius: 11px;
  }

  .cover,
  .deal-cover {
    aspect-ratio: 3 / 2;
  }

  .cover-mark,
  .deal-cover > span:first-child {
    font-size: 30px;
  }

  /* 分类结果页的角标可能很长(「计算机 / 编程语言」),窄卡上必须能省略 */
  .cover-badge {
    top: 7px;
    left: 7px;
    max-width: calc(100% - 14px);
    padding: 2px 7px;
    font-size: 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .deal-flag {
    top: 7px;
    right: 7px;
    padding: 2px 6px;
    font-size: 10px;
  }

  .info,
  .deal-body {
    padding: 9px 10px 11px;
    gap: 4px;
  }

  .title,
  .deal-title {
    font-size: 13px;
  }

  .author,
  .deal-author {
    font-size: 11px;
  }

  .meta,
  .deal-prices {
    margin-top: 2px;
  }

  .price,
  .deal-now {
    font-size: 15px;
  }

  .stock,
  .deal-was {
    font-size: 10px;
  }

  .buy {
    padding: 7px 0;
    margin-top: 5px;
    font-size: 12px;
    border-radius: 7px;
  }

  /* ---- Hero ---- */
  .hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-stats {
    flex-wrap: wrap;
    gap: 8px 14px;
  }

  /* 书堆是绝对定位的装饰,375px 下会被 .hero-grid 的 overflow: hidden 裁掉两侧 ——
     小屏干脆收掉,别露半个书脊 */
  .hero-visual {
    display: none;
  }

  /* 本周新书 / 特价的行式卡片同步收紧 */
  .arrival-row {
    grid-template-columns: 40px 1fr auto;
    gap: 12px;
    padding: 11px 13px;
  }

  .arrival-cover {
    width: 40px;
    height: 40px;
    font-size: 18px;
  }

  .arrival-title {
    font-size: 14px;
  }

  .arrival-price {
    font-size: 16px;
  }
}
</style>
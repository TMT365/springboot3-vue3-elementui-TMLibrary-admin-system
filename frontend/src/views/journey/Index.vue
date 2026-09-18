<script setup lang="ts">
/**
 * 学习经历 —— 项目开发过程中真实踩过的坑。
 *
 * <h2>版式</h2>
 * <p>两栏:左侧粘性目录(锚点导航 + 滚动高亮),右侧正文卡片。
 * 正文按"症状 → 根因 → 解法 → 收获"四段式 —— 这是排查问题的自然顺序,
 * 也是读者最容易代入的顺序(先看到现象,再理解原因)。</p>
 *
 * <h2>为什么用 IntersectionObserver 而不是监听 scroll</h2>
 * <p>scroll 事件在滚动时每帧都触发,里面还要读 `getBoundingClientRect()` —— 会强制
 * 同步布局,长页面上明显掉帧。IntersectionObserver 由浏览器在空闲时批量回调,
 * 不阻塞滚动。</p>
 *
 * <h2>目录为什么用按钮而不是 router-link</h2>
 * <p>目录是页内锚点,不改 URL 路由 —— 用 `scrollIntoView` 保持 URL 干净,
 * 而且能控制滚动偏移(吸顶栏会挡住标题,要减掉那个高度)。</p>
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CATEGORY_META, JOURNEY_ENTRIES } from './entries'
import type { JourneyCategory, JourneyEntry } from './entries'

const router = useRouter()

/** 当前高亮的章节 —— IntersectionObserver 回调里更新 */
const activeId = ref<string>('')
/** 手机端目录折叠状态 —— 桌面上目录是常驻的,这个值不起作用 */
const tocOpen = ref<boolean>(false)

/** 按分类分组给目录用 —— 目录里分三块,不是一条平铺的列表 */
const groupedEntries = computed(() => {
  const groups: { category: JourneyCategory; entries: JourneyEntry[] }[] = []
  for (const key of Object.keys(CATEGORY_META) as JourneyCategory[]) {
    const entries = JOURNEY_ENTRIES.filter((e) => e.category === key)
    if (entries.length > 0) {
      groups.push({ category: key, entries })
    }
  }
  return groups
})

/** 统计每个分类有几条 —— Hero 区展示 */
const stats = computed(() =>
  (Object.keys(CATEGORY_META) as JourneyCategory[]).map((key) => ({
    key,
    ...CATEGORY_META[key],
    count: JOURNEY_ENTRIES.filter((e) => e.category === key).length,
  })),
)

/**
 * 滚到某个章节。
 *
 * offset:吸顶的导航栏高度(约 72px)+ 一点余量 —— 不减掉的话
 * 标题会被顶栏盖住,看起来像"跳错了位置"。
 */
function scrollTo(id: string): void {
  const el = document.getElementById(id)
  if (!el) return
  const top = el.getBoundingClientRect().top + window.scrollY - 88
  window.scrollTo({ top, behavior: 'smooth' })
  activeId.value = id
  tocOpen.value = false // 手机上选完自动收起
}

// ============================================================
// 滚动高亮:IntersectionObserver
// ============================================================
let observer: IntersectionObserver | null = null

onMounted(() => {
  // rootMargin 顶部留 -100px:让"刚滚过吸顶栏的那一条"成为激活项,
  // 否则高亮总是慢半拍(元素刚进入视口底部就高亮了)
  observer = new IntersectionObserver(
    (entries) => {
      // 取当前所有可见条目里最靠上的那个 —— 快速滚动时会同时有多个可见
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
      if (visible.length > 0) {
        activeId.value = visible[0].target.id
      }
    },
    { rootMargin: '-100px 0px -60% 0px', threshold: 0 },
  )

  for (const entry of JOURNEY_ENTRIES) {
    const el = document.getElementById(entry.id)
    if (el) observer.observe(el)
  }
})

onBeforeUnmount(() => {
  // ★ 必须断开:否则组件卸载后 observer 还持有 DOM 引用,内存泄漏
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="journey">
    <!-- ============ 返回入口 ============ -->
    <header class="journey-top">
      <button class="back-btn" type="button" @click="router.push('/')">
        <el-icon><ArrowLeft /></el-icon>
        返回首页
      </button>
    </header>

    <!-- ============ Hero ============ -->
    <section class="hero">
      <span class="eyebrow">Learning Journey</span>
      <h1 class="hero-title">开发这个项目,我踩过的坑</h1>
      <p class="hero-sub">
        共 <strong>{{ JOURNEY_ENTRIES.length }}</strong> 条真实经历 ——
        每条都写清楚"看到什么现象、为什么这样、最后怎么解决"。
        不写正确但没营养的总结,只写下次遇到能少走弯路的东西。
      </p>

      <div class="hero-stats">
        <div v-for="s in stats" :key="s.key" class="stat" :style="{ '--c': s.color }">
          <el-icon class="stat-icon"><component :is="s.icon" /></el-icon>
          <div class="stat-text">
            <span class="stat-num">{{ s.count }}</span>
            <span class="stat-label">{{ s.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 手机端目录开关 ============ -->
    <button class="toc-toggle" type="button" @click="tocOpen = !tocOpen">
      <el-icon><component :is="tocOpen ? 'ArrowUp' : 'List'" /></el-icon>
      目录({{ JOURNEY_ENTRIES.length }} 条)
    </button>

    <!-- ============ 主体:目录 + 正文 ============ -->
    <div class="layout">
      <aside class="toc" :class="{ 'is-open': tocOpen }">
        <nav class="toc-inner" aria-label="目录">
          <p class="toc-head">目录</p>
          <div v-for="group in groupedEntries" :key="group.category" class="toc-group">
            <p class="toc-group-title" :style="{ color: CATEGORY_META[group.category].color }">
              {{ CATEGORY_META[group.category].label }}
            </p>
            <button
              v-for="entry in group.entries"
              :key="entry.id"
              type="button"
              class="toc-item"
              :class="{ 'is-active': activeId === entry.id }"
              @click="scrollTo(entry.id)"
            >
              {{ entry.title }}
            </button>
          </div>
        </nav>
      </aside>

      <main class="entries">
        <article
          v-for="entry in JOURNEY_ENTRIES"
          :id="entry.id"
          :key="entry.id"
          class="entry"
          :style="{ '--c': CATEGORY_META[entry.category].color }"
        >
          <header class="entry-head">
            <span class="entry-cat">
              <el-icon><component :is="CATEGORY_META[entry.category].icon" /></el-icon>
              {{ CATEGORY_META[entry.category].label }}
            </span>
            <div class="entry-tags">
              <span v-for="tag in entry.tags" :key="tag" class="tag">{{ tag }}</span>
            </div>
          </header>

          <h2 class="entry-title">{{ entry.title }}</h2>

          <!-- 四段式:症状 → 根因 → 解法 → 收获 -->
          <div class="block">
            <p class="block-label is-symptom">症状</p>
            <p class="block-body">{{ entry.symptom }}</p>
          </div>

          <div class="block">
            <p class="block-label is-cause">根因</p>
            <p class="block-body">{{ entry.cause }}</p>
          </div>

          <div class="block">
            <p class="block-label is-solution">解法</p>
            <p class="block-body">{{ entry.solution }}</p>
          </div>

          <pre v-if="entry.code" class="code-block"><code>{{ entry.code.content }}</code></pre>

          <p class="takeaway">
            <el-icon><Star /></el-icon>
            <span>{{ entry.takeaway }}</span>
          </p>
        </article>

        <footer class="entries-foot">
          <p>以上都是这个项目里真实发生过的。</p>
          <el-button type="primary" @click="router.push('/')">回到首页</el-button>
        </footer>
      </main>
    </div>
  </div>
</template>

<style scoped>
.journey {
  max-width: 1240px;
  margin: 0 auto;
  padding: 24px 24px 80px;
  -webkit-font-smoothing: antialiased;
}

/* ============================================================
 * 返回
 * ============================================================ */
.journey-top {
  margin-bottom: 18px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 200ms ease, color 200ms ease;
}

.back-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

/* ============================================================
 * Hero
 * ============================================================ */
.hero {
  margin-bottom: 44px;
}

.eyebrow {
  display: inline-block;
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 12px;
}

.hero-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(32px, 4.4vw, 52px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  margin: 0 0 14px;
  color: var(--color-text);
  text-wrap: balance;
}

.hero-sub {
  max-width: 660px;
  margin: 0 0 26px;
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text-muted);
}

.hero-sub strong {
  color: var(--color-accent);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.hero-stats {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.stat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  /* 左侧色轨跟着分类色走 —— 统计卡也参与"颜色即分类"的编码 */
  border-left: 3px solid var(--c);
}

.stat-icon {
  font-size: 17px;
  color: var(--c);
}

.stat-text {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-num {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

/* ============================================================
 * 目录切换按钮 —— 桌面隐藏,手机显示
 * ============================================================ */
.toc-toggle {
  display: none;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 11px;
  margin-bottom: 18px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  color: var(--color-text);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

/* ============================================================
 * 两栏布局
 * ============================================================ */
.layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 44px;
  align-items: start;
}

/* ---- 目录 ---- */
.toc {
  position: sticky;
  /* 顶部留出吸顶导航的高度,否则目录顶部会被盖住 */
  top: 24px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  /* 细滚动条,不抢视觉 */
  scrollbar-width: thin;
}

.toc-head {
  margin: 0 0 14px;
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.toc-group {
  margin-bottom: 20px;
}

.toc-group-title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.toc-item {
  display: block;
  width: 100%;
  padding: 7px 11px;
  margin-bottom: 2px;
  background: none;
  border: none;
  border-left: 2px solid var(--color-border);
  border-radius: 0 6px 6px 0;
  color: var(--color-text-muted);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 200ms ease,
    color 200ms ease,
    border-color 200ms ease;
}

.toc-item:hover {
  background: var(--color-bg-alt);
  color: var(--color-text);
}

.toc-item.is-active {
  border-left-color: var(--color-accent);
  background: rgba(76, 175, 80, 0.08);
  color: var(--color-accent);
  font-weight: 600;
}

/* ---- 正文 ---- */
.entries {
  display: flex;
  flex-direction: column;
  gap: 26px;
  min-width: 0;
}

/* scroll-margin-top:锚点跳转时给吸顶栏留位置。
   比起在 JS 里算偏移,这个更稳 —— 浏览器原生支持 */
.entry {
  scroll-margin-top: 88px;
  padding: 24px 28px 22px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: 0 1px 3px var(--color-shadow);
}

.entry-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.entry-cat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--c);
}

.entry-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  padding: 2px 9px;
  background: var(--color-bg-alt);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-soft);
  letter-spacing: 0.02em;
}

.entry-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.35;
  margin: 0 0 18px;
  color: var(--color-text);
  text-wrap: balance;
}

/* ---- 四段式 ---- */
.block {
  margin-bottom: 14px;
}

.block-label {
  display: inline-block;
  margin: 0 0 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

/* 三段用不同的底色区分:症状(红=有问题)、根因(紫=要理解)、解法(绿=已解决) */
.block-label.is-symptom {
  background: rgba(245, 108, 108, 0.12);
  color: #f56c6c;
}

.block-label.is-cause {
  background: rgba(156, 39, 176, 0.1);
  color: #9c27b0;
}

.block-label.is-solution {
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
}

.block-body {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--color-text);
  /* 用户写的内容里有换行(\n),保留它 —— 解法经常是分步的 */
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* ---- 代码块 ---- */
.code-block {
  margin: 0 0 14px;
  padding: 15px 18px;
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.7;
}

.code-block code {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
  color: var(--color-text);
  white-space: pre;
}

/* ---- 收获 ---- */
.takeaway {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0;
  padding: 13px 16px;
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.09), rgba(76, 175, 80, 0.03));
  border-left: 3px solid var(--color-accent);
  border-radius: 0 10px 10px 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text);
}

.takeaway .el-icon {
  flex-shrink: 0;
  margin-top: 4px;
  color: var(--color-accent);
}

.entries-foot {
  padding: 30px 0 0;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
}

.entries-foot p {
  margin: 0 0 16px;
}

/* ============================================================
 * 暗色
 * ============================================================ */
:root[data-theme='dark'] .entry {
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

:root[data-theme='dark'] .code-block {
  background: rgba(0, 0, 0, 0.28);
}

:root[data-theme='dark'] .takeaway {
  background: rgba(76, 175, 80, 0.1);
}

/* ============================================================
 * 响应式
 * ============================================================ */

/* ≤1024:目录窄一档 */
@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 210px minmax(0, 1fr);
    gap: 30px;
  }
}

/* ≤820:目录改成可折叠,点按钮展开 —— 两栏在手机上会把正文压到没法读 */
@media (max-width: 820px) {
  .journey {
    padding: 18px 16px 64px;
  }

  .toc-toggle {
    display: flex;
  }

  .layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .toc {
    position: static;
    max-height: none;
    overflow: visible;
    /* 默认收起;展开时由 .is-open 控制 */
    display: none;
    margin-bottom: 20px;
    padding: 16px 18px;
    background: var(--color-card);
    border: 1px solid var(--color-border);
    border-radius: 12px;
  }

  .toc.is-open {
    display: block;
  }
}

/* ≤600:正文字号和内边距收一档 */
@media (max-width: 600px) {
  .hero-title {
    font-size: 30px;
  }

  .hero-sub {
    font-size: 14px;
  }

  .entry {
    padding: 18px 16px 16px;
    border-radius: 14px;
  }

  .entry-title {
    font-size: 19px;
  }

  .block-body {
    font-size: 14px;
    line-height: 1.8;
  }

  .code-block {
    padding: 12px 13px;
    font-size: 11.5px;
  }

  .takeaway {
    padding: 11px 13px;
    font-size: 13.5px;
  }
}
</style>

<script setup lang="ts">
/**
 * AppSkeleton  -  全站统一的骨架屏(loading 占位)。
 *
 * 之前十几个页面各自用 <el-skeleton> + 一个 el-skeleton-item,形状、动画、
 * 圆角、间距各写各的,换主题还要自己改。收进一个组件后:
 *   - 动画(paper 色 shimmer)、圆角、间距、颜色全在 theme.css §4.6 一份
 *   - 明暗主题自动跟随令牌,不用在每个页面配
 *   - 尊重 prefers-reduced-motion(theme.css §5 统一压制动画时长)
 *
 * variant 决定"这一屏占位长什么样":
 *   table —— 表格骨架:每行几列灰块(列数传 columns),列宽均分
 *   rows  —— 文字行骨架:一列细长条,像一段正文在慢慢加载
 *   grid  —— 卡片网格骨架:几个等高的大块(商城 / 图书封面那种)
 *   block —— 整块骨架:一个占满容器的大块(图表区)
 *
 * 颜色用 color-mix 在 --color-bg-alt 与 --color-border 之间打底,再叠一层
 * 白色高光从左到右扫过,所以明暗下都柔和、不抢戏。
 */
withDefaults(
  defineProps<{
    variant?: 'table' | 'rows' | 'grid' | 'block'
    rows?: number
    columns?: number
  }>(),
  { variant: 'table', rows: 5, columns: 4 },
)
</script>

<template>
  <div
    class="app-skeleton"
    :class="`is-${variant}`"
    :style="{ '--skeleton-rows': rows, '--skeleton-cols': variant === 'rows' ? 1 : columns }"
    role="status"
    aria-label="加载中"
    aria-busy="true"
  >
    <template v-if="variant === 'block'">
      <div class="app-skeleton-cell is-block" />
    </template>
    <template v-else>
      <div v-for="r in rows" :key="r" class="app-skeleton-row">
        <span
          v-for="c in variant === 'rows' ? 1 : columns"
          :key="c"
          class="app-skeleton-cell"
          :style="{ '--skeleton-delay': `${((r - 1) * 120 + (c - 1) * 60) % 720}ms` }"
        />
      </div>
    </template>
  </div>
</template>
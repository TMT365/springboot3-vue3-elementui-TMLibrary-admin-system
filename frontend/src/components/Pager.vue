<script setup lang="ts">
/**
 * 分页器  -  通用 el-pagination 包装
 *
 * 用法:
 *   <Pager v-model:page="page" v-model:size="size" :total="total" @change="fetchList" />
 *
 * v-model:page / v-model:size 双向绑定;@change 在 page/size 任一变化时触发
 */

import { computed } from 'vue'
import { useMediaQuery } from '@/composables/useMediaQuery'

const props = defineProps<{
  page: number
  size: number
  total: number
}>()

const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'update:size', value: number): void
  (e: 'change'): void
}>()

const currentPage = computed<number>({
  get: () => props.page,
  set: (v) => emit('update:page', v),
})

const pageSize = computed<number>({
  get: () => props.size,
  set: (v) => emit('update:size', v),
})

/**
 * 手机窄屏:六个区块(总数/每页条数/上一页/页码/下一页/跳页框)横着排不进 375px,
 * 会顶出横向滚动条。窄屏只留「上一页 页码 下一页」。
 */
const isNarrow = useMediaQuery('(max-width: 600px)')
const layout = computed<string>(() =>
  isNarrow.value ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper',
)
</script>

<template>
  <div class="pager">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      :layout="layout"
      :pager-count="isNarrow ? 5 : 7"
      background
      @current-change="emit('change')"
      @size-change="emit('change')"
    />
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  /* 右侧给右下角的"反馈"悬浮按钮留位。
     悬浮按钮是 position:fixed,永远钉在视口右下角(直径 --fab-size = 48px,
     右边距 --fab-edge = 24px);而分页器是跟着文档流的 —— 页面短到需要滚动时,
     分页器会滑进那个角,被按钮压住(实测 1264×768 滚到底:
     "前往 __ 页"输入框整个被盖住)。
     60px 的让位 = 按钮左沿(视口右起 72px)再留一点余量。 */
  padding-right: 60px;
}

/* 窄屏:分页器居中,并且允许换行(页码多的时候不要顶破容器) */
@media (max-width: 600px) {
  .pager {
    justify-content: center;
    /* 居中后右边不再有控件,让位就没意义了 */
    padding-right: 0;
  }

  .pager :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 6px;
  }
}
</style>
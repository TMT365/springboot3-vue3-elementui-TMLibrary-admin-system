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
</script>

<template>
  <div class="pager">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
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
}
</style>
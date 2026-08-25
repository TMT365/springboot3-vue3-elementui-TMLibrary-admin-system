<script setup lang="ts">
/**
 * 搜索条  -  slot 容纳表单字段 + 重置按钮
 *
 * 用法:
 *   <SearchBar @reset="onReset" @search="onSearch">
 *     <el-input v-model="filters.title" placeholder="书名" />
 *     <el-input v-model="filters.author" placeholder="作者" />
 *     <template #actions>
 *       <el-button type="primary" @click="onSearch">搜索</el-button>
 *     </template>
 *   </SearchBar>
 */

const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'search'): void
}>()

function onReset(): void {
  emit('reset')
}
</script>

<template>
  <div class="search-bar">
    <div class="search-bar__fields">
      <slot />
    </div>
    <div class="search-bar__actions">
      <el-button @click="onReset">重置</el-button>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 16px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.search-bar__fields {
  display: flex;
  gap: 12px;
  flex: 1;
  align-items: center;
  flex-wrap: wrap;
}
.search-bar__actions {
  display: flex;
  gap: 8px;
}
</style>
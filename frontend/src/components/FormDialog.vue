<script setup lang="ts">
/**
 * 表单弹窗  -  el-dialog 包装
 *
 * 用法:
 *   <FormDialog v-model="visible" title="编辑图书" @confirm="onSubmit">
 *     <el-form ...>...</el-form>
 *   </FormDialog>
 */

import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    width?: string
  }>(),
  {
    width: '520px',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed<boolean>({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <slot />
  </el-dialog>
</template>
<script setup lang="ts">
/**
 * 新增 / 编辑图书  -  P4 占位
 *
 * P5 任务:route.params.id 存在 → 编辑模式 bookApi.display(id);否则新增
 *         提交 → bookApi.create(body) 或 /update(id, body);成功后 router.push('/books')
 */

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'

const route = useRoute()
const router = useRouter()

const isEdit = ref<boolean>(!!route.params.id)
const formRef = ref<FormInstance>()

const form = reactive({
  title: '',
  author: '',
  isbn: '',
  price: '0.00',
  stockQuantity: 0,
  publishedDate: '',
})

async function onSubmit(): Promise<void> {
  // P5 接通后端
}
</script>

<template>
  <div class="book-edit">
    <el-alert type="info" :closable="false" show-icon>
      P4 占位  -  表单字段已就位,提交按钮禁用。P5 接通后端后启用。
    </el-alert>
    <el-card style="margin-top: 16px;">
      <el-form ref="formRef" :model="form" label-width="100px" style="max-width: 600px;">
        <el-form-item label="书名">
          <el-input v-model="form.title" placeholder="请输入书名" disabled />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.author" placeholder="请输入作者" disabled />
        </el-form-item>
        <el-form-item label="ISBN">
          <el-input v-model="form.isbn" placeholder="10-20 位" disabled />
        </el-form-item>
        <el-form-item label="价格">
          <el-input v-model="form.price" disabled>
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stockQuantity" :min="0" disabled />
        </el-form-item>
        <el-form-item label="出版日期">
          <el-date-picker v-model="form.publishedDate" type="date" placeholder="选择日期" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSubmit" disabled>
            {{ isEdit ? '保存修改' : '新增图书' }}
          </el-button>
          <el-button @click="router.replace('/admin/books')">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
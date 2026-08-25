<script setup lang="ts">
/**
 * 下单页  -  P4 占位
 *
 * P5 任务:搜索图书 bookApi.multiSearch → 选 N 本,每本填数量 → 实时总价
 *         提交 purchaseApi.create({items: [...]}) → 跳 /purchases
 */

import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const items = ref<Array<{ bookId: number; quantity: number }>>([])
const submitting = ref<boolean>(false)
const quantity = ref<number>(1)
</script>

<template>
  <div class="purchase-create">
    <el-alert type="info" :closable="false" show-icon>
      P4 占位  -  P5 接通后端后填入图书搜索与下单逻辑。当前展示空表单结构。
    </el-alert>
    <el-card style="margin-top: 16px;">
      <template #header>
        <span>添加图书</span>
      </template>
      <el-form label-width="100px">
        <el-form-item label="选择图书">
          <el-input placeholder="搜索图书(P5 接通)" disabled />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="quantity" :min="1" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" disabled :loading="submitting">提交订单</el-button>
          <el-button @click="router.replace('/admin/purchases')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
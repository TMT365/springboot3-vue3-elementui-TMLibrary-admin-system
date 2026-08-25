<script setup lang="ts">
/**
 * user/Manage  -  用户管理(P5 接通版)
 *
 * v2 精修:
 * - 操作列改用 el-dropdown(避免 2 个 disabled button 占位)
 * - 状态色细分(ACTIVE 绿 / INACTIVE 灰 / SUSPENDED 橙)
 * - 加搜索占位
 * - 跟 book/List / purchase/List 风格统一
 */

import { ref } from 'vue'
import { formatDateTime } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import type { UserDto } from '@/types/api'

const searchKeyword = ref<string>('')

const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<UserDto[]>([])

/**
 * 状态 → tag type / 标签
 * - ACTIVE:success(绿) -  正常
 * - INACTIVE:info(灰) -  未激活
 * - SUSPENDED:warning(橙) -  已暂停
 */
function statusTagType(status: string): 'success' | 'warning' | 'info' | undefined {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'INACTIVE': return 'warning'
    case 'SUSPENDED': return 'info'
    default: return undefined
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE': return '正常'
    case 'INACTIVE': return '未激活'
    case 'SUSPENDED': return '已暂停'
    default: return status
  }
}

function roleLabel(role: string): string {
  switch (role) {
    case 'BOSS': return '老板'
    case 'ADMIN': return '管理员'
    case 'USER': return '普通用户'
    default: return role
  }
}
</script>

<template>
  <div class="user-manage">
    <header class="page-head">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-sub">
          共 <strong>{{ total }}</strong> 位用户
        </p>
      </div>
    </header>

    <!-- 提示条(P5 公告) -->
    <el-alert type="warning" :closable="false" show-icon class="notice">
      后端 <code>/api/users/register</code> 当前不在 JWT 白名单,注册入口暂未开放。P5 后端修白名单后再加。
    </el-alert>

    <!-- 搜索 + 筛选条(P5 接通后启用) -->
    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索用户名 / 邮箱 / 手机号"
        clearable
        size="default"
        :prefix-icon="'Search'"
        class="search-input"
      />
      <el-button type="primary" disabled>搜索</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-empty
        v-if="items.length === 0"
        description="暂无用户数据,后端接通后展示"
      />

      <el-table
        v-else
        :data="items"
        class="premium-table"
      >
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="username" label="用户名" width="140">
          <template #default="{ row }">
            <span class="cell-title">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'BOSS' ? 'danger' : row.role === 'ADMIN' ? 'warning' : 'info'"
              effect="light"
              size="small"
            >
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="{ row }">
            <span class="cell-muted">{{ row.email || ' - ' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="phoneNumber" label="手机号" width="150">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.phoneNumber || ' - ' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <span class="cell-muted">{{ formatDateTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default>
            <el-dropdown trigger="click">
              <el-button link type="primary" size="small">
                操作 <el-icon class="caret"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled>编辑</el-dropdown-item>
                  <el-dropdown-item disabled divided>重置密码</el-dropdown-item>
                  <el-dropdown-item disabled divided>
                    <span style="color: #f56c6c;">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <Pager v-model:page="page" v-model:size="size" :total="total" />
  </div>
</template>

<style scoped>
.user-manage {
  max-width: 1280px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

/* 页面标题  -  跟 book/List 完全对齐 */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 4px;
  color: var(--color-text);  text-wrap: balance;
}

.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.page-sub strong {
  color: var(--color-accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.notice {
  margin-bottom: 16px;
  border-radius: 10px;
}

/* ============================================================
 * 工具条  -  搜索 + 操作
 * ============================================================ */
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.search-input {
  flex: 1;
  max-width: 360px;
}

/* 表格外壳  -  同 book/List */
.table-card {
  background: var(--color-card);
  border-radius: 14px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
  overflow: hidden;
}

.premium-table {
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-bg-alt);
  --el-table-row-hover-bg-color: var(--color-bg-alt);
  border-radius: 14px;
  overflow: hidden;
}

.premium-table :deep(.el-table__inner-wrapper) {
  border-radius: 14px;
}

.premium-table :deep(.el-table__header-wrapper) th {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  background: var(--color-bg-alt);
}

.premium-table :deep(.el-table__row) td {
  font-size: 14px;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.premium-table :deep(.el-table__row:last-child td) {
  border-bottom: none;
}

.premium-table :deep(.el-table__row:hover) td {
  background: var(--color-bg-alt);
}

.cell-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);}

.cell-mono {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 13px;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.cell-muted {
  font-size: 13px;
  color: var(--color-text-muted);
}

.caret {
  margin-left: 2px;
  font-size: 10px;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .table-card {
    border-radius: 12px;
    overflow-x: auto;
  }

  .premium-table {
    min-width: 720px;
  }
}
</style>
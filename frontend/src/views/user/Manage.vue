<script setup lang="ts">
/**
 * user/Manage  -  用户管理
 *
 * 调用:GET /api/users/list(分页 + username 模糊搜索)
 * 权限:仅 ADMIN / BOSS 可看全量列表(后端强校验,路由守卫也限制了页面入口)
 *
 * 未接的部分(操作列仍 disabled):
 *   - 编辑用户(PATCH /api/users/{id})、重置密码、软删(DELETE 需密码二次确认)
 *   后端接口都在,但需要配套表单/确认弹窗,单独做一期
 */

import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { formatDateTime } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import type { UserDto } from '@/types/api'

const searchKeyword = ref<string>('')

/**
 * 筛选条件 —— 默认「全部」。
 *
 * 后端 UserSearchRequest.compact() 对 role / status 有兜底默认:
 *   不传 → 只查「普通用户」+「正常状态」
 * 想看管理员/老板、或已暂停的账号,必须显式传值。
 * 所以这里默认就传 -1(后端约定:负数 = 全部,置 null 跳过该条件)。
 *
 * 注:ADMIN 角色调用时后端会强制只返回 role=0 的用户,与这里的筛选无关。
 */
const ROLE_ALL = -1
const STATUS_ALL = -1
const roleFilter = ref<number>(ROLE_ALL)
const statusFilter = ref<number>(STATUS_ALL)

const page = ref<number>(1)
const size = ref<number>(10)
const total = ref<number>(0)
const items = ref<UserDto[]>([])
const loading = ref<boolean>(true)
const error = ref<string | null>(null)

/** 拉取用户列表 —— 分页 + 用户名模糊 + 角色/状态筛选 */
async function fetchUsers(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await userApi.list({
      page: page.value,
      size: size.value,
      // 空串后端会当"不参与筛选";这里直接给 undefined 更明确
      username: searchKeyword.value.trim() || undefined,
      role: roleFilter.value,
      status: statusFilter.value,
    })
    items.value = result.data
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/** 搜索 / 改筛选:回到第 1 页再查(否则停在第 5 页搜出 1 条会空白) */
function onSearch(): void {
  page.value = 1
  void fetchUsers()
}

onMounted(fetchUsers)

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

    <!-- 错误状态 -->
    <div v-if="error" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <!-- 搜索条 —— 用户名 LIKE 模糊 + 角色 / 状态精确筛选 -->
    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="按用户名搜索"
        clearable
        size="default"
        :prefix-icon="'Search'"
        class="search-input"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />

      <el-select v-model="roleFilter" class="filter-select" @change="onSearch">
        <el-option label="全部角色" :value="-1" />
        <el-option label="普通用户" :value="0" />
        <el-option label="管理员" :value="1" />
        <el-option label="老板" :value="2" />
      </el-select>

      <el-select v-model="statusFilter" class="filter-select" @change="onSearch">
        <el-option label="全部状态" :value="-1" />
        <el-option label="正常" :value="0" />
        <el-option label="未激活" :value="1" />
        <el-option label="已暂停" :value="2" />
      </el-select>

      <el-button type="primary" @click="onSearch">搜索</el-button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-skeleton v-if="loading && items.length === 0" :rows="6" animated />

      <el-empty
        v-else-if="!loading && items.length === 0"
        :description="searchKeyword ? '没有匹配的用户' : '暂无用户数据'"
      />

      <el-table
        v-else
        v-loading="loading"
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

    <Pager v-model:page="page" v-model:size="size" :total="total" @change="fetchUsers" />
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

/* ============================================================
 * 工具条  -  搜索 + 操作
 * ============================================================ */
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

/* 筛选下拉:固定宽度,搜索框吃掉剩余空间 */
.filter-select {
  width: 132px;
  flex: 0 0 auto;
}

.search-input {
  flex: 1;
  max-width: 360px;
}

/* 表格外壳  -  同 book/List */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.08);
  color: #f56c6c;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 14px;
}

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
  /* 行距:EP 默认 8px 太密,和 book/List 保持一致用 15px */
  padding: 15px 0;
}

.premium-table :deep(.el-table__header-wrapper) th {
  padding: 12px 0;
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

/* 手机号 —— 11 位数字连排,加字距(同订单号 / ISBN 的处理) */
.cell-mono {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.05em;
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

  /* 竖排时下拉撑满,跟搜索框对齐 */
  .filter-select {
    width: 100%;
  }

  .search-input {
    max-width: none;
  }

  /* 窄屏横向滚动交给 el-table 自己(.el-scrollbar)——
     不要在外面套一层 overflow-x 容器 + 给表格 min-width:
     EP 的固定列是 position: sticky,它的吸附基准是**最近的滚动祖先**,
     即表格内部的 .el-scrollbar;外层再套一个滚动容器的话,
     内部那个永远不滚,固定列就跟着表格一起被推出屏幕,
     「操作」列要一路滑到最右才看得见。 */
  .table-card {
    border-radius: 12px;
  }
}
</style>
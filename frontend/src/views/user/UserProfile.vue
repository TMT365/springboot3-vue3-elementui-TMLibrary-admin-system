<script setup lang="ts">
/**
 * UserProfile - 个人信息(v2 bento 重做)
 *
 * 设计:bento grid 多卡片布局,4 类不同尺寸的 3D 立体卡片,
 * 突出主题(identity = hero 最大,其他作为支撑卡片小一号)。
 *
 * 布局(12-col grid):
 *   +--- IDENTITY (span 12, hero) ----------------+
 *   |  Avatar 96px  +  Username + role/email/phone   |
 *   +-----------------------------------------------+
 *   +--- 4 mini stat cards (span 3 each) -----------+
 *   +--- DETAILS (span 7) ----+ ACTIVITY (span 5) -+
 *   +--------------------------+--------------------+
 *
 * 设计原则(来自 design-taste-frontend):
 *   - 不同尺寸(identity 最大,stat 最小,details/activity 中等)
 *   - 多层 box-shadow + 顶部高光线制造 3D 立体感
 *   - 真实渐变背景(避免纯白纸感)
 *   - hero 用更强阴影和 accent 边框暗示层级
 */

import { ref, computed, onMounted } from 'vue'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { UserDto, UserRole, UserStatus } from '@/types/api'

const userStore = useUserStore()
const profile = ref<UserDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

// ============ 派生量 ============

const roleLabel = computed<string>(() => {
  switch (profile.value?.role) {
    case 'BOSS':  return '老板'
    case 'ADMIN': return '管理员'
    case 'USER':  return '普通用户'
    default:      return '-'
  }
})

const statusLabel = computed<string>(() => {
  switch (profile.value?.status) {
    case 'ACTIVE':    return '正常'
    case 'INACTIVE':  return '未激活'
    case 'SUSPENDED': return '已暂停'
    default:          return '-'
  }
})

const statusType = computed<'success' | 'info' | 'warning'>(() => {
  switch (profile.value?.status) {
    case 'ACTIVE':    return 'success'
    case 'INACTIVE':  return 'info'
    case 'SUSPENDED': return 'warning'
    default:          return 'info'
  }
})

const memberSinceDays = computed<number>(() => {
  if (!profile.value?.createdTime) return 0
  const days = Math.floor(
    (Date.now() - new Date(profile.value.createdTime).getTime()) / (1000 * 60 * 60 * 24),
  )
  return Math.max(0, days)
})

const isAccountAtRisk = computed(() =>
  (profile.value?.failedLoginAttempts ?? 0) >= 3,
)

function formatDateTime(s: string | null | undefined): string {
  if (!s) return '从未'
  return s.replace('T', ' ').slice(0, 16)
}

function formatRelativeTime(s: string | null | undefined): string {
  if (!s) return '从未登录'
  const ms = Date.now() - new Date(s).getTime()
  const mins = Math.floor(ms / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs} 小时前`
  const days = Math.floor(hrs / 24)
  if (days < 30) return `${days} 天前`
  return formatDateTime(s).slice(0, 10)
}

async function fetchProfile(): Promise<void> {
  if (userStore.userId == null) {
    error.value = '无法获取用户 ID,请重新登录'
    loading.value = false
    return
  }
  loading.value = true
  error.value = null
  try {
    profile.value = await userApi.getById(userStore.userId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchProfile)
</script>

<template>
  <div class="user-profile">
    <!-- ============ Error banner ============ -->
    <div v-if="error" class="error-banner">
      <el-icon><CircleClose /></el-icon>
      <span>{{ error }}</span>
    </div>

    <!-- ============ Loading ============ -->
    <el-skeleton v-else-if="loading" :rows="10" animated class="skeleton" />

    <!-- ============ Bento Grid ============ -->
    <div v-else-if="profile" class="bento">
      <!-- ① HERO: identity (最大,最突出) -->
      <article class="card card-hero">
        <div class="hero-inner">
          <!-- 大头像 + 渐变背景 + 立体阴影 -->
          <div class="avatar-large">
            {{ profile.username.slice(0, 1).toUpperCase() }}
          </div>

          <div class="hero-text">
            <div class="hero-name-row">
              <h2 class="hero-name">{{ profile.username }}</h2>
              <span class="hero-id">#{{ profile.id }}</span>
            </div>

            <div class="hero-tags">
              <el-tag :type="statusType" effect="dark" size="default">
                {{ statusLabel }}
              </el-tag>
              <el-tag effect="plain" type="success" size="default">
                {{ roleLabel }}
              </el-tag>
            </div>

            <div class="hero-contact">
              <div class="contact-row">
                <el-icon><Message /></el-icon>
                <span>{{ profile.email }}</span>
              </div>
              <div class="contact-row">
                <el-icon><Phone /></el-icon>
                <span>{{ profile.phoneNumber }}</span>
              </div>
            </div>
          </div>

          <!-- 右上角 ID 卡片装饰 -->
          <div class="hero-corner" aria-hidden="true">
            <span>USER</span>
            <span>·</span>
            <span>{{ profile.role }}</span>
          </div>
        </div>
      </article>

      <!-- ② 4 mini stat cards(等宽,小尺寸) -->
      <article class="card card-stat">
        <div class="stat-label">角色</div>
        <div class="stat-value">{{ roleLabel }}</div>
      </article>

      <article class="card card-stat" :class="{ 'is-warn': isAccountAtRisk }">
        <div class="stat-label">失败登录</div>
        <div class="stat-value">
          {{ profile.failedLoginAttempts }}
          <span v-if="isAccountAtRisk" class="warn-tag">注意</span>
        </div>
      </article>

      <article class="card card-stat">
        <div class="stat-label">状态</div>
        <div class="stat-value">
          <el-tag :type="statusType" size="small" effect="light">
            {{ statusLabel }}
          </el-tag>
        </div>
      </article>

      <article class="card card-stat">
        <div class="stat-label">账号创建</div>
        <div class="stat-value">
          <strong>{{ memberSinceDays }}</strong>
          <span class="stat-unit">天</span>
        </div>
      </article>

      <!-- ③ DETAILS + ④ ACTIVITY(不对称 60/40) -->
      <!-- ③ DETAILS:详细字段(中等,使用 el-descriptions) -->
      <article class="card card-details">
        <header class="card-head">
          <h3 class="card-title">账号资料</h3>
          <p class="card-sub">基本身份信息</p>
        </header>

        <el-descriptions :column="1" border class="details-table">
          <el-descriptions-item label="用户名">
            {{ profile.username }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ profile.email }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">
            {{ profile.phoneNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag size="small" effect="plain" type="success">
              {{ roleLabel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType" size="small" effect="light">
              {{ statusLabel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item
            v-if="profile.accountLockedUntil"
            label="账号锁定至"
          >
            <span class="warn-text">{{ formatDateTime(profile.accountLockedUntil) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </article>

      <!-- ④ ACTIVITY:活动时间线(中等,垂直 timeline) -->
      <article class="card card-activity">
        <header class="card-head">
          <h3 class="card-title">时间记录</h3>
          <p class="card-sub">账号活动轨迹</p>
        </header>

        <el-timeline class="activity-timeline">
          <el-timeline-item
            v-if="profile.lastLoginTime"
            timestamp="上次登录"
            placement="top"
            type="primary"
            :hollow="isAccountAtRisk"
          >
            <div class="time-line">
              <div class="time-primary">{{ formatRelativeTime(profile.lastLoginTime) }}</div>
              <div class="time-secondary">
                {{ formatDateTime(profile.lastLoginTime) }}
              </div>
              <div v-if="profile.lastLoginIp" class="time-meta">
                IP: {{ profile.lastLoginIp }}
              </div>
            </div>
          </el-timeline-item>

          <el-timeline-item
            v-if="profile.updatedTime"
            timestamp="最近更新"
            placement="top"
          >
            <div class="time-line">
              <div class="time-primary">{{ formatDateTime(profile.updatedTime) }}</div>
            </div>
          </el-timeline-item>

          <el-timeline-item
            v-if="profile.createdTime"
            timestamp="账号创建"
            placement="top"
            color="#9e9e9e"
          >
            <div class="time-line">
              <div class="time-primary">{{ formatDateTime(profile.createdTime) }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </article>
    </div>
  </div>
</template>

<style scoped>
.user-profile {
  max-width: 980px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

/* ============ Bento Grid ============ */
.bento {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 18px;
}

.card {
  position: relative;
  background: var(--color-card);
  border-radius: 14px;
  padding: 22px 24px;
  /* 多层 box-shadow - 立体浮起 */
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 12px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
  transition: transform 220ms cubic-bezier(0.2, 0, 0, 1), box-shadow 220ms cubic-bezier(0.2, 0, 0, 1);
}

.card::before {
  /* 顶部 1px 渐变高光线 - 立体感关键 */
  content: '';
  position: absolute;
  top: 0;
  left: 12px;
  right: 12px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.9), transparent);
  pointer-events: none;
}

.card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 4px 8px rgba(0, 0, 0, 0.06),
    0 12px 32px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

/* ============ Error / Loading ============ */
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.08);
  color: #f56c6c;
  border-radius: 10px;
}

.skeleton {
  padding: 20px 0;
}

/* ============================================================
 * ① HERO IDENTITY (col-span 12, max prominence)
 * ============================================================ */
.card-hero {
  grid-column: span 12;
  padding: 32px 36px;
  /* 比其他卡更大的阴影 - 真正"浮"起来 */
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.04),
    0 12px 32px rgba(0, 0, 0, 0.10),
    0 32px 80px rgba(76, 175, 80, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
  background:
    linear-gradient(135deg, var(--color-card) 0%, color-mix(in srgb, var(--color-card), var(--color-accent) 4%) 100%);
}

.hero-inner {
  display: flex;
  align-items: center;
  gap: 28px;
  position: relative;
}

/* 大头像:96px,渐变 + 多层阴影 */
.avatar-large {
  width: 96px;
  height: 96px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background:
    linear-gradient(135deg, var(--color-accent) 0%, color-mix(in srgb, var(--color-accent), #000 18%) 100%);
  color: #fff;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 42px;
  font-weight: 700;
  border-radius: 50%;
  /* 头像多层阴影 - 真正的 3D 浮起 */
  box-shadow:
    0 4px 8px rgba(0, 0, 0, 0.08),
    0 16px 32px rgba(76, 175, 80, 0.20),
    inset 0 2px 0 rgba(255, 255, 255, 0.30),
    inset 0 -2px 4px rgba(0, 0, 0, 0.10);
}

.hero-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.hero-name-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.hero-name {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0;
  color: var(--color-text);
  line-height: 1.1;
}

.hero-id {
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-size: 14px;
  color: var(--color-text-soft);
  font-variant-numeric: tabular-nums;
}

.hero-tags {
  display: flex;
  gap: 8px;
}

.hero-contact {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-text-muted);
}

.contact-row .el-icon {
  color: var(--color-accent);
  font-size: 14px;
}

/* 右上角装饰徽章 */
.hero-corner {
  position: absolute;
  top: 12px;
  right: 16px;
  display: flex;
  gap: 4px;
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--color-text-soft);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-weight: 600;
  text-transform: uppercase;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--color-bg-alt);
  font-variant-numeric: tabular-nums;
}

/* ============================================================
 * ② 4 mini stat cards (col-span 3 each,小尺寸)
 * ============================================================ */
.card-stat {
  grid-column: span 3;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 比 hero 弱一档的阴影 */
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 8px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.card-stat.is-warn {
  background: linear-gradient(135deg, #fffbf3 0%, #fef3e2 100%);
  box-shadow:
    0 1px 2px rgba(245, 158, 11, 0.08),
    0 4px 12px rgba(245, 158, 11, 0.10),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.stat-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.10em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
  display: flex;
  align-items: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.stat-value strong {
  font-size: 24px;
  font-weight: 800;
  color: var(--color-accent);
}

.stat-unit {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-muted);
  text-transform: none;
  letter-spacing: 0;
}

.warn-tag {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #d97706;
  background: rgba(245, 158, 11, 0.15);
  padding: 2px 8px;
  border-radius: 999px;
  font-variant-numeric: tabular-nums;
}

/* ============================================================
 * ③ DETAILS (col-span 7,中等)
 * ============================================================ */
.card-details {
  grid-column: span 7;
}

.card-head {
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--color-border);
}

.card-title {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  margin: 0 0 4px;
  color: var(--color-text);
}

.card-sub {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.details-table {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
}

.details-table :deep(.el-descriptions__label) {
  color: var(--color-text-muted);
  font-weight: 500;
  font-size: 13px;
}

.details-table :deep(.el-descriptions__content) {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 500;
}

.warn-text {
  color: #d97706;
  font-weight: 500;
}

/* ============================================================
 * ④ ACTIVITY (col-span 5,中等)
 * ============================================================ */
.card-activity {
  grid-column: span 5;
}

.activity-timeline {
  padding: 4px 0 0;
}

.activity-timeline :deep(.el-timeline-item__node) {
  background-color: var(--color-accent);
}

.activity-timeline :deep(.el-timeline-item__wrapper) {
  padding-bottom: 14px;
}

.time-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.time-primary {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}

.time-secondary {
  font-size: 12px;
  color: var(--color-text-muted);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  font-variant-numeric: tabular-nums;
}

.time-meta {
  font-size: 11px;
  color: var(--color-text-soft);
  font-family: ui-monospace, 'SF Mono', Menlo, monospace;
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}

/* ============ Responsive - 移动端单列 ============ */
@media (max-width: 768px) {
  .bento {
    grid-template-columns: 1fr;
    gap: 14px;
  }
  .card-hero,
  .card-stat,
  .card-details,
  .card-activity {
    grid-column: span 1;
  }
  .hero-inner {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
  .hero-name {
    font-size: 26px;
  }
  .avatar-large {
    width: 80px;
    height: 80px;
    font-size: 34px;
  }
}
</style>
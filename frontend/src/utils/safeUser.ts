/**
 * User 实体脱敏  -  后端 User 实体含 passwordHash / salt / passwordResetToken 等敏感字段,
 * 前端类型 UserDto 已 Omit 这些字段,但运行时数据仍可能携带;这里兜底显式剔除。
 *
 * 用法:api/user.ts 拿到后端 User 响应后,.then(mapSafeUser) 转成 UserDto 再返回。
 * 即使将来 UserDto 类型松了,safeUser 也会挡一层。
 */

import type { UserDto } from '@/types/api'

/** 后端 User 实体的全部已知字段(含敏感字段) */
interface RawUser extends UserDto {
  passwordHash?: string
  salt?: string
  passwordResetToken?: string
  passwordResetTokenExpiration?: string
}

export function mapSafeUser(raw: RawUser): UserDto {
  return {
    id: raw.id,
    username: raw.username,
    email: raw.email,
    role: raw.role,
    status: raw.status,
    phoneNumber: raw.phoneNumber,
    createdTime: raw.createdTime,
    updatedTime: raw.updatedTime,
    lastLoginTime: raw.lastLoginTime,
    lastLoginIp: raw.lastLoginIp,
    failedLoginAttempts: raw.failedLoginAttempts,
    accountLockedUntil: raw.accountLockedUntil,
    deletedAt: raw.deletedAt,
  }
}
/**
 * User 实体脱敏 + 枚举翻译
 *
 * 实际后端 UserVo 的 role / status 字段是 Integer(0/1/2,见 backend UserVo.java),
 * 但 UI 层用 UserRole / UserStatus 字符串枚举(view 代码
 *  `status === 'ACTIVE'` / `role === 'ADMIN'` 保持可读)。
 *
 * 这里在 API 出口处完成 number → string 翻译,view 层零感知。
 * 同时剥离后端实体可能带回来的敏感字段(passwordHash / salt 等),
 * UserDto 类型已 Omit,但运行时再挡一层。
 */

import type { UserDto, UserRole, UserStatus, UserVoRaw } from '@/types/api'

/** 后端 User 实体的全部已知字段(含敏感字段) */
interface RawUser extends UserVoRaw {
  passwordHash?: string
  salt?: string
  passwordResetToken?: string
  passwordResetTokenExpiration?: string
}

/* 后端枚举 code 映射 —— 对应 backend UserRole.java / UserStatus.java。
 * 定义在这里而不是 types/api.d.ts:那是纯类型声明文件,不能含运行时值。 */
const ROLE_BY_CODE: Record<number, UserRole> = {
  0: 'USER',
  1: 'ADMIN',
  2: 'BOSS',
}

const STATUS_BY_CODE: Record<number, UserStatus> = {
  0: 'ACTIVE',
  1: 'INACTIVE',
  2: 'SUSPENDED',
}

function roleToString(code: number | undefined): UserRole {
  if (code == null) return 'USER'
  return ROLE_BY_CODE[code] ?? 'USER'
}

function statusToString(code: number | undefined): UserStatus {
  if (code == null) return 'ACTIVE'
  return STATUS_BY_CODE[code] ?? 'ACTIVE'
}

export function mapSafeUser(raw: RawUser): UserDto {
  return {
    id: raw.id,
    username: raw.username,
    realName: raw.realName ?? '',
    email: raw.email ?? '',
    avatarUrl: raw.avatarUrl ?? '',
    role: roleToString(raw.role),
    status: statusToString(raw.status),
    phoneNumber: raw.phoneNumber ?? '',
    createdTime: raw.createdTime,
    updatedTime: raw.updatedTime,
    lastLoginTime: raw.lastLoginTime,
    lastLoginIp: raw.lastLoginIp,
    failedLoginAttempts: raw.failedLoginAttempts,
    accountLockedUntil: raw.accountLockedUntil,
    deletedAt: raw.deletedAt,
  }
}

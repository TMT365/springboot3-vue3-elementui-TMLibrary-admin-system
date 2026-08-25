// =============================================================
// TMLibrary 后端 API 类型契约  -  全部手写,匹配 backend 的 Java DTO
// 维护规则:后端改了字段必须同步这里;DTO 命名对齐后端类名
// =============================================================

// ------------------- 通用响应壳 -------------------

/** 后端统一响应壳:`code === 200` 才算成功;`data` 为泛型业务数据 */
export interface Result<T> {
  code: number
  msg: string
  data: T | null
}

/** 分页结果:`total` 是总记录数,`data` 是当前页数据列表 */
export interface PageResult<T> {
  total: number
  data: T[]
}

// ------------------- 枚举(后端 Jackson 序列化为 name()) -------------------

/** 用户角色 */
export type UserRole = 'USER' | 'ADMIN' | 'BOSS'

/** 订单状态 */
export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELLED'

/** 用户账号状态 */
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

// ------------------- 分页入参 -------------------

/** 通用分页参数 */
export interface PageQuery {
  page?: number
  size?: number
}

// ------------------- Auth -------------------

/** POST /api/auth/login 请求体(后端会覆盖 ipAddress 字段) */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  username: string
  role: UserRole
}

// ------------------- Book -------------------

/** Book 实体返回形态  -  大小写路径用 BookDto 来标记"已对齐后端" */
export interface BookDto {
  id: number
  title: string
  author: string
  isbn: string
  /** BigDecimal → JSON 字符串,UI 用 formatPrice() 渲染 */
  price: string
  /** LocalDate → "yyyy-MM-dd" */
  publishedDate: string
  /** LocalDateTime → "yyyy-MM-ddTHH:mm:ss" */
  createdTime: string
  updatedTime: string
  stockQuantity: number | null
}

/** 新增 / 修改图书请求体 */
export interface BookSaveRequest {
  title: string
  author: string
  isbn: string
  /** BigDecimal JSON 字符串 */
  price: string
  stockQuantity: number
  /** "yyyy-MM-dd" */
  publishedDate: string
}

/** GET /api/books 多条件搜索 query */
export interface BookSearchRequest extends PageQuery {
  title?: string
  author?: string
  minPrice?: string
  maxPrice?: string
  minStock?: number
  maxStock?: number
  publishedDate?: string
}

/** GET /api/books/search/publishedDate/by */
export interface BookPublishedDateByQuery extends PageQuery {
  year: number
  month?: number
  day?: number
}

/** GET /api/books/search/{CreatedTime|UpdatedTime}/by */
export interface BookDateTimeByQuery extends PageQuery {
  year: number
  month?: number
  day?: number
  hour?: number
  minute?: number
}

// ------------------- User(敏感字段剥离) -------------------

/**
 * 安全用户 DTO  -  严格不包含 passwordHash / salt / passwordResetToken 等敏感字段。
 * 如果后端响应真返回这些字段,运行时由 utils/safeUser.ts 兜底剥掉,UI 层永不接触。
 */
export interface UserDto {
  id: number
  username: string
  email: string
  role: UserRole
  status: UserStatus
  phoneNumber: string
  createdTime: string
  updatedTime: string
  lastLoginTime: string | null
  lastLoginIp: string | null
  failedLoginAttempts: number
  accountLockedUntil: string | null
  deletedAt: string | null
}

/** POST /api/users/register 请求体 */
export interface UserRegisterRequest {
  username: string
  password: string
  email: string
  /** 必须是 11 位 */
  phoneNumber: string
}

/** PATCH /api/users/{id} 请求体  -  所有字段可选,只传要改的 */
export interface UserUpdatedRequest {
  username?: string
  email?: string
  phoneNumber?: string
  status?: UserStatus
  /** 仅 BOSS 可改 */
  role?: UserRole
}

/** DELETE /api/users/{id} 请求体  -  需要密码二次确认 */
export interface UserDeleteRequest {
  password: string
}

/** PATCH /api/users/{id}/password 请求体 */
export interface UserPasswordRequest {
  oldPassword: string
  newPassword: string
}

/** GET /api/users/list query */
export interface UserSearchRequest extends PageQuery {
  username?: string
  role?: UserRole
  status?: UserStatus
  phoneNumber?: string
  lastLoginIp?: string
  failedLoginAttempts?: number
  createdTimeStart?: string
  createdTimeEnd?: string
  updateTimeStart?: string
  updateTimeEnd?: string
  lastLoginTimeStart?: string
  lastLoginTimeEnd?: string
  accountLockedUntilStart?: string
  accountLockedUntilEnd?: string
  deletedAtStart?: string
  deletedAtEnd?: string
}

// ------------------- Purchase -------------------

/** POST /api/purchases 请求体  -  userId 从 token 拿,DTO 不带 */
export interface PurchaseRequest {
  items: PurchaseItemRequest[]
}

/** PurchaseRequest.items 元素 */
export interface PurchaseItemRequest {
  bookId: number
  quantity: number
}

/** GET /api/purchases/{id} 或 GET /api/users/{id}/purchases 响应元素 */
export interface PurchaseResponse {
  orderNumber: string
  status: OrderStatus
  /** BigDecimal 字符串 */
  totalAmount: string
  items: PurchaseItemResponse[]
}

/** PurchaseResponse.items 元素 */
export interface PurchaseItemResponse {
  bookId: number
  quantity: number
  /** 下单时的价格快照 */
  price: string
  /** price × quantity */
  subtotal: string
}
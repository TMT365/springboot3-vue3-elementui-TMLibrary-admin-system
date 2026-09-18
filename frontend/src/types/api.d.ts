// =============================================================
// TMLibrary 后端 API 类型契约  -  全部手写,匹配 backend 的 Java DTO
// 维护规则:后端改了字段必须同步这里;DTO 命名对齐后端类名
//
// 关于"BigDecimal → 字符串":这是 API.md 的说法,但后端没有任何
// 自定义 Jackson 序列化器,默认 BigDecimal → JSON number。本文件
// 已按真实行为改为 number(BookDto.price / PurchaseResponse.totalAmount /
// PurchaseItemResponse.price|subtotal)。
//
// 关于 UserVo.role / UserVo.status:后端 UserVo 用 Integer 字段(0/1/2),
// 而 LoginResponse.role 用 UserRole 枚举(序列化为字符串 "USER")。
// 两种序列化方式不一致但都是真实的。本文件对应:
//   - UserDto.role / .status  → number
//   - LoginResponse.role      → UserRole 字符串
// safeUser.ts 在 API 出口处把 number 翻译成字符串,view 层统一用
// UserRole / UserStatus,view 代码无需感知差异。
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

/** 用户角色 —— 仅 LoginResponse 用,UserVo 用 Integer */
export type UserRole = 'USER' | 'ADMIN' | 'BOSS'

/** 订单状态 —— PurchaseResponse 用 */
export type OrderStatus = 'PENDING' | 'PAID' | 'CANCELLED' | 'TIMEOUT'

/** 用户账号状态 —— UserVo 实际是 Integer,这里是 UI 层抽象
 *  (safeUser.ts 把 0/1/2 翻译为 ACTIVE/INACTIVE/SUSPENDED) */
export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'

/* 注意:本文件是 .d.ts(纯类型声明),编译后不产生任何 JS。
 * 不要把运行时常量(对象 / 函数)放在这里 —— Vite 在运行时解析不到,
 * 会导致 "Failed to resolve import" 并中断整条模块链。
 * 运行时映射放在 src/utils/safeUser.ts 里。 */

// ------------------- 分页入参 -------------------

/** 通用分页参数 */
export interface PageQuery {
  page?: number
  size?: number
}

// ------------------- Auth -------------------

/** 验证码响应(2026-09 新增)—— JSON 对象替代原 image/png 二进制
 *  - image: data URI,直接给 <img src> 用
 *  - expiresAt: 毫秒时间戳,后端用 String 输出避免 JS Number 精度风险;
 *    前端用 Number() 转回 number 后做倒计时计算
 *  详见 backend CaptchaResponse.java 注释 */
export interface CaptchaResponse {
  image: string
  expiresAt: string
}

/** POST /api/captcha/login 或 /api/captcha/register 请求体 */
export interface GetCaptchaRequest {
  username: string
}

/** POST /api/users/login 请求体 —— 见 backend LoginRequest.java */
export interface LoginRequest {
  username: string
  password: string
  /** 验证码原文 —— 与 captcha 申请时同一个 uuid 绑定 */
  captcha: string
  /** §2.1 申请 captcha 时用的 uuid */
  uuid: string
  /** 后端字段 —— 前端不传,后端从 request 拿 IP 自动填 */
  ipAddress?: never
}

/** 登录响应 —— role 是 UserRole 枚举,Jackson 序列化为字符串 */
export interface LoginResponse {
  token: string
  username: string
  role: UserRole
}

/** POST /api/users/logout 响应 —— 见 backend LogoutResponse.java */
export interface LogoutResponse {
  /** 固定 true,前端据此清 localStorage 并跳转 */
  loggedOut: boolean
  /** 提示文案,可直接展示 */
  message: string
  /** 建议前端跳转的路径 */
  redirectUrl: string
  /** ISO-8601 字符串 */
  logoutAt: string
}

// ------------------- Book -------------------

/** Book 实体返回形态 —— 见 backend Book.java
 *  BigDecimal price → JSON number(默认 Jackson 行为) */
export interface BookDto {
  id: number
  title: string
  author: string
  isbn: string
  /** BigDecimal → JSON number;UI 用 formatPrice() 渲染 */
  price: number
  /** LocalDate → "yyyy-MM-dd" */
  publishedDate: string
  /** LocalDateTime → "yyyy-MM-ddTHH:mm:ss" */
  createdTime: string
  updatedTime: string
  stockQuantity: number | null
  /** 所属分类 id(book_categories.id);null = 未分类(分类功能上线前的老书) */
  categoryId: number | null
}

// ------------------- BookCategory(图书分类) -------------------

/** GET /api/books/categories 的树节点 —— 见 backend BookCategoryNode.java
 *  只有两级:顶层是大类(带 children),children 里是小类(children 为 null)。
 *  大类的 bookCount 是**累加值**(自身直挂 + 所有子类之和),小类则是自身直挂数。 */
export interface BookCategoryNode {
  id: number
  name: string
  /** 大类图标名(Element Plus 图标,如 "Reading");小类为 null */
  icon: string | null
  bookCount: number
  children: BookCategoryNode[] | null
}

/** POST /api/books/categories 请求体 —— 见 backend CategoryCreateRequest.java */
export interface CategoryCreateRequest {
  /** 0 或省略 = 新建大类;> 0 = 在该大类下新建小类 */
  parentId?: number
  name: string
}

/** POST /api/books/categories 的响应 —— 直接返回 BookCategory 实体(不是树节点)
 *  见 backend BookCategory.java */
export interface BookCategoryDto {
  id: number
  /** 0 = 大类 */
  parentId: number
  name: string
  icon: string | null
  sortOrder: number
  /** 直挂图书数 —— 新建的必然是 0 */
  bookCount: number
  createdTime: string
  updatedTime: string
}

/** POST /api/books/created 请求体 —— 见 backend BookSaveRequest.java
 *  BigDecimal 字段:前端发 number,Jackson 接收后转 BigDecimal */
export interface BookSaveRequest {
  title: string
  author: string
  isbn: string
  price: number
  stockQuantity: number
  /** "yyyy-MM-dd" */
  publishedDate: string
  /** 图书分类(book_categories 里的**小类** id);可空 —— 兼容分类功能上线前录入的老书 */
  categoryId?: number | null
}

/** PATCH /api/books/{isbn} 请求体 —— 见 backend BookUpdateRequest.java
 *  库存字段已移出本接口,改用 BookStockAdjustRequest(避免与在途预占冲突) */
export interface BookUpdateRequest {
  title?: string
  author?: string
  price?: number
  /** "yyyy-MM-dd" */
  createdDate?: string
  publishedDate?: string
  /** 传了就改分类(后端会同步新旧两个分类的计数);不传保持原样 */
  categoryId?: number | null
}

/** PATCH /api/books/{isbn}/stock 请求体 —— 见 backend BookStockAdjustRequest.java
 *  绝对值,非增量;盘点语义,DB 覆盖后 Redis 按「新库存 - 在途预占」重算 */
export interface BookStockAdjustRequest {
  stockQuantity: number
}

/** GET /api/books 多条件搜索 query —— 见 backend BookSearchRequest.java
 *  BigDecimal 用 number(LocalDateTime 用字符串 yyyy-MM-ddTHH:mm:ss,这里不涉及) */
export interface BookSearchRequest extends PageQuery {
  title?: string
  author?: string
  minPrice?: number
  maxPrice?: number
  minStock?: number
  maxStock?: number
  publishedDate?: string
  /** 关键字模糊搜索 —— 书名 / 作者 / ISBN **任一**命中即可(后端是 OR) */
  keyword?: string
  /** 按分类筛选。传大类时后端会一并带上它的所有子类 */
  categoryId?: number
}

/** GET /api/books/search/publishedDate/by —— 见 backend BookPublishedDateByRequest */
export interface BookPublishedDateByQuery extends PageQuery {
  year: number
  month?: number
  day?: number
}

/** GET /api/books/search/{CreatedTime|UpdatedTime}/by —— 见 backend BookDateTimeByRequest */
export interface BookDateTimeByQuery extends PageQuery {
  year: number
  month?: number
  day?: number
  hour?: number
  minute?: number
}

// ------------------- User(敏感字段剥离) -------------------

/** 后端原始 UserVo 形态 —— Integer role/status 来自 backend UserVo.java
 *  UI 真正用的是 UserDto(safeUser.ts 在 API 出口处把 number 翻译成字符串) */
export interface UserVoRaw {
  id: number
  username: string
  /** 后端 encryptInformation() 兜底为 ""(不是 null) */
  realName: string
  /** 后端 maskEmail() 兜底为 "" */
  email: string
  avatarUrl: string
  /** 0 ACTIVE / 1 INACTIVE / 2 SUSPENDED —— Integer code */
  status: number
  /** 0 USER / 1 ADMIN / 2 BOSS —— Integer code */
  role: number
  /** 后端 encryptInformation(7) 兜底为 "" */
  phoneNumber: string
  createdTime: string
  updatedTime: string
  lastLoginTime: string | null
  lastLoginIp: string | null
  failedLoginAttempts: number
  accountLockedUntil: string | null
  deletedAt: string | null
}

/**
 * UI 层 UserDto —— 在 UserVoRaw 基础上把 number role/status 翻译为字符串,
 *  沿用 UserRole / UserStatus 枚举,view 层保持原有 `status === 'ACTIVE'` 写法。
 * 转换在 utils/safeUser.ts 里完成(API 出口处)。
 */
export interface UserDto extends Omit<UserVoRaw, 'status' | 'role' | 'avatarUrl'> {
  status: UserStatus
  role: UserRole
  avatarUrl: string
}

/** POST /api/users/register 请求体 —— 见 backend UserRegisterRequest.java */
export interface UserRegisterRequest {
  username: string
  password: string
  email: string
  phoneNumber: string
  /** 验证码(2026-09 加入) */
  captcha: string
  /** 申请 captcha 时用的 uuid */
  uuid: string
}

/** PATCH /api/users/{id} 请求体 —— 见 backend UserUpdatedRequest.java
 *  id 字段后端存在但忽略(以 URL {id} 为准,防 body 串改);
 *  status/role 后端是 Integer,UI 层用枚举,提交时 safeUser 反向翻译。 */
export interface UserUpdatedRequest {
  /** 后端有但忽略 —— 前端不传,让 URL {id} 决定 */
  id?: never
  username?: string
  email?: string
  phoneNumber?: string
  status?: UserStatus
  /** 仅 BOSS 可改 */
  role?: UserRole
}

/** DELETE /api/users/{id} 请求体 —— 见 backend UserDeleteRequest.java */
export interface UserDeleteRequest {
  password: string
}

/** PATCH /api/users/{id}/password 请求体 —— 见 backend UserPasswordRequest.java */
export interface UserPasswordRequest {
  oldPassword: string
  newPassword: string
}

/** GET /api/users/list query —— 见 backend UserSearchRequest.java
 *  role/status 后端是 Integer code:
 *    role   0 USER / 1 ADMIN / 2 BOSS,  **-1 = 全部**
 *    status 0 ACTIVE / 1 INACTIVE / 2 SUSPENDED, **-1 = 全部**
 *  不传这两个参数时后端 compact() 会兜底成 0(只查普通用户 + 只查正常状态),
 *  所以 UI 想"看全部"必须显式传 -1。 */
export interface UserSearchRequest extends PageQuery {
  username?: string
  role?: number
  status?: number
  phoneNumber?: string
  lastLoginIp?: string
  failedLoginAttempts?: number
  createdTimeStart?: string
  createdTimeEnd?: string
  updatedTimeStart?: string
  updatedTimeEnd?: string
  lastLoginTimeStart?: string
  lastLoginTimeEnd?: string
  accountLockedUntilStart?: string
  accountLockedUntilEnd?: string
  deletedAtStart?: string
  deletedAtEnd?: string
}

// ------------------- Stats(仪表盘统计) -------------------

/** GET /api/stats/dashboard 响应 —— 见 backend DashboardStatsResponse.java
 *  4 组数据一次返回,前端 4 张图各取一组 */
export interface DashboardStatsResponse {
  /** 最近 N 天每天新增用户数 */
  newUsersTrend: DailyCountItem[]
  /** 最近 N 天每天成交订单数 + 成交额(仅 PAID) */
  salesTrend: DailySalesItem[]
  /** 销量 Top N 的书 */
  bookSalesTop: BookSalesItem[]
  /** 订单状态分布(可能缺状态,前端补齐) */
  orderStatusDistribution: StatusCountItem[]
}

/** 某天 + 数量 —— LocalDate 序列化为 "yyyy-MM-dd" */
export interface DailyCountItem {
  date: string
  count: number
}

/** 某天 + 订单数 + 成交额(BigDecimal → number) */
export interface DailySalesItem {
  date: string
  orders: number
  amount: number
}

/** 书 + 累计销量 */
export interface BookSalesItem {
  bookId: number
  title: string
  author: string
  quantity: number
  /** BigDecimal → number */
  amount: number
}

/** 订单状态 + 数量(status 是 Integer code,标签前端映射) */
export interface StatusCountItem {
  status: number
  count: number
}

// ------------------- Purchase -------------------

/** POST /api/purchases 请求体 —— 见 backend PurchaseRequest.java
 *  userId 字段已删除(从 token 拿,DTO 不带) */
export interface PurchaseRequest {
  items: PurchaseItemRequest[]
}

/** PurchaseRequest.items 元素 —— 见 backend PurchaseItemRequest.java */
export interface PurchaseItemRequest {
  bookId: number
  quantity: number
}

/** GET /api/purchases 分页元素(管理端订单列表)—— 见 backend OrderListItem.java
 *  比 PurchaseResponse 轻:不带 items,但带 createdTime */
export interface OrderListItem {
  /** 雪花 ID,后端用 String 输出(Long 超出 JS 安全整数范围) */
  orderNumber: string
  status: OrderStatus
  /** BigDecimal → number */
  totalAmount: number
  /** LocalDateTime → "yyyy-MM-ddTHH:mm:ss" */
  createdTime: string
}

/** GET /api/purchases/{orderNumber} 或 GET /api/users/{id}/purchases 响应元素
 *  status 是 OrderStatus 枚举(序列化为字符串);BigDecimal 字段是 number */
export interface PurchaseResponse {
  orderNumber: string
  status: OrderStatus
  /** BigDecimal → JSON number */
  totalAmount: number
  items: PurchaseItemResponse[]
  /** 下单时间 —— orders.created_time,已截到秒:"2026-09-16T21:36:33" */
  createdTime: string
  /** 支付时间 —— orders.paid_time;未支付 / 已取消 / 超时都是 null */
  paidTime: string | null
}

// ------------------- 用户反馈 -------------------

/** 反馈分类 —— 后端 FeedbackCreateRequest 的 @Pattern 白名单,前端只能传这 4 个之一 */
export type FeedbackCategory = 'BUG' | 'FEATURE' | 'QUESTION' | 'OTHER'

/** 反馈状态 —— 后端是 TINYINT(0..3),前端用数字常量保持和后端一致
 *  0=OPEN 1=IN_PROGRESS 2=RESOLVED 3=CLOSED */
export type FeedbackStatus = 0 | 1 | 2 | 3

/** 反馈优先级 —— 0=LOW 1=NORMAL 2=HIGH 3=URGENT */
export type FeedbackPriority = 0 | 1 | 2 | 3

/** POST /api/feedbacks/mine 请求体 —— 见 backend FeedbackCreateRequest.java */
export interface FeedbackCreateRequest {
  category: FeedbackCategory
  /** ≤ 120 字符(后端 @Size 与 SQL VARCHAR(120) 对齐) */
  title: string
  /** ≤ 5000 字符 */
  body: string
}

/** POST /api/feedbacks/{id}/reply 请求体 —— 见 backend FeedbackReplyRequest.java
 *  isInternal=true 仅管理员可生效;普通用户传了也会被后端强制当 false */
export interface FeedbackReplyRequest {
  body: string
  isInternal?: boolean
}

/** PATCH /api/feedbacks/{id}/status 请求体 —— 见 backend FeedbackStatusRequest.java
 *  两个字段至少传一个 */
export interface FeedbackStatusRequest {
  status?: FeedbackStatus
  priority?: FeedbackPriority
}

/** GET /api/feedbacks/{id} 响应 —— 见 backend FeedbackView.java */
export interface FeedbackView {
  id: number
  userId: number
  /** 提交人用户名(联表取) */
  username: string | null
  category: FeedbackCategory
  title: string
  body: string
  status: FeedbackStatus
  priority: FeedbackPriority
  createdTime: string
  updatedTime: string
  resolvedTime: string | null
  replies: FeedbackReplyView[]
}

/** FeedbackView.replies 元素 —— 见 backend FeedbackReplyView.java */
export interface FeedbackReplyView {
  id: number
  userId: number
  username: string | null
  /** 0=USER 1=ADMIN 2=BOSS —— 前端据此区分气泡样式 */
  role: number
  /** 1 = 内部备注(普通用户拿不到,后端已过滤) */
  isInternal: number
  body: string
  createdTime: string
}

/** 反馈列表行 —— 见 backend FeedbackSummary.java(不含 body,点进详情才取) */
export interface FeedbackSummary {
  id: number
  title: string
  userId: number
  username: string | null
  category: FeedbackCategory
  status: FeedbackStatus
  priority: FeedbackPriority
  hasReply: boolean
  replyCount: number
  createdTime: string
  updatedTime: string
}

/** 搜索候选词类型 —— 见 backend BookSuggestion.java */
export type BookSuggestionType = 'TITLE' | 'AUTHOR' | 'ISBN'

/** GET /api/books/suggest 的响应元素 —— 下拉列表一条 */
export interface BookSuggestion {
  type: BookSuggestionType
  /** 候选词本身(书名 / 作者名 / ISBN) */
  text: string
  /** 作者类候选为 null */
  isbn: string | null
  /** 热度 = 该候选在已支付订单里的累计销量,不会是 null */
  hot: number
}

/** PurchaseResponse.items 元素 —— BigDecimal 字段是 number */
export interface PurchaseItemResponse {
  bookId: number
  quantity: number
  /** 下单时的价格快照(BigDecimal) */
  price: number
  /** price × quantity(BigDecimal) */
  subtotal: number
}

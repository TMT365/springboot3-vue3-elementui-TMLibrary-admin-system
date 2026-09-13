# TMLibrary 接口文档

> Spring Boot 4.1.0 + Java 25 + 纯 MyBatis + Redis + JWT
>
> Base URL: `http://localhost:8080`(开发环境)
>
> 所有请求和响应均使用 `application/json`(除验证码图片外)。

---

## 1. 通用约定

### 1.1 响应格式 `Result<T>`

所有接口(除验证码图片)统一返回：

```json
{
  "code": 200,
  "msg": "成功",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | 业务状态码(见 [§ 1.4](#14-业务状态码-resultcode)) |
| `msg` | string | 提示信息,后端原文返回 |
| `data` | T / null | 业务数据,失败时为 `null` |

### 1.2 分页响应 `PageResult<T>`

`data` 为分页时,使用：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "total": 42,
    "data": [ ... ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | 命中总数 |
| `data` | T[] | 当前页数据 |

### 1.3 鉴权(JWT)

- **免鉴权白名单**(按「路径 + HTTP 方法」判定):

  | 路径 | 方法 | 说明 |
  |---|---|---|
  | `/api/users/login` | `POST` | 登录 |
  | `/api/users/register` | `POST` | 注册 |
  | `/api/captcha/**` | `POST` | 验证码 |
  | `/api/books**` | **`GET`** | 图书展示:商城、列表、详情、三种粒度搜索 |

  > 图书模块**只有 GET 放行**。同一前缀下的 `POST /api/books/created`、
  > `PATCH /api/books/{isbn}`、`PATCH /api/books/{isbn}/stock`、
  > `DELETE /api/books/deleted/isbn/{isbn}` 仍**必须**携带 token。

- 其余接口(含 `GET /api/users/**`、`GET /api/purchases/**`)一律要求
  `Authorization: Bearer <token>`。
- token 由 `POST /api/users/login` 返回,前端保存到 localStorage。
- token 过期 / 被登出 / 缺失 → **HTTP 401**,`code=401`,`msg` 为 `缺少 Authorization 头` / `令牌无效` / `Token已登出作废，请重新登录` 之一。
- 当前用户信息由 JwtAuthFilter 解析后写入 request attribute `CURRENT_USER`(内部机制,客户端无感)。

### 1.4 业务状态码 `ResultCode`

| code | 名称 | 用途 |
|---:|---|---|
| 200 | `SUCCESS` | 成功 |
| 201 | `CREATED` | 创建成功 |
| 204 | `NO_CONTENT` | 无返回内容 |
| 400 | `BAD_REQUEST` | 参数错误 / 校验失败 |
| 401 | `UNAUTHORIZED` | 未登录 / 登录失败 |
| 403 | `FORBIDDEN` | 权限不足 / 账号被锁 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 409 | `CONFLICT` | 状态冲突 / 并发冲突(重复取消、已支付再取消、付款时库存不足) |
| 422 | `UNPROCESSABLE_ENTITY` | 业务校验失败 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误 |

> 上表的 `code` 同时作为 **HTTP 状态码**返回。

### 1.5 错误响应示例

**HTTP 状态码与 `code` 字段一致** —— 错误响应使用真实的 HTTP 状态码
(而非一律返回 200 把错误码塞在响应体里),便于网关/监控按状态码统计错误率。
响应体仍是统一的 `Result` 壳:

```http
HTTP/1.1 401 Unauthorized

{
  "code": 401,
  "msg": "用户名或密码错误",
  "data": null
}
```

> 过滤器(`JwtAuthFilter`)写入的错误也遵循同一约定,前端可统一按
> `err.response.data` 解包,无需区分两套风格。

---

## 2. 鉴权模块 `/api`

### 2.1 获取登录验证码

`POST /api/captcha/login?uuid=<uuid>`

- **请求体**：
  ```json
  { "username": "alice" }
  ```
- **响应**：`image/png` 二进制流(直接渲染到 `<img src>`)。
- **白名单**:无需 token。
- **说明**:
  - `uuid` 由前端生成(推荐 UUID),作为 captcha 在 Redis 的 key 后缀。
  - 验证码和请求时的 `username` 绑定,3 分钟 TTL。
  - 同一个 uuid 只能验证一次(登录成功后被 Redis 删除)。

### 2.2 注册

`POST /api/users/register`

- **白名单**:无需 token。
- **请求体** `UserRegisterRequest`:
  ```json
  {
    "username": "alice",
    "password": "secret123",
    "email": "alice@example.com",
    "phoneNumber": "13800000000"
  }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `username` | string | ✅ | 长度 1-50 |
  | `password` | string | ✅ | 长度 6-20(后端 BCrypt 哈希) |
  | `email` | string | ✅ | 邮箱格式 |
  | `phoneNumber` | string | ✅ | 长度 11 |

- **响应 data**：`int`(新用户 ID)

### 2.3 登录

`POST /api/users/login`

- **白名单**:无需 token。
- **请求体** `LoginRequest`:
  ```json
  {
    "username": "alice",
    "password": "secret123",
    "captcha": "aB3x",
    "uuid": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|:---:|---|
  | `username` | string | ✅ | 与 captcha 申请时一致 |
  | `password` | string | ✅ | 明文,后端 BCrypt |
  | `captcha` | string | ✅ | 验证码原文 |
  | `uuid` | string | ✅ | 调用 § 2.1 时用的 uuid |

- **响应 data** `LoginResponse`:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "alice",
    "role": "USER"
  }
  ```
  - `role`: `USER` / `ADMIN` / `BOSS`

- **失败码**:
  - `404 NOT_FOUND` — 未找到请求验证码(uuid 不存在或已过期)
  - `400 BAD_REQUEST` — 验证码错误 / 验证码与账号不匹配
  - `401 UNAUTHORIZED` — 用户名或密码错误(统一文案,**不区分**用户是否存在,防账号枚举)
  - `403 FORBIDDEN` — 账户未激活 / 已软删 / 连续失败锁定
  - `500 INTERNAL_ERROR` — Redis 验证码 JSON 解析异常

- **失败锁定策略**:连续失败 3 次锁定 15 分钟;计数据与锁定在同一条原子 SQL 内完成
  (无读改写竞态)。锁定到期后失败计数自动重置。

### 2.4 登出

`POST /api/users/logout`

- **请求头**:`Authorization: Bearer <token>`
- **请求体**:无
- **响应 data** `LogoutResponse`:
  ```json
  {
    "loggedOut": true,
    "message": "登出成功,请重新登录",
    "redirectUrl": "/login",
    "logoutAt": "2026-09-13T13:20:00Z"
  }
  ```

  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `loggedOut` | boolean | 固定 `true`,前端据此清 localStorage 并跳转 |
  | `message` | string | 提示文案,可直接展示 |
  | `redirectUrl` | string | 建议前端跳转的路径 |
  | `logoutAt` | Instant | 服务端登出时间(ISO-8601) |

- **机制**:解析 token,提取 `jti`,按剩余 TTL 写入 Redis 黑名单(同 token 不能再用)。
- **前端约定**:收到 `loggedOut=true` 后必须清除本地 token 并跳转 `redirectUrl`;
  否则用户停留在受保护页面,后续请求会被 401 拦截。

---

## 3. 用户管理 `/api/users`

所有接口需要 `Authorization: Bearer <token>`。

### 3.1 列表查询(分页 + 多条件)

`GET /api/users/list?username=&role=&page=1&size=10`

- **权限**:仅 `ADMIN` / `BOSS` 可看全量列表,普通用户只看到自己。
- **Query 参数** `UserSearchRequest`(全部可选):
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `username` | string | 模糊查询 |
  | `role` | int | 精确筛选目标用户的角色(`0` USER / `1` ADMIN / `2` BOSS)。**仅 `BOSS` 生效**;`ADMIN` 恒被限制为只看 `role=0` |
  | `status` | int | 精确(`0` ACTIVE / `1` INACTIVE / `2` SUSPENDED),默认 `0` |
  | `phoneNumber` | string | 模糊查询 |
  | `lastLoginIp` | string | 精确 |
  | `failedLoginAttempts` | int | 精确(用于排查异常账号) |
  | `createdTimeStart` / `createdTimeEnd` | datetime | 区间,start > end 时整段置空 |
  | `updatedTimeStart` / `updatedTimeEnd` | datetime | 同上 |
  | `lastLoginTimeStart` / `lastLoginTimeEnd` | datetime | 同上 |
  | `accountLockedUntilStart` / `accountLockedUntilEnd` | datetime | 同上 |
  | `deletedAtStart` / `deletedAtEnd` | datetime | 同上 |
  | `page` | int | 默认 1 |
  | `size` | int | 默认 10,上限 100 |

- **响应 data**:`PageResult<UserVo>`,`UserVo` 见 [§ 5.3](#53-user-用户视图)。

### 3.2 详情

`GET /api/users/{id}`

- **路径**:`id` int
- **权限**:自己 / `ADMIN` / `BOSS` 可看,其他角色 `403 FORBIDDEN`。
- **响应 data**:`UserVo`,敏感字段已脱敏:
  - `realName` — 保留首字符,余下 `*`(`张三丰` → `张**`)
  - `phoneNumber` — 保留前 7 位,余下 `*`(`13800001234` → `1380000****`)
  - `email` — 本地部分保留首字符,域名完整(`alice@example.com` → `a****@example.com`)

### 3.3 更新用户信息

`PATCH /api/users/{id}`

- **路径**:`id` int
- **权限**:
  - `username` / `email` / `phoneNumber` — 只能改自己
  - `status` — `ADMIN` 及以上
  - `role` — `BOSS`
- **请求体** `UserUpdatedRequest`(所有字段可选):
  ```json
  {
    "username": "newAlice",
    "email": "new@example.com",
    "phoneNumber": "13900000000",
    "status": 0,
    "role": 0
  }
  ```
  - `id` 字段**忽略**:以 URL `{id}` 为准,防止 body 串改。
  - 密码 / 软删 / 登录时间 / 失败计数 等字段**不接受**通过本接口改。

### 3.4 软删(需密码确认)

`DELETE /api/users/{id}`

- **权限**:自己可软删自己;`BOSS` 可软删任何人。
- **请求体** `UserDeleteRequest`:
  ```json
  { "password": "secret123" }
  ```

### 3.5 修改密码

`PATCH /api/users/{id}/password`

- **权限**:`currentUserId == id`(只能改自己的密码)
- **请求体** `UserPasswordRequest`:
  ```json
  {
    "oldPassword": "secret123",
    "newPassword": "newSecret456"
  }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `oldPassword` | string | ✅ | 非空 |
  | `newPassword` | string | ✅ | 长度 6-20 |

### 3.6 看订单

`GET /api/users/{id}/purchases`

- **权限**:自己看自己的;`BOSS` 看任意人;其他情况 `403 FORBIDDEN`。
- **响应 data**:`List<PurchaseResponse>`,见 [§ 5.5](#55-订单响应-purchaseresponse)。

---

## 4. 图书管理 `/api/books`

`Book` 实体字段:
```json
{
  "id": 1,
  "title": "深入理解 Java 虚拟机",
  "author": "周志明",
  "isbn": "9787111543246",
  "price": 99.00,
  "publishedDate": "2024-01-15",
  "createdTime": "2024-01-15T10:00:00",
  "updatedTime": "2024-01-15T10:00:00",
  "stockQuantity": 100
}
```

### 4.1 简单分页列表

`GET /api/books/list?page=1&size=10`

- **Query**:
  | 字段 | 类型 | 默认 | 说明 |
  |---|---|---|---|
  | `page` | int | 1 | 页码 |
  | `size` | int | 10 | 每页条数 |
- **响应 data**:`PageResult<Book>`

### 4.2 新建图书

`POST /api/books/created`

- **请求体** `BookSaveRequest`:
  ```json
  {
    "title": "深入理解 Java 虚拟机",
    "author": "周志明",
    "isbn": "9787111543246",
    "price": 99.00,
    "stockQuantity": 100,
    "publishedDate": "2024-01-15"
  }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `title` | string | ✅ | 长度 ≤ 200 |
  | `author` | string | ✅ | 长度 ≤ 100 |
  | `isbn` | string | ✅ | 正则 `^[0-9Xx-]{10,20}$` |
  | `price` | BigDecimal | ✅ | ≥ 0 |
  | `stockQuantity` | Integer | ✅ | ≥ 0 |
  | `publishedDate` | date | ✅ | — |

### 4.3 按 ISBN 查询

`GET /api/books/{isbn}`

- **响应 data**:`Book`(不存在时 `data=null`,code=200)

### 4.4 按 ISBN 修改

`PATCH /api/books/{isbn}`

- **请求体** `BookUpdateRequest`(字段全可选):
  ```json
  {
    "title": "深入理解 Java 虚拟机 (第三版)",
    "author": "周志明",
    "price": 109.00,
    "createdDate": "2024-01-15",
    "publishedDate": "2024-01-15"
  }
  ```

  > **库存字段已移出本接口**,改用 § 4.5 独立调整。原因:交易链路(下单/取消/付款)
  > 会持续改动可用库存,管理端直接覆盖会与在途预占冲突,且无法审计。
  >
  > 修改图书后服务端会同步刷新 Redis 库存 —— **保留在途预占**,
  > 按 `可用库存 = DB库存 − 在途预占` 重算,不会让未支付订单的预占消失。

### 4.5 调整库存(盘点语义)

`PATCH /api/books/{isbn}/stock`

- **请求体** `BookStockAdjustRequest`:
  ```json
  { "stockQuantity": 150 }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `stockQuantity` | Integer | ✅ | ≥ 0(绝对值,非增量) |

- **机制**:把 DB `books.stock_quantity` 覆盖为该值(库存真值),随后同步 Redis
  `stock = 新库存 − reserved`。若在途预占超过新库存,可用库存按 0 计(不出现负数)。
- **语义**:盘盈入库 / 盘亏修正。与交易链路的增量扣减相互独立,便于单独授权与审计。

### 4.6 按 ISBN 删除

`DELETE /api/books/deleted/isbn/{isbn}`

### 4.7 多条件组合搜索

`GET /api/books?title=&author=&minPrice=&maxPrice=&minStock=&maxStock=&publishedDate=&page=1&size=10`

- **Query 参数** `BookSearchRequest`(全部可选,空串 = 不参与):
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `title` | string | 模糊 |
  | `author` | string | 模糊 |
  | `minPrice` / `maxPrice` | BigDecimal | 价格区间 |
  | `minStock` / `maxStock` | int | 库存区间 |
  | `publishedDate` | date | 精确匹配 |
  | `page` | int | 默认 1 |
  | `size` | int | 默认 10,上限 100 |

- **响应 data**:`PageResult<Book>`

### 4.8 按出版日期粒度查询

`GET /api/books/search/publishedDate/by?year=2024&month=6&day=15&page=1&size=10`

- **粒度**:3 级 — `year` / `year+month` / `year+month+day`
- **校验**:`year` 必填(1900-2100);`month` / `day` 可选但必须**从大到小连续**(`month=6&day=15` 合法,`day=15&hour=10` 跳级 → 400)。

### 4.9 按创建时间粒度查询

`GET /api/books/search/CreatedTime/by?year=2024&month=6&day=15&hour=10&minute=30&page=1&size=10`

- **粒度**:5 级 — `year` / `year+month` / `year+month+day` / `year+month+day+hour` / `year+month+day+hour+minute`
- 校验同上。

### 4.10 按更新时间粒度查询

`GET /api/books/search/UpdatedTime/by?year=...&...`

- 同 § 4.8,字段语义换成 `updatedTime`。

---

## 5. 订单管理 `/api/purchases`

所有接口需要登录,订单按 `orderNumber`(Snowflake ID, Long)索引。

### 5.1 下单

`POST /api/purchases`

- **请求体** `PurchaseRequest`:
  ```json
  {
    "items": [
      { "bookId": 1, "quantity": 2 },
      { "bookId": 5, "quantity": 1 }
    ]
  }
  ```
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|:---:|---|
  | `items` | List | ✅ | 至少 1 项 |
  | `items[].bookId` | Integer | ✅ | 图书 ID |
  | `items[].quantity` | Integer | ✅ | > 0 |

  > **不要传 userId** — 真实用户身份从 token 解析,客户端伪造无效。

- **机制**:
  1. 校验用户状态(Redis 缓存)
  2. 对每个 item:DB 读价格 + Redis Lua 预占库存(任一环节失败 → 反向释放**已预占的全部** bookId)
  3. 插 `orders` + `order_items`(DB 事务)
  4. 写 Redis 双 key:Hash 订单数据 + ZSet 历史/超时索引
     (Redis 写失败同样触发第 2 步的反向释放,不留库存泄漏)
  5. 订单 30 分钟未支付 → 定时任务自动关单(CANCELLED + 释放库存)

- **响应 data**:`int`(新订单 ID)

- **失败码**:
  - `401 UNAUTHORIZED` — 用户已被禁
  - `400 BAD_REQUEST` — quantity ≤ 0 / bookId 非法 / items 为空
  - `404 NOT_FOUND` — 图书不存在
  - `409 CONFLICT` — 库存不足(下单预占阶段)

  > **库存真值说明**:Redis 只做"预占";真实库存以 DB `books.stock_quantity` 为准,
  > **付款成功时才扣减**。极端情况(Redis 淘汰/重启导致预占丢失)下,
  > 可能多个用户同时下单成功,但**只有库存足够的那个能支付成功**,其余在 [§ 5.4](#54-支付订单) 收到 `409 CONFLICT` 并保持订单 `PENDING`。

### 5.2 订单详情

`GET /api/purchases/{orderNumber}`

- **路径**:`orderNumber` long
- **权限**:仅订单所有者可看(Controller 校验)。
- **响应 data** `PurchaseResponse`,见 [§ 5.5](#55-订单响应-purchaseresponse)

### 5.3 取消订单

`DELETE /api/purchases/{orderNumber}`

- **权限**:仅订单所有者。
- **机制**:状态 → `CANCELLED`,Redis 释放库存预占。

### 5.4 支付订单

`PATCH /api/purchases/{orderNumber}/pay?paymentMethod=ALIPAY`

- **Query**:
  | 字段 | 类型 | 默认 | 说明 |
  |---|---|---|---|
  | `paymentMethod` | string | `DEFAULT` | 预留字段(ALIPAY / WECHAT / ...) |

- **机制**(付款是**唯一**扣减 DB 库存的时机):
  1. `SELECT ... FOR UPDATE` 锁订单行,校验所有权 + 状态为 `PENDING`
  2. 逐 item 原子扣减 DB 库存:
     `UPDATE books SET stock_quantity = stock_quantity - N WHERE id = ? AND stock_quantity >= N`
     — 任一项返回 0 行 → `409 CONFLICT`,**整个付款回滚**,订单保持 `PENDING`
  3. 状态守卫更新订单:`UPDATE ... SET order_status = 'PAID' WHERE order_number = ? AND order_status = 'PENDING'`
  4. Redis 确认预占(`reserved` 减,`stock` 不变 — 预占时已减过)
  5. 事务提交后再更新 Redis Hash 状态 + 清理超时索引(`afterCommit` 钩子)

- **失败码**:
  - `403 FORBIDDEN` — 不是订单所有者
  - `409 CONFLICT` — 订单已取消/已支付,或**库存不足**(并发售罄)

- **当前未接支付网关**,仅翻状态 + 确认库存。真实接入时需先调网关,等回调验签后调本接口。

### 5.5 订单响应 `PurchaseResponse`

```json
{
  "orderNumber": "1234567890123456",
  "status": "PENDING",
  "totalAmount": 297.00,
  "items": [
    {
      "bookId": 1,
      "quantity": 2,
      "price": 99.00,
      "subtotal": 198.00
    }
  ]
}
```

- `status`: `PENDING`(待支付)/ `PAID`(已支付)/ `CANCELLED`(已取消)/ `TIMEOUT`(超时取消)
- `price` 是下单时的**快照价**,不受后续 book 调价影响。
- 不暴露内部字段:`id` / `orderId` / `createdTime` / `userId` 等。

---

## 6. 数据字典(枚举)

### 6.1 `UserRole` — 用户角色

| code | name | 描述 |
|---:|---|---|
| 0 | `USER` | 普通用户 |
| 1 | `ADMIN` | 管理员 |
| 2 | `BOSS` | 老板 |

### 6.2 `UserStatus` — 用户状态

| code | name | 描述 |
|---:|---|---|
| 0 | `ACTIVE` | 正常 |
| 1 | `INACTIVE` | 未激活 |
| 2 | `SUSPENDED` | 已暂停/封号 |

### 6.3 `OrderStatus` — 订单状态

| code | name | 描述 |
|---:|---|---|
| 0 | `PENDING` | 待支付 |
| 1 | `PAID` | 已支付 |
| 2 | `CANCELLED` | 已取消 |
| 3 | `TIMEOUT` | 超时取消 |

---

## 7. 端点速查表

| Method | URL | Auth | 说明 |
|---|---|:---:|---|
| POST | `/api/captcha/login` | ❌ | 获取登录验证码 |
| POST | `/api/users/register` | ❌ | 注册 |
| POST | `/api/users/login` | ❌ | 登录 |
| POST | `/api/users/logout` | ✅ | 登出 |
| GET | `/api/users/list` | ✅ | 列表查询(分页+多条件) |
| GET | `/api/users/{id}` | ✅ | 详情 |
| PATCH | `/api/users/{id}` | ✅ | 更新 |
| DELETE | `/api/users/{id}` | ✅ | 软删(需密码) |
| PATCH | `/api/users/{id}/password` | ✅ | 改密 |
| GET | `/api/users/{id}/purchases` | ✅ | 看订单 |
| GET | `/api/books/list` | ❌ | 简单分页 |
| POST | `/api/books/created` | ✅ | 新建 |
| GET | `/api/books/{isbn}` | ❌ | 详情 |
| PATCH | `/api/books/{isbn}` | ✅ | 修改 |
| PATCH | `/api/books/{isbn}/stock` | ✅ | 调整库存(盘点) |
| DELETE | `/api/books/deleted/isbn/{isbn}` | ✅ | 删除 |
| GET | `/api/books` | ❌ | 多条件搜索 |
| GET | `/api/books/search/publishedDate/by` | ❌ | 按出版日期粒度 |
| GET | `/api/books/search/CreatedTime/by` | ❌ | 按创建时间粒度 |
| GET | `/api/books/search/UpdatedTime/by` | ❌ | 按更新时间粒度 |
| POST | `/api/purchases` | ✅ | 下单 |
| GET | `/api/purchases/{orderNumber}` | ✅ | 订单详情 |
| DELETE | `/api/purchases/{orderNumber}` | ✅ | 取消订单 |
| PATCH | `/api/purchases/{orderNumber}/pay` | ✅ | 支付订单 |

> ❌ = 白名单(无需 token) / ✅ = 需要 `Authorization: Bearer <token>`

---

## 8. 附录

### 8.1 登录完整流程

```
┌─────────┐  POST /api/captcha/login?uuid=X   ┌────────┐
│ Frontend│ ─────────────────────────────────→ │Backend │
│         │ ←───── image/png ────────────────── │        │
│         │                                     │ 写Redis│
│         │                                     │  TTL=3m│
└─────────┘                                     └────────┘
       ↓ (用户输入 username + password + captcha)
┌─────────┐  POST /api/users/login             ┌────────┐
│ Frontend│ ─────────────────────────────────→ │Backend │
│         │                                     │ 验证captcha│
│         │                                     │ +username绑定│
│         │                                     │ BCrypt密码   │
│         │                                     │ Redis缓存用户│
│         │ ←───── { token, username, role } ── │ 写lastLogin  │
└─────────┘                                     └────────┘
       ↓ (后续请求带 Authorization)
┌─────────┐  GET /api/books/list               ┌────────┐
│ Frontend│  Authorization: Bearer <token>     │Backend │
│         │ ─────────────────────────────────→ │  JwtAuthFilter│
│         │                                     │  解析+校验jti黑名单 │
│         │ ←───── { code, msg, data } ──────── │  业务处理  │
└─────────┘                                     └────────┘
```

### 8.2 库存预占流程

```
下单 → bookMapper.selectById(非锁读)→ 拿价格
     → BookInventoryService.tryReserve(Lua 原子)
        ├─ 成功 → reservedBookIds 记录,继续下一个 item
        └─ 失败 → throw → catch 中对已预占的 book 反向 release
     → 全部成功 → @Transactional 插 orders + order_items
     → 写 Redis(仅超时索引,5 次往返 → 1 次):
        └─ ZSet: tmlibrary:user:byId:{userId}:orders:pending:expire:idx ← 超时索引
     ⚠️ 以上任何一步失败(含 Redis 写)→ 统一反向 release 所有已预占库存

     ℹ️ 订单详情/历史不再写 Redis 缓存 —— 此前那两类 key 只写不读,
        既浪费每次下单 7 次 Redis 往返,又制造"DB 与 Redis 可能漂移却无人发现"的隐患。
        订单一律以 DB 为准。
```

30 分钟未支付 → `OrderExpireScheduler`(每 60s 扫描,Redis SETNX 分布式锁)→ 调 `cancelExpiredOrder`
→ 先 release 库存 → 状态守卫 UPDATE(`WHERE order_status='PENDING'`)→ 提交 → afterCommit 更新 Redis。

### 8.3 运维端点(Actuator)

| 端点 | 说明 |
|---|---|
| `GET /actuator/health` | 健康检查 |
| `GET /actuator/metrics` | 可用指标列表 |
| `GET /actuator/metrics/{name}` | 单项指标详情 |

业务指标:

| 指标名 | 含义 | 处置建议 |
|---|---|---|
| `tmlibrary_inventory_drift_total` | Lua 返回负值,Redis 库存已偏离真值 | 查 `INVENTORY DRIFT` 日志定位根因;对账任务会修复数值 |
| `tmlibrary_inventory_reconcile_repaired_total` | 对账任务发现并修复了不一致 | 持续增长说明漂移在反复发生,需定位来源 |
| `tmlibrary_order_compensate_failed_total` | 下单失败后回滚预占也失败 | **任何非零值都应告警**,需人工对账 |

> ⚠️ **安全提示**:`JwtAuthFilter` 只注册在 `/api/*` 上,`/actuator/**` **不走 JWT 鉴权**。
> 当前仅暴露 `health` 与 `metrics`;生产环境建议改用独立 management 端口,
> 或在网关/反向代理层限制访问来源。

### 8.4 关键设计

- **JWT 黑名单**:登出时按 token 剩余 TTL 写入 `tmlibrary:auth:byJti:{jti}:blackList`,过期自动清。
- **库存双轨**:
  - **DB `books.stock_quantity` = 真值(物理未售库存)** — 只在**付款成功**时原子扣减
    (`WHERE stock_quantity >= N`,不足则拒绝,返回 409)
  - **Redis Hash `stock` / `reserved` = 预占缓存** — 下单 `stock -= N, reserved += N`;
    支付 `reserved -= N`;取消/超时 `stock += N, reserved -= N`
  - 好处:Redis 淘汰/重启不会造成真实超卖(付款时由 DB 条件更新兜底)
- **Redis Key 统一规范**:`tmlibrary:{domain}:by{Id|Username|Isbn|Jti|Uuid|Number}:{keyId}:{feature}`,
  全部集中在 `common/redis/RedisKeys.java`,禁止业务代码拼接字符串。
- **缓存失效**:所有 Redis 写操作注册在 `afterCommit` 钩子中(DB 回滚不会写脏缓存);
  修改用户/图书时同步失效「详情缓存 + 状态缓存 + 库存 Hash」三类 key。
- **订单数据** 走 Redis Hash(字段独立更新,改状态只动 `status` 字段)。
- **历史/超时索引** 走 Redis ZSet(score = 时间戳),方便范围查询。
- **状态流转**统一走带守卫的 UPDATE(`WHERE order_status = 期望前置状态`),
  防止「已支付订单被超时任务取消」这类并发越权流转。
- **密码哈希**:BCrypt cost=10,后端唯一处理,前端永远传明文。
- **登录失败锁定**:单条原子 SQL 完成「计数 +1」与「达阈值时加锁」,
  锁定到期后自动重置计数;登录失败响应统一文案,防账号枚举。
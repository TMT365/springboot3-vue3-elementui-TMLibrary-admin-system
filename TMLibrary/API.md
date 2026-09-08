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

- 除白名单(`/api/users/login`、`/api/users/register`、`/api/captcha/**`)外,所有接口**必须**携带 `Authorization: Bearer <token>`。
- token 由 `POST /api/users/login` 返回,前端保存到 localStorage。
- `GET /api/users/login` 失败或 token 过期/被登出 → HTTP 401,`code=401, msg=缺少 Authorization 头 / 令牌无效 / Token已登出作废`。
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
| 409 | `CONFLICT` | 状态冲突(如重复取消) |
| 422 | `UNPROCESSABLE_ENTITY` | 业务校验失败 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误 |

### 1.5 错误响应示例

```json
{
  "code": 401,
  "msg": "密码错误",
  "data": null
}
```

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
  - `401 UNAUTHORIZED` — 用户不存在 / 密码错误
  - `403 FORBIDDEN` — 账户未激活 / 软删除 / 连续失败锁定(15 分钟)
  - `500 INTERNAL_ERROR` — Redis 验证码 JSON 解析异常

### 2.4 登出

`POST /api/users/logout`

- **请求头**:`Authorization: Bearer <token>`
- **请求体**:无
- **响应 data**:`null`
- **机制**:解析 token,提取 `jti`,按剩余 TTL 写入 Redis 黑名单(同 token 不能再用)。

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
  | `role` | int | 精确(`0` USER / `1` ADMIN / `2` BOSS),默认 `0` |
  | `status` | int | 精确(`0` ACTIVE / `1` INACTIVE / `2` SUSPENDED),默认 `0` |
  | `phoneNumber` | string | 模糊查询 |
  | `lastLoginIp` | string | 精确 |
  | `failedLoginAttempts` | int | 精确 |
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
- **响应 data**:`UserVo`(含敏感字段脱敏:`realName` / `phoneNumber` 仅保留前 N 位,余下 `*`)

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
- **响应 data**:`List<PurchaseResponse>`,见 [§ 4.5](#45-订单响应-purchaseresponse)。

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
    "stockQuantity": 150,
    "createdDate": "2024-01-15",
    "publishedDate": "2024-01-15"
  }
  ```

### 4.5 按 ISBN 删除

`DELETE /api/books/deleted/isbn/{isbn}`

### 4.6 多条件组合搜索

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

### 4.7 按出版日期粒度查询

`GET /api/books/search/publishedDate/by?year=2024&month=6&day=15&page=1&size=10`

- **粒度**:3 级 — `year` / `year+month` / `year+month+day`
- **校验**:`year` 必填(1900-2100);`month` / `day` 可选但必须**从大到小连续**(`month=6&day=15` 合法,`day=15&hour=10` 跳级 → 400)。

### 4.8 按创建时间粒度查询

`GET /api/books/search/CreatedTime/by?year=2024&month=6&day=15&hour=10&minute=30&page=1&size=10`

- **粒度**:5 级 — `year` / `year+month` / `year+month+day` / `year+month+day+hour` / `year+month+day+hour+minute`
- 校验同上。

### 4.9 按更新时间粒度查询

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
  2. 对每个 item:DB 读价格 + Redis Lua 预占库存(失败时反向释放已预占的)
  3. 插 `orders` + `order_items`(DB 事务)
  4. 写 Redis 双 key:Hash 订单数据 + ZSet 历史/超时索引
  5. 订单 30 分钟未支付 → 定时任务自动关单(CANCELLED + 释放库存)

- **响应 data**:`int`(新订单 ID)

- **失败码**:
  - `401 UNAUTHORIZED` — 用户已被禁
  - `400 BAD_REQUEST` — quantity ≤ 0 / bookId 非法
  - `404 NOT_FOUND` — 图书不存在 / 库存不足

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

- **机制**:状态 → `PAID`,Redis 确认库存(`reserved` 减,`stock` 不变 — 在下单时已减过)。
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
     → 写 Redis 双 key:
        ├─ Hash: tmlibrary:order:{orderNumber}      ← 订单数据
        ├─ ZSet: tmlibrary:user:{userId}:history    ← 时间索引
        └─ ZSet: tmlibrary:user:{userId}:expire     ← 超时索引
```

30 分钟未支付 → `OrderExpireScheduler`(每 60s 扫描,Redis SETNX 分布式锁)→ 调 `cancelExpiredOrder` → 状态置 CANCELLED + release 库存。

### 8.3 关键设计

- **JWT 黑名单**:登出时按 token 剩余 TTL 写入 `tmlibrary:auth:jwt:blackList:{jti}`,过期自动清。
- **库存**:`available_stock` (stock) + `reserved_stock` (reserved) 在 Redis Hash 同一 book 下;下单 → `stock -= N, reserved += N`;支付 → `reserved -= N`;取消/超时 → `stock += N, reserved -= N`。
- **订单数据** 走 Redis Hash(字段独立更新,改状态只动 `status` 字段)。
- **历史/超时索引** 走 Redis ZSet(score = 时间戳),方便范围查询。
- **密码哈希**:BCrypt cost=10,后端唯一处理,前端永远传明文。
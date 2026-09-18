# TMLibrary 接口文档

> Spring Boot 4.1.0 + Java 25 + 纯 MyBatis + Redis + JWT
>
> Base URL: `http://localhost:8080`(开发环境)
>
> 所有请求和响应均使用 `application/json`。
>
> (2026-09 起验证码也是 JSON —— 返回 `{image: "data:image/png;base64,...", expiresAt}`,
> 不再直接返回 image/png 二进制。老文档里"除验证码图片外"的说法已过时。)

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

### 1.6 Jackson 序列化约定(Spring Boot 自动配置)

后端没有自定义 `ObjectMapper`,遵循 Spring Boot 4.x 默认行为:

| Java 类型 | JSON 形态 | 示例 |
|---|---|---|
| `BigDecimal` | **number** | `99.00` |
| `Integer` / `int` | number | `0` / `42` |
| `Long` | number | `1234567890123456`(订单号精度) |
| `Enum` | **字符串(name())** | `"PENDING"` / `"USER"` |
| `LocalDate` | `yyyy-MM-dd` | `"2024-01-15"` |
| `LocalDateTime` | `yyyy-MM-ddTHH:mm:ss` | `"2024-01-15T10:00:00"` |
| `Instant` | ISO-8601 UTC | `"2026-09-13T13:20:00Z"` |
| `String` | 字符串 | — |

**⚠️ 易踩的坑(后端 DTO 之间不一致)**:

- `LoginResponse.role` 字段是 `UserRole` 枚举 → JSON **`"USER"`** (字符串)
- `UserVo.role` / `UserVo.status` 字段是 `Integer` → JSON **`0` / `1` / `2`** (数字)
- `PurchaseResponse.status` 字段是 `OrderStatus` 枚举 → JSON **`"PENDING"`** (字符串)
- `Book.price` / `PurchaseResponse.totalAmount` 等 `BigDecimal` → JSON **number**

> **重要**:`UserVo` 用 Integer 而不是枚举类型,**是因为管理端搜索条件要按 code 精确筛选
> (BOSS 看 role=2,ADMIN 看 role=0)**;如果改成枚举字段,搜索 query 就拿不到 code 了。
> LoginResponse 用枚举是为了 JWT 反序列化直观 (`UserRole.fromString(claims.get("role"))`)。

---

## 2. 鉴权模块 `/api`

### 2.1 获取登录验证码

`POST /api/captcha/login?uuid=<uuid>`

- **请求体**：
  ```json
  { "username": "alice" }
  ```
- **响应 data** `CaptchaResponse`(2026-09 改为 JSON 对象,替代原 image/png 二进制):
  ```json
  {
    "image": "data:image/jpeg;base64,/9j/4AAQ...",
    "expiresAt": 1763325600000
  }
  ```
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `image` | string | base64 data URI,可直接作为 `<img src>` 使用 |
  | `expiresAt` | **string** | 过期时间戳(毫秒,**用 String 不用 long**,见下方) |

  > **为什么 `expiresAt` 是 String 不是 long?**
  >
  > JS 的 `Number` 是 64-bit 浮点,安全整数范围 `±2^53 ≈ ±9×10^15`;
  > 当前毫秒时间戳 `~1.7×10^12` 还在范围内,但:
  > - Java `long` 范围 `±9.2×10^18`,某些序列化路径(微秒/纳秒精度)会越界
  > - 前端某些 JSON 解析器(老浏览器)会丢精度
  > - String 跨网络无损,前端 `Number(str)` 解析毫秒时间戳永远安全
  >
  > 这是「string at the boundary」惯例 —— 内部 `long` 算时间,跨网络用 String 传输。
- **白名单**:无需 token。
- **说明**:
  - `uuid` 由前端生成(推荐 UUID),作为 captcha 在 Redis 的 key 后缀。
  - 验证码和请求时的 `username` 绑定,3 分钟 TTL。
  - 同一个 uuid 只能验证一次(登录成功后被 Redis 删除)。
  - 验证码字符集:**大写字母 + 数字,排除 l/1/I/0/O 五个易混字符**(剩 32 字符),长度 4;图像尺寸 120×48。
    早版本用 62 个大小写字母+数字,因 l/1/I 字号宽度差异大,会裁字;
    2026-09 改为「A-Z 除 I/O + 数字 2-9」+ 26px 粗体 + 抗锯齿,信息熵 log₂(32⁴) ≈ 20 bit。
  - **Redis key(2026-09 加 username)**:`tmlibrary:captcha:login:{username}:{uuid}:code`(见 `RedisKeys.CAPTCHA_LOGIN`)。key 里带 username 是安全改进:① 即使前端 bug / UUID 碰撞导致 A、B 用了同一 uuid,key 不同也互不污染;② 用户改名字后,可以用 `KEYS tmlibrary:captcha:login:{oldUsername}:*:code` 一次性清掉旧名字的所有 captcha。

### 2.2 获取注册验证码(2026-09 新增)

`POST /api/captcha/register?uuid=<uuid>`

- **请求体**:
  ```json
  { "username": "alice" }
  ```
- **响应 data**:`CaptchaResponse`(同 § 2.1)
- **白名单**:无需 token。
- **说明**:
  - 跟 § 2.1 同一份 `CaptchaUtil` 生成图片,但 Redis key 走 `tmlibrary:captcha:register:{username}:{uuid}:code`
    —— 跟 login captcha 分开(路径段不同),登录 captcha 不能拿去注册,反之亦然
  - key 里同样带 username(2026-09 安全改进),理由同 § 2.1

### 2.3 注册

`POST /api/users/register`

- **白名单**:无需 token。
- **请求体** `UserRegisterRequest`:
  ```json
  {
    "username": "alice",
    "password": "secret123",
    "email": "alice@example.com",
    "phoneNumber": "13800000000",
    "captcha": "aB3x",
    "uuid": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `username` | string | ✅ | 长度 1-50 |
  | `password` | string | ✅ | 长度 6-20(后端 BCrypt 哈希) |
  | `email` | string | ✅ | 邮箱格式 |
  | `phoneNumber` | string | ✅ | 长度 11 |
  | `captcha` | string | ✅ | 长度 = `CaptchaUtil.LENGTH`(4) |
  | `uuid` | string | ✅ | 跟 § 2.2 申请时同一个 uuid |

- **校验流程**:后端先读 `tmlibrary:captcha:register:{username}:{uuid}:code`,校验:
  1. key 存在(`404 NOT_FOUND`)—— 因为 key 里有 username,**请求 username 跟 captcha 申请时不一致直接 404**,不用单独比对
  2. captcha 跟申请时一致(`400 BAD_REQUEST`「验证码错误」)
  3. defense-in-depth 再比对一次 value 里的 username 跟请求(`400 BAD_REQUEST`「验证码与账号不匹配」)
  4. 全部通过 → 删 key(防重放)→ 创建用户 → 返回新用户 ID

- **响应 data**:`int`(新用户 ID)

### 2.4 登录

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
  | `ipAddress` | string | ❌ | 前端不传,后端从 `request.getRemoteAddr()` 拿 |

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
  | `loggedOut` | boolean | 后端固定返回 `true`,前端据此清 localStorage 并跳转 |
  | `message` | string | 提示文案,可直接展示 |
  | `redirectUrl` | string | 建议前端跳转的路径 |
  | `logoutAt` | Instant | 服务端登出时间(ISO-8601,见 § 1.6) |

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

- **响应 data**:`PageResult<UserVo>`,`UserVo` 字段见 [§ 3.2 详情](#32-详情)(包括脱敏规则和序列化方式)。

### 3.2 详情

`GET /api/users/{id}`

- **路径**:`id` int
- **权限**:自己 / `ADMIN` / `BOSS` 可看,其他角色 `403 FORBIDDEN`。
- **响应 data**:`UserVo`,敏感字段已脱敏:
  - `realName` — 保留首字符,余下 `*`(`张三丰` → `张**`);为 `null` 时后端兜底为 `""`(空串,**不是 null**)
  - `phoneNumber` — 保留前 7 位,余下 `*`(`13800001234` → `1380000****`);为 `null` 时后端兜底为 `""`
  - `email` — 本地部分保留首字符,域名完整(`alice@example.com` → `a****@example.com`);为 `null` 时后端兜底为 `""`
  - `avatarUrl` — 头像 URL,可能为 `null`(未设置头像),前端按 fallback 处理

  **序列化重要**:
  - `role` / `status` 字段是 **`Integer` (0/1/2)**,JSON 序列化为 **number**,**不是字符串枚举** —— 跟 `LoginResponse.role` 不一样(那边是枚举 → 字符串)
  - 原因:管理端搜索要按 code 精确筛(role=2 / status=0 等),见 § 1.6 的"易踩坑"
  - 前端若要用枚举名(`'ACTIVE'` / `'ADMIN'`),需要自己 number → string 映射;前端 `utils/safeUser.ts` 已实现

  ```json
  {
    "id": 42,
    "username": "alice",
    "realName": "张**",
    "email": "a****@example.com",
    "avatarUrl": null,
    "role": 1,
    "status": 0,
    "phoneNumber": "1380000****",
    "createdTime": "2024-01-15T10:00:00",
    "updatedTime": "2024-01-15T10:00:00",
    "lastLoginTime": "2026-09-13T13:20:00",
    "lastLoginIp": "192.168.1.1",
    "failedLoginAttempts": 0,
    "accountLockedUntil": null,
    "deletedAt": null
  }
  ```

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
  - `id` 字段**忽略**:以 URL `{id}` 为准,防止 body 串改(后端 DTO 里**确实存在** `id` 字段,前端可不传)。
  - 密码 / 软删 / 登录时间 / 失败计数 等字段**不接受**通过本接口改。
  - **`status` / `role` 字段是 `Integer`**(JSON number),不是字符串枚举 —— 同 § 3.2 UserVo。

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
  "stockQuantity": 100,
  "categoryId": 201
}
```

> `categoryId` 指向 `book_categories.id` 里的**小类**(见 § 4.11);
> `null` = 未分类(分类功能上线前录入的老书)。

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
    "publishedDate": "2024-01-15",
    "categoryId": 201
  }
  ```
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|:---:|---|
  | `title` | string | ✅ | 长度 ≤ 200 |
  | `author` | string | ✅ | 长度 ≤ 100 |
  | `isbn` | string | ✅ | 正则 `^[0-9Xx-]{10,20}$` |
  | `price` | BigDecimal | ✅ | ≥ 0;**JSON 序列化为 number**(见 § 1.6) |
  | `stockQuantity` | Integer | ✅ | ≥ 0 |
  | `publishedDate` | date | ✅ | — |
  | `categoryId` | Integer | ❌ | 小类 id;不传 = 未分类 |

  > 传了 `categoryId` 时,该分类的 `book_count` 会在**同一个事务里 +1**。

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
    "publishedDate": "2024-01-15",
    "categoryId": 204
  }
  ```
  - `price` 是 `BigDecimal`,JSON 序列化为 **number**(见 § 1.6)。
  - `categoryId` **传了才改**,且只接受 > 0 的值(即"改成某个分类");
    不传 / 传 0 都表示"保持原分类" —— 本接口**不支持把分类置空**。
    改分类时旧分类 `book_count` −1、新分类 +1,与图书更新同一事务。

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

`GET /api/books?title=&author=&keyword=&minPrice=&maxPrice=&minStock=&maxStock=&publishedDate=&categoryId=&page=1&size=10`

- **Query 参数** `BookSearchRequest`(全部可选,空串 = 不参与):
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `title` | string | 书名模糊 |
  | `author` | string | 作者模糊 |
  | `keyword` | string | **关键字**:书名 / 作者 / ISBN **任一**命中即可(OR)。商城搜索框用这个 |
  | `minPrice` / `maxPrice` | BigDecimal | 价格区间 |
  | `minPrice` / `maxPrice` | BigDecimal | 价格区间 |
  | `minStock` / `maxStock` | int | 库存区间 |
  | `publishedDate` | date | 精确匹配 |
  | `categoryId` | int | 按分类筛选,**传大类时连同其所有子类一起命中**(商城点分类用) |
  | `page` | int | 默认 1 |
  | `size` | int | 默认 10,上限 100 |

  > `keyword` 与 `title` / `author` 这些**精确维度是 AND 叠加**:两个都传 = "关键字命中 **且** 书名匹配"。
  > `keyword` 内部那三个 LIKE 才是 OR —— 搜索框只给一个输入框,用户不区分自己输的是书名还是作者,
  > 用 AND 的话「周志明」(作者名)永远搜不到。
  >
  > 代价:三个 LIKE 都是前后模糊(`'%x%'`),**走不了索引**。图书表量级小可以接受,
  > 真要上量得换全文索引或搜索引擎。

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

### 4.11 图书分类树

`GET /api/books/categories`

- **鉴权**:❌ **免登录**(商城侧栏、分类 chips、后台表单都要读)。
  挂在 `/api/books` 前缀下是为了命中 `JwtAuthFilter` 里 `("/api/books", GET)` 的白名单;
  字面量段 `categories` 的匹配优先级高于变量段 `{isbn}`,不会和 § 4.3 撞车。
- **响应 data**:`List<BookCategoryNode>`,只有两级(大类 → 小类):
  ```json
  [
    {
      "id": 2,
      "name": "计算机",
      "icon": "Cpu",
      "bookCount": 7,
      "children": [
        { "id": 201, "name": "编程语言", "icon": null, "bookCount": 4, "children": null },
        { "id": 202, "name": "算法与数据结构", "icon": null, "bookCount": 1, "children": null }
      ]
    }
  ]
  ```
  | 字段 | 类型 | 说明 |
  |---|---|---|
  | `icon` | string\|null | 大类图标名(Element Plus 图标,如 `Cpu`);小类恒为 `null` |
  | `bookCount` | int | **大类是累加值**(自身直挂 + 所有子类之和),小类是自身直挂数 |
  | `children` | array\|null | 大类的子类列表(可能为空数组);小类为 `null` |

  > `bookCount` 存在 `book_categories.book_count` 字段里,由图书的增 / 删 / 改分类
  > 在同一事务内维护(见 § 4.2、§ 4.4、§ 4.6)。
  > 只在服务端做增量,不依赖定时重算;漂移时可用 `BookCategoryMapper.recountAll()` 修复
  > (走 SQL 修完要手动 `DEL tmlibrary:book:byScope:categories:tree`,见下)。

- **缓存**:整棵树缓存在 Redis 单 key `tmlibrary:book:byScope:categories:tree`,TTL 30 分钟。
  - 读:cache-aside —— 命中直接返回,未命中回源组装后写回(Redis 异常时静默回源,不影响可用性)
  - 写:**先删该 key,再写 MySQL**(不是"更新缓存");删除点是 § 4.12 新建分类、
    以及 § 4.2 / § 4.4 / § 4.6 里图书增删改引起的计数变化
  - 不做延迟双删 —— 第二次删除的时序问题留给后续 MQ(订阅 binlog)在事务提交后统一失效
  - TTL 只作兜底:直接改库、漏删、异常路径靠它自愈

### 4.12 新建分类

`POST /api/books/categories`

- **鉴权**:✅ 需要 `ADMIN` / `BOSS`(后端二次校验角色,普通用户 `403`)。
- **请求体** `CategoryCreateRequest`:
  ```json
  { "parentId": 2, "name": "函数式编程" }
  ```
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|:---:|---|
  | `parentId` | Integer | ❌ | **省略或 0 = 新建大类**;> 0 = 在该大类下新建小类 |
  | `name` | string | ✅ | 长度 ≤ 50,同父下唯一 |

- **响应 data**:`BookCategory` 实体(含 DB 回填的 `id` / `bookCount` / 时间戳)
- **幂等**:同父下重名**不报错**,直接返回已存在的那个分类(避免前端重复点击失败)。
- **失败码**:
  - `400 BAD_REQUEST` — 名字为空 / 超长 / 试图在**小类**下再建子类(最多两级)
  - `404 NOT_FOUND` — `parentId` 指向的大类不存在
  - `403 FORBIDDEN` — 非管理员

- **后台用法**:新增图书页的「小类」下拉开了 `allow-create`,
  用户敲一个新名字 → 先打本接口建分类拿到 `id` → 再带着 `categoryId` 提交 § 4.2。

### 4.13 搜索候选词(下拉建议)

`GET /api/books/suggest?q=计算&limit=8`

- **鉴权**:❌ **免登录**(商城搜索框未登录也要能用)。
  同样挂在 `/api/books` 前缀下蹭白名单;字面量段 `suggest` 优先于 `{isbn}`。
- **Query**:
  | 字段 | 类型 | 默认 | 说明 |
  |---|---|---|---|
  | `q` | string | — | 已输入内容;空白直接返回 `[]`(不打库) |
  | `limit` | int | 8 | 条数上限,夹到 `[1, 20]` |
- **响应 data**:`List<BookSuggestion>`
  ```json
  [
    { "type": "TITLE",  "text": "深入理解计算机系统", "isbn": "9787111544937", "hot": 4 },
    { "type": "AUTHOR", "text": "周志明",             "isbn": null,            "hot": 4 },
    { "type": "ISBN",   "text": "9787111543246",      "isbn": "9787111543246", "hot": 4 }
  ]
  ```
  | 字段 | 说明 |
  |---|---|
  | `type` | `TITLE` / `AUTHOR` / `ISBN`,前端据此选图标 |
  | `text` | 候选词本身,选中后回填输入框 |
  | `isbn` | 只有 `TITLE` / `ISBN` 有值(留给"点候选直接进详情"),作者为 `null` |
  | `hot` | **热度** = 该候选在**已支付订单**里的累计销量;无成交为 `0`(不会为 `null`) |

- **匹配**:书名 / 作者 / ISBN 任一命中即可(OR);`ISBN` 类候选只在输入
  **≥3 位且只含数字/X/横杠**时才查(省掉纯中文输入时的一次无用查询)。
- **排序**:`hot` 降序 → 类型优先级(书名 > 作者 > ISBN)→ 字面序。
  字面序兜底是故意的:同热度时结果必须稳定,否则翻页/重查会跳。
- **实现位置**:`SearchSuggestServiceImpl` —— 这是将来换 Elasticsearch 的**接缝**,
  入参出参形状不变,只替换取数与相关性打分。
  > 当前匹配走 MySQL `LIKE '%q%'`,**用不上索引**。候选词查询带 LIMIT、
  > 且前端有 280ms 防抖,图书表量级下可以接受。
- **个性化排序暂未实现**:本接口免登录,而 `JwtAuthFilter` 命中白名单后
  直接放行、**不解析 token**,服务端拿不到 userId。要做"按这个用户买过的分类加权",
  得先让白名单路径支持"有 token 就解析"(可选鉴权)。

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
     - 正常路径:扫描 Redis 超时索引发现订单
     - **DB 兜底**:随后按 `orders.status=PENDING AND expire_time < NOW()` 再扫一遍,
       覆盖 Redis 索引丢失(淘汰/重启/误删)的场景 —— 否则这些订单会永久停留在 PENDING

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
  "createdTime": "2026-09-16T21:36:33",
  "paidTime": null,
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

- `status`: `OrderStatus` **枚举**,JSON 序列化为字符串(`"PENDING"` / `"PAID"` / `"CANCELLED"` / `"TIMEOUT"`,见 § 8.3 / § 1.6)
- `totalAmount` / `price` / `subtotal` 是 `BigDecimal`,JSON 序列化为 **number**(见 § 1.6)
- `price` 是下单时的**快照价**,不受后续 book 调价影响
- `createdTime` = 下单时间(`orders.created_time`),`paidTime` = 支付时间(`orders.paid_time`)
  - 都是 `LocalDateTime` → `"yyyy-MM-ddTHH:mm:ss"`,**已截到秒**(`truncatedTo(SECONDS)`),
    前端可以直接 `new Date(...)`(Safari 解析不了超过 3 位的小数秒)
  - **`paidTime` 在未支付时为 `null`**(待支付 / 已取消 / 超时关闭都没有支付时刻),
    前端必须按 null 处理
  - `paidTime` 只有 `PENDING → PAID` 那一次流转会写(`updateStatusByOrderNumberGuard`
    里按 `toStatus = 1` 判定)。**不要用 `updated_time` 代替** —— 支付后的任何改动
    都会顶掉它,而"什么时候付的钱"是财务口径
- 不暴露内部字段:`id` / `orderId` / `userId` 等

---

## 6. 反馈工单 `/api/feedbacks`

> 2026-09 新增。全部端点**都要求登录** —— `/api/feedbacks/**` 不在 JWT 白名单里,
> 未登录会被过滤器直接挡下返回 401。
>
> 权限模型:普通用户只能看/回**自己提的**工单;ADMIN / BOSS 能看全部、
> 能改状态、能写**内部备注**(普通用户看不到)。

### 6.1 提交反馈

```
POST /api/feedbacks/mine
Auth: 需登录
```

**请求体** `FeedbackCreateRequest`

| 字段 | 类型 | 必填 | 校验 |
|---|---|:---:|---|
| `category` | string | ✅ | 必须是 `BUG` / `FEATURE` / `QUESTION` / `OTHER` 之一 |
| `title` | string | ✅ | ≤ 120 字符 |
| `body` | string | ✅ | ≤ 5000 字符 |

```json
{ "category": "BUG", "title": "搜索「设计」搜不到设计模式", "body": "复现步骤:1. 打开商城 2. …" }
```

**响应 data**:新建工单的 `id`(`Long`),前端拿到后直接跳详情页。

> `status` 固定初始化为 `0`(待处理),`priority` 固定 `1`(普通),都不由提交人指定。

### 6.2 我的反馈列表

```
GET /api/feedbacks/mine?page=1&size=10
Auth: 需登录
```

**响应 data** `PageResult<FeedbackSummary>` —— 每行只有摘要,**不含 `body`**
(列表不需要正文,带上会让响应体膨胀几倍)。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 工单 id |
| `category` | string | `BUG` / `FEATURE` / `QUESTION` / `OTHER` |
| `title` | string | 一句话概述 |
| `status` | number | `0` 待处理 / `1` 处理中 / `2` 已解决 / `3` 已关闭 |
| `priority` | number | `0` 低 / `1` 普通 / `2` 高 / `3` 紧急 |
| `replyCount` | number | 回复条数(**不含内部备注**) |
| `createdTime` / `updatedTime` | string | 时间戳 |

### 6.3 全部反馈列表(管理端)

```
GET /api/feedbacks/all?status=0&category=BUG&page=1&size=10
Auth: ADMIN / BOSS
```

比 6.2 多两个可选筛选参数 `status` / `category`,响应结构相同,但每行额外带
`username`(提交人)。普通用户调用返回 `403`。

### 6.4 反馈详情

```
GET /api/feedbacks/{id}
Auth: 需登录(本人或 ADMIN/BOSS)
```

**响应 data** `FeedbackView` —— 工单主体 + `replies` 回复数组。

**内部备注的可见性**:`isInternal = 1` 的回复**只有 ADMIN / BOSS 看得到**,
普通用户拿到的响应里这些条目已被过滤。缓存也按视角分了 key(用户版 / 管理员版),
不会串味。

非本人且非管理员 → `403`;工单不存在 → `404`。

### 6.5 追述 / 回复

```
POST /api/feedbacks/{id}/reply
Auth: 需登录(本人或 ADMIN/BOSS)
```

**请求体** `FeedbackReplyRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `body` | string | ✅ | 回复内容 |
| `isInternal` | boolean | ❌ | 默认 `false`。**仅 ADMIN/BOSS 有效** |

> ⚠️ 普通用户传 `isInternal: true` 会被**强制当作 false** —— 否则任何人都能
> 塞一条"管理员内部备注"进来伪装官方口径。

### 6.6 改状态 / 优先级

```
PATCH /api/feedbacks/{id}/status
Auth: ADMIN / BOSS
```

**请求体** `FeedbackStatusRequest` —— `status` 与 `priority` **至少传一个**。

| 字段 | 类型 | 取值范围 |
|---|---|---|
| `status` | number | `0` 待处理 / `1` 处理中 / `2` 已解决 / `3` 已关闭 |
| `priority` | number | `0` 低 / `1` 普通 / `2` 高 / `3` 紧急 |

```json
{ "status": 2, "priority": 2 }
```

> 状态改为 `2`(已解决)或 `3`(已关闭)时自动写 `resolved_time = NOW()`;
> **改回 `0`/`1` 会把它清空为 NULL** —— 避免"重新打开后还留着上次的解决时间"
> 这种自相矛盾的数据。

---

## 7. 统计与风控

### 7.1 仪表盘统计

```
GET /api/stats/dashboard?days=30
Auth: ADMIN / BOSS
```

**参数**:`days` 统计窗口天数,**夹到 `[7, 90]`**(传 1 按 7 算,传 365 按 90 算 ——
防止有人用 `days=99999` 去打全表聚合)。

**响应 data** `DashboardStatsResponse`,4 组数据:

| 字段 | 说明 |
|---|---|
| `newUsersTrend` | 按天新增用户(`DailyCountItem[]`) |
| `salesTrend` | 按天订单数 + 销售额(`DailySalesItem[]`) |
| `topBooks` | 销量 Top N(`BookSalesItem[]`,**只统计已支付订单**) |
| `orderStatusDistribution` | 订单状态分布(`StatusCountItem[]`) |

> 整个响应体缓存到 Redis 单个 key,TTL 5 分钟。统计不是强一致场景,
> 过期自动回源,不做主动失效。

### 7.2 查看生效中的 IP 封禁

```
GET /api/security/ip-bans
Auth: ADMIN / BOSS
```

**响应 data** `IpBanInfo[]` —— 当前生效(未过期)的封禁记录,
含 IP / 封禁原因 / 封禁时刻 / 解封时刻。

> 同一 IP 可能有多条历史记录(重复封禁是新增而不是覆盖,保留审计痕迹),
> 这个接口按 IP 倒序取**最新一条**。

### 7.3 人工解封

```
DELETE /api/security/ip-bans/{ip}
Auth: ADMIN / BOSS
```

同时清 **MySQL 记录**和 **Redis 封禁标记** —— 只清一边的话,
要么 Redis 里还封着(等于没解),要么 IP 风控过滤器每次都得查库(失去 Redis 的意义)。

---

## 8. 数据字典(枚举)

### 8.1 `UserRole` — 用户角色

| code | name | 描述 |
|---:|---|---|
| 0 | `USER` | 普通用户 |
| 1 | `ADMIN` | 管理员 |
| 2 | `BOSS` | 老板 |

### 8.2 `UserStatus` — 用户状态

| code | name | 描述 |
|---:|---|---|
| 0 | `ACTIVE` | 正常 |
| 1 | `INACTIVE` | 未激活 |
| 2 | `SUSPENDED` | 已暂停/封号 |

### 8.3 `OrderStatus` — 订单状态

| code | name | 描述 |
|---:|---|---|
| 0 | `PENDING` | 待支付 |
| 1 | `PAID` | 已支付 |
| 2 | `CANCELLED` | 已取消 |
| 3 | `TIMEOUT` | 超时取消 |

### 8.4 枚举在 DTO 中的两种序列化方式

后端 DTO 字段**不一致地**使用 `Integer` 或 `Enum` 类型,导致 JSON 序列化结果有差异。
具体到哪个 DTO 用哪种:

| 字段 | 所在 DTO | 字段类型 | JSON 形态 | 用法 |
|---|---|---|---|---|
| `role` | `LoginResponse` | `UserRole` **枚举** | **字符串** `"USER"` | 登录后 JWT 一致性 |
| `role` | `UserVo` (列表/详情) | `Integer` code | **number** `0/1/2` | 管理端按 code 搜索 |
| `role` | `UserSearchRequest` (query) | `Integer` code | **number** `0/1/2` | 同上,query 传 number |
| `role` | `UserUpdatedRequest` (body) | `Integer` code | **number** `0/1/2` | 同上,body 传 number |
| `status` | `UserVo` | `Integer` code | **number** `0/1/2` | 列表/详情展示用 number |
| `status` | `UserSearchRequest` (query) | `Integer` code | **number** `0/1/2` | 同上 |
| `status` | `UserUpdatedRequest` (body) | `Integer` code | **number** `0/1/2` | 同上 |
| `status` | `PurchaseResponse` | `OrderStatus` **枚举** | **字符串** `"PENDING"` | 订单状态语义清晰 |

> **设计取舍**:
> - 登录用枚举 → JWT claim `role` 也是 `UserRole.name()`,反序列化时 `UserRole.fromString(claims.get("role"))` 直观
> - 管理用 Integer → 搜索 query 直接传 `?role=2`,无需做 name → code 转换;管理界面用 `<el-tag :type="tagType(row.status)">` 之类的转换
> - 订单用枚举 → 状态语义直接读,无歧义;DB `order_status` 是 `Integer`,Java 端 `OrderStatus.getOrderStatusByCode()` 还原

> **前端处理**:
> 前端用 `safeUser.ts` 在 API 出口处把 `UserVo` 的 `role` / `status` 从 number 翻译回字符串枚举
> (`'USER'` / `'ADMIN'` / `'BOSS'`、`'ACTIVE'` / `'INACTIVE'` / `'SUSPENDED'`),
> 让 view 层可以写 `status === 'ACTIVE'` 这样的字符串比较。`LoginResponse` 和 `PurchaseResponse`
> 的枚举字段由 Jackson 自动序列化为字符串,前端无需翻译。

---

## 9. 端点速查表

| Method | URL | Auth | 说明 |
|---|---|:---:|---|
| POST | `/api/captcha/login` | ❌ | 获取登录验证码(返回 JSON `{image, expiresAt}`) |
| POST | `/api/captcha/register` | ❌ | 获取注册验证码(2026-09 新增) |
| POST | `/api/users/register` | ❌ | 注册(需 captcha + uuid) |
| POST | `/api/users/login` | ❌ | 登录(需 captcha + uuid) |
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
| GET | `/api/purchases` | ✅ | **全部订单分页(管理端,仅 ADMIN/BOSS)** |
| GET | `/api/purchases/{orderNumber}` | ✅ | 订单详情 |
| DELETE | `/api/purchases/{orderNumber}` | ✅ | 取消订单 |
| PATCH | `/api/purchases/{orderNumber}/pay` | ✅ | 支付订单 |
| GET | `/api/books/categories` | ❌ | 图书分类树(两级) |
| POST | `/api/books/categories` | ✅ | 新建分类(仅 ADMIN/BOSS) |
| GET | `/api/books/suggest` | ❌ | 搜索候选词(下拉建议) |
| POST | `/api/feedbacks/mine` | ✅ | 提交反馈 |
| GET | `/api/feedbacks/mine` | ✅ | 我的反馈列表 |
| GET | `/api/feedbacks/all` | ✅ | 全部反馈(仅 ADMIN/BOSS) |
| GET | `/api/feedbacks/{id}` | ✅ | 反馈详情(本人或管理员) |
| POST | `/api/feedbacks/{id}/reply` | ✅ | 追述 / 回复 |
| PATCH | `/api/feedbacks/{id}/status` | ✅ | 改状态/优先级(仅 ADMIN/BOSS) |
| GET | `/api/stats/dashboard` | ✅ | 仪表盘统计(仅 ADMIN/BOSS) |
| GET | `/api/security/ip-bans` | ✅ | 生效中的 IP 封禁列表(仅 ADMIN/BOSS) |
| DELETE | `/api/security/ip-bans/{ip}` | ✅ | 人工解封(仅 ADMIN/BOSS) |

> ❌ = 白名单(无需 token) / ✅ = 需要 `Authorization: Bearer <token>`
>
> ✅ 里带「仅 ADMIN/BOSS」的,除了要有合法 token,角色不够还会返回 `403`。
> 其余 ✅ 端点普通用户也能调,但只能操作属于自己的数据(具体边界见各章节)。

---

## 10. 附录

### 10.1 登录完整流程

```
┌─────────┐  POST /api/captcha/login?uuid=X   ┌────────┐
│ Frontend│ ─────────────────────────────────→ │Backend │
│         │ ←─ {image: dataURI, expiresAt} ── │        │
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

### 10.2 库存预占流程

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

### 10.3 运维端点(Actuator)

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
| `tmlibrary_order_expire_db_fallback_total` | DB 兜底扫描发现"Redis 索引里没有的过期订单" | 非零即说明 Redis 超时索引已不可靠(淘汰/重启丢失),关单正退化为依赖 DB 兜底 |

> ⚠️ **安全提示**:`JwtAuthFilter` 只注册在 `/api/*` 上,`/actuator/**` **不走 JWT 鉴权**。
> 当前仅暴露 `health` 与 `metrics`;生产环境建议改用独立 management 端口,
> 或在网关/反向代理层限制访问来源。

### 10.4 关键设计

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
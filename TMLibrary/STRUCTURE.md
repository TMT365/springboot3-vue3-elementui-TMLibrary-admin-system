# TMLibrary 后端逐文件职责清单

> 生成时间:2026-09-18
> 范围:`TMLibrary/` 后端全部文件(Java 源码 / 资源 / 测试 / 顶层文件 / 脚本 / 测试数据)
> 说明:每条描述都是从文件真实内容(类级 Javadoc、注解、方法签名、文件头注释)读出来的,不猜。

---

## 怎么读这个文件

1. **路径全部相对于 `TMLibrary/`**。比如 `config/RedisConfig.java` 指的是
   `TMLibrary/src/main/java/com/tmt/TMLibrary/config/RedisConfig.java`。
2. 表格统一两列:`| 路径 | 一句话职责 |`。按包 / 目录分成小节,先看小标题定位。
3. **有值得说的设计取舍,会在该行后附一句说明** —— 重点看这几处:
   `MyBatisConfig`(为什么手写)、`RandomExpirationTimeWithOffset`(单位换算坑)、
   `RedisKeys`(为什么不做成配置项)、`PurchaseServiceImpl`(Redis 为什么不在事务里)。
4. **两处特别标注**用 ⚠️ 标出:`scripts/schema.sql` 与 `RedisConfig.java`。
5. 有三处是**空壳 / 空文件**,如实标注,不要以为是漏写:
   - `config/MyUtilsConfig.java` —— 空 `@Configuration`,没有任何 Bean
   - `config/RestConfig.java` —— `RestTemplate` 的 Bean 整段被注释掉
   - `src/test/resources/db/schema.sql` —— 0 字节
6. 看不出职责的写 **职责不明**,不编。

### 包级总览

| 包 / 目录 | 干什么 | 大致文件数 |
|---|---|---|
| `com.tmt.TMLibrary`(根) | Spring Boot 启动类 | 1 |
| `common` 及其子包 | 通用枚举、统一响应壳、Redis key 常量、工具类、指标 | 14 |
| `config` | Spring 配置:MyBatis / Redis / ES / MVC / traceId / 两个空壳 | 7 |
| `controller` | REST 端点,按业务域拆 9 个 | 9 |
| `dto` 及其子包 | 入参 / 出参 / Redis 缓存对象(纯数据,不含业务) | 39 |
| `entity` | 数据库表映射实体 | 9 |
| `exception` | 业务异常 + 全局异常处理 | 4 |
| `mapper` | MyBatis 接口(纯 MyBatis,没用 MyBatis-Plus) | 8 |
| `scheduler` | 定时任务:超时关单、库存对账 | 2 |
| `security` 及其子包 | JWT 鉴权过滤器、IP 风控、当前用户注入 | 10 |
| `service` 及其子包 | 业务接口 + 实现 + 搜索建议装饰器链 | 26 |
| `vo` | 对外视图对象(脱敏后) | 2 |
| `src/main/resources` | 配置 / SQL / MyBatis XML / Lua 脚本 | 25 |
| `src/test` | 测试代码与测试资源 | 4 |
| 顶层文件 | 构建、文档、环境变量模板、容器化 | 12 |
| `scripts/` | 启动脚本、ES 分词器安装、Logstash 同步、总体规划表 | 6 |
| `testdata/` | 批量测试数据与加载工具 | 3 |
| `.mvn/` | Maven Wrapper 引导(properties + jar) | 2 |

---

## 1. 启动类

| 路径 | 一句话职责 |
|---|---|
| `TmLibraryApplication.java` | Spring Boot 启动类;`@EnableScheduling` 打开定时任务 —— `OrderExpireScheduler` / `BookInventoryReconcileScheduler` 靠它才会跑 |

---

## 2. `common` —— 通用枚举、响应壳、工具

### 2.1 `common`(根)

| 路径 | 一句话职责 |
|---|---|
| `common/CaptchaType.java` | 验证码用途枚举(LOGIN / REGISTER),决定 Redis key 走 `login:{uuid}` 还是 `register:{uuid}` —— 两条路径互不干扰,登录的验证码不能拿去注册 |

### 2.2 `common/Order`

| 路径 | 一句话职责 |
|---|---|
| `common/Order/OrderStatus.java` | 订单状态枚举 PENDING(0) / PAID(1) / CANCELLED(2) / TIMEOUT(3);按 code 反查时查不到抛 `BusinessException` 而不是返回 null |

### 2.3 `common/Result`

| 路径 | 一句话职责 |
|---|---|
| `common/Result/Result.java` | 全站统一响应壳 `{code, msg, data}`,提供 `success()` / `fail()` 静态工厂 |
| `common/Result/PageResult.java` | 分页壳,只有 `total` + `data` 两个字段 |
| `common/Result/ResultCode.java` | 业务状态码枚举;含 `TOO_MANY_REQUESTS(429)` —— IP 风控封禁专用,前端据此弹封禁提示 |

### 2.4 `common/User`

| 路径 | 一句话职责 |
|---|---|
| `common/User/UserRole.java` | 角色枚举 USER(0) / ADMIN(1) / BOSS(2),支持按名字和按 code 反查 |
| `common/User/UserStatus.java` | 用户状态枚举 ACTIVE(0) / INACTIVE(1) / SUSPENDED(2) |

### 2.5 `common/metrics`

| 路径 | 一句话职责 |
|---|---|
| `common/metrics/InventoryMetrics.java` | 库存/订单一致性计数器(漂移、对账修复、DB 兜底关单、补偿失败)。这些数持续增长即代表异常路径被触发了;Counter 按 tag 缓存,避免每次打点都重建 meter(建 meter 有锁开销) |

### 2.6 `common/redis`

| 路径 | 一句话职责 |
|---|---|
| `common/redis/RedisKeys.java` | 全站 Redis key 集中管理,命名规范 `tmlibrary:{domain}:by{Accessor}:{keyId}:{feature}`。**刻意不做成 `@ConfigurationProperties`** —— key 是结构性契约,改一个等同"所有线上缓存作废",不该放进 yml 让运维误改;纯静态 final + 私有构造器,JVM 类加载即固化 |

### 2.7 `common/utils`

| 路径 | 一句话职责 |
|---|---|
| `common/utils/CaptchaUtil.java` | 画验证码图。字符集排除 `l/1/I/0/O` 五个易混字符(剩 31 个,熵 ≈19.7 bit 仍比纯数字高 50%)—— 早版本用 62 字符会因字宽不均裁字;2026-09 改成返回 `byte[]` 由 controller 决定怎么响应 |
| `common/utils/IpUtil.java` | 取客户端 IP。`resolveClientIp` 纯本地计算、不发网络请求(登录热路径用);`getClientIp` 会调 `ip-api.com` 做地理定位 —— 阻塞式远程调用,别放在登录热路径上 |
| `common/utils/RandomExpirationTimeWithOffset.java` | 带随机抖动的 TTL(基础时长 + 0~299 秒),防大量 key 同时过期造成缓存雪崩。**单位换算的坑**:旧实现先把值换成秒、却仍用原单位构造 `Expiration`,导致所有非秒级 TTL 被放大 60 倍(3 分钟 → 3 小时);现在换算与构造统一用秒,亚秒级入参兜底为 1 秒 |
| `common/utils/Snowflake.java` | 雪花算法订单号发号器(41 位时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列);时钟回拨直接抛异常,同毫秒超 4096 个则等下一毫秒 |
| `common/utils/TimeCostAspect.java` | 切面,环绕 `controller` 包下所有方法,打一行 `方法签名 executed in N ms` |

---

## 3. `config` —— Spring 配置

| 路径 | 一句话职责 |
|---|---|
| `config/MyBatisConfig.java` | **手写 MyBatis 配置**,绕过 `mybatis-spring-boot-starter 3.0.5` 与 Spring Boot 4.x 的不兼容:① 自动配置的 `@AutoConfigureAfter` 引用了 3.x 的旧包路径,在 4.x 下静默跳过;② `MybatisProperties.applyTo()` 内部调了 4.x 已删的 `PropertyMapper.alwaysApplyingWhenNonNull()`。所以只用两个安全的 getter,`mybatis.configuration.*` 全靠手动 new。**SQL 日志也只能在这里 `setLogImpl`** —— yml 到生效的链路整个断了 |
| `config/RedisConfig.java` | 提供 `StringRedisTemplate` 与 `ObjectMapper` 两个 Bean。⚠️ **第 18 行有一处被注释掉的明文 Redis 口令**(凭据已失效,但建议删掉那行,别再留在仓库里) |
| `config/ElasticsearchConfig.java` | 手搓 `ElasticsearchClient` Bean。不用 Boot 自动配置是因为 `spring.elasticsearch.*` 走的是已废弃的 transport-client,9.x 集群直接拒连;用高层稳定的 `ElasticsearchTransportConfig$Builder` 而不是 package-private 的 `Rest5ClientBuilder` |
| `config/WebMvcConfig.java` | 注册 CORS,放行前端 dev server(Vite :5173)对 `/api/**` 的跨域;origin 从 `app.cors.origins` 读,生产要收敛成精确域名(用 `*` 会连 credentials 一起被禁掉) |
| `config/TraceIdFilter.java` | 每个请求生成 traceId 写进 MDC,日志才能按请求串起来。`@Order(HIGHEST_PRECEDENCE)` 必须排在风控/鉴权之前 —— 否则被拦掉的请求反而没有 traceId,而那恰恰最需要排查;接受的 `X-Trace-Id` 头按白名单字符集 `[A-Za-z0-9_-]{1,32}` 校验,防日志注入(伪造日志行) |
| `config/MyUtilsConfig.java` | ⚠️ **空壳**:只有 `@Configuration`,**没有任何 `@Bean`**(连 import 进来的 `CaptchaUtil` 都没用上) |
| `config/RestConfig.java` | ⚠️ **空壳**:`RestTemplate` 的 `@Bean` **整段被注释掉**(`IpUtil` 自己 new 了一个,所以没人在等这个 Bean) |

---

## 4. `controller` —— REST 端点

| 路径 | 一句话职责 |
|---|---|
| `controller/BookController.java` | 图书 REST(列表 / 新建 / 删除 / 改 / 盘点调库存 / 详情 / 多条件搜索 / 3 种粒度查询)。⚠️ **2026-09 才补上 ADMIN/BOSS 鉴权**:`create`(POST `/created`)、`deleteByISBN`(DELETE)、`updateByISBN`(PATCH `/{isbn}`)、`adjustStock`(PATCH `/{isbn}/stock`)**这 4 个写方法此前没有任何角色检查** —— 任何已登录用户拿 token 就能增删改图书、改库存。前端把这些页面挂在 `meta:{admin:true}` 路由下,但**前端路由守卫不是安全边界**,绕过它只需要一个 curl |
| `controller/UserController.java` | 用户管理 9 个端点(注册 / 登录 / 登出 / 列表 / 详情 / 更新 / 软删 / 改密 / 看订单);当前用户由 `@CurrentUser` 注入 |
| `controller/PurchaseController.java` | 购书订单 4 个端点:下单(POST)、详情(GET)、取消(DELETE)、支付(PATCH `/pay`) |
| `controller/CaptchaController.java` | 验证码端点。2026-09 从 `image/png` 二进制流改成 JSON(前端拿 `expiresAt` 做倒计时),并新增 `/register` —— 与 `/login` 共用 service 但 Redis key 走不同路径;两个端点都免 token |
| `controller/CategoryController.java` | 图书分类接口。**挂在 `/api/books/categories` 而不是 `/api/categories`** 是为了蹭 `("/api/books", GET)` 那条白名单,读接口的形状不必和鉴权配置耦合;字面量段 `categories` 优先级高于 `{isbn}`,所以不会撞车 |
| `controller/SearchSuggestController.java` | 搜索下拉候选词,免登录(同样蹭 `/api/books` 的 GET 白名单) |
| `controller/FeedbackController.java` | 反馈工单;全路径不在白名单 → 一律要求登录,管理员权限在方法内校验(不在过滤器里做,同 `StatsController` 套路) |
| `controller/StatsController.java` | 统计接口,目前只有仪表盘一个端点;过滤器层已要求 token,这里再补一层角色校验,只放 ADMIN/BOSS |
| `controller/SecurityController.java` | 风控运维:查看 / 解除 IP 封禁。没有这两个端点的话,误封只能手写 SQL + 手动清 Redis(只清 DB 不清 Redis 的话封禁会一直生效到 TTL 结束) |

---

## 5. `dto` —— 数据传输对象

### 5.1 `dto/redis`

| 路径 | 一句话职责 |
|---|---|
| `dto/redis/CaptchaRedis.java` | Redis 里的验证码元数据:验证码原文 + 绑定的 username + expiresAt(毫秒时间戳)。**只存验证码不存密码**(鸡生蛋) |
| `dto/redis/UserRedis.java` | 用户正缓存对象(含 passwordHash,仅服务端内部使用) |
| `dto/redis/UserStatusRedis.java` | 用户状态的轻量缓存:id + status + deletedAt,下单前 `checkUser` 用它省一次 DB |

### 5.2 `dto/request`

| 路径 | 一句话职责 |
|---|---|
| `dto/request/LoginRequest.java` | 登录入参 |
| `dto/request/GetCaptchaRequest.java` | 取验证码入参:username + uuid。**不能带密码** —— 验证码的目的就是"密码提交前的校验" |
| `dto/request/UserRegisterRequest.java` | 注册入参;`email`/`phoneNumber`/`password`/`username` 带校验注解,2026-09 加了 `captcha` + `uuid` 两个字段 |
| `dto/request/UserSearchRequest.java` | 用户列表多条件筛选。`role` / `status` 用 `-1` 表示"全部",不传则 `compact()` 兜底为 USER / ACTIVE;`role` 筛选仅 BOSS 生效,ADMIN 恒被钉死为 role=0 |
| `dto/request/UserUpdatedRequest.java` | 改用户信息。刻意**不含** password / salt / lastLoginTime / lastLoginIp / failedLoginAttempts / accountLockedUntil / deletedAt / updateTime / id —— 这些由 Service 内部维护,不暴露给前端 |
| `dto/request/UserDeleteRequest.java` | `DELETE /api/users/{id}` 的 body,只放密码;id 走 URL 不放 body |
| `dto/request/UserPasswordRequest.java` | 改密 body;必须 `currentUserId == id`(只能改自己) |
| `dto/request/BookSaveRequest.java` | 新建图书入参;`@NotBlank` / `@Size` / `@Pattern` 校验,ISBN 正则 `^[0-9Xx-]{10,20}$` |
| `dto/request/BookUpdateRequest.java` | 改图书信息;**库存不在此接口** —— 交易链路会持续改动可用库存,管理端直接 SET 会与在途预占打架,调库存走 `PATCH /{isbn}/stock`;传 `categoryId` 会同步新旧两个分类的计数 |
| `dto/request/BookSearchRequest.java` | `GET /api/books` 多条件搜索入参。归一化集中在 `compact()`(空串→null、负数→null、size 上限 100 防 DoS);**放 DTO 不放 Service** 的理由:单一职责 + 可脱离 Spring 上下文直接 new 出来单测 |
| `dto/request/BookStockAdjustRequest.java` | 库存调整,**盘点语义(绝对值,非增量)**。与"更新图书"拆开是因为库存有两个变更来源(交易链路 / 管理盘点),混在一个接口里系统无法区分"盘点结果"和"随手填的值",也无法审计 |
| `dto/request/BookPublishedDateByRequest.java` | `publishedDate` 的 3 级粒度查询(year / +month / +day);`compact()` 校验"必须从大到小连续",跳级(如 `year=2024&day=15`)返回 400 |
| `dto/request/BookDateTimeByRequest.java` | `createdTime` / `updatedTime` 的 5 级粒度查询(year → minute),同样由 `compact()` 校验连续性 |
| `dto/request/PurchaseRequest.java` | 下单入参,**刻意不含 userId** —— 用户身份从 `@CurrentUser` 拿,客户端能传 userId 就是攻击面 |
| `dto/request/PurchaseItemRequest.java` | 下单行:bookId + quantity,都带 `@NotNull` |
| `dto/request/CategoryCreateRequest.java` | 新建分类入参(后台"新增图书"里那个可以现敲的小类输入框,先建分类拿 id 再提交图书) |
| `dto/request/FeedbackCreateRequest.java` | 提交反馈。字段长度与 SQL 的 `VARCHAR(32)/(120)/TEXT` 对齐 —— 前后端都把长度卡住,避免垃圾数据 |
| `dto/request/FeedbackReplyRequest.java` | 反馈回复 / 追述。提交人和管理员共用,差异在 `isInternal`:用户传 false,管理员可传 true 写内部备注(对提交人不可见) |
| `dto/request/FeedbackStatusRequest.java` | 改反馈状态 / 优先级,仅 ADMIN/BOSS。两个字段都允许省略但至少要传一个,这条跨字段校验**写在 Service 层** |

### 5.3 `dto/response`

| 路径 | 一句话职责 |
|---|---|
| `dto/response/LoginResponse.java` | 登录响应:token + username + role(过滤掉 user 实体的敏感字段) |
| `dto/response/LogoutResponse.java` | 登出响应,带明确的 `loggedOut` 信号。后端无法真的"重定向"客户端,只能用响应体告诉前端"该跳走了",前端据此清 localStorage + 跳登录页 |
| `dto/response/CaptchaResponse.java` | 验证码响应(base64 data URI + 过期时间)。**`expiresAt` 用 String 不用 long** —— "string at the boundary"惯例:Java long 范围到 9.2×10^18,微秒/纳秒精度会越过 JS 安全整数 2^53;String 前后端都无损 |
| `dto/response/PurchaseResponse.java` | 完整订单响应,由 `from(OrderWithItems)` 转换 —— 隐藏内部自增 id,暴露订单号 / 状态 / 金额 / 明细 / 两个时间(下单、支付) |
| `dto/response/PurchaseItemResponse.java` | 订单行明细。只暴露 bookId(跳详情用)/ quantity / price_snapshot(下单时快照价,不受 book.price 后改影响)/ subtotal,不暴露 id / orderId / createdTime |
| `dto/response/OrderListItem.java` | 管理端订单列表一行。比 `PurchaseResponse` 轻(列表不展开明细),但补上 `createdTime`(列表要按时间展示) |
| `dto/response/DashboardStatsResponse.java` | 仪表盘快照,一次返回 4 组数据(新增用户趋势 / 销量趋势 / 每本书销量 Top / 订单状态分布),也是 Redis 缓存的对象;刻意不含 `generatedAt` |
| `dto/response/DailyCountItem.java` | 「某天 + 数量」聚合行,新增用户趋势用 |
| `dto/response/DailySalesItem.java` | 「某天 + 订单数 + 销售额」,只统计 `order_status = 1`(PAID)—— 待支付/已取消/超时都不算成交 |
| `dto/response/BookSalesItem.java` | 「书 + 销量」聚合行,数据来自 `order_items ⋈ orders(仅 PAID) ⋈ books` |
| `dto/response/StatusCountItem.java` | 「订单状态 + 数量」;status 只回数值,**中文标签由前端映射** —— 展示文案属于 UI 层 |
| `dto/response/BookCategoryNode.java` | 分类树节点。`bookCount` 对大类是**累加值**(自身直挂 + 所有子类之和),商城侧栏显示大类时拿到的就是"这一类一共有几本" |
| `dto/response/BookSuggestion.java` | 搜索候选词条目。**这是给前端的稳定契约**:当前由 MySQL LIKE 查出来,后续换 Elasticsearch 只改 Service 里的取数逻辑,这个形状不变 |
| `dto/response/FeedbackSummary.java` | 反馈列表一行,**不带 body** —— body 可能很大,列表一次返回 N 条会胖;点进详情才拿完整 body |
| `dto/response/FeedbackView.java` | 反馈详情 + 回复列表,一次 GET 拿完整状态;`isInternal=true` 的项**已在 Service 层按当前用户角色过滤**过 |
| `dto/response/FeedbackReplyView.java` | 单条回复(详情页用) |
| `dto/response/IpBanInfo.java` | 封禁信息,挂在 429 响应的 `data` 字段上,前端用它渲染"访问已被限制"弹窗 |

---

## 6. `entity` —— 表映射实体

| 路径 | 一句话职责 |
|---|---|
| `entity/User.java` | 用户实体;含 passwordHash / lastLoginIp / failedLoginAttempts / accountLockedUntil / passwordResetToken / deletedAt 全部字段 |
| `entity/Book.java` | 图书实体,对应 `books` 表(含 2026-09 新增的 `categoryId`) |
| `entity/BookCategory.java` | 分类实体。**`parentId = 0` 表示大类而不是 NULL** —— MySQL 唯一索引里 NULL 互不相等,用 NULL 的话顶层重名约束会失效 |
| `entity/Order.java` | 订单实体,对应 `orders` 表(含 2026-09 新增的 `paidTime`) |
| `entity/OrderItem.java` | 订单明细实体(orderId / bookId / quantity / price 快照) |
| `entity/OrderWithItems.java` | 订单 + 明细的组合体,一个订单号可以有多条明细 |
| `entity/Feedback.java` | 反馈工单主表。`status` 用 `Integer` 不用枚举 —— 模仿 `Order.orderStatus` 的做法,DB 里就是 TINYINT,业务层的"翻译"集中在 Controller 校验和列表 chip 渲染里 |
| `entity/FeedbackReply.java` | 反馈回复 / 追述。**一张表装三类内容**:用户追述(role=0)、管理员回复(role=1/2)、管理员内部备注(role=1/2 + isInternal=1) |
| `entity/IpBan.java` | IP 封禁记录。同一 IP 允许多次封禁(解封后再犯),表里不做唯一约束,保留历史 |

---

## 7. `exception`

| 路径 | 一句话职责 |
|---|---|
| `exception/BusinessException.java` | 业务异常,携带 `ResultCode`;冒泡到 `GlobalExceptionHandler` 被转成 `Result.fail(404, "图书不存在,id=5")` |
| `exception/AuthException.java` | 鉴权异常,继承 `BusinessException`,三个构造器分别接 ResultCode / ResultCode+msg / code+msg |
| `exception/GlobalExceptionHandler.java` | 全局异常处理(`@RestControllerAdvice`)。**所有错误响应返回真实的 HTTP 状态码**,而不是一律 200 把错误码塞在响应体里 —— 响应体仍是统一的 `Result` 壳,但这样网关/监控按 HTTP 状态统计错误率才不会失真 |
| `exception/OrderAutoCancelledException.java` | 付款时因库存不足导致订单被自动取消。**特殊在它必须让事务提交而不是回滚** —— 触发链路在同一个事务内把订单置为 CANCELLED 并释放预占,若按默认规则回滚,刚写的"已取消"会被撤销,订单又回到 PENDING,用户陷入"付不了款也取消不掉"的状态;所以 `payOrder` 上标了 `noRollbackFor` |

---

## 8. `mapper` —— MyBatis 接口

| 路径 | 一句话职责 |
|---|---|
| `mapper/UserMapper.java` | 用户 CRUD + 列表筛选 + 登录失败计数**原子递增**(`failed_login_attempts + 1 >= threshold` 写进单条 SQL,避免先 SELECT 再 UPDATE 的两阶段窗口) |
| `mapper/BookMapper.java` | 图书 CRUD + 分页 + 多条件动态 SQL + 4 种粒度区间查询 |
| `mapper/BookCategoryMapper.java` | 分类读写。表很小(几十行),读路径一律全表取出后在内存里组装树,**不做按层查询**(避免 N+1);计数用 `book_count + #{delta}` 原子加减 |
| `mapper/OrderMapper.java` | 订单/明细读写。`selectOrderByOrderNumberForUpdate` 是 `SELECT ... FOR UPDATE`,依赖 InnoDB 行锁;`selectOrderPage` **必须带 ORDER BY** —— 无排序的 `LIMIT offset,n` 翻页可能重复或漏记录 |
| `mapper/StatsMapper.java` | 仪表盘 4 个聚合查询。参数是半开区间左端点 `[start, now)`,**SQL 不做日期算术** —— Service 端算好再传,这样 `created_time >= #{start}` 能走索引 |
| `mapper/SearchSuggestMapper.java` | 候选词取数。**三条查询分开而不是 UNION** —— 各类型的"热度"聚合口径不同(书名/ISBN 是单本书,作者要把名下所有书求和),拆开更好读,合并排序放 Service |
| `mapper/FeedbackMapper.java` | 反馈工单/回复读写。列表用一条 SQL 联 users 取 username + LEFT JOIN 算回复数,**避免 N+1** |
| `mapper/IpBanMapper.java` | 封禁记录落库 / 查询 / 解封。只在"触发封禁"和"人工解封"时写,每请求的判定走 Redis |

---

## 9. `scheduler` —— 定时任务

| 路径 | 一句话职责 |
|---|---|
| `scheduler/OrderExpireScheduler.java` | 每 60 秒扫 `pendingExpireIdx` ZSet,对 `score ≤ now` 的订单号调 `cancelExpiredOrder`。锁 TTL 50s **必须小于** `@Scheduled` 周期 60s,否则下个周期触发时锁还没过期;释放用 Lua `if get == self then del` 避免误删别人的锁 |
| `scheduler/BookInventoryReconcileScheduler.java` | 库存对账:周期性校验不变式 `Redis(stock + reserved) == DB.stock_quantity`,不成立则以 DB 为准修正 `stock`(保留 `reserved`)。Redis 库存只是"预占缓存",DB 的 `stock_quantity` 才是真值;同款 Redis SETNX 锁保证多实例只有一个节点在对账 |

---

## 10. `security` —— 鉴权与风控

### 10.1 `security`(根)

| 路径 | 一句话职责 |
|---|---|
| `security/SecurityConfig.java` | 安全 Bean:`BCryptPasswordEncoder`(cost 10,自带随机 salt,不需要单独存 salt)+ **显式注册 `JwtAuthFilter`**(控制 URL pattern 与 order=10,排在 CORS preflight / RequestContext 之后) |
| `security/IpRiskControlFilter.java` | IP 风控过滤器,每个 `/api/*` 请求都过一遍。order 5 **排在 `JwtAuthFilter`(10) 之前** —— 已封禁的 IP 连 token 都不用解析。回环/私网地址(127.0.0.1、::1、10.x、192.168.x、172.16-31.x)一律跳过,否则本地开发时自己就把自己封了 |
| `security/AuthErrorWriter.java` | 过滤器层写错误响应体(走 ObjectMapper);与 `GlobalExceptionHandler` 保持同一套 `Result` 壳,并支持把 `IpBanInfo` 塞进 `data` 供前端渲染封禁弹窗 |

### 10.2 `security/context`

| 路径 | 一句话职责 |
|---|---|
| `security/context/CurrentUser.java` | 参数级注解,标记"当前登录用户";`@Target(PARAMETER)` + 运行时保留(Spring 反射要读) |
| `security/context/CurrentUserArgumentResolver.java` | 参数解析器:从 request attribute `CURRENT_USER` 读出 `UserView` 注入 Controller 方法参数 |
| `security/context/CurrentUserContext.java` | attribute 名的读写器 —— Filter 写、Resolver 读,**共享同一个常量**避免拼写错 |
| `security/context/UserView.java` | 当前登录用户的简化视图(**不暴露 passwordHash / salt 及任何内部安全字段**) |

### 10.3 `security/jwt`

| 路径 | 一句话职责 |
|---|---|
| `security/jwt/JwtAuthFilter.java` | JWT 过滤器。白名单是**「路径 + HTTP 方法」**规则 —— `/api/books` 的 **GET 全放行**(商城未登录可浏览),同前缀的 POST/PATCH/DELETE 仍要 token。类头特意写了「**不要加 `@Component`**」:由 `SecurityConfig` 显式注册,避免被默认 servlet 再注册一次 |
| `security/jwt/JwtService.java` | 签发与解析 JWT;签发时把 `jti` 拼进 claims,登出后 jti 进 Redis 黑名单,后续请求被过滤器拦下 |
| `security/jwt/JwtProperties.java` | `@ConfigurationProperties(prefix = "jwt")`,映射 `secret` / `expirationSeconds` |

---

## 11. `service` 及其子包

### 11.1 `service`(接口)

| 路径 | 一句话职责 |
|---|---|
| `service/AuthService.java` | 登录 / 登出两个方法;登出把 token 的 jti 写入 Redis 黑名单(剩余 TTL) |
| `service/UserManagementService.java` | 用户管理:注册(**强制 role=USER,屏蔽自选角色漏洞**)/ 软删 / 列表 / 更新 / 改密 / 查单个 |
| `service/BookService.java` | 图书业务:CRUD + 分页 + 多条件搜索 + 4 种粒度查询 + **盘点语义**的 `adjustStock`(绝对值) |
| `service/BookInventoryService.java` | **Redis 库存原子操作门面**。把所有库存变更收敛到这里,**业务代码不许直接拼 Redis key 或写 Lua**;每个 book 一个 Hash(`stock` 可用 / `reserved` 已预占);所有变更走 Lua,Redis 单线程串行执行 = 天然原子,不需要额外分布式锁 |
| `service/PurchaseService.java` | 订单生命周期门面:创建 / 查询 / 取消 / 支付 / 系统关单 5 类。`cancelExpiredOrder` 由定时任务调用,**不校验用户状态** |
| `service/CategoryService.java` | 图书分类 —— 读路径给商城(免登录),写路径给后台 |
| `service/CaptchaService.java` | 验证码服务。2026-09 重构:不再写 `HttpServletResponse`,改返回 `CaptchaResponse`;支持 LOGIN / REGISTER 两种用途 |
| `service/StatsService.java` | 仪表盘统计快照,走 Redis cache-aside(命中直接反序列化,未命中 4 个聚合查询后写缓存 TTL 5 分钟) |
| `service/IpBanService.java` | IP 风控:高频请求检测 + 封禁 24 小时。**Redis 是每请求的判定源(快),DB 是审计记录**(Redis 重启后仍能查"封过谁、为什么");代价是 Redis 被清空会提前解封,需人工重封 |
| `service/FeedbackService.java` | 反馈工单接口。**管理员权限校验放 Controller 层**,Service 不重复判断角色 —— 越早判断越易"做错位置" |
| `service/SearchSuggestService.java` | 搜索候选词接口。**这是将来接 Elasticsearch 的那个接缝** —— 接口形状(q/limit 入参、`List<BookSuggestion>` 出参)保持不变,只换实现 |
| `service/SearchSuggestServiceConfig.java` | 选 Bean + 运行期自动降级。组合顺序 `Caching(Fallback(ES, MySQL))`;启动**之前**对 ES 打一个 1 秒 ping,连不上就把开关回退成 off,应用照样起(只是搜索走 MySQL);用 `@Primary` 强制让 wrapper 排第一 |

### 11.2 `service/impl`

| 路径 | 一句话职责 |
|---|---|
| `service/impl/AuthServiceImpl.java` | 登录实现:BCrypt 校验 + **失败 3 次锁 15 分钟** + 用户正/负缓存。负缓存用显式哨兵 JSON(`{"__negative__":true}`)而不是空串 —— 空串会与 `readValue("")` 抛 `JsonProcessingException` 的边界混淆 |
| `service/impl/UserManagementServiceImpl.java` | 用户管理实现。改完名字会**清掉该用户名下所有 captcha**(login + register 两套),旧 username 的 pending 验证码立即失效 |
| `service/impl/CaptchaServiceImpl.java` | 验证码实现:生成图片 → 算 `expiresAt = now + 3 分钟` → 把"文字 + username + expiresAt"序列化写 Redis(TTL 3 分钟)→ JPEG 编码成 base64 data URI 返回。同一个 uuid 只能验证一次(登录/注册成功后由 AuthService / UserService 删掉 Redis key) |
| `service/impl/BookServiceImpl.java` | 图书实现。`getByISBN` 用 **SingleFlight**(进程内 `CompletableFuture` 表)防同一 isbn 的并发请求全部穿透打 DB,比分布式锁轻量但**只限单 JVM**;follower 等待超时 `app.book.singleflight-timeout-ms` 默认 5000ms |
| `service/impl/BookInventoryServiceImpl.java` | 库存实现:Lua + **HSETNX**。用 HSETNX 而不是 HSET 的原因 —— 并发预热同一 book 时两边都读到 `stock=10`,用 HSET 会让 B 覆盖 A 已写的 `reserved`,丢失 A 已预占的 3 本 |
| `service/impl/PurchaseServiceImpl.java` | 订单核心。**一致性策略**:DB 事务只管订单插入,Redis **不在事务内** —— 失败时手动反向 `release`(`reservedBookIds` 列表);极端情况(DB 成功 Redis 失败)会库存泄漏,靠定时任务 + DB `expireTime` 兜底 |
| `service/impl/CategoryServiceImpl.java` | 分类实现:整棵树缓存成一个 key。写路径**一律"先删缓存,再写 MySQL"**(不是更新缓存)—— 删除幂等,而"更新缓存"在并发写下会把旧值盖在新值上;已知残留窗口(删缓存后、事务提交前的读会把旧树写回)先接受,计划靠 MQ 解决 |
| `service/impl/StatsServiceImpl.java` | 仪表盘实现:整个响应体一个 key、TTL 5 分钟。**不做主动失效** —— 下单/注册时去清缓存会把统计模块耦合进交易链路,收益不值;缓存序列化异常只 log,照常查库返回实时数据 |
| `service/impl/IpBanServiceImpl.java` | 风控实现。计数用**固定窗口**(`window = epochSeconds / windowSeconds`):比滑动窗口省一半内存,代价是窗口边界上最多可能放过 2 倍流量 —— 对"封脚本"这个场景完全够用(脚本是持续高频,不是卡边界打) |
| `service/impl/FeedbackServiceImpl.java` | 反馈实现。**只缓存详情,不缓存列表**(列表过滤维度多,缓存键笛卡尔积会爆炸且命中率低);详情**分普通用户/管理员两套 key** —— 内部备注要按角色过滤,共用 key 会出现"用户查→缓存→管理员查→拿到被过滤过的那份"的串味 |
| `service/impl/SearchSuggestServiceImpl.java` | 候选词实现:MySQL LIKE + 销量热度排序。**个性化暂未实现**,原因很具体:`/api/books/**` 的 GET 在过滤器白名单里,命中白名单后**直接放行、不解析 token**,所以这里拿不到 userId,没法按"这个用户买过什么"加权 |
| `service/impl/EsSearchSuggestServiceImpl.java` | 候选词的 ES 实现。三类型统一打一次 ES,index 里三个字段各自最佳匹配(title 走 edge-ngram 前缀匹配 / author 走 standard / isbn 走 keyword 精确);ES 挂了就抛异常交给上层降级,**不会把"ES 挂了"传到前端** |

### 11.3 `service/search`

| 路径 | 一句话职责 |
|---|---|
| `service/search/CachingSearchSuggestService.java` | 候选词缓存装饰器(**Redis 在前,ES/MySQL 在后**)。**空结果也缓存**(TTL 1 分钟)—— 用户打错字时每个字都打一次 ES,不缓存等于把 ES 当靶子;做成装饰器而不是在 Service 里加 if,是为了让取数逻辑完全不感知缓存的存在 |
| `service/search/SuggestIndexInitializer.java` | 启动时建 `tmlibrary_books_suggest` 索引 + 从 MySQL 全量同步。**连不上 ES 只 log warn 不抛启动异常** —— 搜索挂了商城还能用(降级 MySQL),不能让索引同步问题把整个应用拖死 |

---

## 12. `vo` —— 对外视图对象

| 路径 | 一句话职责 |
|---|---|
| `vo/UserVo.java` | 返回给前端的用户视图。`realName` 保留首字符脱敏(`张三丰` → `张**`)、手机号保留前 7 位、邮箱保留首字符 + 完整域名;`encryptInformation` 带 `keepIndex > 长度` 的越界保护 |
| `vo/IpApiVo.java` | 封装第三方 `ip-api.com` 返回的 IP 数据,只保留必要字段 |

---

## 13. `src/main/resources`

### 13.1 配置

| 路径 | 一句话职责 |
|---|---|
| `src/main/resources/application.yml` | 主配置:数据源 / Redis / CORS / 雪花 / ES / IP 风控 / actuator / MyBatis。**连接信息一律 `${ENV:}` 从环境变量读,留空默认值是故意的** —— 让缺环境变量时启动失败,好过再把密码写进 yml;`mybatis.log-impl` 那行已被架空(见 `MyBatisConfig`) |
| `src/main/resources/application-dev.yml` | 本地开发覆盖(Redis 连接池 lettuce / 超时 / database);凭据同样从环境变量读,本文件是 git 跟踪的所以不放任何敏感信息 |
| `src/main/resources/application-prod.yml` | 生产覆盖。HikariCP 池大小与超时约束(开发默认池 10 对生产偏小,且没有超时约束,慢查询会把连接占死直到池耗尽;`leak-detection-threshold` 超过 20 秒没还连接就打日志);**关掉 SQL 日志** —— `StdOutImpl` 在生产会日志爆炸、泄露登录参数、同步写拖慢响应,换 `NoLoggingImpl` |
| `src/main/resources/logback-spring.xml` | 企业级日志。每一块都在解决一个具体问题:traceId 串链路、异步 appender 不让业务线程阻塞磁盘 I/O、ERROR 单独一个文件给告警系统盯、屏蔽 Spring/Tomcat/Hikari 噪音、**默认相对路径 `logs/` 而不是 `/opt/logs/`**(后者要 root 建目录,没配 `LOG_FILE` 时 logback 直接报错,实测踩过)、`%msg` 换行转义防日志注入 |

### 13.2 `src/main/resources/db` —— 真正的建表脚本

> 这一组才是**用来建库的**。`scripts/schema.sql` 不是,见第 15 节。

| 路径 | 一句话职责 |
|---|---|
| `src/main/resources/db/users.sql` | `users` 单表建表(从 `scripts/schema.sql` 拆出,内容与之一致) |
| `src/main/resources/db/books.sql` | `books` 单表建表;含 2026-09 新增的 `category_id`(逻辑引用,无物理外键) |
| `src/main/resources/db/orders.sql` | `orders` 单表建表。注释解释了 `paid_time` **为什么必须单独一列** —— 不能用 `updated_time` 代替,支付后又取消/改地址会把时间顶掉,"什么时候付的钱"是财务口径 |
| `src/main/resources/db/order_items.sql` | `order_items` 单表建表;`idx_order_items_book_id` 当前没有查询使用,按需求保留供后续按图书统计销量 |
| `src/main/resources/db/book_categories.sql` | 分类表建表,**同时负责给 `books` 补 `category_id` 列** —— MySQL 8.0 的 `ADD COLUMN` 不支持 `IF NOT EXISTS`,用存储过程兜底;用自增 id 是因为种子数据自己指定固定 id 保证幂等,但后台新建分类不传 id,没有自增就会报 "Field 'id' doesn't have a default value" |
| `src/main/resources/db/feedback.sql` | 反馈主表 `feedbacks` + 回复表 `feedback_replies` 建表,单文件可独立执行;一张表装两类内容靠 `role` + `is_internal` 区分,比拆两张更省 JOIN |
| `src/main/resources/db/ip_bans.sql` | 封禁表建表。头部写了运行期查询和**人工解封的完整步骤**(改了 DB 还要 `redis-cli DEL` 清 Redis,否则要等 TTL) |
| `src/main/resources/db/seed-demo-data.sql` | 演示数据,让仪表盘 4 张图有数据可看(铺开到最近 28 天,覆盖 4 种订单状态)。幂等做法:users/books 用固定 username/isbn + INSERT IGNORE;orders 用固定 9e15 号段 + INSERT IGNORE;**order_items 没有业务唯一键,重复执行会翻倍,所以先 DELETE 本脚本产生的明细再重建**;演示账号密码统一 `Demo@123456`,生产不要执行 |

### 13.3 `src/main/resources/mapper` —— MyBatis XML

| 路径 | 一句话职责 |
|---|---|
| `src/main/resources/mapper/BookMapper.xml` | 图书 SQL。用**显式 `<resultMap>`** 映射列名 → 属性名,所以不需要全局驼峰转换(这也是 `MyBatisConfig` 里不依赖 `applyTo()` 的底气) |
| `src/main/resources/mapper/UserMapper.xml` | 用户 SQL。列表筛选里 **ADMIN 的角色筛选被钉死为 role=0**(`<if>` 条件直接写死),只有 BOSS 能改 `role`,权限边界下推到 SQL |
| `src/main/resources/mapper/OrderMapper.xml` | 订单 SQL。JOIN 会按明细数返回多行,MyBatis 用 `<collection>` 合并成 1 个 `OrderWithItems`;`OrderItemResultMap` 把主键列映射为 `item_id`,避免和订单 id 撞名 |
| `src/main/resources/mapper/BookCategoryMapper.xml` | 分类 SQL。全表查询按 `parent_id → sort_order` 升序排好,组装树时直接顺序遍历 |
| `src/main/resources/mapper/StatsMapper.xml` | 统计 SQL。注释解释了**为什么这里 `GROUP BY DATE(...)` 可以接受** —— "不用 YEAR()/MONTH()"的约定针对的是**点查**(对索引列套函数会让索引失效),这里是**区间聚合**,`WHERE created_time >= #{start}` 仍然走索引做范围扫描,而分组必须下推 SQL 否则要把整个区间拉回 JVM 再聚合 |
| `src/main/resources/mapper/SearchSuggestMapper.xml` | 候选词 SQL。LIKE 前后模糊**走不了索引**,但候选词查询带 LIMIT + 前端有防抖 + 图书量级小可以接受;**热度口径 = `order_status = 1`(PAID)的订单明细里 `quantity` 的累计**(只算数量不算笔数,"卖了 5 本"和"卖了 1 本 5 次"等价) |
| `src/main/resources/mapper/FeedbackMapper.xml` | 反馈 SQL。`Feedback`/`FeedbackReply` 是简单 entity,不用自定义 resultMap;只有带 `username`(来自联表 users)的 DTO 才单独写 |
| `src/main/resources/mapper/IpBanMapper.xml` | 封禁 SQL。`insertBan` 用 `useGeneratedKeys` 把自增主键回写到实体 |

### 13.4 `src/main/resources/scripts/redis` —— Lua 脚本

| 路径 | 一句话职责 |
|---|---|
| `src/main/resources/scripts/redis/warmup_book.lua` | 冷 key 预热(下单遇到 hash 不存在时调)。**逐字段 HSETNX,不覆盖已有字段** —— 并发预热时 HSET 会覆盖别人已写的 `reserved`,丢失已预占的量;放 Lua 是因为 5 个独立 HSETNX 跨 5 个 RTT,期间可能被其他客户端插入写操作 |
| `src/main/resources/scripts/redis/pre_deduct_stock.lua` | 库存预占(下单)。副作用 `stock -= qty, reserved += qty`;返回 ≥0 剩余可用库存 / -1 book 不在 Redis(调用方需先 warmUp 重试)/ -2 库存不足 |
| `src/main/resources/scripts/redis/confirm_stock.lua` | 确认扣减(支付成功)。**只减 `reserved` 不动 `stock`** —— 库存真扣在预占时就完成了 |
| `src/main/resources/scripts/redis/release_stock.lua` | 释放预占(取消订单 / 超时关单)。`reserved -= qty, stock += qty`;-2 表示 reserved 不足,属数据异常,正常情况下不该出现 |
| `src/main/resources/scripts/redis/sync_book_stock.lua` | 管理端改库存后同步 Redis。**为什么不能直接 DEL 整个 hash** —— hash 里存着在途订单的 `reserved`,直接删除会让所有未支付订单的预占"消失",这些订单付款时会被 DB 守卫拒绝,真实客户付不了款;脚本在 `reserved` 保持不变的前提下反推 `stock`,从而维持不变式 `Redis(stock + reserved) == DB.stock_quantity` |

---

## 14. `src/test`

| 路径 | 一句话职责 |
|---|---|
| `src/test/java/com/tmt/TMLibrary/TMLibraryApplicationTests.java` | 空的 `contextLoads` 冒烟测试,只确认 Spring 上下文能起来 |
| `src/test/java/com/tmt/TMLibrary/Author/RedisTest.java` | 手写实验用的 Redis 序列化器测试(正片代码大部分被注释掉,在试 `RedisTemplate` 的各种 value 序列化器)。包名 `Author` 是自己的试验田,不是正式测试 |
| `src/test/resources/db/schema.sql` | **职责不明** —— 0 字节空文件,疑似占位 |
| `src/test/resources/testdata/books-fixture.json` | 10 条图书 fixture,字段与 `BookSaveRequest` 对齐(title/author/isbn/price/publishedDate/stockQuantity) |

---

## 15. 顶层文件

| 路径 | 一句话职责 |
|---|---|
| `pom.xml` | Spring Boot **4.1.0** + Java **25** + 纯 MyBatis(无 MyBatis-Plus)+ Redis + jjwt。依赖注释里有干货:`jjwt-api` 编译期用不加 scope、`jjwt-impl` scope=runtime(**编译看不到类,只有运行加载**);`spring-boot-starter-validation` 在 4.x 已单独成包 |
| `mvnw` | Maven Wrapper POSIX 启动脚本,本机不装 Maven 也能构建 |
| `mvnw.cmd` | Maven Wrapper 的 Windows 版 |
| `Dockerfile` | 多阶段构建:maven+JDK25 编译 → JRE25-alpine 运行。最终镜像**没有源码、没有构建工具、没有 .env**;**构建阶段 `-DskipTests`**(测试要连 MySQL/Redis,构建容器里没有,跑必然失败);非 root(uid 10001)运行;`JAVA_OPTS` 用 `MaxRAMPercentage=75` 让 JVM 按 cgroup 限额算堆(否则容器限了内存 JDK 仍按宿主机内存算 → OOMKilled);`HEALTHCHECK` 走 `/actuator/health` |
| `.dockerignore` | 构建上下文排除。**最要紧的一条是 `.env`** —— 虽然 Dockerfile 只 COPY pom.xml 和 src 不会把它打进镜像,但它仍会被塞进构建上下文(慢,而且多一份暴露面) |
| `.env.example` | 环境变量模板(不含敏感信息,提交进 git 给团队复用)。MySQL / Redis / JWT / 日志路径 / 雪花机器号 / ES / CORS / 风控全在这里;`DB_PASSWORD` 与 `JWT_SECRET` 必须改,JWT 生成方式 `openssl rand -base64 48` |
| `.env` | 本地真实凭据文件(**被 `.gitignore` 忽略,本文件不记录其内容**) |
| `.gitignore` | 忽略 `target/`、IDE 配置、`logs/`、`.env`。注释留了一条教训:**`src/main/resources/db/*.sql` 曾因未入库而丢失**,现在不再忽略该目录 —— 建表脚本属于代码资产 |
| `.gitattributes` | 强制 `mvnw` 用 LF、`*.bat` 用 CRLF,避免 Windows 检出后脚本跑不起来 |
| `API.md` | 接口文档:响应格式、**鉴权白名单(按「路径 + HTTP 方法」判定)**、业务状态码表、各端点请求/响应示例 |
| `REFERENCE.md` | Book CRUD 业务代码参考。写于"项目业务代码已清空"那个时期,给出第二天手写时的完整参考实现 + 推荐编写顺序(先写启动类,否则 `./mvnw compile` 直接失败) |
| `issue.md` | 遗留问题清单 + 已修复记录。价值在修复记录:TTL 放大 60 倍、图书展示漏加白名单、索引未与实际查询对齐、超时关单只依赖 Redis(新增 DB 兜底扫描)等,每条都写了根因和修法 |

---

## 16. `scripts/`

| 路径 | 一句话职责 |
|---|---|
| `scripts/dev.sh` | 一键启动后端:从 `.env` 加载环境变量(`set -a` 让变量自动 export,Spring Boot 才读得到)→ 校验 `DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET` 非空 → 透传参数给 `./mvnw spring-boot:run` |
| `scripts/install-ik.sh` | 给 ES 装 IK 中文分词器。**为什么不是一条 `elasticsearch-plugin install` 就完事**:IK 只发布到 9.5.3 而本项目 ES 是 9.5.4,而 ES 在安装阶段做**严格版本校验**(patch 不同也直接抛异常,不是警告);所以脚本下载最接近版本 → 改 `plugin-descriptor.properties` 里的版本号 → 重新打包 → 再安装。不装的后果:standard 分析器把中文切成单字,「设计」会命中「计算机」(因为有个「计」字) |
| ⚠️ `scripts/schema.sql` | **给人看的总体规划表,不用于建库**。内容是建库语句 + 设计约定(不用物理外键 / 必须 InnoDB / 时间用 DATETIME)+ 旧库补索引说明,**只含 4 张核心表**(`users` / `books` / `orders` / `order_items`)。**真正建表请用 `src/main/resources/db/*.sql`** —— 那边才是按表拆好、可直接执行的脚本,而且多出了 `book_categories` / `feedbacks` / `ip_bans` 三张后加的表 |
| `scripts/logstash/README.md` | 说明**两个 ES 索引的分工**:`tmlibrary_books_suggest` 由应用启动时建(`SuggestIndexInitializer`,下拉候选词、字段少);`tmlibrary_books` 由 Logstash 同步(全文搜索、字段全 —— 含分类/价格/库存/日期)。含 IK 分词器的验证命令 |
| `scripts/logstash/logstash.conf` | Logstash JDBC 定时同步配置。注释解释了**为什么不做增量**:热度来自 `order_items`(别人下单付款),而 `books.updated_time` 不会因此变化 —— 用 `updated_time` 做游标的话销量永远同步不过去;所以现在是全量重读,靠 ES 侧 `document_id + doc_as_upsert` 做幂等覆盖 |
| `scripts/logstash/books-index-template.json` | `tmlibrary_books` 索引模板:IK 中文分词(`ik_max_word` 建索引 / `ik_smart` 查询)+ title 的多字段 `kw`(keyword,供精确匹配/聚合) |

---

## 17. `testdata/`

| 路径 | 一句话职责 |
|---|---|
| `testdata/README.md` | 测试数据目录说明。注意:它提到的 seeder 源码 `src/main/java/com/tmt/TMLibrary/testdata/BookTestDataSeeder.java` **现在已经不存在了**,`load.sh` 里对它的调用可能已经失效 |
| `testdata/books-1000.json` | 1000 条图书数据(8002 行),字段与 `books` 表对齐 |
| `testdata/load.sh` | 批量生成 / 入库脚本;DB 凭据走环境变量(`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`),`--truncate` 会在入库前清空 `book` 表 |

---

## 18. `.mvn`

| 路径 | 一句话职责 |
|---|---|
| `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper 配置,指定 Maven 版本与 distributionUrl |
| `.mvn/wrapper/maven-wrapper.jar` | Maven Wrapper 的引导 jar(二进制,`mvnw` 靠它下载并启动对应版本的 Maven) |

---

## 附:已知问题速查

| 位置 | 问题 |
|---|---|
| `config/MyUtilsConfig.java` | 空 `@Configuration`,无 Bean —— 要么补上 `CaptchaUtil` 相关的 Bean,要么整个删掉 |
| `config/RestConfig.java` | `RestTemplate` 的 Bean 被注释掉 —— `IpUtil` 自己 new 了一个,长期看应二选一 |
| `config/RedisConfig.java:18` | 有一行被注释掉的**明文 Redis 口令**(已失效,但建议删除该行) |
| `src/test/resources/db/schema.sql` | 0 字节空文件 |
| `scripts/schema.sql` | 容易被误当成建库脚本执行 —— 它只是给人看的规划表,且缺少后加的三张表 |
| `testdata/README.md` | 引用的 `BookTestDataSeeder.java` 已不存在 |
| `controller/BookController.java` | 写方法的 ADMIN/BOSS 鉴权是 2026-09 才补的,补之前是裸奔状态 —— 保留此条以免将来重构时又被"顺手"去掉 |
| `service/impl/SearchSuggestServiceImpl.java` | 个性化推荐做不了,根因是白名单路径不解析 token 拿不到 userId;要做需先支持"可选鉴权" |

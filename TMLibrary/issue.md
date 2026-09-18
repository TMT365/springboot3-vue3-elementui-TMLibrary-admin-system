# TMLibrary 遗留问题清单

> 更新时间:2026-09-18(第七轮修复后)
> 状态:历次发现的逻辑问题已全部修复。本文件保留完整修复记录供追溯,
> 末尾的「当前未解决」列出**已知但仍未处理**的事项。

---

## 当前未解决

按严重程度排。这些**没有修**,是留给后续处理的。

| # | 问题 | 影响 | 建议处理方式 |
|---|---|---|---|
| 1 | **无自动化测试覆盖关键路径** | 库存并发、订单状态机、鉴权三块核心逻辑没有测试保护,改动靠人工回归 | 优先补 `PurchaseServiceImpl` 的状态机测试和 `BookInventoryServiceImpl` 的 Lua 并发测试(可用 Testcontainers) |
| 2 | **`RedisConfig.java:18` 有一行被注释掉的明文口令** | 注释掉了不执行,但它已进入公开 git 历史(提交 `5daba7a`)。**实测该口令已失效**(`AUTH failed: WRONGPASS`),不构成泄漏 | 直接删掉那一行(连同整段被注释的旧 Bean 一起清掉) |
| 3 | **IP 风控的第三方归属地查询是同步阻塞的** | `IpUtil` 调 ip-api.com / ipinfo.io,慢或超时会拖住封禁判定链路 | 加超时 + 本地缓存,或改成异步补充信息 |
| 4 | **`config/MyUtilsConfig.java`、`config/RestConfig.java` 是空壳** | 无功能影响,但会让读代码的人困惑 | 删掉,或补上注释说明"预留给 X" |
| 5 | **`testdata/README.md` 引用的 seeder 源码已不存在** | `src/main/java/com/tmt/TMLibrary/testdata/BookTestDataSeeder.java` 找不到,`load.sh` 里对它的调用可能失效 | 确认后修文档或补回源码 |
| 6 | **`src/test/resources/db/schema.sql` 是 0 字节空文件** | 测试上下文若依赖它会失败 | 补齐内容或删除 |

---

## 附:已修复清单

### 第七轮(本次,2026-09-18)

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **图书写操作完全没鉴权** | `BookController` 的 4 个写方法(`POST /created`、`PATCH /{isbn}`、`PATCH /{isbn}/stock`、`DELETE /deleted/isbn/{isbn}`)**一个角色检查都没有** —— 任何已登录用户拿 token 直接打接口就能增删改图书、改库存。前端把这些页面挂在 `meta: { admin: true }` 路由下,但**前端路由守卫不是安全边界**,绕过它只需要一个 curl。同期 `PurchaseController` / `StatsController` / `SecurityController` / `CategoryController` / `FeedbackController` 都有检查,**只有 BookController 漏了**,属于遗漏而非设计 | 照 `SecurityController.requireAdmin` 的既有写法加 `requireAdminOrBoss(me)`:未登录抛 `AuthException`(401),角色不够抛 `BusinessException`(403)。已端到端验证:USER 打 4 个写接口全部 403、读接口仍 200(未误伤商城)、ADMIN 正常通过、无 token 401 |
| **`scripts/schema.sql` 建表不全的误判** | 曾认为它是"一键建库"脚本但只建 4 张表(缺 `book_categories`/`feedbacks`/`feedback_replies`/`ip_bans`)。**经确认它只是给人看的总体规划表,不用于建库** | 不修改内容。改为在 `README.md` 和 `docker-compose.yml` 里写明真正的建表路径(`db/*.sql` 四个文件按序执行),Docker 初始化按 `01~05` 前缀挂载 |

### 第六轮

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **索引未与实际查询对齐** | 建表脚本的二级索引部分是我推测的,未逐个核对是否有查询使用。核实后发现 `idx_orders_status_expire` 与 `idx_order_items_book_id` **完全无用**;users 表 6 个索引服务一个后台列表,偏重 | 见下三项 |
| **超时关单只依赖 Redis(缺陷根源)** | `expire_time` 列只写不读,超时订单的发现完全依赖 Redis ZSet —— 该索引丢失时订单**永久停留 PENDING**,预占不释放;且对账任务会把幽灵预占当成合法值固化下来 | 新增 DB 兜底扫描:`SELECT order_number FROM orders WHERE order_status=PENDING AND expire_time < NOW()`(走 `idx_orders_status_expire`),调度器在 Redis 扫描后执行;命中即打 WARN + 新增指标 `tmlibrary_order_expire_db_fallback_total` |
| **users 索引过重** | 6 个索引中 3 个仅服务低频可选筛选(锁定时间/失败次数/软删时间) | 精简为 3 个(`uk_username` + `role,status,created_time` + `created_time`),低频筛选走全表扫描并在注释中说明取舍 |
| **users 列表分页无 ORDER BY** | `LIMIT offset,n` 无排序时翻页可能重复或漏记录(books 侧有 `ORDER BY id`,users 侧漏了) | 补 `ORDER BY id ASC` |
| `idx_order_items_book_id` | 当前无查询使用 | 按需求**保留**,注释改为"保留供后续按图书统计销量" |

### 第五轮

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **图书展示漏加白名单** | API.md 标注 6 个图书查询端点为免鉴权,但过滤器白名单只有 login/register/captcha;前端 `/mall` 是公开路由,接线后未登录访客会吃 401 | `JwtAuthFilter` 白名单升级为「路径 + HTTP 方法」规则;`/api/books` 的 **GET 全部放行**,同前缀的 POST/PATCH/DELETE 仍需 token(25 项决策矩阵已验证) |

### 第四轮

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **TTL 放大 60 倍** | `RandomExpirationTimeWithOffset` 先把值换算成秒、却仍用原单位构造 `Expiration`,导致所有非秒级 TTL 被放大 60 倍(3 分钟 → 3 小时;已用真实 jar 实测确认) | 换算与构造统一使用秒;亚秒级入参兜底为 1 秒;异常类型改为 `IllegalArgumentException` |
| **lastLoginIp 恒为 NULL** | `LoginRequest.ipAddress` 从未被赋值,导致 `last_login_ip` 永远为空、按 IP 筛选永远无结果 | 新增 `IpUtil.resolveClientIp()`(纯本地解析,不发起网络请求);登录接口注入客户端真实 IP |
| **HTTP 状态码不统一** | 业务异常一律返回 HTTP 200(错误码仅存在于响应体),而过滤器返回真实 401 —— 网关按状态码统计错误率失真 | `GlobalExceptionHandler` 全部改用 `ResponseEntity`,返回与 `code` 一致的真实状态码;前端 `request.ts` 错误分支改为解包响应体 |

### 第三轮

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **C-3** | 订单 Hash / 历史 ZSet 只写不读,每单白耗 7 次 Redis 往返 + 漂移隐患 | 移除两类 key 的全部写入与常量定义,订阅单一 DB 真值源;下单 Redis 往返 7 → 1 |
| **B-1** | Snowflake `(1,1)` 硬编码,多实例部署会撞号 | 改为 `app.snowflake.worker-id` / `datacenter-id` 可配置,启动时打印生效值 |
| **O-2** | 库存存在两个写入口,语义含糊无法审计 | 新增 `PATCH /api/books/{isbn}/stock` 盘点接口;`BookUpdateRequest` 移除 `stockQuantity` |
| **O-4** | 无指标,库存漂移不可观测 | 引入 Actuator + Micrometer,新增 3 个业务指标并接入服务层 |
| **O-5** | 列表/搜索缺索引,存在全表扫描 | 新增 `scripts/db-indexes.sql`(后续并入 `scripts/schema.sql`) |
| **安全** | Redis 密码与 JWT secret 明文提交进 git | 全部改为环境变量注入;`.env.example` 补全模板与生成指引;`dev.sh` 校验 JWT_SECRET |

### 第二轮

| 编号 | 问题 | 修复方式 |
|---|---|---|
| **I-1** | 改任何图书字段都会删除库存 Hash,清空在途预占 → 真实客户付款被拒 | 新增 `sync_book_stock.lua`,改为「保留 reserved,按 DB 重算 stock」;`updateByISBN` 不再删除 Hash |
| **I-2** | Redis `stock` 漂移后永不自愈 | 新增 `BookInventoryReconcileScheduler`,用不变式 `stock + reserved == DB.stock_quantity` 周期校验并修复 |
| **I-3** | `release` / `confirm` 返回 -2 仅 WARN,线上不可观测 | 提升为 ERROR,输出 `INVENTORY DRIFT` 前缀,便于日志检索与告警 |
| **I-4** | 付款库存不足 → 订单卡死 PENDING,用户既付不了款也取消不掉 | 增加预检 + `autoCancelBecauseOutOfStock`;用 `noRollbackFor` 让"自动关单"真正提交 |
| **I-5** | 同一 bookId 在 items 中重复出现会生成多条 order_item | 下单入口按 bookId 合并数量(带溢出保护) |
| **C-1** | `createUser` 不清负缓存 → 新注册用户 3 分钟内登不上 | 注册成功后删除 `userByUsername` 缓存 |
| **C-2** | 列表接口的 `role` / `failedLoginAttempts` 筛选被静默忽略 | DTO 字段改为 `Integer`,XML 补上两个查询条件(role 筛选仅对 BOSS 生效) |
| **C-4** | ADMIN 可绕过列表限制查看 BOSS 详情 | `getById` 权限规则与列表对齐:ADMIN 只能看普通用户 |
| **B-2** | SingleFlight follower 等待阈值硬编码 5 秒 | 改为 `app.book.singleflight-timeout-ms` 可配置 |
| **B-3** | JWT 白名单用 `startsWith` 前缀匹配,存在误放行隐患 | 拆分为精确匹配(login/register)+ 前缀匹配(captcha) |
| **B-4** | 登录时 `user.getStatus()` 为 NULL 会 NPE | 常量前置:`ACTIVE.getCode().equals(status)` |
| **B-5** | `createOrder` 主键未回填时返回假的订单号 1 | 改为 fail-fast 抛异常 |
| **O-1** | —— | 即 I-2 的对账任务,已落地 |

### 第一轮

**Redis Key 统一**

| 问题 | 修复 |
|---|---|
| JWT 黑名单写入用 `user:jwt:blackList:`、校验读 `auth:jwt:blackList:` — 登出完全失效 | 统一为 `RedisKeys.jwtBlacklist(jti)` |
| 用户状态缓存 `user:users:status:{id}` 与 `user:{id}:status` 两套写法,失效操作全部空转 | 统一为 `RedisKeys.userStatus(id)` |
| 用户 by-username 缓存删除路径少一段 `username:`,改密后旧密码仍可登录 30 分钟 | 统一为 `RedisKeys.userByUsername(name)` |
| 图书信息缓存错放在 `user:books:` 域 | 改为 `RedisKeys.bookInfoByIsbn(isbn)` |
| 全部 key 硬编码散落在 8 个文件 | 集中于 `common/redis/RedisKeys.java`,统一 `tmlibrary:{domain}:byXxx:{id}:{feature}` 规范 |

**库存一致性**

| 问题 | 修复 |
|---|---|
| DB `stock_quantity` 从不扣减,Redis 淘汰即超卖 | 付款时原子扣减 DB(`WHERE stock_quantity >= N`),DB 成为库存真值 |
| `createOrder` 后段(DB/Redis 写入)异常无补偿 → 库存永久泄漏 | 全方法 try/catch,统一反向 release 所有已预占 bookId |
| `cancelExpiredOrder` 崩溃窗口 → 库存永久泄漏 | 重排为「先释放 → 守卫 UPDATE → 提交 → afterCommit 写 Redis」 |
| 状态流转 UPDATE 无前置状态守卫 | 新增 `updateStatusByOrderNumberGuard` |
| `warmUpBook` 5 次独立 `HSETNX` 非原子 | 合并为 `warmup_book.lua` 单次调用 |

**并发与事务**

| 问题 | 修复 |
|---|---|
| `@Transactional` 方法内直接写 Redis,DB 回滚不撤销 Redis | 统一改为 `afterCommit` 钩子 |
| 登录失败计数「读-改-写」跨 3 条 SQL,并发下双锁、off-by-one | 合并为单条原子 SQL `incrementAndMaybeLock` |
| 锁定到期后未重置计数,解锁后第一次失败立即再次锁定 | 锁定过期时自动重置 |

**缓存与校验**

| 问题 | 修复 |
|---|---|
| `getByISBN` 空串负缓存导致 `readValue("")` 抛异常,且新建图书 3 分钟不可见 | 改用哨兵 JSON,`create` / `update` / `delete` 主动失效 |
| `checkUser` 空串缓存中毒,命中即抛异常 | 哨兵化 + 损坏缓存自动驱逐 |
| 登录正缓存被无条件写入的空 marker 覆盖 | 改为 if/else 分支 |
| 登录失败响应泄露用户是否存在(账号枚举) | 统一文案「用户名或密码错误」 |
| `BookController.list` 的 `size` 无上限 | 归一到 `[1, 100]` |
| `UserVo` 邮箱未脱敏,脱敏函数有越界风险 | 邮箱按本地部分脱敏,修复 `substring` 越界 |

**鉴权与业务**

| 问题 | 修复 |
|---|---|
| ADMIN 列表查询 SQL 过滤反向(`AND role = #{role}`)导致后台列表恒为空 | 修正为 `AND role = 0` |
| `GET /api/users/{id}` 无权限校验 | 增加「自己 / ADMIN / BOSS」校验 |
| 库存不足返回 404 语义错误 | 改为 409 CONFLICT |
| 登出无明确的客户端跳转信号 | 新增 `LogoutResponse` |
| `updateUserById` 无 `deleted_at IS NULL` 守卫,可复活软删用户 | UPDATE 增加守卫条件 |
| 订单 INSERT 不显式写时间字段 | Service 层显式设置 |
| 调度器 `fixedRate` + 锁 TTL(50s)< 扫描周期(60s) | 改 `fixedDelay`,`TTL` → 120s |
| 单次 `ZRANGEBYSCORE` 可能拉出十万级 member 阻塞 Redis | 改分批拉取(200/批)+ 批次数上限 |

---

## 验证状态说明

- ✅ **静态验证**:`mvn clean compile` 通过(82 个源文件)
- ✅ **单元级验证**:TTL 修复用真实 spring-data-redis jar 实测;白名单用 25 项
  「方法 × 路径」决策矩阵验证(含"用户/订单 GET 仍须拦截"的回归检查)
- ✅ **Lua 脚本验证**:`warmup_book.lua`、`sync_book_stock.lua` 已针对运行中的 Redis 实测
  (idempotency、reserved 保留、负库存截断)
- ⚠️ **运行时验证**:集成测试未执行 —— 本地 MySQL 需要密码且仓库无 `.env`;
  logback 默认日志路径 `/opt/logs` 不存在(可用 `LOG_FILE` 环境变量覆盖)

---

## 部署注意事项(本轮改动引入)

1. **必须设置 `JWT_SECRET`**,否则应用启动即失败(≥ 32 字节,`openssl rand -base64 48`)
   - ⚠️ 已签发的 token 在密钥更换后全部失效,需配合发布窗口
2. **必须设置 Redis 凭据环境变量**(如启用密码):`REDIS_USERNAME` / `REDIS_PASSWORD`
3. **多实例部署**必须为每个实例分配不同的 `SNOWFLAKE_WORKER_ID`(0-31)
4. **建表脚本需手动执行**:`scripts/schema.sql`(含建表 + 索引;已有库补索引见文末注释段)
5. **`stockQuantity` 已从 `PATCH /api/books/{isbn}` 移除**,前端需改用
   `PATCH /api/books/{isbn}/stock`(当前前端图书编辑页为 P4 占位,尚未接线,不影响)

---

## 待观察项

以下问题已修复,但**依赖运行时验证**才能确认效果,建议上线后重点观察:

| 观察点 | 指标 / 日志 | 说明 |
|---|---|---|
| 库存是否仍有漂移 | `tmlibrary_inventory_drift_total` | 持续 > 0 说明有未覆盖的异常路径,需查 `INVENTORY DRIFT` 日志定位 |
| 对账任务修复频率 | `tmlibrary_inventory_reconcile_repaired_total` | 偶发正常;持续增长代表漂移在反复发生 |
| 下单补偿是否失效 | `tmlibrary_order_compensate_failed_total` | 任何非零值都需人工对账 |
| 付款时自动关单频率 | `order {} auto-cancelled at payment` | 频繁出现说明 Redis 预占与 DB 库存长期不一致 |

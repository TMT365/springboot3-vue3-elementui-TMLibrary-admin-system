# TMLibrary 遗留问题清单

> 更新时间:2026-09-13(第二轮修复后)
> 状态:本文件记录**尚未修复**的问题。已完成项见文末「附:已修复清单」。

---

## 一、待决策项(需要业务/架构判断,非纯技术修复)

### 🔵 C-3 订单 Hash / 历史 ZSet 只写不读

**实测引用统计**

```
orderData              4 处引用 — 全是 opsForHash().put,零读取
userHistoryIdx         1 处引用 — 只有 ZSet.add,零读取
userPendingExpireIdx   6 处引用 — 被调度器读取 ✓ 有效
```

每笔订单白白多 **7 次 Redis 往返**,并引入「DB 与 Redis 订单数据可能漂移但无人发现」的隐患。

**两个方向,需选一个**

| 方案 | 做法 | 代价 |
|---|---|---|
| **用法化** | `getOrderByOrderNumber` / `listOrdersByUserId` 改为缓存优先 + 回填 | 增加一层缓存一致性维护成本;订单详情有 JOIN(含 items),缓存结构需要重新设计 |
| **删除** | 移除 `orderData` 与 `userHistoryIdx` 的全部写入 | 若将来要做"订单历史快速分页"需重新引入 |

> 当前未动:两种方案都会改变可观测行为,需先确认是否有后续功能规划依赖这两个 key。

---

### 🔵 B-1 多实例部署 Snowflake 会撞号

**位置**:`PurchaseServiceImpl`

```java
private final Snowflake snowflake = new Snowflake(1, 1);   // workerId / datacenterId 硬编码
```

两个实例并行时,同一毫秒 + 同一序列 → 相同 `orderNumber`。有唯一索引则插入失败,无则数据错乱。

**可选方案**
1. 从配置 / 环境变量读取(`app.snowflake.worker-id`),部署时每实例分配不同值
2. 按 Pod 序号 / IP 末段派生
3. 引入发号器(数据库号段 / Redis INCR)替代本地 Snowflake

> 当前未动:单实例部署下无影响,选哪种取决于部署形态。

---

### 🔵 O-2 库存变更收敛为单一入口

当前库存有两个写入口:
1. **交易链路** — 下单预占 / 取消释放 / 付款扣减(走 Lua + 条件 UPDATE)
2. **管理链路** — `PATCH /api/books/{isbn}` 直接设置 `stockQuantity`

虽然已通过 `syncStockFromDb`(保留 reserved 重算 stock)让两者不冲突,但语义上仍存在歧义:
管理员把库存改成 5,而当时有 10 本在途预占 → 可用库存被截断为 0,在途订单付款时会失败。

**建议**:拆一个专用接口 `PATCH /api/books/{isbn}/stock`,语义为"盘盈/盘亏调整",
与普通字段更新分离,便于审计与权限收紧。

---

### 🔵 O-4 可观测性(部分完成)

已做:`release` / `confirm` 失败、对账发现漂移,均以 **ERROR** 级别输出并带 `INVENTORY DRIFT` 前缀。

**仍缺**:未接入 Micrometer counter,无法做告警规则与趋势看板。建议引入 `spring-boot-starter-actuator` 并注册:
- `inventory_drift_total` — 漂移次数
- `inventory_reconcile_repaired_total` — 对账修复次数
- `order_compensate_failed_total` — 下单补偿失败次数

---

### 🔵 O-5 列表接口缺索引

`users` 表按 `username LIKE '%x%'`、`created_time` / `status` / `role` 组合查询,
`books` 表按 `published_date` / `stock_quantity` 区间查询。

未见到对应的索引定义(DDL 不在本仓库),数据量上升后大概率全表扫描。

**建议**:对高频筛选列建组合索引,`LIKE '%x%'` 这类前后模糊无法走索引,
如需支持建议改为前缀匹配或引入全文索引 / ES。

---

## 附:已修复清单

### 第二轮(本次)

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

- ✅ **静态验证**:`mvn clean compile` 通过(80 个源文件)
- ✅ **Lua 脚本验证**:`warmup_book.lua`、`sync_book_stock.lua` 已针对运行中的 Redis 实测(idempotency、reserved 保留、负库存截断)
- ⚠️ **运行时验证**:集成测试未执行 —— 本地 MySQL 需要密码且仓库无 `.env`;logback 默认日志路径 `/opt/logs` 不存在(可用 `LOG_FILE` 环境变量覆盖)

## 已知安全事项(未在代码中修复)

`application-dev.yml` 中 Redis 密码与 JWT secret 为明文提交进 git,而本地 Redis 实际未启用密码。
JWT secret 泄露意味着任何拿到仓库的人都能伪造任意用户的令牌。
**建议**:迁移到环境变量并轮换密钥。

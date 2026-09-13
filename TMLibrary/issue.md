# TMLibrary 遗留问题清单

> 生成时间:2026-09-13
> 范围:代码逻辑层面(库存一致性 / 缓存失效 / 业务路径 / 优化方向)
> 状态:本文件记录**尚未修复**的问题。已完成的修复见文末「附:已修复清单」。

---

## 一、库存一致性

### 🔴 I-1 改任何图书字段都会删掉库存 Hash → 在途预占被清空

**位置**:`BookServiceImpl.updateByISBN`(失效缓存那几行)

```java
int rowsAffected = bookMapper.updateBookByISBN(book);
if (rowsAffected > 0) {
    stringRedisTemplate.delete(RedisKeys.bookInfoByIsbn(isbn));
    stringRedisTemplate.delete(RedisKeys.bookInventory(book.getId()));   // ← 无条件删库存 Hash
}
```

**后果链**

1. 管理员改个**错别字**(标题/作者)也会执行到这一行
2. 库存 Hash 被整体删除 → 里面记录的 `reserved`(在途预占)一并消失
3. 下次 `tryReserve` 因 key 不存在 → `warmUpBook` 从 DB 重建 → `reserved=0`、`stock=DB值`
4. **所有 PENDING 订单的预占被"遗忘"** → 这些订单付款时 DB 守卫发现库存不足 → 409,**真实客户付不了款**
5. 同时 `BeanUtils.copyProperties(request, book)` 会把前端传的 `stockQuantity` 直接写进 DB,与管理端的另一套库存写入口冲突

**修复方向**

不要 `delete`,而是「保留 `reserved`,重算 `stock`」——利用下面的不变式:

```
stock = DB.stock_quantity - reserved
```

仅在 `stockQuantity` 真正变化时才触碰库存 Hash;读取 `reserved` 与写入 `stock` 需用 Lua 保证原子。

---

### 🔴 I-2 `Redis.stock` 一旦漂移永不自愈

**位置**:`BookInventoryServiceImpl.warmUpBook` 的触发条件

`warmUpBook` 只在 Lua 返回 `-1`(key 不存在)时触发。只要 Hash 存在,即使 `stock`/`reserved` 已经错位,也不会被拉回真值:

- `release` 返回 `-2`(reserved 不足)→ 仅 `log.warn`,错位留存
- 重复 `confirm`、人工误操作、I-1 的删除重建 → 同样留下永久错位
- 结果:前台显示「售罄」而 DB 有货(或反之)

**可用的不变量(天然校验和)**

```
Redis(stock + reserved) == DB.stock_quantity
```

| 时刻 | Redis stock | Redis reserved | 和 | DB |
|---|---|---|---|---|
| 初始 | 10 | 0 | 10 | 10 ✓ |
| 预占 3 | 7 | 3 | 10 | 10 ✓ |
| 付款 | 7 | 0 | 7 | 7 ✓ |
| 取消 | 10 | 0 | 10 | 10 ✓ |

**修复方向**

新增对账定时任务:扫描 `tmlibrary:book:byId:*:inventory`,不满足不变式就修正
`stock = DB.stock_quantity - reserved`。这条任务同时兜住 I-1 / I-3 的所有漂移。

---

### 🟡 I-3 `release` / `confirm` 返回 `-2` 被静默吞掉

**位置**:`BookInventoryServiceImpl.release` / `confirm`

仅 `log.warn`。数据已错位但没有任何指标可观察,线上无法发现。建议提升为 ERROR 级别并接入 Micrometer counter。

---

### 🟡 I-4 付款失败后订单卡死在 PENDING

**位置**:`PurchaseServiceImpl.payOrder`(DB 扣减失败分支)

遇到库存不足 → 抛 409 → `@Transactional` 回滚 → 订单仍是 PENDING,用户既没被自动取消,也没有明确指引,只能手动取消或等 30 分钟超时。

**修复方向**:付款遇到库存不足时,顺手把订单置为 CANCELLED(带守卫 UPDATE)并释放预占,响应明确告知「库存不足,订单已取消」。

---

### 🟢 I-5 同一 bookId 在 items 里重复出现

`[{bookId:1,qty:2},{bookId:1,qty:3}]` → 生成两条 `order_item`、分两次预占。库存上没错,但业务上应 merge-by-bookId。

---

## 二、缓存失效

### 🔴 C-1 `createUser` 不清负缓存 → 新注册用户 3 分钟内登不上

**位置**:`UserManagementServiceImpl.createUser`

```java
userMapper.insertUser(user);
return user.getId();      // ← 没有清理 RedisKeys.userByUsername(username)
```

**场景**:有人用 `alice` 尝试登录(用户不存在)→ 写入 `NEGATIVE_SENTINEL`(3 分钟 TTL)→ alice 注册成功 → **登录直接命中负缓存**,返回「用户名或密码错误」,最长 3 分钟。

**修复方向**:注册成功后 `delete(RedisKeys.userByUsername(username))`。
(对比:`BookServiceImpl.create` 已有同样的清理,UserService 侧遗漏。)

---

### 🟡 C-2 列表接口的 `role` / `failedLoginAttempts` 筛选参数被静默忽略

| 层 | `role` 是否存在 |
|---|---|
| DTO `UserSearchRequest` | ✅ 有字段 |
| API.md §3.1 | ✅ 有文档 |
| Service 层 | ✅ 还专门做了权限判断 |
| **UserMapper.xml** | ❌ **完全没有引用** |

DTO 共 16 个字段,SQL 实际使用 14 个 → `role` 与 `failedLoginAttempts` 传了等于没传。

**修复方向**:XML 补条件,注意与 `role == 1 → AND role = 0` 的权限约束叠加。

---

### 🟡 C-3 订单 Hash / 历史 ZSet 只写不读

**实测引用统计**

```
orderData              4 处引用 — 全是 opsForHash().put,零读取
userHistoryIdx         1 处引用 — 只有 ZSet.add,零读取
userPendingExpireIdx   6 处引用 — 被调度器读取 ✓ 有效
```

每笔订单白白多 **7 次 Redis 往返**,并引入「DB 与 Redis 订单数据可能漂移但无人发现」的隐患。

**修复方向(二选一)**
1. 用起来:`getOrderByOrderNumber` / `listOrdersByUserId` 改为缓存优先 + 回填
2. 删掉这两类写入,只保留 `userPendingExpireIdx`

---

### 🟢 C-4 ADMIN 可绕过列表限制查看 BOSS 详情

`selectUsersByCriteria` 中 ADMIN 被明确限制「看不到 BOSS」;但 `UserController.getById` 只做了「自己 / ADMIN / BOSS 可看」,**ADMIN 知道 id 就能拿到 BOSS 详情**,与列表规则不一致。

**修复方向**:统一为 ADMIN 查 BOSS 详情也返回 403。

---

## 三、业务路径

### 🟡 B-1 多实例部署 Snowflake 会撞号

**位置**:`PurchaseServiceImpl` 字段初始化

```java
private final Snowflake snowflake = new Snowflake(1, 1);   // workerId / datacenterId 硬编码
```

两个实例并行时,同一毫秒 + 同一序列 → 相同 `orderNumber`。有唯一索引则插入失败,无则数据错乱。

**修复方向**:从配置 / 环境变量读取,或按机器标识派生。

---

### 🟡 B-2 `getByISBN` 的 follower 等待阈值硬编码 5 秒

`existing.get(5, TimeUnit.SECONDS)` — DB 慢查询时会批量抛 `INTERNAL_ERROR`。建议配置化,或与 DB 超时对齐。

---

### 🟢 B-3 JWT 白名单使用 `startsWith` 前缀匹配

```java
WHITELIST.stream().anyMatch(path::startsWith);
```

`/api/users/login` 会匹配 `/api/users/login-anything`。当前无此类路由,不可利用,但认证代码应精确匹配:login / register 用 `equals`,仅 `/api/captcha/` 保留 `startsWith`。

---

### 🟢 B-4 登录时 `user.getStatus()` 可能 NPE

```java
if (!user.getStatus().equals(UserStatus.ACTIVE.getCode()))   // status 为 NULL → NPE → 500
```

常量前置即可天然空安全:`!UserStatus.ACTIVE.getCode().equals(user.getStatus())`。

---

### 🟢 B-5 `createOrder` 可能返回假订单号

```java
return order.getId() != null ? order.getId() : 1;   // useGeneratedKeys 未回填时返回 1
```

应 fail-fast 抛异常,避免把错误数据返回给前端。

---

## 四、优化方向(按收益排序)

| # | 方向 | 收益 |
|---|---|---|
| **O-1** | **加库存对账任务** — 用 `stock + reserved == DB.stock_quantity` 不变式,不满足则用 Lua 重算 `stock` | 一条任务兜住 I-1 / I-2 / I-3 的全部漂移,是该双轨库存模型唯一的自愈手段 |
| **O-2** | **库存变更收敛为单一入口** — 禁止管理端直接改 `stockQuantity`(或改为专用「调整库存」接口,内部走同一套 Lua) | 消除两个写入口冲突,`updateByISBN` 不再需要触碰库存 Hash |
| **O-3** | **orderData / historyIdx 二选一**(见 C-3) | 每单省 7 次 Redis 往返,去掉无法验证的重复数据源 |
| **O-4** | **可观测性** — `CRITICAL` 日志、Lua `-2`、补偿失败全部接入 Micrometer counter | 当前仅有 log,线上问题不可发现 |
| **O-5** | **补索引** — `users(created_time/status/role)` 组合、`books(published_date/stock_quantity)` 区间查询 | 列表接口目前很可能全表扫描 |

---

## 附:已修复清单

以下问题已在 2026-09-13 的修复中处理完毕,此处仅作背景说明。

### Redis Key 统一

| 问题 | 修复 |
|---|---|
| JWT 黑名单写入用 `user:jwt:blackList:`、校验读 `auth:jwt:blackList:` — 登出完全失效 | 统一为 `RedisKeys.jwtBlacklist(jti)` |
| 用户状态缓存 `user:users:status:{id}` 与 `user:{id}:status` 两套写法,失效操作全部空转 | 统一为 `RedisKeys.userStatus(id)` |
| 用户 by-username 缓存删除路径少一段 `username:`,改密后旧密码仍可登录 30 分钟 | 统一为 `RedisKeys.userByUsername(name)` |
| 图书信息缓存错放在 `user:books:` 域,且改库存不影响库存 Hash | 改为 `RedisKeys.bookInfoByIsbn(isbn)`,更新时同步失效库存 Hash |
| 全部 key 硬编码散落在 8 个文件 | 集中于 `common/redis/RedisKeys.java`,统一 `tmlibrary:{domain}:byXxx:{id}:{feature}` 规范 |

### 库存一致性

| 问题 | 修复 |
|---|---|
| DB `stock_quantity` 从不扣减,Redis 淘汰即超卖 | 付款时原子扣减 DB(`WHERE stock_quantity >= N`),DB 成为库存真值 |
| `createOrder` 后段(DB/Redis 写入)异常无补偿 → 库存永久泄漏 | 全方法 try/catch,统一反向 release 所有已预占 bookId |
| `cancelExpiredOrder` 崩溃窗口 → 库存永久泄漏 | 重排顺序为「先释放 → 守卫 UPDATE → 提交 → afterCommit 写 Redis」 |
| 状态流转 UPDATE 无前置状态守卫 | 新增 `updateStatusByOrderNumberGuard`,`WHERE order_status = #{fromStatus}` |
| `warmUpBook` 5 次独立 `HSETNX` 非原子 | 合并为 `warmup_book.lua` 单次调用 |

### 并发与事务

| 问题 | 修复 |
|---|---|
| 所有 `@Transactional` 方法内直接写 Redis,DB 回滚不撤销 Redis | 统一改为 `afterCommit` 钩子 |
| 登录失败计数「读-改-写」跨 3 条 SQL,并发下双锁、off-by-one | 合并为单条原子 SQL `incrementAndMaybeLock`,`IF(...)` 内联判定 |
| 锁定到期后未重置计数,解锁后第一次失败立即再次锁定 | 锁定过期时自动 `resetFailedLoginAttemptsById` |

### 缓存与校验

| 问题 | 修复 |
|---|---|
| `getByISBN` 空串负缓存导致 `readValue("")` 抛异常,且新建图书 3 分钟不可见 | 改用哨兵 JSON,`create` / `update` / `delete` 主动失效 |
| `checkUser` 空串缓存中毒,命中即抛异常 | 哨兵化 + 损坏缓存自动驱逐 |
| 登录正缓存被无条件写入的空 marker 覆盖,正缓存永不生效 | 改为 if/else 分支,只在未命中时回填 |
| 登录失败响应泄露用户是否存在(账号枚举) | 统一文案「用户名或密码错误」 |
| `BookController.list` 的 `size` 无上限 | 归一到 `[1, 100]` |
| `UserVo` 邮箱未脱敏,且脱敏函数有越界风险 | 邮箱按本地部分脱敏,修复 `substring` 越界 |

### 鉴权与业务

| 问题 | 修复 |
|---|---|
| ADMIN 列表查询 SQL 过滤反向(`AND role = #{role}`)导致后台列表恒为空 | 修正为 `AND role = 0`(只看普通用户) |
| `GET /api/users/{id}` 无权限校验,任意登录用户可查他人详情 | 增加「自己 / ADMIN / BOSS」校验 |
| 库存不足返回 404 语义错误 | 改为 409 CONFLICT |
| 登出无明确的客户端跳转信号 | 新增 `LogoutResponse{loggedOut, redirectUrl, logoutAt}` |
| `updateUserById` 无 `deleted_at IS NULL` 守卫,并发下可复活已软删用户 | UPDATE 增加守卫条件 |
| 订单 INSERT 不显式写时间字段,依赖 DB 默认值 | Service 层显式设置 `createdTime` / `updatedTime` |
| 调度器 `fixedRate` + 锁 TTL(50s)< 扫描周期(60s),多实例重复扫描 | 改为 `fixedDelay`,`TTL` 提升至 120s |
| 单次 `ZRANGEBYSCORE` 可能拉出十万级 member 阻塞 Redis | 改为分批拉取(200/批)+ 批次数上限 |

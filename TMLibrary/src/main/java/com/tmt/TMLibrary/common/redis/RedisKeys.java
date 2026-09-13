package com.tmt.TMLibrary.common.redis;

/**
 * <h1>RedisKeys — 全局 Redis Key 集中管理</h1>
 *
 * <h2>命名规范(已统一)</h2>
 * <pre>
 *   tmlibrary:{domain}:by{Accessor}:{keyId}:{feature}
 *   - domain   : auth | captcha | user | order | book | scheduler
 *   - Accessor : Id | Username | Isbn | Number | Uuid | Jti
 *               ↑ 明确标识"用什么 ID 寻址"
 *   - keyId    : 实际 ID 值(由占位符 %s / %d 填充)
 *   - feature  : info / status / data / inventory / blackList / code / idx
 *               ↑ 明确标识"这块数据是干嘛的"
 * </pre>
 *
 * <h2>为什么不做成 @ConfigurationProperties</h2>
 * Key 是结构性契约,改一个 key 等同"所有线上缓存作废",绝不应该放进 yml 让运维误改。
 * 这里用纯静态 final + 私有构造器,JVM 类加载即固化。
 *
 * <h2>历史坑位(已统一,见 {@code BUG-FIX-REDIS-KEYS})</h2>
 * <ul>
 *   <li>JWT 黑名单曾同时存在 {@code user:jwt:blackList:} 与 {@code auth:jwt:blackList:} — 现在统一为 {@link #jwtBlacklist(String)}</li>
 *   <li>用户状态曾同时存在 {@code user:users:status:{id}} 与 {@code user:{id}:status} — 现在统一为 {@link #userStatus(int)}</li>
 *   <li>用户 by-username 曾同时存在 {@code user:users:{u}} 与 {@code user:users:username:{u}} — 现在统一为 {@link #userByUsername(String)}</li>
 *   <li>图书信息曾放在 {@code user:books:{isbn}}(域错配) — 现在统一为 {@link #bookInfoByIsbn(String)}</li>
 * </ul>
 *
 * <h2>关于订单缓存</h2>
 * <p>订单详情与历史<b>不设 Redis 缓存</b>:此前的 {@code order:byNumber:*:data} Hash 与
 * {@code user:byId:*:orders:history:idx} ZSet 只写不读(所有查询走 DB),既浪费每次下单
 * 7 次 Redis 往返,又制造了"DB 与 Redis 可能漂移却无人发现"的隐患。
 * 订单一律以 DB 为准;Redis 只保留 scheduler 真正消费的 {@link #userPendingExpireIdx(int)}。</p>
 *
 * @author tmt
 */
public final class RedisKeys {

    private RedisKeys() {
        // utility class — 禁止实例化
    }

    // ============================================================
    // 1. Auth — JWT 黑名单(按 jti)
    // ============================================================
    /** 登出时写入,filter 校验。占位符 = jti */
    public static final String JWT_BLACKLIST = "tmlibrary:auth:byJti:%s:blackList";

    // ============================================================
    // 2. Captcha(按 uuid)
    // ============================================================
    /** 登录验证码,与 username 绑定,3 分钟 TTL。占位符 = uuid */
    public static final String CAPTCHA_LOGIN = "tmlibrary:captcha:byUuid:%s:code";

    // ============================================================
    // 3. User — 三种用户维度缓存
    // ============================================================
    /** by-username 缓存(login 热路径)。占位符 = username */
    public static final String USER_BY_USERNAME = "tmlibrary:user:byUsername:%s:info";

    /** by-id status 缓存(PurchaseService.checkUser 用)。占位符 = userId */
    public static final String USER_STATUS_BY_ID = "tmlibrary:user:byId:%d:status";

    // ============================================================
    // 4. Order — 订单数据 + 索引
    // ============================================================
    /** 用户待支付超时 ZSet 索引(score = expireTimeMillis)。占位符 = userId */
    public static final String USER_PENDING_EXPIRE_IDX = "tmlibrary:user:byId:%d:orders:pending:expire:idx";

    /** scheduler SCAN 用的 glob 模式 — 匹配以上所有 user 的 pending:expire idx */
    public static final String USER_PENDING_EXPIRE_IDX_PATTERN = "tmlibrary:user:byId:*:orders:pending:expire:idx";

    // ============================================================
    // 5. Book — 库存 Hash + 信息缓存
    // ============================================================
    /** 库存 Hash(bookId 维度,字段:stock/reserved/id/title/price)。占位符 = bookId */
    public static final String BOOK_INVENTORY = "tmlibrary:book:byId:%d:inventory";

    /** 图书详情缓存(isbn 维度,修复自原 user:books:{isbn} 错配域)。占位符 = isbn */
    public static final String BOOK_INFO_BY_ISBN = "tmlibrary:book:byIsbn:%s:info";

    /** 库存 Hash 的 SCAN 匹配模式 — 供对账任务遍历 */
    public static final String BOOK_INVENTORY_PATTERN = "tmlibrary:book:byId:*:inventory";

    /** 从库存 key 里提取 bookId 的正则(与 BOOK_INVENTORY 模板保持一致) */
    public static final java.util.regex.Pattern BOOK_INVENTORY_ID_EXTRACTOR =
            java.util.regex.Pattern.compile("^tmlibrary:book:byId:(\\d+):inventory$");

    // ============================================================
    // 6. Scheduler — 分布式锁
    // ============================================================
    /** 订单超时关单调度锁(SETNX 抢锁) — 固定 key,无占位符 */
    public static final String SCHED_LOCK_ORDER_EXPIRE = "tmlibrary:scheduler:lock:order-expire";

    /** 库存对账任务调度锁 */
    public static final String SCHED_LOCK_INVENTORY_RECONCILE = "tmlibrary:scheduler:lock:inventory-reconcile";

    // ============================================================
    // 构建方法 — 唯一推荐调用方式
    // ============================================================

    public static String jwtBlacklist(String jti) {
        return String.format(JWT_BLACKLIST, jti);
    }

    public static String captchaLogin(String uuid) {
        return String.format(CAPTCHA_LOGIN, uuid);
    }

    public static String userByUsername(String username) {
        return String.format(USER_BY_USERNAME, username);
    }

    public static String userStatus(int userId) {
        return String.format(USER_STATUS_BY_ID, userId);
    }

    public static String userPendingExpireIdx(int userId) {
        return String.format(USER_PENDING_EXPIRE_IDX, userId);
    }

    public static String bookInventory(int bookId) {
        return String.format(BOOK_INVENTORY, bookId);
    }

    public static String bookInfoByIsbn(String isbn) {
        return String.format(BOOK_INFO_BY_ISBN, isbn);
    }
}
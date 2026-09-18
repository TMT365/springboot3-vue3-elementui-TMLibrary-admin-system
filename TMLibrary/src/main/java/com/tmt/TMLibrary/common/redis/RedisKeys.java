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
    // 2. Captcha(按 username + uuid + 类型)
    // ============================================================
    /**
     * 登录验证码 —— 把 username 编进 key(2026-09 加):
     * <br>路径模式:{@code tmlibrary:captcha:login:{username}:{uuid}:code}
     * <br>占位符 = {username}, {uuid}
     * <br><b>为什么 key 里要带 username?</b>
     * <ul>
     *   <li>防止极端情况下(前端 bug / UUID 碰撞)用户 A 的 captcha 被用户 B 拿去登录</li>
     *   <li>支持按 username 清理:用户改名字后,可以用
     *       {@code KEYS tmlibrary:captcha:login:{oldUsername}:*:code} 一次性清掉旧名字的所有 captcha</li>
     *   <li>UUID v4 冲突概率极低(~1/2^122),但带 username 0 成本,且语义更清晰</li>
     * </ul>
     */
    public static final String CAPTCHA_LOGIN = "tmlibrary:captcha:login:%s:%s:code";

    /**
     * 注册验证码 —— 跟 login 路径对称。
     * <br>路径模式:{@code tmlibrary:captcha:register:{username}:{uuid}:code}
     * <br>占位符 = {username}, {uuid}
     */
    public static final String CAPTCHA_REGISTER = "tmlibrary:captcha:register:%s:%s:code";

    /**
     * 用于 {@code KEYS} 模式匹配 —— 清掉某用户的所有登录 captcha
     */
    public static final String CAPTCHA_LOGIN_PATTERN = "tmlibrary:captcha:login:%s:*:code";

    /**
     * 用于 {@code KEYS} 模式匹配 —— 清掉某用户的所有注册 captcha
     */
    public static final String CAPTCHA_REGISTER_PATTERN = "tmlibrary:captcha:register:%s:*:code";

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

    /**
     * 图书分类树 —— 整棵树一个 key(全表也就几十行,一次往返拿全,不做分片)。
     * <br>路径模式:{@code tmlibrary:book:byScope:categories:tree}
     * <br>无占位符(全站唯一的一棵树),跟 {@code SCHED_LOCK_*} 一样直接引用常量。
     * <br><b>失效</b>:写路径(新建分类、图书增删改引起的计数变化)**先删本 key 再写 MySQL**
     * —— 是 cache-aside 的"删除"而不是"更新",避免并发写把旧值又写回缓存。
     * <br><b>TTL 30 分钟只作兜底</b>:漏删(直接改库、异常路径)时靠它自愈。
     */
    public static final String BOOK_CATEGORY_TREE = "tmlibrary:book:byScope:categories:tree";

    /**
     * 反馈详情缓存 —— 同一份 FeedbackView 在不同人眼里**不一样**(内部备注过滤),
     * 所以 key 加上当前用户 id,免得"用户查 → 缓存 → 管理员查 → 拿到用户那份被过滤的"
     * 这种"缓存里其实是另一个人的视图"的串味问题。
     *
     * <p>路径模式:{@code tmlibrary:feedback:byId:{id}:byViewer:{userId}:view}</p>
     */
    public static final String FEEDBACK_DETAIL = "tmlibrary:feedback:byId:%d:byViewer:%d:view";

    /**
     * 反馈详情缓存(管理员视角,不过滤内部备注) —— 同一个 id 走不同的 key。
     * 有了这个 + 上面那个,普通用户和管理员查同一条反馈会走两条独立的缓存条目,
     * 互不污染。
     */
    public static final String FEEDBACK_DETAIL_ADMIN = "tmlibrary:feedback:byId:%d:byAdmin:view";

    /**
     * 失效反馈的**所有**详情缓存(所有视角)用的 SCAN 模式。
     *
     * <p>为什么需要它:详情缓存按 viewer 分了 N 条 key(每个看过这条反馈的用户一条),
     * 改状态时如果只删管理员那份,普通用户那份还留着旧状态 —— 用户要等最多 5 分钟
     * 才能看到"已解决"。实测确认过这个漏洞。</p>
     *
     * <p>用 SCAN 而不是 KEYS:KEYS 会阻塞 Redis 单线程(生产上是大忌),
     * SCAN 是游标式分批遍历。反馈详情 key 数量 = 看过的人数,量级可控。</p>
     */
    public static final String FEEDBACK_DETAIL_PATTERN = "tmlibrary:feedback:byId:%d:*";

    /**
     * 搜索候选词缓存 —— 每个 (关键词, 条数) 一个 key。
     * <br>路径模式:{@code tmlibrary:book:byScope:suggest:{q}:{limit}}
     * <br>占位符 = 关键词, 条数
     * <br><b>为什么加这一层</b>:候选词是"每敲一个字就打一次"的高频接口。
     * 前排 Redis 之后,热门前缀(「计」「计算」)完全不碰 ES ——
     * ES 的价值在分词和相关性排序,不在扛 QPS,让它做它擅长的事。
     * <br><b>TTL 5 分钟,不做主动失效</b>:候选词的"失效条件"是任意图书变动,
     * 逐个 key 追踪不现实(一个书名能出现在无数个前缀的结果里)。
     * 5 分钟是"新鲜度 vs 命中率"的折中;真要求秒级新鲜就得上
     * 版本号/代际 key(book 变更时把代际 +1,拼接进 key)。
     */
    public static final String BOOK_SUGGEST_CACHE = "tmlibrary:book:byScope:suggest:%s:%d";

    // ============================================================
    // 6. Stats — 仪表盘统计缓存
    // ============================================================
    /**
     * 仪表盘统计快照 —— 整个响应体一个 key(一次往返拿全量,不用 4 个 key)。
     * <br>路径模式:{@code tmlibrary:stats:byScope:dashboard-{days}d:overview}
     * <br>占位符 = {days}(统计窗口天数)
     * <br>TTL 5 分钟:统计非强一致场景,过期自动回源,不做主动失效
     */
    public static final String STATS_DASHBOARD = "tmlibrary:stats:byScope:dashboard-%dd:overview";

    // ============================================================
    // 7. Security — IP 风控(封禁标记 + 请求计数)
    // ============================================================
    /**
     * IP 封禁标记 —— 每个 /api/* 请求都要读一次,必须走 Redis(不能查 DB)。
     * <br>路径模式:{@code tmlibrary:sec:byIp:{ip}:ban}
     * <br>TTL = 封禁剩余时间,到期自动消失(不用定时任务解封)。
     * <br>占位符 = ip
     */
    public static final String IP_BAN = "tmlibrary:sec:byIp:%s:ban";

    /**
     * IP 请求计数(固定窗口)。窗口号 = 当前时间 / 窗口秒数,天然滚动。
     * <br>路径模式:{@code tmlibrary:sec:byIp:{ip}:rate:{window}}
     * <br>TTL = 窗口长度 × 2(留一倍余量,避免边界上 key 提前消失)
     * <br>占位符 = ip, window
     */
    public static final String IP_RATE = "tmlibrary:sec:byIp:%s:rate:%d";

    // ============================================================
    // 8. Scheduler — 分布式锁
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

    public static String captchaLogin(String username, String uuid) {
        return String.format(CAPTCHA_LOGIN, username, uuid);
    }

    public static String captchaRegister(String username, String uuid) {
        return String.format(CAPTCHA_REGISTER, username, uuid);
    }

    /** 该用户名下所有登录 captcha 的 KEY 匹配模式 */
    public static String captchaLoginPattern(String username) {
        return String.format(CAPTCHA_LOGIN_PATTERN, username);
    }

    /** 该用户名下所有注册 captcha 的 KEY 匹配模式 */
    public static String captchaRegisterPattern(String username) {
        return String.format(CAPTCHA_REGISTER_PATTERN, username);
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

    /**
     * 搜索候选词缓存 key。
     *
     * <p>关键词统一 <b>trim + 转小写</b> 后再拼 key:「Java」和「java」是同一个查询,
     * 不该各占一个 key。调用方已经 trim 过,这里做小写归一。</p>
     */
    public static String bookSuggestCache(String keyword, int limit) {
        return String.format(BOOK_SUGGEST_CACHE, keyword.toLowerCase(), limit);
    }

    public static String statsDashboard(int days) {
        return String.format(STATS_DASHBOARD, days);
    }

    /** 反馈详情缓存(普通用户视角,内部备注已过滤) */
    public static String feedbackDetail(Long feedbackId, Integer viewerId) {
        return String.format(FEEDBACK_DETAIL, feedbackId, viewerId);
    }

    /** 反馈详情缓存(管理员视角,不过滤内部备注) */
    public static String feedbackDetailAdmin(Long feedbackId) {
        return String.format(FEEDBACK_DETAIL_ADMIN, feedbackId);
    }

    /** 某条反馈所有视角的详情缓存 key 匹配模式 —— 供 SCAN 批量失效 */
    public static String feedbackDetailPattern(Long feedbackId) {
        return String.format(FEEDBACK_DETAIL_PATTERN, feedbackId);
    }

    public static String ipBan(String ip) {
        return String.format(IP_BAN, ip);
    }

    public static String ipRate(String ip, long window) {
        return String.format(IP_RATE, ip, window);
    }
}
package com.tmt.TMLibrary.service;

/**
 * <h1>BookInventoryService — Redis 库存原子操作门面</h1>
 *
 * <p>把库存变更收敛到一个服务，<strong>避免业务代码直接拼 Redis key 或写 Lua</strong>。</p>
 *
 * <h2>存储结构</h2>
 *
 * <p>每个 book 一个 Redis Hash：</p>
 *
 * <pre>
 * tmlibrary:book:{bookId}
 *   ├─ id       : bookId
 *   ├─ title    : 标题
 *   ├─ price    : 价格
 *   ├─ stock    : 可用库存（用户能买的）
 *   └─ reserved : 已预占库存（PENDING 订单占用中）
 * </pre>
 *
 * <h2>调用语义</h2>
 *
 * <table border="1">
 *   <tr><th>方法</th><th>触发时机</th><th>stock</th><th>reserved</th></tr>
 *   <tr><td>{@code tryReserve}</td><td>下单创建 PENDING</td><td>−=N</td><td>+=N</td></tr>
 *   <tr><td>{@code release}</td><td>取消/超时关单</td><td>+=N</td><td>−=N</td></tr>
 *   <tr><td>{@code confirm}</td><td>支付成功</td><td>不变</td><td>−=N</td></tr>
 *   <tr><td>{@code restoreReserved}</td><td>事务回滚补偿（confirm 的逆）</td><td>不变</td><td>+=N</td></tr>
 * </table>
 *
 * <h2>并发安全</h2>
 *
 * <p>所有库存变更走 Lua 脚本（Redis 单线程串行执行 = 天然原子），不需要额外的分布式锁。</p>
 *
 * <h2>不变式与原子性边界</h2>
 *
 * <p><b>不变式：{@code Redis(stock + reserved) == DB books.stock_quantity}。</b>
 * DB 侧只在付款时才真正扣减 {@code stock_quantity}，所以 {@code reserved}
 * 记录的是"还没付款但已经占住"的量。</p>
 *
 * <p><b>边界一：DB 是唯一权威，Redis 只是预占缓存。</b>
 * 付款时真正的扣减走 {@code UPDATE books SET stock_quantity = stock_quantity - ?}
 * 的条件更新（{@code stock_quantity >= ?}），外加数据库 CHECK 约束
 * {@code stock_quantity >= 0} —— <b>物理超卖在 DB 层不可能发生</b>。
 * Redis 提前扣的是"可用量"，不是"物理库存"。</p>
 *
 * <p><b>边界二：Redis 的 release 不改变 {@code stock + reserved} 之和。</b>
 * 这意味着对账任务（{@link #reconcile}，只校验总和）<b>发现不了</b>
 * "预占和订单配对错乱"这类漂移 —— 总量对得上，但某个订单占的量
 * 已经被别人还回池子了。所以 release/confirm 必须在 DB 事务内执行，
 * 并且事务回滚时由调用方做反向补偿。</p>
 *
 * <p><b>边界三：预热不能把 reserved 写死 0。</b>
 * Hash 缺失可能是 Redis 重启导致，此时 DB 里还有 PENDING 订单占着这本书；
 * reserved 归零就等于把在途预占全忘了，同一份库存会被卖两次。</p>
 *
 * @see com.tmt.TMLibrary.service.impl.BookInventoryServiceImpl
 * @see <a href="https://redis.io/docs/interact/programmability/eval-intro/">Redis Lua 文档</a>
 */
public interface BookInventoryService {

    /**
     * 尝试预占库存（下单时）。
     *
     * <p>返回 {@code false} 的两种情况：
     * <ol>
     *   <li>{@code bookId} 在 Redis 和 DB 里都不存在</li>
     *   <li>可用 {@code stock} 不足</li>
     * </ol>
     *
     * @param bookId   图书主键
     * @param quantity 要预占的数量（必须 {@code > 0}）
     * @return {@code true} = 预占成功；{@code false} = 失败
     */
    boolean tryReserve(Integer bookId, Integer quantity);

    /**
     * 释放已预占库存（取消订单 / 超时关单时）。
     *
     * <p>把 {@code reserved} 还回 {@code stock}。<b>幂等</b>：多次调用按调用次数累加。
     *
     * @param bookId   图书主键
     * @param quantity 要释放的数量（必须 {@code > 0}）
     */
    void release(Integer bookId, Integer quantity);

    /**
     * 确认扣减（支付成功时）。
     *
     * <p>{@code reserved} 减 N，<b>{@code stock} 不变</b>（在 {@code tryReserve} 时已减过）。
     *
     * @param bookId   图书主键
     * @param quantity 要确认的数量（必须 {@code > 0}）
     */
    void confirm(Integer bookId, Integer quantity);

    /**
     * 归还一笔已被 {@link #confirm} 确认掉的预占 —— {@code confirm} 的逆操作。
     *
     * <p>{@code reserved} 加 N，<b>{@code stock} 不变</b>（{@code confirm} 时也没动过 stock）。
     *
     * <p>用途：{@code confirm} 是在 DB 事务<b>内部</b>执行的（订单状态改成已支付、
     * DB 库存扣减要和它保持一致）。如果事务随后回滚（订单其实没支付成功），
     * Redis 侧这笔预占就得还回来，否则 Redis 的可用量会比真实值少，
     * 别的用户会看到"库存不足"。</p>
     *
     * @param bookId   图书主键
     * @param quantity 要归还的数量（必须 {@code > 0}）
     */
    void restoreReserved(Integer bookId, Integer quantity);

    /**
     * 管理端修改图书后,把 DB 库存同步到 Redis —— <b>保留在途预占</b>。
     *
     * <p>执行 {@code stock = DB.stock_quantity - reserved}，维持不变式
     * {@code Redis(stock + reserved) == DB.stock_quantity}。</p>
     *
     * <p>为什么不能直接删除 Hash：Hash 里的 {@code reserved} 记录了未支付订单的预占量，
     * 删除会让这些预占消失,导致真实客户到付款时被判"库存不足"。</p>
     *
     * <p>Hash 不存在时不做任何事(下次预热会从 DB 读最新值)。</p>
     *
     * @param bookId 图书主键
     */
    void syncStockFromDb(Integer bookId);

    /**
     * 库存对账 —— 检查并修复单个 book 的 Redis 库存。
     *
     * <p>校验不变式 {@code Redis(stock + reserved) == DB.stock_quantity}，
     * 不成立则以 DB 为准修正 {@code stock}(保留 {@code reserved})。</p>
     *
     * <p>用于兜住各类漂移：release 返回 -2、重复 confirm、人工误操作、
     * 以及历史上"删除 Hash 重建"留下的错位。</p>
     *
     * @param bookId 图书主键
     * @return {@code true} = 发现并修复了不一致；{@code false} = 一致或无需处理
     */
    boolean reconcile(Integer bookId);
}

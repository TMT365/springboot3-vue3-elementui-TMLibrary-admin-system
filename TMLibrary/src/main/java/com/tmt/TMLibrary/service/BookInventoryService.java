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
 * </table>
 *
 * <h2>并发安全</h2>
 *
 * <p>所有库存变更走 Lua 脚本（Redis 单线程串行执行 = 天然原子），不需要额外的分布式锁。</p>
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

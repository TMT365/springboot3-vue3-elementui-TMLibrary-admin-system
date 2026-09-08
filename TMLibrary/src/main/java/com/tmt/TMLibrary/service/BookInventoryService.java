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
}

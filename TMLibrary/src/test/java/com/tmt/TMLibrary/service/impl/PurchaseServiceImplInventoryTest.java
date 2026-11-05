package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.User.UserStatus;
import com.tmt.TMLibrary.common.metrics.InventoryMetrics;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.redis.UserStatusRedis;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderItem;
import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.mapper.OrderMapper;
import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.service.BookInventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存预占的「事务回滚补偿」单测。
 *
 * <h2>守的是什么</h2>
 * <p>Redis 不参与 DB 事务。{@code release}/{@code confirm} 必须在 DB 事务<b>内部</b>执行
 * (订单状态翻转要和库存变化同生共死),但事务回滚时 Redis 那一步不会自动撤销:</p>
 * <ul>
 *   <li>{@code release} 回滚后不撤销 → 该订单占的量已经被还回池子,别人能抢走 →
 *       这个订单付款时会被 DB 守卫拒绝("库存不足(并发售罄)")。<b>这个方向最危险。</b></li>
 *   <li>{@code confirm} 回滚后不撤销 → Redis 可用量比真实值少,别人看到"库存不足"。</li>
 * </ul>
 * <p>所以每次 release/confirm 都要登记一个逆操作,由 afterCompletion 钩子在回滚时执行:
 * 释放的逆 = 重新预占({@code tryReserve}),确认的逆 = 归还 reserved({@code restoreReserved})。</p>
 *
 * <p>纯 Mockito 单测,不连数据库/Redis。事务语义用
 * {@link TransactionSynchronizationManager#initSynchronization()} 手工模拟,
 * 真实环境的行为由 scripts/e2e-inventory-concurrency.sh 验证。</p>
 */
class PurchaseServiceImplInventoryTest {

    private static final Long ORDER_NUMBER = 23800224795463681L;
    private static final Integer USER_ID = 5;
    private static final Integer BOOK_ID = 3;
    private static final Integer QUANTITY = 2;

    private OrderMapper orderMapper;
    private BookMapper bookMapper;
    private UserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private BookInventoryService bookInventoryService;
    private InventoryMetrics metrics;
    private ZSetOperations<String, String> zSetOperations;
    private ValueOperations<String, String> valueOperations;

    private PurchaseServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        bookMapper = mock(BookMapper.class);
        userMapper = mock(UserMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        bookInventoryService = mock(BookInventoryService.class);
        metrics = mock(InventoryMetrics.class);
        zSetOperations = mock(ZSetOperations.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 用户状态缓存命中 → ACTIVE,让 checkUser 放行
        UserStatusRedis active = new UserStatusRedis();
        active.setId(USER_ID);
        active.setStatus(UserStatus.ACTIVE.getCode());
        when(valueOperations.get(anyString())).thenReturn("{\"status\":0}");
        try {
            when(objectMapper.readValue(anyString(), eq(UserStatusRedis.class))).thenReturn(active);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        service = new PurchaseServiceImpl(
                orderMapper, bookMapper, userMapper, redisTemplate, objectMapper,
                bookInventoryService, metrics);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------- 辅助 ----------

    private void mockPendingOrder() {
        Order order = new Order();
        order.setId(71);
        order.setOrderNumber(ORDER_NUMBER);
        order.setUserId(USER_ID);
        order.setOrderStatus(OrderStatus.PENDING.getCode());
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(order);

        OrderItem item = new OrderItem();
        item.setBookId(BOOK_ID);
        item.setQuantity(QUANTITY);
        when(orderMapper.selectOrderItemsByOrderNumber(ORDER_NUMBER)).thenReturn(List.of(item));
    }

    private void mockBookWithStock(int stock) {
        Book book = new Book();
        book.setId(BOOK_ID);
        book.setTitle("深入理解计算机系统");
        book.setPrice(new BigDecimal("139.00"));
        book.setStockQuantity(stock);
        when(bookMapper.selectById(BOOK_ID)).thenReturn(book);
    }

    /** 模拟事务回滚:只触发 afterCompletion(ROLLED_BACK),afterCommit 不会跑 */
    private void rollbackTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    /** 模拟事务提交 */
    private void commitTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> {
                    s.beforeCommit(true);
                    s.afterCommit();
                    s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                });
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("取消订单：事务回滚 → 释放掉的预占被重新预占回来")
    void cancelOrderRollbackRestoresReservation() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull()))
                .thenReturn(1);

        service.cancelOrder(ORDER_NUMBER, USER_ID);

        // 事务还没结束:只释放,还没补偿
        verify(bookInventoryService).release(BOOK_ID, QUANTITY);
        verify(bookInventoryService, never()).tryReserve(anyInt(), anyInt());

        rollbackTransaction();

        // 回滚 → 逆操作执行:重新预占
        verify(bookInventoryService).tryReserve(BOOK_ID, QUANTITY);
    }

    @Test
    @DisplayName("取消订单：事务提交 → 不做补偿(库存确实已经还回去了)")
    void cancelOrderCommitDoesNotCompensate() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull()))
                .thenReturn(1);

        service.cancelOrder(ORDER_NUMBER, USER_ID);
        commitTransaction();

        verify(bookInventoryService).release(BOOK_ID, QUANTITY);
        verify(bookInventoryService, never()).tryReserve(anyInt(), anyInt());
    }

    @Test
    @DisplayName("取消订单：没有事务 → 不登记补偿(没有回滚就不该撤销)")
    void cancelOrderWithoutTransactionDoesNotCompensate() {
        mockPendingOrder();
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull()))
                .thenReturn(1);

        service.cancelOrder(ORDER_NUMBER, USER_ID);

        verify(bookInventoryService).release(BOOK_ID, QUANTITY);
        verify(bookInventoryService, never()).tryReserve(anyInt(), anyInt());
    }

    @Test
    @DisplayName("支付订单：事务回滚 → confirm 掉的预占被归还(reserved 加回)")
    void payOrderRollbackRestoresReserved() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        mockBookWithStock(10);
        when(bookMapper.decrementStockIfEnough(BOOK_ID, QUANTITY)).thenReturn(1);
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.PAID.getCode()), eq("ALIPAY")))
                .thenReturn(1);

        service.payOrder(ORDER_NUMBER, USER_ID, "ALIPAY");

        verify(bookInventoryService).confirm(BOOK_ID, QUANTITY);
        verify(bookInventoryService, never()).restoreReserved(anyInt(), anyInt());

        rollbackTransaction();

        verify(bookInventoryService).restoreReserved(BOOK_ID, QUANTITY);
    }

    @Test
    @DisplayName("支付订单：事务提交 → 不做补偿")
    void payOrderCommitDoesNotCompensate() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        mockBookWithStock(10);
        when(bookMapper.decrementStockIfEnough(BOOK_ID, QUANTITY)).thenReturn(1);
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.PAID.getCode()), eq("ALIPAY")))
                .thenReturn(1);

        service.payOrder(ORDER_NUMBER, USER_ID, "ALIPAY");
        commitTransaction();

        verify(bookInventoryService).confirm(BOOK_ID, QUANTITY);
        verify(bookInventoryService, never()).restoreReserved(anyInt(), anyInt());
    }

    @Test
    @DisplayName("超时关单：事务回滚 → 释放掉的预占被重新预占回来")
    void expireRollbackRestoresReservation() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), isNull()))
                .thenReturn(1);

        service.cancelExpiredOrder(ORDER_NUMBER);

        verify(bookInventoryService).release(BOOK_ID, QUANTITY);
        rollbackTransaction();

        verify(bookInventoryService).tryReserve(BOOK_ID, QUANTITY);
    }

    @Test
    @DisplayName("回滚补偿失败不能把异常抛出去(事务已经结束了,抛也没用)")
    void compensationFailureIsSwallowed() {
        TransactionSynchronizationManager.initSynchronization();
        mockPendingOrder();
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull()))
                .thenReturn(1);
        // 补偿用的"重新预占"失败(库存被别人抢走了)
        when(bookInventoryService.tryReserve(BOOK_ID, QUANTITY)).thenReturn(false);

        service.cancelOrder(ORDER_NUMBER, USER_ID);

        // 不抛异常,只记日志
        rollbackTransaction();

        verify(bookInventoryService).tryReserve(BOOK_ID, QUANTITY);
    }

    @Test
    @DisplayName("多本书的订单：每一本都要登记各自的补偿")
    void multipleItemsEachGetCompensation() {
        TransactionSynchronizationManager.initSynchronization();
        Order order = new Order();
        order.setId(72);
        order.setOrderNumber(ORDER_NUMBER);
        order.setUserId(USER_ID);
        order.setOrderStatus(OrderStatus.PENDING.getCode());
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(order);

        OrderItem item1 = new OrderItem();
        item1.setBookId(3);
        item1.setQuantity(1);
        OrderItem item2 = new OrderItem();
        item2.setBookId(4);
        item2.setQuantity(2);
        when(orderMapper.selectOrderItemsByOrderNumber(ORDER_NUMBER)).thenReturn(List.of(item1, item2));
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull()))
                .thenReturn(1);

        service.cancelOrder(ORDER_NUMBER, USER_ID);
        rollbackTransaction();

        verify(bookInventoryService).tryReserve(3, 1);
        verify(bookInventoryService).tryReserve(4, 2);
    }
}

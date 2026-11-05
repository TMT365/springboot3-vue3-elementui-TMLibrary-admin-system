package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.metrics.InventoryMetrics;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderItem;
import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.mapper.OrderMapper;
import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.service.BookInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时自动取消的状态机单测。
 *
 * <p>守的是两件事：</p>
 * <ol>
 *   <li>超时关单必须落 <b>TIMEOUT(3)</b>，不是 CANCELLED(2)——以前写的是 CANCELLED，
 *       导致管理端订单列表的超时单显示成「已取消」，看板订单状态分布图里超时那格恒为 0。</li>
 *   <li>订单不存在时不能抛异常（scheduler 遇到幽灵索引成员要能安静跳过）。</li>
 * </ol>
 *
 * <p>纯 Mockito 单测，不连数据库；集成层面的行为（真正落库 + 清 Redis 索引 + 归还库存）
 * 由 scripts/e2e-timeout 脚本在本地跑真实环境验证。</p>
 */
class PurchaseServiceImplExpireTest {

    private static final Long ORDER_NUMBER = 23800224795463680L;
    private static final Integer USER_ID = 5;
    private static final Integer BOOK_ID = 3;

    private OrderMapper orderMapper;
    private BookMapper bookMapper;
    private UserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private BookInventoryService bookInventoryService;
    private InventoryMetrics metrics;
    private ZSetOperations<String, String> zSetOperations;

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
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service = new PurchaseServiceImpl(
                orderMapper, bookMapper, userMapper, redisTemplate, objectMapper,
                bookInventoryService, metrics);
    }

    private Order orderWithStatus(Integer status) {
        Order order = new Order();
        order.setId(61);
        order.setOrderNumber(ORDER_NUMBER);
        order.setUserId(USER_ID);
        order.setOrderStatus(status);
        return order;
    }

    @Test
    @DisplayName("超时关单：PENDING → TIMEOUT，归还库存并清掉超时索引")
    void pendingOrderBecomesTimeout() {
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER))
                .thenReturn(orderWithStatus(OrderStatus.PENDING.getCode()));
        OrderItem item = new OrderItem();
        item.setBookId(BOOK_ID);
        item.setQuantity(1);
        when(orderMapper.selectOrderItemsByOrderNumber(ORDER_NUMBER)).thenReturn(List.of(item));
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), eq(null)))
                .thenReturn(1);

        service.cancelExpiredOrder(ORDER_NUMBER);

        verify(orderMapper).updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), isNull());
        verify(bookInventoryService).release(BOOK_ID, 1);
        verify(zSetOperations).remove(
                eq(RedisKeys.userPendingExpireIdx(USER_ID)), eq(String.valueOf(ORDER_NUMBER)));
    }

    @Test
    @DisplayName("超时关单：绝不写 CANCELLED(2)")
    void pendingOrderNeverBecomesCancelled() {
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER))
                .thenReturn(orderWithStatus(OrderStatus.PENDING.getCode()));
        when(orderMapper.selectOrderItemsByOrderNumber(ORDER_NUMBER)).thenReturn(List.of());
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), eq(null)))
                .thenReturn(1);

        service.cancelExpiredOrder(ORDER_NUMBER);

        verify(orderMapper, never()).updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.CANCELLED.getCode()), isNull());
    }

    @Test
    @DisplayName("已支付的订单：不动状态、不动库存，只清超时索引")
    void paidOrderIsNotTouched() {
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER))
                .thenReturn(orderWithStatus(OrderStatus.PAID.getCode()));

        service.cancelExpiredOrder(ORDER_NUMBER);

        verify(orderMapper, never()).updateStatusByOrderNumberGuard(
                anyLong(), anyInt(), anyInt(), any());
        verify(bookInventoryService, never()).release(anyInt(), anyInt());
    }

    @Test
    @DisplayName("订单不存在（幽灵索引成员）：静默返回，不抛异常")
    void missingOrderDoesNotThrow() {
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER)).thenReturn(null);

        assertThatCode(() -> service.cancelExpiredOrder(ORDER_NUMBER)).doesNotThrowAnyException();

        verify(orderMapper, never()).updateStatusByOrderNumberGuard(
                anyLong(), anyInt(), anyInt(), any());
        verify(bookInventoryService, never()).release(anyInt(), anyInt());
    }

    @Test
    @DisplayName("状态被并发改掉：UPDATE 影响 0 行时只清索引，不重复归还库存")
    void concurrentStatusChangeDoesNotDoubleRelease() {
        when(orderMapper.selectOrderByOrderNumberForUpdate(ORDER_NUMBER))
                .thenReturn(orderWithStatus(OrderStatus.PENDING.getCode()));
        OrderItem item = new OrderItem();
        item.setBookId(BOOK_ID);
        item.setQuantity(2);
        when(orderMapper.selectOrderItemsByOrderNumber(ORDER_NUMBER)).thenReturn(List.of(item));
        when(orderMapper.updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), eq(null)))
                .thenReturn(0);

        service.cancelExpiredOrder(ORDER_NUMBER);

        verify(bookInventoryService, times(1)).release(BOOK_ID, 2);
        verify(orderMapper, times(1)).updateStatusByOrderNumberGuard(
                eq(ORDER_NUMBER), eq(OrderStatus.PENDING.getCode()), eq(OrderStatus.TIMEOUT.getCode()), eq(null));
    }
}

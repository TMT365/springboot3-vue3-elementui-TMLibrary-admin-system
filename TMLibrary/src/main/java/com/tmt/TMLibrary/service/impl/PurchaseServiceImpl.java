package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.utils.RandomExpirationTimeWithOffset;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderWithItems;
import com.tmt.TMLibrary.entity.OrderItem;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.mapper.OrderMapper;
import com.tmt.TMLibrary.service.BookInventoryService;
import com.tmt.TMLibrary.service.PurchaseService;

import lombok.RequiredArgsConstructor;

import com.tmt.TMLibrary.dto.request.PurchaseItemRequest;
import com.tmt.TMLibrary.dto.request.PurchaseRequest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.Result.ResultCode;

import java.util.concurrent.TimeUnit;

import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.common.User.UserStatus;
import com.tmt.TMLibrary.common.utils.Snowflake;
import com.tmt.TMLibrary.dto.redis.UserStatusRedis;
import tools.jackson.databind.ObjectMapper;

/**
 * <h1>PurchaseServiceImpl — 订单核心实现</h1>
 *
 * <h2>数据流（创建订单）</h2>
 *
 * <pre>
 * 1. checkUser（Redis 缓存的 UserStatus）
 * 2. 对每个 OrderItem：
 *    a. bookMapper.selectById(bookId)            — 拿价格（非锁读）
 *    b. bookInventoryService.tryReserve          — Lua 预占（异常时反向 release）
 * 3. orderMapper.insertOrder + insertOrderItems
 * 4. 写 Redis 双 key：
 *    - Hash: tmlibrary:order:{orderNumber}                            ← 订单数据
 *    - ZSet: tmlibrary:user:{userId}:orders:history:idx              ← 时间索引
 *    - ZSet: tmlibrary:user:{userId}:orders:pending:expire:idx       ← 超时索引
 * </pre>
 *
 * <h2>一致性策略</h2>
 *
 * <ul>
 *   <li><strong>DB 事务</strong>：{@code @Transactional(rollbackFor = Exception.class)} 覆盖订单插入</li>
 *   <li><strong>Redis 不在事务内</strong>：失败时手动反向 {@code release}（{@code reservedBookIds} 列表）</li>
 *   <li><strong>双写不一致</strong>：DB 成功 Redis 失败 → 库存泄漏（已预占但订单写不进 cache）
 *       极端情况下需要人工对账；当前用定时任务 + DB expireTime 兜底</li>
 * </ul>
 *
 * <h2>取消/支付时的库存语义</h2>
 *
 * <ul>
 *   <li>{@code cancelOrder}（用户主动）：{@code release} 库存，{@code status=CANCELLED}</li>
 *   <li>{@code cancelExpiredOrder}（系统超时）：同上但跳过用户状态校验</li>
 *   <li>{@code payOrder}（支付成功）：{@code confirm} 库存（reserved 减，stock 不变）</li>
 * </ul>
 *
 * @see PurchaseService
 */
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {
    private final OrderMapper orderMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;
    private final Snowflake snowflake = new Snowflake(1, 1);

    private final StringRedisTemplate stringRedisTemplate;

    // ==================== BEGIN CLAUDE CODE: Redis Key 重构 ====================
    // 旧：tmlibrary:user:users:status:{id}（多余的 users）
    // 新：tmlibrary:user:{id}:status（标准两段式）
    private static final String REDIS_USER_STATUS_PATH_TPL        = "tmlibrary:user:%s:status";
    // 历史订单索引 — ZSet member=orderNumber, score=createdTimeMillis
    private static final String REDIS_USER_HISTORY_ORDER_IDX_TPL  = "tmlibrary:user:%s:orders:history:idx";
    // 待支付超时索引 — ZSet member=orderNumber, score=expireTimeMillis
    // 配 @Scheduled 或 Redis keyspace notification 做超时关单
    private static final String REDIS_USER_PENDING_EXPIRE_IDX_TPL = "tmlibrary:user:%s:orders:pending:expire:idx";
    // 订单数据 — Hash per orderNumber，字段可独立更新（状态变更只改 status 字段）
    private static final String REDIS_ORDER_DATA_PREFIX           = "tmlibrary:order:";

    private static String userStatusPath(Integer userId)      { return String.format(REDIS_USER_STATUS_PATH_TPL, userId); }
    private static String historyIdx(Integer userId)          { return String.format(REDIS_USER_HISTORY_ORDER_IDX_TPL, userId); }
    private static String pendingExpireIdx(Integer userId)     { return String.format(REDIS_USER_PENDING_EXPIRE_IDX_TPL, userId); }
    private static String orderData(Long orderNumber)         { return REDIS_ORDER_DATA_PREFIX + orderNumber; }
    // ==================== END CLAUDE CODE: Redis Key 重构 ====================

    private static final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    // ==================== BEGIN CLAUDE CODE: 注入 BookInventoryService ====================
    private final BookInventoryService bookInventoryService;
    // ==================== END CLAUDE CODE: 注入 BookInventoryService ====================

    private boolean checkUser(Integer userId) {
        // ==================== BEGIN CLAUDE CODE: 改用新 key 模板 ====================
        String jsonString = stringRedisTemplate.opsForValue().get(userStatusPath(userId));
        // ==================== END CLAUDE CODE: 改用新 key 模板 ====================
        UserStatusRedis status;
        if (jsonString == null || jsonString.trim().isEmpty()) {
            User user = userMapper.selectUserById(userId);
            if (user == null) {
                stringRedisTemplate.opsForValue().set(userStatusPath(userId), "", RandomExpirationTimeWithOffset.get(3L, TimeUnit.MINUTES));
                return true;
            }
            status = UserStatusRedis.fromUser(user);
            String json = objectMapper.writeValueAsString(status);
            stringRedisTemplate.opsForValue().set(userStatusPath(userId), json, RandomExpirationTimeWithOffset.get(15L, TimeUnit.MINUTES));
        } else {
            status = objectMapper.readValue(jsonString, UserStatusRedis.class);
        }

        if (status == null)               return true;
        if (status.getStatus() == null)   return true;
        if (!status.getStatus().equals(UserStatus.ACTIVE.getCode())) return true;
        return status.getDeletedAt() != null;
    }

    private void checkOrder(Order order, Long orderNumber, Integer currentUserId) {
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found: " + orderNumber);
        }
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "User is not authorized to cancel this order");
        }
        if (order.getOrderStatus().equals(OrderStatus.CANCELLED.getCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "Order is already cancelled: " + orderNumber);
        }
        if (order.getOrderStatus().equals(OrderStatus.PAID.getCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "Order is already paid and cannot be cancelled: " + orderNumber);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createOrder(PurchaseRequest purchaseRequest, Integer currentUserId) {

        if (checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to create order");
        }

        // ==================== BEGIN CLAUDE CODE: 库存预占用 tryReserve + 失败回滚 ====================
        // 先做 DB 读取拿价格（轻量非锁读），再走 Redis Lua 预占
        // 顺序保证：DB 失败时 Redis 没碰过；Redis 失败时 DB 已读但未改，回滚靠 @Transactional
        // 中途抛异常的 Redis 残留：用 reservedBookIds 列表在 catch 里反向 release
        List<Integer> reservedBookIds = new ArrayList<>();
        List<Integer> reservedQtys    = new ArrayList<>();
        // ==================== END CLAUDE CODE: 库存预占用 tryReserve + 失败回滚 ====================

        List<OrderItem> orderItems = new ArrayList<>();
        Order order = new Order();
        order.setUserId(currentUserId);
        order.setOrderNumber(snowflake.nextId());

        BigDecimal totalAmount = BigDecimal.ZERO;
        try {
            for (PurchaseItemRequest item : purchaseRequest.getItems()) {
                if (item.getQuantity() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "Quantity must be greater than 0 for bookId: " + item.getBookId());
                }
                if (item.getBookId() == null || item.getBookId() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "BookId cannot be null");
                }

                // ==================== BEGIN CLAUDE CODE: 替换 selectByIdForUpdate + atomicDecrementStock ====================
                // 1) 非锁读 book 拿 price（写 OrderItem 需要）
                Book book = bookMapper.selectById(item.getBookId());
                if (book == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "Book not found: " + item.getBookId());
                }
                // 2) Redis Lua 原子预占 — 替代原 DB 悲观锁路径
                if (!bookInventoryService.tryReserve(item.getBookId(), item.getQuantity())) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "Insufficient stock for bookId: " + item.getBookId());
                }
                reservedBookIds.add(item.getBookId());
                reservedQtys.add(item.getQuantity());
                // ==================== END CLAUDE CODE: 替换 selectByIdForUpdate + atomicDecrementStock ====================

                OrderItem orderItem = new OrderItem();
                try {
                    orderItem.setPrice(book.getPrice());
                    orderItem.setBookId(item.getBookId());
                    orderItem.setQuantity(item.getQuantity());
                    orderItems.add(orderItem);
                    totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                } catch (ArithmeticException e) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "Error calculating total amount: " + e.getMessage());
                } catch (NullPointerException e) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "Book price is null for bookId: " + item.getBookId());
                }
            }
        } catch (RuntimeException e) {
            // ==================== BEGIN CLAUDE CODE: 失败回滚已预占的 Redis 库存 ====================
            for (int i = 0; i < reservedBookIds.size(); i++) {
                try {
                    bookInventoryService.release(reservedBookIds.get(i), reservedQtys.get(i));
                } catch (Exception ex) {
                    // release 失败只能记日志（兜底兜不住，需要人工对账）
                    System.err.println("[CLAUDE] failed to release redis reservation: bookId="
                        + reservedBookIds.get(i) + ", qty=" + reservedQtys.get(i) + ", err=" + ex.getMessage());
                }
            }
            // ==================== END CLAUDE CODE: 失败回滚已预占的 Redis 库存 ====================
            throw e;
        }

        order.setTotalAmount(totalAmount);
        order.setOrderStatus(OrderStatus.PENDING.getCode());

        // ==================== BEGIN CLAUDE CODE: set expireTime 到实体 + 用于 Redis score ====================
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);
        order.setExpireTime(expireTime);
        // ==================== END CLAUDE CODE: set expireTime 到实体 + 用于 Redis score ====================

        orderMapper.insertOrder(order);

        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderMapper.insertOrderItem(orderItem);
        }

        // ==================== BEGIN CLAUDE CODE: 写 Redis 双 key + 待支付超时索引 ====================
        // 1. Hash 存订单数据（字段独立，后续改状态只动 status 字段，不重读 DB）
        String orderKey = orderData(order.getOrderNumber());
        stringRedisTemplate.opsForHash().put(orderKey, "id",          String.valueOf(order.getId()));
        stringRedisTemplate.opsForHash().put(orderKey, "userId",      String.valueOf(order.getUserId()));
        stringRedisTemplate.opsForHash().put(orderKey, "totalAmount", totalAmount.toPlainString());
        stringRedisTemplate.opsForHash().put(orderKey, "status",      String.valueOf(OrderStatus.PENDING.getCode()));
        stringRedisTemplate.opsForHash().put(orderKey, "expireTime",  expireTime.toString());

        // 2. 历史订单索引（ZSet — 时间倒序排）
        String historyKey = historyIdx(currentUserId);
        long createdMillis = LocalDateTime.now().atZone(zoneId).toInstant().toEpochMilli();
        stringRedisTemplate.opsForZSet().add(historyKey, String.valueOf(order.getOrderNumber()), createdMillis);

        // 3. 待支付超时索引（ZSet — 按 expireTime 排，配定时任务或 keyspace notification 关单）
        //    修原 179 行 bug：旧代码 key 为 per-orderNumber，每笔订单一个 ZSet 无意义；改为 per-user
        String expireKey = pendingExpireIdx(currentUserId);
        long expireMillis = expireTime.atZone(zoneId).toInstant().toEpochMilli();
        stringRedisTemplate.opsForZSet().add(expireKey, String.valueOf(order.getOrderNumber()), expireMillis);
        // ==================== END CLAUDE CODE: 写 Redis 双 key + 待支付超时索引 ====================

        return 1;
    }

    @Override
    public Order getOrderByOrderNumber(Long orderNumber) {
        return orderMapper.selectOrderByOrderNumber(orderNumber);
    }

    @Override
    public OrderWithItems getOrderWithItemsByOrderNumber(Long orderNumber) {
        return  orderMapper.selectOrderWithItemsByOrderNumber(orderNumber);
    }

    @Override
    public List<OrderWithItems> listOrdersByUserId(Integer currentUserId) {
        return orderMapper.selectOrderWithItemsByUserId(currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(Long orderNumber, Integer currentUserId) {

        if (checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to cancel order");
        }
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        checkOrder(order, orderNumber, currentUserId);
        order.setOrderStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateStatusByOrderNumber(orderNumber, OrderStatus.CANCELLED.getCode());

        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Order items not found for OrderNumber: " + orderNumber);
        }

        // ==================== BEGIN CLAUDE CODE: 释放 Redis 预占 + 更新订单状态字段 ====================
        for (OrderItem orderItem : orderItems) {
            bookInventoryService.release(orderItem.getBookId(), orderItem.getQuantity());
        }
        // 只动 status 字段，不重写整个订单（Hash 的好处）
        stringRedisTemplate.opsForHash().put(orderData(orderNumber), "status", String.valueOf(OrderStatus.CANCELLED.getCode()));
        // ==================== END CLAUDE CODE: 释放 Redis 预占 + 更新订单状态字段 ====================

        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int payOrder(Long orderNumber, Integer currentUserId, String paymentMethod) {

        // 支付方式还未做

        if (checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to pay order");
        }
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        checkOrder(order, orderNumber, currentUserId);
        order.setOrderStatus(OrderStatus.PAID.getCode());
        orderMapper.updateStatusByOrderNumber(orderNumber, OrderStatus.PAID.getCode());

        // ==================== BEGIN CLAUDE CODE: confirm Redis 预占 + 更新订单状态 ====================
        // paymentMethod 仍未接支付网关，这里只翻状态
        // 真支付网关回调时再扩展：验签 → 调用 payOrder
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                bookInventoryService.confirm(orderItem.getBookId(), orderItem.getQuantity());
            }
        }
        stringRedisTemplate.opsForHash().put(orderData(orderNumber), "status", String.valueOf(OrderStatus.PAID.getCode()));
        // ==================== END CLAUDE CODE: confirm Redis 预占 + 更新订单状态 ====================

        return 1;
    }

    // ==================== BEGIN CLAUDE CODE: 系统主动关单 ====================
    /**
     * 系统主动关单（定时任务调用）— 不校验用户状态，按订单号直接关。
     * <br>幂等：状态非 PENDING 时直接清理 expire idx 不报错。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrder(Long orderNumber) {
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        if (order == null) {
            // 订单已不存在 — 无需处理
            return;
        }

        Integer currentStatus = order.getOrderStatus();
        // 已支付或已取消 — 仅清理 expire idx，跳过
        if (currentStatus.equals(OrderStatus.PAID.getCode())
                || currentStatus.equals(OrderStatus.CANCELLED.getCode())) {
            stringRedisTemplate.opsForZSet().remove(
                pendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)
            );
            return;
        }
        // 非 PENDING 状态（理论上不该出现，但防御一下） — 直接返回
        if (!currentStatus.equals(OrderStatus.PENDING.getCode())) {
            return;
        }

        // 关单：DB 状态翻转
        orderMapper.updateStatusByOrderNumber(orderNumber, OrderStatus.CANCELLED.getCode());

        // 释放库存预占
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                bookInventoryService.release(orderItem.getBookId(), orderItem.getQuantity());
            }
        }

        // 更新 Redis Hash + 从 expire idx 移除（避免下次又被扫到）
        stringRedisTemplate.opsForHash().put(
            orderData(orderNumber),
            "status",
            String.valueOf(OrderStatus.CANCELLED.getCode())
        );
        stringRedisTemplate.opsForZSet().remove(
            pendingExpireIdx(order.getUserId()),
            String.valueOf(orderNumber)
        );
    }
    // ==================== END CLAUDE CODE: 系统主动关单 ====================
}

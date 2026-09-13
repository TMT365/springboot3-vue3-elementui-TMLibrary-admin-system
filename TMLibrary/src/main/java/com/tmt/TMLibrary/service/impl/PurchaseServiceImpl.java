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
import lombok.extern.slf4j.Slf4j;

import com.tmt.TMLibrary.dto.request.PurchaseItemRequest;
import com.tmt.TMLibrary.dto.request.PurchaseRequest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.tmt.TMLibrary.exception.AuthException;

import java.math.BigDecimal;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.redis.RedisKeys;

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
@Slf4j
public class PurchaseServiceImpl implements PurchaseService {
    private final OrderMapper orderMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;
    private final Snowflake snowflake = new Snowflake(1, 1);

    private final StringRedisTemplate stringRedisTemplate;

    // 旧：tmlibrary:user:users:status:{id}（多余的 users）
    // 新：tmlibrary:user:{id}:status（标准两段式）
    // Redis key 由 RedisKeys 统一管理,本类不再硬编码

    private static final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper objectMapper;

    /** 负缓存哨兵 — 防止空串写入导致 readValue("") 抛异常 */
    private static final String NEGATIVE_SENTINEL = "{\"__negative__\":true}";

    /** 订单未支付超时时长(分钟)— 与 OrderExpireScheduler 的扫描周期配合 */
    private static final int ORDER_EXPIRE_MINUTES = 30;

    private final BookInventoryService bookInventoryService;

    /**
     * 检查用户是否可以下单/取消/支付。
     *
     * @return true = 用户不可用(应拒绝),false = 用户可用
     */
    private boolean checkUser(Integer userId) {
        String jsonString = stringRedisTemplate.opsForValue().get(RedisKeys.userStatus(userId));

        // 负缓存命中 — 该 userId 在 TTL 内确认不存在,直接判不可用(不再打 DB,防穿透)
        if (NEGATIVE_SENTINEL.equals(jsonString)) {
            return true;
        }

        UserStatusRedis status;
        if (jsonString == null || jsonString.trim().isEmpty()) {
            User user = userMapper.selectUserById(userId);
            if (user == null) {
                // 用户不存在 → 写负 sentinel(非空串,避免下次 readValue("") 抛异常)
                stringRedisTemplate.opsForValue().set(
                    RedisKeys.userStatus(userId), NEGATIVE_SENTINEL,
                    RandomExpirationTimeWithOffset.get(3L, TimeUnit.MINUTES));
                return true;
            }
            status = UserStatusRedis.fromUser(user);
            String json = objectMapper.writeValueAsString(status);
            stringRedisTemplate.opsForValue().set(
                RedisKeys.userStatus(userId), json,
                RandomExpirationTimeWithOffset.get(15L, TimeUnit.MINUTES));
        } else {
            try {
                status = objectMapper.readValue(jsonString, UserStatusRedis.class);
            } catch (Exception e) {
                // 缓存内容损坏 — 清除后按"不可用"处理,下次请求重新回填
                log.warn("corrupt UserStatusRedis cache for userId={}, evicting", userId, e);
                stringRedisTemplate.delete(RedisKeys.userStatus(userId));
                return true;
            }
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

        // reservedBookIds/reservedQtys 在 try 外声明 — catch 块需要访问
        // @Transactional 只能回滚 DB,Redis 写不在事务里 — 必须显式补偿
        List<Integer> reservedBookIds = new ArrayList<>();
        List<Integer> reservedQtys    = new ArrayList<>();

        try {
            if (checkUser(currentUserId)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to create order");
            }
            if (purchaseRequest.getItems() == null || purchaseRequest.getItems().isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "items cannot be empty");
            }

            List<OrderItem> orderItems = new ArrayList<>();
            Order order = new Order();
            order.setUserId(currentUserId);
            order.setOrderNumber(snowflake.nextId());

            BigDecimal totalAmount = BigDecimal.ZERO;

            // Phase 1: 预占库存(Redis Lua)
            for (PurchaseItemRequest item : purchaseRequest.getItems()) {
                if (item.getQuantity() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "Quantity must be greater than 0 for bookId: " + item.getBookId());
                }
                if (item.getBookId() == null || item.getBookId() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "BookId cannot be null");
                }

                // 1) 非锁读 book 拿 price（写 OrderItem 需要）
                Book book = bookMapper.selectById(item.getBookId());
                if (book == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "Book not found: " + item.getBookId());
                }
                // 2) Redis Lua 原子预占 — 替代原 DB 悲观锁路径
                if (!bookInventoryService.tryReserve(item.getBookId(), item.getQuantity())) {
                    throw new BusinessException(ResultCode.CONFLICT, "Insufficient stock for bookId: " + item.getBookId());
                }
                reservedBookIds.add(item.getBookId());
                reservedQtys.add(item.getQuantity());

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

            // Phase 2: DB 写入(事务内,@Transactional 失败回滚)
            order.setTotalAmount(totalAmount);
            order.setOrderStatus(OrderStatus.PENDING.getCode());
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expireTime = now.plusMinutes(ORDER_EXPIRE_MINUTES);
            order.setExpireTime(expireTime);
            // 显式写时间字段,不依赖 DB DEFAULT CURRENT_TIMESTAMP
            order.setCreatedTime(now);
            order.setUpdatedTime(now);
            orderMapper.insertOrder(order);
            for (OrderItem oi : orderItems) {
                oi.setOrderId(order.getId());
                oi.setCreatedTime(now);
                orderMapper.insertOrderItem(oi);
            }

            // Phase 3: Redis 写入(Hash + 2 ZSet) — 任何异常 → 下方 catch 反向 release
            String orderKey = RedisKeys.orderData(order.getOrderNumber());
            stringRedisTemplate.opsForHash().put(orderKey, "id",          String.valueOf(order.getId()));
            stringRedisTemplate.opsForHash().put(orderKey, "userId",      String.valueOf(order.getUserId()));
            stringRedisTemplate.opsForHash().put(orderKey, "totalAmount", totalAmount.toPlainString());
            stringRedisTemplate.opsForHash().put(orderKey, "status",      String.valueOf(OrderStatus.PENDING.getCode()));
            stringRedisTemplate.opsForHash().put(orderKey, "expireTime",  expireTime.toString());

            String historyKey = RedisKeys.userHistoryIdx(currentUserId);
            long createdMillis = LocalDateTime.now().atZone(zoneId).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(historyKey, String.valueOf(order.getOrderNumber()), createdMillis);

            String expireKey = RedisKeys.userPendingExpireIdx(currentUserId);
            long expireMillis = expireTime.atZone(zoneId).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(expireKey, String.valueOf(order.getOrderNumber()), expireMillis);

            return order.getId() != null ? order.getId() : 1;

        } catch (RuntimeException e) {
            // 统一补偿:对所有已成功 Lua 预占的 bookId 释放库存
            for (int i = 0; i < reservedBookIds.size(); i++) {
                try {
                    bookInventoryService.release(reservedBookIds.get(i), reservedQtys.get(i));
                    log.info("compensated reservation: bookId={}, qty={}",
                        reservedBookIds.get(i), reservedQtys.get(i));
                } catch (Exception releaseEx) {
                    // 关键告警:补偿失败 → Redis stock 已多扣,无更上层兜底,必须人工对账
                    log.error("CRITICAL: failed to compensate reservation bookId={}, qty={}, originalErr={}, releaseErr={}",
                        reservedBookIds.get(i), reservedQtys.get(i), e.getMessage(), releaseEx.getMessage());
                }
            }
            throw e;
        }
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
            throw new AuthException(ResultCode.UNAUTHORIZED, "User is not authorized to cancel order");
        }
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        checkOrder(order, orderNumber, currentUserId);

        // 状态守卫 UPDATE — 只有 PENDING 才能被用户主动取消
        int rows = orderMapper.updateStatusByOrderNumberGuard(
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.CANCELLED.getCode());
        if (rows == 0) {
            // 已经被 cancel/expire/pay 抢先 — checkOrder 已挡住 PAID/CANCELLED,这里防御性兜底
            throw new BusinessException(ResultCode.CONFLICT, "Order already processed: " + orderNumber);
        }

        // 释放 Redis 预占 — 任一 item 失败 → 整笔失败,让用户重试(避免部分释放)
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Order items not found for OrderNumber: " + orderNumber);
        }

        for (OrderItem orderItem : orderItems) {
            try {
                bookInventoryService.release(orderItem.getBookId(), orderItem.getQuantity());
            } catch (Exception releaseEx) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "释放库存失败:bookId=" + orderItem.getBookId() + ",err=" + releaseEx.getMessage(), releaseEx);
            }
        }

        // afterCommit 钩子:Hash 状态翻转 + 主动 ZREM expire idx(scheduler 不必再扫)
        registerRedisWriteAfterCommit(() -> {
            stringRedisTemplate.opsForHash().put(RedisKeys.orderData(orderNumber),
                "status", String.valueOf(OrderStatus.CANCELLED.getCode()));
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(currentUserId),
                String.valueOf(orderNumber));
        });

        return order.getId() != null ? order.getId() : 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int payOrder(Long orderNumber, Integer currentUserId, String paymentMethod) {

        // 支付方式还未做(预留字段,接网关时验签后再调本方法)

        if (checkUser(currentUserId)) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "User is not authorized to pay order");
        }
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        checkOrder(order, orderNumber, currentUserId);

        // ===== 单一权威字段:DB books.stock_quantity = 未售出的物理库存 =====
        // 在用户付款时才原子扣减 DB(下单阶段 Redis 预占就够了,DB 不动)
        // Redis 仅作预占缓存 + 热点加速,真实库存以 DB 为准
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                int rows = bookMapper.decrementStockIfEnough(orderItem.getBookId(), orderItem.getQuantity());
                if (rows == 0) {
                    // 库存不足 — DB 拒绝扣减,抛错让 @Transactional 回滚整个付款
                    // 订单保持 PENDING,调用方(支付网关回调)决定是否取消订单 + 退款
                    throw new BusinessException(ResultCode.CONFLICT,
                        "库存不足,bookId=" + orderItem.getBookId()
                            + ",qty=" + orderItem.getQuantity()
                            + "(并发售罄或管理员未及时补货)");
                }
            }
        }

        // DB 扣减成功 → 翻状态(状态守卫:只有 PENDING 才能转 PAID)
        int paidRows = orderMapper.updateStatusByOrderNumberGuard(
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.PAID.getCode());
        if (paidRows == 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                "Order status changed concurrently: " + orderNumber);
        }
        order.setOrderStatus(OrderStatus.PAID.getCode());

        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                bookInventoryService.confirm(orderItem.getBookId(), orderItem.getQuantity());
            }
        }
        // Hash 状态翻转(afterCommit 钩子避免 DB 回滚但 Redis 已写)
        registerRedisWriteAfterCommit(() -> {
            stringRedisTemplate.opsForHash().put(RedisKeys.orderData(orderNumber),
                "status", String.valueOf(OrderStatus.PAID.getCode()));
            // 主动从 pending expire idx 移除,scheduler 不必再扫
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(currentUserId),
                String.valueOf(orderNumber));
        });

        return order.getId() != null ? order.getId() : 1;
    }

    /**
     * 系统主动关单(定时任务调用)— 不校验用户状态,按订单号直接关。
     * <br>幂等:状态非 PENDING 时直接清理 expire idx 不报错。
     *
     * <h2>修复后的顺序(R-3)</h2>
     * <pre>
     *   1. SELECT FOR UPDATE 拿行锁
     *   2. 状态守卫:非 PENDING → 直接 ZREM expire idx 兜底,返回
     *   3. Lua release(库存归还)   ← 先于 DB UPDATE
     *      - 若失败:DB 保持 PENDING,scheduler 下次重试
     *   4. UPDATE orders SET status=CANCELLED WHERE order_number=? AND order_status='PENDING'
     *      ← 状态守卫的原子 SQL,即使 FOR UPDATE 被优化掉也安全
     *   5. @Transactional commit
     *   6. afterCommit 钩子:Hash.put + ZREM
     * </pre>
     * 崩溃窗口分析:3 与 4 之间崩 → DB 仍 PENDING,Redis stock 已释放,
     * scheduler 下次扫到该订单 → 重走 3-4-5-6 → release 二次释放(Lua -2 静默)→ UPDATE 0 行 → 早返回。
     * 永远不会泄漏库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrder(Long orderNumber) {
        Order order = orderMapper.selectOrderByOrderNumberForUpdate(orderNumber);
        if (order == null) {
            return;
        }

        Integer currentStatus = order.getOrderStatus();

        // 非 PENDING(已 PAID 或已 CANCELLED)— 仅清理 expire idx,跳过主流程
        if (!currentStatus.equals(OrderStatus.PENDING.getCode())) {
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)
            );
            return;
        }

        // 步骤 3:先释放库存(Lua)— 若失败,DB 保持 PENDING,scheduler 下次重试
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderNumber(orderNumber);
        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                bookInventoryService.release(orderItem.getBookId(), orderItem.getQuantity());
            }
        }

        // 步骤 4:状态守卫的 UPDATE — 只有 PENDING 才能转 CANCELLED
        int rows = orderMapper.updateStatusByOrderNumberGuard(
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.CANCELLED.getCode());
        if (rows == 0) {
            // 别人抢先改了状态(Paid 或 Cancelled)— 我们的 release 多减一次,但 Lua -2 静默处理
            log.warn("cancelExpiredOrder: order {} status changed concurrently, release may double-count", orderNumber);
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)
            );
            return;
        }

        // 步骤 6:afterCommit 钩子 — Hash.put + ZREM
        registerRedisWriteAfterCommit(() -> {
            stringRedisTemplate.opsForHash().put(
                RedisKeys.orderData(orderNumber),
                "status",
                String.valueOf(OrderStatus.CANCELLED.getCode())
            );
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)
            );
        });
    }

    /**
     * 注册 Redis 写入到当前事务的 afterCommit 钩子 — 避免 DB 回滚但 Redis 已写脏。
     * <p>如果当前没有事务(例如单元测试场景),则同步立即执行。</p>
     */
    private void registerRedisWriteAfterCommit(Runnable redisOp) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        redisOp.run();
                    } catch (Exception e) {
                        log.error("Redis write failed after DB commit, manual reconciliation needed", e);
                    }
                }
            });
        } else {
            redisOp.run();
        }
    }
}

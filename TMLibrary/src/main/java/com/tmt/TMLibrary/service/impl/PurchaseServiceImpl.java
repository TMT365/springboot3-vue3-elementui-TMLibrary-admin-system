package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Result.PageResult;
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
import com.tmt.TMLibrary.exception.OrderAutoCancelledException;

import java.math.BigDecimal;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.Order.PaymentMethod;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.metrics.InventoryMetrics;
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
 * <h2>并发模型(库存双轨:DB 权威 + Redis 预占)</h2>
 *
 * <p><b>DB 是唯一权威,Redis 只是预占缓存</b>:</p>
 * <ul>
 *   <li>下单：{@code tryReserve} 在 Redis 里扣"可用量"(Lua 原子),DB 库存不动</li>
 *   <li>付款：{@code decrementStockIfEnough} 条件 UPDATE 扣 DB 物理库存
 *       —— {@code WHERE stock_quantity >= qty} + DB 的 CHECK 约束,
 *       <b>物理超卖在 DB 层不可能发生</b></li>
 *   <li>取消/超时：{@code release} 把预占还回 Redis</li>
 * </ul>
 *
 * <p><b>三个必须记住的不变式</b>(细节见
 * {@link com.tmt.TMLibrary.service.BookInventoryService} 的类注释):</p>
 * <ol>
 *   <li>{@code Redis(stock + reserved) == DB books.stock_quantity}</li>
 *   <li>Redis 的 {@code release} <b>不改变</b>上式的和 —— 所以"总量对得上"的
 *       对账任务<b>发现不了</b>"预占和订单配对错乱"。这就是所有 Redis 库存操作
 *       都要在 DB 事务内做、并登记回滚补偿的原因。</li>
 *   <li>Redis 预热时 {@code reserved} 必须来自 DB 的 PENDING 实占量,不能写死 0</li>
 * </ol>
 *
 * <p><b>回滚补偿</b>({@code registerRedisCompensation}):Redis 不参与 DB 事务,
 * 事务回滚时 Redis 那一步不会自动撤销。所以每次 {@code release}/{@code confirm}
 * 都会登记一个逆操作(释放的逆 = 重新预占,确认的逆 = 归还 reserved),
 * 由 {@code afterCompletion} 钩子在回滚时执行。</p>
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
    /**
     * 订单号发号器。
     * <p>workerId / datacenterId 从配置读取 — <b>多实例部署时必须为每个实例分配不同的值</b>,
     * 否则同一毫秒内的相同序列会生成重复的 orderNumber(有唯一索引则插入失败,无则数据错乱)。</p>
     * <p>配置项:{@code app.snowflake.worker-id}(0-31)、{@code app.snowflake.datacenter-id}(0-31)。</p>
     */
    private Snowflake snowflake;

    @org.springframework.beans.factory.annotation.Value("${app.snowflake.worker-id:1}")
    private long snowflakeWorkerId;

    @org.springframework.beans.factory.annotation.Value("${app.snowflake.datacenter-id:1}")
    private long snowflakeDatacenterId;

    @jakarta.annotation.PostConstruct
    void initSnowflake() {
        this.snowflake = new Snowflake(snowflakeWorkerId, snowflakeDatacenterId);
        log.info("Snowflake 初始化完成 workerId={}, datacenterId={} — "
            + "多实例部署请确保各实例取值不同", snowflakeWorkerId, snowflakeDatacenterId);
    }

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
    private final InventoryMetrics metrics;

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
        if (order.getOrderStatus().equals(OrderStatus.TIMEOUT.getCode())) {
            // 超时关单是终态 —— 用户再来取消/支付时给一句人话,而不是笼统的"已取消"
            throw new BusinessException(ResultCode.CONFLICT, "Order has timed out and been closed: " + orderNumber);
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

            // Phase 0: 校验 + 合并重复 bookId
            // 同一本书出现多次([{id:1,qty:2},{id:1,qty:3}])会合并成一条 id:1 qty:5,
            // 避免生成多条相同 order_item、以及重复预占同一本书
            LinkedHashMap<Integer, Integer> mergedItems = new LinkedHashMap<>();
            for (PurchaseItemRequest item : purchaseRequest.getItems()) {
                if (item.getBookId() == null || item.getBookId() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "BookId cannot be null or non-positive");
                }
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "Quantity must be greater than 0 for bookId: " + item.getBookId());
                }
                final int currentBookId = item.getBookId();
                mergedItems.merge(currentBookId, item.getQuantity(), (a, b) -> {
                    try {
                        return Math.addExact(a, b);
                    } catch (ArithmeticException overflow) {
                        throw new BusinessException(ResultCode.BAD_REQUEST,
                            "Quantity overflow for bookId: " + currentBookId);
                    }
                });
            }

            // Phase 1: 预占库存(Redis Lua)
            for (Map.Entry<Integer, Integer> entry : mergedItems.entrySet()) {
                int bookId = entry.getKey();
                int quantity = entry.getValue();

                // 1) 非锁读 book 拿 price（写 OrderItem 需要）
                Book book = bookMapper.selectById(bookId);
                if (book == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "Book not found: " + bookId);
                }
                // 2) Redis Lua 原子预占 — 替代原 DB 悲观锁路径
                if (!bookInventoryService.tryReserve(bookId, quantity)) {
                    throw new BusinessException(ResultCode.CONFLICT, "Insufficient stock for bookId: " + bookId);
                }
                reservedBookIds.add(bookId);
                reservedQtys.add(quantity);

                OrderItem orderItem = new OrderItem();
                try {
                    orderItem.setPrice(book.getPrice());
                    orderItem.setBookId(bookId);
                    orderItem.setQuantity(quantity);
                    orderItems.add(orderItem);
                    totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(quantity)));
                } catch (ArithmeticException e) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "Error calculating total amount: " + e.getMessage());
                } catch (NullPointerException e) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "Book price is null for bookId: " + bookId);
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

            // Phase 3: Redis 写入(仅超时索引) — 放到 afterCommit 执行
            // 只写 scheduler 真正会读的 pending:expire 索引;
            // 订单详情/历史一律以 DB 为准,不再维护只写不读的缓存副本
            //
            // ⚠️ 为什么必须等事务提交后才写索引:
            // 此刻订单还没提交,后面任何一步失败(主键没回填、库存补偿抛异常…)都会回滚,
            // DB 里没有这单,索引里却多了一个成员 —— 幽灵成员永远过期、又永远删不掉,
            // 会一直霸占 ZSet 的头部,把真正该关的单挤到批次上限之外(饿死)。
            //
            // 写索引失败也不该阻断下单:DB 才是权威,索引真丢了还有 scheduler 的
            // DB 兜底扫描兜着(按 orders.expire_time 找 PENDING)。
            String expireKey = RedisKeys.userPendingExpireIdx(currentUserId);
            long expireMillis = expireTime.atZone(zoneId).toInstant().toEpochMilli();
            registerRedisWriteAfterCommit(() ->
                stringRedisTemplate.opsForZSet().add(expireKey, String.valueOf(order.getOrderNumber()), expireMillis));

            if (order.getId() == null) {
                // useGeneratedKeys 未回填主键 — 返回假的订单号会让前端拿到错误数据
                throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "订单创建失败:数据库未回填主键");
            }
            return order.getId();

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
                    metrics.orderCompensateFailed(reservedBookIds.get(i));
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

    /** 每页条数上限 —— 防 size=999999 打爆 DB(与 BookController.MAX_PAGE_SIZE 对齐) */
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public PageResult<Order> listAllOrders(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int total = orderMapper.countAllOrders();
        // total=0 时不必再查一次列表(空集)
        List<Order> rows = total == 0
                ? List.of()
                : orderMapper.selectOrderPage((safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, rows);
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
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.CANCELLED.getCode(), null);
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
                releaseWithRollbackCompensation(orderItem.getBookId(), orderItem.getQuantity());
            } catch (Exception releaseEx) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR,
                    "释放库存失败:bookId=" + orderItem.getBookId() + ",err=" + releaseEx.getMessage(), releaseEx);
            }
        }

        // afterCommit 钩子:Hash 状态翻转 + 主动 ZREM expire idx(scheduler 不必再扫)
        registerRedisWriteAfterCommit(() ->
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(currentUserId),
                String.valueOf(orderNumber)));

        return order.getId() != null ? order.getId() : 1;
    }

    /**
     * 支付订单。
     *
     * <h2>noRollbackFor 的意义(I-4)</h2>
     * <p>付款时若库存不足,本方法会在同一事务内把订单置为 CANCELLED 并释放预占,
     * 然后抛 {@link OrderAutoCancelledException}。<b>该异常必须让事务提交</b>——
     * 否则刚写入的"已取消"会被回滚,订单回到 PENDING,用户既付不了款也取消不掉。
     * Spring 的规则匹配取"最具体"的一条,本类比 Exception 更具体,故优先生效。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = OrderAutoCancelledException.class)
    public int payOrder(Long orderNumber, Integer currentUserId, String paymentMethodRaw) {

        /*
         * 支付方式:入口处就收敛成枚举。
         *
         * 这一步必须在**任何写操作之前** —— 传了不认识的值直接 400,
         * 不能等订单状态都翻成 PAID 了才发现方式非法(那会留下
         * "已支付但方式是脏值"的半成品记录)。
         *
         * ⚠️ 这里只是**记录用户选了什么**,并没有真的调支付网关。
         * 接真实网关时要在扣库存 / 翻状态之前插一步"调网关 + 验签",
         * 验签通过才允许继续 —— 否则任何人直接 PATCH 这个接口就能把订单改成已支付。
         */
        PaymentMethod paymentMethod = PaymentMethod.from(paymentMethodRaw);

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

            // ---- 步骤 1:预检(非锁读,用于判断是否走"自动关单"路径)----
            // 此时尚未做任何库存变更,所以后续提交事务(不回滚)是安全的
            for (OrderItem orderItem : orderItems) {
                Book book = bookMapper.selectById(orderItem.getBookId());
                if (book == null
                        || book.getStockQuantity() == null
                        || book.getStockQuantity() < orderItem.getQuantity()) {
                    // 库存已不足 → 在同一事务内关单 + 释放预占,再以"不回滚"的异常结束
                    autoCancelBecauseOutOfStock(order, orderItems, currentUserId);
                    throw new OrderAutoCancelledException(
                        "库存不足,订单已自动取消:bookId=" + orderItem.getBookId()
                            + ",需要=" + orderItem.getQuantity()
                            + ",库存=" + (book == null ? "图书不存在" : book.getStockQuantity()));
                }
            }

            // ---- 步骤 2:权威扣减(条件更新,兜住预检与扣减之间的并发竞态)----
            for (OrderItem orderItem : orderItems) {
                int rows = bookMapper.decrementStockIfEnough(orderItem.getBookId(), orderItem.getQuantity());
                if (rows == 0) {
                    // 预检通过但扣减失败 = 极小概率的并发售罄
                    // 这里回滚(库存已部分变更,交回滚处理最安全),订单保持 PENDING 由用户/超时处理
                    throw new BusinessException(ResultCode.CONFLICT,
                        "库存不足(并发售罄),请稍后重试或取消订单: bookId=" + orderItem.getBookId());
                }
            }
        }

        // DB 扣减成功 → 翻状态(状态守卫:只有 PENDING 才能转 PAID)
        int paidRows = orderMapper.updateStatusByOrderNumberGuard(
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.PAID.getCode(), paymentMethod.name());
        if (paidRows == 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                "Order status changed concurrently: " + orderNumber);
        }
        order.setOrderStatus(OrderStatus.PAID.getCode());
        // 内存里的 order 也同步一份,和 DB 刚写入的 payment_method 保持一致
        order.setPaymentMethod(paymentMethod.name());

        if (orderItems != null && !orderItems.isEmpty()) {
            for (OrderItem orderItem : orderItems) {
                confirmWithRollbackCompensation(orderItem.getBookId(), orderItem.getQuantity());
            }
        }
        // Hash 状态翻转(afterCommit 钩子避免 DB 回滚但 Redis 已写)
        registerRedisWriteAfterCommit(() ->
            // 主动从 pending expire idx 移除,scheduler 不必再扫
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(currentUserId),
                String.valueOf(orderNumber)));

        return order.getId() != null ? order.getId() : 1;
    }

    /**
     * 付款时库存不足 → 在<b>当前事务内</b>关单并释放预占(I-4)。
     *
     * <p>调用方必须紧接着抛 {@link OrderAutoCancelledException}(标注了 noRollbackFor),
     * 让这些写入真正提交,否则订单会回到 PENDING。</p>
     */
    private void autoCancelBecauseOutOfStock(Order order, List<OrderItem> orderItems, Integer currentUserId) {
        int rows = orderMapper.updateStatusByOrderNumberGuard(
            order.getOrderNumber(), OrderStatus.PENDING.getCode(), OrderStatus.CANCELLED.getCode(), null);
        if (rows == 0) {
            log.warn("auto-cancel skipped, order {} status changed concurrently", order.getOrderNumber());
            return;
        }
        for (OrderItem orderItem : orderItems) {
            try {
                releaseWithRollbackCompensation(orderItem.getBookId(), orderItem.getQuantity());
            } catch (Exception e) {
                log.error("auto-cancel: release failed bookId={}, qty={}",
                    orderItem.getBookId(), orderItem.getQuantity(), e);
            }
        }
        registerRedisWriteAfterCommit(() ->
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(currentUserId),
                String.valueOf(order.getOrderNumber())));
        log.warn("order {} auto-cancelled at payment due to insufficient stock", order.getOrderNumber());
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
     *   4. UPDATE orders SET status=TIMEOUT WHERE order_number=? AND order_status='PENDING'
     *      ← 状态守卫的原子 SQL,即使 FOR UPDATE 被优化掉也安全
     *   5. @Transactional commit
     *   6. afterCommit 钩子:Hash.put + ZREM
     * </pre>
     * 崩溃窗口分析:3 与 4 之间崩 → DB 仍 PENDING,Redis stock 已释放,
     * scheduler 下次扫到该订单 → 重走 3-4-5-6 → release 二次释放(Lua -2 静默)→ UPDATE 0 行 → 早返回。
     * 永远不会泄漏库存。
     *
     * <h2>为什么落 TIMEOUT 而不是 CANCELLED</h2>
     * <p>超时关单和"用户主动取消"是两件事,前端、统计口径都要能分开看:
     * 管理端订单列表有四种状态标签,看板的订单状态分布图也按四种状态分桶。
     * 全项目只有这一处能把订单变成 TIMEOUT —— 以前这里写的是 CANCELLED,
     * 于是超时单永远显示成"已取消",TIMEOUT 分支是死代码,分布图里超时那格恒为 0。</p>
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
                releaseWithRollbackCompensation(orderItem.getBookId(), orderItem.getQuantity());
            }
        }

        // 步骤 4:状态守卫的 UPDATE — 只有 PENDING 才能转 TIMEOUT(超时取消)
        int rows = orderMapper.updateStatusByOrderNumberGuard(
            orderNumber, OrderStatus.PENDING.getCode(), OrderStatus.TIMEOUT.getCode(), null);
        if (rows == 0) {
            // 别人抢先改了状态(Paid 或 Cancelled)— 我们的 release 多减一次,但 Lua -2 静默处理
            log.warn("cancelExpiredOrder: order {} status changed concurrently, release may double-count", orderNumber);
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)
            );
            return;
        }

        // 步骤 6:afterCommit 钩子 — 从超时索引移除
        registerRedisWriteAfterCommit(() ->
            stringRedisTemplate.opsForZSet().remove(
                RedisKeys.userPendingExpireIdx(order.getUserId()),
                String.valueOf(orderNumber)));
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

    /**
     * 注册"事务回滚补偿" —— 事务回滚时把刚才在 Redis 上做掉的改动原样撤销。
     *
     * <h2>为什么需要它</h2>
     * <p>Redis 不参与 DB 事务。{@code release} / {@code confirm} 必须<b>在 DB 事务内部</b>执行
     * (订单状态翻转和 DB 扣库存要跟 Redis 保持同一个事务语义,失败要能一起回滚),
     * 但如果 DB 事务随后回滚,Redis 那一步<b>不会跟着回滚</b>:</p>
     * <ul>
     *   <li>{@code release} 的副作用是 {@code stock += N, reserved -= N} —— 总和不变,
     *       但"某订单占的量"已经被还回池子,别人可以再抢一次 → 该订单付款时会被 DB 守卫拒绝。
     *       <b>这个方向最危险</b>。</li>
     *   <li>{@code confirm} 的副作用是 {@code reserved -= N} —— 总和少了 N,
     *       可用量比真实值少,别的用户会看到"库存不足"(方向安全,10 分钟内被对账修复)。</li>
     * </ul>
     * <p>所以在做完 Redis 操作的同时挂一个 afterCompletion 钩子:回滚时执行"逆操作",
     * 让 Redis 回到操作前的状态。</p>
     *
     * <p>注意:没有活动事务时不注册补偿 —— 没有事务就没有回滚,撤销反而会写错。</p>
     */
    private void registerRedisCompensation(Runnable undoOp) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    undoOp.run();
                } catch (Exception e) {
                    // 补偿失败不能抛 —— 此刻事务已经结束,抛出去也回滚不了什么
                    log.error("Redis rollback compensation failed, inventory drift needs reconcile", e);
                }
            }
        });
    }

    /**
     * 释放预占,并登记"回滚补偿"—— 撤销 release 就是重新预占
     * ({@code stock -= N, reserved += N},正好是 release 的逆操作)。
     */
    private void releaseWithRollbackCompensation(Integer bookId, Integer quantity) {
        bookInventoryService.release(bookId, quantity);
        registerRedisCompensation(() -> {
            if (!bookInventoryService.tryReserve(bookId, quantity)) {
                log.error("INVENTORY DRIFT: rollback compensation could not re-reserve "
                    + "bookId={}, qty={} (stock was taken by others), reconcile needed", bookId, quantity);
            }
        });
    }

    /**
     * 确认扣减,并登记"回滚补偿"—— 撤销 confirm 就是把 {@code reserved} 还回去
     * ({@link com.tmt.TMLibrary.service.BookInventoryService#restoreReserved})。
     */
    private void confirmWithRollbackCompensation(Integer bookId, Integer quantity) {
        bookInventoryService.confirm(bookId, quantity);
        registerRedisCompensation(() -> bookInventoryService.restoreReserved(bookId, quantity));
    }

    @Override
    public java.util.List<Long> findExpiredPendingOrderNumbers(int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Long> numbers = orderMapper.selectExpiredPendingOrderNumbers(
            OrderStatus.PENDING.getCode(), limit);
        return numbers == null ? java.util.Collections.emptyList() : numbers;
    }
}

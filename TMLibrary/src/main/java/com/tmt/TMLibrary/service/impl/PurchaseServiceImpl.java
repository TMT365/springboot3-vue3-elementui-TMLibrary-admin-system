package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderWithItems;
import com.tmt.TMLibrary.entity.OrderItem;
import java.util.List;

import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.mapper.OrderMapper;
import com.tmt.TMLibrary.service.PurchaseService;

import lombok.RequiredArgsConstructor;

import com.tmt.TMLibrary.dto.PurchaseItemRequest;
import com.tmt.TMLibrary.dto.PurchaseRequest;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.common.Result.ResultCode;
import java.util.ArrayList;
import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.common.User.UserStatus;

// Service 需要@Slf4j来记录异常吗？需要的，我们需要知道是那个类的那个方法抛出了异常。

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {
    private final OrderMapper orderMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;


    private boolean checkUser(Integer userId) {
        // 这里可以实现用户状态的检查逻辑，例如查询数据库中的用户状态
        // 如果用户被禁用或删除，返回false，否则返回true
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            return false;
        }
        if (user.getStatus() == null) {
            return false;
        }
        if (!user.getStatus().equals(UserStatus.ACTIVE.getCode())) {
            return false;
        }
        return user.getDeletedAt() == null;
    }

    private void checkOrder(Order order, Integer orderId, Integer currentUserId, String where) {
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found: " + orderId, where);
        }
        if (!order.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "User is not authorized to cancel this order", where);
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED.getCode()) {
            throw new BusinessException(ResultCode.CONFLICT, "Order is already cancelled: " + orderId, where);
        }
        if (order.getOrderStatus() == OrderStatus.PAID.getCode()) {
            throw new BusinessException(ResultCode.CONFLICT, "Order is already paid and cannot be cancelled: " + orderId, where);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createOrder(PurchaseRequest purchaseRequest, Integer currentUserId) {
        String where = "["+this.getClass().getName() +"]"+ ".createOrder";
        // 当前用户校验，防止用户的Status不合法, 用户已经被禁用或者被删除
        if (!checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to create order", where);
        }
        // 定义一个集合用来存储订单项
        List<OrderItem> orderItems = new ArrayList<>();
        // 实现创建订单的逻辑
        Order order = new Order();
        order.setUserId(currentUserId);
        // 调用UUID生成订单号
        order.setOrderNumber(UUID.randomUUID().toString());
        // 计算总金额, 那还需要去数据库查商品价格
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseItemRequest item : purchaseRequest.getItems()) {
            if (item.getQuantity() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Quantity must be greater than 0 for bookId: " + item.getBookId(), where);
            }
            if (item.getBookId() == null || item.getBookId() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "BookId cannot be null", where);
            }
            // 根据商品ID查询商品信息
            Book book = bookMapper.selectByIdForUpdate(item.getBookId());
            // 这里使用了悲观锁的方式来查询商品信息, 以防止在高并发的情况下, 商品价格被修改, 导致计算总金额不准确
            OrderItem orderItem = new OrderItem();
            if (book == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Book not found: " + item.getBookId(), where);
            }
            // 如果数据库里面的库存小于购买的数量, 那就抛出异常
            if (book.getStockQuantity() < item.getQuantity()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Insufficient stock for bookId: " + item.getBookId(), where);
            }
            try {
                orderItem.setPrice(book.getPrice());
                orderItem.setBookId(item.getBookId());
                orderItem.setQuantity(item.getQuantity());
                orderItems.add(orderItem);
                totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                // Book 进行原子操作
                bookMapper.atomicDecrementStock(book.getId(), item.getQuantity());
            } catch (ArithmeticException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Error calculating total amount: " + e.getMessage(), where);
            } catch (NullPointerException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Book price is null for bookId: " + item.getBookId(), where);
            } catch (Exception e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Error calculating total amount: " + e.getMessage(), where);
            }
        }
        order.setTotalAmount(totalAmount);
        // 设置订单状态为待支付
        order.setOrderStatus(OrderStatus.PENDING.getCode());
        // 创建时间和更新时间由数据库自动生成, 所以不需要在这里设置
        orderMapper.insertOrder(order);
        // orderItem要在order插入后才能获取到orderId, 所以要在orderMapper.insertOrder(order)之后再设置orderItem的orderId
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderMapper.insertOrderItem(orderItem);
        }   
        return 1;
    }

    @Override
    public Order getOrderById(Integer orderId) {
        return orderMapper.selectOrderById(orderId);
    }

    @Override
    public OrderWithItems getOrderWithItemsByOrderId(Integer orderId) {
        return orderMapper.selectOrderWithItemsByOrderId(orderId);
    }

    @Override
    public OrderItem getOrderItemById(Integer orderItemId) {
        return orderMapper.selectOrderItemById(orderItemId);
    }

    @Override
    public List<OrderWithItems> listOrdersByUserId(Integer currentUserId) {
        // 这里可以实现根据用户ID查询订单的逻辑
        return orderMapper.selectOrderWithItemsByUserId(currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(Integer orderId, Integer currentUserId) {
        String where = "["+this.getClass().getName() +"]"+ ".cancelOrder";
        // 这里可以实现取消订单的逻辑
        // 首先校验用户的状态，如果是禁用或者删除的用户，不能取消订单
        if (!checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to cancel order", where);
        }
        // 其次查询订单的状态，如果订单已经支付或者已经取消，不能取消订单
        // 其实支付完成了也可以取消订单, 但是要考虑支付网关的退款问题, 所以这里暂时不允许取消已经支付的订单，后面可以考虑增加退款的逻辑
        Order order = orderMapper.selectOrderByIdForUpdate(orderId);
        checkOrder(order, orderId, currentUserId, where);
        // 如果订单状态是待支付或者其他状态，可以取消订单
        order.setOrderStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateStatusById(orderId, OrderStatus.CANCELLED);
        // 取消订单后，需要将订单项的库存还原
        // 查询订单项
        List<OrderItem> orderItems = orderMapper.selectOrderItemsByOrderId(orderId);
        if (orderItems == null || orderItems.isEmpty()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Order items not found for orderId: " + orderId, where);
        }
        // 还原库存
        for (OrderItem orderItem : orderItems) {
            Book book = bookMapper.selectByIdForUpdate(orderItem.getBookId().intValue());
            if (book == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Book not found for bookId: " + orderItem.getBookId(), where);
            }
            bookMapper.atomicIncrementStock(book.getId(), orderItem.getQuantity());
        }

        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int payOrder(Integer orderId, Integer currentUserId, String paymentMethod) {
        String where = "["+this.getClass().getName() +"]"+ ".payOrder";
        // 这里可以实现支付订单的逻辑
        // 首先校验用户的状态，如果是禁用或者删除的用户，不能支付订单
        if (!checkUser(currentUserId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User is not authorized to pay order");
        }
        // 其次查询订单的状态，如果订单已经支付或者已经取消，不能支付订单
        Order order = orderMapper.selectOrderByIdForUpdate(orderId);
        checkOrder(order, orderId, currentUserId, where);
        // 如果订单状态是待支付或者其他状态，可以支付订单
        order.setOrderStatus(OrderStatus.PAID.getCode());
        orderMapper.updateStatusById(orderId, OrderStatus.PAID);
        return 1;
    }
}
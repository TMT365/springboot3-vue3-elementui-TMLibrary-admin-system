package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.dto.PurchaseRequest;
import com.tmt.TMLibrary.dto.PurchaseResponse;
import com.tmt.TMLibrary.entity.OrderWithItems;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.CurrentUser;
import com.tmt.TMLibrary.security.UserView;
import com.tmt.TMLibrary.service.PurchaseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @brief 购书订单 RESTful Controller。
 *
 *        <p>
 *        端点:
 *        <pre>
 *   POST   /api/purchases              — 下单(创建 PENDING 订单)
 *   GET    /api/purchases/{id}         — 订单详情(含 items)
 *   DELETE /api/purchases/{id}         — 取消订单(改状态 CANCELLED + 退库存)
 *   PATCH  /api/purchases/{id}/pay     — 支付(改状态 PAID)
 *        </pre>
 *
 *        <p>
 *        当前用户从 request attribute "CURRENT_USER" 读 — JwtAuthFilter 写入,
 *        @CurrentUser UserView me 注入到方法参数。
 */
@Slf4j
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    /**
     * 下单 — POST /api/purchases
     * 返回订单 ID,客户端可用 GET /api/purchases/{id} 看详情。
     */
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody PurchaseRequest req,
            @CurrentUser UserView me) {
        // 多加一层兜底,防止 Filter 没写 attribute 时(null)抛 401。
        requireLogin(me);
        log.info("前端请求/api/purchases, 参数={}", req);
        int orderId = purchaseService.createOrder(req, me.getId());
        return Result.success(orderId);
    }

    /**
     * 订单详情 — GET /api/purchases/{id}
     * 仅订单所有者可查看(service 没做权限检查,这里 Controller 兜底)。
     * 返回 PurchaseResponse(只暴露客户端需要的字段)。
     */
    @GetMapping("/{id}")
    public Result<PurchaseResponse> getById(@PathVariable int id,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/purchases/{}", id);
        OrderWithItems order = purchaseService.getOrderWithItemsByOrderId(id);
        if (order == null || order.getOrder() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Order not found: " + id);
        }
        if (!order.getOrder().getUserId().equals(me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Not authorized to view this order");
        }
        return Result.success(PurchaseResponse.from(order));
    }

    /**
     * 取消订单 — DELETE /api/purchases/{id}
     * 状态 → CANCELLED,库存还原。Service 内部做 owner 校验。
     */
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable int id,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求DELETE /api/purchases/{}", id);
        purchaseService.cancelOrder(id, me.getId());
        return Result.success();
    }

    /**
     * 支付订单 — PATCH /api/purchases/{id}/pay?paymentMethod=ALIPAY
     * 状态 → PAID。paymentMethod 作为 query 参数,留给未来接支付网关时扩展。
     */
    @PatchMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable int id,
            @RequestParam(name = "paymentMethod", defaultValue = "DEFAULT") String paymentMethod,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/purchases/{}/pay?paymentMethod={}", id, paymentMethod);
        purchaseService.payOrder(id, me.getId(), paymentMethod);
        return Result.success();
    }

    /**
     * 防御兜底 — Filter 没写 attribute 时(null)抛 401。
     */
    private void requireLogin(UserView me) {
        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }
}

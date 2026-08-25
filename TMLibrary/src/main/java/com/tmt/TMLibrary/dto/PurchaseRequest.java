package com.tmt.TMLibrary.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * 下单请求体 — 客户端发起购买时 POST /api/purchases 的入参。
 * <p>
 * 用户身份(userId)不在这里 — Controller 从 @CurrentUser UserView me.getId() 拿,
 * 客户端 DTO 传 userId 字段是攻击面,已被移除。
 * <p>
 * 字段:
 * <ul>
 * <li>购买项列表:一个包含多个 PurchaseItemRequest 的列表,每个 PurchaseItemRequest 表示一个购买项,包含商品ID、数量</li>
 * </ul>
 */
@Data
public class PurchaseRequest {
    /*
     * userId 字段已删除 — 原本是 @NotNull(message = "用户ID不能为空") private Integer userId;
     * 删除原因:服务端从 @CurrentUser UserView 拿真实用户身份,不该让客户端伪造。
     * 如果收到旧客户端发的 {"userId": X, "items": [...},该字段被 Jackson 忽略。
     */
    @NotNull(message = "购买项不能为空")
    private List<PurchaseItemRequest> items;
}

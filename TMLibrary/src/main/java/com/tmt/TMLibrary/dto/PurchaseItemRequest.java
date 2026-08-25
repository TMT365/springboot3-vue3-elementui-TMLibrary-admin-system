package com.tmt.TMLibrary.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class PurchaseItemRequest {
    @NotNull(message = "书籍ID不能为空")
    private Integer bookId;
    @NotNull(message = "购买数量不能为空")
    private Integer quantity;
}

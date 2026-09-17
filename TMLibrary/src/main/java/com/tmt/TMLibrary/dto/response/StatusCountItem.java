package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 「订单状态 + 数量」聚合行 —— 状态分布图用。
 * <p>status 是 Integer code(0 待支付 / 1 已支付 / 2 已取消 / 3 超时),
 * 中文标签由前端映射 —— 后端只回数值,展示文案属于 UI 层。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatusCountItem {
    /** 0=PENDING 1=PAID 2=CANCELLED 3=TIMEOUT */
    private int status;
    private long count;
}

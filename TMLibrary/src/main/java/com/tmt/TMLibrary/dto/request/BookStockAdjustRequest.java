package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 库存调整请求 — 盘点语义(绝对值,非增量)。
 *
 * <p>为什么单独一个接口:库存有两个变更来源——</p>
 * <ol>
 *   <li><b>交易链路</b>:下单预占 / 取消释放 / 付款扣减,走 Redis Lua + DB 条件 UPDATE</li>
 *   <li><b>管理链路</b>:管理员盘点(进货入库 / 盘亏修正)</li>
 * </ol>
 *
 * <p>两者若混在同一个「更新图书」接口里,语义会含糊:管理员把库存改成 5 时,
 * 系统无法区分这是"盘点结果"还是"随手填的值",也无法审计。
 * 拆出本接口后,盘点动作可以被单独授权与记录。</p>
 *
 * <p>提交后服务端会以该值作为 DB 权威库存,并同步 Redis
 * ({@code stock = 新值 - reserved},保留在途预占)。</p>
 */
@Data
public class BookStockAdjustRequest {

    /** 调整后的库存绝对值(≥ 0) */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    private Integer stockQuantity;
}

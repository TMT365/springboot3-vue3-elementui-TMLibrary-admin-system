package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 仪表盘统计快照 —— GET /stats/dashboard 的响应体,也是 Redis 缓存的对象。
 *
 * <p>一次请求返回 4 组数据,前端 4 张图各取一组:
 * <ul>
 *   <li>{@code newUsersTrend} — 最近新增用户(按天)</li>
 *   <li>{@code salesTrend} — 销量趋势(按天,仅 PAID 订单)</li>
 *   <li>{@code bookSalesTop} — 每本书销量 Top N</li>
 *   <li>{@code orderStatusDistribution} — 订单状态分布</li>
 * </ul>
 *
 * <p>不含 {@code generatedAt} 之类的字段:缓存的就是这份快照,
 * 前端要"新鲜度"看 HTTP 层的 Cache 语义即可,不必把时间戳塞进业务体。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    /** 最近 N 天每天的新增用户数(已排除软删用户) */
    private List<DailyCountItem> newUsersTrend;

    /** 最近 N 天每天的成交订单数 + 销售额(仅统计 order_status=PAID) */
    private List<DailySalesItem> salesTrend;

    /** 销量最高的书(按下单件数倒序,取前 N) */
    private List<BookSalesItem> bookSalesTop;

    /** 最近 N 天订单状态分布(0 待支付 / 1 已支付 / 2 已取消 / 3 超时) */
    private List<StatusCountItem> orderStatusDistribution;
}

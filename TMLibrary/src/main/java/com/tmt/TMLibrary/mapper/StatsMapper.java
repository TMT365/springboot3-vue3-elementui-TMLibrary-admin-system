package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.dto.response.BookSalesItem;
import com.tmt.TMLibrary.dto.response.DailyCountItem;
import com.tmt.TMLibrary.dto.response.DailySalesItem;
import com.tmt.TMLibrary.dto.response.StatusCountItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘统计查询 —— 全部是聚合,不返回实体。
 *
 * <p>参数约定(沿用 BookMapper 的区间扫描风格):
 * <ul>
 *   <li>{@code start} 是**半开区间左端点** {@code [start, now)},由 Service 端算好传入,
 *       XML 只做 {@code created_time >= #{start}} 的索引友好比较</li>
 *   <li>不使用 {@code YEAR()}/{@code MONTH()} 做点查,但**分组**用
 *       {@code GROUP BY DATE(...)} —— 分组必须在 SQL 里做,否则要把区间内所有行拉回 JVM 再聚合</li>
 * </ul>
 */
@Mapper
public interface StatsMapper {

    /** 最近 N 天每天的新增用户数(排除软删)。按日期升序 */
    List<DailyCountItem> countNewUsersByDay(@Param("start") LocalDateTime start);

    /** 最近 N 天每天的成交订单数 + 成交额(仅 PAID)。按日期升序 */
    List<DailySalesItem> sumSalesByDay(@Param("start") LocalDateTime start);

    /** 销量 Top N 的书(按下单件数倒序) */
    List<BookSalesItem> topBookSales(@Param("start") LocalDateTime start,
                                     @Param("limit") int limit);

    /** 最近 N 天订单状态分布(返回全部 4 种状态,无数据的补 0 由前端做) */
    List<StatusCountItem> countOrdersByStatus(@Param("start") LocalDateTime start);
}

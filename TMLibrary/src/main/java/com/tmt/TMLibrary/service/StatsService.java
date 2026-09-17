package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.DashboardStatsResponse;

public interface StatsService {

    /**
     * 仪表盘统计快照 —— 走 Redis cache-aside:
     * <ol>
     *   <li>命中 {@code tmlibrary:stats:byScope:dashboard-{days}d:overview} → 直接反序列化返回</li>
     *   <li>未命中 → 4 个聚合查询 → 写缓存(TTL 5 分钟)→ 返回</li>
     * </ol>
     *
     * @param days 统计窗口天数(调用方已夹到 [7, 90])
     */
    DashboardStatsResponse dashboard(int days);
}

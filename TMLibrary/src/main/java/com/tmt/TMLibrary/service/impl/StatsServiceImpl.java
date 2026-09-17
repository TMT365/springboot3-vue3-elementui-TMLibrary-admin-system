package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.response.DashboardStatsResponse;
import com.tmt.TMLibrary.mapper.StatsMapper;
import com.tmt.TMLibrary.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 仪表盘统计 —— Redis cache-aside。
 *
 * <p><b>缓存策略</b>:整个响应体一个 key,TTL 5 分钟。统计是"看一眼"的场景,
 * 不要求强一致 —— 5 分钟内的重复访问直接吃缓存,过期自动回源。不做主动失效
 * (下单/注册时去清缓存会把统计模块耦合进交易链路,收益不值)。
 *
 * <p><b>缓存失败不阻塞业务</b>:序列化/反序列化异常只 log,照常查库返回实时数据
 * —— 跟 {@code AuthServiceImpl.refreshUserCache} 一个套路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    /** 缓存 TTL —— 5 分钟 */
    private static final long CACHE_TTL_MINUTES = 5;
    /** 每本书销量取前 N */
    private static final int TOP_BOOKS_LIMIT = 8;

    private final StatsMapper statsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardStatsResponse dashboard(int days) {
        String cacheKey = RedisKeys.statsDashboard(days);

        // 1. 先查缓存
        DashboardStatsResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 回源 —— 半开区间 [今天往前 days 天的 00:00, now)
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        DashboardStatsResponse fresh = new DashboardStatsResponse(
                statsMapper.countNewUsersByDay(start),
                statsMapper.sumSalesByDay(start),
                statsMapper.topBookSales(start, TOP_BOOKS_LIMIT),
                statsMapper.countOrdersByStatus(start));

        // 3. 写缓存(失败不影响返回)
        writeCache(cacheKey, fresh);

        return fresh;
    }

    /** 读缓存 —— 任何异常(含脏 JSON)都当未命中,回源即可 */
    private DashboardStatsResponse readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, DashboardStatsResponse.class);
        } catch (Exception e) {
            log.warn("统计缓存读取失败,回源查库: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, DashboardStatsResponse data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForValue()
                    .set(key, json, Expiration.from(CACHE_TTL_MINUTES, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("统计缓存写入失败(不影响本次返回): key={}, err={}", key, e.getMessage());
        }
    }
}

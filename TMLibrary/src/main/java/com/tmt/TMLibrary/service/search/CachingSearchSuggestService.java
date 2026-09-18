package com.tmt.TMLibrary.service.search;

import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.SearchSuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 候选词缓存装饰器 —— <b>Redis 在前,ES/MySQL 在后</b>。
 *
 * <pre>
 *   请求 → Redis 命中? ─是→ 直接返回
 *                    └否→ 委托给下游(ES,失败再退 MySQL)→ 写回 Redis → 返回
 * </pre>
 *
 * <h2>为什么套一层装饰器,而不是在 Service 里加 if</h2>
 * <p>缓存是横切关注点。装饰器让"取数逻辑"(ES 版 / MySQL 版 / 降级包装)
 * 完全不感知缓存的存在 —— 加缓存、换缓存、去掉缓存都不用碰它们。
 * 组合顺序见 {@code SearchSuggestServiceConfig}:</p>
 * <pre>
 *   Caching(  Fallback( ES, MySQL )  )
 * </pre>
 *
 * <h2>缓存什么</h2>
 * <ul>
 *   <li><b>空结果也缓存</b> —— 用户打错字时("不存在的书名xyz")每个字都打一次 ES,
 *       不缓存就等于把 ES 当靶子。空结果 TTL 短一些(1 分钟),既能挡重复,
 *       又不至于让"刚上架的书搜不到"持续太久</li>
 *   <li>非空结果 TTL 5 分钟(见 {@link RedisKeys#BOOK_SUGGEST_CACHE})</li>
 * </ul>
 *
 * <h2>Redis 挂了怎么办</h2>
 * <p>读/写异常一律 <b>静默穿透</b>到下游 —— 缓存是加速手段,不是可用性依赖。
 * 与 {@code CategoryServiceImpl} / {@code StatsServiceImpl} 一个套路。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class CachingSearchSuggestService implements SearchSuggestService {

    /** 非空结果 TTL */
    private static final long TTL_MINUTES = 5;
    /** 空结果 TTL —— 短一些,避免"新上架的书搜不到"持续太久 */
    private static final long EMPTY_TTL_MINUTES = 1;

    private final SearchSuggestService delegate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<BookSuggestion> suggest(String q, Integer limit) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            // 空输入不打下游、也不缓存 —— 没有缓存价值(每个空查询都一样,但也没人查)
            return List.of();
        }
        int cap = Math.min(Math.max(limit == null ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        String key = RedisKeys.bookSuggestCache(keyword, cap);

        // ① 读缓存
        List<BookSuggestion> cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        // ② 穿透到下游(ES → 失败退 MySQL)
        List<BookSuggestion> fresh = delegate.suggest(keyword, cap);
        if (fresh == null) {
            fresh = List.of();
        }

        // ③ 写回
        writeCache(key, fresh);
        return fresh;
    }

    /** 读缓存 —— 任何异常(含脏 JSON)都当未命中 */
    private List<BookSuggestion> readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            // 用数组接:泛型 List 的反序列化要 TypeReference,数组省掉这层
            BookSuggestion[] arr = objectMapper.readValue(json, BookSuggestion[].class);
            return Arrays.asList(arr);
        } catch (Exception e) {
            log.warn("候选词缓存读取失败,穿透到下游: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, List<BookSuggestion> data) {
        try {
            long ttl = data.isEmpty() ? EMPTY_TTL_MINUTES : TTL_MINUTES;
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data),
                    Expiration.from(ttl, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("候选词缓存写入失败(不影响本次返回): key={}, err={}", key, e.getMessage());
        }
    }
}

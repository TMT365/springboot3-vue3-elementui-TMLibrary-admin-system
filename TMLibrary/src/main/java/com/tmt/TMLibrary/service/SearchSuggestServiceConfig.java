package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.impl.EsSearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.impl.SearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.search.CachingSearchSuggestService;
import com.tmt.TMLibrary.service.search.SearchHealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * {@link SearchSuggestService} Bean 选择 + 自动降级。
 *
 * <h2>选择规则</h2>
 * <ul>
 *   <li>ES 当前可用 → 用 {@link EsSearchSuggestServiceImpl}(走 Elasticsearch)</li>
 *   <li>否则 → 用 MySQL 的 {@link SearchSuggestServiceImpl}</li>
 * </ul>
 *
 * <h2>运行期自动降级 + 恢复</h2>
 * <p>ES 的可用性由 {@link SearchHealthState} 统一判定(启动探 + 每 30s 重探)。
 * {@link FallbackSearchSuggestService} 每次调用都问它:<b>ES 可用</b>就试 ES,
 * 失败则当场标记降级、落回 MySQL;<b>ES 不可用</b>就直接走 MySQL,不再让请求
 * 去撞一次注定失败的 ES 连接。ES 恢复后 SearchHealthState 探到,自动切回 ——
 * 不用重启进程。</p>
 *
 * <h2>为什么这里用 @Primary</h2>
 * <p>{@link SearchSuggestService} 有两个候选 Bean(ES 版 + MySQL 版,默认用
 * @ConditionalOnXxx 来挑),但只有「一个」会被注入。{@code @Primary} 强制把这个
 * wrapper 排第一,业务层(@Autowired SearchSuggestService)拿到的是 wrapper,
 * wrapper 内部按需委托。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SearchSuggestServiceConfig {

    private final SearchHealthState searchHealthState;

    /**
     * 主 Bean —— 组合顺序:
     *
     * <pre>
     *   Caching(  Fallback( ES, MySQL )  )
     * </pre>
     *
     * <p>从外到内:先看 Redis,命中直接返回;未命中才往下走 ES;
     * ES 失败再退 MySQL。三层各自独立,换任意一层都不影响其它两层。</p>
     */
    @Bean
    @Primary
    public SearchSuggestService searchSuggestService(
            EsSearchSuggestServiceImpl esImpl,
            SearchSuggestServiceImpl mysqlImpl,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        SearchSuggestService fallback =
                new FallbackSearchSuggestService(esImpl, mysqlImpl, searchHealthState);
        return new CachingSearchSuggestService(fallback, stringRedisTemplate, objectMapper);
    }

    /**
     * 包装器:走 ES,失败时 fallback 到 MySQL(同请求内,不影响下次)。
     *
     * <p>设计成"包 ES + 包 MySQL",既保留接缝(只暴露 {@link SearchSuggestService}
     * 接口),又能在 ES 故障时不传递异常。ES 可用性读 {@link SearchHealthState},
     * 失败一次就把状态翻成"降级",避免后续请求反复撞 ES 连接。</p>
     */
    @RequiredArgsConstructor
    static class FallbackSearchSuggestService implements SearchSuggestService {
        private final EsSearchSuggestServiceImpl es;
        private final SearchSuggestServiceImpl mysql;
        private final SearchHealthState health;

        @Override
        public List<BookSuggestion> suggest(String q, Integer limit) {
            if (health.isEsHealthy()) {
                try {
                    List<BookSuggestion> r = es.suggest(q, limit);
                    if (r != null) return r;
                } catch (Exception ex) {
                    log.warn("ES 候选词查询失败,降级 MySQL(30s 后自动重试 ES): {}", ex.getMessage());
                    health.markUnhealthy();
                }
            }
            return mysql.suggest(q, limit);
        }
    }
}
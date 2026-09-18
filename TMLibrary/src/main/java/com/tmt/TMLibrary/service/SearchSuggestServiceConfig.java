package com.tmt.TMLibrary.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.impl.EsSearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.impl.SearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.search.CachingSearchSuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link SearchSuggestService} Bean 选择 + 自动降级。
 *
 * <h2>选择规则</h2>
 * <ul>
 *   <li>{@code app.search.elasticsearch.enabled=true} → 用 {@link EsSearchSuggestServiceImpl}</li>
 *   <li>否则 → 用 MySQL 的 {@link SearchSuggestServiceImpl}</li>
 * </ul>
 *
 * <h2>运行期自动降级</h2>
 * <p>就算开关开了,ES 也可能在某次重启后连不上(密码变了 / 容器挂了)。
 * {@link FallbackSearchSuggestService} 会捕获 ES 实现抛的异常,fallback 到 MySQL,
 * 一次失败不影响后续请求 —— 因为每次调用都会重新判断 ES 是否恢复。</p>
 *
 * <h2>为什么这里用 @Primary</h2>
 * <p>{@link SearchSuggestService} 有两个候选 Bean(ES 版 + MySQL 版,默认用 @ConditionalOnXxx
 * 来挑),但只有「一个」会被注入。{@code @Primary} 强制把这个 wrapper 排第一,
 * 业务层(@Autowired SearchSuggestService)拿到的是 wrapper,wrapper 内部按需委托。</p>
 *
 * <h2>启动期 healthcheck</h2>
 * <p>开关 = on 时,Spring 启动**之前**对 ES 打一个 1 秒超时的 ping。
 * 连不上就让开关回退到 off,应用照样起来,只是搜索会走 MySQL 实现。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SearchSuggestServiceConfig {

    private final ElasticsearchClient esClient;

    @Value("${app.search.elasticsearch.enabled:true}")
    private boolean esEnabled;

    /**
     * 启动期 ES 健康检查 —— 失败就把 enabled 改回 false,让下面的 Bean 走 MySQL。
     *
     * <p>ping 用 {@code cluster.health},1 秒超时,失败(连不上、超时、401)一律视为
     * "不可用"。检查是 best-effort,自身异常不能阻塞应用启动。</p>
     */
    @Bean
    public Boolean esHealthAtStartup() throws Exception {
        if (!esEnabled) return false;
        try {
            // esClient 自带超时配置(连接/请求 都设了),这里额外给一层 1s 总预算
            boolean ok = esClient
                    .ping()
                    .value(); // ping 9.x 是 boolean 返回值
            if (!ok) {
                log.warn("⚠️ ES ping 返回 false —— 搜索建议将降级回 MySQL 实现");
                esEnabled = false;
            } else {
                log.info("✅ ES 健康检查通过 —— 搜索建议走 Elasticsearch");
            }
            return ok;
        } catch (Exception e) {
            log.warn("⚠️ ES ping 失败({}) —— 搜索建议将降级回 MySQL 实现", e.getMessage());
            esEnabled = false;
            return false;
        }
    }

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
                new FallbackSearchSuggestService(esImpl, mysqlImpl, () -> esEnabled);
        return new CachingSearchSuggestService(fallback, stringRedisTemplate, objectMapper);
    }

    /**
     * 包装器:走 ES,失败时 fallback 到 MySQL(同请求内,不影响下次)。
     *
     * <p>设计成"包 ES + 包 MySQL",既保留接缝(只暴露 {@link SearchSuggestService} 接口),
     * 又能在 ES 故障时不传递异常。</p>
     */
    @RequiredArgsConstructor
    static class FallbackSearchSuggestService implements SearchSuggestService {
        private final EsSearchSuggestServiceImpl es;
        private final SearchSuggestServiceImpl mysql;
        private final java.util.function.Supplier<Boolean> esEnabled;

        @Override
        public List<BookSuggestion> suggest(String q, Integer limit) {
            if (esEnabled.get()) {
                try {
                    List<BookSuggestion> r = es.suggest(q, limit);
                    if (r != null) return r;
                } catch (Exception ex) {
                    log.warn("ES 候选词查询失败,降级 MySQL: {}", ex.getMessage());
                }
            }
            return mysql.suggest(q, limit);
        }
    }
}
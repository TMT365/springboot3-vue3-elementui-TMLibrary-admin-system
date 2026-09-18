package com.tmt.TMLibrary.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tmt.TMLibrary.config.ElasticsearchConfig;
import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.SearchSuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 搜索候选词 —— Elasticsearch 实现。
 *
 * <p>这是 {@link SearchSuggestService} 的"另一个实现",用 Spring 的
 * {@code @ConditionalOnProperty} 让 {@code app.search.elasticsearch.enabled=true}
 * 时本类被注入,否则注入 MySQL 版本。详见 {@code SearchSuggestServiceConfig}。</p>
 *
 * <h2>查询模型</h2>
 * <p>三类型统一打一次 ES,index 里三个字段各自最佳匹配:</p>
 * <ul>
 *   <li>{@code title} —— edge-ngram 索引 / standard 查询(前缀 + 包含 + 错位容错)</li>
 *   <li>{@code author} —— standard,不需要前缀匹配</li>
 *   <li>{@code isbn} —— keyword exact match 字段</li>
 * </ul>
 *
 * <p>ES 返回的多字段命中在客户端合成一个候选词条目 —— 这样
 * "周志" 既能命中 author(《深入理解 Java 虚拟机》)、也能命中 title
 * (《深入理解 Java 虚拟机》title 里没有 "周志",就不会重复出现)。</p>
 *
 * <h2>失败处理</h2>
 * <p>ES 超时 / 连不上 / 字段错 → 抛异常 → 上层 {@code SearchSuggestServiceConfig}
 * 捕获后回退 MySQL 实现。不会把"ES 挂了"传递到前端。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsSearchSuggestServiceImpl implements SearchSuggestService {

    /** ES 客户端 bean —— 同容器内已有,直接注入 */
    private final ElasticsearchClient esClient;
    private final ElasticsearchConfig.Properties esProps;

    /**
     * 查询超时(毫秒)—— 候选词是高频接口(用户每次输入都打),
     * 这个值要小于 {@code app.search.elasticsearch.socket-timeout-ms} 才生效,
     * 否则会被 socket 超时先截断。
     */
    @Value("${app.search.elasticsearch.suggest-query-timeout-ms:150}")
    private int queryTimeoutMs;

    @Override
    public List<BookSuggestion> suggest(String q, Integer limit) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) return List.of();

        int cap = Math.min(Math.max(limit == null ? DEFAULT_LIMIT : limit, 1), MAX_LIMIT);
        String index = esProps.getSuggestIndex();

        try {
            // search_analyzer 是 title_search(没有 ngram),所以"周"会按单字匹配;
            // 配合 title_edge_ngram 的索引,前缀(逐字扩展)和子串都能命中。
            final String kw = keyword;
            SearchResponse<Map> resp = esClient.search(s -> s
                    .index(index)
                    .size(cap * 10) // 10x 取数,客户端裁剪;ES 也设了 size cap,避免 overscan
                    .timeout(queryTimeoutMs + "ms")
                    // bool.should 两路并行,命中任一即可:
                    //   ① multiMatch —— title/author 走 IK 分词,按相关度打分(title 权重最高)
                    //   ② wildcard    —— isbn 是 keyword 字段,分词器不参与,只能通配匹配,
                    //                    这样「9787」能命中「9787111544937」
                    // 之前那版靠 ngram + minimumShouldMatch("100%") 硬凑,
                    // 是因为 standard 分析器把中文切成单字、默认 OR 会假阳性;
                    // 换成 IK 之后按词切分本身就是精确的,不再需要那个补丁。
                    .query(qb -> qb.bool(b -> b
                            .should(sh -> sh.multiMatch(m -> m
                                    .query(kw)
                                    .fields("title^3", "author^2")
                                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                    // 75% 而不是默认 OR:IK 虽然按词切,但仍会留下少量高频单字
                                    // token(「的」「是」),OR 语义下「不存在的xyz」会命中
                                    // 任何标题里带「的」的书。75% 的效果:
                                    //   1-2 个 token → 全部必须命中(短查询要求精确)
                                    //   4 个 token   → 允许漏 1 个(长查询别太严)
                                    .minimumShouldMatch("75%")))
                            .should(sh -> sh.wildcard(w -> w
                                    .field("isbn")
                                    .value("*" + escapeWildcard(kw) + "*")))
                            .minimumShouldMatch("1")
                    )), Map.class);

            List<BookSuggestion> raw = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                Map<?, ?> doc = hit.source();
                if (doc == null) continue;
                String type = detectType(doc, kw);
                if (type == null) continue; // 三种字段都没命中,丢弃
                BookSuggestion s = new BookSuggestion();
                s.setType(type);
                s.setText(displayText(doc, type));
                s.setIsbn(text(doc, "isbn"));
                Object hot = doc.get("hot");
                s.setHot(hot instanceof Number ? ((Number) hot).intValue() : 0);
                raw.add(s);
            }

            // 按热度降序 + 类型优先级(书名 > 作者 > ISBN) + 字面序
            // —— 跟 MySQL 版本保持完全一致,前端列表顺序可预测
            raw.sort(
                    Comparator.comparing(BookSuggestion::getHot, Comparator.reverseOrder())
                            .thenComparingInt(EsSearchSuggestServiceImpl::typeRank)
                            .thenComparing(BookSuggestion::getText));

            return raw.size() > cap ? new ArrayList<>(raw.subList(0, cap)) : raw;
        } catch (IOException e) {
            // 抛回去给外层降级逻辑(MultiSearchSuggestService)处理
            throw new RuntimeException("ES 候选词查询失败", e);
        }
    }

    /**
     * 转义 wildcard 查询里的特殊字符 —— 否则用户输入 {@code *} 或 {@code ?}
     * 会被当成通配符,「*」一条就能把整个索引捞出来。
     */
    private static String escapeWildcard(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '*' || c == '?' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 哪一字段命中率最高 —— 用作候选词类型 */
    private static String detectType(Map<?, ?> doc, String kw) {
        String kwLower = kw.toLowerCase();
        String title = text(doc, "title");
        String author = text(doc, "author");
        String isbn = text(doc, "isbn");
        if (isbn != null && isbn.toLowerCase().contains(kwLower)) return BookSuggestion.TYPE_ISBN;
        if (author != null && author.toLowerCase().contains(kwLower)) return BookSuggestion.TYPE_AUTHOR;
        if (title != null && title.toLowerCase().contains(kwLower)) return BookSuggestion.TYPE_TITLE;
        // 三个字段都没字面命中 —— 一般是 fuzziness 命中,按 ES 给的相关度退到 title
        if (title != null) return BookSuggestion.TYPE_TITLE;
        if (author != null) return BookSuggestion.TYPE_AUTHOR;
        if (isbn != null) return BookSuggestion.TYPE_ISBN;
        return null;
    }

    /** 把 ES 命中包成前端要的展示文本:ISBN 类型展示数字(去掉可能的 X/-);其他原样 */
    private static String displayText(Map<?, ?> doc, String type) {
        String s = text(doc, switchTypeField(type));
        if (s == null) return "";
        if (BookSuggestion.TYPE_ISBN.equals(type)) {
            // ISBN 用原值即可,后端已经存的是带横杠形式,跟商城其它地方一致
            return s;
        }
        return s;
    }

    private static String switchTypeField(String type) {
        return switch (type) {
            case "TITLE"  -> "title";
            case "AUTHOR" -> "author";
            case "ISBN"   -> "isbn";
            default       -> "title";
        };
    }

    private static String text(Map<?, ?> doc, String key) {
        Object v = doc.get(key);
        return v == null ? null : v.toString();
    }

    private static int typeRank(BookSuggestion s) {
        return switch (s.getType()) {
            case BookSuggestion.TYPE_TITLE  -> 0;
            case BookSuggestion.TYPE_AUTHOR -> 1;
            case BookSuggestion.TYPE_ISBN   -> 2;
            default                         -> 3;
        };
    }
}
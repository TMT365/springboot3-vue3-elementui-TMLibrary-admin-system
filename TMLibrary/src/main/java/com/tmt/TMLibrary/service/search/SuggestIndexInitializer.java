package com.tmt.TMLibrary.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.tmt.TMLibrary.config.ElasticsearchConfig;
import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.entity.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时把 {@code tmlibrary_books_suggest} 索引建出来,并从 MySQL 全量同步。
 *
 * <h2>为什么是"启动时同步"</h2>
 * <p>ES 是搜索增强,主数据在 MySQL —— 真上量后这里要换成异步(MQ 消费 book 增删改事件);
 * 现在图书只有 12 本演示数据,启动期几毫秒就跑完,塞 MQ 是杀鸡用牛刀。
 * <b>这里的代码后面要换,接口要稳定</b>:</p>
 *
 * <ul>
 *   <li>{@link #ensureIndex()} —— 建索引(已存在则跳过)</li>
 *   <li>{@link #fullSyncFromMysql()} —— 从 MySQL 全量同步,失败只 log 不阻塞启动</li>
 * </ul>
 *
 * <h2>健康行为</h2>
 * <p>连不上 ES / indexer 异常 → <b>不抛启动异常</b>,只 log warn。
 * 原因:搜索功能挂了商城还能用(降级回 MySQL),不能让索引同步问题把整个应用拖死。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestIndexInitializer {

    private final ElasticsearchClient esClient;
    private final ElasticsearchConfig.Properties esProps;
    private final BookMapper bookMapper;

    /**
     * 启动后触发 —— {@code @PostConstruct} 太早(Spring 上下文还没装完),
     * {@link ApplicationReadyEvent} 才是稳的。
     */
    /**
     * 总开关 —— 关掉 ES 时<b>完全不碰 ES</b>(不建索引、不同步)。
     *
     * <p>不这么做的话,关了开关启动仍会去连 ES:ES 没起时启动日志会多一条
     * 无意义的告警,而"关掉"本身就该意味着"别碰它"。</p>
     */
    @Value("${app.search.elasticsearch.enabled:true}")
    private boolean esEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        if (!esEnabled) {
            log.info("Elasticsearch 已通过配置关闭(app.search.elasticsearch.enabled=false),跳过索引初始化");
            return;
        }
        try {
            ensureIndex();
            fullSyncFromMysql();
        } catch (Exception e) {
            // 兜底:即便 ensureIndex 抛了,fullSync 也跑一下让数据库侧数据能进 ES
            log.warn("ES 搜索建议索引初始化遇到问题,搜索功能将降级回 MySQL 实现: {}", e.getMessage(), e);
        }
    }

    /**
     * index JSON —— settings + mappings 一次发给 {@code createIndex}.
     *
     * <p>ES 9.x 在 client 端对 {@code createIndex} body 反序列化更严:
     * {@code number_of_shards} / {@code number_of_replicas} 必须在 settings 子对象里,
     * 写在顶层 body 会被拒(JSON 路径 "number_of_shards" 报 unknown field)。</p>
     *
     * <h2>中文分词:IK(需要单独装插件)</h2>
     * <p>默认 ES 镜像<b>没有</b>中文分词器,用 standard 分析器会把中文切成单字
     * (「深入理解」→ 深/入/理/解),单字匹配会带来大量假阳性
     * (「设计」命中「计算机」—— 因为有个「计」字)。</p>
     *
     * <p>这里用 IK 的 <b>ik_max_word 建索引 / ik_smart 查询</b> 这对组合:
     * 索引侧尽量多切(提高召回),查询侧粗粒度切(提高精度)。</p>
     *
     * <p><b>⚠️ 部署前提</b>:IK 必须和 ES <b>版本严格一致</b>,否则 ES 在安装阶段
     * 就抛 {@code IllegalArgumentException}(PluginsUtils.verifyCompatibility)。
     * 本项目 ES 9.5.4 + IK 9.5.3 是"改描述文件版本号"硬装的,见
     * {@code scripts/install-ik.sh}。升级 ES 时必须同步换 IK。</p>
     */
    private static final String SUGGEST_INDEX_JSON = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "title": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": { "kw": { "type": "keyword", "ignore_above": 200 } }
                  },
                  "author": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": { "kw": { "type": "keyword", "ignore_above": 100 } }
                  },
                  "isbn": { "type": "keyword" },
                  "hot": { "type": "integer" }
                }
              }
            }
            """;

    void ensureIndex() throws IOException {
        String index = esProps.getSuggestIndex();
        boolean exists = esClient.indices().exists(ExistsRequest.of(b -> b.index(index))).value();
        if (exists) {
            log.info("ES 索引已存在,跳过 create: {}", index);
            return;
        }
        esClient.indices().create(b -> b
                .index(index)
                .withJson(new StringReader(SUGGEST_INDEX_JSON)));
        log.info("ES 索引已创建: {}", index);
    }

    /**
     * 全量从 MySQL 同步 —— ES 9.x bulk API,每批 100 本。
     *
     * <p>演示数据 12 本,一批就够。图书量大时要切到 Scroll API 分批,
     * 现在的 100 本/批是个合理的起点。</p>
     */
    void fullSyncFromMysql() throws IOException {
        String index = esProps.getSuggestIndex();

        int total = bookMapper.countBooks();
        if (total == 0) {
            log.info("MySQL 没有图书,ES 不需要 sync");
            return;
        }

        // 热度:每本书的已支付累计销量。跟候选词的排序口径一致。
        // 缺失(没有成交)的按 0 —— 不是 null,ES 的 integer 字段收 null 也能存,
        // 但排序时要处理 null,不如源头统一成 0。
        Map<Integer, Integer> salesMap = loadSalesCount();

        // 分批拉,避免一次 selectList(Integer.MAX_VALUE) 把 JVM 内存打爆
        // (现在 books 表只有 12 行,但接口要按"全量 sync"的标准写法走)
        final int batch = 200;
        long totalErrors = 0;
        int totalSynced = 0;
        for (int offset = 0; offset < total; offset += batch) {
            List<Book> page = bookMapper.selectList(offset, batch);
            if (page.isEmpty()) break;

            List<co.elastic.clients.elasticsearch.core.bulk.BulkOperation> ops = new ArrayList<>(page.size());
            for (Book bk : page) {
                String isbn = bk.getIsbn();
                // BulkOperation.Builder.index(fn) 的 fn 收 IndexOperation.Builder<TDocument>;
                // 后者继承自 BulkOperationBase.AbstractBuilder,.index(String)/.id(String) 都在那。
                Map<String, Object> doc = toDoc(bk, salesMap.getOrDefault(bk.getId(), 0));
                ops.add(co.elastic.clients.elasticsearch.core.bulk.BulkOperation.of(io ->
                    io.<Map<String, Object>>index(idx -> idx
                        .index(index)
                        .id(isbn)
                        .document(doc))));
            }
            var bulk = esClient.bulk(b -> b.operations(ops));
            int pageErrors = bulk.errors() ? (int) bulk.items().stream().filter(i -> i.error() != null).count() : 0;
            totalErrors += pageErrors;
            totalSynced += page.size();
            log.info("ES bulk page: offset={}  size={}  took={}ms  errors={}",
                    offset, page.size(), bulk.took(), pageErrors);
        }
        log.info("ES 全量同步完成: synced={} errors={}", totalSynced, totalErrors);
        if (totalErrors > 0) {
            log.warn("⚠️ ES 同步有失败条目,搜索可能不完整;下次启动会再次同步");
        }

        // 强制刷新一次 —— 否则立刻查可能看不到(默认 refresh_interval=1s,
        // 启动时刚 init,前一秒查到的概率不低,但刷新一次保稳定)
        esClient.indices().refresh(b -> b.index(index));
    }

    /** ES 文档 = 候选词所需的最少字段;id 用 isbn(主键、唯一) */
    private static Map<String, Object> toDoc(Book bk, int hot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", bk.getTitle());
        m.put("author", bk.getAuthor());
        m.put("isbn", bk.getIsbn());
        m.put("hot", hot);
        return m;
    }

    /** bookId → 已支付累计销量。缺失的按 0 处理(调用方 getOrDefault) */
    private Map<Integer, Integer> loadSalesCount() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Map<String, Object> row : bookMapper.selectSalesCountByBook()) {
            Object id = row.get("bookId");
            Object sold = row.get("sold");
            if (id instanceof Number n && sold instanceof Number s) {
                map.put(n.intValue(), s.intValue());
            }
        }
        return map;
    }
}
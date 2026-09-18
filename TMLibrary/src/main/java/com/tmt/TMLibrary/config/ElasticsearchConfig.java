package com.tmt.TMLibrary.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.ElasticsearchTransportConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 客户端 Bean —— 单一 {@link ElasticsearchClient} 实例,所有搜索组件复用。
 *
 * <h2>为什么不用 Spring Boot 的 spring.elasticsearch.* 自动配置</h2>
 * <p>走 transport-client(传统节点协议),ES 7 后已 deprecated,9.x 集群直接拒连。
 * 现代官方客户端是 {@link ElasticsearchClient}(9.x 取代了 HLRC)。</p>
 *
 * <h2>9.x 的 HTTP 客户端</h2>
 * <p>elasticsearch-java 9.x 走 <b>rest5-client</b>(基于 Apache HttpClient 5,
 * 包名前缀 {@code org.apache.hc.*5.*} —— 和老的 {@code org.apache.http} 不一样)。
 * 老的 rest-client 模块在 9.x <b>已不再发布</b> —— 直接锁 9.5.4 的话,Maven 上
 * 能拿到的只有 rest5-client 这一条路。</p>
 *
 * <h2>用 ElasticsearchTransportConfig$Builder 而不是 Rest5ClientBuilder</h2>
 * <p>{@code Rest5ClientBuilder} 在 9.5.4 里<b>是 package-private</b> —— 它是 low-level 细节,
 * 不该让业务层直接 new。而 {@code ElasticsearchTransportConfig$Builder} 是高层、
 * public、稳定 API:传 hosts(URI 列表)+ 账号 + JsonpMapper,内部自动选 rest5。</p>
 *
 * <h2>多 host + 超时</h2>
 * <p>逗号分隔 host,底层做 round-robin + 失败节点剔除。
 * 连接/请求超时通过 {@link org.apache.hc.client5.http.config.RequestConfig} 设
 * (rest5-client 内部用 HC5 全局默认值;要更细的超时走自定义 transport 配置,
 * 留作下一轮)。</p>
 */
@Configuration
public class ElasticsearchConfig {

    @ConfigurationProperties(prefix = "app.search.elasticsearch")
    public static class Properties {
        private String hosts;
        private String username;
        private String password;
        private String suggestIndex;
        private String bookIndex;
        private int connectTimeoutMs = 1000;
        private int socketTimeoutMs = 2000;

        public String getHosts() { return hosts; }
        public void setHosts(String hosts) { this.hosts = hosts; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSuggestIndex() { return suggestIndex; }
        public void setSuggestIndex(String suggestIndex) { this.suggestIndex = suggestIndex; }
        public String getBookIndex() { return bookIndex; }
        public void setBookIndex(String bookIndex) { this.bookIndex = bookIndex; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getSocketTimeoutMs() { return socketTimeoutMs; }
        public void setSocketTimeoutMs(int socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }
    }

    @Bean
    @ConfigurationProperties(prefix = "app.search.elasticsearch")
    public Properties esProperties() {
        return new Properties();
    }

    /**
     * ElasticsearchClient —— Spring 容器里需要 ES 的地方直接 @Autowired。
     *
     * <p>{@code ElasticsearchTransportConfig$Builder} 已经把 hosts/认证/JsonpMapper
     * 一并配好,内部 {@code buildTransport()} 自动选 rest5 实现。
     * 公开构造路径,9.x 文档主推这条路。</p>
     */
    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient(Properties props) {
        List<URI> hosts = parseHosts(props.getHosts());

        ElasticsearchTransportConfig cfg = new ElasticsearchTransportConfig.Builder()
                .hosts(hosts)
                .usernameAndPassword(props.getUsername(),
                        props.getPassword() == null ? "" : props.getPassword())
                .jsonMapper(new JacksonJsonpMapper())
                .build();

        ElasticsearchTransport transport = cfg.buildTransport();
        return new ElasticsearchClient(transport);
    }

    /** "http://a:9200,https://b:9200" → List&lt;URI&gt; */
    private static List<URI> parseHosts(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of(URI.create("http://localhost:9200"));
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(URI::create)
                .collect(Collectors.toList());
    }
}
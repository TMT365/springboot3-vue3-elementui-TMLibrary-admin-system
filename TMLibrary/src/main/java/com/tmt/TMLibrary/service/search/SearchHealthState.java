package com.tmt.TMLibrary.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 可用性的<b>单一事实来源</b>。
 *
 * <p>搜索建议的自动降级(无 ES → MySQL)一直是有的,但原来那个降级是<b>一次性的</b>:
 * 启动时 ping 一次 ES,连不上就把开关改死成 false,之后再也不重试 —— ES 容器中途
 * 拉起来也不会切回,得整进程重启才生效。本类把"ES 是否可用"变成<b>动态状态</b>:</p>
 *
 * <ul>
 *   <li>启动时 {@link #probeAtStartup()} 探一次,定下初始可用性;</li>
 *   <li>{@link #probeRecovery()} 每 30s 探一次:
 *       <ul>
 *         <li>当前<b>不可用</b> → 探通就翻回可用(ES 恢复后自动切回,不用重启);</li>
 *         <li>当前<b>可用</b> → 探挂就翻成不可用(ES 中途失联也兜得住)。</li>
 *       </ul></li>
 *   <li>只读方(降级包装器、索引初始化器)各自问 {@link #isEsHealthy()}。</li>
 * </ul>
 *
 * <h2>关闭开关时</h2>
 * <p>{@code app.search.elasticsearch.enabled=false} 表示"明确别碰 ES":
 * 启动不探、定时不探、{@link #isEsHealthy()} 恒 false → 搜索一路走 MySQL,
 * 完全不发任何 ES 请求。</p>
 *
 * <h2>为什么是 ping 而不是建连</h2>
 * <p>ES 客户端是惰性建连,不 ping 的话一次真实的搜索请求才会暴露失联。
 * 显式 ping 让"失联"在几秒内就被发现,而不是等到用户在下拉框里第一次输入。
 * ping 自带 1s 连接超时(见 application.yml connect-timeout-ms),这里不再加预算。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHealthState {

    private final ElasticsearchClient esClient;

    /** 总开关 —— 关掉时完全不碰 ES(见 javadoc)。 */
    @Value("${app.search.elasticsearch.enabled:true}")
    private boolean esEnabled;

    /** 当前是否判定 ES 可用。volatile:被调度线程写、被请求线程读。 */
    private volatile boolean esHealthy;

    /**
     * 启动期探一次 —— 不阻塞应用启动:ES 挂了应用照常起,只是搜索走 MySQL。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void probeAtStartup() {
        if (!esEnabled) {
            esHealthy = false;
            log.info("Elasticsearch 已通过配置关闭(app.search.elasticsearch.enabled=false),搜索建议走 MySQL");
            return;
        }
        esHealthy = probe();
        if (esHealthy) {
            log.info("✅ ES 健康检查通过 —— 搜索建议走 Elasticsearch");
        } else {
            log.warn("⚠️ ES 暂不可用 —— 搜索建议先降级回 MySQL 实现,每 30s 自动重试恢复");
        }
    }

    /**
     * 周期探活 —— 只在<b>状态翻转</b>时打日志,不刷屏。
     */
    @Scheduled(initialDelay = 35_000, fixedDelay = 30_000)
    public void probeRecovery() {
        if (!esEnabled) {
            return;
        }
        boolean ok = probe();
        if (ok == esHealthy) {
            return; // 状态没变,不打扰
        }
        if (ok) {
            esHealthy = true;
            log.info("✅ ES 已恢复 —— 搜索建议切回 Elasticsearch");
        } else {
            esHealthy = false;
            log.warn("⚠️ ES 失联 —— 搜索建议降级回 MySQL 实现,恢复后自动切回");
        }
    }

    /** 只读方问 ES 当前是否可用。 */
    public boolean isEsHealthy() {
        return esHealthy;
    }

    /**
     * 一次真实的 ES 查询失败比定时探活更"硬"的证据 —— 由降级包装器调用,
     * 当场把状态翻成降级,后续请求直接走 MySQL;恢复交给 {@link #probeRecovery()}。
     */
    public void markUnhealthy() {
        if (esHealthy) {
            esHealthy = false;
            log.warn("ES 候选词查询失败 —— 标记降级,恢复后自动切回");
        }
    }

    /** 单次 ping —— 任何异常(连不上/超时/401)一律视为不可用。 */
    private boolean probe() {
        try {
            return esClient.ping().value();
        } catch (Exception e) {
            return false;
        }
    }
}
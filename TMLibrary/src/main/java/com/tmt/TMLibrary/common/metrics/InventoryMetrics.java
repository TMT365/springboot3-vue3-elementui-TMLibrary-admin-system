package com.tmt.TMLibrary.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>库存与订单一致性指标</h1>
 *
 * <p>这些计数器衡量的是"系统自愈能力是否被触发",数值持续增长即代表存在异常路径。</p>
 *
 * <table border="1">
 *   <tr><th>指标</th><th>含义</th><th>处置</th></tr>
 *   <tr>
 *     <td>{@code tmlibrary_inventory_drift_total}</td>
 *     <td>Lua 脚本返回负值(hash 丢失 / reserved 不足),Redis 库存已偏离真值</td>
 *     <td>查 {@code INVENTORY DRIFT} 日志定位根因;对账任务会修复数值</td>
 *   </tr>
 *   <tr>
 *     <td>{@code tmlibrary_inventory_reconcile_repaired_total}</td>
 *     <td>对账任务发现并修复了不一致</td>
 *     <td>若持续增长,说明漂移在反复发生,需定位来源而非依赖对账</td>
 *   </tr>
 *   <tr>
 *     <td>{@code tmlibrary_order_compensate_failed_total}</td>
 *     <td>下单失败后回滚预占也失败 —— <b>需要人工对账</b></td>
 *     <td>任何非零值都应告警</td>
 *   </tr>
 * </table>
 *
 * <p>通过 {@code GET /actuator/metrics/{name}} 读取。</p>
 */
@Component
public class InventoryMetrics {

    private static final String DRIFT = "tmlibrary_inventory_drift_total";
    private static final String RECONCILE_REPAIRED = "tmlibrary_inventory_reconcile_repaired_total";
    private static final String COMPENSATE_FAILED = "tmlibrary_order_compensate_failed_total";

    private final MeterRegistry registry;
    /** Counter 按 tag 缓存 — 避免每次打点都重建 meter(建 meter 有锁开销) */
    private final ConcurrentHashMap<String, Counter> cache = new ConcurrentHashMap<>();

    public InventoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次库存漂移。
     *
     * @param source 触发点:release / confirm / reconcile
     * @param bookId 图书 ID
     */
    public void driftDetected(String source, int bookId) {
        counter(DRIFT, "source", source, "bookId", String.valueOf(bookId)).increment();
    }

    /**
     * 记录一次对账修复。
     *
     * @param bookId 图书 ID
     */
    public void reconcileRepaired(int bookId) {
        counter(RECONCILE_REPAIRED, "bookId", String.valueOf(bookId)).increment();
    }

    /**
     * 记录一次订单补偿(回滚预占)失败 —— 需要人工介入。
     *
     * @param bookId 图书 ID
     */
    public void orderCompensateFailed(int bookId) {
        counter(COMPENSATE_FAILED, "bookId", String.valueOf(bookId)).increment();
    }

    private Counter counter(String name, String... tags) {
        String key = name + String.join("|", tags);
        return cache.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(name);
            for (int i = 0; i + 1 < tags.length; i += 2) {
                builder.tag(tags[i], tags[i + 1]);
            }
            return builder.register(registry);
        });
    }
}

package com.tmt.TMLibrary.common.utils;

import org.springframework.data.redis.core.types.Expiration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 带随机抖动的 TTL —— 防止大量 key 在同一时刻集中过期(缓存雪崩)。
 *
 * <h2>单位换算的坑(历史 bug)</h2>
 * <p>旧实现先把值换算成秒,却仍用<b>原来的单位</b>去构造 {@link Expiration}:</p>
 *
 * <pre>
 *   time = MINUTES.toSeconds(3);              // 180
 *   Expiration.from(180, TimeUnit.MINUTES);   // ← 180 分钟 = 3 小时!
 * </pre>
 *
 * <p>结果所有非秒级调用方的 TTL 被放大 60 倍(实测 {@code get(3, MINUTES)} 得到
 * 10,800,000 ms)。正确做法是换算与构造使用<b>同一单位</b>。</p>
 *
 * <p>现在统一换算为秒后构造,调用方无论传 SECONDS 还是 MINUTES 都得到预期时长。</p>
 */
public class RandomExpirationTimeWithOffset {

    /** 抖动上限(秒)—— 在基础 TTL 之上随机增加 0~299 秒 */
    private static final int MAX_JITTER_SECONDS = 300;

    private static final Random RAND = new Random();

    /**
     * 构造带随机抖动的过期时间。
     *
     * @param time     基础时长(必须 &gt; 0)
     * @param timeUnit 单位;为 {@code null} 时按秒处理
     * @return 过期时间 = 基础时长 + 0~299 秒随机抖动
     */
    public static Expiration get(long time, TimeUnit timeUnit) {
        if (time <= 0) {
            throw new IllegalArgumentException("expiration time must be greater than zero");
        }

        TimeUnit unit = (timeUnit == null) ? TimeUnit.SECONDS : timeUnit;

        // 关键:换算与构造必须用同一单位,否则 TTL 会被放大/缩小
        long baseSeconds = unit.toSeconds(time);
        if (baseSeconds <= 0) {
            // 亚秒级入参(如 500 MILLISECONDS)会被截断为 0,按 1 秒兜底
            baseSeconds = 1;
        }
        long ttlSeconds = baseSeconds + RAND.nextInt(MAX_JITTER_SECONDS);

        return Expiration.from(ttlSeconds, TimeUnit.SECONDS);
    }
}

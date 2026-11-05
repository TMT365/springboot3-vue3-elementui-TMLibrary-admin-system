package com.tmt.TMLibrary.Author;

import com.tmt.TMLibrary.entity.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis 冒烟测试：用 {@link StringRedisTemplate} + Jackson 手写序列化，验证
 * "对象 → JSON 字符串 → Redis → JSON 字符串 → 对象" 的往返在真实 Redis 上成立。
 *
 * <p><b>为什么不用 {@code RedisTemplate<String, Object>}：</b>
 * 那个 bean 在 {@code RedisConfig} 里已经注释掉了（现在只提供 StringRedisTemplate）。
 * 早期版本的本测试直接注入它，因此上下文永远起不来 —— 是个长期红的测试。
 * 这里改用项目实际在用的 {@code StringRedisTemplate}，并补上真正的断言。</p>
 *
 * <p>这是**集成**测试，需要真实 MySQL / Redis：环境变量没导出时跳过。
 * 跑法：{@code set -a; . ./.env; set +a; ./mvnw test}</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
public class RedisTest {

    private static final String KEY = "RedisTest:order2";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void jsonRoundTripThroughRedis() {
        Order order = new Order();
        order.setOrderStatus(1);
        order.setOrderNumber(19999999999990L);
        order.setTotalAmount(BigDecimal.valueOf(200));

        ObjectMapper objectMapper = new ObjectMapper();

        // 存：对象 → JSON
        String json = objectMapper.writeValueAsString(order);
        stringRedisTemplate.opsForValue().set(KEY, json);

        try {
            // 取：JSON → 对象
            String jsonStr = stringRedisTemplate.opsForValue().get(KEY);
            assertNotNull(jsonStr, "Redis 里应能读回刚写入的 JSON");

            Order restored = objectMapper.readValue(jsonStr, Order.class);
            assertEquals(order.getOrderNumber(), restored.getOrderNumber());
            assertEquals(order.getOrderStatus(), restored.getOrderStatus());
            assertEquals(0, order.getTotalAmount().compareTo(restored.getTotalAmount()));
        } finally {
            // 测试数据不留在库里
            stringRedisTemplate.delete(KEY);
        }
    }
}
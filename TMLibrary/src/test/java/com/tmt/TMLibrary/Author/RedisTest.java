package com.tmt.TMLibrary.Author;

import com.tmt.TMLibrary.entity.Order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.lang.runtime.ObjectMethods;
import java.math.BigDecimal;

@SpringBootTest
public class RedisTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Order order;

    @Test
    public void test1() {

//        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
//        valueOperations.set("RedisTest:test2", "使用String序列化器 hello");
//        System.out.println(valueOperations.get("RedisTest:test2"));

        order = new Order();
        order.setOrderStatus(1);
        order.setOrderNumber(19999999999990L);
        order.setTotalAmount(BigDecimal.valueOf(200));
        // 新版的使用GenericJackJson...需要使用ObjectMapper对象，不如直接使用JacksonJson...直接指定一个序列化器
        // 存入对象类型
//        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
//        valueOperations.set("RedisTest:order1", order);
//        order = (Order) valueOperations.get("RedisTest:order1");

        // 手动实现序列化器
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(order);
        redisTemplate.opsForValue().set("RedisTest:order2", json);

        String jsonStr = (String) redisTemplate.opsForValue().get("RedisTest:order2");
        order = objectMapper.readValue(jsonStr, Order.class);
        System.out.println(jsonStr);

        // 直接使用 stringRedisTemplate 就不要配置什么序列化器了

        System.out.println("=======================================================================");
        System.out.println(order);
    }
}

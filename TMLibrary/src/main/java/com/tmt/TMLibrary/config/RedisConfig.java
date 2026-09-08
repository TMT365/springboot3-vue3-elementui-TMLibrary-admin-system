package com.tmt.TMLibrary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

//    @Bean
//    public RedisConnectionFactory redisConnectionFactory() {
//        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
//        config.setHostName("localhost");
//        config.setPort(6379);
//        config.setUsername("admin");
//        config.setPassword(RedisPassword.of("w15274695856w"));
//        config.setDatabase(0);
//        return new LettuceConnectionFactory();
//    }

    // 第一种方法
//    @Bean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
//
//        // 创建RedisTemplate对象
//        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
//        // 设置连接工厂
//        redisTemplate.setConnectionFactory(redisConnectionFactory);
//        // 设置key序列化器
//        redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
//        redisTemplate.setHashKeySerializer(StringRedisSerializer.UTF_8);
//        // 设置value序列化器
//        redisTemplate.setHashValueSerializer(StringRedisSerializer.UTF_8);
//        redisTemplate.setValueSerializer(StringRedisSerializer.UTF_8);
//
//
//        // 使用jacksonJson序列化器会存储""
//        redisTemplate.setValueSerializer( new JacksonJsonRedisSerializer<Order>(Order.class));
//        // 通用jacksonJson序列化器
//        redisTemplate.setValueSerializer( new GenericJacksonJsonRedisSerializer(objectMapper) );
//        redisTemplate.afterPropertiesSet();
//        return redisTemplate;
//    }

    // 第二种方法 StringRedisTemplate
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    // 自己手动设置映射器
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

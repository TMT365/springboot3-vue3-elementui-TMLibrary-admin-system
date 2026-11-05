package com.tmt.TMLibrary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：整棵 Spring 上下文能否装配起来（数据源、Redis、MyBatis、调度器等）。
 *
 * <p>这是**集成**测试 —— 需要真实 MySQL / Redis，连接信息来自环境变量
 * （{@code scripts/dev.sh} 会从 {@code .env} 导出）。环境变量没导出时直接**跳过**，
 * 而不是抛 "Failed to determine suitable jdbc url" 让整个 {@code ./mvnw test} 变红。</p>
 *
 * <p>跑法：{@code set -a; . ./.env; set +a; ./mvnw test}</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class TMLibraryApplicationTests {

    @Test
    void contextLoads() {
        // 上下文装配成功即通过，无需额外断言。
    }
}
package com.tmt.TMLibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // 启用 @Scheduled 定时任务（OrderExpireScanner）
public class TmLibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TmLibraryApplication.class, args);
    }
}

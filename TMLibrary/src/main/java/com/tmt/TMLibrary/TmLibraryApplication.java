package com.tmt.TMLibrary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// @MapperScan("com.tmt.TMLibrary.mapper") // 扫描mapper接口所在的包
// 使用这个注解后，Spring Boot会自动扫描指定包下的所有Mapper接口，并为它们创建代理对象，从而实现与数据库的交互。这样，你就不需要在每个Mapper接口上单独添加@Mapper注解了。
public class TmLibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TmLibraryApplication.class, args);
    }
}

package com.tmt.TMLibrary.entity;
// 这是一个简单的Book实体类，包含id、name、author和isbn属性，以及相应的getter和setter方法。
// 就是定义了数据库中的Book表的结构和属性。
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Book {

    // Lombok @Data 自动生成 getter/setter。纯 MyBatis 不需要 MyBatis-Plus 的 @TableName 这些注解。
    private int id;
    private String title;
    private String author;
    private String isbn;
    private BigDecimal price;
    private LocalDate publishedDate;
    private LocalDateTime createdTime;  // 记录创建时间
    private LocalDateTime updatedTime;  // 记录更新时间
    private Integer stockQuantity;
    // Getters and setters

    //@Data 注解会自动生成所有字段的 getter 和 setter 方法，以及 equals、hashCode 和 toString 方法。你可以根据需要添加其他方法或注解。
}
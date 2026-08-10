/*
 * 校验注解用 jakarta.validation.*：
 * 
 * - @NotBlank 校验 String（非 null + 非空）
 * - @NotNull 校验 BigDecimal / Integer / LocalDateTime
 * - @DecimalMin 校验 BigDecimal 最小值
 * - @Min 校验 Integer 最小值
 * - @Pattern 正则校验 ISBN 格式
 * 
 * 注意包名是 jakarta.validation，不是 javax.validation（Spring Boot 4.x 用 jakarta 命名空间）。
 */

// dto层||DTO 是 Data Transfer Object（数据传输对象），专门用来在两层之间搬数据的
package com.tmt.TMLibrary.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class BookSaveRequest {
    
    @NotBlank(message = "书名不能为空")
    @Size(max = 200, message = "书名长度不能超过 200")
    private String title;

    @NotBlank(message = "作者不能为空")
    @Size(max = 100, message = "作者长度不能超过 100")
    private String author;

    @NotBlank(message = "ISBN 不能为空")
    @Pattern(regexp = "^[0-9Xx-]{10,20}$", message = "ISBN 格式不正确")
    private String isbn;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能小于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    private Integer stockQuantity;

    @NotNull(message = "出版日期不能为空")
    private LocalDate publishedDate;
}

/*
 * `@NotBlank` 校验字符串非 null 且非空,**`@NotNull` 不校验空字符串**,所以校验字符串用 `@NotBlank`
 * `@NotNull` 校验 BigDecimal / Integer / Long 等数值类型
 * `@Pattern` 用正则校验 ISBN 格式,`[0-9Xx-]{10,20}` 允许数字、X、横杠、10-20 位
 *  触发校验失败需要 controller 上加 `@Valid`,异常会被 GlobalExceptionHandler 接住
 */
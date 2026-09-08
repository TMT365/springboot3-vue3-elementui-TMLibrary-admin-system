package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class BookUpdateRequest {

    //@NotBlank(message = "goodNos不能为空")
    //private String goodNos; // 这个可以作为对外暴露的商品ID，不泄露数据库里面的自增ID

    @Size(max = 200, message = "书名长度不能超过 200")
    private String title;


    @Size(max = 100, message = "作者长度不能超过 100")
    private String author;

    @DecimalMin(value = "0.00", message = "价格不能小于 0")
    private BigDecimal price;


    @Min(value = 0, message = "库存不能小于 0")
    private Integer stockQuantity;

    private LocalDate createdDate;

    private LocalDate publishedDate;

}

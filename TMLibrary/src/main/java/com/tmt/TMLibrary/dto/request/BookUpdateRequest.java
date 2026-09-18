package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.Size;
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


    // 库存不在此接口调整 —— 交易链路(下单/取消/付款)会持续改动可用库存,
    // 管理端直接 SET 会与在途预占打架。调整库存请用 PATCH /books/{isbn}/stock

    private LocalDate createdDate;

    private LocalDate publishedDate;

    /** 图书分类 id —— 传了就改分类(Service 会同步新旧两个分类的计数),不传保持原样 */
    private Integer categoryId;

}

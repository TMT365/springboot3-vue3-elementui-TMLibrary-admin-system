package com.tmt.TMLibrary.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @brief GET /api/books 多条件搜索的入参 DTO
 *
 * 所有筛选字段都是包装类型(可为 null),null = "该条件不参与"。
 * 由 Spring 用 {@code @ModelAttribute} 从 query 参数自动绑定。
 *
 * 归一化集中在 {@link #compact()}:空串 → null、负数 → null、
 * page ≤ 0 → 1、size ≤ 0 → 10、size > 100 → 100(防 DoS)。
 *
 * 为什么归一化放 DTO 不放 Service:
 *  1. 单一职责 — DTO 管"自己长什么样",Service 只管"调 Mapper"
 *  2. 可单测 — 直接 new 出来调 compact() 即可,无需 Spring 上下文
 */
@Data
public class BookSearchRequest {

    private String title;
    private String author;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minStock;
    private Integer maxStock;
    private LocalDate publishedDate;
    private Integer page;
    private Integer size;

    /**
     * @brief 归一化:空串→null、负数→null、page/size 兜底、size 上限 100。
     *        幂等 — 多次调用结果相同。
     * @return this(链式友好)
     */
    public BookSearchRequest compact() {
        this.title = blankToNull(this.title);
        this.author = blankToNull(this.author);
        this.minPrice = (minPrice == null || minPrice.signum() < 0) ? null : minPrice;
        this.maxPrice = (maxPrice == null || maxPrice.signum() < 0) ? null : maxPrice;
        this.minStock = (minStock == null || minStock < 0) ? null : minStock;
        this.maxStock = (maxStock == null || maxStock < 0) ? null : maxStock;
        this.page = (page == null || page <= 0) ? 1 : page;
        this.size = (size == null || size <= 0) ? 10 : Math.min(size, 100);
        return this;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}

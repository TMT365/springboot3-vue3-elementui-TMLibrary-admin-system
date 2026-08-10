package com.tmt.TMLibrary.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @brief BookSearchRequest.compact() 单测 — 验证 DTO 归一化逻辑
 *
 * 覆盖:
 *   - 空串 / 纯空白 → null
 *   - 负数 → null
 *   - page ≤ 0 → 1
 *   - size ≤ 0 → 10
 *   - size > 100 → 100(防 DoS)
 *   - 正常值保持不变
 *   - 多次调用幂等
 */
@DisplayName("BookSearchRequest.compact() 归一化测试")
class BookSearchRequestTest {

    @Nested
    @DisplayName("字符串字段归一化")
    class StringNormalization {

        @Test
        @DisplayName("title 为空串 → null")
        void title_blankString_becomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("");

            req.compact();

            assertThat(req.getTitle()).isNull();
        }

        @Test
        @DisplayName("title 为纯空白 → null")
        void title_whitespace_becomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("   \t  ");

            req.compact();

            assertThat(req.getTitle()).isNull();
        }

        @Test
        @DisplayName("title 首尾空白被 trim")
        void title_surroundingSpaces_trimmed() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("  Java  ");

            req.compact();

            assertThat(req.getTitle()).isEqualTo("Java");
        }

        @Test
        @DisplayName("title 正常值原样保留")
        void title_normalValue_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("Effective Java");

            req.compact();

            assertThat(req.getTitle()).isEqualTo("Effective Java");
        }

        @Test
        @DisplayName("title=null 不抛 NPE")
        void title_nullSafe() {
            BookSearchRequest req = new BookSearchRequest();

            req.compact();

            assertThat(req.getTitle()).isNull();
        }
    }

    @Nested
    @DisplayName("价格字段归一化")
    class PriceNormalization {

        @Test
        @DisplayName("minPrice=-1 → null,不参与 WHERE")
        void minPrice_negative_becomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMinPrice(new BigDecimal("-1"));

            req.compact();

            assertThat(req.getMinPrice()).isNull();
        }

        @Test
        @DisplayName("minPrice=0 保留(0 是合法值)")
        void minPrice_zero_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMinPrice(BigDecimal.ZERO);

            req.compact();

            assertThat(req.getMinPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("minPrice=10.50 保留")
        void minPrice_normalValue_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMinPrice(new BigDecimal("10.50"));

            req.compact();

            assertThat(req.getMinPrice()).isEqualByComparingTo(new BigDecimal("10.50"));
        }

        @Test
        @DisplayName("maxPrice=-0.01 → null")
        void maxPrice_negative_becomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMaxPrice(new BigDecimal("-0.01"));

            req.compact();

            assertThat(req.getMaxPrice()).isNull();
        }
    }

    @Nested
    @DisplayName("库存字段归一化")
    class StockNormalization {

        @Test
        @DisplayName("minStock=-5 → null")
        void minStock_negative_becomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMinStock(-5);

            req.compact();

            assertThat(req.getMinStock()).isNull();
        }

        @Test
        @DisplayName("maxStock=0 保留")
        void maxStock_zero_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMaxStock(0);

            req.compact();

            assertThat(req.getMaxStock()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("分页兜底")
    class PaginationFallback {

        @Test
        @DisplayName("page=null → 1")
        void page_null_fallbackToOne() {
            BookSearchRequest req = new BookSearchRequest();

            req.compact();

            assertThat(req.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("page=0 → 1")
        void page_zero_fallbackToOne() {
            BookSearchRequest req = new BookSearchRequest();
            req.setPage(0);

            req.compact();

            assertThat(req.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("page=-1 → 1")
        void page_negative_fallbackToOne() {
            BookSearchRequest req = new BookSearchRequest();
            req.setPage(-1);

            req.compact();

            assertThat(req.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("size=null → 10")
        void size_null_fallbackToTen() {
            BookSearchRequest req = new BookSearchRequest();

            req.compact();

            assertThat(req.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("size=0 → 10")
        void size_zero_fallbackToTen() {
            BookSearchRequest req = new BookSearchRequest();
            req.setSize(0);

            req.compact();

            assertThat(req.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("size=10000 → 100(防 DoS 上限)")
        void size_hugeValue_clampedToHundred() {
            BookSearchRequest req = new BookSearchRequest();
            req.setSize(10000);

            req.compact();

            assertThat(req.getSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("size=50 保留")
        void size_normalValue_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setSize(50);

            req.compact();

            assertThat(req.getSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("size=100 边界值保留")
        void size_boundary_preserved() {
            BookSearchRequest req = new BookSearchRequest();
            req.setSize(100);

            req.compact();

            assertThat(req.getSize()).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("compact() 多次调用幂等")
    void compact_idempotent() {
        BookSearchRequest req = new BookSearchRequest();
        req.setTitle("  Java  ");
        req.setPage(0);
        req.setSize(9999);

        req.compact();
        String titleAfterFirst = req.getTitle();
        int sizeAfterFirst = req.getSize();

        req.compact();
        req.compact();

        assertThat(req.getTitle()).isEqualTo(titleAfterFirst);
        assertThat(req.getSize()).isEqualTo(sizeAfterFirst);
        assertThat(req.getSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("compact() 返回 this(链式友好)")
    void compact_returnsSelf() {
        BookSearchRequest req = new BookSearchRequest();

        BookSearchRequest result = req.compact();

        assertThat(result).isSameAs(req);
    }
}

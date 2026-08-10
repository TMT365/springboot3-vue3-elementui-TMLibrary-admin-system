package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @brief BookPublishedDateByRequest.compact() 单测 — 粒度连续性校验
 *
 * 覆盖:
 *   - 负数/0 兜底为 null
 *   - 粒度连续性:有 month 必须有 year,有 day 必须有 month
 *   - 跳级 → 抛 BusinessException(BAD_REQUEST)
 *   - page/size 兜底同 BookSearchRequest
 */
@DisplayName("BookPublishedDateByRequest.compact() 粒度校验测试")
class BookPublishedDateByRequestTest {

    @Nested
    @DisplayName("负数 / 0 兜底")
    class NegativeFallback {

        @Test
        @DisplayName("year=-1 → null(让 @NotNull 接管给 400)")
        void year_negative_becomesNull() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(-1);

            req.compact();

            assertThat(req.getYear()).isNull();
        }

        @Test
        @DisplayName("month=0 → null")
        void month_zero_becomesNull() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(0);

            req.compact();

            assertThat(req.getMonth()).isNull();
        }

        @Test
        @DisplayName("day=-5 → null")
        void day_negative_becomesNull() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(-5);

            req.compact();

            assertThat(req.getDay()).isNull();
        }
    }

    @Nested
    @DisplayName("粒度连续性校验")
    class GranularityContinuity {

        @Test
        @DisplayName("year=2024 → 合法(年粒度)")
        void yearOnly_valid() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);

            req.compact();

            assertThat(req.getYear()).isEqualTo(2024);
            assertThat(req.getMonth()).isNull();
            assertThat(req.getDay()).isNull();
        }

        @Test
        @DisplayName("year=2024&month=6 → 合法(月粒度)")
        void yearMonth_valid() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(6);

            req.compact();

            assertThat(req.getYear()).isEqualTo(2024);
            assertThat(req.getMonth()).isEqualTo(6);
        }

        @Test
        @DisplayName("year=2024&month=6&day=15 → 合法(日粒度)")
        void yearMonthDay_valid() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);

            req.compact();

            assertThat(req.getYear()).isEqualTo(2024);
            assertThat(req.getMonth()).isEqualTo(6);
            assertThat(req.getDay()).isEqualTo(15);
        }

        @Test
        @DisplayName("month=6 缺 year → 抛 BusinessException(BAD_REQUEST)")
        void monthWithoutYear_throws() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setMonth(6);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("month");
        }

        @Test
        @DisplayName("year=2024&day=15 缺 month → 抛 BusinessException(BAD_REQUEST)")
        void dayWithoutMonth_throws() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setDay(15);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("day");
        }
    }

    @Nested
    @DisplayName("分页兜底")
    class PaginationFallback {

        @Test
        @DisplayName("page=null → 1,size=null → 10")
        void defaultsApplied() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);

            req.compact();

            assertThat(req.getPage()).isEqualTo(1);
            assertThat(req.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("size=500 → 100(上限)")
        void size_clamped() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setSize(500);

            req.compact();

            assertThat(req.getSize()).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("compact() 返回 this(链式)")
    void compact_returnsSelf() {
        BookPublishedDateByRequest req = new BookPublishedDateByRequest();
        req.setYear(2024);

        assertThat(req.compact()).isSameAs(req);
    }
}

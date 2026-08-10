package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @brief BookDateTimeByRequest.compact() 单测 — 5 级粒度连续性校验
 *
 * 覆盖:
 *   - 5 级合法粒度:year / +month / +day / +hour / +minute
 *   - 跳级场景:有 hour 缺 day,有 minute 缺 hour 等等
 *   - 负数/0 兜底
 *   - page/size 兜底
 */
@DisplayName("BookDateTimeByRequest.compact() 粒度校验测试")
class BookDateTimeByRequestTest {

    @Nested
    @DisplayName("5 级合法粒度")
    class ValidGranularity {

        @Test
        @DisplayName("year=2024 合法")
        void yearOnly_valid() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);

            req.compact();

            assertThat(req.getYear()).isEqualTo(2024);
        }

        @Test
        @DisplayName("year=2024&month=6 合法")
        void yearMonth_valid() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);

            req.compact();

            assertThat(req.getMonth()).isEqualTo(6);
        }

        @Test
        @DisplayName("year+month+day 合法")
        void yearMonthDay_valid() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);

            req.compact();

            assertThat(req.getDay()).isEqualTo(15);
        }

        @Test
        @DisplayName("year+month+day+hour 合法")
        void yearMonthDayHour_valid() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(10);

            req.compact();

            assertThat(req.getHour()).isEqualTo(10);
        }

        @Test
        @DisplayName("year+month+day+hour+minute 合法")
        void yearMonthDayHourMinute_valid() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(10);
            req.setMinute(30);

            req.compact();

            assertThat(req.getMinute()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("跳级异常")
    class GranularitySkip {

        @Test
        @DisplayName("month=6 缺 year → 抛 BusinessException(BAD_REQUEST)")
        void monthWithoutYear_throws() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setMonth(6);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("month");
        }

        @Test
        @DisplayName("year=2024&day=15 缺 month → 抛 BusinessException")
        void dayWithoutMonth_throws() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setDay(15);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("day");
        }

        @Test
        @DisplayName("year=2024&hour=10 缺 day → 抛 BusinessException")
        void hourWithoutDay_throws() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setHour(10);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("hour");
        }

        @Test
        @DisplayName("year+month+day+minute 缺 hour → 抛 BusinessException")
        void minuteWithoutHour_throws() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setMinute(30);

            assertThatThrownBy(req::compact)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BAD_REQUEST.getCode())
                .hasMessageContaining("minute");
        }
    }

    @Nested
    @DisplayName("负数兜底")
    class NegativeFallback {

        @Test
        @DisplayName("hour=-1 → null")
        void hour_negative_becomesNull() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(-1);

            req.compact();

            assertThat(req.getHour()).isNull();
        }

        @Test
        @DisplayName("minute=-1 → null")
        void minute_negative_becomesNull() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(10);
            req.setMinute(-1);

            req.compact();

            assertThat(req.getMinute()).isNull();
        }
    }

    @Nested
    @DisplayName("分页兜底")
    class PaginationFallback {

        @Test
        @DisplayName("page=0 → 1")
        void page_zero_fallbackToOne() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setPage(0);

            req.compact();

            assertThat(req.getPage()).isEqualTo(1);
        }

        @Test
        @DisplayName("size=200 → 100(上限)")
        void size_clamped() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setSize(200);

            req.compact();

            assertThat(req.getSize()).isEqualTo(100);
        }
    }
}

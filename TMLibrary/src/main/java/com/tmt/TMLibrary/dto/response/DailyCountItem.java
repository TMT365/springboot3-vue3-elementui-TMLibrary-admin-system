package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 「某天 + 数量」聚合行 —— 新增用户趋势用。
 * <p>MyBatis 通过 {@code mapUnderscoreToCamelCase} 把 {@code DATE(created_time)} 映射到 {@code date},
 * {@code COUNT(*)} 映射到 {@code count}。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountItem {
    /** 日期(无时区) */
    private LocalDate date;
    /** 当天数量 */
    private long count;
}

package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.impl.EsSearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.impl.SearchSuggestServiceImpl;
import com.tmt.TMLibrary.service.search.SearchHealthState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 无 ES 时的搜索建议降级路径 —— 覆盖 {@code SearchSuggestServiceConfig.FallbackSearchSuggestService}.
 *
 * <p>验证三类行为:</p>
 * <ul>
 *   <li>ES 健康 → 走 ES,不碰 MySQL;</li>
 *   <li>ES 查询抛异常 → 落回 MySQL,并当场把 ES 状态标成降级;</li>
 *   <li>ES 降级(或关闭)→ 直接走 MySQL,完全不撞 ES 连接。</li>
 * </ul>
 */
class SearchSuggestServiceConfigTest {

    private EsSearchSuggestServiceImpl es;
    private SearchSuggestServiceImpl mysql;
    private SearchHealthState health;
    private SearchSuggestServiceConfig.FallbackSearchSuggestService fallback;

    @BeforeEach
    void setUp() {
        es = mock(EsSearchSuggestServiceImpl.class);
        mysql = mock(SearchSuggestServiceImpl.class);
        health = mock(SearchHealthState.class);
        fallback = new SearchSuggestServiceConfig.FallbackSearchSuggestService(es, mysql, health);
    }

    private static BookSuggestion s(String type, String text) {
        BookSuggestion b = new BookSuggestion();
        b.setType(type);
        b.setText(text);
        return b;
    }

    @Test
    void esHealthy_usesEsNotMysql() {
        List<BookSuggestion> esResult = List.of(s("TITLE", "深入理解计算机系统"));
        when(health.isEsHealthy()).thenReturn(true);
        when(es.suggest("计算机", 8)).thenReturn(esResult);

        List<BookSuggestion> got = fallback.suggest("计算机", 8);

        assertThat(got).isEqualTo(esResult);
        verify(es, times(1)).suggest("计算机", 8);
        verifyNoInteractions(mysql);
        verify(health, never()).markUnhealthy();
    }

    @Test
    void esThrows_fallsBackToMysqlAndMarksDegraded() {
        List<BookSuggestion> mysqlResult = List.of(s("TITLE", "深入理解Java虚拟机"));
        when(health.isEsHealthy()).thenReturn(true);
        when(es.suggest(any(), anyInt())).thenThrow(new RuntimeException("ES down"));
        when(mysql.suggest("Java", 8)).thenReturn(mysqlResult);

        List<BookSuggestion> got = fallback.suggest("Java", 8);

        assertThat(got).isEqualTo(mysqlResult);
        verify(es, times(1)).suggest("Java", 8);
        verify(mysql, times(1)).suggest("Java", 8);
        verify(health, times(1)).markUnhealthy();
    }

    @Test
    void esDegraded_goesStraightToMysqlWithoutTouchingEs() {
        List<BookSuggestion> mysqlResult = List.of(s("AUTHOR", "周志明"));
        when(health.isEsHealthy()).thenReturn(false); // 已降级 / 关闭
        when(mysql.suggest("周", 8)).thenReturn(mysqlResult);

        List<BookSuggestion> got = fallback.suggest("周", 8);

        assertThat(got).isEqualTo(mysqlResult);
        verifyNoInteractions(es);
        verify(mysql, times(1)).suggest("周", 8);
        verify(health, never()).markUnhealthy();
    }

    @Test
    void esReturnsNull_fallsBackToMysqlButKeepsHealthy() {
        when(health.isEsHealthy()).thenReturn(true);
        when(es.suggest("空", 8)).thenReturn(null); // null 结果不算 ES 故障
        List<BookSuggestion> mysqlResult = List.of();
        when(mysql.suggest("空", 8)).thenReturn(mysqlResult);

        List<BookSuggestion> got = fallback.suggest("空", 8);

        assertThat(got).isEqualTo(mysqlResult);
        verify(health, never()).markUnhealthy();
    }
}
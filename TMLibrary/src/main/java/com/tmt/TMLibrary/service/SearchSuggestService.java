package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.BookSuggestion;

import java.util.List;

/**
 * 搜索候选词。
 *
 * <p><b>这是将来接 Elasticsearch 的那个接缝</b>:接口形状(入参 q/limit、
 * 出参 List&lt;BookSuggestion&gt;)保持不变,只换实现 ——
 * 现在由 {@code SearchSuggestServiceImpl} 走 MySQL LIKE。</p>
 */
public interface SearchSuggestService {

    /**
     * 按关键字取候选词,已按热度排序。
     *
     * @param q     用户已输入的内容(前后空格会被裁掉;空串直接返回空列表)
     * @param limit 最多返回几条,会被夹到 [1, {@value #MAX_LIMIT}]
     */
    List<BookSuggestion> suggest(String q, Integer limit);

    /** 单次返回的条数上限 —— 下拉列表超过这个数就得滚动了,不如让用户继续打字 */
    int MAX_LIMIT = 20;

    /** 不传 limit 时的默认条数 */
    int DEFAULT_LIMIT = 8;
}

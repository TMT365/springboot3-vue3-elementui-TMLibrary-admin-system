package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.dto.response.BookSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索候选词取数 —— 目前走 MySQL LIKE,后续切 Elasticsearch 时这个接口整体替换。
 *
 * <p>三条查询分开而不是 UNION:各类型的"热度"聚合口径不同(书名/ISBN 是单本书,
 * 作者要把名下所有书求和),拆开写 SQL 更好读,合并与排序放在 Service 里做。</p>
 */
@Mapper
public interface SearchSuggestMapper {

    /** 书名候选 —— 按书名模糊匹配 */
    List<BookSuggestion> selectTitleSuggestions(@Param("q") String q, @Param("limit") int limit);

    /** 作者候选 —— 按作者名模糊匹配,热度 = 该作者名下所有书的销量之和 */
    List<BookSuggestion> selectAuthorSuggestions(@Param("q") String q, @Param("limit") int limit);

    /** ISBN 候选 —— 只在输入看起来像 ISBN 时才查(见 Service 里的正则守卫) */
    List<BookSuggestion> selectIsbnSuggestions(@Param("q") String q, @Param("limit") int limit);
}

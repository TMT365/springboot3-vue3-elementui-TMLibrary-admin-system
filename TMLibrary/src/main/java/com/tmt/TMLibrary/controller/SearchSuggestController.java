package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.service.SearchSuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索候选词(下拉建议)。
 *
 * <p><b>免登录</b>:挂在 {@code /books} 前缀下,蹭 JwtAuthFilter 里
 * {@code ("/books", GET)} 那条白名单 —— 商城搜索框未登录也要能用。</p>
 *
 * <p>路径匹配:字面量段 {@code suggest} 优先于变量段 {@code {isbn}},
 * 所以不会和 {@code GET /books/{isbn}} 撞车(同 {@code /categories})。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/books/suggest")
@RequiredArgsConstructor
public class SearchSuggestController {

    private final SearchSuggestService searchSuggestService;

    /**
     * GET /books/suggest?q=计算&amp;limit=8
     *
     * @param q     已输入内容;空白直接返回空数组(不打库)
     * @param limit 条数上限,夹到 [1, 20],默认 8
     */
    @GetMapping
    public Result<List<BookSuggestion>> suggest(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", required = false) Integer limit) {
        long start = System.currentTimeMillis();
        List<BookSuggestion> suggestions = searchSuggestService.suggest(q, limit);
        // 候选词是高频接口(每次输入都打),留一行耗时便于观察 —— 换 ES 后对比效果
        log.debug("搜索候选 q={} limit={} → {} 条, {}ms",
                q, limit, suggestions.size(), System.currentTimeMillis() - start);
        return Result.success(suggestions);
    }
}

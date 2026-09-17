package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.dto.response.BookSuggestion;
import com.tmt.TMLibrary.mapper.SearchSuggestMapper;
import com.tmt.TMLibrary.service.SearchSuggestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 搜索候选词 —— 当前实现:MySQL LIKE 匹配 + 销量热度排序。
 *
 * <h2>为什么现在不用 Elasticsearch</h2>
 * <p>候选词查询的特征是"短、频、带 LIMIT",图书表在这个量级下 LIKE 完全够用。
 * 真正需要 ES 的是<b>相关性排序</b>(分词、拼音、错字容错、TF-IDF),
 * 那是下一步的事 —— 这个类就是那个替换点:换掉 {@link #collect} 里的取数,
 * 下面的排序/裁剪逻辑可以原样保留。</p>
 *
 * <h2>关于"个性化"</h2>
 * <p>暂未实现,原因是<b>接口是免登录的</b>:{@code /api/books/**} 的 GET 在
 * JwtAuthFilter 白名单里,过滤器命中白名单后直接放行、<b>不会解析 token</b>,
 * 所以这里拿不到 userId,没法按"这个用户买过什么"加权。
 * 要做个性化得先让白名单路径支持"有 token 就解析"(可选鉴权),或者
 * 让商城在请求里显式带上用户标识 —— 两条路都要动鉴权,单独评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSuggestServiceImpl implements SearchSuggestService {

    /** 看起来像 ISBN 的输入才去查 ISBN 候选(数字 / X / 横杠),省掉纯中文输入时的一次无用查询 */
    private static final Pattern ISBN_LIKE = Pattern.compile("^[0-9Xx-]{3,}$");

    /** 同热度时的类型优先级:书名 > 作者 > ISBN */
    private static final List<String> TYPE_ORDER =
            List.of(BookSuggestion.TYPE_TITLE, BookSuggestion.TYPE_AUTHOR, BookSuggestion.TYPE_ISBN);

    private final SearchSuggestMapper suggestMapper;

    @Override
    public List<BookSuggestion> suggest(String q, Integer limit) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            // 空输入不该打库 —— 前端在没输入时会直接清空下拉,这里是服务端的兜底
            return List.of();
        }

        int cap = normalizeLimit(limit);
        List<BookSuggestion> candidates = collect(keyword, cap);

        // 热度优先;同热度按类型(书名 > 作者 > ISBN),再同就按字面序,保证结果稳定可预期
        candidates.sort(
                Comparator.comparing(BookSuggestion::getHot, Comparator.reverseOrder())
                        .thenComparingInt(s -> typeRank(s.getType()))
                        .thenComparing(BookSuggestion::getText));

        return candidates.size() > cap ? new ArrayList<>(candidates.subList(0, cap)) : candidates;
    }

    /**
     * 取三类候选的并集。
     *
     * <p>每类都按 cap 取,合并后再统一裁剪 —— 不能每类只取 cap/3:
     * 用户输「计算机」时书名候选可能只有 1 条,剩下的名额应该让给作者候选,
     * 按类型硬分名额反而会让结果变少。</p>
     */
    private List<BookSuggestion> collect(String keyword, int cap) {
        List<BookSuggestion> all = new ArrayList<>();
        all.addAll(suggestMapper.selectTitleSuggestions(keyword, cap));
        all.addAll(suggestMapper.selectAuthorSuggestions(keyword, cap));
        if (ISBN_LIKE.matcher(keyword).matches()) {
            all.addAll(suggestMapper.selectIsbnSuggestions(keyword, cap));
        }
        return all;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int typeRank(String type) {
        int idx = TYPE_ORDER.indexOf(type);
        return idx < 0 ? TYPE_ORDER.size() : idx;
    }
}

package com.tmt.TMLibrary.dto.response;

import lombok.Data;

/**
 * 搜索候选词 —— GET /api/books/suggest 的响应元素。
 *
 * <p>下拉列表的一个条目。前端按 {@code type} 决定图标和次要文案,
 * 按 {@code hot} 决定右侧要不要显示热度。</p>
 *
 * <p><b>这个 DTO 是给搜索建议用的稳定契约</b>:当前候选词由 MySQL 的 LIKE 查出来,
 * 后续换成 Elasticsearch 只改 Service 里的取数逻辑,这个形状不变 ——
 * 前端不需要跟着动。</p>
 */
@Data
public class BookSuggestion {

    /** 候选词类型 —— 见 {@link #TYPE_TITLE} / {@link #TYPE_AUTHOR} / {@link #TYPE_ISBN} */
    private String type;

    /** 候选词本身(书名 / 作者名 / ISBN),前端直接展示并回填输入框 */
    private String text;

    /**
     * 选中后可直接定位的 ISBN —— 只有 TITLE / ISBN 类候选有值,作者类为 null。
     * <p>留着是为了后续做"点候选直接进详情页",目前前端只用 text 去搜。</p>
     */
    private String isbn;

    /**
     * 热度 —— 该候选词对应图书在<b>已支付订单</b>里的累计销量。
     * <p>没有成交记录时为 0(不会为 null,SQL 里 COALESCE 过)。</p>
     */
    private Integer hot;

    public static final String TYPE_TITLE = "TITLE";
    public static final String TYPE_AUTHOR = "AUTHOR";
    public static final String TYPE_ISBN = "ISBN";
}

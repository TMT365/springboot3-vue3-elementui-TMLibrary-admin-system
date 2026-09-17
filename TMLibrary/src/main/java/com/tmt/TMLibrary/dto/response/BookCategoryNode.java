package com.tmt.TMLibrary.dto.response;

import com.tmt.TMLibrary.entity.BookCategory;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点 —— GET /api/books/categories 的响应元素(商城侧栏直接用)。
 *
 * <p>{@code bookCount} 对大类是<b>累加值</b>:自身直挂的书 + 所有子类之和,
 * 所以商城侧栏显示大类时拿到的就是"这一类一共有几本"。</p>
 */
@Data
public class BookCategoryNode {

    private Integer id;

    private String name;

    /** 图标名(大类有;小类是 null,前端不渲染图标位) */
    private String icon;

    /** 大类 = 自身 + 子类之和;小类 = 自身直挂数 */
    private Integer bookCount;

    /** 小类列表;大类总是有这个字段(可能为空数组),小类为 null */
    private List<BookCategoryNode> children;

    /** 由实体构造节点(不含 children,由调用方组装树) */
    public static BookCategoryNode from(BookCategory c) {
        BookCategoryNode node = new BookCategoryNode();
        node.setId(c.getId());
        node.setName(c.getName());
        node.setIcon(c.getIcon());
        node.setBookCount(c.getBookCount() == null ? 0 : c.getBookCount());
        return node;
    }

    /** 追加一个子节点,并把子节点的数量累加到自己的 bookCount 上 */
    public void addChild(BookCategoryNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        setBookCount(getBookCount() + child.getBookCount());
    }
}

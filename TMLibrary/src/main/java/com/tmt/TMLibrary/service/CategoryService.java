package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.BookCategoryNode;
import com.tmt.TMLibrary.entity.BookCategory;

import java.util.List;

/**
 * 图书分类 —— 读路径给商城(免登录),写路径给后台。
 */
public interface CategoryService {

    /**
     * 两级分类树,供商城侧栏 / chips 直接渲染。
     * <p>大类节点的 {@code bookCount} 已累加其所有子类,所以"这一类共几本"直接可用。</p>
     */
    List<BookCategoryNode> listTree();

    /**
     * 新建分类。
     *
     * @param parentId 0(或 null)= 新建大类;&gt; 0 = 在指定大类下新建小类
     * @param name     分类名(同父下不可重名)
     */
    BookCategory create(Integer parentId, String name);

    /**
     * 调整某分类的直挂图书数 —— 由 BookService 在增删改书时调用,
     * 与书的变更处于同一个事务里。
     *
     * @param delta +1(新增一本书)/ -1(删除或移出)
     */
    void adjustBookCount(Integer categoryId, int delta);
}

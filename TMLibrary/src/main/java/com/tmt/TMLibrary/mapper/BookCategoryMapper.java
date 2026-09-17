package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 图书分类 —— 表很小(几十行),读路径一律全表取出后在内存里组装,
 * 不做按层查询(避免 N+1)。
 */
@Mapper
public interface BookCategoryMapper {

    /** 全表,按 parent_id → sort_order 升序(组装树时直接顺序遍历) */
    List<BookCategory> selectAll();

    BookCategory selectById(@Param("id") Integer id);

    /** 同父下按名字精确查(新建时防重;也用于校验重复） */
    BookCategory selectByParentAndName(@Param("parentId") Integer parentId,
                                       @Param("name") String name);

    int insert(BookCategory category);

    /**
     * 调整直挂图书数。
     * <p>用 {@code book_count = book_count + #{delta}} 而不是先读后写 ——
     * 并发下不会丢更新;配合 {@code WHERE book_count + #{delta} >= 0} 防止减成负数。</p>
     */
    int adjustBookCount(@Param("id") Integer id, @Param("delta") int delta);

    /** 从 books 实表重算全部计数(运维修复用,幂等) */
    int recountAll();
}

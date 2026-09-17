package com.tmt.TMLibrary.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图书分类(两级)—— 对应 book_categories 表。
 *
 * <p>{@code parentId = 0} 表示大类(顶层);大于 0 表示小类,值指向所属大类。
 * 顶层用 0 而不是 NULL,是为了让 {@code UNIQUE(parent_id, name)} 真正生效
 * —— MySQL 的唯一索引里 NULL 互不相等,用 NULL 的话顶层重名约束会失效。</p>
 */
@Data
public class BookCategory {

    private Integer id;

    /** 0 = 大类;> 0 = 所属大类 id */
    private Integer parentId;

    private String name;

    /** Element Plus 图标名(只有大类有,小类为 null) */
    private String icon;

    /** 排序,小的在前 */
    private Integer sortOrder;

    /** 直接挂载在本分类下的图书数(不含子类;大类展示时由服务端累加) */
    private Integer bookCount;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

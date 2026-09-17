package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建分类的入参 —— POST /api/books/categories
 *
 * <p>后台「新增图书」里那个可以现敲的小类输入框,敲完先打这个接口把分类建出来,
 * 拿到 id 再提交图书。</p>
 */
@Data
public class CategoryCreateRequest {

    /**
     * 父分类 id —— 0 或 null = 新建大类;&gt; 0 = 在该大类下新建小类。
     * <p>用 0 而不是 null 当"顶级"哨兵,是为了让 {@code UNIQUE(parent_id, name)}
     * 能真正生效(MySQL 唯一索引里多个 NULL 互不冲突,顶层重名就拦不住了)。</p>
     */
    private Integer parentId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名长度不能超过 50")
    private String name;
}

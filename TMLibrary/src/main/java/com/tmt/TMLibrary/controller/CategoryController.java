package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.request.CategoryCreateRequest;
import com.tmt.TMLibrary.dto.response.BookCategoryNode;
import com.tmt.TMLibrary.entity.BookCategory;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图书分类接口。
 *
 * <p><b>为什么挂在 {@code /books/categories} 而不是 {@code /categories}</b>:
 * {@code JwtAuthFilter} 的白名单是一条前缀规则 {@code ("/books", GET)}——
 * 只要路径落在 {@code /books} 下面且是 GET,未登录的商城访客就能读。
 * 换个前缀就要再往白名单里加一条,读接口的形状没必要和鉴权配置耦合。</p>
 *
 * <p><b>会不会和 {@code GET /books/{isbn}} 撞车</b>:不会。Spring 的路径匹配
 * 里字面量段 {@code categories} 的优先级高于变量段 {@code {isbn}},
 * 所以 {@code /books/categories} 永远进这里,不会被当成 isbn="categories"。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/books/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 分类树(两级,含每类的图书数)—— GET /books/categories
     * <p>免登录:商城侧栏、分类 chips 都用它,游客也要看得见。</p>
     */
    @GetMapping
    public Result<List<BookCategoryNode>> tree() {
        return Result.success(categoryService.listTree());
    }

    /**
     * 新建分类 —— POST /books/categories
     * <p>后台专用:普通用户不该能往分类表里塞数据。</p>
     */
    @PostMapping
    public Result<BookCategory> create(@RequestBody @Valid CategoryCreateRequest req,
                                       @CurrentUser UserView me) {
        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (!UserRole.ADMIN.getCode().equals(me.getRole())
                && !UserRole.BOSS.getCode().equals(me.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可新建分类");
        }
        log.info("前端请求新建分类, parentId={}, name={}", req.getParentId(), req.getName());
        return Result.success(categoryService.create(req.getParentId(), req.getName()));
    }
}

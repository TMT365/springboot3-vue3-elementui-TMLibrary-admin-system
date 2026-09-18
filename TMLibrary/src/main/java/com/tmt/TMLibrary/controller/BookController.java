package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.request.BookSearchRequest;
import com.tmt.TMLibrary.dto.request.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.request.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.request.BookUpdateRequest;
import com.tmt.TMLibrary.dto.request.BookSaveRequest;
import com.tmt.TMLibrary.dto.request.BookStockAdjustRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.BookService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/books")
// @RequiredArgsConstructor 注解会生成一个包含所有 final 字段的构造函数, 这样 Spring 就可以通过构造函数注入 BookService 实例
@RequiredArgsConstructor
public class BookController {

    /** 单页最大条数 — 与 BookSearchRequest/UserSearchRequest 的 compact() 上限保持一致 */
    private static final int MAX_PAGE_SIZE = 100;

    private final BookService bookService;

    /**
     * url = /books/list?page=1&amp;size=10
     * @param page
     * @param size
     * @return {@code Result<PageResult<Book>>}
     */
    @GetMapping("/list")
    public Result<PageResult<Book>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 归一化:page 至少 1,size 限制在 [1, 100] — 防止 size=99999999 打爆 DB
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        log.info("前端请求/books/list?page={}&size={}", safePage, safeSize);
        return Result.success(bookService.page(safePage, safeSize));
    }

    /**
     * url = /books/created
     *
     * <p><b>仅 ADMIN / BOSS</b>。此前这里(以及下面三个写方法)没有任何角色检查 ——
     * 任何已登录用户拿 token 直接打接口就能增删改图书、改库存。
     * 前端把这些页面挂在 {@code meta: { admin: true }} 路由下,但
     * <b>前端路由守卫不是安全边界</b>,绕过它只需要一个 curl。</p>
     *
     * <p>读写权限的分界:JwtAuthFilter 的白名单放行 {@code /books} 的
     * 全部 GET(商城未登录可浏览),写操作靠这里兜底。</p>
     */
    @PostMapping("/created")
    public Result<Void> create(@RequestBody @Valid BookSaveRequest req, @CurrentUser UserView me) {
        requireAdminOrBoss(me);
        log.info("前端请求/books/created, req={}", req);
        bookService.create(req);
        return Result.success();
    }

    /**
     * url = /books/deleted/isbn/978-3-16-148410-0
     * <p>仅 ADMIN / BOSS —— 见 {@link #create} 的说明。</p>
     */
    @DeleteMapping("/deleted/isbn/{isbn}")
    public Result<Void> deleteByISBN(@PathVariable(name = "isbn", required = true) String isbn,
                                     @CurrentUser UserView me) {
        requireAdminOrBoss(me);
        log.info("前端请求/books/{}", isbn);
        bookService.deleteByISBN(isbn);
        return Result.success();
    }

    /**
     * url = /books/978-3-16-148410-0
     * <p>仅 ADMIN / BOSS —— 见 {@link #create} 的说明。</p>
     */
    @PatchMapping("/{isbn}")
    public Result<Void> updateByISBN(@PathVariable(name = "isbn", required = true) String isbn,
                                     @RequestBody @Valid BookUpdateRequest req,
                                     @CurrentUser UserView me) {
        requireAdminOrBoss(me);
        log.info("前端请求/books/{}, 参数={}", isbn, req);
        bookService.updateByISBN(isbn, req);
        return Result.success();
    }

    /**
     * 调整库存(盘点语义,绝对值) — PATCH /books/978-3-16-148410-0/stock
     * <p>与「更新图书信息」分离:交易链路会持续改动可用库存,
     * 管理端盘点需要独立的语义、权限与审计。</p>
     * <p>仅 ADMIN / BOSS —— 见 {@link #create} 的说明。库存被任意用户改写
     * 会直接破坏超卖防线,这条尤其不能漏。</p>
     */
    @PatchMapping("/{isbn}/stock")
    public Result<Void> adjustStock(@PathVariable(name = "isbn", required = true) String isbn,
                                    @RequestBody @Valid BookStockAdjustRequest req,
                                    @CurrentUser UserView me) {
        requireAdminOrBoss(me);
        log.info("前端请求/books/{}/stock, 库存调整为 {}", isbn, req.getStockQuantity());
        bookService.adjustStock(isbn, req.getStockQuantity());
        return Result.success();
    }

    @GetMapping("/{isbn}")
    public Result<Book> getByISBN(@PathVariable(name = "isbn", required = true) String isbn) {
        log.info("前端请求/books/{}", isbn);
        return Result.success(bookService.getByISBN(isbn));
    }


    // ============== P1 第一个子任务:多条件组合查询 ==============

    /**
     * 多条件组合查询(走动态 SQL)
     * url = GET /books?title=&amp;author=&amp;minPrice=&amp;maxPrice=&amp;minStock=&amp;maxStock=&amp;publishedDate=&amp;page=1&amp;size=10
     * 所有参数都可选;Service 层负责归一化(空串→null、负数→null、page/size 兜底、size 上限 100)
     * @param query Spring 用 &#64;ModelAttribute 从 query 参数自动绑到 DTO 字段
     */
    @GetMapping
    public Result<PageResult<Book>> search(@ModelAttribute BookSearchRequest query) {
        log.info("前端请求/books, 参数={}", query);
        return Result.success(bookService.search(query));
    }

    // ============== 任务二:时间粒度查询(/by 后缀) ==============

    /**
     * 按 publishedDate 粒度查询(year / year+month / year+month+day)
     * url = GET /books/search/publishedDate/by?year=YYYY&amp;month=MM&amp;day=DD&amp;page=1&amp;size=10
     * year 必填;month/day 可选但必须连续 — 校验在 DTO.compact() 里
     */
    @GetMapping("/search/publishedDate/by")
    public Result<PageResult<Book>> searchByPublishedDateBy(@ModelAttribute BookPublishedDateByRequest req) {
        log.info("前端请求/books/search/publishedDate/by, 参数={}", req);
        return Result.success(bookService.searchByPublishedDateBy(req));
    }

    /**
     * 按 createdTime 粒度查询(year → minute,5 级)
     * url = GET /books/search/CreatedTime/by?year=YYYY&amp;month=MM&amp;day=DD&amp;hour=HH&amp;minute=mm&amp;page=1&amp;size=10
     */
    @GetMapping("/search/CreatedTime/by")
    public Result<PageResult<Book>> searchByCreatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        log.info("前端请求/books/search/CreatedTime/by, 参数={}", req);
        return Result.success(bookService.searchByCreatedTimeBy(req));
    }

    /**
     * 按 updatedTime 粒度查询(同 createdTime)
     * url = GET /books/search/UpdatedTime/by?year=YYYY&amp;month=MM&amp;day=DD&amp;hour=HH&amp;minute=mm&amp;page=1&amp;size=10
     */
    @GetMapping("/search/UpdatedTime/by")
    public Result<PageResult<Book>> searchByUpdatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        log.info("前端请求/books/search/UpdatedTime/by, 参数={}", req);
        return Result.success(bookService.searchByUpdatedTimeBy(req));
    }

    // ============== 鉴权辅助 ==============

    /**
     * 要求当前用户是 ADMIN 或 BOSS。
     *
     * <p>写法与 {@code SecurityController.requireAdmin} /
     * {@code PurchaseController} 内的角色判断保持一致:未登录抛
     * {@link AuthException}(401),已登录但角色不够抛
     * {@link BusinessException}(403)。</p>
     *
     * <p>注意 {@code me == null} 这一支在正常链路里到不了 ——
     * 这 4 个写端点不在 {@code JwtAuthFilter} 的白名单里,没有合法 token
     * 根本进不到 Controller。留着是为了防御性编程:万一以后有人改了白名单,
     * 这里不会因为 NPE 变成一个 500。</p>
     */
    private void requireAdminOrBoss(UserView me) {
        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Integer role = me.getRole();
        if (!UserRole.ADMIN.getCode().equals(role) && !UserRole.BOSS.getCode().equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可操作图书");
        }
    }
}
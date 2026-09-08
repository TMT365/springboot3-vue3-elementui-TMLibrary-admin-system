package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.dto.request.BookSearchRequest;
import com.tmt.TMLibrary.dto.request.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.request.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.request.BookUpdateRequest;
import com.tmt.TMLibrary.dto.request.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;
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

    private final BookService bookService;

    /**
     * url = /api/books/list?page=1&amp;size=10
     * @param page
     * @param size
     * @return {@code Result<PageResult<Book>>}
     */
    @GetMapping("/list")
    public Result<PageResult<Book>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("前端请求/api/books/list?page={}&size={}", page, size);
        return Result.success(bookService.page(page, size));
    }

    /**
     * url = /api/books/created
     * @param req
     * @return
     */
    @PostMapping("/created")
    public Result<Void> create(@RequestBody @Valid BookSaveRequest req) {
        log.info("前端请求/api/books/created, req={}", req);
        bookService.create(req);
        return Result.success();
    }

    /**
     * url = /api/books/978-3-16-148410-0
     * @param isbn
     * @return
     */
    @DeleteMapping("/deleted/isbn/{isbn}")
    public Result<Void> deleteByISBN(@PathVariable(name = "isbn", required = true) String isbn) {
        log.info("前端请求/api/books/{}", isbn);
        bookService.deleteByISBN(isbn);
        return Result.success();
    }
    /**
     * url = /api/books/978-3-16-148410-0
     * @param isbn
     * @param req
     * @return
     */
    @PatchMapping("/{isbn}")
    public Result<Void> updateByISBN(@PathVariable(name = "isbn", required = true) String isbn, @RequestBody @Valid BookUpdateRequest req) {
        log.info("前端请求/api/books/{}, 参数={}", isbn, req);
        bookService.updateByISBN(isbn, req);
        return Result.success();
    }

    @GetMapping("/{isbn}")
    public Result<Book> getByISBN(@PathVariable(name = "isbn", required = true) String isbn) {
        log.info("前端请求/api/books/{}", isbn);
        return Result.success(bookService.getByISBN(isbn));
    }


    // ============== P1 第一个子任务:多条件组合查询 ==============

    /**
     * 多条件组合查询(走动态 SQL)
     * url = GET /api/books?title=&amp;author=&amp;minPrice=&amp;maxPrice=&amp;minStock=&amp;maxStock=&amp;publishedDate=&amp;page=1&amp;size=10
     * 所有参数都可选;Service 层负责归一化(空串→null、负数→null、page/size 兜底、size 上限 100)
     * @param query Spring 用 &#64;ModelAttribute 从 query 参数自动绑到 DTO 字段
     */
    @GetMapping
    public Result<PageResult<Book>> search(@ModelAttribute BookSearchRequest query) {
        log.info("前端请求/api/books, 参数={}", query);
        return Result.success(bookService.search(query));
    }

    // ============== 任务二:时间粒度查询(/by 后缀) ==============

    /**
     * 按 publishedDate 粒度查询(year / year+month / year+month+day)
     * url = GET /api/books/search/publishedDate/by?year=YYYY&amp;month=MM&amp;day=DD&amp;page=1&amp;size=10
     * year 必填;month/day 可选但必须连续 — 校验在 DTO.compact() 里
     */
    @GetMapping("/search/publishedDate/by")
    public Result<PageResult<Book>> searchByPublishedDateBy(@ModelAttribute BookPublishedDateByRequest req) {
        log.info("前端请求/api/books/search/publishedDate/by, 参数={}", req);
        return Result.success(bookService.searchByPublishedDateBy(req));
    }

    /**
     * 按 createdTime 粒度查询(year → minute,5 级)
     * url = GET /api/books/search/CreatedTime/by?year=YYYY&amp;month=MM&amp;day=DD&amp;hour=HH&amp;minute=mm&amp;page=1&amp;size=10
     */
    @GetMapping("/search/CreatedTime/by")
    public Result<PageResult<Book>> searchByCreatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        log.info("前端请求/api/books/search/CreatedTime/by, 参数={}", req);
        return Result.success(bookService.searchByCreatedTimeBy(req));
    }

    /**
     * 按 updatedTime 粒度查询(同 createdTime)
     * url = GET /api/books/search/UpdatedTime/by?year=YYYY&amp;month=MM&amp;day=DD&amp;hour=HH&amp;minute=mm&amp;page=1&amp;size=10
     */
    @GetMapping("/search/UpdatedTime/by")
    public Result<PageResult<Book>> searchByUpdatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        log.info("前端请求/api/books/search/UpdatedTime/by, 参数={}", req);
        return Result.success(bookService.searchByUpdatedTimeBy(req));
    }
}
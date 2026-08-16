package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.dto.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.dto.BookSearchRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.service.BookService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * @brief url = /api/books/list?page=1&size=10
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/list")
    public Result<PageResult<Book>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.page(page, size));
    }
    /**
     * @brief url = /api/books/display?id=1
     * @param id
     * @return
     */
    @GetMapping("/display")
    public Result<Book> getById(@RequestParam(defaultValue = "1") int id) {
        return Result.success(bookService.getById(id));
    }

    /**
     * @brief url = /api/books/created
     * @param req
     * @return
     */
    @PostMapping("/created")
    public Result<Void> create(@RequestBody @Valid BookSaveRequest req) {
        bookService.create(req);
        return Result.success();
    }
    /**
     * @brief url = /api/books/updated?id=1
     * @param id
     * @param req
     * @return
     */
    @PatchMapping("/updated")
    public Result<Void> update(@RequestParam(required = true) int id,
            @RequestBody @Valid BookSaveRequest req) {
        bookService.updateById(id, req);
        return Result.success();
    }

    /**
     * @brief url = /api/books/deleted/id/1
     * @param id
     * @return
     */
    @DeleteMapping("/deleted/id/{id}")
    public Result<Void> delete(@PathVariable(name = "id", required = true) int id) {
        bookService.deleteById(id);
        return Result.success();
    }

    /**
     * @brief url = /api/books/deleted/isbn/978-3-16-148410-0
     * @param isbn
     * @return
     */
    @DeleteMapping("/deleted/isbn/{isbn}")
    public Result<Void> deleteByISBN(@PathVariable(name = "isbn", required = true) String isbn) {
        bookService.deleteByISBN(isbn);
        return Result.success();
    }
    /**
     * @brief url = /api/books/updated/isbn/978-3-16-148410-0
     * @param isbn
     * @param req
     * @return
     */
    @PatchMapping("/updated/isbn/{isbn}")
    public Result<Void> updateByISBN(@PathVariable(name = "isbn", required = true) String isbn, @RequestBody @Valid BookSaveRequest req) {
        bookService.updateByISBN(isbn, req);
        return Result.success();
    }

    @GetMapping("/display/isbn/{isbn}")
    public Result<Book> getByISBN(@PathVariable(name = "isbn", required = true) String isbn) {
        return Result.success(bookService.getByISBN(isbn));
    }

    @GetMapping("/search/title/{title}")
    public Result<PageResult<Book>> searchByTitle(@PathVariable(name = "title", required = true) String title,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.searchByTitle(title, page, size));
    }

    @GetMapping("/search/Author/{author}")
    public Result<PageResult<Book>> searchByAuthor(@PathVariable(name = "author", required = true) String author,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.searchByAuthor(author, page, size));
    }

    @GetMapping("/search/publishedDate/{publishedDate}")
    public Result<PageResult<Book>> searchByPublishedDate(@PathVariable(name = "publishedDate", required = true) String publishedDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 进行类型转换，将字符串转换为LocalDate
        LocalDate parsedPublishedDate = LocalDate.parse(publishedDate);
        return Result.success(bookService.searchByPublishedDate(parsedPublishedDate, page, size));
    }

    @GetMapping("/search/CreatedTime/{createdTime}")
    public Result<PageResult<Book>> searchByCreatedTime(@PathVariable(name = "createdTime", required = true) String createdTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 进行类型转换，将字符串转换为LocalDateTime
        LocalDateTime parsedCreatedTime = LocalDateTime.parse(createdTime);
        return Result.success(bookService.searchByCreatedTime(parsedCreatedTime, page, size));
    }

    @GetMapping("/search/UpdatedTime/{updatedTime}")
    public Result<PageResult<Book>> searchByUpdatedTime(@PathVariable(name = "updatedTime", required = true) String updatedTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 进行类型转换，将字符串转换为LocalDateTime
        LocalDateTime parsedUpdatedTime = LocalDateTime.parse(updatedTime);
        return Result.success(bookService.searchByUpdatedTime(parsedUpdatedTime, page, size));
    }

    @GetMapping("/search/PriceRange/{min}/{max}")
    public Result<PageResult<Book>> searchByPriceRange(@PathVariable(name = "min", required = true) BigDecimal min,
            @PathVariable(name = "max", required = true) BigDecimal max,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.searchByPriceRange(min, max, page, size));
    }

    @GetMapping("/search/StockQuantityRange/{min}/{max}")
    public Result<PageResult<Book>> searchByStockQuantityRange(@PathVariable(name = "min", required = true) int min,
            @PathVariable(name = "max", required = true) int max,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.searchByStockQuantityRange(min, max, page, size));
    }

    // ============== P1 第一个子任务:多条件组合查询 ==============

    /**
     * @brief 多条件组合查询(走动态 SQL)
     * url = GET /api/books?title=&author=&minPrice=&maxPrice=&minStock=&maxStock=&publishedDate=&page=1&size=10
     * 所有参数都可选;Service 层负责归一化(空串→null、负数→null、page/size 兜底、size 上限 100)
     * @param query Spring 用 @ModelAttribute 从 query 参数自动绑到 DTO 字段
     * @ModelAttribute 
     */
    @GetMapping
    public Result<PageResult<Book>> search(@ModelAttribute BookSearchRequest query) {
        return Result.success(bookService.search(query));
    }

    // ============== 任务二:时间粒度查询(/by 后缀) ==============

    /**
     * @brief 按 publishedDate 粒度查询(year / year+month / year+month+day)
     * url = GET /api/books/search/publishedDate/by?year=YYYY&month=MM&day=DD&page=1&size=10
     * year 必填;month/day 可选但必须连续 — 校验在 DTO.compact() 里
     */
    @GetMapping("/search/publishedDate/by")
    public Result<PageResult<Book>> searchByPublishedDateBy(@ModelAttribute BookPublishedDateByRequest req) {
        return Result.success(bookService.searchByPublishedDateBy(req));
    }

    /**
     * @brief 按 createdTime 粒度查询(year → minute,5 级)
     * url = GET /api/books/search/CreatedTime/by?year=YYYY&month=MM&day=DD&hour=HH&minute=mm&page=1&size=10
     */
    @GetMapping("/search/CreatedTime/by")
    public Result<PageResult<Book>> searchByCreatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        return Result.success(bookService.searchByCreatedTimeBy(req));
    }

    /**
     * @brief 按 updatedTime 粒度查询(同 createdTime)
     * url = GET /api/books/search/UpdatedTime/by?year=YYYY&month=MM&day=DD&hour=HH&minute=mm&page=1&size=10
     */
    @GetMapping("/search/UpdatedTime/by")
    public Result<PageResult<Book>> searchByUpdatedTimeBy(@ModelAttribute BookDateTimeByRequest req) {
        return Result.success(bookService.searchByUpdatedTimeBy(req));
    }
}
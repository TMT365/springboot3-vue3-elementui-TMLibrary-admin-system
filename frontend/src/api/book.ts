/**
 * Book 相关 API  -  19 个端点
 *
 * ⚠️ 大小写陷阱:后端路径 /search/Author/, /search/CreatedTime/, /search/UpdatedTime/ 是驼峰首字母大写
 *   这里用 BOOK_PATH 常量统一管理,避免散落各处拼错
 */

import { http } from '@/utils/request'
import type {
  BookDateTimeByQuery,
  BookDto,
  BookPublishedDateByQuery,
  BookSaveRequest,
  BookSearchRequest,
  PageQuery,
  PageResult,
} from '@/types/api'

// 大小写敏感的路径前缀(后端 controller 写啥就是啥)
const BOOK_PATH = {
  LIST:                  '/api/books/list',
  DISPLAY:               '/api/books/display',
  CREATED:               '/api/books/created',
  UPDATED:               '/api/books/updated',
  DELETED_ID:            '/api/books/deleted/id',
  DELETED_ISBN:          '/api/books/deleted/isbn',
  UPDATED_ISBN:          '/api/books/updated/isbn',
  DISPLAY_ISBN:          '/api/books/display/isbn',
  SEARCH_TITLE:          '/api/books/search/title',
  SEARCH_AUTHOR:         '/api/books/search/Author',         // ← 大写 A
  SEARCH_PUBLISHED_DATE: '/api/books/search/publishedDate',
  SEARCH_CREATED_TIME:   '/api/books/search/CreatedTime',   // ← 大写 C
  SEARCH_UPDATED_TIME:   '/api/books/search/UpdatedTime',   // ← 大写 U
  SEARCH_PRICE_RANGE:    '/api/books/search/PriceRange',
  SEARCH_STOCK_RANGE:    '/api/books/search/StockQuantityRange',
  ROOT:                  '/api/books',
  BY_PUBLISHED_DATE:     '/api/books/search/publishedDate/by',
  BY_CREATED_TIME:       '/api/books/search/CreatedTime/by',
  BY_UPDATED_TIME:       '/api/books/search/UpdatedTime/by',
} as const

export const bookApi = {
  // ------------------- 列表 / 详情 -------------------

  /** GET /api/books/list?page=&size= */
  list: (query?: PageQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.LIST,
      params: query,
    }),

  /** GET /api/books/display?id= */
  display: (id: number): Promise<BookDto> =>
    http<BookDto>({
      method: 'GET',
      url: BOOK_PATH.DISPLAY,
      params: { id },
    }),

  /** GET /api/books/display/isbn/{isbn} */
  displayByIsbn: (isbn: string): Promise<BookDto> =>
    http<BookDto>({
      method: 'GET',
      url: `${BOOK_PATH.DISPLAY_ISBN}/${encodeURIComponent(isbn)}`,
    }),

  // ------------------- 创建 / 更新 / 删除 -------------------

  /** POST /api/books/created */
  create: (body: BookSaveRequest): Promise<void> =>
    http<void>({
      method: 'POST',
      url: BOOK_PATH.CREATED,
      data: body,
    }),

  /** PATCH /api/books/updated?id= */
  update: (id: number, body: BookSaveRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: BOOK_PATH.UPDATED,
      params: { id },
      data: body,
    }),

  /** PATCH /api/books/updated/isbn/{isbn} */
  updateByIsbn: (isbn: string, body: BookSaveRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `${BOOK_PATH.UPDATED_ISBN}/${encodeURIComponent(isbn)}`,
      data: body,
    }),

  /** DELETE /api/books/deleted/id/{id} */
  deleteById: (id: number): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `${BOOK_PATH.DELETED_ID}/${id}`,
    }),

  /** DELETE /api/books/deleted/isbn/{isbn} */
  deleteByIsbn: (isbn: string): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `${BOOK_PATH.DELETED_ISBN}/${encodeURIComponent(isbn)}`,
    }),

  // ------------------- 路径变量搜索 -------------------

  searchByTitle: (title: string, query?: PageQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_TITLE}/${encodeURIComponent(title)}`,
      params: query,
    }),

  searchByAuthor: (author: string, query?: PageQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_AUTHOR}/${encodeURIComponent(author)}`,
      params: query,
    }),

  searchByPublishedDate: (
    publishedDate: string,
    query?: PageQuery,
  ): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_PUBLISHED_DATE}/${encodeURIComponent(publishedDate)}`,
      params: query,
    }),

  searchByCreatedTime: (
    createdTime: string,
    query?: PageQuery,
  ): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_CREATED_TIME}/${encodeURIComponent(createdTime)}`,
      params: query,
    }),

  searchByUpdatedTime: (
    updatedTime: string,
    query?: PageQuery,
  ): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_UPDATED_TIME}/${encodeURIComponent(updatedTime)}`,
      params: query,
    }),

  searchByPriceRange: (
    min: string,
    max: string,
    query?: PageQuery,
  ): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_PRICE_RANGE}/${encodeURIComponent(min)}/${encodeURIComponent(max)}`,
      params: query,
    }),

  searchByStockRange: (
    min: number,
    max: number,
    query?: PageQuery,
  ): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: `${BOOK_PATH.SEARCH_STOCK_RANGE}/${min}/${max}`,
      params: query,
    }),

  // ------------------- 多条件搜索(根路径) -------------------

  /** GET /api/books  -  BookSearchRequest 作 query */
  multiSearch: (query: BookSearchRequest): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.ROOT,
      params: query,
    }),

  // ------------------- 时间粒度 -------------------

  searchPublishedDateBy: (query: BookPublishedDateByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_PUBLISHED_DATE,
      params: query,
    }),

  searchCreatedTimeBy: (query: BookDateTimeByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_CREATED_TIME,
      params: query,
    }),

  searchUpdatedTimeBy: (query: BookDateTimeByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_UPDATED_TIME,
      params: query,
    }),
}
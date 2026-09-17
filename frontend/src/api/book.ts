/**
 * Book 相关 API  -  对齐后端 API.md §4 + §7
 *
 * 端点(共 10 个,真实存在于后端):
 *   GET    /api/books/list?page=&size=                    简单分页
 *   POST   /api/books/created                            新建(BookSaveRequest)
 *   GET    /api/books/{isbn}                             详情
 *   PATCH  /api/books/{isbn}                             修改(BookUpdateRequest,无 stockQuantity)
 *   PATCH  /api/books/{isbn}/stock                       调整库存(BookStockAdjustRequest,盘点)
 *   DELETE /api/books/deleted/isbn/{isbn}                删除
 *   GET    /api/books?...                                多条件搜索
 *   GET    /api/books/search/publishedDate/by?...        按出版日期粒度
 *   GET    /api/books/search/CreatedTime/by?...          按创建时间粒度
 *   GET    /api/books/search/UpdatedTime/by?...          按更新时间粒度
 *
 * 历史包袱清理(已删除,无任何调用方):
 *   - display / displayByIsbn
 *   - update(id, body) / updateByIsbn(isbn, body) via /updated/...
 *   - searchByTitle / searchByAuthor / searchByPublishedDate /
 *     searchByCreatedTime / searchByUpdatedTime /
 *     searchByPriceRange / searchByStockRange
 */

import { http } from '@/utils/request'
import type {
  BookCategoryDto,
  BookCategoryNode,
  BookSuggestion,
  BookDateTimeByQuery,
  BookDto,
  BookPublishedDateByQuery,
  BookSaveRequest,
  BookSearchRequest,
  BookStockAdjustRequest,
  BookUpdateRequest,
  CategoryCreateRequest,
  PageQuery,
  PageResult,
} from '@/types/api'

const BOOK_PATH = {
  LIST:                  '/api/books/list',
  CREATED:               '/api/books/created',
  DELETED_ISBN:          '/api/books/deleted/isbn',
  ROOT:                  '/api/books',
  CATEGORIES:            '/api/books/categories',
  SUGGEST:               '/api/books/suggest',
  BY_PUBLISHED_DATE:     '/api/books/search/publishedDate/by',
  BY_CREATED_TIME:       '/api/books/search/CreatedTime/by',   // ← 大写 C
  BY_UPDATED_TIME:       '/api/books/search/UpdatedTime/by',   // ← 大写 U
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

  /** GET /api/books/{isbn}  -  详情(不存在时 data=null,code=200) */
  getByIsbn: (isbn: string): Promise<BookDto> =>
    http<BookDto>({
      method: 'GET',
      url: `/api/books/${encodeURIComponent(isbn)}`,
    }),

  // ------------------- 创建 / 修改 / 删除 / 盘点 -------------------

  /** POST /api/books/created  -  新建(API §4.2 BookSaveRequest) */
  create: (body: BookSaveRequest): Promise<void> =>
    http<void>({
      method: 'POST',
      url: BOOK_PATH.CREATED,
      data: body,
    }),

  /** PATCH /api/books/{isbn}  -  修改(API §4.4 BookUpdateRequest)
   *  库存字段已移出本接口,改用 adjustStock。
   *  后端会同步刷新 Redis 库存并保留在途预占。 */
  updateByIsbn: (isbn: string, body: BookUpdateRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/books/${encodeURIComponent(isbn)}`,
      data: body,
    }),

  /** PATCH /api/books/{isbn}/stock  -  调整库存(API §4.5 盘点语义)
   *  stockQuantity 是绝对值,非增量;DB 覆盖后 Redis 按
   *  「新库存 − 在途预占」 重算,不会让未支付订单的预占消失。 */
  adjustStock: (isbn: string, body: BookStockAdjustRequest): Promise<void> =>
    http<void>({
      method: 'PATCH',
      url: `/api/books/${encodeURIComponent(isbn)}/stock`,
      data: body,
    }),

  /** DELETE /api/books/deleted/isbn/{isbn} */
  deleteByIsbn: (isbn: string): Promise<void> =>
    http<void>({
      method: 'DELETE',
      url: `${BOOK_PATH.DELETED_ISBN}/${encodeURIComponent(isbn)}`,
    }),

  // ------------------- 多条件搜索(根路径) -------------------

  /** GET /api/books  -  BookSearchRequest 作 query(API §4.7) */
  multiSearch: (query: BookSearchRequest): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.ROOT,
      params: query,
    }),

  // ------------------- 时间粒度 -------------------

  /** GET /api/books/search/publishedDate/by?year=&month=&day= (API §4.8)
   *  粒度 3 级;year 必填(1900-2100);month/day 必须从大到小连续 */
  searchPublishedDateBy: (query: BookPublishedDateByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_PUBLISHED_DATE,
      params: query,
    }),

  /** GET /api/books/search/CreatedTime/by?year=&month=&day=&hour=&minute= (API §4.9)
   *  粒度 5 级;校验同上 */
  searchCreatedTimeBy: (query: BookDateTimeByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_CREATED_TIME,
      params: query,
    }),

  /** GET /api/books/search/UpdatedTime/by?... (API §4.10) */
  searchUpdatedTimeBy: (query: BookDateTimeByQuery): Promise<PageResult<BookDto>> =>
    http<PageResult<BookDto>>({
      method: 'GET',
      url: BOOK_PATH.BY_UPDATED_TIME,
      params: query,
    }),

  // ------------------- 图书分类 -------------------

  /** GET /api/books/categories  -  两级分类树(大类含 children,数量已累加)
   *
   *  挂在 /api/books 前缀下是为了蹭 JwtAuthFilter 的 GET 白名单 —— 商城未登录也要能读。
   *  不会和 GET /api/books/{isbn} 冲突:Spring 里字面量段优先于变量段。 */
  listCategories: (): Promise<BookCategoryNode[]> =>
    http<BookCategoryNode[]>({
      method: 'GET',
      url: BOOK_PATH.CATEGORIES,
    }),

  /** GET /api/books/suggest?q=&limit=  -  搜索候选词(下拉建议)
   *
   *  免登录;已按热度(已支付订单销量)排序。
   *  前端在 el-autocomplete 的 fetch-suggestions 里调,自带防抖。 */
  suggest: (q: string, limit = 8): Promise<BookSuggestion[]> =>
    http<BookSuggestion[]>({
      method: 'GET',
      url: BOOK_PATH.SUGGEST,
      params: { q, limit },
    }),

  /** POST /api/books/categories  -  新建分类(需 ADMIN/BOSS)
   *
   *  后台「新增图书」里现敲一个小类名 → 先建分类拿到 id,再带着 categoryId 提交图书。
   *  同父下重名时后端返回**已存在的那个分类**(幂等),不会报错。 */
  createCategory: (body: CategoryCreateRequest): Promise<BookCategoryDto> =>
    http<BookCategoryDto>({
      method: 'POST',
      url: BOOK_PATH.CATEGORIES,
      data: body,
    }),
}

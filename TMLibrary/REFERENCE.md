# Book CRUD 业务代码参考

> **用途**: 项目当前业务代码已清空,本文档提供明天手写时的完整参考实现。
> 复制时按"编写顺序"一节的小节顺序逐文件创建即可。

---

## 0. 当前项目状态

```text
TMLibrary/
├── pom.xml                          ← 已配好(Spring Boot 4.1 + Java 25 + MyBatis + Lombok)
├── mvnw, mvnw.cmd, .mvn/wrapper/    ← Maven Wrapper 已就绪
├── src/main/resources/
│   ├── application.yml              ← MyBatis + 数据源(dev profile 已激活)
│   ├── application-dev.yml          ← 本机 MySQL 密码已填
│   └── application-prod.yml
└── src/main/java/com/tmt/TMLibrary/
    ├── (空,需要自己写)
    └── 各空包都有 .gitkeep 占位
```

**没有 TmLibraryApplication.java → `./mvnw compile` 会失败**,所以第一个文件必须是启动类。

---

## 1. 编写顺序(推荐)

| 步骤 | 文件 | 包 | 为什么先写它 |
|---|---|---|---|
| 1 | `TmLibraryApplication.java` | `com.tmt.TMLibrary` | 没有它编不过 |
| 2 | `Book.java` | `entity` | 数据结构是其他一切的锚 |
| 3 | `BookMapper.java` | `mapper` | 调通"查/写"这条主线 |
| 4 | `BookMapper.xml` | `resources/mapper` | SQL 落地 |
| 5 | `Result.java` | `common` | 统一返回结构 |
| 6 | `ResultCode.java` | `common` | 返回码枚举 |
| 7 | `PageResult.java` | `common` | 分页包装 |
| 8 | `BusinessException.java` | `exception` | service 抛异常 |
| 9 | `BookSaveRequest.java` | `dto` | 入参 + 校验 |
| 10 | `BookService.java` | `service` | 业务接口 |
| 11 | `BookServiceImpl.java` | `service.impl` | 业务实现 |
| 12 | `GlobalExceptionHandler.java` | `exception` | 异常统一处理 |
| 13 | `BookController.java` | `controller` | REST 接口 |
| 14 | `db/schema.sql` | `resources/db` | 建表 SQL |

每写完一组就 `./mvnw compile` 一下,失败立刻看到,不会拖到后期。

---

## 2. 各文件完整代码

### 2.1 `TmLibraryApplication.java`

**路径**: `src/main/java/com/tmt/TMLibrary/TmLibraryApplication.java`

```java
package com.tmt.TMLibrary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tmt.TMLibrary.mapper")  // 扫所有 mapper 接口
public class TmLibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmLibraryApplication.class, args);
    }
}
```

**关键点**:
- `@MapperScan` 一次性扫所有 mapper,不用在每个 mapper 接口上加 `@Mapper`
- 这是项目里**唯一**需要 `@SpringBootApplication` 的类

---

### 2.2 `Book.java` (entity)

**路径**: `src/main/java/com/tmt/TMLibrary/entity/Book.java`

```java
package com.tmt.TMLibrary.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Book {

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private BigDecimal price;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

**关键点**:
- 字段名用驼峰,XML 里列名是下划线(`create_time`),靠 `map-underscore-to-camel-case` 自动转
- **不加** MyBatis-Plus 注解(`@TableName` 等),纯 MyBatis 风格
- Lombok `@Data` 自动生成 getter/setter/toString/equals/hashCode

---

### 2.3 `BookMapper.java` (mapper 接口)

**路径**: `src/main/java/com/tmt/TMLibrary/mapper/BookMapper.java`

```java
package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.entity.Book;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BookMapper {

    int insert(Book book);

    int updateById(Book book);

    int deleteById(@Param("id") Long id);

    Book selectById(@Param("id") Long id);

    List<Book> selectList(@Param("keyword") String keyword,
                          @Param("offset") int offset,
                          @Param("size") int size);

    long selectCount(@Param("keyword") String keyword);
}
```

**关键点**:
- **不加** `@Mapper` — 已由 `@MapperScan` 统一处理
- 多参数必须加 `@Param`,否则 XML 里 `#{keyword}` 拿不到值(单参数可省略,但加上更清晰)
- 方法返回值:增删改返回 `int`(影响行数),查返回 `Book` 或 `List<Book>`,count 返回 `long`

---

### 2.4 `BookMapper.xml` (SQL)

**路径**: `src/main/resources/mapper/BookMapper.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.tmt.TMLibrary.mapper.BookMapper">

    <resultMap id="BookResultMap" type="com.tmt.TMLibrary.entity.Book">
        <id     column="id"          property="id"          />
        <result column="title"       property="title"       />
        <result column="author"      property="author"      />
        <result column="isbn"        property="isbn"        />
        <result column="price"       property="price"       />
        <result column="stock"       property="stock"       />
        <result column="create_time" property="createTime"  />
        <result column="update_time" property="updateTime"  />
    </resultMap>

    <sql id="Base_Column_List">
        id, title, author, isbn, price, stock, create_time, update_time
    </sql>

    <sql id="Where_Clause">
        <where>
            <if test="keyword != null and keyword != ''">
                AND title LIKE CONCAT('%', #{keyword}, '%')
            </if>
        </where>
    </sql>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO book (title, author, isbn, price, stock)
        VALUES (#{title}, #{author}, #{isbn}, #{price}, #{stock})
    </insert>

    <update id="updateById">
        UPDATE book
        <set>
            <if test="title  != null">title  = #{title},</if>
            <if test="author != null">author = #{author},</if>
            <if test="isbn   != null">isbn   = #{isbn},</if>
            <if test="price  != null">price  = #{price},</if>
            <if test="stock  != null">stock  = #{stock},</if>
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM book WHERE id = #{id}
    </delete>

    <select id="selectById" resultMap="BookResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM book
        WHERE id = #{id}
    </select>

    <select id="selectList" resultMap="BookResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM book
        <include refid="Where_Clause"/>
        ORDER BY id DESC
        LIMIT #{offset}, #{size}
    </select>

    <select id="selectCount" resultType="long">
        SELECT COUNT(*) FROM book
        <include refid="Where_Clause"/>
    </select>

</mapper>
```

**关键点**:
- `namespace` 必须和 mapper 接口全限定名**完全一致**
- `useGeneratedKeys="true" keyProperty="id"` → MySQL 自增主键回填到 `book.id`
- `<set>` + `<if>` 实现部分更新,只更新非 null 字段
- `#{...}` 是参数占位符(防 SQL 注入),`${...}` 是字符串替换(危险,别用)
- 分页用手写 `LIMIT offset, size`,不用 PageHelper/MyBatis-Plus
- `resultType="long"` 是 MyBatis 内置别名,可写

---

### 2.5 `Result.java`

**路径**: `src/main/java/com/tmt/TMLibrary/common/Result.java`

```java
package com.tmt.TMLibrary.common;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.msg  = ResultCode.SUCCESS.getMsg();
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg  = msg;
        return r;
    }

    public static <T> Result<T> fail(ResultCode rc, String msg) {
        return fail(rc.getCode(), msg);
    }
}
```

**关键点**:
- 统一返回 `{code, msg, data}` 结构,前端永远只看 `code === 200`
- 静态工厂方法 `success()` / `fail()`,不要让外部直接 `new Result<>`

---

### 2.6 `ResultCode.java`

**路径**: `src/main/java/com/tmt/TMLibrary/common/ResultCode.java`

```java
package com.tmt.TMLibrary.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
```

**关键点**:
- `code` 是**业务码**,跟 HTTP 状态码解耦 — 前端只看这个
- 用 enum 是为了类型安全,避免到处散落 `404` 这种 magic number

---

### 2.7 `PageResult.java`

**路径**: `src/main/java/com/tmt/TMLibrary/common/PageResult.java`

```java
package com.tmt.TMLibrary.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private List<T> records;
}
```

**关键点**:
- `total` = 总记录数(用于前端算总页数),`records` = 当前页数据
- 三个 Lombok 注解:`@Data`(全方法) + `@NoArgsConstructor`(无参构造,反序列化需要) + `@AllArgsConstructor`(方便 new)

---

### 2.8 `BusinessException.java`

**路径**: `src/main/java/com/tmt/TMLibrary/exception/BusinessException.java`

```java
package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String msg) {
        super(msg);
        this.code = rc.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
```

**关键点**:
- 继承 `RuntimeException`,这样事务能回滚(checked exception 不会)
- 三种构造方式覆盖三种调用风格,实际最常用第二种(`ResultCode + 自定义 msg`)

---

### 2.9 `BookSaveRequest.java` (dto)

**路径**: `src/main/java/com/tmt/TMLibrary/dto/BookSaveRequest.java`

```java
package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookSaveRequest {

    @NotBlank(message = "书名不能为空")
    @Size(max = 200, message = "书名长度不能超过 200")
    private String title;

    @NotBlank(message = "作者不能为空")
    @Size(max = 100, message = "作者长度不能超过 100")
    private String author;

    @NotBlank(message = "ISBN 不能为空")
    @Pattern(regexp = "^[0-9Xx-]{10,20}$", message = "ISBN 格式不正确")
    private String isbn;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能小于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    private Integer stock;
}
```

**关键点**:
- 校验注解来自 `jakarta.validation`(包名是 `jakarta` 不是 `javax`,Spring Boot 4.x 是 jakarta 命名空间)
- `@NotBlank` 校验字符串非 null 且非空,**`@NotNull` 不校验空字符串**,所以校验字符串用 `@NotBlank`
- `@NotNull` 校验 BigDecimal / Integer / Long 等数值类型
- `@Pattern` 用正则校验 ISBN 格式,`[0-9Xx-]{10,20}` 允许数字、X、横杠、10-20 位
- 触发校验失败需要 controller 上加 `@Valid`,异常会被 GlobalExceptionHandler 接住

---

### 2.10 `BookService.java` (业务接口)

**路径**: `src/main/java/com/tmt/TMLibrary/service/BookService.java`

```java
package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.common.PageResult;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;

public interface BookService {

    PageResult<Book> page(String keyword, int page, int size);

    Book getById(Long id);

    void create(BookSaveRequest req);

    void update(Long id, BookSaveRequest req);

    void delete(Long id);
}
```

**关键点**:
- interface + impl 是工程惯例,便于 mock 测试和 AOP 代理(@Transactional)
- 入参用 DTO,出参用 Entity(简单 CRUD 不必再封一层 VO)

---

### 2.11 `BookServiceImpl.java` (业务实现)

**路径**: `src/main/java/com/tmt/TMLibrary/service/impl/BookServiceImpl.java`

```java
package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.PageResult;
import com.tmt.TMLibrary.common.ResultCode;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    @Override
    public PageResult<Book> page(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        long total = bookMapper.selectCount(keyword);
        List<Book> records = bookMapper.selectList(keyword, offset, size);
        return new PageResult<>(total, records);
    }

    @Override
    public Book getById(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, id=" + id);
        }
        return book;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(BookSaveRequest req) {
        Book book = new Book();
        BeanUtils.copyProperties(req, book);
        bookMapper.insert(book);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BookSaveRequest req) {
        Book existing = getById(id);                       // 不存在直接抛 NOT_FOUND
        BeanUtils.copyProperties(req, existing);           // 不复制 null -> 部分更新
        existing.setId(id);                                // BeanUtils 不覆盖已有值
        bookMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        bookMapper.deleteById(id);
    }
}
```

**关键点**:
- `@Service` 让 Spring 管理这个 Bean
- `@RequiredArgsConstructor` (Lombok) 自动为 `final` 字段生成构造器,Spring 通过构造器注入(比 `@Autowired` 字段注入更推荐)
- `@Transactional(rollbackFor = Exception.class)` 写在**写操作**上 — `rollbackFor = Exception.class` 表示**任何异常都回滚**(默认只回滚 RuntimeException)
- `BeanUtils.copyProperties(req, existing)` **不复制 null 字段** → 部分更新只覆盖前端传来的字段
- 分页:`page` 从 1 开始(用户友好),数据库 offset 从 0 开始 → `(page - 1) * size`

---

### 2.12 `GlobalExceptionHandler.java`

**路径**: `src/main/java/com/tmt/TMLibrary/exception/GlobalExceptionHandler.java`

```java
package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleAny(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "服务器内部错误: " + e.getClass().getSimpleName());
    }
}
```

**关键点**:
- `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`,所有 controller 抛的异常都会被这里的方法拦截
- 多个 `@ExceptionHandler` 按异常类型匹配,Spring 选**最具体**的(子类优先)
- `@Valid` 校验失败抛 `MethodArgumentNotValidException`,从 `BindingResult` 里拿所有字段错误拼成提示
- 兜底 `Exception.class` 防止 NPE 之类直接暴露给前端
- `@Slf4j` 自动注入 `log` 字段(Lombok)

---

### 2.13 `BookController.java`

**路径**: `src/main/java/com/tmt/TMLibrary/controller/BookController.java`

```java
package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.PageResult;
import com.tmt.TMLibrary.common.Result;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public Result<PageResult<Book>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(bookService.page(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Book> getById(@PathVariable Long id) {
        return Result.success(bookService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid BookSaveRequest req) {
        bookService.create(req);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestBody @Valid BookSaveRequest req) {
        bookService.update(id, req);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return Result.success();
    }
}
```

**关键点**:
- `@RestController` 直接返回 JSON,不要用 `@Controller`(那是返回视图的)
- `@RequestMapping("/api/books")` 类级别前缀,方法注解上只写子路径
- `@RequestBody` 接 JSON 请求体,`@Valid` 触发 DTO 上的校验注解
- `@PathVariable` 接 URL 路径参数 `/api/books/{id}`,`@RequestParam` 接 `?key=value`
- `@RequestParam(defaultValue = "1")` 给默认值,避免空指针
- controller 三件事:**接参 → 调 service → 包装返回**,**不写任何业务逻辑**

---

### 2.14 `db/schema.sql`

**路径**: `src/main/resources/db/schema.sql`

```sql
CREATE DATABASE IF NOT EXISTS tmlibrary DEFAULT CHARACTER SET utf8mb4;
USE tmlibrary;

DROP TABLE IF EXISTS book;
CREATE TABLE book (
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    author      VARCHAR(100)  NOT NULL,
    isbn        VARCHAR(20)   NOT NULL UNIQUE,
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    stock       INT           NOT NULL DEFAULT 0,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO book (title, author, isbn, price, stock) VALUES
    ('深入理解Java虚拟机', '周志明',            '9787111641234', 129.00, 10),
    ('Spring实战(第5版)',   '克雷格·沃斯',       '9787115417305',  89.00, 15),
    ('算法导论',           'Thomas H.Cormen', '9787111407010', 128.00,  5);
```

**关键点**:
- `isbn` 用 `UNIQUE` 约束 → 数据库层防重复
- `price` 用 `DECIMAL(10,2)` → 钱**不要用** float/double(精度问题)
- `create_time` 用 `DEFAULT CURRENT_TIMESTAMP` 让数据库自动维护
- `ON UPDATE CURRENT_TIMESTAMP` 让任何 UPDATE 自动更新 `update_time`
- 跑完这个文件后,**手动执行一次**:
  ```bash
  mysql -u root -p < /home/tmt/JavaPractice/Maven/my-admin-project/TMLibrary/src/main/resources/db/schema.sql
  ```

---

## 3. 验证清单(每写完一组跑一次)

| 写完什么 | 跑什么 | 预期 |
|---|---|---|
| 启动类 + 任意 .java | `./mvnw compile` | BUILD SUCCESS |
| 加完 mapper 接口 + XML | 启动 + `curl http://localhost:8080/api/books?page=1` | 返回 200 + JSON |
| 加完 service | 启动 + 走一遍 5 个接口 | 增删改查全通 |
| 加完 controller + 全局异常 | 故意发非法请求触发校验 | 返回 `{code: 400, msg: "..."}` |

## 4. 启动 + 端到端验证

```bash
# 1. 启动
cd /home/tmt/JavaPractice/Maven/my-admin-project/TMLibrary
./mvnw spring-boot:run

# 2. 另开终端跑这些(预期全部返回 code=200)

# 列表
curl "http://localhost:8080/api/books?page=1&size=10"

# 搜索
curl "http://localhost:8080/api/books?keyword=Java"

# 详情
curl http://localhost:8080/api/books/1

# 新增
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"测试书","author":"我","isbn":"9787111111111","price":99.00,"stock":5}'

# 修改
curl -X PUT http://localhost:8080/api/books/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"新名字","author":"新作者","isbn":"9787111111111","price":50.00,"stock":3}'

# 删除
curl -X DELETE http://localhost:8080/api/books/1

# 故意触发校验失败
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title":"","author":"","isbn":"abc","price":-1,"stock":-5}'
# 预期: {"code":400,"msg":"title: 书名不能为空; ..."}
```

## 5. 容易掉的坑

1. **`BeanUtils.copyProperties` 不复制 null** — 部分更新时这是好事,但更新**主键字段**时要手动 `setId()`
2. **校验包名是 `jakarta.validation.*`** — Spring Boot 4.x 是 jakarta,不是 javax
3. **`@NotBlank` vs `@NotNull`** — 字符串用前者(校验 null + 空),数值用后者
4. **`@RequestParam` 没传会 400** — 加 `required = false` 或 `defaultValue`
5. **`#{...}` vs `${...}`** — 前者防 SQL 注入,后者是字符串拼接(几乎别用)
6. **BigDecimal 比较** — 用 `compareTo()`,别用 `==`
7. **事务加在 service,不在 controller** — 事务边界由业务层定义
8. **mapper XML 的 `namespace` 必须等于接口全限定名** — 写错会报 "binding not found"
9. **MySQL 密码已配在 `application-dev.yml`**,但如果想用其他用户/库,改这一行就行

## 6. 不需要做的事(留给以后)

- ❌ 引 Spring Security / JWT(P2 阶段)
- ❌ 引 MyBatis-Plus(偏好纯 MyBatis)
- ❌ 密码加密(P2)
- ❌ Flyway / Liquibase(单文件 schema.sql 够用)
- ❌ Hutool / Apache Commons(暂时不需要)
- ❌ Redis / 缓存

---

**写完任何一步想让我看 / 改 / 解释,直接说。**
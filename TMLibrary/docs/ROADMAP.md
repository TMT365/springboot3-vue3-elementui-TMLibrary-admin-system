# TMLibrary 学习路线图

> 最后更新:2026-08-08
> 目标:做一个合格的前后端分离项目,涵盖图书 CRUD、购买信息、用户信息,同时扎实掌握 Spring Boot 和 Vue 3。
> 当前阶段:**P0 已全部完成 ✅,下一步进入 P1(Book CRUD 完整化)**。

---

## 当前状态盘点(2026-08-09)

### ✅ 已完成(2026-08-06 ~ 2026-08-09)

| 层级 | 内容 |
|---|---|
| 项目骨架 | Spring Boot 4.1.0 + Java 25 + Maven wrapper,Lombok 注解处理器已挂载 |
| 配置 | `application.yml` + `dev/prod` profile,DB 凭据走环境变量;`mybatis.log-impl` 走 `@Value` 注入(2026-08-09 修);CORS 走 `app.cors.origins` 配置(2026-08-09 修) |
| 通用层 | `Result` / `ResultCode` / `PageResult` |
| 异常层 | `BusinessException` + `GlobalExceptionHandler`(已覆盖 BusinessException / DuplicateKeyException / MethodArgumentNotValidException / HttpMessageNotReadableException / MethodArgumentTypeMismatchException / **DateTimeParseException** / Exception 兜底)— 2026-08-09 新增 DateTimeParseException |
| 图书 CRUD | Entity + DTO + Mapper(**17 → 25 个方法**)+ XML(手写 SQL + `<if>` 动态 SQL + `<where>` + `<![CDATA[]]>`)+ Service + Controller(**15 → 19 个端点**),编译通过 |
| P1 第一阶段 | `GET /api/books`(多条件 query)+ `BookSearchRequest` DTO + `<sql id="BookSearchWhere">` 共享 WHERE 逻辑 |
| P1 第二阶段 | 时间粒度查询:`/search/{publishedDate\|CreatedTime\|UpdatedTime}/by?year=&month=&day=&hour=&minute=` + 半开区间 `[start, end)` SQL + Java 端算区间端点 |
| 数据库 | `schema.sql` 建 `book` 表 |
| 文档 | `Spring-Boot内部机制详解.md`(35 KB 中文笔记) |
| 工具 | `scripts/dev.sh` |

### ⚠️ 已识别但未修复的问题

| 问题 | 位置 | 优先级 |
|---|---|---|
| `BookMapper` 14 个搜索/筛选方法**部分**复活(精确匹配端点),剩**多条件 query 端点已用**(`/api/books`) | `mapper/BookMapper.java` | ✅ P1 第一子任务解决 |
| 完全没有测试 | `src/test/java/` 只有 `.gitkeep` | P1 第二子任务 |
| 没有用户/认证/购买/前端 | 整体缺失 | P2+ |

### 🔴 今日会话内产生的"认知产出"(2026-08-09)

- [x] 完整盘点项目结构与各模块状态
- [x] 识别出 6 类已写但未端点调用的 Mapper 方法
- [x] 修正一处事实错误:原本以为 `GlobalExceptionHandler` 没覆盖 `@Valid` 校验,实际已覆盖
- [x] 设计 P0–P6 学习路线图
- [x] 写定"先做 P0 + P1"的本周建议
- [x] ~~错误地建议把 `Book.id` 改 `long`~~,从 P0 移除
- [x] 补 `HttpMessageNotReadableException` handler(2026-08-08)
- [x] 补 `MethodArgumentTypeMismatchException` handler(2026-08-08)
- [x] `handleValidation` 改用 `ResultCode.BAD_REQUEST.getCode()`(2026-08-08)
- [x] `WebMvcConfig` 加 CORS(2026-08-08)
- [x] prod profile 日志脱敏(2026-08-08)
- [x] P0 启动验证(2026-08-08)
- [x] **P1 第一子任务**:`GET /api/books` 多条件 query + `<where>` 动态 SQL(2026-08-09)
- [x] **P1 第一子任务扩展**:3 个时间粒度端点(`/by`)+ 半开区间 SQL(2026-08-09)
- [x] **修 #1**:`DateTimeParseException` 转 400(2026-08-09)
- [x] **修 #3**:`MyBatisConfig` 手设 `Configuration.setLogImpl()`,SQL 日志生效(2026-08-09)
- [x] **修 #4**:`WebMvcConfig` CORS 走 `app.cors.origins` 配置(2026-08-09)
- [x] **修 #2**:`BookServiceImpl` 多余 `;;` 清理(2026-08-09,顺手)

---

## 路线图:P0 → P6

总策略:**单一资源 → 关联资源 → 认证 → 前端 → 联调 → 打磨**,每个阶段都有明确的"我学到了什么"产出。

| 阶段 | 主题 | 后端产出 | 前端产出 | 核心掌握点 |
|---|---|---|---|---|
| **P0** | 脚手架收尾 | 补 2 个 ExceptionHandler、CORS、类型统一、prod 日志脱敏 | — | 异常体系完整性、Profile 隔离 |
| **P1** | Book CRUD 完整化 | 14 个死代码复活、Controller 支持 query 搜索、补单测 | — | MyBatis `<if>` 动态 SQL、Query 参数绑定边界 |
| **P2** | User + JWT 认证 | `user` 表、登录接口、JWT Filter 或 Spring Security | — | 无状态认证、密码加盐、Filter 链路 |
| **P3** | Purchase 订单 | `order` + `order_item` 表、事务内扣库存+写订单、JOIN 查询 | — | **跨表事务、行锁、`<collection>` 嵌套映射** |
| **P4** | 前端骨架 | — | Vite + Vue 3 + TS + Element Plus + Pinia + Axios + Router | 项目分层、响应式、状态管理 |
| **P5** | 前后端联调 | API 文档化 | 登录页、图书管理、购书、订单、用户管理 | API 契约、跨域、Token 注入、错误码约定 |
| **P6** | 收尾打磨 | 统一 `PageQuery` 入参、日志切面 | 三态(Loading/Empty/Error)、表单校验反馈 | 工程化、可观测性、UX |

---

## P0 — 脚手架收尾 ✅ 已完成(2026-08-08)

> 目标:不写新功能,只补现有洞。完成后 `./mvnw spring-boot:run` 起来,前端 5173 端口能跨域调通任意一个接口。
>
> 实际产出:
> - `GlobalExceptionHandler` 增加 2 个 handler(`HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException`)
> - `handleValidation` 改用 `ResultCode.BAD_REQUEST.getCode()`,移除硬编码
> - `WebMvcConfig` 增加 CORS 配置(`/api/**` 允许 `localhost:5173`)
> - `handleAny` 通过 `@Value` 读取 profile,prod 环境不暴露异常类名
> - 启动 + curl 三连验证通过
> - **额外暴露的版本兼容问题(全部修复)**:
>   - `mybatis-spring-boot-starter 3.0.5` 与 Spring Boot 4.x 不兼容,`@AutoConfigureAfter` 引用的 `DataSourceAutoConfiguration` 包路径已变更
>   - 解决方案:`config/MyBatisConfig.java` 手写 `SqlSessionFactory` + `MapperScannerConfigurer`
>   - `BookMapper.xml` XML 声明不在第 1 行(被前置注释挡住),已修复
>   - SQL 中的 `<=` `>=` 需用 `<![CDATA[...]]>` 包裹,4 处已修复

### 子任务清单

- [x] `GlobalExceptionHandler` 补 2 个 handler
  - [x] `HttpMessageNotReadableException` → 400 + "请求体格式错误"
  - [x] `MethodArgumentTypeMismatchException` → 400 + "参数类型错误"
  - [x] `handleValidation` 改为 `ResultCode.BAD_REQUEST.getCode()` 而非硬编码 `400`

- [x] `WebMvcConfig` 加 CORS
  - [x] `addMapping("/api/**")` 允许 `http://localhost:5173`
  - [x] 允许 `Authorization` 头

- [x] `prod` profile 日志脱敏
  - [x] `application-prod.yml` 中 `handleAny` 不暴露异常类名
  - [x] 方案 A:用 `@Profile("dev")` 注解切两个 handler
  - [x] 方案 B:handler 里 `@Value("${app.debug:false}")` 判断

- [x] 启动验证
  - [x] `./mvnw spring-boot:run` 启动成功
  - [x] `curl localhost:8080/api/books` 返回正确的 `Result` 包结构(即使空 list)

### 学习锚点

每补一个 handler 都要问自己:**前端拿到这个响应能做什么?** 能回答才算完整。

---

## P1 — Book CRUD 完整化(预计 2–3 天)

> 目标:把 Mapper 里写好但没人调的 14 个方法真正串到 Controller,支持搜索/筛选/分页;补第一批单测。
>
> **进度(2026-08-09)**:第一子任务 + 时间粒度扩展已完成,剩单测。

### 子任务清单

- [x] Controller 改造:`GET /api/books` 支持 query 参数 `title` / `author` / `minPrice` / `maxPrice` / `minStock` / `maxStock` / `publishedDate` / `page` / `size`
- [x] Service 层参数归一化:空串 → null、负数 → null、`page` ≤ 0 → 1、`size` > 100 clamp 到 100
- [x] Mapper 调用对应动态 SQL(已存在,需 Service 引用)
- [x] 用 `<where>` 标签,避免 `WHERE 1=1` 丑法
- [x] **(扩展)** 时间粒度查询:year → minute 5 级,走半开区间 `[start, end)`,Java 算区间端点
- [ ] 单测
  - [ ] `BookMapper` 集成测试(`@SpringBootTest` + H2 或 Testcontainers):`selectListByTitle` 转义 `%` / `_`
  - [ ] `BookServiceImpl` 单测:空 query 返回全量、page=0 兜底、size=负数兜底、粒度连续性校验
  - [ ] `BookController` 切片测试(`@WebMvcTest`):验证 `Result` 包结构 + HTTP 状态码
- [x] SQL 日志生效(`MyBatisConfig` 手设 `Configuration.setLogImpl(StdOutImpl)`,2026-08-09 修)

### 学习锚点

- ✅ MyBatis `<where>` 怎么避免 `WHERE 1=1`(已落地)
- ✅ `<if>` 包装类型字段(可为 null)vs 基本类型(0 = 已传)的语义区别(已落地:`minPrice=-1` 兜底成 null 而不是 0)
- ✅ 半开区间 `[start, end)` vs `BETWEEN`(已落地:索引友好,Java 端算区间端点)
- ✅ 粒度连续性校验(已落地:DTO `compact()` 拒绝跳级粒度,转 400)

---

## P2 — User + JWT 认证(预计 3–4 天)

> 目标:加 `user` 表 + 登录接口,后续请求带 Token 验证身份。

### 子任务清单

- [ ] DB:新建 `user` 表(`id / username / password_hash / salt / role / created_time`)
- [ ] `schema.sql` 更新
- [ ] Entity / Mapper / Service / Controller for User
- [ ] 密码加盐 + BCrypt 哈希
- [ ] 选型:**手写 JWT Filter** vs **Spring Security**
  - [ ] 推荐先手写,理解底层后再切 Security 对比
- [ ] `POST /api/auth/login` → 返回 `{token, user}`
- [ ] `JwtAuthenticationFilter` 解析 `Authorization: Bearer <token>`
- [ ] 过滤器异常处理(Filter 异常在 DispatcherServlet 前,`@RestControllerAdvice` 接不到,需自己 try-catch)
- [ ] "当前用户"传递:ThreadLocal / RequestAttribute / 自定义注解
- [ ] 单测:登录成功/失败、Token 过期、签名错误

### 学习锚点

- 无状态认证和服务端 Session 的本质区别
- 为什么密码要加盐 + 慢哈希
- Filter 链路在 Spring MVC 请求生命周期里的位置

---

## P3 — Purchase 订单(预计 4–5 天)

> 这是含金量最高的一块,也是面试最爱问的。

### 子任务清单

- [ ] DB:`order` + `order_item` 两张表
- [ ] 关系:User(1) → Order(N) → OrderItem(N) → Book(1)
- [ ] Entity / Mapper / Service / Controller
- [ ] `POST /api/purchases` 核心逻辑(`@Transactional`)
  - [ ] `SELECT ... FOR UPDATE` 查库存(行锁)
  - [ ] 库存不足抛 `BusinessException`
  - [ ] 扣库存
  - [ ] 写 Order
  - [ ] 写 OrderItem(批量)
- [ ] JOIN 查询:`GET /api/users/{id}/purchases` 返回"用户 + 所有订单 + 每个订单的所有图书"
  - [ ] 用 `<collection>` 嵌套映射 resultMap
- [ ] 单测:超卖场景、事务回滚、JOIN 查询 DTO 装配

### 学习锚点

- `@Transactional` 的传播行为(默认 `REQUIRED`,审计场景用 `REQUIRES_NEW`)
- 行锁 vs 乐观锁:超卖怎么避免
- JOIN 映射 resultMap 的 `<collection>` 用法
- 事务边界 = 业务边界(为什么放 Service 不放 Controller)

---

## P4 — 前端骨架(预计 2–3 天)

```
frontend/
├── src/
│   ├── api/        # axios 封装 + 各资源 API
│   ├── stores/     # Pinia: user.ts / book.ts
│   ├── router/     # Vue Router + 守卫
│   ├── views/
│   │   ├── auth/Login.vue
│   │   ├── book/List.vue / Edit.vue
│   │   ├── purchase/List.vue / Create.vue
│   │   └── user/Manage.vue
│   ├── components/ # Pager / SearchBar / FormDialog
│   ├── layouts/    # AdminLayout(侧边栏 + 顶栏)
│   └── utils/      # request.ts(axios 实例 + 拦截器)
```

### 学习锚点(按顺序)

1. Composition API:`ref` / `reactive` / `computed` / `watch` 区别
2. Pinia 取代 Vuex 的 `defineStore` 风格
3. Axios 拦截器:请求自动塞 Token、响应统一拆 `Result<T>` 包、401 重定向登录
4. Vue Router `beforeEach` 检查 Pinia 里的 token

---

## P5 — 前后端联调(预计 2–3 天)

### 子任务清单

- [ ] 先写 API 文档(markdown 或 OpenAPI 都行)
- [ ] 前后端字段命名约定:后端驼峰 / 前端也驼峰(避免 snake_case)
- [ ] 错误码约定文档:401 / 403 / 4xx / 5xx 各自前端怎么处理
- [ ] 联调登录 → 列表 → 编辑 → 删除
- [ ] 联调购书流程
- [ ] 联调我的订单

---

## P6 — 收尾打磨(预计 2–3 天)

### 后端

- [ ] 统一 `PageQuery { page, size }` 入参对象,所有 Controller 改用它
- [ ] 关键业务方法(`purchase`)加 `@Around` 日志切面
- [ ] 异常日志结构化(JSON)

### 前端

- [ ] 列表页三态:Loading / Empty / Error
- [ ] 空状态有图标
- [ ] 错误状态有"重试"按钮
- [ ] 表单校验失败滚动到第一个错误字段

---

## 决策日志

| 日期 | 决策 | 原因 |
|---|---|---|
| 2026-08-08 | 用手写 JWT Filter 而非直接 Spring Security | 学习中先理解底层,后续再对比工业级方案 |
| 2026-08-08 | 暂不引入代码生成器(MyBatis-Plus Generator 等) | 手写 XML 阶段是必须经历的阵痛,跳过了基础不扎实 |
| 2026-08-08 | API 字段统一驼峰 | 减少前后端转换心智负担 |
| 2026-08-08 | **修正**:`Book.id` 维持 `int`,不统一为 `long` | 数据库用 `SMALLINT UNSIGNED`(0–65535),Java `int`(±21 亿)覆盖无压力,统一 `long` 是过度设计。原"防溢出"判断错误,从 P0 移除。 |
| 2026-08-08 | **`mybatis-spring-boot-starter 3.0.5` 与 Spring Boot 4.x 不兼容** | starter 内部的 `@AutoConfigureAfter` 引用了 SB 3.x 的旧包路径,SB 4.x 已搬走该类,导致 MyBatis 自动配置静默跳过。同时 `CoreConfiguration.applyTo()` 内部用了 SB 4.x 已删除的 `PropertyMapper.alwaysApplyingWhenNonNull()`。当前解决方案:写 `MyBatisConfig` 手装配,等 MyBatis 出新版适配 SB 4.x 后删除。 |
| 2026-08-08 | 后续排查**不能靠推断,要先跑起来看实际日志** | 第一轮诊断我推断"`@MapperScan` 缺失",用户坚持"运行一下"才暴露真正的版本兼容问题。**涉及 Spring auto-config / classloader / 反射注解的判断,唯一可靠方法就是执行验证**。 |
| 2026-08-09 | **SQL 日志修复:不走 `mybatis-spring-boot-starter` 的 `applyTo()`,直接 `Configuration.setLogImpl()`** | yml 里 `mybatis.configuration.log-impl` 配置链路已断(starter 跳过 + `applyTo()` 不能调),改由 `MyBatisConfig` 直接 new 原生 `Configuration` 并 setLogImpl,绕开 starter 的 SB 4.x 不兼容 API。配置项移到 `mybatis.log-impl`(自定义),默认 `StdOutImpl` |
| 2026-08-09 | **DTO `compact()` 模式作为参数归一化的标准做法** | 把"空串 → null、负数 → null、page/size 兜底"放进 DTO 自己的方法,而不是 Service。原因:① 单一职责;② 可单测(`new XxxRequest().compact()` 即可,无需 Spring 上下文);③ 风格一致。**关键点:负数兜底成 null 而非 0** —— 用户写 `?minPrice=-1` 想表达"无下限",转 0 就成了"免费起步",改变语义 |
| 2026-08-09 | **时间粒度走半开区间 `[start, end)`,Java 端算区间端点** | vs `YEAR()/MONTH()` 函数。范围查询索引友好(B-tree range scan),不需要 functional index。Java 端用 `LocalDate.plusYears/Months/Days` 自动处理跨年/跨月/跨日边界(如 `2024-12-31.plusDays(1) = 2025-01-01`) |
| 2026-08-09 | **`@ModelAttribute` 自动绑定 query 参数到 POJO** | Controller 用 `@ModelAttribute BookSearchRequest query` 替代一堆 `@RequestParam`,Spring 自动按字段名绑定。空串、缺失字段都给 null,不抛错。`Integer` 包装类型区分"未传"和"传了 0"。这是 P6 "统一 `PageQuery`" 的预演 |
| 2026-08-09 | **粒度连续性校验:严格从大到小,不能跳级** | 例:`year=2024&day=15` 无 month → 400;`hour=10` 无 day → 400。原因:跳级粒度语义不清(月 0 不存在),且 SQL 拼出来可能查全表(性能灾难)。校验放在 DTO `compact()`,抛 `BusinessException(BAD_REQUEST)` → GlobalExceptionHandler 转 400 |
| 2026-08-09 | **Java 编译错误会级联报错到 Lombok 注解处理失败** | 今天 `MyBatisConfig` 双 `Configuration` 导入冲突导致整个编译失败,GlobalExceptionHandler 的 `log`、BookServiceImpl 的 `setCreatedTime` 等 Lombok 生成方法全部报"cannot find symbol"。**根因:javac 编译错误会级联**,而 Lombok 注解处理是在错误状态下跳过的。**教训:看到一批 Lombok "cannot find symbol" 错误,先查其他文件的硬错误,不要直接怀疑 Lombok 配置** | |

---

## 怎么继续(下次启动时)

1. 打开 `docs/ROADMAP.md` → 看 **当前状态盘点** 确认上次进度
2. 看 **P1 子任务清单**,挑下一个未完成的 `[ ]` 开始
3. 完成后勾掉该项,并在 **决策日志** 里追加新的判断
4. **不要** 直接跳阶段;每个阶段结束做一次完整 demo 才推进

### 当前下一步建议:**P1 第二子任务 — 单测**

预计改动:新增 `src/test/java/com/tmt/TMLibrary/` 目录 + 3 个测试类(Mapper 集成测试 / Service 单测 / Controller 切片测试),约 200 行。
- **Mapper 集成测试**:用 H2 内存数据库(`@SpringBootTest`),覆盖 `selectListByTitle` LIKE 转义、空 query 全量
- **Service 单测**:Mock BookMapper,验证 `compact()` 行为、空 query → 全量、page=0 兜底、size 负数兜底、size>100 clamp、粒度跳级 → BusinessException
- **Controller 切片测试**:`@WebMvcTest` + MockBean BookService,验证 `Result` 包结构 + HTTP 状态码 + 400 路径

预计 3-4 小时。改完后 `./mvnw test` 全绿即 P1 收尾,可以进 P2。

---

## 学习方法备忘

1. **先读官方文档,再 ChatGPT**。Spring Boot Reference、Vue 3 教程、Pinia 文档都比二手文章准。
2. **每个阶段都"故意搞坏它一次"**。把 `@Transactional` 去掉看会怎样,把 JWT Filter 注释掉看会怎样。
3. **Mapper 写完一定要打 SQL 日志看实际生成的 SQL**。`application.yml` 已配 `StdOutImpl`,每个复杂查询都盯着看。
4. **每阶段结束给自己一个 demo**:P1 用 curl 调通所有搜索条件,P2 用 Postman 带 Token 调通,P3 完整走一遍下单。
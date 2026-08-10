# Spring Boot 内部机制详解 —— 以 TMLibrary 项目为例

> 目的：把项目里那些 `@Service`、`@Mapper`、`@Transactional`、`@RestControllerAdvice` 背后到底发生了什么，**从一次请求发起到响应回来**完整讲清楚。
>
> 阅读对象：能跟着 `CLAUDE.md` 跑起来项目，但想搞清楚"框架到底干了啥"的同学。

---

## 目录

- [0. 整体地图：项目里到底有哪些文件](#0-整体地图项目里到底有哪些文件)
- [1. Spring Boot 启动全过程](#1-spring-boot-启动全过程)
- [2. IOC：控制反转到底是什么](#2-ioc控制反转到底是什么)
- [3. DI：依赖注入的三种姿势](#3-di依赖注入的三种姿势)
- [4. AOP：面向切面编程](#4-aop面向切面编程)
- [5. 为什么 CORS 会拦住前端到后端](#5-为什么-cors-会拦住前端到后端)
- [6. 一次完整请求的生命周期（结合本项目）](#6-一次完整请求的生命周期结合本项目)
- [7. 自我验证：拿项目代码对着文档看](#7-自我验证拿项目代码对着文档看)

---

## 0. 整体地图：项目里到底有哪些文件

先把项目结构摆开，然后用一个具体的请求走一遍每层的作用。

```
TMLibrary/src/main/
├── java/com/tmt/TMLibrary/
│   ├── TmLibraryApplication.java     ← 启动入口（main 方法在这里）
│   ├── config/
│   │   └── WebMvcConfig.java          ← CORS 等 MVC 配置
│   ├── controller/
│   │   └── BookController.java        ← 接收 HTTP 请求（@RestController）
│   ├── service/
│   │   ├── BookService.java           ← 业务接口（抽象定义）
│   │   └── impl/
│   │       └── BookServiceImpl.java   ← 业务实现（@Service + @Transactional）
│   ├── mapper/
│   │   └── BookMapper.java            ← MyBatis mapper（@Mapper）
│   ├── entity/
│   │   └── Book.java                  ← 数据库表的 Java 映射（@Data）
│   ├── dto/
│   │   └── BookSaveRequest.java       ← 入参校验（@NotBlank 等）
│   ├── common/Result/
│   │   ├── Result.java                ← 统一响应包装 {code, msg, data}
│   │   ├── ResultCode.java            ← 状态码枚举
│   │   └── PageResult.java            ← 分页结果
│   └── exception/
│       ├── BusinessException.java     ← 业务异常（手动抛）
│       └── GlobalExceptionHandler.java ← 全局异常拦截（@RestControllerAdvice）
└── resources/
    ├── application.yml                ← Spring 主配置
    ├── application-dev.yml            ← dev 环境配置
    ├── mapper/
    │   └── BookMapper.xml             ← 手写 SQL（和 BookMapper.java 一一对应）
    └── db/
        └── schema.sql                 ← 建表脚本
```

### 一句话概括每层职责

| 层 | 谁负责 | 例子 |
|---|---|---|
| **Controller** | 收 HTTP 请求、做参数校验、决定返回什么 HTTP 状态 | `BookController.list()` 接收 `GET /api/books?page=1&size=10` |
| **Service** | 业务逻辑（事务在这里管） | `BookServiceImpl.create()` 校验库存、把 DTO 转 Entity |
| **Mapper** | 数据库访问（纯方法签名，SQL 在 XML） | `bookMapper.insertBook(book)` 执行 `<insert>` 标签 |
| **Entity** | 表的 Java 形状（getter/setter） | `Book.java` 对应 `book` 表 |
| **DTO** | 入参形状，带校验注解 | `BookSaveRequest` 上的 `@NotBlank` 触发 400 |
| **Exception** | 把异常翻译成统一响应 | `BusinessException("图书不存在")` → `404` |

---

## 1. Spring Boot 启动全过程

### 1.1 从 `main()` 出发

`TmLibraryApplication.java` 整个就这一段：

```java
@SpringBootApplication
public class TmLibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TmLibraryApplication.class, args);
    }
}
```

`@SpringBootApplication` 是一个"复合注解"，相当于同时打了三个注解：

| 元注解 | 作用 |
|---|---|
| `@SpringBootConfiguration` | 标记这是个 Spring Boot 配置类（基本就是 `@Configuration`） |
| `@EnableAutoConfiguration` | **核心**：让 Spring Boot 根据 classpath 里的 jar 自动装配 Bean |
| `@ComponentScan` | 扫描当前包（`com.tmt.TMLibrary`）下所有 `@Component`、`@Service`、`@Repository`、`@Controller` 等 |

### 1.2 `SpringApplication.run()` 内部到底干了什么

简化版的启动流程：

```
main()
  ↓
SpringApplication.run(TmLibraryApplication.class, args)
  ↓
1. 创建 SpringApplication 实例
   - 推断应用类型：检测到 spring-boot-starter-webmvc → Web 应用（Servlet）
   - 加载所有 spring.factories / spring.application.imports 里声明的初始化器
   - 加载所有 ApplicationListener（事件监听器）
  ↓
2. 调用 run() 方法
   ↓
3. 创建 ApplicationContext（这里是 AnnotationConfigServletWebServerApplicationContext）
   - 加载 application.yml、application-dev.yml（按 profile 激活）
   - 合并配置（dev 覆盖主文件）
   ↓
4. ★★★ 触发自动装配（@EnableAutoConfiguration）★★★
   - 看到 classpath 里有：
     · mybatis-spring-boot-starter → 自动配置 SqlSessionFactory、DataSource、MapperScanner
     · spring-boot-starter-webmvc → 自动配置 DispatcherServlet、嵌入式 Tomcat
     · spring-boot-starter-validation → 自动配置 MethodValidationPostProcessor
   - 这些"自动配置类"在 META-INF/spring/...AutoConfiguration.imports 里列着
  ↓
5. Bean 定义扫描
   - @ComponentScan 从 com.tmt.TMLibrary 往下扫
   - 发现：
     · @SpringBootApplication 标记的 TmLibraryApplication 本身
     · @Configuration 标记的 WebMvcConfig
     · @RestController 标记的 BookController
     · @Service 标记的 BookServiceImpl
     · @Mapper 标记的 BookMapper
     · @RestControllerAdvice 标记的 GlobalExceptionHandler
   - 给每个类生成一个 BeanDefinition（"如何创建这个 Bean 的蓝图"）
  ↓
6. ★★★ 实例化所有单例 Bean ★★★
   - 按依赖关系排序：先创建没有依赖的，再创建依赖它们的
   - 比如 BookMapper 没有依赖，先创建；BookServiceImpl 依赖 BookMapper，后创建
   - 创建过程中：
     · BeanPostProcessor 介入（AOP 代理在这里生成）
     · @Autowired / 构造器注入在这里执行
     · @PostConstruct 在这里回调
   ↓
7. 启动内嵌 Web 服务器（Tomcat）
   - 端口默认 8080（来自 server.port，或 spring-boot-starter-webmvc 默认）
   - 把 DispatcherServlet 注册到 Tomcat
   ↓
8. 发布 ApplicationReadyEvent
   - 触发所有监听这个事件的 Listener
   - 控制台打印：
     ║  Tomcat started on port 8080
     ║  Started TmLibraryApplication in 2.34 seconds
  ↓
9. main() 阻塞，主线程进入 wait
   - 此时 Tomcat 在自己的线程池里监听 8080 端口
   - 整个 JVM 不退出，因为有非守护线程（Tomcat）活着
```

### 1.3 Bean 的生命周期（单例）

Bean 从出生到死亡经历这些阶段（**只对单例 Bean 有效**，单例是默认作用域）：

```
BeanDefinition 蓝图
  ↓
实例化（new 出来，或工厂方法）
  ↓
属性注入（@Autowired、构造器参数等）★ 这里循环依赖会被检测
  ↓
BeanNameAware / ApplicationContextAware 等回调
  ↓
BeanPostProcessor.postProcessBeforeInitialization（初始化前）
  ↓
@PostConstruct 方法
  ↓
InitializingBean.afterPropertiesSet()（实现了这个接口的话）
  ↓
自定义 init-method
  ↓
BeanPostProcessor.postProcessAfterInitialization（初始化后）★ AOP 代理在这里创建
  ↓
★ Bean 就绪，可以被注入和使用 ★
  ↓
... 应用运行期间 ...
  ↓
容器关闭时
  ↓
@PreDestroy 方法
  ↓
DisposableBean.destroy()
  ↓
自定义 destroy-method
```

**关键点**：AOP 代理在第 7 步之后才生成。这就是为什么 `@Transactional` 注解的方法被调用时，事务才"生效"——其实不是"生效"，是被代理对象拦截了。

---

## 2. IOC：控制反转到底是什么

### 2.1 名字的由来

传统写法：

```java
// 你手动 new 一个 BookMapper
BookMapper bookMapper = new BookMapperImpl();
// 你手动 new 一个 BookService，把 mapper 塞进去
BookService bookService = new BookServiceImpl(bookMapper);
// 你手动 new 一个 BookController，把 service 塞进去
BookController bookController = new BookController(bookService);
```

**问题**：谁负责把"依赖"串起来？写代码的人。三个对象彼此耦合：改一个的构造器，所有引用都要改。

IOC 的做法：**把"组装依赖"这件事从你的代码里抽出来，交给一个容器（Spring 容器）去做**。你只负责声明"我需要啥"，容器负责"给你准备好"。

```java
// 你只需要写：
@Service
public class BookServiceImpl implements BookService {
    private final BookMapper bookMapper;
    public BookServiceImpl(BookMapper bookMapper) {  // ← 容器会自动把 BookMapper 传进来
        this.bookMapper = bookMapper;
    }
}
```

控制反转：原来由你控制的"对象创建和组装"，反转给容器控制。

### 2.2 Spring 容器到底是个啥

`ApplicationContext` 就是 Spring 容器（`BeanFactory` 是它的简化版）。它本质上是一个 **Map<String, Object>** —— Bean 名字到 Bean 实例的映射。

启动后，容器里大概装着这些 Bean（简化）：

```
┌─────────────────────────────────────────────────────────┐
│ ApplicationContext (容器)                              │
├─────────────────────────────────────────────────────────┤
│ bookController     → BookController 实例                │
│ bookServiceImpl    → BookServiceImpl 实例（不是 BookService！）│
│ bookMapper         → BookMapper 代理对象（CGLIB/JDK）    │
│ bookService        → 同上，指向同一个 bookServiceImpl    │
│ globalExceptionHandler → GlobalExceptionHandler 实例    │
│ webMvcConfig       → WebMvcConfig 实例                  │
│ dataSource         → HikariDataSource（连接池）          │
│ sqlSessionFactory  → MyBatis SqlSessionFactory         │
│ transactionManager → PlatformTransactionManager        │
│ ...                                                    │
└─────────────────────────────────────────────────────────┘
```

### 2.3 注解的"父子关系"

```
@Component  ← 通用：标记"我是 Spring 管理的对象"
   ├── @Service       ← 业务层（语义化，框架对它有额外处理，比如事务扫描）
   ├── @Repository    ← 数据访问层（自动把 SQL 异常转 Spring 的 DataAccessException）
   ├── @Controller    ← MVC 控制器（返回视图名）
   │       └── @RestController  ← 返回 JSON 而非视图（= @Controller + @ResponseBody）
   └── @Configuration ← 配置类（里面 @Bean 方法返回的对象也会被容器管理）
```

所有这些注解最终都被 `@ComponentScan` 一视同仁地扫到注册进容器。区别只是"语义 + 框架针对性处理"。

### 2.4 Bean 的作用域（Scope）

默认 `singleton`（单例，整个应用共享一个实例）。其他还有：

| Scope | 说明 | 用例 |
|---|---|---|
| `singleton`（默认） | 容器里就一份 | 99% 的 Bean |
| `prototype` | 每次 `getBean()` 都 new 一个 | 有状态的临时对象 |
| `request` | 每个 HTTP 请求一份 | Web 层用 |
| `session` | 每个 HTTP session 一份 | 用户状态时 |

`BookServiceImpl` 是单例，所以它**不能有"实例字段保存业务数据"**。它只能通过参数接收数据。这是单例模式的约束。

---

## 3. DI：依赖注入的三种姿势

依赖注入（DI）是 IOC 的一种实现方式：容器把依赖对象"注入"给你的对象。

### 3.1 构造器注入（推荐，**项目里用的就是这种**）

```java
@Service
public class BookServiceImpl implements BookService {
    private final BookMapper bookMapper;
    public BookServiceImpl(BookMapper bookMapper) {  // ← 容器调这个构造器
        this.bookMapper = bookMapper;
    }
}
```

**优点**：
- `final` 字段保证依赖不可变，线程安全
- 启动时强制注入（如果 `BookMapper` 不存在，启动失败，而不是运行到一半 NPE）
- 容易写单元测试（直接 `new BookServiceImpl(mockMapper)`）

### 3.2 Setter 注入

```java
@Service
public class BookServiceImpl implements BookService {
    private BookMapper bookMapper;
    @Autowired
    public void setBookMapper(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }
}
```

允许循环依赖（但循环依赖本身是设计问题）。Spring 官方不再推荐。

### 3.3 字段注入（**最懒，但不推荐**）

```java
@Service
public class BookServiceImpl implements BookService {
    @Autowired
    private BookMapper bookMapper;  // 直接注入字段
}
```

**缺点**：
- 不能 `final`
- 没法绕过构造器测试
- 隐藏了依赖关系，看不出来类到底依赖啥

**项目里已经避开了这个坑**：所有 Bean 都用构造器或 `@RequiredArgsConstructor`（Lombok 生成构造器）。

### 3.4 注入怎么发生的（构造器注入的微观过程）

```java
// 1. 容器扫到 BookServiceImpl 有 @Service
// 2. 容器发现它需要一个 BookMapper 参数
// 3. 容器去容器里查"bookMapper"这个名字 → 找到实例
// 4. 容器反射调用 BookServiceImpl(BookMapper) 构造器
// 5. BookServiceImpl 实例创建好，放进容器（key = "bookServiceImpl"）
```

如果容器找不到 `BookMapper`？启动失败：

```
***************************
APPLICATION FAILED TO START
***************************
Parameter 0 of constructor in BookServiceImpl required a bean of type 'BookMapper' that could not be found.
```

---

## 4. AOP：面向切面编程

### 4.1 问题引出

看 `BookServiceImpl.create()`：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void create(BookSaveRequest request) {
    // 业务代码：把 DTO 转 Entity，调用 mapper
    bookMapper.insertBook(book);
}
```

只有一行 `@Transactional(rollbackFor = Exception.class)`。这个注解是**怎么生效的**？打开数据库、提交事务、回滚事务这些"横切"逻辑，是谁在什么时机插入到 `bookMapper.insertBook(book)` 调用的前后？

答：AOP（Aspect-Oriented Programming）。

### 4.2 AOP 的核心术语

想象你有一块业务代码：

```
clientController.list()  ─┐
                          ├─→  BookServiceImpl.page()  ─┐
                                                        ├─→  BookMapper.selectList()
clientController.create() ─┘                             │
                          └─→  BookServiceImpl.create() ─┘
```

横切关注点（cross-cutting concerns）是要在**很多方法**前后都做的事情：

- **日志**：每个方法都要记录"谁、什么时候、传了什么参数"
- **事务**：每个写方法都要"开启 → 提交 / 回滚"
- **权限**：每个方法都要检查"当前用户能不能调"
- **性能监控**：每个方法都要记录耗时

这些如果每个方法都手写一遍，太啰嗦。AOP 的思路：**把横切逻辑抽出来，声明"切在哪些方法的哪些位置"，框架帮你织入**。

```
            ┌──────── 切面 (Aspect) ────────┐
            │                                │
            │  切点 (Pointcut):  │
            │    execution(* BookServiceImpl.*(..))  │
            │                                │
            │  通知 (Advice):                │
            │    @Before("pointcut()")  │
            │    @After("pointcut()")   │
            │    @Around("pointcut()")  │
            │                                │
            └────────────────────────────────┘
                       ↓ 织入 (Weaving)
   BookServiceImpl.page()   BookServiceImpl.create()
   ┌────────────────────┐  ┌────────────────────────┐
   │ @Before 开始       │  │ @Before 开始           │
   │ 业务代码           │  │ 业务代码               │
   │ @After 结束        │  │ @After 结束             │
   └────────────────────┘  └────────────────────────┘
```

### 4.3 代理（Proxy）：AOP 是怎么"插入"的

AOP 不是改你的源码，而是在运行时**生成一个代理对象**替代原对象。当别人调用你的方法时，其实调用的是代理。

Spring 用两种代理技术：

| 技术 | 适用 | 原理 |
|---|---|---|
| **JDK 动态代理** | 目标类**实现了接口** | `Proxy.newProxyInstance()` 生成实现了同样接口的代理类 |
| **CGLIB 字节码增强** | 目标类**没有接口**（或强制） | 生成目标类的子类，覆盖方法 |

`BookServiceImpl implements BookService` 有接口 → JDK 动态代理。
`BookMapper` 是接口，MyBatis 用 JDK 动态代理生成 mapper 实现。
`GlobalExceptionHandler` 没有接口只有注解 → CGLIB。

**生成代理的时机**：在 Bean 生命周期第 7 步"BeanPostProcessor.postProcessAfterInitialization"之后。容器发现某个 Bean 需要 AOP 增强，就返回一个包装后的代理对象放进容器。

之后所有 `bookService.page(...)` 调用，都是调代理对象，代理对象拦截后再调真实对象。

### 4.4 项目里的 AOP 实战

#### 4.4.1 `@Transactional` —— Spring 自带的切面

你写的代码：
```java
@Transactional(rollbackFor = Exception.class)
public void create(BookSaveRequest request) { ... }
```

Spring 在启动时扫描所有带 `@Transactional` 的方法，生成代理。代理里的逻辑大致是：

```
调用 create() 之前:
  1. 从 TransactionManager 获取一个 Connection
  2. 设置 autoCommit = false
  3. 把 Connection 绑到当前线程 (ThreadLocal)

调用真正的 create():
  4. 执行你的业务代码（包括 bookMapper.insertBook）
     —— mapper 用的 Connection 就是上面绑的那个

调用之后:
  5. 没异常 → commit()
  6. 有异常 → 看 @Transactional(rollbackFor = ...) 配置
     → 你的设置是 Exception.class（任何异常都回滚）→ rollback()
  7. 关闭 Connection
```

这就是为什么：
- 事务方法必须是 `public`（CGLIB 代理子类覆盖不了 private 方法）
- 自调用（`this.create()`）不会触发事务（绕过代理了）
- 默认只回滚 RuntimeException，加 `rollbackFor = Exception.class` 才覆盖所有

#### 4.4.2 `@RestControllerAdvice` —— 全局异常处理

你写的 `GlobalExceptionHandler` 也是一个"切面"，但 Spring 是用另一种机制实现的：`HandlerExceptionResolver`。

```
Controller 方法抛异常
       ↓
DispatcherServlet 捕获
       ↓
遍历所有 @ExceptionHandler 方法，找匹配的
       ↓
匹配 BusinessException → handleBusiness()
匹配 DuplicateKeyException → handleDuplicateKey()
匹配 MethodArgumentNotValidException → handleValidation()
兜底: Exception → handleAny()
       ↓
返回 Result 对象 → 转 JSON → 响应给客户端
```

这个机制不是 AOP，但思想相似：把"异常处理"这种横切关注点集中起来。

#### 4.4.3 如果你自己想写一个 AOP

例：每个 Service 方法都打日志。

```java
@Aspect
@Component
public class ServiceLogAspect {

    @Around("execution(* com.tmt.TMLibrary.service..*.*(..))")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        log.info("→ {} 开始", method);
        try {
            Object result = pjp.proceed();  // ← 这里调用真实方法
            log.info("← {} 结束, 耗时 {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.warn("✗ {} 抛异常: {}", method, e.getMessage());
            throw e;
        }
    }
}
```

加 `@Aspect` 和 `@Component`（后者让 Spring 扫到这个切面），就生效了。无需改任何业务代码。

---

## 5. 为什么 CORS 会拦住前端到后端

### 5.1 同源策略（Same-Origin Policy）

浏览器有一个**内置安全策略**：默认情况下，JS 发起的跨域 HTTP 请求会被拦截。

什么是"同源"？

```
http://localhost:5173/api/books
          ↓        ↓      ↓
        协议     主机名   端口
```

三个全一样才算同源。

| URL | 协议 | 主机名 | 端口 | 和上面同源？ |
|---|---|---|---|---|
| `http://localhost:5173/api/users` | http | localhost | 5173 | ✅ 同源 |
| `http://localhost:5173/api/books` | http | localhost | 5173 | ✅ 同源 |
| **`http://localhost:8080/api/books`** | **http** | **localhost** | **8080** | ❌ **跨域** |
| `https://localhost:5173/api/books` | https | localhost | 5173 | ❌ 跨域（协议不同） |

你的情况：前端 `http://localhost:5173`，后端 `http://localhost:8080` → **端口不同 → 跨域 → 浏览器拦**。

### 5.2 拦的不是请求，是响应

关键点：**浏览器是把请求发出去了，收到响应后检查响应头，没有允许跨域的头就丢弃响应、报错**。

所以你可能看到：
- 后端日志显示请求正常到达、正常返回
- 前端 console 报：`No 'Access-Control-Allow-Origin' header is present on the requested resource`
- Network 面板里 status 可能是 200，但响应体是空的，浏览器拒收

后端没毛病，是浏览器"自作主张"。

### 5.3 简单请求 vs 预检请求

不是所有跨域请求都会被预检。浏览器分两类：

#### 简单请求（直接发）

满足以下所有条件：
- 方法是 GET / HEAD / POST
- Content-Type 是 text/plain、multipart/form-data、application/x-www-form-urlencoded 三选一
- 没有自定义头

简单请求浏览器直接发，然后在响应里检查 `Access-Control-Allow-Origin`。

#### 预检请求（Preflight，先发 OPTIONS 试探）

**不满足**上面任一条件的，就是"非简单请求"。比如你的 `POST /api/books` 带 `Content-Type: application/json`，就是要预检的。

流程：

```
浏览器                                    后端
  │                                          │
  ├─ OPTIONS /api/books ────────────────────→│
  │  Origin: http://localhost:5173            │
  │  Access-Control-Request-Method: POST      │
  │  Access-Control-Request-Headers:           │
  │    content-type                            │
  │                                          │
  │← 200 OK ─────────────────────────────────┤
  │  Access-Control-Allow-Origin:              │
  │    http://localhost:5173                   │
  │  Access-Control-Allow-Methods: POST        │
  │  Access-Control-Allow-Headers: content-type│
  │                                          │
  ├─ POST /api/books ───────────────────────→│
  │  (真正的请求)                               │
  │                                          │
  │← 200 OK (带 CORS 头) ─────────────────────┤
```

**预检没过 = 浏览器连真正的请求都不发**。这就是为什么很多人看到"前端怎么都调不通"——OPTIONS 那一关就被拦了。

### 5.4 项目里 `WebMvcConfig` 到底干了啥

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

| 配置 | 作用 |
|---|---|
| `addMapping("/api/**")` | 只对 `/api/**` 路径生效，其他路径不管 |
| `allowedOrigins(...)` | 允许来自 `http://localhost:5173` 的请求。响应头会带 `Access-Control-Allow-Origin: http://localhost:5173` |
| `allowedMethods(...)` | 允许的方法。**必须包含 OPTIONS**，否则预检过不去 |
| `allowedHeaders("*")` | 允许任何请求头。响应头会带 `Access-Control-Allow-Headers: *` |
| `allowCredentials(true)` | 允许带 cookie / Authorization 头。响应头会带 `Access-Control-Allow-Credentials: true`（注意：开了这个之后 `allowedOrigins` 不能是 `*`） |
| `maxAge(3600)` | 浏览器缓存预检结果 3600 秒（一小时内重复发请求不重新预检） |

启动后 Tomcat 收到任何 `/api/**` 请求时，会自动处理 CORS：

- 普通请求 → 加上 4 个 CORS 响应头
- OPTIONS 请求 → 直接 200 OK 返回 CORS 头，不进 Controller

### 5.5 为什么项目之前没 CORS 配置就 500

```
浏览器                            Spring
  │                                  │
  ├─ OPTIONS /api/books ────────────→│
  │  Origin: http://localhost:5173    │
  │                                  │
  │  Spring 没特殊处理 OPTIONS，     │
  │  走默认 → 404 / 401 / 405        │
  │                                  │
  │← 404 ────────────────────────────┤
  │  (没有 CORS 头)                  │
  │                                  │
  │ 浏览器: "预检没过，不发真请求"   │
  │ 前端看到: "Network Error"        │
```

修了 `WebMvcConfig` 之后，预检请求会拿到 200 + CORS 头，真正的 POST/GET 才能继续。

---

## 6. 一次完整请求的生命周期（结合本项目）

以"前端 Vue 发起 `POST http://localhost:8080/api/books` 创建一本书"为例，把前面所有概念串起来：

```
[Vue 前端]
  fetch('/api/books', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({title: '深入理解Java', author: '周志明', isbn: '9787121360000', price: 99, stockQuantity: 10, publishedDate: '2024-01-15'})
  })
  ↓
[浏览器]
  1. 检测到跨域（5173 → 8080）
  2. 这是非简单请求（Content-Type: application/json），触发预检
  3. 发 OPTIONS 请求
  ↓
[Tomcat :8080]
  4. DispatcherServlet 接收 OPTIONS
  5. 走到 CorsConfigurationSource（由 WebMvcConfig 配置）
  6. 命中 /api/** + allowedOrigins + allowedMethods(含OPTIONS) + allowedHeaders(*)
  7. 直接返回 200 + 4 个 CORS 头，不进 Controller
  ↓
[浏览器]
  8. 预检通过，缓存配置
  9. 发真正的 POST 请求，带 body
  ↓
[Tomcat :8080 → DispatcherServlet]
  10. 根据 URL /api/books 找 HandlerMapping
  11. 找到 BookController.create() (因为 @PostMapping + @RequestMapping("/api/books"))
  12. 把请求参数封装、调用 Argument Resolver
  13. @RequestBody 把 JSON 反序列化成 BookSaveRequest
  14. @Valid 触发校验（@NotBlank、@Pattern 等）
     → 校验通过：继续
     → 校验失败：抛 MethodArgumentNotValidException
                 → 被 GlobalExceptionHandler.handleValidation 接住
                 → 返回 400 + 字段错误信息
  ↓
[BookController.create()]
  15. 调用 bookService.create(request)
  ↓
[代理对象: BookServiceImpl]
  16. ★ AOP 拦截 ★ (@Transactional)
  17. PlatformTransactionManager.getTransaction() 开启事务
  18. 把 Connection 绑到 ThreadLocal
  19. 调到真实的 BookServiceImpl.create()
  ↓
[BookServiceImpl.create()]
  20. BeanUtils.copyProperties(request, book)
  21. book.setCreatedTime(LocalDateTime.now())
  22. bookMapper.insertBook(book)
  ↓
[代理对象: BookMapper]
  23. MyBatis 生成的 JDK 动态代理拦截
  24. 根据方法名 "insertBook" + 参数类型，去 BookMapper.xml 找对应 <insert>
  25. 用 ThreadLocal 里的 Connection 执行 INSERT INTO book ...
  26. useGeneratedKeys=true → 把自增 id 回填到 book 对象
  27. INSERT 影响的行数作为返回值
  ↓
[MySQL]
  28. 写入 book 表，事务未提交
  ↓
[回到 BookServiceImpl]
  29. create() 方法正常返回
  ↓
[回到 BookServiceImpl 的代理]
  30. ★ AOP 收尾 ★
  31. 没有异常 → transactionManager.commit()
  32. Connection 提交，释放
  ↓
[回到 BookController]
  33. return Result.success()
  ↓
[DispatcherServlet]
  34. @RestController + 方法返回 Result → Jackson 序列化成 JSON
  35. 加上 CORS 响应头（因为是跨域）
  36. 写回响应
  ↓
[浏览器]
  37. 收到 200 + CORS 头 + JSON body
  38. fetch 的 Promise resolve
  39. Vue 拿到响应，渲染到页面
```

整条链路用了哪些 Spring 机制：

| 步骤 | 用到的 Spring 机制 |
|---|---|
| 启动时创建 Bean | IOC |
| 构造器注入 | DI |
| `@Transactional` 拦截 | AOP（动态代理） |
| MyBatis mapper 调用 | AOP（JDK 动态代理） |
| `@Valid` 校验 | Spring Validator |
| `@RestControllerAdvice` 接异常 | HandlerExceptionResolver |
| `WebMvcConfig.addCorsMappings` | HandlerInterceptor / CorsConfigurationSource |
| `@RequestBody` JSON → 对象 | HttpMessageConverter (Jackson) |
| 对象 → JSON | 同上 |

---

## 7. 自我验证：拿项目代码对着文档看

按这个顺序读源码，每一步都能验证一个概念：

| 看哪个文件 | 验证哪个概念 |
|---|---|
| `TmLibraryApplication.java` | `@SpringBootApplication` = `@Configuration + @EnableAutoConfiguration + @ComponentScan` |
| `application.yml` 里的 `mybatis.mapper-locations` | MyBatis 自动配置读取这个值 |
| `BookMapper.java` 上的 `@Mapper` | 被 `MapperScannerConfigurer` 扫到，注册成 Bean |
| `BookMapper.xml` 里 `<insert>` 的 `id="insertBook"` | 和 Java 方法名一一对应（MyBatis 启动期校验） |
| `BookServiceImpl` 的构造器 | DI 的构造器注入 |
| `BookServiceImpl.create()` 的 `@Transactional` | AOP 生成代理，事务拦截 |
| `BookServiceImpl.updateById()` 的 `BeanUtils.copyProperties` | 不复制 null 字段 → 部分更新 |
| `BookController` 上的 `@RequiredArgsConstructor` (Lombok) | Lombok 生成构造器，配合 final 字段实现 DI |
| `BookSaveRequest` 的 `@NotBlank`、`@Pattern` | Bean Validation，触发需要 `@Valid` |
| `BookController.create()` 的 `@Valid` | 触发校验，失败抛 `MethodArgumentNotValidException` |
| `GlobalExceptionHandler` 的多个 `@ExceptionHandler` | 异常 → 统一响应 |
| `BusinessException` 的 `ResultCode.NOT_FOUND` | 枚举复用状态码 |
| `WebMvcConfig.addCorsMappings` | CORS 配置，处理跨域 |
| `Result.success()` 静态工厂 | 统一响应结构 |

### 推荐动手实验

1. **打断点**：在 `BookController.create()` 打个断点，发请求，看调用栈。最上面应该是 `DispatcherServlet.doDispatch()`，下面依次是 HandlerAdapter、反射调用你的方法、AOP 拦截器、最终进入你的方法。

2. **看代理**：在 IDE 里 debug 时，看 `bookService` 这个变量的实际类型。**不是 `BookServiceImpl`**，而是 `BookServiceImpl$$SpringCGLIB$$xxxx` 之类的。这就是代理。

3. **关掉 CORS 试一次**：注释 `WebMvcConfig.addCorsMappings` 里的代码，重启，前端发请求，看 Network 面板的 OPTIONS 响应是不是没 CORS 头。

4. **故意触发异常**：发一个缺 `title` 的请求，看 `GlobalExceptionHandler.handleValidation` 的日志输出。

5. **故意重复 ISBN**：发两次相同 ISBN 的请求，看后端日志会不会走 `handleDuplicateKey`，响应是不是 409 而不是 500。

---

## 附录：常见面试题速答

### Q: `@Component`、`@Service`、`@Repository`、`@Controller` 有什么区别？

功能上没区别（都被 `@ComponentScan` 扫到注册）。区别在语义和框架针对性：
- `@Service`：业务层
- `@Repository`：数据访问层，额外把平台异常（SQLException）转成 Spring 的 `DataAccessException` 体系
- `@Controller`：MVC 控制器
- `@Component`：通用

### Q: Spring Bean 是线程安全的吗？

**单例 Bean 默认不是线程安全的**。Spring 不会帮你加锁。如果 Bean 有可变实例字段，且会被多线程访问，需要自己处理（加锁、用 ThreadLocal、或改成 prototype）。

你的 `BookServiceImpl` 是无状态的（无实例字段保存数据），所以线程安全。

### Q: `@Autowired` 和 `@Resource` 有什么区别？

- `@Autowired`（Spring 注解）：按类型找，找多个再按名字（@Qualifier 可指定）
- `@Resource`（JSR-250 标准注解）：按名字找，找不到再按类型

### Q: Spring Boot 怎么知道用哪个端口启动？

默认 8080。可以在 `application.yml` 改：
```yaml
server:
  port: 9090
```
或启动时 `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`

### Q: `@Transactional` 为什么加在 Service 而不是 Controller？

- 事务边界应该贴着"业务操作"，不是 HTTP 请求。一个 HTTP 请求可能包含多个 Service 调用，应该让多个 Service 方法在同一事务里。
- Controller 层加事务也会"工作"，但粒度太粗，且把"业务"和"HTTP"耦合了。

### Q: Bean 的生命周期回调有几种？

1. `@PostConstruct`（推荐）
2. `InitializingBean.afterPropertiesSet()`
3. 自定义 `init-method`（XML 时代）
4. 感知接口：`ApplicationContextAware`、`BeanNameAware` 等

销毁对称：
1. `@PreDestroy`
2. `DisposableBean.destroy()`
3. 自定义 `destroy-method`

### Q: 为什么项目里把 `Result.success()` / `Result.fail()` 设计成静态工厂而不是直接 `new Result<>()`？

封装"成功"和"失败"的语义，让调用方不能随便 `new Result(200, "成功", null)`，必须走工厂方法，便于将来加日志、埋点、metrics。

---

## 一句话总结

- **Spring Boot 启动**：`main()` → `SpringApplication.run()` → 扫注解 → 自动装配 → 实例化 Bean → 启动 Tomcat
- **IOC**：把对象的创建和组装从代码里抽出来，交给 Spring 容器
- **DI**：容器把依赖对象"注入"给你的对象（推荐构造器注入）
- **AOP**：把横切关注点（事务、日志、权限）从业务代码里抽出来，运行时通过代理织入
- **CORS**：浏览器的同源策略拦跨域响应，后端需要用 `Access-Control-Allow-*` 响应头显式"放行"

掌握这五点，Spring Boot 项目里 90% 的代码你都能看懂它"为什么这么写"。
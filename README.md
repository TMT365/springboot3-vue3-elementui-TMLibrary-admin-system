# TMLibrary

一个用 Spring Boot 4 + Vue 3 搭建的**图书管理系统** —— 前台商城 + 后台管理,覆盖图书、分类、订单、用户、反馈工单的完整流程。

不是脚手架,也不是 CRUD 演示:库存扣减走 Redis Lua 原子预占,超时关单有 DB 兜底,中文搜索接了 Elasticsearch,日志带 traceId 贯穿,反馈系统有按角色分层的缓存。踩过的坑都记在 [`TMLibrary/issue.md`](TMLibrary/issue.md) 和站内 `/journey` 页面上。

```
后端  Spring Boot 4.1 · Java 25 · MyBatis · MySQL 8 · Redis 7+(8.x 亦可) · Elasticsearch 9.5(可选)
前端  Vue 3.5 · TypeScript · Vite 8 · Element Plus · Pinia
```

---

## 目录

- [快速开始(Docker,推荐)](#快速开始docker推荐)
- [快速开始(本地开发)](#快速开始本地开发)
- [数据库怎么建](#数据库怎么建)
- [目录结构](#目录结构)
- [这个项目做了什么](#这个项目做了什么)
- [配置项](#配置项)
- [生产部署](#生产部署)
- [文档索引](#文档索引)
- [已知问题](#已知问题)

---

## 快速开始(Docker,推荐)

不需要装 Java / Node / MySQL / Redis,只需要 Docker。

```bash
git clone https://github.com/TMT365/springboot3-vue3-elementui-TMLibrary-admin-system.git
cd springboot3-vue3-elementui-TMLibrary-admin-system

# 1. 准备环境变量(★ 标记的必须改)
cp .env.docker.example .env.docker
#   至少改这三个:MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / JWT_SECRET
#   JWT_SECRET 用:openssl rand -base64 48

# 2. 起
docker compose --env-file .env.docker up -d --build
```

启动后用浏览器打开:

| 地址 | 说明 |
|---|---|
| http://localhost:8081 | 前端(唯一对外入口) |
| http://localhost:8080/actuator/health | 后端健康检查 |
| http://localhost:8080/actuator/metrics | 指标 |

**内置演示账号**(只有加载了 `seed-demo-data.sql` 才有,密码统一 `Demo@123456`):

| 账号 | 角色 | 能做什么 |
|---|---|---|
| `tmt` | BOSS | 全部权限,含改角色 |
| `demo_admin` | ADMIN | 后台管理,不能改角色 |
| `demo_alice` / `demo_bob` / `demo_carol` / `demo_dave` | USER | 前台浏览、下单、提反馈 |

### 常用命令

```bash
# 看状态 / 日志
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs -f backend

# 带 Elasticsearch 启动(中文搜索增强,可选)
docker compose --env-file .env.docker --profile search up -d --build

# 停止(保留数据)
docker compose --env-file .env.docker down

# 停止并删除所有数据(数据库、Redis、日志全清)
docker compose --env-file .env.docker down -v
```

### 为什么 Elasticsearch 是可选的

后端的候选词服务在**启动时**会对 ES 打一个 1 秒超时的健康检查(`SearchSuggestServiceConfig`),连不上就把开关回退到 off,搜索自动走 MySQL 的 `LIKE` 实现,应用照常启动。运行期 ES 挂掉也会被 `FallbackSearchSuggestService` 捕获并降级。

所以小机器上完全可以不跑 ES —— 只是中文搜索会退化成简单匹配。

> ⚠️ 官方 ES 镜像**不带 IK 中文分词器**。不装的话中文会被拆成单个字(搜「设计」会命中「计算机系统」)。安装见 [`TMLibrary/scripts/install-ik.sh`](TMLibrary/scripts/install-ik.sh)。

---

## 快速开始(本地开发)

需要 JDK 25、Node 20+、MySQL 8、Redis 7+。Elasticsearch 可选。

> Redis 只用到 `GET/SET/DEL/TTL/SETNX/HSETNX/SCAN/ZSET` 这批基础命令 + Lua 脚本 + ACL 命名用户,
> 7.x 和 8.x 行为一致。Docker 镜像固定 `redis:7-alpine`,本机开发用 8.x 也能跑。
> MySQL 侧同理:建表脚本要求 8.0+,Docker 镜像固定 `mysql:8.4`。

### 后端

```bash
cd TMLibrary

# 1. 环境变量(scripts/dev.sh 会从 .env 读取)
cp .env.example .env
#   编辑 .env,填 DB_PASSWORD、REDIS_PASSWORD、JWT_SECRET

# 2. 建库建表 —— 见下一节

# 3. 起
./scripts/dev.sh
#   切生产 profile:
./scripts/dev.sh -Dspring-boot.run.profiles=prod
```

`dev.sh` 会做三件事:加载 `.env` → 校验必填项非空 → 把参数透传给 `./mvnw spring-boot:run`。**密码在回显时会脱敏**(只打印长度),不用担心截图泄露。

### 前端

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173,/api 代理到 localhost:8080
```

其他脚本:

```bash
npm run build        # vue-tsc 类型检查 + vite 构建
npm run preview      # 本地预览生产构建
npm run type-check   # 只做类型检查
```

---

## 数据库怎么建

⚠️ **这里有个容易踩的坑,先说清楚。**

`TMLibrary/scripts/schema.sql` 是**给人看的总体规划表**,只包含 4 张核心表(users / books / orders / order_items),**不要拿它建库**。照它跑出来的库会缺 `book_categories`、`feedbacks`、`feedback_replies`、`ip_bans` —— 分类接口和反馈系统会直接 500。

**完整建表要按顺序跑这四个文件**(都在 `TMLibrary/src/main/resources/db/` 下):

```bash
cd TMLibrary
mysql -u root -p < scripts/schema.sql                                    # 建库 + 4 张核心表
mysql -u root -p < src/main/resources/db/book_categories.sql             # 分类树
mysql -u root -p < src/main/resources/db/feedback.sql                    # 反馈工单 + 回复
mysql -u root -p < src/main/resources/db/ip_bans.sql                     # IP 封禁记录

# 可选:演示数据(3 个 demo 账号 + 25 笔订单,INSERT IGNORE 幂等,可重复跑)
mysql -u root -p < src/main/resources/db/seed-demo-data.sql
```

用 Docker 的话不用手动跑 —— `docker-compose.yml` 已经把上面这几个文件按 `01~05` 的前缀挂进 `docker-entrypoint-initdb.d/`,MySQL 容器首次启动时按**文件名字典序**自动执行。

> 需要压测数据的话,`TMLibrary/testdata/` 下有 1000 条图书 JSON 和批量入库脚本 `load.sh`。

---

## 目录结构

```
.
├── TMLibrary/                  Spring Boot 后端
│   ├── src/main/java/com/tmt/TMLibrary/
│   │   ├── controller/         REST 入口 —— 参数校验 + 权限判定
│   │   ├── service/            业务逻辑(impl/ 实现,search/ 搜索与缓存装饰器)
│   │   │                       └ 密码重置:PasswordResetService + MailService(有 SMTP 发信 / 无则打日志)
│   │   ├── mapper/             MyBatis 接口(XML 在 resources/mapper/)
│   │   ├── entity/ dto/ vo/    三层数据模型,互不串用
│   │   ├── security/           JWT 过滤器 · IP 风控 · 当前用户解析
│   │   ├── scheduler/          订单超时关单 · 库存对账
│   │   ├── config/             MyBatis / Redis / ES / CORS / traceId 配置
│   │   └── common/             统一返回体 · Redis Key · 枚举 · 工具
│   ├── src/main/resources/
│   │   ├── mapper/*.xml        MyBatis SQL
│   │   ├── db/*.sql            ✅ 真正用来建表的脚本
│   │   ├── scripts/redis/*.lua 库存预占/确认/释放的原子脚本
│   │   ├── application*.yml    dev / prod 配置
│   │   └── logback-spring.xml  企业级日志(traceId + 异步 + ERROR 单独落盘)
│   ├── scripts/                dev.sh(一键启动) · schema.sql(总览表) · install-ik.sh
│   ├── testdata/               1000 条测试图书 + 批量入库脚本
│   └── Dockerfile
│
├── frontend/                   Vue 3 前端
│   ├── src/
│   │   ├── api/                按领域封装的请求函数
│   │   ├── views/              页面(auth/book/mall/purchase/user/feedback/journey/landing)
│   │   │                       auth/ 含 Login · Register · ForgotPassword · ResetPassword
│   │   ├── layouts/            三套外壳:Admin / Mall / User
│   │   ├── components/         可复用组件(Pager / FormDialog / FeedbackFab / LoadingScreen / AuthCard)
│   │   ├── composables/        useTheme · useFeedback · useBookSuggest · useChartPalette
│   │   ├── router/             路由表 + 守卫(登录态 / admin 角色 / 标题同步)
│   │   ├── stores/             Pinia:user(JWT) · cart · loading · ipBan
│   │   ├── styles/theme.css    全站设计 token(亮/暗双套)
│   │   └── utils/request.ts    唯一的 HTTP 封装(拆 Result + 401 处理)
│   ├── nginx.conf              生产用 nginx 配置(SPA 回落 + /api 反代)
│   └── Dockerfile
│
├── docker-compose.yml          MySQL + Redis + (ES) + 后端 + 前端
├── .env.docker.example         Docker 部署的环境变量模板
└── .gitignore                  ⚠️ 已排除 .env / .env.docker,只提交 .example
```

**每个文件的详细职责**:

- 后端逐文件清单 → [`TMLibrary/STRUCTURE.md`](TMLibrary/STRUCTURE.md)
- 前端逐文件清单 → [`frontend/README.md`](frontend/README.md)
- 接口文档 → [`TMLibrary/API.md`](TMLibrary/API.md)

---

## 这个项目做了什么

挑几个值得说的。

### 库存:Redis Lua 原子预占 + DB 对账

下单不是简单的 `UPDATE stock = stock - 1`。可用库存放 Redis Hash,Lua 脚本保证「校验 + 扣减 + 累加预占」三步原子(`pre_deduct_stock.lua`)。付款确认、取消释放各有对应脚本。DB 的 `stock_quantity` 是最终真值,定时任务做对账并打 `inventory_drift` 指标。

为什么要这么麻烦:并发下单时先查后改必然超卖,加分布式锁又太重。Lua 在 Redis 单线程里跑,天然原子。

### 订单:双保险的超时关单

超时未支付的订单要自动取消并释放预占。索引放 Redis ZSet(score = 过期时间戳),调度器每 60 秒扫一次 —— 但**只依赖 Redis 是危险的**:ZSet 丢过一次,订单就永久停在 PENDING,预占永不释放。

所以加了 DB 兜底扫描(`WHERE order_status = PENDING AND expire_time < NOW()`),命中时打 WARN + 指标。两条路都走不通才会漏。

### 搜索:ES 三级降级

```
Redis 缓存  →  Elasticsearch  →  MySQL LIKE
```

候选词是"每敲一个字打一次"的高频接口,Redis 挡在前面;ES 负责中文分词和相关性;ES 挂了自动降级到 MySQL。中文用 IK 分词器(`ik_max_word` 建索引 / `ik_smart` 搜索)。

没装 IK 时中文会被 standard analyzer 拆成单字 —— 搜「设计」会命中「计算机系统」,这个坑记在 `issue.md` 里。

### 反馈系统:按角色分层的缓存

管理员能看到内部备注,普通用户不能 —— 同一份数据在不同人眼里不一样。所以详情缓存按 viewer 分 key,改状态时用 `SCAN` 批量失效所有视角的缓存(不能只删管理员那份,否则普通用户会看到旧状态直到 TTL 到期)。

### 日志:traceId 贯穿

`TraceIdFilter` 是最高优先级过滤器,给每个请求生成 traceId 写进 MDC。logback 用异步 appender,ERROR 单独落文件,框架日志(Spring/Tomcat/Hikari/MyBatis/ES)全部降噪到 WARN。生产关掉 MyBatis 的 `StdOutImpl`,避免 SQL 参数里的用户名手机号被打出来。

### 密码重置(忘记密码)

`POST /api/users/forgot-password` + `POST /api/users/reset-password`,两个都是公开端点。

- 令牌 `SecureRandom` 32 字节 → Base64URL 43 字符,**库里只存 SHA-256**(不存明文),30 分钟有效、用完即焚
- **防用户枚举**:邮箱不存在 / 命中多个账号 / 正常发信,三种情况响应逐字节相同
- 邮件:配了 `spring.mail.host` 走 SMTP,没配就以 WARN 打进日志 —— 本地开发不装邮件服务也能走通

> 这里踩过一个坑:重置成功后如果**不清 Redis 里的用户缓存**,登录仍会拿缓存里的旧
> `passwordHash` 校验,表现成「新密码登不进去、旧密码还能用」。见 `issue.md`。

### 安全

- JWT(HS256)+ BCrypt,登出写 Redis 黑名单
- 登录失败计数 + 账号锁定
- IP 风控:固定窗口计数,超阈值封 24 小时
- **X-Forwarded-For 只在来源可信时才采信** —— 否则攻击者伪造 XFF 就能绕过封禁
- 写操作鉴权:所有增删改都要求 ADMIN/BOSS(2026-09 补齐了 `BookController` 遗漏的检查)

---

## 配置项

完整清单见 [`.env.docker.example`](.env.docker.example)(Docker)和 [`TMLibrary/.env.example`](TMLibrary/.env.example)(本地)。

几个关键项:

| 变量 | 必填 | 说明 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | ✅ | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PASSWORD` | ✅ | Redis 连接 |
| `JWT_SECRET` | ✅ | HS256 签名密钥,≥48 字节。**泄露 = 整套鉴权失效** |
| `CORS_ORIGINS` | ✅(prod) | 允许的前端地址。prod 下没有默认值,不配直接启动失败 |
| `ES_HOSTS` / `ES_PASSWORD` | ❌ | 不配则搜索走 MySQL 降级 |
| `IP_BAN_TRUSTED_PROXIES` | ❌ | 可信代理列表。**不要填 `*`** |
| `FRONTEND_BASE_URL` | ❌ | 密码重置邮件里的链接前缀。**必须是浏览器能访问到的前端地址**;默认 `http://localhost:5173`,Docker 下 compose 会自动设成前端端口 |
| `MAIL_FROM` | ❌ | 重置邮件的发件人。多数 SMTP 要求与认证账号同域 |
| `SPRING_MAIL_HOST` 等 | ❌ | SMTP 服务器。**留空 = 不发邮件**,重置链接打进日志,流程照样走通 |

> prod profile 故意让 `CORS_ORIGINS` 失败即停 —— 配置缺失时"起不来"比"起来了但跨域全挂"好排查得多。

---

## 生产部署

上线前请读 **[`DEPLOYMENT.md`](DEPLOYMENT.md)** —— 版本选型、支持周期、许可证决策都在那里。
三个最需要先看的点:

- **MySQL 8.0 于 2026-04 EOL**,本项目锁定的 8.4 LTS 支持到 2032
- **Redis 从 7.4 起换了许可证**,Redis 8 是 AGPLv3/SSPL/RSALv2 三选一,很多公司直接禁 AGPL。
  本项目只用基础命令 + Lua,可以无痛换成 BSD-3 的 **Valkey**(AWS 新集群已默认)
- **上线前必须改的配置**清单(密码、JWT_SECRET、CORS_ORIGINS、FRONTEND_BASE_URL 等)

---

## 文档索引

| 文件 | 内容 |
|---|---|
| [`TMLibrary/API.md`](TMLibrary/API.md) | 全部 REST 端点、请求/响应、鉴权说明 |
| [`TMLibrary/STRUCTURE.md`](TMLibrary/STRUCTURE.md) | 后端逐文件职责清单 |
| [`frontend/README.md`](frontend/README.md) | 前端逐文件职责清单 + 架构约定 |
| [`TMLibrary/issue.md`](TMLibrary/issue.md) | 遗留问题 + 历次修复记录 |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 生产部署与版本选型(支持周期、许可证、资源规划) |
| [`TMLibrary/REFERENCE.md`](TMLibrary/REFERENCE.md) | Book CRUD 参考实现 |
| [`TMLibrary/scripts/logstash/README.md`](TMLibrary/scripts/logstash/README.md) | MySQL → ES 增量同步 |

---

## 已知问题

按严重程度排:

1. **`RedisConfig.java:18` 有一行被注释掉的明文口令**。注释掉了所以不执行,但它在公开 git 历史里(提交 `5daba7a`)。实测该口令**已失效**(`AUTH failed: WRONGPASS`),不构成泄漏,建议删掉这行。
2. **`TMLibrary/API.md` 落后于代码**:`/api/feedbacks/**`(6 个)、`GET /api/purchases`、`/api/stats/dashboard`、`/api/security/ip-bans`(2 个)未收录。端点本身都在,是文档缺口。
3. **两个空壳类**:`config/MyUtilsConfig.java`(空 `@Configuration`)、`config/RestConfig.java`(Bean 被注释掉了)。
4. **无自动化测试覆盖关键路径**。`:test/` 下只有两个冒烟测试;库存并发、订单状态机、鉴权这些核心逻辑没有测试保护。

---

## License

[MIT](LICENSE) © 2026 唐明涛

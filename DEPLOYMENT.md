# TMLibrary 生产部署与版本选型

> 面向"要把它放到真实服务器上"的人。本文只讲**选哪个版本、为什么、以及本项目特有的坑**;
> 具体怎么起服务见 [`README.md`](README.md) 的 Docker 章节。

---

## 一、版本选型总表

| 组件 | 本项目当前 | **生产推荐** | 支持到 | 结论 |
|---|---|---|---|---|
| JDK | 25 | **25(LTS)** | Oracle 长期支持线 | ✅ 直接用 |
| Spring Boot | 4.1.0 | **4.1.x 最新补丁** | 跟随 OSS 支持窗口 | ✅ 打补丁即可 |
| MySQL | 8.4.11(本机) / `mysql:8.4`(容器) | **8.4 LTS** | **2032-04**(高级支持到 2029-04) | ✅ 保持 8.4 |
| Redis | 8.10.1(本机) / **`valkey/valkey:8-alpine`**(容器)| **Valkey 8.1** | — | ✅ **已定,见 §3** |
| Elasticsearch | 9.5.4 | 9.5.x(可选,**不为搜索主搜索提速**) | — | ⚠️ 见 §4 |
| Node | 22(仅构建期) | 22 LTS | — | ✅ 不进产物,无所谓 |
| nginx | 1.27-alpine | 1.27+ | — | ✅ |

**一句话结论**:全部"保持现状 + 定期打补丁",只有 Redis 换成了 Valkey —— 那是**许可证决策**,不是技术决策。

---

## 二、MySQL:必须知道 8.0 快没了

### 时间线

- **MySQL 8.0 于 2026 年 4 月 EOL** —— 如果你手上还有 8.0 的实例,现在就该规划迁移
- **MySQL 8.4 LTS** 支持到 **2032-04**(Oracle 高级支持到 2029-04;AWS RDS 上标准支持到 2029-07、扩展支持到 2032-07)
- MySQL 9.7 LTS 是更新的一条 LTS 线(2034 到期),但**本项目不需要跳过去** —— 8.4 够用到 2032

### 为什么本项目锁定 8.4

`TMLibrary/scripts/schema.sql` 文件头写明要求 **MySQL 8.0+**,`docker-compose.yml` 固定 `mysql:8.4`。8.4 是当前唯一还在支持期内的 8.x LTS,所以**保持不动**即可。

### ⚠️ 8.0 → 8.4 升级的三个坑(真要升级时看)

1. **不能跳版本**:官方只支持 `5.7 → 8.0 → 8.4` 这条路径,直接 5.7 → 8.4 会失败
2. **`mysql_native_password` 在 8.4 默认禁用**:老客户端连不上。本项目用 `mysql-connector-j 9.6.0`,走 `caching_sha2_password`,**不受影响**;但 JDBC URL 里必须保留
   `allowPublicKeyRetrieval=true&useSSL=false`(或者配上真 TLS)—— 否则 `caching_sha2_password` 在非 SSL 连接下握手会失败。这两项 `docker-compose.yml` 里已经写好了
3. **`innodb_change_buffering` 默认值从 `all` 改成 `none`**:写密集场景可能感知到性能变化,压测一下

### 驱动版本

`pom.xml` 用 `mysql-connector-j 9.6.0`。**不要降到 8.4.0** —— 那个版本有官方确认的连接/通信包 bug(issue #115736),8.4.1 起才修好。

---

## 三、Redis:先做许可证决策,再选版本

**这是本文最重要的一节。** Redis 从 7.4 起换了许可证,这件事比技术差异重要得多。

### 现状

| 版本 | 许可证 | 能否放心商用 |
|---|---|---|
| Redis ≤ 7.2.4 | **BSD-3** | ✅ 可以 |
| Redis 7.4 | RSALv2 / SSPLv1 | ⚠️ 都不是 OSI 认可的开源协议 |
| **Redis 8.x** | RSALv2 / SSPLv1 / **AGPLv3** 三选一 | ⚠️ **多数企业法务直接禁 AGPL** |

Redis 8.0(2025-05 发布)加了 AGPLv3 选项,它是 OSI 认可的,但**带网络条款的强 copyleft**:通过网络提供服务时要向用户提供源码。很多公司有"禁止 AGPL 依赖"的一刀切政策 —— **真正把团队推向 Valkey 的是这条政策,不是技术原因**。

### 替代品:Valkey

- 2024-03 从 **Redis OSS 7.2.4**(最后一个 BSD 版本)fork 出来
- **BSD-3 许可证**,Linux Foundation 托管,AWS / Google Cloud / Oracle / Ericsson / Percona 等 backing
- **命令和 wire-protocol 与 Redis 兼容** —— 对本项目来说是纯基础设施替换,**一行代码都不用改**
- 2026 年初 Valkey 8.1 已在广泛生产使用;**AWS 已把 Valkey 设为 ElastiCache / MemoryDB 新集群的默认引擎**,GCP Memorystore 也提供

### 本项目该怎么选

**先看用到了什么。** 本项目只用了这些:

```
GET / SET / DEL / TTL / SETNX / HSETNX / SCAN / ZRANGEBYSCORE / ZREM
+ 5 个 Lua 脚本(库存预占/确认/释放/校准/预热)
+ ACL 命名用户(REDIS_USERNAME)
```

**没有任何 Redis Stack 模块**(RedisJSON / RediSearch / RedisTimeSeries / Bloom 一个都没用)—— 这正是 Valkey 兼容性缺口所在的地方,而本项目完美避开了。

### ✅ 已定:Valkey 8.1

`docker-compose.yml` 已经切到 **`valkey/valkey:8-alpine`**(实测是 Valkey 8.1.10)。

理由,按权重排:

1. **BSD-3,没有许可证问题**。Redis 8 的三选一里含 AGPLv3 —— 技术上你自建、不改 Redis 源码确实不受影响,但**很多公司对 AGPL 是一刀切政策**。这个项目 README 面向"拿去写简历 / 给公司看"的场景,平白多一个 AGPL 依赖会多一轮法务问答
2. **零改动** —— 命令与 wire 协议兼容,而且镜像自带 `redis-cli` / `redis-server` 兼容软链(实测两个名字都能用),compose 里的 `command` 和 `healthcheck` 一个字没改
3. **上云顺路** —— AWS 已把 Valkey 设为 ElastiCache / MemoryDB 新集群的默认引擎,GCP Memorystore 也有

**代价**:本机那个 Redis 8.10 和容器的 Valkey 8.1 严格说不是同一个东西(虽然协议兼容)。本机不用动,日常开发照跑。

**什么时候要重新评估**:如果将来引入 **Redis Stack 模块**(RediSearch / RedisJSON / RedisTimeSeries / Bloom),Valkey 的对应实现更年轻、有缺口 —— 那时得重新选。**本项目目前一个都没用。**

> ⚠️ **别把 Valkey 当成安全升级**。两者同源,**CVE 是一起挨的**(2026-07 的 CVE-2026-23479 / CVE-2026-25243 就是 Redis 8.8 和 Valkey 9.1/9.0/8.1 同窗口一起修的)。选它是因为许可证,不是因为更安全。

### 关于"本地 8.10 / 容器 8.1"这个不一致

本机跑的是 Redis 8.10.1,容器里是 Valkey 8.1.10。**功能上无影响**(上面那批命令 7 和 8 行为一致),但"本地一个版本、线上另一个版本"是典型的翻车来源。无论最后选 Redis 还是 Valkey,**让两边版本线对齐**。

---

## 四、Elasticsearch:能跑就跑,不建议一开始就装

### 它是可选组件,这是设计出来的

后端的候选词服务(`SearchSuggestServiceConfig`)在**启动时**对 ES 打一个 1 秒超时的健康检查 —— 连不上就把开关回退到 off,搜索自动走 MySQL 的 `LIKE` 实现,**应用照常启动**。运行期 ES 挂了也会被 `FallbackSearchSuggestService` 捕获并降级。

所以:**小规模部署完全不用装它**。

⚠️ **「不装 ES」≠「没有搜索」**,这是个常被混淆的事:

- **`/api/books?keyword=`**(主搜索)**从来没接过 ES**,一直走 MySQL `LIKE`。代码注释自己写着「图书表量级小可以接受,真要上量得换全文索引或搜索引擎」
- **`/api/books/suggest`**(搜索候选词下拉)用了 `Caching(Fallback(ES, MySQL))` —— 启动期 ES ping 1 秒不通就降级,运行期挂了再降级。MySQL 兜底版**不是残废版**:标题/作者/ISBN 三条 LIKE,按**销量热度排序**,带 LIMIT

**真正失去的**:ES 给的是**相关度排序 + 容错匹配**(打错字也能搜到)。LIKE 是子串精确匹配。

| 图书量 | LIKE 全表扫描 | 结论 |
|---|---|---|
| 几千 | < 5ms | 完全无感 |
| 10 万 | ~20-50ms | 可接受,前面有 Redis 缓存挡着 |
| 100 万+ | 200ms+ | **该上 ES 了** |

**还有一个常被忽略的事**:就算装了 ES,**主搜索 `/api/books?keyword=` 也还是走 MySQL** —— 因为它压根没接 ES。要让主搜索也走 ES 是**待开发的功能**,不是配一下就行

### 真要装的话,两个坑

1. **IK 分词器必须额外装**。官方镜像不带,不装的话中文会被 standard analyzer 拆成单个字 —— 搜「设计」会命中「计算机系统」。安装见 `TMLibrary/scripts/install-ik.sh`
2. **IK 有 ES 版本硬校验**。ES 在装插件时会**拒绝**版本不匹配的插件(不是警告,是直接报 `Plugin [analysis-ik] was built for Elasticsearch version X but version Y is running`)。社区版 IK 常滞后于 ES 小版本,`install-ik.sh` 里的做法是下载最接近的版本再改 `plugin-descriptor.properties` 绕过校验 —— **这是个有意的 hack,升级 ES 时要重新验一遍分词效果**

### 许可证

- **默认二进制发行版是 Elastic License 2.0(ELv2)**:自托管、内部使用、随应用分发都可以,限制主要针对"把它当托管服务卖给别人"
- 源码从 8.16 起三选一(AGPLv3 / SSPL 1.0 / ELv2),`x-pack/` 只有 ELv2
- **自托管给本项目用,没问题**

### 资源

单节点 + 演示用,compose 里给的 `-Xms512m -Xmx512m` 只够玩。真实数据量下 ES 是这套里**最吃内存**的组件,生产按数据量给(堆不超过物理内存一半,且不超过 31GB 以保留指针压缩)。

---

## 五、镜像 tag 策略

`docker-compose.yml` 里现在用的是**浮动小版本 tag**(`mysql:8.4`、`nginx:1.27-alpine`):

- ✅ 好:自动拿到安全补丁
- ⚠️ 坏:同一天在两台机器上 `build` 可能拿到不同的小版本,"我这跑得好好的"变成玄学

**生产建议二选一:**

```yaml
# 方案 A:锁到补丁版本(简单,推荐起步用)
image: mysql:8.4.11
image: nginx:1.27.4-alpine

# 方案 B:锁到 digest(最严格,可复现)
image: mysql@sha256:xxxxx...
```

**绝对不要用 `:latest`。**

**升级流程**:改 tag → 在预发环境起一遍 → 跑一次关键路径(登录 / 下单 / 支付 / 反馈)→ 再上生产。不要指望"反正是小版本"。

---

## 六、资源规划(起步参考)

给一台 4C8G 的单机参考值:

| 组件 | 内存 | 说明 |
|---|---|---|
| MySQL | 2G | `innodb_buffer_pool_size` 设物理内存的 50-70% |
| 后端 JVM | 2G | Dockerfile 里设了 `-XX:MaxRAMPercentage=75`,**跟着容器限额走**;给容器 2G 就是堆约 1.5G |
| Redis / Valkey | 512M | 本项目缓存的数据量很小(库存 Hash + 用户缓存 + 验证码 + JWT 黑名单) |
| Elasticsearch | 1G+ | **可以不装**;要装就至少 2G |
| 前端 nginx | 64M | 纯静态 |

**JVM 那条很重要**:容器不设 `-XX:MaxRAMPercentage` 的话,JVM 按**宿主机**总内存算堆,容器限了 2G 也会去申请 8G,然后被 OOMKilled。本项目 `TMLibrary/Dockerfile` 里已经处理了。

---

## 七、配置外部化:密码不进 jar

**核心原则**:`application*.yml` **可以提交进 git**(只放占位符),**真实的数据库密码、JWT_SECRET 等敏感值**走**环境变量 / 外部文件**,不进 jar 包。

**项目实际机制(已实现,可直接复用)**:

```
源代码层(可提交):
  TMLibrary/src/main/resources/
  ├── application.yml          ← 默认值 + 占位符 ${VAR:default}
  ├── application-dev.yml      ← 本地开发用的非敏感默认值
  └── application-prod.yml     ← 生产占位符,**没有真实密码**

运行时层(必须 gitignore):
  TMLibrary/.env               ← 本地开发的环境变量(真实密码)
                                  dev.sh 会 source 它、校验必填项
                                  .gitignore 已覆盖
  TMLibrary/Dockerfile         ← 用 SPRING_PROFILES_ACTIVE=prod 启动
                                  容器内用 environment 注入变量(见 compose)
  docker-compose.yml            ← 环境变量从 .env.docker 读(.env.docker.example 是占位)
```

**为什么这样设计**:

- **改密码不需要重新打包 jar**:docker-compose.yml 改完 `restart` 一下,新密码生效;本地 `cp .env.example .env` 后改值即可
- **密码不会泄露进 git**:`.env` 和 `.env.*` 都在 `.gitignore` 里,我刚才核过了
- **profile 切换**:本地 `dev` profile 走 application-dev.yml + .env;服务器 `prod` profile 走 application-prod.yml + 环境变量
- **`CORS_ORIGINS` 等生产必填项故意没兜底**:`application-prod.yml` 里直接写 `origins: ${CORS_ORIGINS}`,不配会让 CI 报错 —— 比"起来了但跨域全挂"好排查

**两种运行模式**:

```bash
# 1. 本地开发(dev profile)—— dev.sh 已经做好了
cd TMLibrary
cp .env.example .env       # 一次
./scripts/dev.sh           # 启动,自动 source .env
# 改密码:编辑 .env → 重启 dev.sh

# 2. 生产部署(prod profile)—— 你截图说的就是这个
# 假设你已经 build 出了 app.jar
java -Xms512m -Xmx1024m -jar app.jar     --spring.profiles.active=prod    # ← 关键:启动时指定 prod profile
# 应用会按 "外部 application-prod.yml > 环境变量 > 内置 application-prod.yml"
# 的优先级查找

# 3. 想要应用外的 application-prod.yml(你截图里说的方式)
# 放在 jar 同级目录,或 ./config/ 子目录,Spring Boot 自动加载:
/opt/tmlibrary/
├── app.jar
├── application-prod.yml      ← 优先级最高,覆盖 jar 内的同名文件
└── logs/
```

**关键优先级**(从高到低,Spring Boot 标准):
1. 命令行参数 `--key=value`
2. `JAR/config/` 下的 application-prod.yml
3. `JAR/` 同级目录的 application-prod.yml
4. `JAR/BOOT-INF/classes/` 里的 application-prod.yml(即打包进去的那个)
5. 环境变量 `KEY=value`

**所以部署姿势很简单**:服务器上 jar 的包同级目录放一个**只含真实凭据**的 `application-prod.yml` —— 其它都用 jar 包里的默认值。**这个文件不要提交,不要放共享磁盘,不要塞 docker image**。

---

## 七(上接 §7)、独立的「纯 Jar + 外部配置」部署姿势

**适用场景**:服务器上**只能有 `dist/` 和 `app.jar`**,不允许 docker、不允许 compose。

```
/opt/tmlibrary/
├── app.jar                                  ← Spring Boot fat jar
├── application-prod.yml                      ← 服务器专属配置(★ 不要 commit)
├── config/                                   ← Spring Boot 自动加载目录(可选)
│   └── application-prod.yml                   ← 同上,放这里也认
└── frontend/
    └── dist/                                 ← 前端构建产物
        ├── index.html
        └── assets/
```

### 外部 application-prod.yml 模板

**生成方式**:从 `TMLibrary/src/main/resources/application-prod.yml` 复制一份到 `/opt/tmlibrary/application-prod.yml`,然后编辑填真值。
`★` = 你这条线**必填且敏感**,绝不能 commit 进 git。

```yaml
# /opt/tmlibrary/application-prod.yml
# ============================================================
# 服务器专属配置 —— 这里只放真实凭据,其它让 jar 里的默认值生效
# 加载优先级:JAR 同级 > JAR 内置,所以这里的值会覆盖 jar 里的 application-prod.yml
# ============================================================

spring:
  datasource:
    url: "jdbc:mysql://你的MySQL地址:3306/tmlibrary?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    username: "tmlapp_app"          # ★ 强烈建议用专用账号,不要给 root
    password: "从密钥管理工具取的密码"   # ★ 真实密码
  data:
    redis:
      host: "你的Valkey地址"
      port: 6379
      password: "你的Valkey密码"       # ★ 没密码 = 任何人都能改库存
      username: ""                    # 默认用户场景下留空
      database: 0

jwt:
  secret: "openssl rand -base64 48 的输出"  # ★ 必填,≥48 字节,泄露 = 可伪造 BOSS token
  expiration-seconds: 604800

app:
  cors:
    origins: "https://library.example.com"  # ★ 真实前端域名,不能用 localhost
  frontend:
    base-url: "https://library.example.com" # ★ 密码重置邮件链接前缀
  mail:
    from: "no-reply@library.example.com"
  search:
    elasticsearch:
      hosts: "http://你的ES地址:9200"
      username: ""
      password: ""
  security:
    ip-ban:
      trust-private-ips: false
      trusted-proxies: ""                  # ★ 空 = 不采信 XFF(直连部署的安全默认)

spring.mail.host: "smtp.example.com"
spring.mail.port: "587"
spring.mail.username: "no-reply@example.com"
spring.mail.password: "SMTP密码"
spring.mail.properties.mail.smtp.auth: "true"
spring.mail.properties.mail.smtp.starttls.enable: "true"

logging:
  file:
    path: "/var/log/tmlapp"               # ★ 确保目录存在,后端有写权限
```

### 服务器侧启动命令

```bash
java -Xms512m -Xmx1024m -jar /opt/tmlibrary/app.jar --spring.profiles.active=prod
```

不要用 nohup & —— 用 systemd / supervisor,挂了会拉起。改完配置 `systemctl restart tmlapp`,不需要重新打包。

### 千万不要放的事

- `application-prod.yml` commit 进 git(密码就漏了)
- 数据库用 root 账号(越权即全库)
- 数据库地址填 localhost(那是另一台机)
- 真实密码直接写在 jar 内的 application-prod.yml(可被反编译看)

---

## 七、上线前必须改的配置

`docker-compose.yml` / `.env.docker` 里这几项**不能带着默认值上线**:

| 变量 | 默认值 | 必须改成 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `replace_me` | 强随机密码 |
| `REDIS_PASSWORD` | `replace_me` | 强随机密码。**这个 Redis 存着库存预占和 JWT 黑名单,没密码等于谁都能改库存** |
| `JWT_SECRET` | `replace_me_...` | `openssl rand -base64 48`。**泄露 = 任何人可伪造 BOSS 的 token** |
| `CORS_ORIGINS` | `http://localhost:8081` | 真实前端域名。prod profile 里这项**没有默认值,不配直接启动失败** —— 这是有意的,配错了的表现是"页面能开但请求全跨域失败",比启动失败难查得多 |
| `FRONTEND_BASE_URL` | 联动 `FRONTEND_PORT` | 真实前端地址。**密码重置邮件里的链接用它拼**,填错用户点开是 404 |
| `IP_BAN_TRUSTED_PROXIES` | 空 | 如果前面挂了外部网关,填网关地址。**空是安全默认值,不要随手填 `*`** —— 那等于让任何人伪造 XFF 绕过封禁 |
| `SPRING_MAIL_HOST` 等 | 注释状态 | 生产**务必配好** —— 不配的话重置链接会打进容器日志,能看到日志的人就能重置任意账号 |

另外两件:

- **HTTPS**:nginx 配置里现在只监听 80。生产要么在前面挂一层 TLS 终结(推荐),要么给 nginx 加证书
- **数据库端口**:compose 里 MySQL / Redis 都绑在 `127.0.0.1`,**不要改成 `0.0.0.0`** —— 它们不该对公网开放

---

## 八、本项目的版本约束来自哪里

排查版本问题时,这些是**权威来源**,不要看二手文档:

| 约束 | 文件 |
|---|---|
| Java / Spring Boot / 各类依赖版本 | `TMLibrary/pom.xml` |
| 建表脚本的 MySQL 版本要求 | `TMLibrary/scripts/schema.sql` 文件头 |
| 实际建表语句(权威) | `TMLibrary/src/main/resources/db/*.sql` |
| 容器镜像版本 | `docker-compose.yml` / 两个 `Dockerfile` |
| ES 客户端版本 | `TMLibrary/pom.xml` 的 `elasticsearch-java` |
| IK 分词器版本与补丁方式 | `TMLibrary/scripts/install-ik.sh` |
| 前端 Node 版本 | `frontend/Dockerfile` |

> ⚠️ `TMLibrary/scripts/schema.sql` 是**给人看的总体规划表**,只含 4 张核心表,**不要拿它建库** ——
> 真正完整的建表脚本是 `src/main/resources/db/` 下那四个(`schema` + `book_categories` + `feedback` + `ip_bans`)。
> 详见 README 的「数据库怎么建」。

---

## 九、附:核实过的结论与数据来源

本文里关于**支持周期**和**许可证**的说法来自公开资料,不是凭印象写的。核实时间:**2026-09**。

**这些是时效性信息,做决策前请再核一次官方页面**(支持周期和许可证都可能变):

- MySQL 8.0 EOL 2026-04、8.4 LTS 支持到 2032-04:`endoflife.ai`、`eosl.date`、AWS RDS 版本管理文档
- MySQL 8.4 升级注意事项(`mysql_native_password` 默认禁用、`innodb_change_buffering` 变更、connector 8.4.0 的 bug #115736):MySQL 8.4 升级实践文章
- Redis 8 三许可证(RSALv2 / SSPLv1 / AGPLv3)、Valkey 从 7.2.4 fork 且为 BSD-3、AWS 将其设为新集群默认引擎、CVE 同源同修:Phoronix、AWS ElastiCache 文档、Redisson 迁移指南
- Elasticsearch 许可证(默认发行版 ELv2,源码 8.16+ 三许可):Elastic 官方 licensing FAQ

**本项目自身的版本约束**(§8 那张表)全部来自仓库文件本身,可直接核对。

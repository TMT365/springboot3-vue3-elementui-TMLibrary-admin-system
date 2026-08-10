# TMLibrary 测试数据 + 批量入库工具

> 最后更新:2026-08-09
> 目录位置:`TMLibrary/testdata/`
> 用途:提供 1000 条预定义的图书数据 + 灵活的批量入库工具

---

## 📁 目录结构

```
testdata/
├── README.md                  # 本文件
└── books-1000.json            # 1000 条预定义数据(运行 seeder 后生成)

# Seeder 源码位置(在 src 下,Maven 会自动编译)
src/main/java/com/tmt/TMLibrary/testdata/
└── BookTestDataSeeder.java    # 生成器 + 批量入库工具(一站式)
```

> 为什么 seeder 在 `src/main/java` 而不在 `testdata/`:
> - 让 Maven 自动编译,无需手动 javac
> - 可用 `java -cp target/classes` 直接跑,无需配 classpath
> - 包名 `com.tmt.TMLibrary.testdata` 跟 `testdata/` 文件夹逻辑对应

---

## 🚀 快速开始

### 一条龙(生成 + 入库)

```bash
cd TMLibrary
export DB_URL='jdbc:mysql://localhost:3306/tmlibrary?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'

# Seeder 编译完直接跑:
./mvnw compile

# 1. 生成 JSON(不连 DB)
cd testdata
java -cp ../target/classes com.tmt.TMLibrary.testdata.BookTestDataSeeder generate

# 2. 入库(读 books-1000.json,清空旧数据)
java -cp ../target/classes:$(./mvnw dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) \
     com.tmt.TMLibrary.testdata.BookTestDataSeeder seed --truncate

# 3. 一条龙(先生成 1000 条 JSON,再 truncate 后入库)
java -cp ../target/classes:$(./mvnw dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) \
     com.tmt.TMLibrary.testdata.BookTestDataSeeder both
```

---

## � 数据构成

| 类别 | 数量 | 说明 |
|---|---|---|
| **真实书单** | 50 | 经典计算机书(Effective Java / Clean Code / 算法导论 / 深入理解计算机系统...)+ 中文经典(三体 / 活着 / 红楼梦...)+ 英文经典(1984 / Animal Farm / The Great Gatsby...) |
| **生成占位** | 950 | 中英文混合,书名 / 作者 / ISBN / 价格 / 日期 / 库存都由算法生成 |
| **总计** | **1000** | |

### 字段范围(由真实数据 + 算法共同决定)

| 字段 | 类型 | 取值范围 |
|---|---|---|
| title | VARCHAR(200) | 真实书名 + 生成的中英文标题(50 个英文 topic × 12 个 prefix / 20 个中文 title) |
| author | VARCHAR(100) | 16 个英文姓名 + 20 个中文姓名 |
| isbn | VARCHAR(20) | 真实 ISBN(公开) + 伪 ISBN(`978-0-NNNNNNNNN-D`) |
| price | DECIMAL(10,2) | 9.00 ~ 199.99 |
| published_date | DATE | 1980-01-01 ~ 2024-12-31 |
| stock_quantity | INT | 0 ~ 200 |

---

## ⚙️ 命令行参数

```bash
# 模式 1:只生成 JSON(不连 DB)
java BookTestDataSeeder generate [count]
#   count: 生成条数,默认 1000

# 模式 2:读 JSON 批量入库
java BookTestDataSeeder seed [file] [--batch=N] [--truncate]
#   file:       JSON 文件路径,默认 books-1000.json
#   --batch=N:  每批入库条数,默认 100
#   --truncate: 入库前先 TRUNCATE TABLE book

# 模式 3:一条龙
java BookTestDataSeeder both [count]
#   count: 生成条数,默认 1000,然后用默认 batch=100 + truncate=true 入库
```

---

## 🛡️ 灵活入库的关键设计

| 特性 | 实现 | 用途 |
|---|---|---|
| **可配置 batch size** | `--batch=N` 参数 | 网络差 / 大字段时调小;网络好调大 |
| **进度报告** | 每批打印 `[seed] 已入库 N / M` | 1000 条 ≈ 10 批,看着输出知道跑到哪 |
| **失败回退** | 整批失败 → 回退逐条插入 | 定位具体哪条失败(如 ISBN 重复) |
| **失败汇总** | 末尾打印"成功/失败/失败索引" | 不让单条失败吞掉整批 |
| **可选 truncate** | `--truncate` 开关 | 重新初始化 vs 增量导入都支持 |
| **确定性** | 固定 Random 种子(42) | 每次生成数据完全一致,可重复 |
| **零依赖启动** | 纯 JDBC + Jackson | 启动 < 1 秒,跑完即退,不污染 Spring 上下文 |

---

## 🔍 数据示例

打开 `books-1000.json`,前几条长这样:

```json
[
  {
    "title": "Effective Java",
    "author": "Joshua Bloch",
    "isbn": "9780134685991",
    "price": 42.00,
    "publishedDate": "2018-01-06",
    "stockQuantity": 100,
    "createdTime": null,
    "updatedTime": null
  },
  ...
]
```

`createdTime` / `updatedTime` 留 null — 入库时由 MySQL `DEFAULT CURRENT_TIMESTAMP` 自动生成。

---

## � 常见踩坑

| 现象 | 原因 | 解决 |
|---|---|---|
| `ClassNotFoundException: BookTestDataSeeder` | `testdata/` 不在 classpath | 跑前 `cp testdata:.` 或用 Maven `exec:java` |
| `Communications link failure` | MySQL 没启动 / 端口错 | `sudo systemctl start mysql` |
| `Access denied for user 'root'@'localhost'` | 环境变量没设 / 密码错 | 检查 `echo $DB_PASSWORD` |
| `Duplicate entry 'xxx' for key 'book.isbn'` | 上次跑过没 truncate 又跑 | 加 `--truncate`,或保证 ISBN 全局唯一 |
| 中文字段入库后变成 `???` | URL 缺 `characterEncoding=UTF-8` | DB_URL 加这个参数 |
| 数据生成后立刻看 createdTime 是 null | 入库由 MySQL DEFAULT 生成 | 查表时就是当前时间,无需 seeder 处理 |

---

## � 跟单测的协作

集成测试 `BookMapperIT` 可以直接用这 1000 条数据做 fixture:

```yaml
# src/test/resources/application.yml
spring:
  sql:
    init:
      data-locations: classpath:testdata/books-1000.json
```

启动时 H2 会自动加载,Mapper 测试就能直接对真 SQL 验证(空 query 全量、LIKE 转义、范围查询、粒度连续性校验等)。

---

## 🔄 重新生成数据

修改 `BookTestDataSeeder.java` 里的素材池(`REAL_BOOKS` / `CHINESE_TITLES` / `ENGLISH_TITLE_TOPICS`...)后,重新跑 `generate` 即可。**固定种子保证结果可重复**。

如果要换一批完全不同的数据,把 `SEED` 常量换个值即可。

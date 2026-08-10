package com.tmt.TMLibrary.testdata;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @brief 测试数据生成器 + 批量入库工具
 *
 * 一站式工具,既能生成 1000 条图书数据(写入 JSON),也能批量入库到 MySQL。
 *
 * **运行模式**(通过命令行第一个参数选择):
 *   - `generate [count]` — 只生成 JSON,不连数据库。默认 count=1000
 *   - `seed [file] [--batch=N] [--truncate]` — 读 JSON,批量入库
 *   - `both [count]` — 先生成,再入库(适合一次性初始化)
 *
 * **使用前**:
 *   1. 设置环境变量 DB_URL / DB_USERNAME / DB_PASSWORD(同主项目)
 *   2. 数据库 schema 已通过 src/main/resources/db/schema.sql 创建
 *
 * **设计要点**:
 *   - **确定性**:固定 Random 种子(42),同一份代码多次运行生成**完全相同**的数据
 *   - **混合真实 + 生成的占位数据**:前 50 条是真实存在的经典书(Effective Java、三体、活着...),后 N-50 条由算法生成
 *   - **批量入库**:JDBC `addBatch() / executeBatch()`,默认每 100 条一批(可调)
 *   - **进度报告**:每批打印 `[seeded 200 / 1000]` 格式
 *   - **错误处理**:单条 SQL 失败不中断整批,记到错误列表,最后打印汇总
 *
 * **为什么不用 Spring / MyBatis 跑这个工具**:
 *   - 不希望 seeder 依赖整个 Spring 上下文(启动慢、配置复杂)
 *   - 纯 JDBC + Jackson,启动 < 1 秒,跑完即退
 *   - 不污染 src/main/java,放在 testdata/ 下作为独立工具
 *
 * **运行示例**:
 *   ```
 *   # 1. 生成 1000 条 JSON(默认)
 *   java testdata/BookTestDataSeeder.java generate
 *
 *   # 2. 入库(默认读 books-1000.json)
 *   java testdata/BookTestDataSeeder.java seed --truncate
 *
 *   # 3. 一条龙
 *   java testdata/BookTestDataSeeder.java both
 *   ```
 */
public class BookTestDataSeeder {

    // 固定种子 → 每次生成的数据完全一致(便于测试可重复)
    private static final long SEED = 42L;
    private static final int DEFAULT_COUNT = 1000;
    private static final int DEFAULT_BATCH_SIZE = 100;

    // ============== 真实书单(前 50 条) ==============
    // 来源:经典计算机书 + 中文小说 + 英文小说 + 商业/管理
    // ISBN 取自公开数据(或接近公开格式的伪 ISBN)
    private static final List<BookSeed> REAL_BOOKS = Arrays.asList(
        new BookSeed("Effective Java", "Joshua Bloch", "9780134685991", new BigDecimal("42.00"), LocalDate.of(2018, 1, 6), 100),
        new BookSeed("Clean Code", "Robert C. Martin", "9780132350884", new BigDecimal("39.95"), LocalDate.of(2008, 8, 1), 80),
        new BookSeed("Design Patterns", "Gang of Four", "9780201633610", new BigDecimal("54.99"), LocalDate.of(1994, 10, 31), 60),
        new BookSeed("The Pragmatic Programmer", "Andrew Hunt", "9780201616224", new BigDecimal("39.95"), LocalDate.of(1999, 10, 30), 70),
        new BookSeed("Refactoring", "Martin Fowler", "9780134757599", new BigDecimal("47.95"), LocalDate.of(2018, 11, 20), 50),
        new BookSeed("Domain-Driven Design", "Eric Evans", "9780321125217", new BigDecimal("54.99"), LocalDate.of(2003, 8, 30), 40),
        new BookSeed("Working Effectively with Legacy Code", "Michael Feathers", "9780131177055", new BigDecimal("49.95"), LocalDate.of(2004, 9, 1), 30),
        new BookSeed("The Mythical Man-Month", "Frederick P. Brooks", "9780201835953", new BigDecimal("29.99"), LocalDate.of(1995, 8, 12), 25),
        new BookSeed("Code Complete", "Steve McConnell", "9780735619678", new BigDecimal("49.95"), LocalDate.of(2004, 6, 19), 45),
        new BookSeed("Structure and Interpretation of Computer Programs", "Harold Abelson", "9780262510875", new BigDecimal("75.00"), LocalDate.of(1996, 7, 25), 20),
        // 中文经典
        new BookSeed("三体", "刘慈欣", "9787229030933", new BigDecimal("38.00"), LocalDate.of(2008, 1, 1), 200),
        new BookSeed("三体Ⅱ:黑暗森林", "刘慈欣", "9787229042066", new BigDecimal("42.00"), LocalDate.of(2008, 5, 1), 180),
        new BookSeed("三体Ⅲ:死神永生", "刘慈欣", "9787544251405", new BigDecimal("48.00"), LocalDate.of(2010, 11, 1), 160),
        new BookSeed("活着", "余华", "9787506365437", new BigDecimal("28.00"), LocalDate.of(2012, 8, 1), 300),
        new BookSeed("许三观卖血记", "余华", "9787506365413", new BigDecimal("25.00"), LocalDate.of(2012, 7, 1), 150),
        new BookSeed("围城", "钱钟书", "9787020024759", new BigDecimal("32.00"), LocalDate.of(2008, 1, 1), 120),
        new BookSeed("红楼梦", "曹雪芹", "9787020002207", new BigDecimal("59.70"), LocalDate.of(1996, 12, 1), 500),
        new BookSeed("三国演义", "罗贯中", "9787020008728", new BigDecimal("49.80"), LocalDate.of(1998, 5, 1), 400),
        new BookSeed("水浒传", "施耐庵", "9787020008735", new BigDecimal("49.80"), LocalDate.of(1998, 5, 1), 350),
        new BookSeed("西游记", "吴承恩", "9787020008742", new BigDecimal("49.80"), LocalDate.of(1980, 5, 1), 450),
        new BookSeed("百年孤独", "加西亚·马尔克斯", "9787544253994", new BigDecimal("55.00"), LocalDate.of(2011, 6, 1), 100),
        new BookSeed("1984", "George Orwell", "9780451524935", new BigDecimal("9.99"), LocalDate.of(1961, 8, 1), 250),
        new BookSeed("Animal Farm", "George Orwell", "9780451526342", new BigDecimal("8.99"), LocalDate.of(1946, 8, 17), 200),
        new BookSeed("Brave New World", "Aldous Huxley", "9780060850524", new BigDecimal("15.99"), LocalDate.of(1932, 9, 1), 180),
        new BookSeed("Fahrenheit 451", "Ray Bradbury", "9781451673319", new BigDecimal("14.99"), LocalDate.of(1953, 10, 19), 220),
        new BookSeed("The Great Gatsby", "F. Scott Fitzgerald", "9780743273565", new BigDecimal("12.99"), LocalDate.of(1925, 4, 10), 300),
        new BookSeed("To Kill a Mockingbird", "Harper Lee", "9780061120084", new BigDecimal("14.99"), LocalDate.of(1960, 7, 11), 280),
        new BookSeed("Pride and Prejudice", "Jane Austen", "9780141439518", new BigDecimal("9.99"), LocalDate.of(1813, 1, 28), 350),
        new BookSeed("The Catcher in the Rye", "J.D. Salinger", "9780316769174", new BigDecimal("10.99"), LocalDate.of(1951, 7, 16), 320),
        new BookSeed("Lord of the Rings", "J.R.R. Tolkien", "9780544003415", new BigDecimal("29.99"), LocalDate.of(1954, 7, 29), 200),
        new BookSeed("The Hobbit", "J.R.R. Tolkien", "9780547928227", new BigDecimal("14.99"), LocalDate.of(1937, 9, 21), 250),
        new BookSeed("Harry Potter and the Sorcerer's Stone", "J.K. Rowling", "9780590353427", new BigDecimal("10.99"), LocalDate.of(1997, 6, 26), 500),
        new BookSeed("A Brief History of Time", "Stephen Hawking", "9780553380163", new BigDecimal("18.00"), LocalDate.of(1988, 4, 1), 150),
        new BookSeed("Sapiens", "Yuval Noah Harari", "9780062316097", new BigDecimal("22.99"), LocalDate.of(2011, 1, 1), 180),
        new BookSeed("Thinking, Fast and Slow", "Daniel Kahneman", "9780374533557", new BigDecimal("17.00"), LocalDate.of(2011, 10, 25), 140),
        new BookSeed("The Art of War", "Sun Tzu", "9781590302255", new BigDecimal("12.95"), LocalDate.of(2003, 9, 1), 200),
        new BookSeed("The Little Prince", "Antoine de Saint-Exupéry", "9780156012195", new BigDecimal("10.00"), LocalDate.of(1943, 4, 6), 400),
        new BookSeed("Norwegian Wood", "Haruki Murakami", "9780375704024", new BigDecimal("15.95"), LocalDate.of(1987, 9, 1), 100),
        new BookSeed("Kafka on the Shore", "Haruki Murakami", "9781400079278", new BigDecimal("16.00"), LocalDate.of(2002, 9, 12), 90),
        new BookSeed("1Q84", "Haruki Murakami", "9780307593313", new BigDecimal("27.95"), LocalDate.of(2009, 5, 29), 80),
        new BookSeed("算法导论", "Thomas H. Cormen", "9787111407010", new BigDecimal("88.00"), LocalDate.of(2012, 12, 1), 100),
        new BookSeed("深入理解计算机系统", "Randal E. Bryant", "9787111321330", new BigDecimal("99.00"), LocalDate.of(2011, 1, 1), 120),
        new BookSeed("Java 编程思想", "Bruce Eckel", "9787111213826", new BigDecimal("108.00"), LocalDate.of(2007, 6, 1), 80),
        new BookSeed("鸟哥的 Linux 私房菜", "鸟哥", "9787115427640", new BigDecimal("98.00"), LocalDate.of(2015, 12, 1), 60),
        new BookSeed("代码大全", "Steve McConnell", "9787115142605", new BigDecimal("98.00"), LocalDate.of(2006, 3, 1), 50),
        new BookSeed("人类简史", "Yuval Noah Harari", "9787508647357", new BigDecimal("68.00"), LocalDate.of(2014, 11, 1), 200),
        new BookSeed("未来简史", "Yuval Noah Harari", "9787508672069", new BigDecimal("68.00"), LocalDate.of(2016, 6, 1), 150),
        new BookSeed("刻意练习", "Anders Ericsson", "9787508672069", new BigDecimal("45.00"), LocalDate.of(2016, 11, 1), 130),
        new BookSeed("思考,快与慢", "Daniel Kahneman", "9787508646787", new BigDecimal("69.00"), LocalDate.of(2012, 7, 1), 110)
    );

    // ============== 生成用的素材池(用于占位数据) ==============
    private static final String[] ENGLISH_TITLE_PREFIXES = {
        "The Art of", "Mastering", "Practical Guide to", "Introduction to",
        "Advanced", "Essential", "Comprehensive", "Modern",
        "Effective", "Deep Dive into", "Hands-On", "Learning"
    };
    private static final String[] ENGLISH_TITLE_TOPICS = {
        "Java Programming", "Python Development", "Web Development", "Data Structures",
        "Machine Learning", "Cloud Computing", "System Design", "Database Systems",
        "Software Architecture", "Network Security", "Algorithms", "DevOps",
        "Microservices", "Distributed Systems", "Software Engineering", "API Design"
    };
    private static final String[] ENGLISH_AUTHORS = {
        "John Smith", "Emily Johnson", "Michael Brown", "Sarah Davis",
        "David Wilson", "Jennifer Miller", "Robert Taylor", "Lisa Anderson",
        "William Thomas", "Karen Martinez", "James White", "Patricia Lee",
        "Christopher Harris", "Barbara Clark", "Daniel Lewis", "Nancy Walker"
    };
    private static final String[] CHINESE_TITLES = {
        "编程之美", "代码精进之路", "技术领导力", "产品经理手册",
        "数据结构与算法", "Java 高级编程", "深入理解 JVM", "微服务架构设计",
        "分布式系统实践", "云原生应用开发", "大数据处理实战", "人工智能入门",
        "深度学习精要", "Python 编程之道", "Web 安全攻防", "Linux 系统管理",
        "数据库性能优化", "高并发编程", "架构之美", "代码重构"
    };
    private static final String[] CHINESE_AUTHORS = {
        "张伟", "王芳", "李娜", "刘洋", "陈静", "杨杰", "赵敏", "黄磊",
        "周婷", "吴昊", "徐丽", "孙强", "马丽", "朱勇", "胡晓", "郭鑫",
        "林峰", "何明", "高远", "罗丹"
    };

    // ============== 入口 ==============

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String mode = args[0];
        switch (mode) {
            case "generate":
                int count = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_COUNT;
                runGenerate(count);
                break;
            case "seed":
                runSeed(parseSeedArgs(args));
                break;
            case "both":
                int bothCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_COUNT;
                String jsonPath = runGenerate(bothCount);
                runSeed(new SeedOptions(jsonPath, DEFAULT_BATCH_SIZE, true));
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("用法:");
        System.out.println("  java BookTestDataSeeder.java generate [count=1000]");
        System.out.println("  java BookTestDataSeeder.java seed [file=books-1000.json] [--batch=N] [--truncate]");
        System.out.println("  java BookTestDataSeeder.java both [count=1000]");
        System.out.println();
        System.out.println("环境变量: DB_URL, DB_USERNAME, DB_PASSWORD(必填,seed/both 模式)");
    }

    private static SeedOptions parseSeedArgs(String[] args) {
        String file = "books-1000.json";
        int batch = DEFAULT_BATCH_SIZE;
        boolean truncate = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--batch=")) {
                batch = Integer.parseInt(a.substring("--batch=".length()));
            } else if (a.equals("--truncate")) {
                truncate = true;
            } else if (!a.startsWith("--")) {
                file = a;
            }
        }
        return new SeedOptions(file, batch, truncate);
    }

    // ============== 生成模式 ==============

    private static String runGenerate(int count) throws IOException {
        List<BookSeed> books = generateBooks(count);
        String path = "books-" + count + ".json";
        writeJson(books, path);
        System.out.println("[generate] 已生成 " + books.size() + " 条 → " + path);
        return path;
    }

    /**
     * @brief 生成 count 条图书数据。
     *        前 min(count, REAL_BOOKS.size()) 条用真实书单,其余用占位数据。
     *        同一 SEED 下结果完全一致。
     */
    private static List<BookSeed> generateBooks(int count) {
        List<BookSeed> result = new ArrayList<>(count);
        // 真实书单
        int realCount = Math.min(count, REAL_BOOKS.size());
        for (int i = 0; i < realCount; i++) {
            result.add(REAL_BOOKS.get(i));
        }
        // 占位数据
        Random rnd = new Random(SEED);
        for (int i = realCount; i < count; i++) {
            result.add(generateFillerBook(i, rnd));
        }
        return result;
    }

    private static BookSeed generateFillerBook(int index, Random rnd) {
        boolean isChinese = rnd.nextDouble() < 0.5;
        String title;
        String author;
        if (isChinese) {
            title = CHINESE_TITLES[rnd.nextInt(CHINESE_TITLES.length)] + " 第" + (rnd.nextInt(20) + 1) + "版";
            author = CHINESE_AUTHORS[rnd.nextInt(CHINESE_AUTHORS.length)];
        } else {
            title = ENGLISH_TITLE_PREFIXES[rnd.nextInt(ENGLISH_TITLE_PREFIXES.length)] + " "
                  + ENGLISH_TITLE_TOPICS[rnd.nextInt(ENGLISH_TITLE_TOPICS.length)] + " Vol." + (rnd.nextInt(20) + 1);
            author = ENGLISH_AUTHORS[rnd.nextInt(ENGLISH_AUTHORS.length)];
        }
        // 唯一 ISBN:978-0 + 9 位序号 + 1 位校验位(伪)
        String isbn = String.format("978-0-%09d-%d", index + 1, rnd.nextInt(10));
        // 价格 9.99 ~ 199.99
        BigDecimal price = BigDecimal.valueOf(9 + rnd.nextInt(191))
                .add(BigDecimal.valueOf(rnd.nextInt(100)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        // 出版日期 1980 ~ 2024
        int year = 1980 + rnd.nextInt(45);
        int month = 1 + rnd.nextInt(12);
        int day = 1 + rnd.nextInt(28);
        LocalDate publishedDate = LocalDate.of(year, month, day);
        // 库存 0 ~ 200
        int stock = rnd.nextInt(201);
        return new BookSeed(title, author, isbn, price, publishedDate, stock);
    }

    private static void writeJson(List<BookSeed> books, String path) throws IOException {
        // 手动拼 JSON — 避免依赖 Jackson(项目用的是 Jackson 3.x 但 JavaTimeModule 不在 classpath,
        // 装 jackson-datatype-jsr310 又增加依赖,这里 1000 条扁平数据手动写更轻)
        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < books.size(); i++) {
            BookSeed b = books.get(i);
            sb.append("  {\n");
            sb.append("    \"title\": ").append(jsonStr(b.title)).append(",\n");
            sb.append("    \"author\": ").append(jsonStr(b.author)).append(",\n");
            sb.append("    \"isbn\": ").append(jsonStr(b.isbn)).append(",\n");
            sb.append("    \"price\": ").append(b.price.toPlainString()).append(",\n");
            sb.append("    \"publishedDate\": ").append(jsonStr(b.publishedDate.format(dateFmt))).append(",\n");
            sb.append("    \"stockQuantity\": ").append(b.stockQuantity).append("\n");
            sb.append("  }");
            if (i < books.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        Files.writeString(Path.of(path), sb.toString());
    }

    /**
     * @brief 转义 JSON 字符串(双引号、反斜杠、控制字符)
     */
    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ============== 入库模式 ==============

    private static void runSeed(SeedOptions opts) throws IOException, SQLException {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USERNAME");
        String dbPwd = System.getenv("DB_PASSWORD");
        if (dbUrl == null || dbUser == null || dbPwd == null) {
            throw new IllegalStateException(
                "环境变量 DB_URL / DB_USERNAME / DB_PASSWORD 必须设置\n"
              + "示例: export DB_URL='jdbc:mysql://localhost:3306/tmlibrary?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'");
        }

        // 读 JSON — 手动解析(避免依赖 Jackson)
        List<BookSeed> books = readJson(opts.file);
        System.out.println("[seed] 读取 " + books.size() + " 条 from " + opts.file);

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPwd)) {
            conn.setAutoCommit(false);

            if (opts.truncate) {
                try (var stmt = conn.createStatement()) {
                    stmt.executeUpdate("TRUNCATE TABLE book");
                    System.out.println("[seed] TRUNCATE TABLE book");
                }
            }

            int total = books.size();
            int succeeded = 0;
            List<Integer> failedIndexes = new ArrayList<>();

            String sql = "INSERT INTO book (title, author, isbn, price, published_date, stock_quantity) "
                       + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (int i = 0; i < total; i++) {
                    BookSeed b = books.get(i);
                    ps.setString(1, b.title);
                    ps.setString(2, b.author);
                    ps.setString(3, b.isbn);
                    ps.setBigDecimal(4, b.price);
                    ps.setDate(5, java.sql.Date.valueOf(b.publishedDate));
                    ps.setInt(6, b.stockQuantity);
                    ps.addBatch();
                    batchCount++;

                    if (batchCount >= opts.batchSize) {
                        try {
                            int[] rs = ps.executeBatch();
                            succeeded += rs.length;
                            conn.commit();
                        } catch (SQLException e) {
                            // 整批失败 → 回退到逐条,以便定位失败条目
                            System.err.println("[seed] 整批失败(" + batchCount + "条),回退逐条: " + e.getMessage());
                            conn.rollback();
                            for (int j = i - batchCount + 1; j <= i; j++) {
                                if (insertOne(conn, sql, books.get(j))) {
                                    succeeded++;
                                } else {
                                    failedIndexes.add(j);
                                }
                            }
                        }
                        System.out.printf("[seed] 已入库 %d / %d (本批 %d 条)%n", succeeded, total, batchCount);
                        batchCount = 0;
                    }
                }
                // 处理尾巴
                if (batchCount > 0) {
                    try {
                        int[] rs = ps.executeBatch();
                        succeeded += rs.length;
                        conn.commit();
                    } catch (SQLException e) {
                        System.err.println("[seed] 尾巴批失败(" + batchCount + "条),回退逐条: " + e.getMessage());
                        conn.rollback();
                        for (int j = total - batchCount; j < total; j++) {
                            if (insertOne(conn, sql, books.get(j))) {
                                succeeded++;
                            } else {
                                failedIndexes.add(j);
                            }
                        }
                    }
                    System.out.printf("[seed] 已入库 %d / %d (本批 %d 条)%n", succeeded, total, batchCount);
                }
            }

            // 汇总
            System.out.println();
            System.out.println("========== 入库汇总 ==========");
            System.out.println("总条数:    " + total);
            System.out.println("成功:      " + succeeded);
            System.out.println("失败:      " + failedIndexes.size());
            if (!failedIndexes.isEmpty()) {
                System.out.println("失败索引:  " + failedIndexes);
            }
        }
    }

    /**
     * @brief 单条插入(批失败时回退用)
     * @return true=成功, false=失败
     */
    private static boolean insertOne(Connection conn, String sql, BookSeed b) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, b.title);
            ps.setString(2, b.author);
            ps.setString(3, b.isbn);
            ps.setBigDecimal(4, b.price);
            ps.setDate(5, java.sql.Date.valueOf(b.publishedDate));
            ps.setInt(6, b.stockQuantity);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("[seed]   单条失败 isbn=" + b.isbn + " : " + e.getMessage());
            return false;
        }
    }

    // ============== 数据类 ==============

    /** 与 book 表字段一一对应(除 id/createdTime/updatedTime,这两由 DB 自动生成) */
    public static class BookSeed {
        public String title;
        public String author;
        public String isbn;
        public BigDecimal price;
        public LocalDate publishedDate;
        public int stockQuantity;
        // 这两个不入库 — 入库时由 DB DEFAULT CURRENT_TIMESTAMP 生成
        public LocalDateTime createdTime;
        public LocalDateTime updatedTime;

        public BookSeed() {} // 手动解析需要无参构造

        public BookSeed(String title, String author, String isbn, BigDecimal price,
                        LocalDate publishedDate, int stockQuantity) {
            this.title = title;
            this.author = author;
            this.isbn = isbn;
            this.price = price;
            this.publishedDate = publishedDate;
            this.stockQuantity = stockQuantity;
        }

        @Override
        public String toString() {
            return String.format("BookSeed{title=%s, author=%s, isbn=%s, price=%s, publishedDate=%s, stock=%d}",
                    title, author, isbn, price,
                    publishedDate.format(DateTimeFormatter.ISO_LOCAL_DATE), stockQuantity);
        }
    }

    // ============== 简易 JSON 解析(只支持本工具生成的扁平结构) ==============

    /**
     * @brief 手动 JSON 解析 — 只支持本 seeder 生成的扁平结构(对象数组,6 个标量字段)
     *        不处理嵌套、注释、Unicode 转义等高级特性
     */
    private static List<BookSeed> readJson(String path) throws IOException {
        String src = Files.readString(Path.of(path));
        List<BookSeed> result = new ArrayList<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        int i = 0;
        int len = src.length();
        // 跳过 [ 前空白
        while (i < len && Character.isWhitespace(src.charAt(i))) i++;
        if (i >= len || src.charAt(i) != '[') {
            throw new IllegalStateException("JSON 根必须是数组");
        }
        i++;
        while (i < len) {
            // 跳过空白和 ,
            while (i < len && (Character.isWhitespace(src.charAt(i)) || src.charAt(i) == ',')) i++;
            if (i >= len) break;
            if (src.charAt(i) == ']') break;
            if (src.charAt(i) != '{') throw new IllegalStateException("期望 {, at " + i);
            // 读一个对象
            BookSeed b = new BookSeed();
            i++; // 跳过 {
            while (i < len) {
                while (i < len && Character.isWhitespace(src.charAt(i))) i++;
                if (i < len && src.charAt(i) == '}') { i++; break; }
                // 读 key
                if (src.charAt(i) != '"') throw new IllegalStateException("期望 key 字符串, at " + i);
                String key = readString(src, i); // 返回结束位置 + 1
                i = skipPastString(src, i);
                while (i < len && Character.isWhitespace(src.charAt(i))) i++;
                if (i >= len || src.charAt(i) != ':') throw new IllegalStateException("期望 :, at " + i);
                i++;
                while (i < len && Character.isWhitespace(src.charAt(i))) i++;
                // 读 value — 每条 if/else 必须有花括号,因为块里有两条语句
                if (key.equals("title")) {
                    b.title = readStringValue(src, i); i = skipPastString(src, i);
                } else if (key.equals("author")) {
                    b.author = readStringValue(src, i); i = skipPastString(src, i);
                } else if (key.equals("isbn")) {
                    b.isbn = readStringValue(src, i); i = skipPastString(src, i);
                } else if (key.equals("price")) {
                    b.price = new BigDecimal(readNumber(src, i)); i = skipPastNumber(src, i);
                } else if (key.equals("publishedDate")) {
                    b.publishedDate = LocalDate.parse(readStringValue(src, i), dateFmt); i = skipPastString(src, i);
                } else if (key.equals("stockQuantity")) {
                    b.stockQuantity = Integer.parseInt(readNumber(src, i)); i = skipPastNumber(src, i);
                } else {
                    /* skip unknown field */
                }
                while (i < len && Character.isWhitespace(src.charAt(i))) i++;
                if (i < len && src.charAt(i) == ',') i++;
            }
            result.add(b);
        }
        return result;
    }

    private static String readString(String s, int from) {
        // from 指向开头的 "
        StringBuilder sb = new StringBuilder();
        int i = from + 1;
        while (i < s.length() && s.charAt(i) != '"') {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case '"':  sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append(n);
                }
                i += 2;
            } else {
                sb.append(s.charAt(i++));
            }
        }
        return sb.toString();
    }

    private static int skipPastString(String s, int from) {
        // from 指向开头的 ",返回结束 "
        int i = from + 1;
        while (i < s.length() && s.charAt(i) != '"') {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) i += 2; else i++;
        }
        return i + 1; // 跳过结尾 "
    }

    private static String readStringValue(String s, int from) {
        // 当前位置必须是 "
        if (s.charAt(from) != '"') throw new IllegalStateException("期望字符串值, at " + from);
        return readString(s, from);
    }

    private static String readNumber(String s, int from) {
        int i = from;
        while (i < s.length() && "-0123456789.".indexOf(s.charAt(i)) >= 0) i++;
        return s.substring(from, i);
    }

    private static int skipPastNumber(String s, int from) {
        int i = from;
        while (i < s.length() && "-0123456789.".indexOf(s.charAt(i)) >= 0) i++;
        return i;
    }

    private static class SeedOptions {
        final String file;
        final int batchSize;
        final boolean truncate;
        SeedOptions(String file, int batchSize, boolean truncate) {
            this.file = file; this.batchSize = batchSize; this.truncate = truncate;
        }
    }
}

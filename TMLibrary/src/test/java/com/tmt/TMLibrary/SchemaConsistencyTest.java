package com.tmt.TMLibrary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema 一致性守门测试。
 *
 * <p>背景:这个项目踩过一次真实的 schema 漂移 ——
 * {@code src/main/resources/db/orders.sql} 加了 {@code payment_method} 列,
 * 但生成物 {@code scripts/full-schema.sql} 没跟着重新生成,
 * 于是 README 里「一键建库」建出来的库少这一列,
 * 应用一查订单就 {@code Unknown column 'payment_method'} 直接崩。
 *
 * <p>另一个同款坑:mapper XML 里手写的列清单(「加了一列要记得改 3 个地方」)。
 * {@code OrderMapper.selectOrderPage} 就真的漏了 {@code paid_time},
 * 导致管理端订单列表的「支付时间」永远是空的。
 *
 * <p>这两类漂移都不会让编译失败、不会让启动失败,只会在跑特定查询时炸。
 * 所以在这里用测试守住 —— CI 一跑就知道。
 *
 * <p>三条断言:
 * <ol>
 *   <li>{@code full-schema.sql}(生成物)与 {@code db/*.sql}(真源)的建表列集合完全一致</li>
 *   <li>每个 mapper 的列清单与对应表的 DDL 列集合完全一致(漏列、多列都算错)</li>
 *   <li>DDL 里出现的表,mapping 侧不能有「查了但表里没有」的列</li>
 * </ol>
 *
 * <p>改完 {@code db/*.sql} 后记得跑:
 * <pre>python3 scripts/gen-full-schema.py</pre>
 */
class SchemaConsistencyTest {

    /** 模块根目录 —— Maven 跑测试时 cwd 就是模块目录。 */
    private static final Path MODULE = Path.of(System.getProperty("user.dir", "."));

    private static final Path DB_DIR = MODULE.resolve("src/main/resources/db");
    private static final Path MAPPER_DIR = MODULE.resolve("src/main/resources/mapper");
    private static final Path FULL_SCHEMA = MODULE.resolve("scripts/full-schema.sql");
    private static final Path OVERVIEW_SCHEMA = MODULE.resolve("scripts/schema.sql");

    /**
     * mapper 文件 → [(表名, 列清单来源)]。
     *
     * <p>来源写 {@code sql:<id>} 表示取 {@code <sql id="...">} 片段,
     * 写 {@code select:<id>} 表示取某个 select 里手写的 SELECT 列清单。
     *
     * <p>这里是**手工声明**的 —— MyBatis 的 SQL 没有可解析的表/列绑定关系,
     * 自动推断要写个 SQL 解析器,不划算。表一多就往这里加一行。
     */
    private static final Map<String, List<String[]>> MAPPER_COLUMN_SOURCES = new LinkedHashMap<>();

    static {
        // 文件名 → (表名, 片段 id)
        MAPPER_COLUMN_SOURCES.put("BookMapper.xml", List.<String[]>of(
                new String[]{"books", "sql:Base_Column_List"}));
        MAPPER_COLUMN_SOURCES.put("BookCategoryMapper.xml", List.<String[]>of(
                new String[]{"book_categories", "sql:Base_Column_List"}));
        MAPPER_COLUMN_SOURCES.put("UserMapper.xml", List.<String[]>of(
                new String[]{"users", "sql:Base_Column_List"}));
        MAPPER_COLUMN_SOURCES.put("IpBanMapper.xml", List.<String[]>of(
                new String[]{"ip_bans", "sql:Base_Column_List"}));
        MAPPER_COLUMN_SOURCES.put("OrderMapper.xml", List.<String[]>of(
                new String[]{"orders", "sql:Order_Base_Column_List"},
                new String[]{"order_items", "sql:OrderItem_Base_Column_List"},
                new String[]{"orders", "select:selectOrderPage"}));
        MAPPER_COLUMN_SOURCES.put("FeedbackMapper.xml", List.<String[]>of(
                new String[]{"feedbacks", "select:selectFeedbackById"}));
    }

    // ────────────────────────────────────────────────────────────────────
    // 断言 1:生成物 full-schema.sql 与真源 db/*.sql 一致
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("full-schema.sql 与 db/*.sql 的建表列集合完全一致(生成物不能过期)")
    void fullSchemaMatchesDbScripts() throws IOException {
        Map<String, Set<String>> source = readDdlFromDbDir();
        Map<String, Set<String>> generated = parseTables(Files.readString(FULL_SCHEMA, StandardCharsets.UTF_8));

        assertFalse(source.isEmpty(), "db/*.sql 里没解析出任何建表语句 —— 解析器坏了,或文件路径变了");

        Set<String> onlyInSource = new TreeSet<>(source.keySet());
        onlyInSource.removeAll(generated.keySet());
        assertTrue(onlyInSource.isEmpty(),
                () -> "full-schema.sql 少了这些表:" + onlyInSource + " —— 跑 python3 scripts/gen-full-schema.py 重新生成");

        Set<String> onlyInGenerated = new TreeSet<>(generated.keySet());
        onlyInGenerated.removeAll(source.keySet());
        assertTrue(onlyInGenerated.isEmpty(),
                () -> "full-schema.sql 多了这些表:" + onlyInGenerated + " —— 跑 python3 scripts/gen-full-schema.py 重新生成");

        for (Map.Entry<String, Set<String>> e : source.entrySet()) {
            String table = e.getKey();
            Set<String> expected = e.getValue();
            Set<String> actual = generated.getOrDefault(table, Set.of());
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(expected);
            assertTrue(missing.isEmpty() && extra.isEmpty(),
                    () -> "表 " + table + " 的建表语句与 db/*.sql 不一致"
                            + (missing.isEmpty() ? "" : " —— 缺列:" + missing)
                            + (extra.isEmpty() ? "" : " —— 多列:" + extra)
                            + " —— 跑 python3 scripts/gen-full-schema.py 重新生成");
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 断言 2:mapper 的列清单与 DDL 一致(加列必须同步改 mapper)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("mapper 的列清单与 DDL 完全一致(加列 / 删列都要同步改 mapper)")
    void mapperColumnListsMatchDdl() throws IOException {
        Map<String, Set<String>> ddl = readDdlFromDbDir();
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, List<String[]>> fileEntry : MAPPER_COLUMN_SOURCES.entrySet()) {
            String fileName = fileEntry.getKey();
            Path xmlPath = MAPPER_DIR.resolve(fileName);
            assertTrue(Files.exists(xmlPath), () -> "找不到 mapper 文件:" + xmlPath);
            String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);

            for (String[] spec : fileEntry.getValue()) {
                String table = spec[0];
                String source = spec[1];
                Set<String> expected = ddl.get(table);
                if (expected == null) {
                    problems.add(fileName + " / " + source + ":DDL 里没有表 " + table);
                    continue;
                }

                Set<String> actual = source.startsWith("sql:")
                        ? parseSqlFragment(xml, source.substring("sql:".length()))
                        : parseSelectColumns(xml, source.substring("select:".length()));

                if (actual == null) {
                    problems.add(fileName + " / " + source + ":没解析出列清单(选择器改名了?)");
                    continue;
                }

                Set<String> missing = new TreeSet<>(expected);
                missing.removeAll(actual);
                Set<String> extra = new TreeSet<>(actual);
                extra.removeAll(expected);

                if (!missing.isEmpty()) {
                    problems.add(fileName + " / " + source + " → 表 " + table
                            + ":漏查了列 " + missing + "(实体里这个字段会恒为 null)");
                }
                if (!extra.isEmpty()) {
                    problems.add(fileName + " / " + source + " → 表 " + table
                            + ":查了 DDL 里没有的列 " + extra + "(运行时会 Unknown column)");
                }
            }
        }

        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    // ────────────────────────────────────────────────────────────────────
    // 断言 3:总览表 scripts/schema.sql 与真源一致
    // ────────────────────────────────────────────────────────────────────

    /**
     * {@code scripts/schema.sql} 是「给人看的总体规划表」,只含 4 张核心表,
     * 文档里明确说了不要拿它建库。但 docker-compose.yml 的 01-schema.sql
     * 挂的就是它 —— 所以它一旦和真源漂移,容器里建出来的库就会跟着歪。
     *
     * <p>这里断言:它有的每一张表,列集合必须和 db/*.sql 完全一致。
     * (它没有的表不报错 —— 本来就只放 4 张核心表。)
     */
    @Test
    @DisplayName("scripts/schema.sql(总览表)与 db/*.sql 逐列一致")
    void overviewSchemaMatchesDbScripts() throws IOException {
        Map<String, Set<String>> source = readDdlFromDbDir();
        Map<String, Set<String>> overview =
                parseTables(Files.readString(OVERVIEW_SCHEMA, StandardCharsets.UTF_8));

        assertFalse(overview.isEmpty(), "scripts/schema.sql 里没解析出建表语句 —— 解析器坏了,或文件路径变了");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : overview.entrySet()) {
            String table = e.getKey();
            Set<String> expected = source.get(table);
            if (expected == null) {
                problems.add("总览表里有真源没有的表:" + table);
                continue;
            }
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(e.getValue());
            Set<String> extra = new TreeSet<>(e.getValue());
            extra.removeAll(expected);
            if (!missing.isEmpty()) {
                problems.add("总览表 " + table + " 少了列:" + missing);
            }
            if (!extra.isEmpty()) {
                problems.add("总览表 " + table + " 多了列:" + extra);
            }
        }

        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    // ────────────────────────────────────────────────────────────────────
    // 解析工具
    // ────────────────────────────────────────────────────────────────────

    /** 读 db/ 下所有建表脚本(除种子数据),返回 {表名 → 列名集合}。 */
    private static Map<String, Set<String>> readDdlFromDbDir() throws IOException {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(DB_DIR)) {
            List<Path> sorted = files
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> !p.getFileName().toString().equals("seed-demo-data.sql"))
                    .sorted()
                    .toList();
            for (Path p : sorted) {
                tables.putAll(parseTables(Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        return tables;
    }

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** 从一段 SQL 里抽出所有 CREATE TABLE 的列名集合。 */
    private static Map<String, Set<String>> parseTables(String sql) {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            String table = m.group(1);
            int start = m.end();
            int depth = 1;
            int i = start;
            while (i < sql.length() && depth > 0) {
                char c = sql.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                i++;
            }
            tables.put(table, parseColumnNames(sql.substring(start, Math.min(i, sql.length()))));
        }
        return tables;
    }

    /**
     * 抽建表语句体里的列名。
     *
     * <p>只认「行首就是反引号列名」的行 —— 索引(PRIMARY KEY / KEY / UNIQUE KEY)、
     * 约束、外键都不是列,自然被排除。注释行先剔掉。
     */
    private static Set<String> parseColumnNames(String body) {
        Set<String> cols = new LinkedHashSet<>();
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            Matcher cm = Pattern.compile("^`(\\w+)`\\s").matcher(line);
            if (cm.find()) {
                cols.add(cm.group(1));
            }
        }
        return cols;
    }

    /** 取 {@code <sql id="xxx">...</sql>} 里的列名。 */
    private static Set<String> parseSqlFragment(String xml, String id) {
        Matcher m = Pattern.compile("<sql\\s+id=\"" + Pattern.quote(id) + "\"\\s*>(.*?)</sql>", Pattern.DOTALL)
                .matcher(xml);
        if (!m.find()) {
            return null;
        }
        return splitColumns(m.group(1));
    }

    /**
     * 取某个 {@code <select id="xxx">} 里 SELECT 后面的列名。
     *
     * <p>只处理「SELECT 一串列 FROM 表」这种直白写法 ——
     * JOIN 查询(列名前带表别名前缀)不在本测试覆盖范围内,那些查询由各自的
     * resultMap 显式声明列名,漏列不会静默变 null,会直接查不出来。
     */
    private static Set<String> parseSelectColumns(String xml, String selectId) {
        Matcher sm = Pattern.compile("<select\\s+id=\"" + Pattern.quote(selectId) + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
                .matcher(xml);
        if (!sm.find()) {
            return null;
        }
        String body = sm.group(1);
        Matcher cm = Pattern.compile("SELECT\\s+(.*?)\\s+FROM\\s", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (!cm.find()) {
            return null;
        }
        return splitColumns(cm.group(1));
    }

    /** 按逗号切列名,去掉 AS 别名和反引号。 */
    private static Set<String> splitColumns(String raw) {
        Set<String> cols = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String col = part.trim();
            int asIdx = col.toUpperCase().indexOf(" AS ");
            if (asIdx > 0) {
                col = col.substring(0, asIdx).trim();
            }
            col = col.replace("`", "").trim();
            if (col.isEmpty() || col.contains("(") || col.contains(")") || col.contains(" ")) {
                continue;
            }
            cols.add(col);
        }
        return cols;
    }
}

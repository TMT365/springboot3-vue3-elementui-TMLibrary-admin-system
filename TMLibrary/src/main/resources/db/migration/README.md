# db/migration —— 手工迁移脚本

## 这是什么

**给已有数据库补结构用的脚本。** 新建库**不需要**执行这里的任何文件 ——
`src/main/resources/db/*.sql` 里的建表语句已经是最新的(包含所有列)。

## ⚠️ 名字里的 `V2__` 不是 Flyway

本项目**没有接入 Flyway,也没有接入 Liquibase**。`pom.xml` 里没有相关依赖,
启动时也不会有任何自动迁移。

文件名沿用 `V<版本号>__<说明>.sql` 只是为了**排序**(`V2` 在 `V10` 前面),
让人一眼看出这是"第几批改动"。**看到这种名字不要以为会自动跑** ——
它必须由你自己手动执行。

## 怎么执行

```bash
# 1. 先确认你的库是不是真的缺这一列
mysql -u<user> -p<db> -e "SELECT COLUMN_NAME FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'payment_method';"

# 2. 返回 0 行 = 还没加,执行迁移
mysql -u<user> -p<db> < src/main/resources/db/migration/V2__orders_payment_method.sql
```

## 加新迁移的规矩

1. **编号递增** —— 下一个叫 `V3__<说明>.sql`。别复用已发布的编号。
2. **幂等** —— MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`。
   本目录现有脚本的处理办法是:文件头写清楚"重复执行会报什么错,看到这个错就说明已经加过了"。
   存量大表的 `ALTER` 建议自己包一层 `information_schema` 判断(参考 `db/orders.sql` 末尾的存储过程写法)。
3. **文件头写清楚四件事**:
   - **背景** —— 为什么要改(不是"加个字段",而是"原来 paymentMethod 参数收了直接丢掉")
   - **给谁用** —— "给已有环境用,全新环境不需要执行"
   - **执行命令** —— 可直接复制的 `mysql -u<user> -p<db> < 文件路径`
   - **存量数据怎么处理** —— 迁移前的历史数据填什么。**查不到的就留 NULL,不要编。**
4. **同步改真源** —— 迁移脚本和 `src/main/resources/db/<表>.sql` 的建表语句要一致。
   改完建表脚本后跑 `python3 scripts/gen-full-schema.py` 重新生成 `scripts/full-schema.sql`。
5. **CI 会替你检查** —— `SchemaConsistencyTest` 断言:
   - `scripts/full-schema.sql`(生成物)与 `db/*.sql`(真源)的建表列集合一致
   - `scripts/schema.sql`(总览表)与 `db/*.sql` 逐列一致
   - 每个 mapper 的列清单与 DDL 一致(漏查 / 多查都算错)

   跑测试:`./mvnw test -Dtest=SchemaConsistencyTest`

## 已有脚本

| 文件 | 作用 | 存量数据 |
|---|---|---|
| `V2__orders_payment_method.sql` | `orders` 表加 `payment_method` 列 | 全部留 `NULL` —— 迁移前压根没记过用户选的支付方式,猜不得 |

# Logstash 定时同步(MySQL → Elasticsearch)

把 `books` 表(含从 `order_items` 算出的热度)定期同步到 `tmlibrary_books` 索引,
给关键字搜索用。**这个索引和应用启动时建的 `tmlibrary_books_suggest` 是两个用途:**

| 索引 | 谁写 | 用途 |
|---|---|---|
| `tmlibrary_books_suggest` | 应用启动时(`SuggestIndexInitializer`) | 搜索框下拉候选词,字段少(title/author/isbn/hot) |
| `tmlibrary_books` | **Logstash**(本目录) | 关键字全文搜索,字段全(含分类/价格/库存/日期) |

---

## 前置:IK 分词器

`books-index-template.json` 用了 `ik_max_word` / `ik_smart`,**默认 ES 镜像里没有**,
必须先装。本机已经装好了(见 `scripts/install-ik.sh`),换机器时重跑那个脚本。

验证:

```bash
curl -s -u elastic:$ES_PASSWORD -H 'Content-Type: application/json' \
  "http://localhost:9200/_analyze" \
  -d '{"analyzer":"ik_smart","text":"深入理解计算机系统"}'
# 期望:["深入","理解","计算机","系统"]   ← 不是单字
```

---

## 起容器

```bash
cd TMLibrary/scripts/logstash

# 1) 建索引模板(必须先建,否则 Logstash 自动创建的 mapping 没有 IK 分析器)
curl -u elastic:$ES_PASSWORD -X PUT "http://localhost:9200/_index_template/tmlibrary_books" \
  -H 'Content-Type: application/json' \
  --data-binary @books-index-template.json

# 2) 准备 MySQL 驱动(Logstash 镜像不带)
mkdir -p drivers
cp ~/.m2/repository/com/mysql/mysql-connector-j/9.6.0/mysql-connector-j-9.6.0.jar drivers/mysql-connector-j.jar

# 3) 起容器
docker run -d --name tmlibrary-logstash \
  --network host \
  -e MYSQL_HOST=127.0.0.1 \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD="$DB_PASSWORD" \
  -e ES_HOSTS=http://127.0.0.1:9200 \
  -e ES_USERNAME=elastic \
  -e ES_PASSWORD="$ES_PASSWORD" \
  -v "$PWD/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro" \
  -v "$PWD/drivers:/usr/share/logstash/drivers:ro" \
  docker.elastic.co/logstash/logstash:9.5.4
```

> `--network host` 是为了让容器能直接访问宿主机的 MySQL(3306)和 ES(9200)。
> macOS / Windows 上改成 `-e MYSQL_HOST=host.docker.internal -e ES_HOSTS=http://host.docker.internal:9200`
> 并去掉 `--network host`。

---

## 验证

```bash
# 看同步日志(每 2 分钟一批)
docker logs -f tmlibrary-logstash

# 索引里有几本书
curl -s -u elastic:$ES_PASSWORD "http://localhost:9200/tmlibrary_books/_count"

# 直接搜一把
curl -s -u elastic:$ES_PASSWORD -H 'Content-Type: application/json' \
  "http://localhost:9200/tmlibrary_books/_search" \
  -d '{"query":{"match":{"title":"计算机"}}}' | python3 -m json.tool | head -30
```

---

## 已知边界(用之前要知道)

1. **删除不会同步**。Logstash 的 JDBC input 只能看到"查询返回了什么",
   看不到"哪一行没了"。删书之后 ES 里那篇文档会残留。
   两种解法:① 应用删书时顺手 `DELETE /tmlibrary_books/_doc/{isbn}`;
   ② 用软删除(`books.deleted_at`),查询里过滤掉。

2. **热度有最多 2 分钟延迟**。因为全量重读是按 schedule 跑的。
   搜索排序对这点延迟不敏感,但如果要做"实时热榜"就不能靠它。

3. **全量重读在本项目规模下没问题**(12 本),上万本之后要改增量:
   加 `WHERE b.updated_time > :sql_last_value` + `use_column_value => true`,
   热度另想办法(见 logstash.conf 里的注释)。

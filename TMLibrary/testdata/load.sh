#!/bin/bash
# ============================================================================
#  TMLibrary 测试数据加载脚本
# ----------------------------------------------------------------------------
#  用法:
#    DB_URL='jdbc:mysql://localhost:3306/tmlibrary?...' \
#    DB_USERNAME=root \
#    DB_PASSWORD=your_password \
#    ./testdata/load.sh [count] [--truncate]
#
#  参数:
#    count       生成数据条数(默认 1000,只对 generate/both 生效)
#    --truncate  入库前先 TRUNCATE TABLE book(默认行为,清空旧数据)
#
#  示例(最常用 — 一条龙:生成 1000 条 + 入库):
#    DB_URL='jdbc:mysql://localhost:3306/tmlibrary?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai' \
#    DB_USERNAME=root DB_PASSWORD=xxx ./testdata/load.sh
#
#  示例(只入库已有的 books-1000.json,不再生成):
#    DB_URL=... DB_USERNAME=root DB_PASSWORD=xxx ./testdata/load.sh seed
# ============================================================================

set -e  # 任一命令失败立即退出

# ---------- 1. 校验环境变量 ----------
: "${DB_URL:?❌ 请设置 DB_URL,例如 jdbc:mysql://localhost:3306/tmlibrary?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}"
: "${DB_USERNAME:?❌ 请设置 DB_USERNAME}"
: "${DB_PASSWORD:?❌ 请设置 DB_PASSWORD}"

# ---------- 2. 切到项目根目录(TMLibrary/) ----------
# 脚本路径:testdata/load.sh → 上一级就是 TMLibrary/
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

echo "📂 项目根目录: $PROJECT_ROOT"

# ---------- 3. 编译 ----------
echo "🔨 编译项目..."
./mvnw -q compile

# ---------- 4. 找 MySQL 驱动 JAR ----------
MYSQL_JAR=$(find ~/.m2/repository/com/mysql -name "mysql-connector-j-*.jar" 2>/dev/null | head -1)
if [ -z "$MYSQL_JAR" ]; then
    echo "❌ 找不到 MySQL 驱动,请先执行: ./mvnw compile"
    exit 1
fi
echo "🔌 MySQL 驱动: $MYSQL_JAR"

# ---------- 5. 拼 classpath ----------
CP="target/classes:$MYSQL_JAR"
cd testdata

# ---------- 6. 决定命令 ----------
# 没参数或首参是数字 → both(生成 + 入库)
# 首参是 seed/generate → 透传
MODE="both"
if [ $# -gt 0 ]; then
    case "$1" in
        seed|generate|both)
            MODE="$1"
            shift
            ;;
    esac
fi

# ---------- 7. 跑 ----------
echo "🚀 启动 Seeder,模式: $MODE"
java -cp "../$CP" com.tmt.TMLibrary.testdata.BookTestDataSeeder "$MODE" "$@"

# ---------- 8. 验证 ----------
echo ""
echo "✅ 完成!验证数据条数:"
echo "   mysql -u $DB_USERNAME -p -e 'SELECT COUNT(*) FROM tmlibrary.book;'"

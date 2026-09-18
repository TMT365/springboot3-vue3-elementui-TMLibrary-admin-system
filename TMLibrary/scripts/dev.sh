#!/usr/bin/env bash
#
# 一键启动 TMLibrary 后端 (dev profile)：
#   1. 从 .env 加载 DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET 等敏感环境变量
#   2. 校验必须项非空
#   3. 透传参数给 ./mvnw spring-boot:run
#
# 首次使用：
#   cp .env.example .env       # 然后编辑 .env 填 DB_PASSWORD
#   ./scripts/dev.sh           # 启动后端
#   ./scripts/dev.sh -Dspring-boot.run.profiles=prod   # 切 profile

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"

# ---------- 1. 检查 .env ----------
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ $ENV_FILE 不存在"
    echo "   cp $PROJECT_ROOT/.env.example $ENV_FILE  # 然后编辑填 DB_PASSWORD"
    exit 1
fi

# ---------- 2. 加载 .env (KEY='value' 格式，bash 可直接 source) ----------
# set -a 让 source 进来的变量自动 export，这样 Spring Boot 才能读到
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

# ---------- 3. 校验必须项 ----------
missing_vars=()
for v in DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET; do
    if [ -z "${!v:-}" ]; then
        missing_vars+=("$v")
    fi
done

if [ ${#missing_vars[@]} -gt 0 ]; then
    echo "❌ 以下环境变量未设置: ${missing_vars[*]}"
    echo "   请编辑 $ENV_FILE"
    exit 1
fi

# ---------- 4. 打印已加载的变量（密码脱敏） ----------
echo "✅ 已加载 $ENV_FILE"
echo "   DB_URL      = $DB_URL"
echo "   DB_USERNAME = $DB_USERNAME"
echo "   DB_PASSWORD = *** (${#DB_PASSWORD} chars)"
echo "   Redis_Host      = $REDIS_HOST"
echo "   Redis_USERNAME = $REDIS_USERNAME"
echo "   Redis_PASSWORD = *** (${#REDIS_PASSWORD} chars)"
echo "   JWT_SECRET  = *** (${#JWT_SECRET} chars)"
echo "   LOG_FILE     = ${LOG_FILE:-(/opt/logs/tmlibrary.log)}"
echo "   JWT_EXPIRATION_SECONDS   = $JWT_EXPIRATION_SECONDS"

# ES 是可选的(连不上会自动降级回 MySQL),所以不做必填校验 ——
# 但**必须回显**,否则"密码写错了 → 静默降级"这种情况用户完全看不出来
if [ -n "${ES_HOSTS:-}" ] || [ -n "${ES_PASSWORD:-}" ]; then
    echo "   ES_HOSTS    = ${ES_HOSTS:-(未设置,应用默认 http://localhost:9200)}"
    echo "   ES_USERNAME = ${ES_USERNAME:-elastic}"
    if [ -n "${ES_PASSWORD:-}" ]; then
        echo "   ES_PASSWORD = *** (${#ES_PASSWORD} chars)"
    else
        echo "   ⚠️  ES_PASSWORD 为空 —— 若 ES 启用了 security,搜索会静默降级回 MySQL"
    fi
else
    echo "   ES_*        = (未配置,搜索走 MySQL 降级实现)"
fi
echo "================配置完成================="
# ---------- 5. 启动后端 (exec 让 Ctrl-C 直接传给 Maven) ----------
cd "$PROJECT_ROOT"
exec ./mvnw spring-boot:run "$@"
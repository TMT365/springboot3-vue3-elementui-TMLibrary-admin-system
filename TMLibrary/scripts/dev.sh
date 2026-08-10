#!/usr/bin/env bash
#
# 一键启动 TMLibrary 后端 (dev profile)：
#   1. 从 .env 加载 DB_URL / DB_USERNAME / DB_PASSWORD 等敏感环境变量
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
for v in DB_URL DB_USERNAME DB_PASSWORD; do
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
echo

# ---------- 5. 启动后端 (exec 让 Ctrl-C 直接传给 Maven) ----------
cd "$PROJECT_ROOT"
exec ./mvnw spring-boot:run "$@"
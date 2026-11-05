#!/usr/bin/env bash
# ============================================================
# e2e-inventory-concurrency.sh — 库存并发 E2E 压测(真实 MySQL + Redis + 后端)
#
# 背景:库存是"DB 权威 + Redis 预占"双轨模型。
#   - 下单:Redis Lua 预占(可用量 -N),DB 不动
#   - 付款:DB 条件 UPDATE 扣物理库存(WHERE stock_quantity >= qty)
#   - 取消/超时:Redis Lua 释放预占
# 不变式:Redis(stock + reserved) == DB books.stock_quantity
#
# 本脚本验三件事:
#   A. 超卖:20 个并发下单抢 5 件库存,必须恰好成功 5 个,其余全是"库存不足"
#      —— DB 物理库存永远不为负,不会被卖成负数
#   B. 并发付款:5 个订单并发付款,必须全部成功,扣减后不变式仍成立
#   C. 支付/取消互斥:同一个订单并发 pay + cancel,必须只有一个成功
#      (靠 SELECT ... FOR UPDATE 行锁串行化,不能两边都成)
#
# 用法:
#   cd TMLibrary && ./scripts/e2e-inventory-concurrency.sh
# 环境变量:
#   BOOK_ID=3  LIMIT=5  CONC=20   —— 压测用的图书 / 库存上限 / 并发数
#
# 注意:脚本会临时改 books.stock_quantity 并删除测试订单,结束时恢复原值。
# ============================================================
set -uo pipefail

cd "$(dirname "$0")/.."

# WSL 里默认带 http_proxy=127.0.0.1:7890,curl 会把本机请求也发给代理 → 502。直连。
export no_proxy='*' NO_PROXY='*'
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY

set -a; . ./.env; set +a

API="${API:-http://127.0.0.1:8080}"
BOOK_ID="${BOOK_ID:-3}"
LIMIT="${LIMIT:-5}"
CONC="${CONC:-20}"
KEY="tmlibrary:book:byId:${BOOK_ID}:inventory"
FAILURES=0

r() { redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --user "$REDIS_USERNAME" -a "$REDIS_PASSWORD" --no-auth-warning "$@"; }
sql() { mysql -h 127.0.0.1 -P 3306 -u "$DB_USERNAME" -p"$DB_PASSWORD" -N -B tmlibrary -e "$1" 2>/dev/null; }

ok()   { echo "  ✅ $1"; }
bad()  { echo "  ❌ $1"; FAILURES=$((FAILURES + 1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1($2)"; else bad "$1: 期望 $3,实际 $2"; fi; }

# ---------- 0) 登录 ----------
echo "=== 0) 登录 demo_admin ==="
UUID="conc-$$-$(date +%s)"
curl -s -X POST "$API/api/captcha/login?uuid=$UUID" -H 'Content-Type: application/json' \
  -d '{"username":"demo_admin"}' > /dev/null
CAPTCHA=$(r GET "tmlibrary:captcha:login:$UUID:code" | python3 -c 'import sys,json;print(json.load(sys.stdin)["captcha"])')
TOKEN=$(curl -s -X POST "$API/api/users/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo_admin\",\"password\":\"Demo@123456\",\"captcha\":\"$CAPTCHA\",\"uuid\":\"$UUID\"}" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}).get("token",""))')
if [ -z "$TOKEN" ]; then echo "❌ 登录失败(后端没起来?先 ./scripts/dev.sh)"; exit 1; fi
echo "  token ok"

# ---------- 准备 ----------
ORIG_STOCK=$(sql "SELECT stock_quantity FROM books WHERE id=$BOOK_ID;")
MAX_ID_BEFORE=$(sql "SELECT COALESCE(MAX(id),0) FROM orders;")
echo "=== 准备:book=$BOOK_ID 原库存=$ORIG_STOCK ==="
cleanup() {
  echo "=== 清理 ==="
  # 先删订单再删订单行 —— 反过来的话,子查询已经查不到订单行了,订单会变成孤儿
  sql "DELETE FROM orders WHERE id > $MAX_ID_BEFORE
        AND id IN (SELECT oi2.order_id FROM order_items oi2 WHERE oi2.book_id=$BOOK_ID);"
  sql "DELETE oi FROM order_items oi JOIN orders o ON o.id=oi.order_id
        WHERE oi.book_id=$BOOK_ID AND o.id > $MAX_ID_BEFORE;"
  sql "UPDATE books SET stock_quantity=$ORIG_STOCK WHERE id=$BOOK_ID;"
  r DEL "$KEY" > /dev/null
  echo "  已还原:stock=$(sql "SELECT stock_quantity FROM books WHERE id=$BOOK_ID;")"
}
trap cleanup EXIT

sql "UPDATE books SET stock_quantity=$LIMIT WHERE id=$BOOK_ID;"
r DEL "$KEY" > /dev/null

# ---------- A) 并发下单 ----------
echo "=== A) 并发下单 $CONC 次抢 $LIMIT 件库存 ==="
OUT=$(mktemp); POUT=$(mktemp); ROUT=$(mktemp)
for i in $(seq 1 $CONC); do
  ( curl -s -X POST "$API/api/purchases" -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' -d "{\"items\":[{\"bookId\":$BOOK_ID,\"quantity\":1}]}" >> "$OUT" ) &
done
wait
OK_COUNT=$(grep -o '"code":200' "$OUT" | wc -l)
echo "  下单成功=$OK_COUNT 失败=$((CONC - OK_COUNT))"
grep -o '"msg":"[^"]*"' "$OUT" | sort | uniq -c | head -5
check "成功下单数必须等于库存上限" "$OK_COUNT" "$LIMIT"
RS=$(r HGET "$KEY" stock); RR=$(r HGET "$KEY" reserved); RS=${RS:-0}; RR=${RR:-0}
echo "  Redis stock=$RS reserved=$RR | DB stock=$(sql "SELECT stock_quantity FROM books WHERE id=$BOOK_ID;")"
check "预占后 Redis(reserved) 等于下单成功数" "$RR" "$OK_COUNT"

# ---------- B) 并发付款 ----------
echo "=== B) 并发付款 ==="
NUMS=$(sql "SELECT o.order_number FROM orders o JOIN order_items oi ON oi.order_id=o.id
             WHERE oi.book_id=$BOOK_ID AND o.order_status=0 AND o.id > $MAX_ID_BEFORE;" | tr '\n' ' ')
for num in $NUMS; do
  ( curl -s -X PATCH "$API/api/purchases/$num/pay?paymentMethod=ALIPAY" \
      -H "Authorization: Bearer $TOKEN" >> "$POUT" ) &
done
wait
PAY_OK=$(grep -o '"code":200' "$POUT" | wc -l)
PAY_TOTAL=$(wc -w <<< "$NUMS")
echo "  付款成功=$PAY_OK / $PAY_TOTAL"
grep -o '"msg":"[^"]*"' "$POUT" | sort | uniq -c | head -5
check "并发付款必须全部成功" "$PAY_OK" "$PAY_TOTAL"
DB_STOCK=$(sql "SELECT stock_quantity FROM books WHERE id=$BOOK_ID;")
echo "  DB stock=$DB_STOCK"
if [ "$DB_STOCK" -lt 0 ]; then bad "DB 物理库存变成负数了: $DB_STOCK"; else ok "DB 物理库存没有变成负数"; fi
RS=$(r HGET "$KEY" stock); RR=$(r HGET "$KEY" reserved); RS=${RS:-0}; RR=${RR:-0}
echo "  Redis stock=$RS reserved=$RR sum=$((RS+RR)) | DB stock=$DB_STOCK"
check "不变式 Redis(stock+reserved)==DB stock" "$((RS+RR))" "$DB_STOCK"

# ---------- C) 支付/取消互斥 ----------
echo "=== C) 同一个订单并发 pay + cancel ==="
sql "UPDATE books SET stock_quantity=$LIMIT WHERE id=$BOOK_ID;"
r DEL "$KEY" > /dev/null
# 下单接口返回的是订单 id,这里从库里取订单号(接口的 path 参数是 order_number)
curl -s -X POST "$API/api/purchases" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"items\":[{\"bookId\":$BOOK_ID,\"quantity\":1}]}" > /dev/null
ONE=$(sql "SELECT o.order_number FROM orders o JOIN order_items oi ON oi.order_id=o.id
           WHERE oi.book_id=$BOOK_ID AND o.id > $MAX_ID_BEFORE ORDER BY o.id DESC LIMIT 1;")
if [ -z "$ONE" ]; then bad "创建订单失败,跳过互斥测试"; else
  ( curl -s -X PATCH "$API/api/purchases/$ONE/pay?paymentMethod=ALIPAY" \
      -H "Authorization: Bearer $TOKEN" >> "$ROUT" ) &
  ( curl -s -X DELETE "$API/api/purchases/$ONE" -H "Authorization: Bearer $TOKEN" >> "$ROUT" ) &
  wait
  WIN=$(grep -o '"code":200' "$ROUT" | wc -l)
  echo "  pay/cancel 成功数=$WIN (必须恰好 1)"
  grep -o '"msg":"[^"]*"' "$ROUT" | sort | uniq -c | head -5
  check "支付与取消只能有一个成功" "$WIN" "1"
  RS=$(r HGET "$KEY" stock); RR=$(r HGET "$KEY" reserved); RS=${RS:-0}; RR=${RR:-0}
  DB_STOCK=$(sql "SELECT stock_quantity FROM books WHERE id=$BOOK_ID;")
  echo "  Redis stock=$RS reserved=$RR sum=$((RS+RR)) | DB stock=$DB_STOCK"
  check "互斥之后不变式仍成立" "$((RS+RR))" "$DB_STOCK"
fi

rm -f "$OUT" "$POUT" "$ROUT"
echo ""
if [ "$FAILURES" -eq 0 ]; then echo "✅ 全部通过"; else echo "❌ $FAILURES 项断言失败"; exit 1; fi

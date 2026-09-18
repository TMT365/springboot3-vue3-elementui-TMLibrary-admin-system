#!/usr/bin/env bash
#
# 给 ES 容器装 IK 中文分词器
#
# ============================================================================
# 为什么需要这个脚本(而不是一条 elasticsearch-plugin install 就完事)
# ============================================================================
# 1. **默认 ES 镜像没有中文分词器**。不装的话 standard 分析器会把中文切成单字
#    (「深入理解」→ 深/入/理/解),单字匹配会带来大量假阳性 ——
#    「设计」会命中「计算机」(因为有个「计」字)。
#
# 2. **IK 只发布到 9.5.3,而本项目 ES 是 9.5.4**。
#    ES 在安装阶段做**严格版本校验**(PluginsUtils.verifyCompatibility),
#    patch 不同也直接抛 IllegalArgumentException,不是警告。
#
#    所以这个脚本的做法是:下载最接近的 IK 版本 → 把
#    plugin-descriptor.properties 里的 elasticsearch.version 改成当前 ES 版本
#    → 重新打包 → 安装。
#
#    这是社区通行的做法(9.5.x 内部 API 面在 patch 之间是稳定的),
#    但**属于绕过官方的兼容性断言**,升级 ES 主/次版本时要重新评估。
#
# ============================================================================
# 用法
#   ./scripts/install-ik.sh              # 用默认值
#   ES_CONTAINER=my-es ./scripts/install-ik.sh
#
# 回滚(ES 起不来时)
#   docker exec <容器> rm -rf /usr/share/elasticsearch/plugins/analysis-ik
#   docker restart <容器>
# ============================================================================

set -euo pipefail

ES_CONTAINER="${ES_CONTAINER:-es-local-dev}"
IK_VERSION="${IK_VERSION:-9.5.3}"
IK_BASE="${IK_BASE:-https://release.infinilabs.com/analysis-ik/stable}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m✗\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 0. 环境检查 ----------
command -v docker > /dev/null || die "找不到 docker"
command -v curl   > /dev/null || die "找不到 curl"
command -v python3 > /dev/null || die "找不到 python3(用来改 zip,系统可能没装 zip 命令)"

docker inspect "$ES_CONTAINER" > /dev/null 2>&1 || die "容器不存在: $ES_CONTAINER"

# 从容器里读出真实 ES 版本 —— 不要用脚本里写死的,否则换个镜像就错
ES_VERSION="$(docker exec "$ES_CONTAINER" bash -c \
  'cat /usr/share/elasticsearch/lib/elasticsearch-*.jar 2>/dev/null | head -c0; ls /usr/share/elasticsearch/lib/ | grep -oP "(?<=elasticsearch-)[0-9.]+(?=\.jar)" | head -1' 2>/dev/null || true)"
if [ -z "$ES_VERSION" ]; then
  # 退路:直接问 ES 自己
  ES_VERSION="$(curl -s -u "${ES_USERNAME:-elastic}:${ES_PASSWORD:-}" \
    "http://localhost:9200/" | python3 -c 'import json,sys;print(json.load(sys.stdin)["version"]["number"])' 2>/dev/null || true)"
fi
[ -n "$ES_VERSION" ] || die "读不到 ES 版本(设 ES_PASSWORD 环境变量试试)"
log "容器 $ES_CONTAINER 的 ES 版本: $ES_VERSION"

# 已经装过就跳过
if docker exec "$ES_CONTAINER" bin/elasticsearch-plugin list 2>/dev/null | grep -q analysis-ik; then
  log "IK 已安装,无需重复操作"
  exit 0
fi

# ---------- 1. 下载最接近的 IK ----------
IK_URL="$IK_BASE/elasticsearch-analysis-ik-${IK_VERSION}.zip"
log "下载 IK $IK_VERSION : $IK_URL"
curl -fsSL --max-time 180 -o "$WORK_DIR/ik.zip" "$IK_URL" \
  || die "下载失败。去 https://release.infinilabs.com/analysis-ik/stable/ 看看有哪些版本,用 IK_VERSION=x.y.z 重跑"

log "校验包内容"
python3 - "$WORK_DIR/ik.zip" <<'PY' || die "下载到的不是合法的 IK 插件包"
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
names = z.namelist()
assert 'plugin-descriptor.properties' in names, "缺少 plugin-descriptor.properties"
print("  包内条目数:", len(names))
PY

# ---------- 2. 改描述文件里的 ES 版本号 ----------
log "把 plugin-descriptor.properties 的 elasticsearch.version 改成 $ES_VERSION"
python3 - "$WORK_DIR/ik.zip" "$WORK_DIR/patched.zip" "$ES_VERSION" <<'PY'
import sys, zipfile, re, io
src, dst, target = sys.argv[1], sys.argv[2], sys.argv[3]
zin = zipfile.ZipFile(src)
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'plugin-descriptor.properties':
            text = data.decode('utf-8')
            text = re.sub(r'^elasticsearch\.version=.*$',
                          f'elasticsearch.version={target}', text, flags=re.M)
            data = text.encode('utf-8')
        zout.writestr(item, data)
# 回读确认
out = zipfile.ZipFile(dst)
desc = out.read('plugin-descriptor.properties').decode()
for line in desc.splitlines():
    if line.startswith(('version=', 'elasticsearch.version=')):
        print('  ', line)
PY

# ---------- 3. 装进容器 ----------
log "复制进容器并安装(--batch 自动确认 outbound_network 授权)"
docker cp "$WORK_DIR/patched.zip" "$ES_CONTAINER:/tmp/ik.zip"
docker exec "$ES_CONTAINER" bin/elasticsearch-plugin install --batch file:///tmp/ik.zip
docker exec "$ES_CONTAINER" rm -f /tmp/ik.zip

# ---------- 4. 重启 + 验证 ----------
log "重启容器"
docker restart "$ES_CONTAINER" > /dev/null

log "等待 ES 起来(最多 120 秒)"
for i in $(seq 1 40); do
  if curl -s -u "${ES_USERNAME:-elastic}:${ES_PASSWORD:-}" --max-time 3 \
      "http://localhost:9200/_cluster/health" -o /dev/null 2>/dev/null; then
    log "ES 已就绪"
    break
  fi
  [ "$i" -eq 40 ] && die "ES 120 秒没起来 —— 执行回滚: docker exec $ES_CONTAINER rm -rf /usr/share/elasticsearch/plugins/analysis-ik && docker restart $ES_CONTAINER"
  sleep 3
done

log "验证 IK 分词"
curl -s -u "${ES_USERNAME:-elastic}:${ES_PASSWORD:-}" -H 'Content-Type: application/json' \
  "http://localhost:9200/_analyze" \
  -d '{"analyzer":"ik_smart","text":"深入理解计算机系统"}' \
  | python3 -c 'import json,sys;t=[x["token"] for x in json.load(sys.stdin)["tokens"]];print("  ",t);assert len(t)>2,"分词结果像单字切分,IK 可能没生效"'

log "完成。注意:换了分析器之后,**已有索引必须重建**(mapping 里写着 analyzer)。"
log "      应用重启会自动重建 tmlibrary_books_suggest;tmlibrary_books 由 Logstash 重建。"

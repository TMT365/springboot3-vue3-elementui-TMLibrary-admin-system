# ============================================================================
# TMLibrary 前端镜像 —— 多阶段构建
#
#   阶段 1 (build)  : node 22 装依赖 + vite 构建出静态文件
#   阶段 2 (runtime): nginx 只托管 dist/,不带 node_modules
#
# 最终镜像里没有 Node、没有源码、没有 node_modules(通常几百 MB)。
#
# 构建:
#   docker build -t tmlibrary-frontend:local ./frontend
#
# 前端是**静态文件**,不需要在构建期知道后端地址:
# 代码里所有请求都是相对路径(/api/...),由 nginx 反向代理转发,
# 见 nginx.conf。换后端只需改 nginx 配置,不用重新构建镜像。
# ============================================================================

# ---------------------------------------------------------------------------
# 阶段 1:构建
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# 先只拷锁文件装依赖 —— 改业务代码不会触发重新 npm ci
COPY package.json package-lock.json ./
# npm ci 而不是 npm install:严格按 lock 文件装,可复现;
# npm install 可能悄悄升级次要版本,构建结果因机器而异
RUN npm ci

COPY . .

# build 脚本 = vue-tsc -b && vite build
# 类型检查不过就构建失败 —— 这是有意的,别为了"能出包"跳过它
RUN npm run build

# ---------------------------------------------------------------------------
# 阶段 2:运行
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine

# 后端地址。给默认值是为了让 envsubst 一定有东西可替 ——
# 留空的话 nginx.conf 里的 proxy_pass 会变成空字符串,配置直接报错。
# docker-compose 里会显式覆盖成服务名。
ENV BACKEND_UPSTREAM=backend:8080

# ⚠️ 必须放 templates/ 且以 .template 结尾。
#    官方 nginx 镜像的 docker-entrypoint.d/20-envsubst-on-templates.sh 会把
#    templates/*.template 做变量替换后输出到 conf.d/。
#    直接放 conf.d/ 的话 ${BACKEND_UPSTREAM} 会被当成字面量,proxy_pass 解析失败。
#    替换只作用于**环境里存在的变量名**,所以 $host / $uri / $remote_addr
#    这些 nginx 内建变量不会被误伤。
COPY nginx.conf /etc/nginx/templates/tmlibrary.conf.template

# 只拷构建产物
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]

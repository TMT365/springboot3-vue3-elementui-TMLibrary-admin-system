# ============================================================================
# TMLibrary 后端镜像 —— 多阶段构建
#
#   阶段 1 (build)  : maven + JDK 25 编译打包
#   阶段 2 (runtime): 只带 JRE 25,不带 Maven、不带源码、不带 .m2
#
# 最终镜像里**没有**源码和构建工具,也没有 .env —— 所有凭据走运行时环境变量。
#
# 构建:
#   docker build -t tmlibrary-backend:local ./TMLibrary
#
# 单跑(需自备 MySQL / Redis):
#   docker run --rm -p 8080:8080 \
#     -e DB_URL='jdbc:mysql://host.docker.internal:3306/tmlibrary?...' \
#     -e DB_USERNAME=root -e DB_PASSWORD=*** \
#     -e REDIS_HOST=host.docker.internal -e REDIS_PASSWORD=*** \
#     -e JWT_SECRET=*** -e CORS_ORIGINS='http://localhost:8081' \
#     tmlibrary-backend:local
#
# 一般不用单跑,直接用仓库根的 docker-compose.yml。
# ============================================================================

# ---------------------------------------------------------------------------
# 阶段 1:构建
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# 先只拷 pom.xml 并拉依赖 —— 这一层会被缓存。
# 之后改 Java 源码不会让依赖重新下载(离线构建时这点尤其重要)。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码。放后面是为了让上面的依赖层尽量复用
COPY src ./src

# -DskipTests:镜像构建阶段不跑测试。
# 测试要连 MySQL/Redis,而构建容器里没有 —— 跑必然失败。
# 测试请在 CI 或本地开发环境跑,不要塞进镜像构建。
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# 阶段 2:运行
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine

# wget 给 HEALTHCHECK 用;alpine 基础镜像默认没有
RUN apk add --no-cache wget

# 不用 root 跑应用。基础镜像自带 uid 1000 的 nobody,
# 新建一个专用用户更明确,也避免和宿主机 uid 撞
RUN addgroup -S tmlibrary && adduser -S -G tmlibrary -u 10001 tmlibrary

WORKDIR /app

# 只拷 jar。--chown 一步到位,省一层 RUN chown
COPY --from=build --chown=tmlibrary:tmlibrary /build/target/*.jar /app/app.jar

# 日志目录:logback-spring.xml 的默认值是 logs/tmlibrary.log(相对路径),
# 容器里落到 /app/logs。做成卷,日志才能留在宿主机上
RUN mkdir -p /app/logs && chown -R tmlibrary:tmlibrary /app/logs
VOLUME ["/app/logs"]

USER tmlibrary

EXPOSE 8080

# 用 prod profile —— 它会强制要求环境变量齐全(缺 CORS_ORIGINS 直接启动失败)。
# 这是有意的:生产配置缺失时"起不来"比"起来了但跨域全挂"好排查。
ENV SPRING_PROFILES_ACTIVE=prod

# 容器内存感知:JDK 默认按宿主机内存算堆,容器限了内存也照算 → OOMKilled。
# MaxRAMPercentage 让 JVM 按 cgroup 限额算。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

# actuator health 已在 application.yml 里暴露(include: health,metrics),
# 且不在 /api/* 下 → 不走 JWT 过滤器,可以直接探
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# exec 形式 + sh -c 是为了让 $JAVA_OPTS 能被展开
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

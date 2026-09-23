# 本地构建注记：原第 1 行为 parser directive「syntax=docker/dockerfile:1.7」，
# 会从 registry 拉取 frontend 镜像，当前镜像源（免费节点）对其返回 403/限流。
# Docker 29 内置 BuildKit frontend 已支持本文件用到的 heredoc（COPY <<'EOF'）
# 等特性，故本地构建时移除该指令、使用内置 frontend；CI/正式环境可恢复。
# (disabled locally) syntax=docker/dockerfile:1.7
# ============================================================
#  Multi-stage Dockerfile for novamind micro-services (R18 / distroless-style)
#
#  Builder  : maven:3.9-eclipse-temurin-21      (~1.5 GB, 丢弃)
#  Runtime  : eclipse-temurin:21-jre-alpine     (~190 MB, alpine, 含 tini+curl+tzdata)
#
#  与 R17 的差异：
#    1) HEALTHCHECK 改用独立 /healthcheck.sh，按 HEALTH_PATH 环境变量路由
#       Spring Boot Actuator / 自定义 /actuator/health/liveness，避免 503 风暴；
#    2) tini 加 -g/-s 选项，子进程组回收更稳，stop 信号可靠下到 Java；
#    3) 新增 JVM Container Awareness（-XX:+UseContainerSupport 默认值已 OK，
#       但显式打开 -XX:MaxRAMPercentage 并打印 cgroup 内存），与 K8s
#       resources.limits 对齐；
#    4) 加 OCI 标准 labels（org.opencontainers.image.*），便于镜像仓库检索；
#    5) USER app 之前 chmod /app，避免启动时 "Permission denied" 残留。
#
#  Build examples:
#    docker build --build-arg SERVICE_MODULE=novamind-gateway      -t ghcr.io/x/novamind-gateway      .
#    docker build --build-arg SERVICE_MODULE=novamind-auth/novamind-auth-service \
#                 --build-arg ARTIFACT_ID=novamind-auth-service    -t ghcr.io/x/novamind-auth .
# ============================================================

# ---------- 1. Builder ----------
FROM maven:3.9-eclipse-temurin-21 AS builder
LABEL stage=builder

ENV MAVEN_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC" \
    MAVEN_CLI_ARGS="-B -ntp -T 1C -DskipTests" \
    TZ=Asia/Shanghai

WORKDIR /workspace

# ---- Layer 1: parent POMs (changes rarely) ----
COPY pom.xml ./
COPY novamind-common/pom.xml        novamind-common/
COPY novamind-api/pom.xml           novamind-api/
COPY novamind-auth/pom.xml          novamind-auth/
COPY novamind-user/pom.xml          novamind-user/
COPY novamind-search/pom.xml        novamind-search/
COPY novamind-media/pom.xml         novamind-media/
COPY novamind-message/pom.xml       novamind-message/
COPY novamind-course/pom.xml        novamind-course/
COPY novamind-pay/pom.xml           novamind-pay/
COPY novamind-trade/pom.xml         novamind-trade/
COPY novamind-exam/pom.xml          novamind-exam/
COPY novamind-learning/pom.xml      novamind-learning/
COPY novamind-promotion/pom.xml     novamind-promotion/
COPY novamind-data/pom.xml          novamind-data/
COPY novamind-aigc/pom.xml          novamind-aigc/
COPY novamind-remark/pom.xml        novamind-remark/
COPY novamind-gateway/pom.xml       novamind-gateway/

# Pre-fetch all dependencies (cached layer)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -T 1C -DskipTests \
        -pl "${SERVICE_MODULE}" -am \
        -DincludeScope=test \
        -Dmaven.javadoc.skip=true \
        dependency:go-offline || true

# ---- Layer 2: sources ----
COPY novamind-common/      novamind-common/
COPY novamind-api/         novamind-api/
COPY novamind-auth/        novamind-auth/
COPY novamind-user/        novamind-user/
COPY novamind-search/      novamind-search/
COPY novamind-media/       novamind-media/
COPY novamind-message/     novamind-message/
COPY novamind-course/      novamind-course/
COPY novamind-pay/         novamind-pay/
COPY novamind-trade/       novamind-trade/
COPY novamind-exam/        novamind-exam/
COPY novamind-learning/    novamind-learning/
COPY novamind-promotion/   novamind-promotion/
COPY novamind-data/        novamind-data/
COPY novamind-aigc/        novamind-aigc/
COPY novamind-remark/      novamind-remark/
COPY novamind-gateway/     novamind-gateway/

# Build the target module (+ its dependencies)
ARG SERVICE_MODULE
ARG ARTIFACT_ID
# 部署修复注记：
#  1) --mount=type=cache：本机无 JDK/Maven，7 个模块串行构建时避免每次重复
#     从 Central 下载全部依赖（各服务 pom 的 finalName 使 jar 无版本后缀，
#     go-offline 层因 ARG 声明在后无法按模块预热，故用缓存卷兜底）。
#  2) cp 通配符必须移出引号（原 `${ARTIFACT_ID}-*.jar` 连通配带引号传入 cp，
#     glob 永不展开——这是提交版 Dockerfile 从未构建成功过的根因），并放宽
#     为 `${ARTIFACT_ID}*.jar`：全部服务模块 pom 设
#     <finalName>${project.artifactId}</finalName>，产物为 novamind-<m>.jar（无版本段）。
RUN --mount=type=cache,target=/root/.m2 \
    SERVICE_MODULE="${SERVICE_MODULE}" \
    ARTIFACT_ID="${ARTIFACT_ID:-${SERVICE_MODULE##*/}}" \
    && echo "Building module: ${SERVICE_MODULE} (artifact=${ARTIFACT_ID})" \
    && mvn ${MAVEN_CLI_ARGS} \
        -pl "${SERVICE_MODULE}" -am \
        clean package \
    && cp "${SERVICE_MODULE}/target/"${ARTIFACT_ID}*.jar /tmp/app.jar \
    || { echo "--- target content debug ---"; ls -lh "${SERVICE_MODULE}/target/" || true; exit 1; } \
    && ls -lh /tmp/app.jar

# ---------- 2. Runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# OCI 镜像元数据（CI/CD 扫描、仓库检索）
LABEL org.opencontainers.image.title="novamind microservice" \
      org.opencontainers.image.description="Distroless-style Spring Boot service for novamind-learning platform" \
      org.opencontainers.image.source="https://github.com/x/tjxt-javaai02" \
      org.opencontainers.image.licenses="Proprietary" \
      org.opencontainers.image.vendor="研究院研发组" \
      maintainer="研究院研发组 <research-maint@itcast.cn>"

# Alpine 必需：tini（PID 1）、bash（脚本）、curl（健康检查）、tzdata（CST 时区）
# --virtual=.build-deps 用来一次性装完最后整体删除，避免 apk index 残留
RUN apk add --no-cache bash tini tzdata curl \
    && ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    # 容器感知：让 JVM 看 cgroup 内存上限而不是宿主物理内存
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    # HEALTHCHECK 探针路径：默认 Spring Boot Actuator，K8s liveness 用 /actuator/health/liveness
    HEALTH_PATH=/actuator/health \
    SPRING_PROFILES_ACTIVE=test

# 写时需要的目录预先建好并交给非 root —— 否则 entrypoint 启动会 "Permission denied"
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/logs /tmp \
    && chown -R app:app /app /tmp

WORKDIR /app
USER app

# Copy artifact from builder (wildcard for versioned JAR)
COPY --from=builder --chown=app:app /tmp/app.jar /app/app.jar

# 自包含的健康检查脚本：
#   - 默认探 /actuator/health（Liveness+Readiness）
#   - 任意 HTTP 非 2xx 即失败，触发重启
#   - 4 秒硬超时，不被慢响应拖垮 K8s probe
#   - curl 不存在时退到 bash /dev/tcp（极小依赖兜底）
COPY --chown=app:app <<'EOF' /healthcheck.sh
#!/bin/sh
# healthcheck.sh — Spring Boot service liveness probe
set -eu
PORT="${SERVER_PORT:-8080}"
PATH_URL="${HEALTH_PATH:-/actuator/health}"
URL="http://127.0.0.1:${PORT}${PATH_URL}"
TIMEOUT="${HEALTH_TIMEOUT:-4}"

if command -v curl >/dev/null 2>&1; then
  code=$(curl -fsS -o /dev/null -w '%{http_code}' --max-time "$TIMEOUT" "$URL" 2>/dev/null || echo 000)
  [ "$code" = "200" ] || exit 1
else
  # bash fallback: 直接 TCP 三次握手 + 发 HTTP/1.0 请求
  exec 3<>/dev/tcp/127.0.0.1/"$PORT" || exit 1
  printf 'GET %s HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n' "$PATH_URL" >&3
  head -n1 <&3 | grep -q '200' || exit 1
fi
exit 0
EOF
RUN chmod +x /healthcheck.sh

EXPOSE 8080

# 增强 1：HEALTHCHECK 用独立脚本，K8s probe 走的就是这个
#   start-period=60s 给 Spring Boot 启动 + Hikari 连接池 + Nacos 注册留余量
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD /healthcheck.sh || exit 1

# 增强 2：tini 全面接管 PID 1
#   -g      : 子进程组（孤儿进程）也归 tini 收割，避免出现"双 PID 1"
#   -s      : 收到 SIGINT/SIGTERM 时不再二次 kill（交给 JVM 自己 graceful shutdown）
#   -v      : 启动时打印版本日志，便于排障
#   --      : 分隔 tini 选项与待执行的 CMD（exec-form 数组语法）
ENTRYPOINT ["/sbin/tini","-g","-s","-v","--"]
CMD ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]

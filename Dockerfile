# ===== 构建阶段：Maven + JDK 21 编译打包 =====
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 使用阿里云镜像加速依赖下载（国内构建必备）
COPY docker/maven-settings.xml /root/.m2/settings.xml
# 先拷 pom，利用 Docker 层缓存避免每次重下依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# ===== 运行阶段：精简 JRE 镜像 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# 安装 curl（供 HEALTHCHECK 使用；装完清理 apt 缓存减体积）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 拷贝构建产物（此时仍为 root，便于设属主）
COPY --from=builder /build/target/*.jar app.jar

# 非 root 用户运行（EXPOSE 8080 > 1024，非 root 可绑定）
RUN useradd -r -s /bin/false appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# 容器内 JVM 调优：按容器内存上限自动配堆；OOM 主动退出便于 compose restart 拉起
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

# 健康检查：依赖 actuator /actuator/health（application.yml 已暴露 health 端点）
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

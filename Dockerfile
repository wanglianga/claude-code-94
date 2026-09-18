# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行
RUN groupadd --system appgroup && useradd --system --gid appgroup --home-dir /app appuser

COPY --from=build /build/target/coldchain.jar /app/coldchain.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# 健康检查：依赖 actuator 未引入，直接用登录探活接口
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/api/health >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-jar", "/app/coldchain.jar"]

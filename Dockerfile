# ============================================================
# Lion Resume Review · 后端运行环境镜像（不含构建、不含 jar）
# 说明：只准备 JRE 运行时；jar 由 docker-compose 在运行时挂载到 /app/app.jar
#   mvn -B -DskipTests package      # 宿主机打 jar → target/resume-review-1.0.0.jar
#   docker compose up -d --build backend   # 换 jar 只需重启容器：docker compose restart backend
# ============================================================

FROM eclipse-temurin:17-jre-jammy

# curl：供容器健康检查访问 /actuator/health
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    # JDK 17 默认开启容器内存感知，这里限制堆上限避免被 OOM Kill
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

# 非 root 运行；/app/data 为简历上传目录（resume.upload-dir=./data/uploads）
RUN useradd -r -u 10001 -m -d /app appuser \
    && mkdir -p /app/data/uploads \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# jar 由外部挂载（compose 的 volumes），缺失时给出明确提示
ENTRYPOINT ["sh", "-c", "\
    if [ ! -f /app/app.jar ]; then \
      echo '[ERROR] /app/app.jar not found: 请先 mvn -DskipTests package 并检查 compose 的 JAR_FILE 挂载路径'; \
      exit 1; \
    fi; \
    exec java $JAVA_OPTS -jar /app/app.jar"]

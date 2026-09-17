# lotask4j-backend 运行镜像 (薄壳可执行 JAR, 业务全部来自 starter — issue #4)
# 构建前置: 先在宿主机构建前端双产物并同步 →
#   cd frontend && pnpm build:embed && pnpm sync-embed && pnpm build && pnpm sync-console
# 再打包 → mvn -pl lotask4j-server-spring-boot-starter,lotask4j-backend package -DskipTests
# (父 POM 在仓库根, 走 relativePath; framework4j 走 JitPack)
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY lotask4j-backend/target/lotask4j-*.jar app.jar

EXPOSE 9080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

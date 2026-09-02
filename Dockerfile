# lotask4j-backend 运行镜像 (thin JAR)
# 构建前置: 先在宿主机打包 → mvn -pl lotask4j-backend -am package -DskipTests
# (父 POM 在仓库根, 走 relativePath; 依赖走 Aliyun 公共镜像 + JitPack, 均可公网解析)
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY lotask4j-backend/target/lotask4j-*.jar app.jar

EXPOSE 9080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

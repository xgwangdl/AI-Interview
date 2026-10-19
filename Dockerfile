# Light-Interview 后端镜像：一条命令就能跑起来（见 README 的「一键跑起来」）。
#
# 构建阶段用仓库自带的 mvnw，不额外拉 Maven 镜像；运行阶段只放 jar 与中文字体。

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

# 先只复制 pom：依赖没变时这一层能用缓存，重复构建快很多
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
COPY common/pom.xml common/
COPY consumer/pom.xml consumer/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl consumer -am dependency:go-offline

COPY common/src common/src
COPY consumer/src consumer/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl consumer -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# 简历导出 PDF 要内嵌中文字体。这里装**文泉驿**（TrueType）而不是 Noto CJK：
# 后者是 CFF 轮廓，PDFBox 能加载却内嵌不了（见 ResumePdfFonts 的说明）。
RUN apk add --no-cache ffmpeg font-wqy-zenhei tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app app \
    && mkdir -p /app/data/media \
    && chown -R 10001:10001 /app

WORKDIR /app
COPY --from=build /src/consumer/target/consumer-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8088
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8088/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

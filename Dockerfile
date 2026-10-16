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
RUN apk add --no-cache font-wqy-zenhei tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app
COPY --from=build /src/consumer/target/consumer-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# Light-Interview 本地启动手顺

适用于 Windows PowerShell。本文只描述本机开发方式：

- PostgreSQL、Redis、S3mock 用 Docker；
- Spring Boot 后端直接运行 JAR；
- Vue 前端用 Vite 开发服务器。

## 0. 先约定

如果希望 Codex 只改代码、不启动或重启服务，可以直接说：

> 只改代码，不启动服务，最后给我启动手顺。

这种情况下不需要让 Codex 占用 `8088` 或 `5173` 端口，按本文自行启动即可。

## 1. 打开仓库目录

每个新终端都先执行：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
```

## 2. 首次准备

确认工具都已安装：

```powershell
java -version
mvn -v
node -v
npm -v
docker version
```

需要 Java 21、Maven、Node.js/npm，以及已启动的 Docker Desktop。

如果没有 `.env`：

```powershell
Copy-Item .env.example .env
```

至少检查这些配置：

```dotenv
AUTH_TOKEN_SECRET=换成一个只在本机使用的长字符串
Ali_API_KEY=你的百炼 API Key
SERVER_PORT=8088
```

`.env` 已被 Git 忽略，不要提交真实 Key。

## 3. 日常启动

### 3.1 启动基础设施

终端一：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
docker compose up -d
docker compose ps
```

正常状态：

- `light-interview-postgres` 为 `healthy`
- `light-interview-redis` 为 `healthy`
- `light-interview-s3mock` 为 `Up`

只有数据库和 Redis 是必需项；如果 `.env` 使用本地录像目录，S3mock 不启动也不影响基本功能。

### 3.2 构建并启动后端

终端二：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
mvn -B package -DskipTests
java -jar consumer\target\consumer-0.0.1-SNAPSHOT.jar
```

首次启动会执行 Flyway 数据库迁移，通常需要 15-30 秒。看到下面这行表示启动成功：

```text
Started ConsumerApplication
```

另开一个 PowerShell 检查健康状态：

```powershell
Invoke-RestMethod http://localhost:8088/actuator/health
```

期望返回：

```text
status
------
UP
```

### 3.3 启动前端

终端三：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview\web
if (-not (Test-Path node_modules)) { npm ci }
npm run dev
```

浏览器打开：

```text
http://localhost:5173
```

可以直接注册新账号，或使用演示账号：

```text
smoke / 123456
```

## 4. 每次修改代码后怎么重启

### 只改前端

不用动后端。Vite 会热更新；如果页面状态异常，在运行 `npm run dev` 的终端按 `Ctrl+C`，再执行：

```powershell
npm run dev
```

### 只改后端

先在运行后端的终端按 `Ctrl+C`，然后：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
mvn -B package -DskipTests
java -jar consumer\target\consumer-0.0.1-SNAPSHOT.jar
```

必须先停旧进程，否则 Windows 可能提示 JAR 被占用。

### 改了数据库迁移

重启后端即可。Flyway 会在启动时执行尚未应用的 `V*.sql`，不要手动修改数据库顶替迁移。

## 5. 完整检查

后端单测：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
mvn -B test
```

前端单测与构建：

```powershell
npm --prefix web test
npm --prefix web run build
```

## 6. 停止服务

### 停止前端或后端

在对应终端按：

```text
Ctrl+C
```

### 停止 Docker 基础设施

保留数据库数据：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
docker compose stop
```

删除容器但保留数据卷：

```powershell
docker compose down
```

只有明确要清空数据时才执行：

```powershell
docker compose down -v
```

`-v` 会删除数据库卷。不要在需要保留练习记录、账号和简历时使用。

### 检查端口是否仍被占用

```powershell
netstat -ano | Select-String ":8088|:5173"
```

确认 PID 对应的是本项目的 `java.exe` 或 `node.exe` 后，再停止：

```powershell
Get-CimInstance Win32_Process -Filter "ProcessId = <PID>" | Select-Object ProcessId,Name,CommandLine
Stop-Process -Id <PID>
```

## 7. 常见问题

### `8088` 已被占用

通常是上一个后端还在运行。先查端口：

```powershell
netstat -ano | Select-String ":8088"
```

确认 PID 和命令行后再停止，不要直接结束不认识的进程。

### 后端日志出现 Flyway 或数据库连接失败

检查：

```powershell
docker compose ps
docker compose logs --tail 100 postgres
```

确认 `.env` 中数据库端口、用户名、密码与 Docker 配置一致。

### 前端打开但接口报错

确认后端健康：

```powershell
Invoke-RestMethod http://localhost:8088/actuator/health
```

Vite 会把 `/api`、`/login`、`/register` 代理到 `localhost:8088`；后端没启动时前端页面能打开，但登录和业务接口会失败。

### 没有真实 AI Key 想先看界面

在 `.env` 中配置：

```dotenv
PRACTICE_ASR_PROVIDER=stub
PRACTICE_EVALUATION_PROVIDER=stub
PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000
PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000
```

历史遗留的 AI 组件仍会读取 `Ali_API_KEY`，因此该变量要填一个非空占位值，例如：

```dotenv
Ali_API_KEY=local-placeholder
```

### `NoClassDefFoundError: CvRequest`

如果用 `mvn -pl consumer spring-boot:run`，先安装共享模块：

```powershell
mvn -B install -DskipTests
mvn -pl consumer spring-boot:run
```

日常启动更推荐本文的“先打包 JAR，再 `java -jar`”，不容易遇到这个模块依赖顺序问题。

## 8. 一条命令的容器方式（可选）

如果不想分开启动后端和前端：

```powershell
Set-Location C:\workspace\AI-Interview\AI-Interview
docker compose --profile full up -d --build
```

打开 <http://localhost:8080>。这种方式的前端端口是 `8080`，不是开发模式的 `5173`。

停止：

```powershell
docker compose --profile full down
```

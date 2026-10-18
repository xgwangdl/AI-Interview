# Light-Interview 部署说明

## 组成

| 组件 | 用途 | 默认端口 |
| --- | --- | --- |
| PostgreSQL + pgvector | 练习会话、回答、分析任务、向量库 | 5432 |
| Redis | 分析任务状态与后续队列 | 6379 |
| s3mock (adobe/s3mock) | S3 兼容录像存储（开发环境） | 9090 |
| consumer (Spring Boot) | 后端 API | 8088 |
| web (Vue 3 + Vite) | 前端 | 5173 |

## 快速开始

```bash
# 1. 准备环境变量
cp .env.example .env
# 编辑 .env，至少填入 Ali_API_KEY（DashScope）和 AUTH_TOKEN_SECRET
# 后端启动时会自动加载仓库根目录（或 consumer/ 目录）下的 .env，无需手动 export

# 2. 启动基础设施
docker compose up -d

# 3. 启动后端（默认使用本地磁盘存储）
mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar

# 4. 启动前端
cd web && npm install && npm run dev
```

也可以用 `mvn -pl consumer spring-boot:run` 做热重载开发，但**必须先执行一次
`mvn -B install -DskipTests`**：`CvRequest` 等类在 `common` 模块里，只构建 consumer 时会从本地
仓库拿到过期的 `common`，启动时报 `NoClassDefFoundError: com/guangge/Interview/vo/CvRequest`。

后端启动时会通过 Flyway 应用 `db/migration` 下的迁移（`V1__practice_sessions.sql`、`V2__analysis_jobs.sql`），
pgvector 扩展由 Hikari 的 `connection-init-sql` 自动创建。

### 登录用户

登录使用「用户名 + 密码」，数据来自 `candidates` 表（`V3__candidate_login.sql` 创建）。
**用户可以直接在登录页自助注册**（`POST /register`），口令以 BCrypt 哈希存进
`candidates.invitation_code`，注册成功即签发 token。用户名唯一由
`V5__candidate_registration.sql` 的唯一索引保证（2–80 字符，支持中文/字母/数字/`_ . -`，
密码至少 6 位）。

需要批量造账号（或做演示数据）时也可以用 SQL，写明文口令即可：

```bash
docker compose exec -T postgres psql -U postgres -d interview \
  -c "INSERT INTO candidates (candidate_name, invitation_code, status) VALUES ('demo','123456',1);"
```

之后用 `demo / 123456` 登录 <http://localhost:5173/login> 即可；首次登录成功后该行会被
自动改写成 BCrypt 哈希，无需人工迁移。请勿在生产环境使用示例口令。

> 注册接口目前没有验证码或频率限制，公网部署时建议在网关层加限流。

启动后：

- 前端 <http://localhost:5173>（Vite 已把 `/api`、`/login`、`/register`、`/auth` 代理到 `localhost:8088`）
- S3 兼容端点 <http://localhost:9090>（开发凭证任意）
- 健康检查 `GET /api/v1/practice-sessions`（需 Bearer Token）

## 存储配置

录像通过 `MediaStorage` 接口存取，由 `practice.media.provider` 选择实现：

| 取值 | 实现 | 说明 |
| --- | --- | --- |
| `local`（默认） | `LocalMediaStorage` | 写入 `MEDIA_STORAGE_DIR`，仅适合单机 |
| `s3` | `S3MediaStorage` | AWS S3 或任何 S3 兼容服务（MinIO、s3mock 等） |

切换到 S3 兼容存储：

```bash
MEDIA_PROVIDER=s3
MEDIA_S3_ENDPOINT=http://localhost:9090
MEDIA_S3_BUCKET=light-interview-media
MEDIA_S3_ACCESS_KEY=test
MEDIA_S3_SECRET_KEY=test
MEDIA_S3_PATH_STYLE=true
```

开发环境用 `adobe/s3mock` 承载该 bucket（`COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS`）。
**注意**：MinIO 官方镜像已从 Docker Hub 下架，直接 `docker pull minio/minio` 会返回 access denied；
要用真实 MinIO 需换用自建镜像源或本地二进制。连接真实 S3 时去掉 `MEDIA_S3_ENDPOINT` 与静态密钥，
改用实例角色 / IAM 默认凭证链。

排查提示：**对象不存在**会被当作「这道题没有录像」（返回空），而 **bucket 名配错**会直接抛错。
这样配置问题不会被误报成录像丢失。

## 关键环境变量

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/interview` | 数据库地址 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `postgres` / `postgres` | 数据库凭证 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `MEDIA_PROVIDER` | `local` | 录像存储实现 |
| `MEDIA_RETENTION_DAYS` | `7` | 录像与分析结果保留天数 |
| `MEDIA_SIGNED_URL_TTL_SECONDS` | `300` | 签名播放地址有效期 |
| `MEDIA_MAX_FILE_SIZE` | `250MB` | 单个上传文件上限（也是每题录像的上限） |
| `MEDIA_MAX_REQUEST_SIZE` | `250MB` | 单个 multipart 请求体上限 |
| `PRACTICE_FLYWAY_ENABLED` | `true` | 启动时是否执行 Flyway 迁移 |
| `AUTH_TOKEN_SECRET` | 开发占位值 | JWT 与媒体签名密钥，**生产必须替换** |
| `AUTH_TOKEN_TTL_HOURS` | `12` | 令牌有效期（小时）。一轮练习约 20 分钟，别设太短 |
| `AUTH_LOGIN_MAX_ATTEMPTS` | `10` | 同一用户名允许的连续登录失败次数 |
| `AUTH_LOGIN_LOCK_WINDOW_MINUTES` | `15` | 超过阈值后的锁定与计数窗口 |
| `Ali_API_KEY` | 空 | DashScope：对话、ASR、TTS、图像 |
| `PRACTICE_ANALYSIS_MAX_ATTEMPTS` | `3` | 分析任务最大重试次数 |
| `PRACTICE_ANALYSIS_PROVIDER_TIMEOUT_MS` | `120000` | 单次转写/评分调用超时，避免挂起拖垮队列 |
| `PRACTICE_ANALYSIS_POLL_INTERVAL_MS` | `15000` | 分析任务轮询间隔 |
| `PRACTICE_ANALYSIS_RETRY_BACKOFF_MS` | `5000` | 失败重试的指数退避基数（第 n 次等 基数 × 2^(n-1)） |
| `PRACTICE_ANALYSIS_RETRY_BACKOFF_MAX_MS` | `300000` | 退避上限 |
| `PRACTICE_ASR_PROVIDER` | `dashscope` | 转写供应商：`dashscope` / `stub` |
| `PRACTICE_EVALUATION_PROVIDER` | `spring-ai` | 评分供应商：`spring-ai` / `stub` |
| `PRACTICE_AVATAR_TTS_ENABLED` | `true` | 关闭后退化为「头像 + 文字」 |
| `FFMPEG_PATH` | `ffmpeg`（从 PATH 查找） | ASR 前抽取音轨；自定义安装位置时填绝对路径 |

### 可观测性与死信队列

- `GET /actuator/prometheus` 暴露分析队列指标：`analysis_queue_length`、
  `analysis_processing_latency_ms`、`analysis_failure_rate`、`analysis_retry_count_total`、
  `analysis_dlq_size`。**与应用同端口且未鉴权**，生产部署请用管理端口或网关限制来源。
- 重试达到 `PRACTICE_ANALYSIS_MAX_ATTEMPTS` 的任务会写入 `analysis_jobs_dlq`；
  `GET /api/v1/analysis/dlq` 查看、`POST /api/v1/analysis/dlq/{jobId}/requeue` 重新入队。

### 代码沙箱（笔试模块）

笔试模块会在**本机 Docker** 里执行用户提交的代码，因此默认**关闭**：

```dotenv
CODE_RUNNER_ENABLED=true          # 默认 false；打开前确认机器可控、不对公网暴露
CODE_RUNNER_MAX_CONCURRENCY=2     # 同时最多跑几个容器
CODE_RUNNER_COMPILE_TIMEOUT_SECONDS=10
CODE_RUNNER_RUN_TIMEOUT_SECONDS=5
CODE_RUNNER_MEMORY_MB=256
```

执行时的隔离参数（禁网、内存/CPU/进程数上限、只读根文件系统、单文件大小上限、
按容器名强杀超时任务）写在 `CodeRunnerService` 的类注释里，修改前请先读那段。

各语言的镜像与体积：

| 语言 | 镜像 | 体积 | 状态 |
| --- | --- | --- | --- |
| Java | `eclipse-temurin:21-jdk-alpine` | 364MB | 已拉取 |
| Python | `python:3.12-slim` | 119MB | 已拉取（**SQL 题也复用它**，内置 sqlite3） |
| JavaScript | `node:20-alpine` | 136MB | 已拉取 |
| C++ | `gcc:13` | 约 1.4GB | 首次执行该语言时自动拉取 |
| Go | `golang:1.22-alpine` | 约 250MB | 首次执行该语言时自动拉取 |

> 公网部署时不要把代码执行放在应用同一台机器上：这套实现面向「自己机器上跑自己的代码」，
> 真要多租户使用，应换成独立的执行节点（或接 Judge0 之类的判题服务）并加更严格的隔离。

### 关于 Redis

compose 里带了 Redis，`spring-boot-starter-data-redis` 也在依赖里，但**当前实现并没有用到它**：

- 分析任务的队列与状态存在 PostgreSQL 的 `analysis_jobs` 表；
- 多实例安全靠条件更新认领（`pending → processing`，`UPDATE ... WHERE status='pending'`），
  拿不到更新的实例直接跳过，不会重复转写与评分；
- 进程中断留下的 `processing` 任务超过 10 分钟会被回收重排，并计入 `attempts`。

PLAN 里写的是「Redis 用于分析任务队列和状态通知」。如果确实需要 Redis 参与队列或做状态推送，
可以在此基础上替换实现；否则建议把 Redis 从 compose 与依赖中移除，避免一个空转的容器。

## 外部依赖

- **ffmpeg**：回答以视频保存，转写前需要抽取音轨，因此宿主机或容器内必须有 ffmpeg。
  可用 `FFMPEG_PATH` 指定路径，`FFMPEG_TIMEOUT_SECONDS` 调整超时（默认 60 秒）。
- **DashScope**：对话、语音识别（Paraformer）、语音合成（Sambert）。
  配置位于 `spring.ai.dashscope.*`，需要一条有效的 `Ali_API_KEY`。

## 数据保留与删除

- 新建练习时会写入 `retention_until = now + MEDIA_RETENTION_DAYS`。
- `PracticeSessionService.deleteExpiredSessions()` 每天 03:17 清理过期会话，
  同时删除对象存储中的录像与分析任务。
- 用户主动删除练习会级联删除回答、分析任务与媒体文件。

## 测试

默认构建是自包含的，需要外部服务的用例会自动跳过：

```bash
mvn -B clean package
```

启动基础设施后可以打开集成层：

```bash
docker compose up -d postgres s3mock
mvn -B test -Ddb.integration=true -Ds3.integration=true
```

- `-Ddb.integration=true`：`PracticePersistenceIntegrationTest` 对真实 PostgreSQL 应用 Flyway
  V1/V2，并验证 `uq_practice_answers_session_question` 唯一约束、`analysis_jobs` 外键与仓储查询。
- `-Ds3.integration=true`：`S3MediaStorageIntegrationTest` 对 `MEDIA_S3_ENDPOINT` 指向的 S3 兼容
  服务做一次 store → HEAD → GET → DELETE 往返，并确认缺失对象返回空而不是抛错。
- `PracticeFlowAcceptanceTest` 属于默认构建：用内存仓储 + 真实本地磁盘存储跑完整五题流程，
  覆盖录制、入队、转写、评分、报告、签名播放地址与删除后不可访问。

## 无凭证本地运行

没有阿里云 Key 时，把转写与评分切到本地兜底实现，整条「录制 → 转写 → 评分 → 报告」链路
依然可以完整跑通（分数会标注来源为 `stub-local`，不会伪装成真实模型结果）：

```bash
PRACTICE_ASR_PROVIDER=stub
PRACTICE_EVALUATION_PROVIDER=stub
PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000
PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000
```

配合 `pwsh scripts/smoke-test.ps1 -Name demo -Code <邀请码> -ExpectAnalysisReady`
可以验证报告确实走到 `ready`。这套开关只是开发/演示用途，生产环境请保持 `dashscope` + `spring-ai`。

## 生产部署

### 前端

```bash
npm --prefix web ci
npm --prefix web run build      # 产物在 web/dist
```

把 `web/dist` 交给任意静态服务（nginx / CDN / 对象存储静态托管）。两个容易踩的点：

1. **前端用的是 HTML5 history 路由**（`createWebHistory`）。静态服务必须把未命中的路径回退到
   `index.html`，否则直接访问或刷新 `/login`、`/practice/setup` 会得到 404。
   这与本地开发时 Vite 把 `/login` 整段代理给后端、结果拿到 Whitelabel 405 是同一类问题。
2. `VITE_API_BASE_URL` **在构建期写死**。只在前后端不同源时才需要：

   ```bash
   VITE_API_BASE_URL=https://api.example.com npm --prefix web run build
   ```

   同源部署（例如 nginx 把 `/api` 反代到后端）就保持为空。

### 后端

- 把 `AUTH_TOKEN_SECRET` 换成强随机值 —— 它同时是 JWT 与媒体签名地址的密钥。
- 前后端不同源时设置 `APP_CORS_ALLOWED_ORIGINS=https://你的前端域名`；同源部署可以不设。
- 对象存储用 `MEDIA_PROVIDER=s3`，凭证走实例角色 / IAM，不要用静态密钥。
- 保持 `PRACTICE_ASR_PROVIDER=dashscope` 与 `PRACTICE_EVALUATION_PROVIDER=spring-ai`（`stub` 仅限开发）。
- 录像保留天数由 `MEDIA_RETENTION_DAYS` 决定，清理任务每天 03:17 执行。

## 遗留接口

旧版前端时代的 REST 路由仍然注册在应用里，路径与现状如下：

| 路径前缀 | 状态 |
| --- | --- |
| `/frontend/candidates` | 可用（依赖 `candidates` 表） |
| `/frontend/interView`、`/frontend/findInterView` | 500，依赖未被 Flyway 创建的 `resumes` / `resume_vectors` |
| `/fileupload/*`、`/interview/*`、`/resume/*`、`/candidate/*`、`/image/*`、`/talk`、`/api/stream-audio` | 同上，属于旧功能 |

两点需要注意：

1. **它们此前完全不做鉴权**：`GET /frontend/candidates` 未登录即可列出候选人。
   现在统一要求与 `/api/v1` 相同的 Bearer Token（`LegacyApiAuthInterceptor`），
   未带或无效凭证返回 401，功能本身不被删除。
2. **它们依赖的表不在 Flyway 里**（`resumes` / `resume_vectors` / `knowledge` 只存在于
   `resources/ddl/` 手动脚本）。这些属于 PLAN 中待移除的旧功能，尚未迁移。
   建议二选一：要么补迁移把这些功能纳入管理，要么按 PLAN 第 6 步删除。

## 验证状态

已实测通过：

- **应用启动**：真实 PostgreSQL 16.14 + Redis 下启动成功，Flyway 校验 2 个迁移，pgvector 初始化 `vector_store`。
- **HTTP 全链路**：`scripts/smoke-test.ps1` 覆盖创建练习 → 上传录像 → 结束 → 报告 →
  签名地址 → 下载 → 删除 → 404，20 项断言全过（加 `-ExpectAnalysisReady` 为 24 项）。
- **认证与数据隔离**：同一条冒烟脚本还验证未认证请求返回 401、另一个用户读取他人练习返回 404。
- **数字人降级**：语音合成失败时返回 503 而不是 500，客户端据此隐藏播报控件、保留字幕。
- **保留期清理**：`deleteExpiredSessions()` 在真实 PostgreSQL 上验证会删除过期会话、回答、
  分析任务（外键级联）与磁盘上的录像文件。
- **前端清理**：浏览器实测过期练习在打开应用时被 `pruneExpired()` 移除，并同步删除服务端数据与录像。
- **分析失败可见**：跑通一次真实（故意失败）的分析任务，报告返回 `status=analysis_failed`
  与脱敏后的原因，前端在排队期间轮询、完成后停止。
- **队列持久化**：在后端停机前留下的 `pending` 任务，重启后由新实例继续处理并完成
  （实测报告从 `awaiting_analysis` 变为 `ready`）。
- **单题入队**：`POST /api/v1/answers/{id}/complete` 让每题上传后立刻开始分析，
  不必等整场结束；未上传录像返回 409，重复调用不会重复建队列。
- **失败重试**：`POST /api/v1/practice-sessions/{id}/analysis/retry` 把 `failed` 任务重置为
  `pending`（attempts 归零）；实测报告从 `analysis_failed` 经 `pending` 恢复为 `ready`。
- **供应商超时**：转写与评分调用有超时上限（默认 120 秒），超时按普通失败进入重试；
  单测用一个挂起 5 秒的供应商 + 300ms 超时验证会快速失败而不是永久阻塞调度线程。
- **登录限流**：同一用户名连续失败 10 次后返回 429（实测 `403 ×10 → 429 ×2`），
  其他用户名不受牵连；成功登录会清零计数。
- **报告审计字段**：模型名/版本、Prompt 版本、分析时间、结构化评分、原始模型响应与时间轴证据
  一并落库，验收用例断言原始响应确实写入 `evaluation_json`。
- **报告页时间轴跳转**：浏览器实测点击证据后视频 `currentTime` 跳到证据起点并继续播放；
  直接打开/硬刷新报告链接也能正常加载。
- **对象存储**：`MEDIA_PROVIDER=s3` 下上传、签名下载、删除同样全过；
  媒体端点返回 **206 + `Content-Range`**，视频拖动进度条可用。
- **数据库迁移**：`mvn test -Ddb.integration=true` 对真实库验证唯一约束、外键与仓储查询。
- **日志脱敏**：模型调用日志只记录消息条数与字符数，不再输出 prompt/response 正文；
  `LogSanitizer` 覆盖 Bearer 令牌、密钥、邮箱、手机号，分析任务失败原因落库前同样脱敏。

## 已知限制

- ~~**真实模型调用未验证**~~：2026-10-10 用有效 `Ali_API_KEY` 逐条验证通过 —— AI 出题、
  Paraformer 转写、Sambert 合成、Spring AI 评分四条链路都真实返回过，详见
  [STATUS.md](STATUS.md) 的「真实供应商验证结果」。
- **真人录像 + 真实模型同场**：录像链路与真实模型链路都已各自验证，但两者组合在同一场会话里
  还没跑过（真人录像当时配的是兜底供应商），需要摄像头授权。
- **数字人口型**：播放面试官语音时用 Web Audio 的时域能量驱动口型，接不上分析器就退回状态动画
  （只做了构建验证，未在真实音频下实测波形）。
- **MinIO 官方镜像已从 Docker Hub 下架**，开发环境默认使用 `adobe/s3mock`；
  接真实 MinIO 需自建镜像源。

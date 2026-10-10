# Light-Interview

求职者的**视频模拟面试训练与复盘**工具：选择岗位、逐题录制回答，后台完成转写与结构化评分，
最后给出带视频时间轴证据的逐题报告。

[English](docs/README.md) · [使用手册](docs/USER-GUIDE.md) · [项目架构](docs/ARCHITECTURE.md) · [部署说明](docs/DEPLOYMENT.md) · [实施状态](docs/STATUS.md) · Apache License 2.0

## 功能

- **练习配置**：目标岗位、公司、JD、简历文件名，面试类型 / 面试官风格 / 难度 / 题量与时长。
- **设备检测**：摄像头、麦克风授权与实时预览，环境音量检查通过后才允许进入面试。
- **AI 出题**：解析上传的 PDF / Word 简历，结合目标岗位、公司、JD 生成逐题提问；模型不可用时
  静默回退到内置题库，不会因为出题失败而卡住练习创建。简历正文只用于出题，不落库。
- **模拟面试**：数字人面试官播报问题（支持口型动画与纯文字降级），每题独立录制、可暂停、可重录。
- **AI 复盘**：回答转写 → 结构化评分（相关性 / 完整性 / 专业性 / STAR / 逻辑 / 语速 / 停顿 / 口头禅）→
  逐题报告，评分结果带可跳转的视频时间点证据。
- **保留与删除**：录像默认保留 7 天，可随时手动删除，删除会同时清理媒体、转写与评分数据。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 3.4 · Spring AI 1.1.8 · Spring AI Alibaba 1.1.2.4（DashScope） |
| 前端 | Vue 3.5 · Vite 6 · Element Plus · Pinia |
| 存储 | PostgreSQL 16 + pgvector · Redis 7 · S3 兼容对象存储（本地磁盘 / MinIO / AWS S3） |
| 媒体 | 浏览器 `MediaRecorder` 录制 · ffmpeg 抽取音轨 · Paraformer 转写 · Sambert 合成 |

## 快速开始

```bash
# 1. 环境变量
cp .env.example .env          # 至少填 Ali_API_KEY 与 AUTH_TOKEN_SECRET
# 后端启动时自动加载仓库根目录的 .env，不需要手动 export

# 2. 基础设施：Postgres + Redis + S3 兼容存储
docker compose up -d

# 3. 构建并启动后端（首次启动会执行 Flyway 迁移）
mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar

# 4. 创建登录账号（迁移只建表不写数据，必须先启动过一次后端）
docker compose exec -T postgres psql -U postgres -d interview \
  -c "INSERT INTO candidates (candidate_name, invitation_code, status) VALUES ('demo','123456',1);"

# 5. 前端
cd web && npm install && npm run dev
```

打开 <http://localhost:5173>，用 `demo / 123456` 登录。
上手流程见 [docs/USER-GUIDE.md](docs/USER-GUIDE.md)，代码结构与扩展点见
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，完整变量与对象存储切换见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 测试

```bash
mvn -B clean package                                    # 单元测试，外部依赖自动跳过
mvn -B test -Ddb.integration=true -Ds3.integration=true # 对真实 Postgres / S3 的集成测试
npm --prefix web test                                   # 前端单元测试（API 客户端 / 路由守卫 / store / 媒体捕获 / 题库）
pwsh scripts/smoke-test.ps1 -Name demo -Code 123456     # 端到端 HTTP 冒烟
```

`scripts/smoke-test.ps1` 覆盖：登录 → 创建练习 → 上传录像 → 结束 → 报告 →
签名播放地址 → Range 请求 → 删除 → 删除后不可访问，并校验未认证请求被拒绝、他人练习不可读取。
加上 `-ExpectAnalysisReady` 时，还会等待并校验分析任务真的跑完（配合下面的本地兜底供应商）。

CI（`.github/workflows/build.yml`）会重复跑上面三条：后端干净构建、前端干净安装后测试并构建。

没有云端凭证也能跑通整条链路：

```bash
PRACTICE_ASR_PROVIDER=stub PRACTICE_EVALUATION_PROVIDER=stub \
  PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000 PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000 \
  java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
```

## 目录结构

```
consumer/    后端应用（practice / analysis / avatar / storage / security 等模块 + Flyway 迁移）
common/      共享工具：统一响应、业务异常、JWT、CORS 配置
web/         Vue 3 单页应用
scripts/     端到端冒烟脚本
docs/        部署说明与英文 README
docker-compose.yml
```

## 接口

```text
POST   /login                                          登录（姓名 + 邀请码）
POST   /api/v1/practice-sessions                        创建练习
GET    /api/v1/practice-sessions                        列出我的练习
POST   /api/v1/practice-sessions/question-plan          按岗位与 JD 生成题目
POST   /api/v1/resume/extract                           解析简历正文（PDF / Word）
POST   /api/v1/practice-sessions/{id}/answers           保存单题回答
POST   /api/v1/answers/{id}/media                       上传录像（multipart）
POST   /api/v1/answers/{id}/complete                    单题完成，立即入队分析
POST   /api/v1/practice-sessions/{id}/complete          结束练习并入队分析
GET    /api/v1/practice-sessions/{id}/report            逐题报告
POST   /api/v1/practice-sessions/{id}/analysis/retry    分析失败后重新排队
GET    /api/v1/media/{id}/signed-url                    签发限时播放地址
GET    /api/v1/media/{id}?expires=&signature=           按签名播放录像
GET    /api/v1/practice-sessions/{id}/questions/{n}/narration  面试官播报文本与音频
DELETE /api/v1/practice-sessions/{id}                   删除练习及其全部媒体
```

## 验证状态

已实测：

- 应用在真实 PostgreSQL 16.14 + Redis 下启动，Flyway 迁移全部应用，pgvector 初始化。
- 浏览器完整跑通「登录 → 创建练习 → 设备检测 → 五题录制 → 报告」。
- 本地磁盘与 S3 两种媒体存储模式下冒烟测试全部通过，媒体端点支持 HTTP Range（206）。
- 未认证请求返回 401，跨用户读取练习返回 404，删除后媒体与记录均不可访问。
- 数字人播报在语音合成不可用时干净降级：接口返回 503，客户端隐藏播报控件、只保留面试官字幕。
- 简历解析：上传真实 `.docx` 能正确抽取正文，不支持的类型返回 415，超限返回 413。
- 保留期清理：超过保留期的会话会连同录像文件、回答与分析任务一起清除，并在真实 PostgreSQL 上
  验证了 `analysis_jobs` 的外键级联。
- 前端也会在打开应用时清理过期练习：浏览器实测把一条会话的保留期改到过去后刷新，
  界面上它消失，服务端的会话、回答、分析任务与磁盘录像一并被删除。
- 分析失败会传达给用户：报告返回 `analysis_failed` 与脱敏后的失败原因（不再无限「等待分析服务」），
  排队期间前端每 10 秒自动轮询，出结果后自动停止。已在真实调度链路上验证。
- 报告的审计字段齐全：模型名与版本、Prompt 版本、分析时间、结构化评分、**原始模型响应**
  以及可跳转视频时间点的证据，都写入每题的 `evaluation_json`，由验收用例断言。
- 遗留接口不再允许匿名访问：`/frontend`、`/interview`、`/resume` 等旧路由统一要求 Bearer Token
  （此前 `GET /frontend/candidates` 未登录即可列出候选人）。
- 完整分析闭环已跑通：本地兜底供应商下，报告进入 `ready`，每题展示转写、五个维度评分、
  语速/停顿/口头禅、改进建议、优秀回答示例与可跳转视频时间轴的证据；
  落库的溯源写明 `stub-local`，并保留了原始模型响应。
- 分析任务的认领是原子的：条件更新 `pending → processing`，多实例不会重复转写与评分；
  进程中断留下的任务会被回收重排并计入重试次数。
- 分析队列是持久化的：排队中的任务在**后端重启后依然被处理**（实测重启前后 report 从
  `awaiting_analysis` 变为 `ready`，任务由新实例完成）。
- 单题即可开始分析：上传完一题调用 `POST /api/v1/answers/{id}/complete`，该题立刻入队；
  实测会话仍是 `in_progress` 时首题已产出评分。未上传录像时返回 409。
- 分析失败可以恢复：模拟一次失败任务后报告返回 `analysis_failed`，调用重试接口后任务
  重新排队，随后报告恢复为 `ready`（实测完整往返）。
- 转写与评分调用带超时（`practice.analysis.provider-timeout-ms`，默认 120 秒）：
  供应商挂起时会快速失败并进入重试，而不是永久占住单线程调度器。
- 登录带限流：同一用户名连续失败超过阈值后返回 429，避免短邀请码被在线爆破；
  实测 `403 ×10 → 429`，且其他账号不受影响。
- 报告页在真实浏览器里验证过：视频可播放，点击时间轴证据会把视频 `currentTime` 跳到证据起点
  并继续播放；硬刷新或直接打开报告链接也能正常加载（不再停在「尚未生成」）。

尚未验证：

- **真人录像 + 真实模型同场**：录像链路（浏览器实拍 5 题）与真实模型链路（出题 / Paraformer
  转写 / Sambert 合成 / Spring AI 评分）都已各自验证，但还没在同一场会话里跑过组合。
- **摄像头权限拒绝与设备不存在**、**录制中断与断网恢复** 两项验收需要真实相机。
- 口型动画的振幅驱动逻辑只做了构建验证，未在真实音频下实测。

真实供应商已于 2026-10-10 用有效 `Ali_API_KEY` 逐条跑通，详见
[docs/STATUS.md](docs/STATUS.md) 的「真实供应商验证结果」。

## 致谢与许可

基于 [guangge](https://github.com/xgwangdl) 的 AI-Interview 重构而来，采用 Apache License 2.0。

感谢 Spring Boot、Spring AI、Spring AI Alibaba、Vue、Element Plus、PostgreSQL / pgvector 等开源项目。

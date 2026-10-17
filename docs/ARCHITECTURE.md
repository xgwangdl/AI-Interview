# Light-Interview 项目架构

面向**开发与维护者**：代码怎么组织、一次练习在系统里怎么流动、改哪里、扩展点在哪。

- 使用与部署：见 [USER-GUIDE.md](USER-GUIDE.md) / [DEPLOYMENT.md](DEPLOYMENT.md)
- 完成度与验证证据：见 [STATUS.md](STATUS.md)

---

## 1. 总览

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 3.4 · Spring AI 1.1.8 · Spring AI Alibaba 1.1.2.4（DashScope） |
| 前端 | Vue 3.5 · Vite 6 · Element Plus · Pinia · Vue Router |
| 数据库 | PostgreSQL 16 + pgvector（Flyway 迁移） |
| 缓存/队列 | Redis（当前仅作为依赖存在，见「已知限制」） |
| 对象存储 | 本地磁盘 / S3 兼容（开发用 `adobe/s3mock`，生产可换 MinIO / AWS S3） |
| 媒体处理 | 浏览器 `MediaRecorder` 录制 · ffmpeg 抽取音轨 · Paraformer 转写 · Sambert 合成 |

运行时组件与端口：

| 组件 | 端口 | 说明 |
| --- | --- | --- |
| consumer（Spring Boot） | 8088 | 后端 API |
| web（Vite） | 5173 | 前端开发服务器 |
| PostgreSQL | 5432 | 业务表 + 向量表 |
| Redis | 6379 | compose 中已起，代码未使用 |
| s3mock | 9090 | S3 兼容的开发用对象存储 |

---

## 2. 仓库结构

```
AI-Interview/
├── pom.xml                     # 聚合 common + consumer 的 Maven 根工程
├── docker-compose.yml          # postgres / redis / s3mock
├── .env.example                # 全部环境变量样例（复制成 .env 使用）
├── common/                     # 共享模块：通用返回体、异常、鉴权工具、共享 record/VO
├── consumer/                   # 后端主体（Spring Boot 应用）
│   ├── src/main/java/com/guangge/Interview/
│   │   ├── ConsumerApplication.java
│   │   ├── practice/           # 【新】练习面试体系（当前主线）
│   │   ├── analysis/           # 【新】转写 + 评分流水线
│   │   ├── avatar/             # 【新】数字人播报与 TTS
│   │   ├── security/           # 【新】登录限流
│   │   ├── assistant/ audio/ controller/ data/ repository/
│   │   │   services/ tools/ images/ mail/ advisor/   # 【遗留】旧面试功能
│   │   └── util/               # LogSanitizer / JsonText 等工具
│   └── src/main/resources/
│       ├── application.properties / application.yml
│       ├── db/migration/       # Flyway：V1 练习表、V2 分析任务、V3 登录账号
│       └── prompt/             # 出题与评分的系统提示词
├── web/                        # Vue 3 前端
│   └── src/{views,stores,services,data}
├── scripts/smoke-test.ps1      # 端到端冒烟脚本
└── docs/                       # 本文档所在目录
```

---

## 3. 模块划分

### 3.1 `common`

与业务无关的共享代码，被 `consumer` 依赖：

| 包 | 内容 |
| --- | --- |
| `auth` | `Sign`（JWT 签发/校验）、`Sessions`、`AuthConstant` |
| `util` | `CommonResult` 统一返回体、`ResultCode` / `IErrorCode`、`JacksonMapperUtils` |
| `exception` | `RestException` 与 `RestExceptionAdvice` 全局异常处理 |
| `config` | `WebConfig`、`RedisConfig` |
| `record` / `vo` | 跨模块的请求响应模型（`CvRequest`、`CandidateRecord`、`InterViewRequest` 等） |

### 3.2 `consumer`

后端主体，**内部分两代代码**：

- **新体系（`practice` + `analysis` + `avatar` + `security`）**：练习面试主线，有完整测试与验收；
- **遗留体系（其余包）**：项目早期的「简历改写 / 编程题 / 候选人管理 / 语音对话」功能。
  它们依赖未被 Flyway 管理的表，部分接口调用即 500；现在统一要求 Bearer Token（见 3.3），
  但保留还是删除属于待决策项（见 STATUS.md）。

### 3.3 遗留接口的鉴权

`practice/security/LegacyApiAuthInterceptor` 通过 `LegacyApiAuthConfiguration` 注册，
对 `/frontend`、`/interview`、`/resume`、`/fileupload`、`/candidate`、`/image`、`/talk`、
`/api/stream-audio` 等路径统一要求与 `/api/v1` 相同的 Bearer Token。

它只拦匿名访问，不改变这些功能的实现——目的是把「谁能调」这件事先对齐。

---

## 4. 一次练习的数据流

```
前端 SetupView
   │  POST /api/v1/practice-sessions/question-plan      ← 可选：AI 出题
   │  POST /api/v1/resume/extract                       ← 可选：简历抽文本
   ▼
POST /api/v1/practice-sessions           创建会话，题目写入 questions_json
   ▼
前端 DeviceCheckView → SessionView
   │  POST /practice-sessions/{id}/answers             逐题创建作答记录
   │  POST /answers/{answerId}/media                   上传录像（video/*）
   ▼
POST /practice-sessions/{id}/complete    校验：至少 1 题、且上传都已结束
   ▼
AnalysisJobService.enqueue(sessionId)    为每个有录像的作答插入 analysis_jobs（pending）
   ▼
@Scheduled processPending()              领取任务：UPDATE ... WHERE status='pending'（原子领取）
   ▼
runProviders(job)
   ├─ MediaStorage.load(mediaKey)        取回录像
   ├─ AudioConverter（ffmpeg）           抽音轨
   ├─ AsrProvider.transcribe(...)        → TranscriptResult
   ├─ EvaluationProvider.evaluate(...)   → 原始 JSON
   └─ EvaluationResultValidator         结构校验（不合格即失败，不落库脏数据）
   ▼
状态写回 analysis_jobs（completed / failed + 脱敏错误）
   ▼
GET /practice-sessions/{id}/report       PracticeReportService 组装报告视图
```

任务状态机：

```
pending ──claim──▶ processing ──成功──▶ completed
                      │
                      └──失败──▶ failed ──retry──▶ pending
```

- 领取是**数据库层的原子更新**，多实例并发也不会重复处理同一任务；
- 失败的 `processing` 超过 10 分钟会被 `reclaimStuckJobs()` 回收（进程崩溃兜底）；
- 每次执行有 `providerTimeoutMs` 超时上限，失败重试上限 `MAX_ATTEMPTS`。

---

## 5. 后端包逐个说明

### 5.1 `analysis` —— 转写与评分流水线

| 类 | 职责 |
| --- | --- |
| `AnalysisJobEntity` / `AnalysisJobRepository` | 任务表实体与仓储 |
| `AnalysisJobService` | 入队、原子领取、执行、重试、卡死回收、超时控制 |
| `AsrProvider` | 转写接口；`DashScopeAsrProvider` 走 Paraformer，`StubAsrProvider` 为本地兜底 |
| `EvaluationProvider` | 评分接口；`SpringAiEvaluationProvider` 走大模型，`StubEvaluationProvider` 为兜底 |
| `EvaluationResultValidator` | 校验固定 JSON 结构（分数 0–100、证据时间区间、必填字段），校验不过抛 `EvaluationFormatException` |
| `EvaluationOutcome` / `EvaluationProvenance` | 评分结果 + 来源（供应商、模型、耗时） |
| `EvaluationRequest` / `EvaluationResult` / `EvaluationReport` | 请求与响应模型 |
| `TranscriptResult` | 转写文本 + 片段 |
| `AnalysisQueueMetrics` | Micrometer 指标：队列长度、处理耗时、失败率、重试与 DLQ 计数 |
| `AnalysisDlqEntity` / `AnalysisDlqRepository` / `AnalysisDlqView` | 死信队列的持久化与视图 |
| `AnalysisDlqController` | `GET /api/v1/analysis/dlq`（列出）与 `POST .../dlq/{jobId}/requeue`（重新入队） |
| `AnalysisRequeueResult` | 重新入队后的明确返回体 |

**设计要点**：供应商以接口注入，切换只改配置；评分强制走 JSON 结构校验，自由文本直接被拒绝，
避免模型输出漂移进数据库。

失败处理（#10）：任务失败后按 `retry-backoff-ms × 2^(n-1)` 退避重排（上限 `retry-backoff-max-ms`），
调度器只领取 `next_attempt_at` 到期的任务；重试到 `max-attempts` 后写入 `analysis_jobs_dlq`
并把原任务置为 `failed`（报告据此显示 `analysis_failed`）。

### 5.2 `avatar` —— 数字人播报

| 类 | 职责 |
| --- | --- |
| `AvatarController` | `GET /api/v1/practice-sessions/{id}/questions/{i}/narration`（拿文本 + 音频地址）与 `.../narration/audio`（拿音频字节） |
| `AvatarProvider` / `LocalAvatarProvider` | 组装播报内容与状态（`AvatarState`） |
| `TtsProvider` / `DashScopeTtsProvider` | Sambert 语音合成 |
| `SpeechAudio` | 音频字节 + MIME 类型 |

播报有两条链路，复用同一套 Provider 与降级逻辑，靠 `AvatarNarrationRequest.audioPath` 区分：

| 链路 | 文本来源 | 接口 |
| --- | --- | --- |
| 面试间念题目 | 会话里的题目原文 | `GET /{id}/questions/{n}/narration[/audio]` |
| 报告页念示范回答 | **评分时已存下的 `sampleAnswer`**（不额外调用模型） | `GET /{id}/questions/{n}/sample-answer[/audio]` |

两个细节：`audioPath` 必须由调用方给出 —— 早先这个地址在 Provider 里写死成 narration 路径，
复用时会把示范回答指向"念题目"的音频；另外单次合成有长度上限，
超长文本按**句子边界**截断（硬切会断在半句），并在响应里用 `truncated` 告诉前端"只念了前半段"。
报告里的 `questions` **只包含有作答的题**，所以按题号查示范回答要按 `questionIndex` 字段找，不能用数组下标。

合成失败会转成 **503** 而不是 500，客户端据此隐藏播报控件、保留字幕。

### 5.3 `practice` —— 练习面试主线

| 子包 | 关键类 | 职责 |
| --- | --- | --- |
| `controller` | `PracticeSessionController` | `/api/v1` 下的会话、作答、上传、报告、删除等接口 |
| `service` | `PracticeSessionService` | 会话生命周期、归属校验、录像落库、保留期清理 |
| | `PracticeReportService` | 把会话 + 作答 + 评分组装成报告视图，并推导 `ready` / `awaiting_analysis` / `analysis_failed` |
| | `PracticeQuestionCatalog` | 读写会话上的题目 JSON |
| `entity` / `repository` | `PracticeSessionEntity`、`PracticeAnswerEntity` 及其仓储 | 持久化 |
| `dto` | `PracticeSessionRequest`、`PracticeAnswerRequest`、`PracticeReportView` 等 | API 模型 |
| `plan` | `QuestionPlanController` / `QuestionPlanService` / `SpringAiQuestionPlanProvider` / `QuestionPlanValidator` | AI 出题，输出同样要过结构校验 |
| `followup` | `PracticeFollowUpService` / `SpringAiPracticeFollowUpProvider` / `PracticeFollowUpValidator` | 面试官追问：同步转写 + 一次模型判断，追问插在被追问的那道题后面（同一道题最多 2 轮，一场最多 3 次） |
| `transcript` | `TranscriptService` | 人工修正转写、保留原始文本与审计、单题重评分；每题最多修正 3 次 |
| `rewrite` | `AnswerRewriteService` / `SpringAiAnswerRewriteProvider` / `AnswerRewriteValidator` | 基于原话改写回答；拦截新增数字与语言翻译；改写历史与用户编辑 |
| `archive` | `PracticeArchiveService` / `PracticeScoreArchiveService` / `PracticeArchiveController` | 训练档案：跨场次的趋势与短板；成绩与录像分开长期保留（V9） |
| `mistakes` | `MistakeBookService` / `MistakeBookView` | 错题本：最好一次没过 75 分的题 + 一键单题重练（V10） |
| `resume` | `ResumeController` / `ResumeExtractionService` | 上传 PDF/Word，用 Apache Tika 抽文本 |
| `security` | `OwnerResolver` | 从 Bearer Token 解析出当前用户，作为数据归属 |
| | `LegacyApiAuthInterceptor` / `LegacyApiAuthConfiguration` | 给遗留接口补鉴权 |
| `media` | `MediaSignedUrlService` | HMAC-SHA256 签发带过期时间的播放地址并校验 |
| | `ChunkedUploadService` / `ChunkUploadController` / `MediaMergeRequest` / `MediaUploadStatusView` | 分片上传、断点续传、合并与整段 sha256 校验（#12） |
| `storage` | `MediaStorage`（接口）· `LocalMediaStorage` · `S3MediaStorage` · `S3ObjectResource` · `S3MediaStorageConfiguration` | 录像存取，`MEDIA_PROVIDER=local/s3` 切换 |

### 5.4 `security`

`LoginAttemptGuard` 实现登录限流：同一用户名连续失败超过阈值后锁定一段时间，
计数在进程内存中（多实例部署会失效，见「已知限制」）。

### 5.5 `util`

| 类 | 职责 |
| --- | --- |
| `LogSanitizer` | 脱敏 Bearer 令牌、密钥、邮箱、手机号 |
| `JsonText` | 从模型返回里提取 JSON |
| `Dto2Record` / `MarkdownToPdfConverter` | 遗留功能的转换工具 |

### 5.6 遗留包

| 包 | 说明 |
| --- | --- |
| `assistant` | `InterviewAssistant` / `JavaAssistant` / `ProgramAssistant` / `ResumeRewriterAssistant`，基于 Spring AI `ChatClient` 的对话助手 |
| `audio` | `AudioService` / `SpeechToTextService` / `TextToSpeechService` / `AudioConverter`（ffmpeg） |
| `controller` | 旧接口：`/interview`、`/frontend`、`/resume`、`/fileupload`、`/candidate`、`/api/stream-audio` |
| `data` / `repository` | 旧实体（`Candidates`、`Resume`、`Knowledge`、`ResumeVector`）与仓储 |
| `services` | `ResumeService`、`KnowledgeService`、`EmbeddingService`、`RedisService` 等 |
| `tools` | Spring AI Function Calling 用的工具类 |
| `images` / `mail` / `advisor` | 图像生成、邮件、日志 Advisor |

---

### 5.7 `coding` —— 笔试模块

独立于视频面试的一套完整链路：AI 出题 → 沙箱判题 → 报告。**执行用户代码是这个模块最大的风险点**，
所以安全边界集中在 `CodeRunnerService` 一个类里。

| 类 | 职责 |
| --- | --- |
| `CodeLanguage` | 语言矩阵：`java` / `python` / `cpp` / `javascript` / `go` 各自的镜像、文件名、编译与运行命令 |
| `CodeRunnerService` | 在一次性 Docker 容器里执行代码，见下方「沙箱安全边界」 |
| `CodeRunResult` | 单次执行结果：退出码、stdout / stderr、编译输出、耗时、是否超时 |
| `QuestionType` | 五种题型：`coding` / `sql` / `fill_blank` / `choice` / `short_answer`，其中前两种需要执行 |
| `CodeJudgeService` | 五种题型的判分：执行型跑用例，填空/选择确定性比对，简答交给模型 |
| `ShortAnswerGrader` / `SpringAiShortAnswerGrader` | 简答题评分（唯一没有客观答案的题型），强制 JSON 结构校验 |
| `CodingQuestionPlanService` | AI 出题 + 自检闭环，见下方「两步生成」 |
| `CodingSessionService` | 会话生命周期、判分入口、报告组装、7 天保留期清理 |
| `CodingController` | `/api/v1/coding-sessions` 下的接口 |

**沙箱安全边界**（参数就是唯一的安全边界，改动前请想清楚）：

```
docker run --rm -i
  --network none                      # 禁网：防外联、防挖矿
  --memory 256m --memory-swap 256m    # 内存上限，且不允许用 swap 绕过
  --cpus 0.5 --pids-limit 64          # 防 fork 炸弹与 CPU 抢占
  --read-only --tmpfs /tmp:rw,size=64m
  -v <临时目录>:/work                 # 只挂当次目录，进程结束即删
  <language image> sh -c "<编译/运行脚本>"
```

脚本里还有两道限制：`ulimit -f 4096`（单文件最多 2MB，防写爆磁盘）、编译与运行各自用
`timeout` 限时（只做容器级总超时的话，一个死循环会一直耗到总超时才被杀）。
输出**写进挂载目录里的文件**而不是读管道——管道缓冲区写满会把子进程卡死，
而且超时后还能拿到部分输出。整体超时后按容器名 `docker kill`，
否则只杀掉客户端进程、容器仍在跑。

**两步生成（AI 出题 + 自检）**：这个设计是被现实打出来的，值得记住为什么。

1. 第一问：只出「题面 + 起始代码 + **能直接运行的参考解**」，测试用例留空。
2. 第二问：把参考解**发回给模型**，用单独的 system prompt 只让它配输入
   （照着参考解的读取方式给几行、给什么格式）。
3. **期望输出一律由参考解在沙箱里跑出来**，不采用模型手算的结果。

为什么不是一次生成：最初让模型同时给出参考解和手算的期望输出，连续三次自检全挂——
模型没法把 stdout 逐字符算准。改成「只给输入 + 一个能跑的参考解」之后，
用例与参考解天然一致，自检只需要回答「参考解能不能跑完」；
跑不通的单个用例会被丢弃（保留可见示例即可），整批不可用才重试（默认 3 次）。

**两个必须记住的实现约束**（都踩过坑）：

1. `ChatClient.Builder.defaultSystem()` 是**修改自身并返回同一实例**。同一份 builder 上建两个
   客户端，第二个 system prompt 会覆盖第一个 → 出题客户端拿着「配测试输入」的提示词去出题。
   每个客户端都必须 `.clone()`（遗留代码 `ProgramAssistant` 也是这么做的）。
2. 下发给前端的题目视图 `CodeQuestionView` **不能带任何答案**：填空只给 `hint` 不给 `accepted`，
   隐藏用例只给名字，`answerJson`（参考解 / 正确选项 / 参考要点）永不出服务端。

**接口**（均在 `/api/v1/coding-sessions` 下，鉴权与数据隔离沿用 `OwnerResolver`）：

| 接口 | 说明 |
| --- | --- |
| `GET /capabilities` | 是否启用代码沙箱、可用语言与题型 |
| `POST /` | 出题并建场（内部走 AI 出题 + 自检，耗时较长） |
| `GET /`、`GET /{id}` | 列表 / 详情（题目 + 每题最近一次提交概要） |
| `POST /{id}/questions/{qid}/run` | 只跑可见示例，**不落库** |
| `POST /{id}/questions/{qid}/submit` | 跑全部用例（含隐藏）并计分落库 |
| `GET /{id}/report` | 笔试报告 |
| `DELETE /{id}` | 删除会话（级联删除题目与提交） |

**判分口径**：

| 题型 | 判分方式 |
| --- | --- |
| 上机编程 | 跑测试用例，比对 stdout（忽略行尾空白与首尾空行） |
| 数据库题 | 参考查询/作答查询都在临时 SQLite 上执行，比对结果集（复用 Python 镜像，无额外镜像） |
| 编程填空 | 按空比对候选答案（忽略首尾与内部多余空白，**保留大小写**——Java 关键字大小写有意义） |
| 选择题 | 比对选项集合（顺序与大小写不敏感，少选/多选都算错） |
| 简答题 | 模型按参考要点评分，强制 JSON 结构（`score` / `summary` / `matched` / `missing`） |

### 5.8 数字人（真人素材版）

面试官头像从"矢量小人"换成了**同一张真人照片切出来的多帧素材**。

| 类 / 文件 | 职责 |
| --- | --- |
| `scripts/split-avatar-sheet.py` | **素材处理脚本**：从组合图里定位 12 张卡片，去掉半透明卡片底，抠出人物，按人脸对齐后输出统一尺寸的帧 |
| `web/public/assets/avatar/*.png` | 切好的 14 张帧（12 个表情/状态 + 1 张合成闭眼 + 1 张大图），340x260 透明 PNG |
| `web/src/services/avatarAssets.js` | 帧清单、说话循环表、预加载 |
| `web/src/services/avatarState.js` | **状态的唯一来源**：7 个状态 + 状态→静止帧的映射 + 错误/自动播放被拦的标记 |
| `web/src/services/avatarAudio.js` | 配音播放器：单例 `HTMLAudioElement` + 一次性创建的 Web Audio 分析器 |
| `web/src/services/mouthAnimation.js` | 音量→嘴型档位的纯函数（可单测） |
| `web/src/components/DigitalHuman.vue` | 渲染帧并按音量切嘴型；只读状态，不写状态 |

**动画的自然度处理**（都在 `mouthAnimation.js` + 组件里，纯函数部分有单测）：

1. **起音快、落音慢的一阶平滑**：直接用瞬时 RMS 会让嘴型在阈值附近抖。
   上升系数 0.55、下降 0.16 —— 张嘴要及时，闭嘴要有个收的过程。
2. **分档迟滞**（margin 0.3）：向上要明显超过阈值才升档、向下要明显低于才降档，
   否则音量停在阈值附近会每秒跳好几次档。
3. **收尾过渡**：说完话先用低音量帧过渡 90ms，再回到静止帧，不是"啪"地闭嘴。
4. **眨眼**：静止时每 3-5 秒随机眨一次、每次 110ms。闭眼帧是**合成**的——
   组合图里没有闭眼素材，脚本把镜片内的眼睛区域换成脸颊肤色并画一条睫毛线（镜框不动）。
   因为闭眼帧的嘴也是闭的，**只在没说话时眨**，否则会看到嘴巴跟着闭一下。
5. **头部微摆动**：CSS 绕脖子附近缓慢转 ±0.6 度；与上下浮动用的是 `transform`
   和 `translate` 两个独立属性，互不覆盖。`prefers-reduced-motion` 下全部关闭。

**可选：待机循环视频**（`IDLE_VIDEO = /assets/avatar/idle-loop.mp4`）。
存在且能加载时，**非说话状态**播视频、说话时切回切帧；文件不存在则静默退回静态帧。
这样"花大成本做的真人循环视频"用在不说话的时候，"跟着动态配音走"仍然靠切帧，
两者各取所长。加视频**不需要改任何代码**，放文件即可。

**能力边界（必须如实说明，不要对外宣称更多）**：

1. 这是**图片切帧动画**；
2. 嘴型由**音量大小**分档驱动（声音大 → 嘴张得大），**不识别音素**，
   念 "a" 还是 "o" 对它没有区别；
3. 真正的音素级口型同步（或基于专业模型的对口型）**本次没有实现**。

音量分析拿不到时（跨域读不到音频、浏览器不支持 Web Audio）自动降级为固定节奏轮换嘴型，
声音不受影响；这一点在 `avatarAudio.js` 与 `DigitalHuman.vue` 里都有注释说明。

**素材处理里的三个坑**（换素材时会再遇到）：

1. 组合图里的透明区是**真透明**（`(0,0,0,0)`），但卡片底是**半透明白**（alpha 60-140），
   直接按 alpha 阈值裁会留一圈灰底 → 用线性重映射把这一段压成透明。
2. **左侧大图的手臂在第 2、3 行会伸进第 1 张卡片的矩形内** → 必须按连通域抠出人物，
   否则第 1 张卡会连着大图的手臂一起被裁（初版就是这样）。
3. 各帧的**人脸位置随卡片不同**（卡片高度不同）→ 按"人脸顶边 + 躯干中心"锚定，
   避免切换嘴型时人物跳动。实测 12 帧输出：人脸顶边极差 **2px**。
4. **第 3 行的说明标签（"聆听状态"那类白条）会跟着裁进来**：标签是整卡宽度的不透明白底、
   从 y≈828 铺到卡片底边，而人物是画在它**上面**的。只能截在标签之前（827）；
   不能靠"抠掉白色"绕过 —— 人物胸前是浅色衬衫（≈(247,247,249)），抠白会在胸口打洞。
   脚本里有 `looks_like_label()` 兜底：底部出现大面积不透明白底就直接报错。
5. **缩放要用「人脸宽度」，不能用「人脸高度」**：行 3 的人物在源图里是缩小版
   （脸宽 76-77 vs 行 1/2 的 115-123，约 0.65 倍），但**脸高**测出来是 96 vs 88，
   只差 9% —— 因为高度会把脖子算进去，把真实差异吃掉了。按脸高统一缩放的后果是
   "说话时头很大、切到聆听人物突然小 30%"。改用脸宽后：14 帧脸宽 106-113（极差 6px）。
   另外缩放按**行**取中位数而不是逐帧量（同一行尺度本来就一样，逐帧量有 ±7% 噪声，
   会让说话时人物忽大忽小）：行 1/2 用 0.92，行 3 用 1.45。
6. **构图要留出领带**：裁得越放大人头越大、领带越容易被挤出画面。现在人脸占画面宽度 33%
   （原来 41%），嘴型帧的整幅裁剪（含领带）都落在 340x260 的画布内。
7. **画布高度要和被标签截断的位置对齐**：行 3 被说明标签截断，身体比行 1/2 短；
   画布取 **340x260** 后，14 帧的身体底部落在 243-254（极差 11px），切换时不会看到身体伸缩。
8. **第 3 行那 4 张"状态图"最终没有用于展示**。它们是**非等比**的：
   脸宽 110-113 时肩宽达到脸宽的 260-270%，而第 1/2 行只有 173-189% ——
   头和肩的比例本身就不同，所以"对齐脸则肩宽近一倍、对齐肩则脸小一圈"，
   靠缩放无法两全，表现出来就是用户反馈的"说话和不说话时人物大小不一样"。
   现在四个非说话状态改用第 1/2 行的嘴型帧（`mouth-m` / `mouth-closed` / `mouth-smile`），
   尺寸天然一致（实测脸宽 69 vs 70），状态差异由文案与指示灯表达。
   这 4 张素材仍然生成并保留，供换素材时对照，但不再预加载、也不参与展示。

重新生成素材：

```bash
python scripts/split-avatar-sheet.py                # 输出素材 + 预览图
python scripts/split-avatar-sheet.py --preview-only # 只出预览，先看效果再决定
```

预览图在 `docs/images/avatar-crop-preview.png`（放在棋盘格上，能一眼看出是否残留卡片底）。

### 5.9 `quota` —— 成本护栏

模型调用是共享成本，这里给它上锁（需求见 [COST-GUARD-REQUIREMENTS.md](COST-GUARD-REQUIREMENTS.md)）：

| 类 | 职责 |
| --- | --- |
| `ModelCallGuard` | 按"账号 + 自然日"计数 + 在飞并发计数；`call`（拿不到就 429）/ `tryAcquire`（拿不到返回许可，由调用方决定降级）/ `require`（事务方法内先扣再进业务）/ `usage` |
| `ModelUsageController` | `GET /api/v1/me/model-usage` —— 前端顶栏额度条的来源 |
| `ModelLease` · `ModelQuotaExceededException` | 许可对象（`close()` 释放并发名额，日额度不返还）与 429 异常 |

计数优先走 Redis（多实例共享、重启不丢），`RedisCounterStore` 与 `InMemoryCounterStore` 都实现同一个
`CounterStore` 接口；Redis 连不上时自动退到内存并记一条警告 —— 护栏失效的退化方向是"照常花钱"，
而不是"谁都别想用"。

**接入点**：面试出题、笔试出题、简答判分、追问、转写与评分（在 `AnalysisJobService` 里）、
简历生成/补全/优化、语音合成。异步链路拿不到额度时**顺延**（`AnalysisDeferredException` → 任务回到
`pending` 并把 `nextAttemptAt` 指到额度重置后），不消耗重试次数、不进死信队列。

### 5.10 `language` —— 外语口语（英语，L1；日语配置保留、暂未上线）

| 类 | 职责 |
| --- | --- |
| `LanguageCatalog` | 语言 / 场景 / 水平的注册表；**音色能力写在配置里**（英语有服务端音色；日语音色不可用，暂时下线，加回只需补一条配置） |
| `LanguageSessionService` | 开一场、同步跑一轮（转写 → 回复 → 合成）、结束出报告、保留期清理；每轮占 1 次额度 |
| `LanguageSpeechService` | 按语言传 `language_hints` 调用 `paraformer-realtime-v2`；按语言选 TTS，日语明确不出音频 |
| `SpringAiLanguageCoach` + `LanguageCoachValidator` | 回复与复盘；校验器挡住"编造用户原话"和"凑数建议"，回复/建议长度也在这里夹住 |
| `LanguageController` | `/api/v1/language-*`，音频轮走 multipart，手打轮走 JSON |

与面试模块的关键区别：**没有异步队列**。对话必须同步返回，否则"入队 + 15 秒轮询"就变成留言板。
能力验证脚本是 `VoiceCapabilityProbe`（`-Dvoice.probe=true`），它不是 CI 的一部分。

### 5.11 `goal` —— 求职目标与每日计划（Phase 1）

| 类 | 职责 |
| --- | --- |
| `JobGoalService` / `JobGoalController` | 目标的 CRUD、倒计时、归档/删除；删除时练习解绑、计划级联删除 |
| `DailyPlanService` / `DailyPlanController` | 今日 3 题的规则生成、开始计划项、完成回写、连续记录 |
| `JobGoalEntity` / `StudyPlanEntity` / `StudyPlanItemEntity` | 目标、7 天计划、每日计划项 |

规则：1 道薄弱维度 + 1 道错题（没有就岗位高频）+ 1 道岗位高频；题目在"开始"时用模板生成，
不额外调用模型，所以首页到开口不被模型延迟卡住。计划项完成后更新目标进度；当天完成 ≥2 题记 1 天连续。

### 5.12 `progress` —— 周报

| 类 | 职责 |
| --- | --- |
| `WeeklyReportService` | 聚合一周的计划完成率、完成练习、当前回答、维度平均、较上周变化与最佳 before/after |
| `WeeklyReportController` | `GET /api/v1/progress/weekly?week=YYYY-MM-DD` |
| `WeeklyReportEntity` / `WeeklyReportRepository` | 同账号同周一份周报快照 |

只统计 `status=completed` 的练习场次；刚创建的空场次会把数字撑得没有意义，因此排除。

### 5.13 `notification` —— 提醒与退订

| 类 | 职责 |
| --- | --- |
| `NotificationService` | 每日提醒、周报、面试倒计时、测试邮件、HMAC 退订 |
| `NotificationController` | `/api/v1/me/notifications*` 与邮件底部退订接口 |
| `NotificationPreferenceEntity` / `NotificationLogEntity` | 偏好与发送去重日志 |

定时任务：每日提醒每 15 分钟检查一次（当天完成 2 题跳过）、周报周一 09:00、
倒计时每天 09:05。同一账号同一类提醒在对应窗口内最多发一封；邮件未配置时记录 failed/skipped，不影响主流程。

### 5.14 `cohort` —— 学习小组 / 班级（Phase 6A）

| 类 | 职责 |
| --- | --- |
| `CohortService` / `CohortController` | 创建小组、邀请码加入、成员与分数共享、归档 |
| `AssignmentService` | 布置作业，为每位学员生成 `study_plan_items`（`kind=assignment`）；聚合完成看板 |
| `CohortHelpService` | 学员针对某道题发求助、助教填写反馈、学员查看反馈 |
| `PeerReviewService` | 匿名同伴互评：只展示转写、3 项评分 + 一句建议、去重与 opt-in |
| `CohortEntity` / `CohortMemberEntity` / `AssignmentEntity` | 小组、成员角色、作业 |

助教创建小组后成为 `coach`；作业项通过 `assignment_id` 挂到学员计划，并在
`DailyPlanService.today` 里与普通每日计划合并，所以作业会出现在「今天练什么」。
分数共享默认关闭；作业看板只显示学员，不把助教自己算进完成率。

### 5.15 `profile` —— 个人资料、外观与账号安全

| 类 | 职责 |
| --- | --- |
| `ProfileController` | `GET/PUT /api/v1/me/profile`；`PUT /api/v1/me/password` |
| `ProfileService` | 读取和更新昵称、邮箱、时区、字号、减弱动效、主题色 |
| `ProfileView` / `ProfileUpdateRequest` | 个人资料响应与带校验的更新请求 |
| `PasswordChangeRequest` | 当前密码与新密码请求；实际校验和 BCrypt 写入由 `CandidatesService` 完成 |

`candidates.candidate_name` 是历史数据归属键，个人资料页把它作为**只读登录名**展示，
修改展示名称只写 `display_name`。时区使用 `ZoneId.of` 验证；主题色白名单与前端
`web/src/services/theme.js` 保持一致。字号支持 `standard` / `large` / `xlarge`，
前端通过根节点缩放保持文字与控件比例一致；减弱动效通过 `data-reduce-motion` 关闭非必要动画。

偏好仍以 `candidates` 表为主：主题色继续兼容原来的 `/api/v1/me/preferences`，
个人设置接口返回同一份主题值，避免侧栏快捷色点与设置页状态分叉。

## 6. 认证与授权

- **注册**：`POST /register`（`RegistrationController` → `CandidatesService.register`），
  用户名唯一（DB 唯一索引兜底并发）、口令用 BCrypt 哈希存进 `candidates.invitation_code`，
  注册成功直接签发 token，前端不用再登录一次；
- **登录**：`POST /login`，按用户名取账号后校验口令。历史数据的明文邀请码仍可登录，
  校验通过后就地升级成 BCrypt 哈希（老账号无需人工迁移）；
- **个人资料**：`GET/PUT /api/v1/me/profile`，只允许修改昵称、邮箱、时区与外观偏好，
  登录名不可改；`PUT /api/v1/me/password` 校验当前密码后写入新的 BCrypt 哈希；
- **令牌**：HS256 JWT，密钥来自 `AUTH_TOKEN_SECRET`，默认有效期 15 分钟；
- **传递**：`Authorization: Bearer <token>`；
- **归属隔离**：`OwnerResolver` 把 token 解析成 owner，所有查询按 owner 过滤——
  所以未认证返回 401、跨用户访问返回 404（不泄露资源是否存在）；
- **限流**：`LoginAttemptGuard`，默认 10 次失败锁 15 分钟；
- **媒体访问**：播放走短时签名 URL（HMAC + expires），不直接暴露存储路径。

---

## 7. 媒体存储与播放

```
上传：POST /api/v1/answers/{answerId}/media   (multipart, Content-Type: video/*)
  └─ PracticeSessionService.storeAnswerMedia → MediaStorage.save → 记录 mediaKey / 大小 / 类型

播放：GET /api/v1/media/{answerId}/signed-url  →  { url: /api/v1/media/{id}?expires=&signature= }
      GET /api/v1/media/{answerId}?expires=&signature=   →  校验签名与过期时间 → 返回字节
```

- 本地模式落在 `MEDIA_STORAGE_DIR`，S3 模式走 `practice.media.s3.*`；
- 媒体端点支持 HTTP Range（206），所以浏览器能拖动进度条；
- 超过 `MEDIA_RETENTION_DAYS`（默认 7 天）的会话会被定时清理，同时删除录像。

大文件走**分片上传**（#12），与单段上传并存：

```
GET    /api/v1/answers/{id}/media/upload?totalChunks=N    → 已收到的分片（刷新后据此续传）
PUT    /api/v1/answers/{id}/media/chunks/{index}?...       → 上传单个分片（application/octet-stream）
POST   /api/v1/answers/{id}/media/merge                    → 校验 sha256 → 拼流写入正式 key → 入队分析
DELETE /api/v1/answers/{id}/media/upload?totalChunks=N     → 放弃并清理临时分片
```

- 上传标识复用 `answerId`，不需要额外的上传会话表；
- 临时分片存在 `uploads/{answerId}/chunk-N`，合并成功后删除；
- 合并前先按序算一遍整段 SHA-256，**校验失败不覆盖已有媒体**，分片保留供补传；
- 前端 `chunkedUpload.js` 超过 4MB 自动分片，遇到后端 404/405 回退单段上传。

---

## 8. 数据模型

Flyway 迁移在 `consumer/src/main/resources/db/migration/`：

| 迁移 | 表 | 要点 |
| --- | --- | --- |
| `V1__practice_sessions.sql` | `practice_sessions`、`practice_answers` | 会话（owner、题目 JSON、状态、保留期）+ 逐题作答（录像 key、大小、时长） |
| `V2__analysis_jobs.sql` | `analysis_jobs` | 分析任务：`status`、`attempts`、`last_error`，按 `answer_id` 唯一 |
| `V3__candidate_login.sql` | `candidates` | 登录账号（姓名 + 邀请码） |
| `V4__analysis_queue_observability.sql` | `analysis_jobs` / `analysis_jobs_dlq` | 退避字段 `next_attempt_at` + 死信表 |
| `V5__candidate_registration.sql` | `candidates` | 用户名唯一索引（开放注册），口令改存 BCrypt 哈希 |
| `V6__coding_assessment.sql` | `coding_sessions` / `code_questions` / `code_submissions` | 笔试模块：会话、题目（五种题型共用一张表）、提交与判分结果 |
| `V7__user_theme_preference.sql` | `candidates` | 每个账号的界面主题色 |
| `V8__resume_library.sql` | `resumes` / `resume_optimizations` | 简历库与按 JD 优化的历史（逐段改动 + 待补充占位符） |
| `V9__score_archive_and_follow_up.sql` | `practice_score_archive` + `practice_sessions.follow_up_enabled` | 成绩与录像分开保留（只存分数与维度），并回填历史；追问开关 |
| `V10__mistake_book.sql` | `practice_score_archive` | 题面 / 题目分类 / 面试类型与风格跟着成绩走，错题本才有题可重练；回填还在保留期内的历史 |
| `V11__language_practice.sql` | `language_sessions` / `language_turns` | 外语口语：会话（语言/场景/水平/轮数/报告）+ 每一轮（录音、转写、回复、建议、四段耗时） |
| `V12__language_stream_timing.sql` | `language_turns` | L2 首字/首帧耗时（实时通道当前默认关闭） |
| `V13__job_goals_and_daily_plans.sql` | `job_goals` / `study_plans` / `study_plan_items` + `practice_sessions.goal_id` | 求职目标、每日计划与练习回挂目标 |
| `V14__continuous_interview_and_transcript_revisions.sql` | `practice_sessions` / `practice_answers` / `answer_transcript_revisions` | 连续面试模式与暂停次数、转写原文/修正时间、修正审计 |
| `V15__answer_attempts_and_rewrites.sql` | `practice_answers` / `answer_rewrites` | 同一道题最多 3 个 attempt、当前版本标记、改写正文与历史 |
| `V16__weekly_reports_and_notifications.sql` | `weekly_reports` / `notification_preferences` / `notification_log` | 周报快照、邮件提醒偏好与去重日志 |
| `V17__learning_cohorts_and_assignments.sql` | `cohorts` / `cohort_members` / `assignments` + `study_plan_items.assignment_id` | 学习小组、邀请码、作业与完成看板 |
| `V18__cohort_feedback_and_peer_reviews.sql` | `cohort_help_requests` / `peer_reviews` + `cohort_members.peer_review_enabled` | 学员求助、助教反馈、匿名同伴互评 |
| `V19__profile_settings.sql` | `candidates` 的 `display_name` / `timezone` / `font_scale` / `reduce_motion` / `created_at` | 个人资料、外观偏好与加入时间 |

> 遗留功能的表（`resume`、`knowledge`、`resume_vectors`、`vector_store` 等）由
> `resources/ddl/` 下的脚本维护，**不在 Flyway 管理范围内**，这也是遗留接口容易 500 的原因。
> `spring.ai.vectorstore.pgvector` 使用的 `vector_store` 表由 Spring AI 在启动时按需初始化。

---

## 9. 配置项

全部配置集中在 `consumer/src/main/resources/application.properties`，环境变量样例见 `.env.example`。
后端会自动加载仓库根目录（或 `consumer/` 目录）的 `.env`（`spring.config.import`）。

| 分组 | 关键配置 | 默认值 |
| --- | --- | --- |
| 数据库 | `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | 本地 interview 库 |
| 迁移 | `PRACTICE_FLYWAY_ENABLED` | true |
| 服务 | `SERVER_PORT` | 8088 |
| 媒体 | `MEDIA_PROVIDER` / `MEDIA_STORAGE_DIR` / `MEDIA_RETENTION_DAYS` / `MEDIA_SIGNED_URL_TTL_SECONDS` | local / ./data/media / 7 / 300 |
| S3 | `MEDIA_S3_ENDPOINT` / `_REGION` / `_BUCKET` / `_ACCESS_KEY` / `_SECRET_KEY` / `_PATH_STYLE` | 见 .env.example |
| 上传上限 | `MEDIA_MAX_FILE_SIZE` / `MEDIA_MAX_REQUEST_SIZE` | 250MB |
| 供应商 | `PRACTICE_ASR_PROVIDER` / `PRACTICE_EVALUATION_PROVIDER` | dashscope / spring-ai |
| 分析调度 | `PRACTICE_ANALYSIS_MAX_ATTEMPTS` / `_PROVIDER_TIMEOUT_MS` / `_POLL_INTERVAL_MS` / `_INITIAL_DELAY_MS` | 3 / 120000 / 15000 / 30000 |
| 重试退避 | `PRACTICE_ANALYSIS_RETRY_BACKOFF_MS` / `_RETRY_BACKOFF_MAX_MS` | 5000 / 300000 |
| 可观测性 | `management.endpoints.web.exposure.include`（health / info / prometheus） | 见 application.properties |
| 代码沙箱 | `CODE_RUNNER_ENABLED` / `_MAX_CONCURRENCY` / `_COMPILE_TIMEOUT_SECONDS` / `_RUN_TIMEOUT_SECONDS` / `_MEMORY_MB` | false / 2 / 10 / 5 / 256 |
| 笔试 | `practice.coding.retention-days` / `practice.coding.plan-max-attempts` | 7 / 3 |
| 数字人 | `PRACTICE_AVATAR_PROVIDER` / `_TTS_ENABLED` / `_VOICE` | local-2d / true / sambert-zhixiang-v1 |
| 鉴权 | `AUTH_TOKEN_SECRET` / `AUTH_LOGIN_MAX_ATTEMPTS` / `AUTH_LOGIN_LOCK_WINDOW_MINUTES` | 见 .env.example |
| AI 额度 | `QUOTA_MODEL_ENABLED` / `QUOTA_MODEL_DAILY_LIMIT` / `QUOTA_MODEL_MAX_CONCURRENT` / `QUOTA_MODEL_ZONE` | true / 120 / 3 / Asia/Shanghai |
| 外语口语 | `LANGUAGE_RETENTION_DAYS` | 7 |
| 跨域 | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` 等 |
| 模型 | `spring.ai.dashscope.*`（chat `qwen-max`、embedding `text-embedding-v2`、speech / transcription） | 统一读 `Ali_API_KEY` |

> `spring.ai.dashscope.*.api-key` 都写成 `${Ali_API_KEY:}`，缺 Key 时不会在占位符解析阶段崩溃，
> 而是给出明确的 `DashScope API key must be set`。

---

## 10. 前端架构

| 文件 | 职责 |
| --- | --- |
| `main.js` / `App.vue` | 应用入口与整体布局 |
| `router.js` | 路由：`/login`、`/`（工作台）、`/today`、`/goals`、`/goals/new`、`/goals/:id`、`/practice/setup`、`/practice/device-check`、`/practice/:id/session`、`/practice/:id/report`、`/history`、`/mistakes`、`/language`、`/language/:id` |
| `authGuard.js` | 路由守卫：未登录跳登录页 |
| `views/` | 练习主线页面：`LoginView`、`DashboardView`、`SetupView`、`DeviceCheckView`、`SessionView`、`ReportView`、`HistoryView`、`MistakeBookView`（简历与笔试的页面见下两行） |
| `views/Coding*` | 笔试三个页面：`CodingSetupView`（出题配置 + 历史）、`CodingExamView`（答题，含五种题型渲染与运行/提交）、`CodingReportView`（报告）。路由 `/coding`、`/coding/:id`、`/coding/:id/report` |
| `components/CodeEditor.vue` + `services/monaco.js` | Monaco 封装：**懒加载**（只有打开笔试页才拉那几 MB），含 Java/Python/C++/JS/Go/SQL 高亮；只需核心 worker，因为这些语言是主线程 Monarch 词法高亮 |
| `components/DigitalHuman.vue` | 真人数字人：切帧动画 + 音量驱动嘴型，见 5.8 |
| `stores/practice.js` | Pinia store：会话列表与草稿、`localStorage` 持久化、过期清理、断网补传 |
| `views/MistakeBookView.vue` + `services/practiceMistakes.js` | 错题本：总览页那 5 条的组装、`/mistakes` 页的分页与岗位筛选（参数拼接与页码计算都在 service 里，可单测）；重练走 store 的 `retryMistake` |
| `services/api.js` | 后端 API 封装，统一注入 token、错误归一化 |
| `services/mediaCapture.js` | `MediaRecorder` 封装：权限、开始/停止、分片、时长 |
| `services/mediaStore.js` | 上传失败的本地缓存 |
| `data/questions.js` | 内置题库（无 Key 时的回退题目） |

前端通过 `VITE_API_BASE_URL` 指向后端；开发态由 `web/vite.config.js` 代理到 8088。

---

## 11. 测试策略

| 层 | 工具 | 规模 |
| --- | --- | --- |
| 后端单元/集成 | JUnit 5 + Spring Boot Test | 126 个用例（另有 6 个需要容器、5 个需要 Docker 沙箱的用例默认跳过） |
| 前端 | Vitest | 52 个用例（API 客户端、路由守卫、媒体捕获、分片上传、数字人状态与嘴型、store、内置题库） |
| 端到端 | `scripts/smoke-test.ps1` | 24 项断言，覆盖登录 → 出题 → 上传 → 分析 → 报告 → 签名播放 → Range → 删除 |
| 真实链路端到端 | `scripts/e2e-real-providers.ps1` | 真实凭据 + 真实音轨，产出 `artifacts/e2e/report-*.json` |
| 界面截图 | `web/scripts/capture-screenshots.mjs` | 真实浏览器登录后逐页截图，输出到 `docs/images/`（README 配图来源）；UI 改版后重跑即可，避免配图与代码脱节。用系统 Chrome（`CHROME_PATH` 可覆盖），因为 Playwright 自带的 Chromium 在内网常常下不动 |
| 数字人 | `scripts/split-avatar-sheet.py`、`web/scripts/capture-session-avatar.mjs` | 前者从组合图切帧并生成预览；后者用假摄像头走完整流程截图，并采样说话过程中的嘴型，验证"嘴型确实在切换"（实测 40 次采样出现 6 种帧） |

```bash
# 全部后端测试（含需要 Postgres / S3 的集成用例）
mvn -B clean package
mvn -B test "-Ddb.integration=true" "-Ds3.integration=true"

# 前端
npm --prefix web test

# 端到端：兜底供应商（假字节即可）
pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady

# 端到端：真实供应商（ASR 需要真实音轨）
pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady \
  -MediaPath data/verify-clip.mp4 -AnalysisTimeoutSeconds 300

# 真实供应商 E2E（带审计产物；也可用 -MediaPath 指定真人录制的素材）
pwsh scripts/e2e-real-providers.ps1

# 分析队列指标
curl http://localhost:8088/actuator/prometheus | Select-String analysis_
```

> 真实 ASR 是异步的，脚本会等 `pending` 与 `processing` 两种状态，别只判断 `pending`。

---

## 12. 扩展点

| 想做什么 | 改哪里 |
| --- | --- |
| 换转写供应商 | 实现 `AsrProvider`，在配置里把 `PRACTICE_ASR_PROVIDER` 指过去 |
| 换评分模型/提示词 | 实现 `EvaluationProvider`，或改 `prompt/practice-evaluation-system-prompt.st` |
| 换出题提示词 | `prompt/practice-question-plan-system-prompt.st` + `SpringAiQuestionPlanProvider` |
| 换 TTS | 实现 `TtsProvider` / `AvatarProvider`，配置 `PRACTICE_AVATAR_*` |
| 换对象存储 | 实现 `MediaStorage`，配置 `MEDIA_PROVIDER` |
| 加题型/题库 | `data/questions.js`（前端内置）+ `PracticeQuestionCatalog`（后端读取） |
| 调整保留期与调度 | `MEDIA_RETENTION_DAYS`、`PRACTICE_ANALYSIS_*` |

---

## 13. 已知限制

1. **遗留接口**：`/interview`、`/resume`、`/fileupload` 等依赖未被 Flyway 管理的表，部分调用即 500；
   已补鉴权，但迁移还是删除尚未决定。
2. **Redis 未使用**：分析队列与任务认领在 PostgreSQL，登录限流在进程内存，Redis 目前是空转依赖。
3. **无 Key 无法启动**：遗留的四个 `*Assistant` 在构造时就需要 DashScope `ChatModel` 并校验 Key，
   所以即使 `PRACTICE_*_PROVIDER=stub`，也必须给 `Ali_API_KEY` 一个非空值。
4. **单机限流**：`LoginAttemptGuard` 用进程内存计数，多实例部署时失效。
5. **口型动画**：振幅驱动逻辑只有构建验证，未在真实音频下实测。
6. **actuator 未鉴权**：`/actuator/prometheus` 与应用同端口且不带鉴权，
   生产部署需要用管理端口或网关限制访问来源。
7. **分片合并读两遍**：为保证「校验失败不落库」，合并前先算一遍整段 SHA-256 再拼流写入；
   在 S3 下意味着分片会被读两次，文件很大时要注意流量成本。
8. **DLQ 无独立管理员角色**：`/api/v1/analysis/dlq` 按数据归属过滤，只能看到自己的死信任务。
9. **代码沙箱默认关闭**：`CODE_RUNNER_ENABLED=false`。它会在宿主机执行任意用户代码，
   只应在自己可控的机器上打开；公网部署需要换成独立执行节点并加更严格的隔离。
10. **C++ / Go 镜像未预拉**：Java（364MB）、Python（119MB）、Node（136MB）已拉取；
    C++ 用的 `gcc:13` 约 1.4GB，Go 约 250MB，首次执行该语言时会自动拉取，会等几分钟。
11. **出题耗时**：一次出题要跑「生成 → 参考解沙箱自检 →（失败则换一批重试，默认 3 次）」，
    实测三题一批约 92 秒，最坏可能到 2 分钟以上；前端会显示真实等待秒数。
12. **SQL 方言是 SQLite**：数据库题在内存 SQLite 上执行（复用 Python 镜像，不引入额外镜像），
    需要真 Postgres/MySQL 语义时要另接执行环境。
13. **数字人不是音素级口型同步**：嘴型由音量分档驱动，识别不了具体发音；
    音量为 0 时回到闭唇帧，音量分析不可用时降级为定时轮换。详见 5.8。
14. **数字人素材依赖那张组合图**：`web/public/assets/avatar/avatar-expression-sheet.png`
    既是运行素材也是切图脚本的输入（约 2.4MB）。换人物时必须重新跑脚本，
    并检查 `docs/images/avatar-crop-preview.png`。

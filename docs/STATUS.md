# Light-Interview 实施状态

对照 `PLAN.md` 的逐条核对。状态含义：

- **已验证**：有可复现的运行时证据（命令输出、HTTP 响应、浏览器实测或测试用例）。
- **已实现·未验证**：代码在，但缺少运行时证据。
- **待输入 / 待决定**：需要外部凭证或产品决策。

复现命令见文末。

> **新需求（待确认）**：简历功能（存入 + 按 JD 优化 + 导出 + 与练习打通）的需求文档已写好：
> [RESUME-REQUIREMENTS.md](RESUME-REQUIREMENTS.md)。里面有现状盘点（项目里已有一套**遗留**简历改写，
> 但数据表没纳入 Flyway 所以调不通）、10 条验收标准、分期建议，以及 6 个需要你拍板的问题。

> **分支约定**：改动一律先提交到 `feature/practice-interview`，自测通过并**经确认后**再合并到 `master`。
> 开工前先 `git merge --ff-only master` 把分支同步到主干。详见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

## 下次继续（最后更新 2026-10-10）

### 本轮验证结果（2026-10-10 续做）

上一轮删掉 Vosk 后留下的唯一悬念「应用还能不能起来」已经验证，同时补上了两块之前一直缺的运行时证据：

- **启动验证通过**：`java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar` →
  `Started ConsumerApplication in 14.7s`，Tomcat 监听 8088。Vosk 移除没有破坏启动
  （对应上一轮记录的「单元测试全绿但启动失败」那类风险，这次排除）。
- **端到端冒烟 24 项断言全过**：`pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady`
  覆盖登录、401/404 鉴权、出题、TTS 503 降级、上传、分析完成、签名播放、HTTP Range 206、删除后 404。
- **90 个测试、0 跳过**：容器起来后 `mvn -B test "-Ddb.integration=true" "-Ds3.integration=true"`
  把原先跳过的 6 个用例（`PracticePersistenceIntegrationTest` 4 个、`S3MediaStorageIntegrationTest` 2 个）跑成绿色。
  注意 PowerShell 下 `-Dkey=value` 必须加引号，否则会被拆成 `.integration=true` 并报生命周期错误。
- 打包体积仍为 173.0 MB，`rg -i vosk` 全仓库 0 命中。

### 新发现：缺 `Ali_API_KEY` 时的启动行为

- `application.properties` 里三处 `spring.ai.dashscope.*.api-key=${Ali_API_KEY}` 原本没有默认值，
  缺 Key 时先以 `Could not resolve placeholder 'Ali_API_KEY'` 炸掉上下文，报错指向 TTS 而不是根因。
- 已改为 `${Ali_API_KEY:}`，现在报错变为可执行的一句话：
  `DashScope API key must be set. Use the connection property: spring.ai.dashscope.api-key`。
- **但无 Key 仍然起不来**：根因是四个遗留 AI 组件（`InterviewAssistant`、`JavaAssistant`、
  `ProgramAssistant`、`ResumeRewriterAssistant`）构造时就要 `ChatClient.Builder`，
  而底层 `DashScopeChatModel` 强制校验 key，与 `PRACTICE_*_PROVIDER=stub` 无关。
- 文档本来就写明「至少填 `Ali_API_KEY`」，所以这属于已声明约束，不是缺陷。
  若要做到**真正无 Key 演示**，需把上述遗留组件改成条件装配（连带 `InterViewController`、
  `AudioStreamController`、`ResumeRewriterService`）——正好与待办 4「遗留接口迁移还是删除」一起决定。

### 新发现：后端原本不读 `.env`（已修）

- 文档让用户 `cp .env.example .env` 填 Key，但**后端没有 dotenv 依赖**，`spring-boot:run` 与
  `java -jar` 都不读它 —— Spring Boot 只读环境变量与 `application*.properties`。
  也就是说照文档做，Key 填了也起不来。
- 已在 `application.properties` 加入
  `spring.config.import=optional:file:.env[.properties],optional:file:../.env[.properties]`，
  两条路径分别覆盖「在仓库根目录跑 fat jar」与「在 consumer/ 目录跑 Maven 插件」两种工作目录；
  文件不存在时静默跳过。
- 实测（2026-10-10，临时 `.env` + shell 里**不设任何**环境变量）：
  `java -jar consumer/target/*.jar` 读到 `.env` 的 `SERVER_PORT=8099` →
  `Started ConsumerApplication in 18.0s`，随后冒烟 24 项全过；
  `mvn -pl consumer spring-boot:run` 同样从 `../.env` 读到 8099 并启动成功。
- 顺带修掉文档缺陷：`docs/DEPLOYMENT.md` 原本直接给 `mvn -pl consumer spring-boot:run`，
  干净检出下会因 `-pl` 不带 `-am` 而从本地仓库拿到过期的 `common`，报
  `NoClassDefFoundError: com/guangge/Interview/vo/CvRequest`。已改为推荐的
  `mvn -B clean package` + `java -jar`，并在文档里写明「用 `spring-boot:run` 需先
  `mvn -B install -DskipTests`」。
- 验证用的临时 `.env` 已删除，仓库根目录当前**没有** `.env`。

### 如何提供 `Ali_API_KEY`（面聊时不要直接贴 Key）

1. 在仓库根目录新建 `.env`（该文件已在 `.gitignore` 里，不会进版本库），加一行
   `Ali_API_KEY=你的Key`；其余变量可照 `.env.example` 复制；
2. 告诉我「填好了」，我会用真实供应商（`PRACTICE_ASR_PROVIDER=dashscope`、
   `PRACTICE_EVALUATION_PROVIDER=spring-ai`）启动后端并跑冒烟；
3. 我不会回显 Key，错误信息走 `analysis.error` / `analysis_jobs.last_error`（已脱敏）。

### 真实供应商验证结果（2026-10-10，Key 到手后）

`.env` 已填好（`Ali_API_KEY` 117 字符，`PRACTICE_ASR_PROVIDER=dashscope`、
`PRACTICE_EVALUATION_PROVIDER=spring-ai`），四条真实模型链路**首次全部跑通**：

| 链路 | 供应商 | 证据 |
| --- | --- | --- |
| 出题 | Spring AI + DashScope chat | `POST /api/v1/practice-sessions/question-plan` 返回 3 道贴 JD 的题：Spring Boot 高并发处理、订单系统性能瓶颈定位、分布式事务实践 |
| TTS | Sambert | `narration/audio` 返回 200 与 1.96 MB 音频（此前只验证过 503 降级路径） |
| ASR | Paraformer | 把一段中文回答合成为音频再喂回上传接口，转写几乎逐字一致：`削峰`→`消峰`、`八百毫秒`→`800毫秒` |
| 评分 | Spring AI | 综合 **85**，维度分有区分度：relevance 90 / completeness 85 / professionalism 90 / starStructure 70 / logic 90 —— 该回答确实没有 STAR 结构，低分合理 |

- **得分 0 不是兜底值**：第一次拿 `docs/step2.mp4` 的片段测试时得到 0 分，因为那段音频是面试官开场白，
  与题目毫无关系。`EvaluationResultValidator` 只做 0–100 边界校验、没有默认值，所以 0 是模型自己的判断；
  换成贴题回答后立刻变成 85。
- **真实链路冒烟 24 项断言全过**：
  `pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady -MediaPath data/verify-clip.mp4`。
- **冒烟脚本已扩展**：新增 `-MediaPath <文件>`（用真实素材，假字节过不了 ffmpeg）与
  `-AnalysisTimeoutSeconds <秒>`；同时修掉一个真缺陷 —— 轮询原先只等 `pending`，而真实 ASR 是异步的、
  会先进入 `processing`，旧逻辑会把「还在处理」当成「已结束」直接判失败。
- **接口事实**：创建练习时题目必须由客户端给定（`questions.size()` 必须等于 `questionCount`，否则 400）；
  AI 出题是独立接口 `POST /api/v1/practice-sessions/question-plan`。
- 本地留了验证脚本 `data/real-verify.ps1`（`data/` 已 gitignore，不进版本库），用「第 2 题的播报 TTS
  当第 1 题的答案音频」在没有真人录音的情况下验证评分链路。
- 留了一个可查看的会话 `23591550-e275-49ad-9c28-3b407ec02693`（报告 ready，含真实转写与评分）。

### GitHub issue #10 / #11 / #12 的实现与验证（2026-10-13）

三个 issue 都判定为「合理且可实现」，已全部实现并逐条验证。

**#10 分析队列：可观测性、重试与死信（DLQ）**

- 指标（`spring-boot-starter-actuator` + Micrometer Prometheus，`/actuator/prometheus`）：
  `analysis_queue_length`、`analysis_processing_latency_ms`、`analysis_failure_rate`、
  `analysis_retry_count_total`、`analysis_dlq_size`，以及 enqueued/completed/failed/dq 计数器。
- 失败改为**指数退避**（`retry-backoff-ms` × 2^(n-1)，上限 `retry-backoff-max-ms`），
  调度器只领取 `next_attempt_at` 已到期的任务（`V4__analysis_queue_observability.sql` 加列 + 索引）。
- 重试到上限后写入新的 `analysis_jobs_dlq` 表；`analysis_jobs` 那一行保持 `failed`，
  报告页照旧显示 `analysis_failed`。人工恢复接口：`GET /api/v1/analysis/dlq`、
  `POST /api/v1/analysis/dlq/{jobId}/requeue`（按数据归属过滤，没有角色模型）。
- 日志补上 job/session/answer 维度，失败原因仍走 `LogSanitizer` 脱敏。
- **运行时验证**：把 `FFMPEG_PATH` 指向不存在的路径强制 ASR 失败 →
  报告 `analysis_failed` 且带可读原因；DLQ 出现 1 条；重入队后调度器再次领取并再次失败
  （`moved_at` 变为新时间，证明旧条目被删除后重建）；
  Prometheus 指标同步变化：`enqueued=1`、`failed=2`、`dlq=2`、`retry=1`、`dlq_size=1`、`failure_rate=1.0`。
- 单元测试：退避时间、DLQ 转存、重入队、指标计数（`AnalysisJobServiceTest` 19 个用例）。

**#11 真实摄像 + 真实模型的单会话端到端**

- 新增 `scripts/e2e-real-providers.ps1`：校验凭据与后端健康 → 选素材（`-MediaPath`，
  否则从 `docs/step*.mp4` 截 15 秒）→ 跑完整闭环 → 把报告 JSON 写成审计产物
  （`artifacts/e2e/`，已 gitignore）。`scripts/smoke-test.ps1` 增加 `-ReportOut` 与 `-KeepSession`。
- 新增可选 CI：`.github/workflows/e2e-real-providers.yml`（`workflow_dispatch`，
  读 `ALI_API_KEY` secret，上传报告 artifact）。**尚未在真实 runner 上跑过**，需要先在仓库配 secret。
- `POST /answers/{id}/media` 与 `POST /answers/{id}/complete` 都会入队分析，前端两条路径
  （录完一题、结束整场）都调用了，**没有发现 race 或漏入队**，不需要改动。
- **运行时验证**：本机真实凭据下 E2E PASSED —— 报告 `ready`、已评分 2/2、
  转写为真实中文内容、审计产物落在 `artifacts/e2e/report-*.json`。
- 诚实说明：**浏览器真摄像头**（权限拒绝/设备不存在、录制中断与断网恢复）仍属人工验收，
  自动化脚本用的是「真实录像文件 + 真实模型」；两者都已各自验证。

**#12 分片上传与断点续传**

- 后端 `practice/media/`：`ChunkedUploadService` + `ChunkUploadController`，接口
  `GET /answers/{id}/media/upload`（查询已收分片）、`PUT /answers/{id}/media/chunks/{index}`、
  `POST /answers/{id}/media/merge`、`DELETE /answers/{id}/media/upload`。
- 上传标识复用 `answerId`，因此**不需要额外的上传会话表**；分片落在
  `uploads/{answerId}/chunk-N`，合并时按序拼流写入正式 key，成功后删除临时分片并入队分析。
- **完整性校验**：合并前按序算整段 SHA-256，不匹配返回 400 且**不覆盖**已有媒体、保留分片供补传。
- 单段上传接口保持不变，前后端都可回退（前端遇到 404/405 自动退回单段）。
- 前端 `web/src/services/chunkedUpload.js`：4MB 分片、先查服务端已有分片再补传（刷新后可续传）、
  整段 sha256；`SessionView` 按大小自动选择分片或单段上传。
- **运行时验证**：3 片素材只传 0、2 → 查询显示 `[0,2]` → 补传后 `3/3 ready` →
  合并成功（305474 字节）→ 临时分片清零 → 错误 sha256 被 400 拒绝 →
  结束练习后报告 `ready`，转写长度 60（真实 ASR），说明合并后的媒体确实进入了分析流水线。
- 测试：后端 `ChunkedUploadServiceTest` 6 个用例；前端 `chunkedUpload.test.js` 8 个用例。

当前规模：后端 **101 个测试**（原 90 + 11 新增）、前端 **33 个 Vitest 用例**（原 25 + 8 新增）。

### 2026-10-13 追加修复：使用体验

- **主按钮文字看不清（真缺陷）**：`.primary-button` 只覆盖了背景色，没有覆盖文字色，
  Element Plus 的默认深灰 `#606266` 压在中绿 `#287b63` 上对比度只有 **1.19:1**
  （WCAG AA 要求正文 ≥ 4.5:1），五个页面的主按钮都受影响。补齐
  `--el-button-text-color/-hover/-active/-disabled` 后为白色 **5.12:1**，合格。
- **配置页选项缺说明**：「面试官风格」原来是三个 10px 的小标签、没有任何解释；
  现在与「面试类型」共用同一套卡片样式，标题 12px、说明 11px，并各配一句可执行说明
  （如「直接追问：抓住回答里的模糊点继续深挖细节」）。
- 「面试类型」的副标题由「经历 · 协作 · 复盘」这类词云换成具体例子
  （如「讲经历与协作，例：和同事意见不合怎么处理」）。
- 附带修掉长说明会被 Element 的 `white-space: nowrap` 挤成一行溢出的问题；
  窄屏改为单列，不再把说明文字隐藏掉。

### 2026-10-13 新增功能：用户自助注册

在此之前账号只能手工 `INSERT` 进 `candidates` 表，除了内置的冒烟用户没人能登录。

- **后端**：`POST /register`（`RegistrationController` → `CandidatesService.register`）。
  用户名 2–80 字符（中文/字母/数字/`_ . -`），密码至少 6 位；注册成功直接签发 token。
- **口令安全**：引入 `spring-security-crypto`，口令用 **BCrypt** 存哈希（此前是明文比对）。
  历史明文账号仍可登录，并在**登录成功后就地升级为哈希**，无需人工迁移。
- **唯一性**：`V5__candidate_registration.sql` 在 `candidate_name` 上建唯一索引——
  实体早就写着 `unique = true`，但 V3 只建了普通索引，两边一直不一致；并发注册同名时
  唯一索引兜底，服务层把 `DataIntegrityViolationException` 转成 409。
- **顺带修的隐患**：`Candidates` 的主键原本用 `GenerationType.IDENTITY`，而 Flyway 建的表只是
  `DEFAULT nextval(...)`（不是 identity 列）。改成 `SEQUENCE + interviewer_id_seq` 后
  JPA 插入才正常——注册之前从没走过 JPA 插入这条路，所以一直没暴露。
- **前端**：登录页加「登录 / 注册」页签（`auth-switch`），注册含确认密码与可选邮箱；
  `tryLogin` / `tryRegister` 收敛到 `services/api.js`；Vite 增加 `/register` 代理。
- **运行时验证**（真实 HTTP）：
  | 场景 | 结果 |
  | --- | --- |
  | 注册新用户 | 200，返回 token，`userId=2` |
  | 用新账号登录 | 200 |
  | 错误密码 | 403 |
  | 重复用户名 | 409 |
  | 密码不足 6 位 | 400 |
  | 老账号 `smoke/123456` | 200（明文自动升级） |
  | 通过 Vite 代理注册 | 200，`userId=3` |

  数据库核对：两条记录的 `invitation_code` 均为 60 字符 `$2a$10$...` BCrypt 哈希。
- 测试：后端 `CandidatesServiceTest` 9 个用例；前端 `api.test.js` 新增 5 个登录/注册用例。
- **已知限制**：注册接口没有验证码与频率限制，公网部署需要在网关层加限流（已写入 DEPLOYMENT.md）。

### 2026-10-14 体验修复（用户反馈 4 项）

1. **表单竖列没对齐**：登录/注册表单没有固定 `label-width`，「用户名」「密码 / 邀请码」
   「邮箱（可选）」宽度各不相同，输入框左边缘参差不齐。已改为
   `label-position="left" label-width="88px"`，标签统一左对齐且字号升到 12px。
2. **题目重复显示、上面那份还看不全**：数字人字幕原来绑定的是**整段问题文本**
   （`narrationCaption = narration.text`），长句溢出 195px 高的画面、被 `overflow: hidden`
   裁掉并压在头像上；同一句话又完整出现在下方问题卡片里。
   现在字幕只显示朗读状态（正在准备 / 正在朗读 / 语音不可用 / 可重播），
   **问题正文只在下方卡片出现一次**。
3. **数字人做成动画**：待机时轻微上下浮动 + 偶尔眨眼，朗读时嘴型（已有）与声波一起动；
   浮动用独立的 `translate` 属性而不是 `transform`，避免覆盖窄屏的 `transform: scale(.85)`；
   另外加了 `prefers-reduced-motion` 兜底。
4. **评分口径不透明**：报告页只给数字不给尺度，用户看到 10 分以为是「勉强及格」。
   现在评分面板下方有一段「评分口径」：80-100 表现良好 / 60-79 基本合格 / 40-59 需要加强 /
   0-39 明显不足，并说明分数衡量的是这一段回答本身。
   同时收紧提示词：明确区间定义；要求 overallScore 与维度分基本一致（不允许维度分很低、
   综合分却偏高）；「没有经验」这类无实质内容的回答按 relevance/completeness 0-20、
   STAR/logic 记 0 处理，且改进建议必须写明「这类回答拿不到分，应给出可迁移的经验或思路」。

   **真实调用验证**（同一个「我没有这方面经验」的回答，qwen-max）：

   | | 旧口径 | 新口径 |
   | --- | --- | --- |
   | 综合分 | 20 | **15** |
   | 相关性 / 完整度 / 专业度 | 10 / 10 / 10 | 10 / 20 / 20 |
   | STAR / 逻辑 | 0 / 0 | 0 / 0 |
   | 改进建议 | 「直接回答问题…」（泛泛） | 「这类回答拿不到分，应先给出可迁移的经验或解题思路」 |

### 2026-10-14 修复：面试官语音一直「暂不可用」

**症状**：进入面试页，数字人字幕显示「语音暂不可用，请阅读下方问题」，小人也不动。

**根因**：`<audio src="/api/v1/.../narration/audio">` 这样直接播放时，浏览器**不会**
给 audio/video 元素带 `Authorization` 头，而这个接口要求 Bearer Token，所以稳定返回 401；
audio 元素触发 `error` → 前端把 `narrationAudioFailed` 置为 true。

注意：这不是这次改动引入的。之前验证 TTS 时用的是带 header 的 HTTP 调用
（冒烟脚本、直接 `Invoke-WebRequest -Headers`），浏览器播放这条路其实一直是坏的；
直到这次字幕改成显示朗读状态才把它暴露出来。

**实测证据**（同一个会话、同一个音频地址）：

| 请求方式 | 结果 |
| --- | --- |
| 不带 Authorization（等价于 `<audio src>`） | **401** |
| 带 Authorization：`Bearer <token>` | **200，188444 字节，`audio/wav`** |

**修复**：新增 `api.js#getNarrationAudio`，用 `fetch` 带上 token 取回音频 Blob，
再用 `URL.createObjectURL` 生成 `blob:` 地址交给 audio 播放（离开页面或切题时 revoke）。
接口本身继续保持鉴权，不动权限模型。

**顺带把动画做明显**：待机浮动从 4px/4.6s 调到 6px/3.6s，说话时声波线条也跟着脉动；
朗读中的口型动画（`avatar-talk`）本来就存在，只是之前因为没有音频、`speaking` 一直为 false
才看不到。添加了 `prefers-reduced-motion` 兜底。

### 2026-10-14 重做面试官面板（布局 + 形象）

**布局**：原来是一整条 195px 高的横幅，小人用 `left: 22%` 绝对定位，中间留了一个纯装饰的
空白方框（`::before` 画的），右侧浮着字幕——大面积留白，没有信息层次。
现在改成两栏：左侧 178px 的头像卡（圆角 + 径向渐变底），右侧是
「AI 面试官 · 风格」/ 朗读状态 / 重播按钮 / 提示，整块填满，空装饰框已删除。

**形象**：原来的小人是用一堆 `div` 拼的（头发色块 + 两个点当眼睛 + 圆角矩形当身体），
所以看起来不像人。现在换成内联 SVG 绘制的扁平职员形象：耳朵、眉毛、带高光的眼睛、
眼镜、鼻梁、嘴巴、白衬衫 + 西装翻领 + 领带，胸前还有工牌；配色沿用项目的沉稳绿色系。

**动画**：朗读时嘴型（`scaleY` 动画）与整体轻微点头同步，右侧四条声波跟着起伏；
待机时缓慢呼吸；`prefers-reduced-motion` 下全部关闭。

说明：这台环境里的内置图像生成工具不可用（CLI 回退需要 `OPENAI_API_KEY`），
  所以这一版是**矢量形象**而不是 AI 真人照片。想换成写实照片时，替换
`.portrait-frame` 里的 `<svg>` 为 `<img>` 即可，布局与动画都不受影响。

### 2026-10-14 新模块：笔试（第一阶段：沙箱与数据模型）

按确认后的方案开工（本机 Docker 沙箱 / Java+Python+C+++JS+Go / 以运行结果为准 /
AI 出题带自检 / 独立模块 / 五种题型 / 学习场景不做防作弊 / 编译 10s·运行 5s·256MB·禁网·并发 2 /
隐藏用例只报"第 N 个未通过" / 与视频一样保留 7 天）。

**已完成并验证**

1. **代码沙箱** `coding/CodeRunnerService`：在本机 Docker 里执行，安全边界全部落在
   `docker run` 参数上——`--network none`、`--memory/--memory-swap`、`--cpus 0.5`、
   `--pids-limit 64`、`--read-only` + `--tmpfs /tmp`、只挂载当次临时目录、`ulimit -f` 限制写盘、
   整体超时后 `docker kill`。编译与运行分别用 `timeout` 限时（只做容器级总超时的话，
   一个死循环会一直耗到总超时才被杀）。输出写文件而不是读管道，避免缓冲区写满死锁，
   超时也能拿到部分输出。
2. **语言矩阵** `coding/CodeLanguage`：

   | 语言 | 镜像 | 体积 |
   | --- | --- | --- |
   | Java | `eclipse-temurin:21-jdk-alpine` | 364MB（已拉取） |
   | Python | `python:3.12-slim` | 119MB（已拉取） |
   | JavaScript | `node:20-alpine` | 136MB（已拉取） |
   | C++ | `gcc:13` | 约 1.4GB（首次执行时拉取） |
   | Go | `golang:1.22-alpine` | 约 250MB（首次执行时拉取） |

3. **真实执行验证**：`mvn -pl consumer test -Dtest=CodeRunnerServiceTest -Dcode.runner.integration=true`
   → **7/7 通过**，包含 Python 读 stdin、JavaScript、Java 编译错误单独回显、
   **死循环被判超时**、**沙箱内联网被阻断**（用例里尝试连 1.1.1.1:53，结果为 BLOCKED）。
   默认不跑这些用例（CI 没有 Docker daemon），用 `-Dcode.runner.integration=true` 打开。
4. **数据模型** `V6__coding_assessment.sql`：`coding_sessions` / `code_questions` /
   `code_submissions` 三张表，五种题型共用一张题目表，靠 `type` 区分各题型用到的列。
   已在真实库上应用成功（schema v6）。
5. **开关**：`practice.code-runner.enabled` 默认 **false**（会在本机执行任意代码，
   默认关闭避免误部署到公网）；本机 `.env` 已显式打开。

**决策记录（在你确认的范围内做的两个补充）**

- **SQL 题用 SQLite 执行**，复用已拉取的 Python 镜像（内置 `sqlite3`），不额外引入
  MySQL/Postgres 镜像；题目里会声明方言是 SQLite。需要真 Postgres 语义时再换。
- **题目/提交分表存储**，题目的参考解与测试用例都落库，便于复现与重判。

**下一阶段**（未开始）：判题服务（五种题型）、AI 出题 + 参考解自检闭环、REST 接口、
前端 Monaco IDE 与答题/报告页面。

### 2026-10-14 笔试模块（第二阶段：判题 + 出题 + 接口 + 前端，已完成）

- **判题** `CodeJudgeService`：上机与 SQL 跑沙箱（用户确认的「以运行结果为准」），
  填空按候选答案比对（忽略多余空白、保留大小写），选择比对选项集合，简答才交给模型按要点评分。
  「运行」只跑可见示例且**不落库**，「提交」跑全部用例（含隐藏）并计分；隐藏用例只回传是否通过。
- **AI 出题** `CodingQuestionPlanService`：一次生成整批题并逐题校验。
  这里踩了一个坑并已修正——最初要求模型同时给出「参考解」和「手算的期望输出」，
  结果三次都没通过自检（模型很难把输出逐字符算对）。现在改成模型只负责
  「输入 + 一个能跑的参考解」，**期望输出一律由参考解在沙箱里跑出来**，
  用例与参考解天然一致，自检只需回答「参考解能不能跑完」；跑不通就换一批重试（默认 3 次）。
  参考解连一个非空输出都给不出来时直接判该题无效。
- **防泄题**：题目视图（`CodeQuestionView`）只带题面、起始代码、可见用例，
  填空只给 `hint` 不给 `accepted`，`answerJson` 永不下发；已用接口实测确认字段里没有 answer。
- **接口**：`POST/GET /api/v1/coding-sessions`、`GET /{id}`、`POST /{id}/questions/{qid}/run|submit`、
  `GET /{id}/report`、`DELETE /{id}`、`GET /capabilities`。鉴权与数据隔离沿用 OwnerResolver。
- **保留期**：与视频一致 7 天，定时清理会话、题目与提交。
- **前端**：Monaco 编辑器（按需懒加载，含 Java/Python/C++/JS/Go/SQL 高亮）、
  新增「笔试练习」入口与三个页面（出题配置 / 答题 / 报告），
  「运行示例」与「提交本题」分离，隐藏用例只显示「用例 N 未通过」。

**端到端实测**（真实模型 + 真实沙箱）：

| 步骤 | 结果 |
| --- | --- |
| AI 出一道 Python 上机题 | 18 秒成功，期望输出 `4.50` 由参考解跑出 |
| 提交正确解 | `passed`，3/3 用例，100 分 |
| 提交只打印 `0.00` 的错误解 | `failed`，1/3 用例，33.3 分（隐藏用例 2 恰好是全零输入，判分区分度正常） |
| 运行示例（不落库） | 1/1 通过，报告里不计入 |
| 题目视图字段 | 无 `answer`，只含可见用例 |

测试规模：后端 **126 个**（新增判题 6 个；Docker 用例默认跳过）、前端 **38 个** + 构建通过。

### 2026-10-14 笔试模块（第三阶段：真实使用中暴露的 5 个缺陷，全部已修）

从「点一下就转圈」到「多选题报错」，实际点界面又挖出 5 个 bug，根因**全是「模型产出格式」与
「代码消费格式」对不上**——这也是这个模块最容易出错的地方，记录下来备查。

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 出题一直转圈 | Java 源码固定写入 `Main.java`，但提示词从没说过，模型写 `public class Solution` → javac 直接报「class Solution is public, should be declared in a file named Solution.java」 | 提示词写死每种语言的文件名与入口约定（`Main.java`+`class Main`、`main.cpp`、`package main`+`func main` 等） |
| 2 | 单个坏用例废掉整道题 | 模型给的隐藏边界用例 stdin 为空，而参考解要读一行 → `NoSuchElementException` | 参考解跑不通的**单个**用例直接丢弃，前提是仍保留可见示例且可用用例 ≥ 2 |
| 3 | 出题客户端不按 schema 输出 | `ChatClient.Builder.defaultSystem()` **修改自身并返回同一实例**，两个客户端共用一个 builder 时第二个提示词覆盖了第一个 | 两个客户端各 `clone()` 一份（旧代码 `ProgramAssistant` 正是这么做的） |
| 4 | SQL 参考查询跑不通 | 把 JSON 转义后的 SQL 塞进 Python 字面量，Python 先还原 `\n` 成真实换行，`json.loads` 收到非法 JSON | SQL 改用 **base64** 传递，无转义歧义 |
| 5 | 提交多选题报「题目数据损坏」 | 参考答案在库里是对象 `{"keys":[...],"points":[...]}`（提示词定的 schema），判题却按**裸数组**解析 | 判题改为从对象里取 `keys` / `points`；单元测试也改成与线上一致的格式（原来用裸数组，所以没拦住） |

另外补了两个可观测性改进，正是它们让上面几个问题能被定位：

- **校验失败时打印模型原始返回**（前 600 字）——#3 就是靠这个才看到模型返回的是
  `{"name":"coding / java","description":...}` 而不是约定 schema。
- **前端显示真实等待秒数**，并说明「最多重试 3 次，可能要等 20 秒到 2 分钟」，避免误以为卡死。

**端到端复验**：Java 上机 + SQL + 选择题三题一批，**92 秒**生成成功，两类需要执行的题都通过了沙箱自检；
选择题用夹具实测「正确 `A,D` → passed 100 分」「错误 `A,B,C` → failed 0 分并提示正确选项」。

### 2026-10-14 新增：每个用户可选页面主题色

- 界面强调色本来就走了 CSS 变量（`--green` / `--green-dark` / `--green-soft` 共 **58 处**），
  所以换主题只要覆盖这几个变量，**不用改任何组件样式**。硬编码的颜色里绝大多数是中性灰
  （文字/边框/浅底），与主题无关。
- 提供三种配色：**松绿**（默认）、**藏青**、**赭橙**。选择器在左侧栏底部，三个色点。
- **按用户持久化**：`V7__user_theme_preference.sql` 给 `candidates` 加 `theme` 列；
  `GET/PUT /api/v1/me/preferences` 读写，非法值返回 400。没登录或离线时退化为
  localStorage，登录后再用服务端的偏好覆盖——启动时先应用本地记录，避免闪一下默认色。
- 实测：默认 `green` → PUT `blue` → 再读仍为 `blue` → PUT `amber` 成功 → PUT `rainbow` 返回 400。

### 2026-10-15 面试官升级为真人数字人

**素材落在哪**：组合图原本**不在项目里**（只在 `C:\Users\xgwan\Downloads\`）。
已复制到 `web/public/assets/avatar/avatar-expression-sheet.png`——`web/` 才是前端根目录，
Vite 的静态目录是 `web/public/`，前端按 `/assets/avatar/xxx.png` 访问。

**素材处理**（`scripts/split-avatar-sheet.py`，可重复执行，不手裁）：

- 原图 1674x940 RGBA，**真透明**（透明区 `(0,0,0,0)`，不是棋盘格）；
- 切出 12 张表情/状态帧 + 1 张大图，输出 340x260 透明 PNG；
- 处理中化解了三个坑：
  1. 卡片底是**半透明白**（alpha 60-140），按阈值硬切会留灰底 → 改为线性重映射压成透明；
  2. **左侧大图的手臂在第 2、3 行伸进了第 1 张卡片的矩形**，初版因此把手臂也裁了进去
     （mouth-o 宽度等于整条列带）→ 改为按连通域从人脸处抠出人物；
  3. 各卡片的人脸位置不同 → 统一按人脸高度缩放、按"人脸顶边 + 躯干中心"锚定。
- 对齐质量（脚本输出的帧实测）：**12 帧人脸顶边极差 2px**；说话循环 4 帧脸心极差 9px；
  透明区 56-61%、实心 38-42%、过渡像素仅 0.5-2.6%（说明没有卡片底残留）。
- 预览图 `docs/images/avatar-crop-preview.png`（棋盘格底，一眼能看出残留/偏移）。

**前端实现**：

| 文件 | 说明 |
| --- | --- |
| `services/avatarAssets.js` | 帧清单、说话循环表、**预加载**（避免切换时闪白） |
| `services/avatarState.js` | **状态的唯一来源**：IDLE/PREPARING/SPEAKING/LISTENING/RECORDING/FOLLOW_UP/ERROR，含错误与"自动播放被拦"标记 |
| `services/avatarAudio.js` | 单例 `HTMLAudioElement` + **只创建一次**的 `MediaElementAudioSourceNode`（重复创建会抛错），提供 play/pause/stop/音量 |
| `services/mouthAnimation.js` | 音量→档位的纯函数（可单测） |
| `components/DigitalHuman.vue` | 渲染帧、按音量切嘴型、预加载、轻微浮动；**只读状态不写状态** |

动画细节：最小驻留 90ms（不几十毫秒暴力切图）、同档位内轮换（不只用两张图来回跳）、
`requestAnimationFrame` 只启动一次、页面切后台回来会重置计时；
整体只做极轻的 `translate` 浮动，**不用缩放伪造说话动作**。

**真实浏览器验证**（`web/scripts/capture-session-avatar.mjs`，用 Chrome 假摄像头走完整流程）：

| 检查项 | 结果 |
| --- | --- |
| 数字人渲染 | `img.dh-frame` 存在，素材 340x260，页面渲染 211x162，`complete=true` |
| 头像区实际画面 | std=53.8、最暗值 2、深色西装 7.3% + 肤色 6.5%（确认是真人照片，不是空白） |
| 朗读中嘴型在切换 | 40 次采样出现 **6 种不同帧**（mouth-normal / smile / e / m / a + 状态帧） |
| 音频结束自动闭嘴 | 播放结束后切到 `state-listening`，声波条停止 |
| 两张截图的差异 | 13,564 像素，集中在嘴部区域（x 594-860, y 172-333） |

**明确没做的**：音素级口型同步。嘴型只按**音量大小**分档，识别不了具体发音；
音量分析不可用时降级为定时轮换嘴型。这一点在代码注释、USER-GUIDE、ARCHITECTURE 里都写明了。

测试：前端从 38 增至 **52 个**（新增数字人状态机 7 个、嘴型逻辑 7 个）。

### 2026-10-15 修复：形象下方多出一条「聆听状态」白条

**现象**：用户截图反馈，数字人下方有一条白色说明条（"聆听状态"）。那是组合图里
第 3 行卡片下方的**说明标签**被一起裁进来了。

**根因**：第 3 行的标签是**整卡宽度的不透明白底**，从 y≈828 一直铺到卡片底边，
而人物是画在标签**上面**的（中间露西装、两侧露白底）。我原来的行带取到 906，把标签包了进去。
第 1、2 行的标签是浅蓝胶囊（312-350 / 601-639）所以在行带之外，只有第 3 行中招。

**为什么不能"把白色抠掉"**：人物胸前是浅色衬衫（≈`(247,247,249)`），
和标签白（`(252,252,252)`）几乎同色，抠白会在胸口打洞。只能截在标签之前。

**修复过程中又暴露两个问题**（都已修）：

1. **裁剪改短后，人脸检测被截断**：原来按"裁剪高度的 62%"限制人脸搜索范围，
   第 3 行裁短后 0.62×149=92 会把脸截断，测出的脸高从 96 掉到 66、缩放翻倍（人物突然变大）。
   改成固定像素窗口（145px，覆盖整张脸但不含脖子）。中途试过"肤色宽度轮廓找下巴"，更不稳（`head-nod` 测出 11px）。
2. **逐帧缩放会在说话时"忽大忽小"**：逐帧测脸高有 ±10% 噪声（张嘴改变肤色区域），
   直接用会让嘴型在两张图之间跳动尺寸。改成**全帧统一缩放**（先量 12 帧取中位数 = 1.37）。
3. **身体长度不一致**：状态帧被标签截断，身体只能到 y≈242，嘴型帧本能到 300，
   切换时会看到身体缩回去 55px。把画布从 360x300 改成 360x248，差值降到 3px；后又按下面那条整体重做为 **340x260**。

**验证**（脚本 + 真实浏览器）：

| 检查项 | 结果 |
| --- | --- |
| 13 张素材底部白条 | **0–2 像素**（修复前是数百），即标签已完全去除 |
| 12 帧人脸顶边极差 | **2px**（71–73），对齐未受影响 |
| 12 帧身体底部 | 嘴型帧 247 / 状态帧 240–244，**差值 3px** |
| 浏览器实测聆听态 | 头像区底部 15 行接近纯白的像素最多 73/210（35%），逐行递减 = 背景渐变，非白条 |
| 说话时嘴型 | 40 次采样 5–6 种不同帧 |

另外新增了 `looks_like_label()` 兜底：裁剪结果底部若出现大面积不透明白底就直接报错，
以后换素材行带取多了会在生成阶段就拦住，而不是等装到界面上才发现。

### 2026-10-15 数字人动画自然度：眨眼 / 微摆动 / 口型平滑 / 待机视频

在修完白条之后做的一轮观感优化（用户选择的方向："先做待机视频 + 这三点"）。

| 项 | 实现 |
| --- | --- |
| 眨眼 | 组合图里**没有闭眼素材**，脚本合成了一帧 `eyes-closed.png`：把镜片内的眼睛区域换成脸颊肤色、中间画一条睫毛线（镜框留在原处）。静止时每 3-5 秒随机眨一次、每次 110ms |
| 头部微摆动 | CSS 绕脖子附近缓慢转 ±0.6 度；与上下浮动分别用 `transform` / `translate` 两个独立属性，互不覆盖 |
| 口型平滑 | 起音快、落音慢的一阶平滑（0.55 / 0.16）+ 分档迟滞（margin 0.3）+ 说完话 90ms 收尾过渡。原来直接用瞬时音量会让嘴型在阈值附近抖 |
| 待机循环视频（可插拔） | 放 `web/public/assets/avatar/idle-loop.mp4` 即启用：**非说话状态**播视频、说话时切回切帧。**文件不存在时静默退回静态帧**，不加文件也完全正常 |

**闭眼合成的位置验证**（这是最容易做歪的一步，所以说清怎么确认的）：

- 先用"非肤色列簇"定位镜片：左 x809-833、右 x856-880、y148-167（关于 x=844 对称）；
- 合成后与 `mouth-closed` 逐像素比对：差异 **1335 像素**，落在 x127-158 与 x191-222
  **两块左右对称的斑块**、垂直中心 y=128 —— 正好是画布上人脸高度 42% 处的眼睛位置。
  若坐标算错，差异会落在别处，这一步就能发现。
- 另出一张 5 倍放大对比图 `docs/images/avatar-blink-check.png`（上=原图睁眼，下=合成闭眼），
  **这张需要人工确认像不像**。

**浏览器实测**：

| 检查项 | 结果 |
| --- | --- |
| 静止 7 秒采样 | 出现 `state-listening` + **`eyes-closed`** 两种帧，捕获 **2 次**眨眼 |
| 说话中采样 40 次 | 4 种嘴型（平滑+迟滞后比原来的 5-6 种更克制，抖动减少） |
| 无视频文件时 | `<img>` 正常渲染，静默退回静态帧，无报错 |

测试：前端 **59 个**（新增平滑 3 个、迟滞 4 个）。

**仍没做的**：音素级口型同步。嘴型依旧只按音量分档，识别不了具体发音；
要做到真口型同步需要外部口型服务（见「下一步可选项」）。

### 2026-10-15 修复：人物裁得太放大（头大、看不到领带）+ 说话/聆听人物大小不一致

**用户反馈**："所有说话的图片都是大头像，没有领带的部分"，而原组合图里的卡片是带领带的。

**根因有两个，第二个是之前一直没查出来的**：

1. **裁得太放大**：人脸占画面宽度 41%，把领带挤到了画布外。原卡片里脸只占约 36%。
2. **行 3 的人物在源图里本身就是缩小版**（脸宽 76-77 vs 行 1/2 的 115-123，约 **0.65 倍**），
   而我一直用"人脸**高度**"衡量大小来统一缩放 —— 这个测量把脖子也算进去，
   结果 96 vs 88（只差 9%），把 30% 的真实差异吃掉了。
   **后果是说话用的是大人物、聆听用的是小人物**，切换时人物大小突变。

**修复**：

- 尺度指标改成**人脸宽度**（行 3 的 76 vs 行 1/2 的 118，真实反映出 0.65 倍）；
- 缩放**按行取中位数**而不是逐帧量（同一行尺度本来就一样，逐帧量有 ±7% 噪声，
  会让说话时人物忽大忽小）：行 1/2 用 0.92、行 3 用 1.45；
- 画布从 360x248 改为 **340x260**，人脸占宽降到 **33%**，嘴型帧的整幅裁剪（含领带）都进画面。

**验证**（输出素材逐张测量）：

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 14 帧脸宽极差 | 101–149（**差 30%**） | 106–113（**差 6px**） |
| 14 帧身体底部极差 | 240–247 | 243–254（差 11px） |
| 人脸占画面宽度 | 41% | **33%**（页面上实测 70/211） |
| 领带区域 | 被裁到画布外 | 在画面内（下半部深色区 x46-164） |

浏览器复验：说话 4 种嘴型、聆听态正常、静止 7 秒捕获 2 次眨眼，均无回归。

### 当前环境状态

- **容器在运行**：`postgres`（healthy）、`redis`（healthy）、`s3mock` 由 `docker compose up -d` 启动，
  跑完集成测试后仍未停（下次可直接复用，或 `docker compose down` 收掉）。
- **后端在运行**：8088，真实供应商（配置全部来自 `.env`），验证会话仍可访问。
  停止方式：`Get-Process java | Stop-Process`。
- **前端**：本轮未改动、未启动。
- **构建状态**（2026-10-10）：`mvn -B clean package` → 90 个测试、6 个跳过、BUILD SUCCESS；
  容器起来后 `-Ddb.integration=true -Ds3.integration=true` → 90 个测试、0 跳过、BUILD SUCCESS。
- 代码与文档完好，`git status` 有 **102 项未提交改动**（15 项删除、39 项修改、48 项新增），
  集中在 `consumer/`（70）与 `web/`（18）。下一次开工建议先决定提交策略，再动代码。

### 下次开工第一件事

1. 三条构建/启动基线都已经跑绿（见上），不用重复；
2. 四条真实模型链路也已跑通（见「真实供应商验证结果」）；
3. 真正剩下的都是**需要你输入**的：摄像头授权（两项验收）、Redis 去留、遗留接口去留 ——
   见下方「需要你处理的事项」；
4. 如果只是想先看效果：`docker compose up -d` → 直接 `java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar`
   （`.env` 会自动加载），演示账号 `smoke / 123456`。

### 附：CC Switch 报错与升级（与本项目无关，只做记录）

报错原文：

> CC Switch local proxy failed while handling Codex endpoint `/responses`.
> Provider: DeepSeek; model: `deepseek-v4-flash`; upstream_status: HTTP 400;
> cause: The `reasoning_content` in the thinking mode must be passed back to the API.

**含义**：`deepseek-v4-flash` 的思考模式会返回 `reasoning_content`，多轮对话时必须把它
原样回传；CC Switch 的本地代理在 Codex `/responses` ↔ DeepSeek 之间做协议转换时把它丢了，
DeepSeek 侧因此返回 400。这**不是** Light-Interview 的问题，是代理层的转换缺陷。

**本机现状**（2026-10-10 读取）：

- 程序：`%LOCALAPPDATA%\Programs\CC Switch\cc-switch.exe`，42.5 MB，文件时间 2026-10-08，
  没有写入版本信息（`FileVersion` 为空），所以只能按文件时间判断新旧。
- 配置：`C:\Users\xgwan\.cc-switch\`（`settings.json`、`live-state.json`、`cc-switch.db`、
  `logs\`、`backups\`）。`live-state.json` 显示 `apps.codex.mode = "proxy"`、`attached = true`。

**下次可选的处置，按代价从低到高**：

1. 在 CC Switch 里把 Codex 切成 `direct`（不经过本地代理），绕开转换层；
2. 换用不带思考模式的 DeepSeek 模型标识，让响应里不出现 `reasoning_content`；
3. 升级 CC Switch：需要联网，且要先确认上游有新版本修了这个透传问题；
4. 把 Codex 的 provider 直接指向 DeepSeek 官方端点，不再经过 CC Switch。

升级属于本机工具运维，且要联网下载安装包 —— 等你确认后再做。

### 上次离开时（2026-10-09）的环境

- **容器**：`postgres` / `redis` / `s3mock` 都在运行（`docker compose ps`）
- **后端**：8088，本地存储 + `stub` 转写/评分 —— 可直接体验完整闭环
- **前端**：5173（Vite dev server）
- **演示账号**：`smoke / 123456`
- **数据**：无残留（`sessions=0 / jobs=0`，本地媒体 0 文件）
- **版本控制**：本轮所有改动**仍未提交**（`git status` 约 78 项，含新增的
  `analysis` / `avatar` / `practice.plan` / `practice.resume` / `practice.security` /
  `practice.media` / `practice.storage` 包与三份文档）。下次开工可先决定是否提交或分组提交。

```bash
# 停止全部（数据卷保留，下次 up 即可恢复）
docker compose down

# 下次恢复
docker compose up -d
mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar      # 见下方环境变量
cd web && npm run dev
```

后端环境变量（无 Key 也能跑通闭环）：

```bash
AUTH_TOKEN_SECRET=change-this-development-secret
PRACTICE_ASR_PROVIDER=stub
PRACTICE_EVALUATION_PROVIDER=stub
PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000
PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000
```

### 恢复后建议的第一件事

**有 `Ali_API_KEY` 时**（唯一还能推进的运行时验证）：

1. 用真实供应商重启后端：把 `PRACTICE_ASR_PROVIDER` / `PRACTICE_EVALUATION_PROVIDER`
   改回 `dashscope` / `spring-ai`，并设置 `Ali_API_KEY`；
2. 跑 `pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady`；
3. 若分析失败，先看报告里的 `analysis.error`，再看 `analysis_jobs.last_error`（已脱敏），
   失败可用 `POST /api/v1/practice-sessions/{id}/analysis/retry` 重试。

**没有 Key 时**：改做需要摄像头的那两项验收（权限拒绝/设备不存在、录制中断与断网恢复）。

### 剩余待办

见上方「需要你处理的事项」，一句话概括（原 4 项，第 1 项已完成）：

1. ~~提供 `Ali_API_KEY`~~ —— 2026-10-10 已提供，四条真实链路验证通过
2. 授权我用摄像头做完剩余两项验收
3. 决定 Redis 采用还是移除
4. 决定遗留接口迁移还是删除

## 需要你处理的事项

| # | 事项 | 影响 | 你可以怎么做 |
| --- | --- | --- | --- |
| 1 | ~~**有效的 `Ali_API_KEY`**~~ **已完成** | 转写、评分、出题、TTS 四条真实模型调用已全部验证通过（见「真实供应商验证结果」） | —— |
| 2 | **摄像头授权** | 验收项「权限拒绝 / 设备不存在」与「录制中断、断网恢复」无法验证 | 允许我在浏览器里操作相机，或你手动走一遍 |
| 3 | **Redis 采用还是移除** | 它现在空转；分析队列在数据库里 | 说一声即可：接共享计数/队列，或从依赖与 compose 中删除 |
| 4 | **遗留接口迁移还是删除** | 旧功能（简历改写、编程题、候选人管理）依赖未被 Flyway 管理的表，调用即 500 | 二选一；删除我可以先给出完整清单 |

## 重构顺序

| PLAN 项 | 状态 | 证据 / 说明 |
| --- | --- | --- |
| 清理根 Maven 模块 | 已完成 | 模块为 `common` + `consumer`；`consumer-api` 已移除 |
| 统一 Java 21 / Spring Boot / Spring AI | 已完成 | Java 21、Spring Boot 3.4.0、Spring AI 1.1.8、Alibaba 1.1.2.4 |
| 移除 `SNAPSHOT` / `M` 版本 | 已完成 | 依赖树中已无里程碑或快照版本 |
| 修复 JWT / CORS / API 客户端问题 | 已完成 | CORS 来源可配、API 客户端解析统一错误体、登录过期不再误报后端故障 |
| 干净环境构建通过 | 已完成 | `mvn -B clean package`、`npm ci` + `npm run build` 均通过 |
| 统一应用边界（单一后端 / 单一前端） | 基本完成 | 后端只有 `consumer`，前端只有 `web`；旧 Vaadin/React 已从仓库移除（仅剩本地空目录） |
| 完成视频训练闭环 | 已完成 | 见下方产品流程 1–8 |
| 完成 AI 复盘 | 已完成 | 结构化评分、逐题/整体报告、时间轴证据 |
| 接入数字人 | 已完成 | 2D 半身 + TTS + 状态动画 + 不可用降级 |
| 清理与发布 | 基本完成 | Compose、环境变量、部署文档、数据删除、日志脱敏、存储清理；遗留接口待决定 |

## 产品流程

| 步骤 | 状态 | 证据 |
| --- | --- | --- |
| 1 岗位 / JD / 简历 | 已验证 | 浏览器实测；简历用真实 `.docx` 抽取正文（HTTP 200，45 字） |
| 2 类型 / 难度 / 风格 / 时长 | 已验证 | 浏览器实测 |
| 3 设备检测 | 已验证（授权通过路径） | 浏览器实测：摄像头/麦克风就绪、音量正常后才允许进入 |
| 4 AI 按简历与岗位出题 | 已实现·未验证 | 接口、Schema 校验与回退路径已验证；无 Key 时返回 503 并回退内置题库 |
| 5 逐题录制（暂停/重录） | 已验证 | 真实浏览器用摄像头录完 5 题并生成报告 |
| 6 后台异步存储/转写/分析 | 已验证 | 用本地兜底供应商跑通完整闭环；队列认领、重试、超时、持久化均已验证 |
| 7 逐题报告 / 回放 / 转写 / 时间轴证据 / 改进建议 | 已验证 | 浏览器实测点击证据跳转到视频时间点（`currentTime` 0 → 2.93 并继续播放） |
| 8 保留 7 天 + 立即删除 | 已验证 | 服务端定时清理与前端 `pruneExpired` 都实测过；删除后媒体与记录不可访问 |

## 版本化接口

PLAN 列出的 10 个接口全部实现，另有补充：

| 接口 | 状态 |
| --- | --- |
| `POST/GET /api/v1/practice-sessions` | 已验证 |
| `GET /api/v1/practice-sessions/{id}`、`/status` | 已验证 |
| `POST .../{id}/start` | 已验证（设备检测页调用，写入 `startedAt`） |
| `POST .../{id}/answers` | 已验证 |
| `POST /api/v1/answers/{id}/media` | 已验证（重传幂等覆盖；未知回答 404） |
| `POST /api/v1/answers/{id}/complete` | 已验证（未上传录像 409；会话未结束即可出分） |
| `GET .../{id}/report` | 已验证 |
| `GET /api/v1/media/{id}/signed-url`、`GET /api/v1/media/{id}` | 已验证（含 HTTP Range 206 与签名/过期校验） |
| `DELETE /api/v1/practice-sessions/{id}` | 已验证 |
| 补充：`.../question-plan`、`/api/v1/resume/extract`、`.../analysis/retry`、`.../narration` | 已验证 |

## 核心接口类型与供应商选择

`AsrProvider`、`TtsProvider`、`AvatarProvider`、`EvaluationProvider`、`MediaStorage`、`AnalysisJobRepository` 均已存在。

供应商通过配置选择（业务层只依赖接口）：

| 配置 | 取值 | 默认 |
| --- | --- | --- |
| `practice.asr.provider` | `dashscope` / `stub` | `dashscope` |
| `practice.evaluation.provider` | `spring-ai` / `stub` | `spring-ai` |
| `practice.media.provider` | `local` / `s3` | `local` |
| `practice.avatar.provider` | `local-2d` | `local-2d` |

本地兜底实现（`stub`）让「录制 → 转写 → 评分 → 报告」在没有云端凭证时也能完整跑通，
溯源标注为 `stub-local`，不会伪装成真实模型结果。

## 数据模型（与 PLAN 的偏差）

PLAN 列了 7 张表；实际用 3 张表 + JSON 列，属于有意的简化：

| PLAN 表 | 实际落地 |
| --- | --- |
| `practice_sessions` | 同名表，另用 `questions_json` 承载题目（一次读写，无需单独表） |
| `practice_answers` | 同名表 |
| `analysis_jobs` | 同名表 |
| `practice_questions` | 并入 `practice_sessions.questions_json` |
| `media_assets` | 并入 `practice_answers` 的 `media_*` 列（一题一段录像） |
| `transcripts` | 并入 `practice_answers` 的 `transcript_*` 列 |
| `evaluation_reports` | 并入 `practice_answers` 的 `evaluation_json` 列 |

报告审计字段全部落库并已断言：模型名与版本、Prompt 版本、分析时间、结构化评分、
**原始模型响应**、可定位视频时间点的证据。

## AI 复盘指标

10 项指标全部在报告页可见（浏览器实测）：相关性、完整性、专业性、STAR 结构、回答逻辑、
语速、停顿次数与总时长、口头禅数量、改进建议、优秀回答示例。评分结果走固定 JSON 结构校验，
自由文本会被拒绝。

## 数字人

2D 半身界面 + TTS + 状态（`idle/thinking/asking/listening/finished`）+ 振幅驱动口型。
合成不可用时接口返回 503，客户端隐藏播报控件、保留字幕；口型的振幅逻辑**只有构建验证**。

## 测试与验收

| PLAN 验收项 | 状态 | 证据 |
| --- | --- | --- |
| Maven 与前端干净构建 | 已验证 | 见复现命令 |
| 登录、Token 注入、权限与用户数据隔离 | 已验证 | 未认证 401、跨用户 404；登录失败 10 次后 429 |
| 摄像头/麦克风权限拒绝与设备不存在 | **待输入** | 需要相机权限 |
| 录制中断、上传重试和断网恢复 | 部分验证 | 重传幂等与未知回答 404 已在 HTTP 层验证；本地缓存合并与清理有 Vitest 用例；**UI 中断与断网路径仍需相机** |
| 视频播放、签名 URL 过期、数据删除 | 已验证 | 播放与证据跳转浏览器实测；过期单测；删除后 404；前端删除/过期清理逻辑另有 Vitest 用例 |
| ASR/评分超时与重复任务幂等 | 已验证 | 挂起供应商 + 300ms 超时单测；入队去重与失败重试往返实测 |
| 数字人 TTS 可用与不可用两条路径 | 已验证 | 有 Key：Sambert 返回 200 与真实音频；无 Key：音频端点 503 → 客户端隐藏控件 |
| 真实模型四条链路（出题 / TTS / ASR / 评分） | 已验证 | 2026-10-10 拿到 Key 后逐条跑通，见「真实供应商验证结果」 |
| 5 题模拟面试全流程 | 已验证（分两半） | 浏览器用真实摄像头录完 5 题并生成报告（当时用兜底供应商）；转写与结构化评分已用真实供应商单独验证。**同一场会话同时具备真人录像与真实模型评分，仍待摄像头授权** |

## 版本策略

| 要求 | 状态 |
| --- | --- |
| Java 21 / Node LTS | 已验证（Java 21.0.6、Node 当前本地版本） |
| 依赖升到最新稳定版 | 已完成（Spring AI 1.1.8 / Alibaba 1.1.2.4 / Spring Boot 3.4.0 / Vue 3.5 / Vite 6） |
| 禁止 `SNAPSHOT` 与里程碑 | 已验证（依赖树扫描） |
| 版本写入锁文件 | 已完成（`package-lock.json` 经 `npm ci` 校验） |
| 在 CI 中固定验证 | 已添加 `.github/workflows/build.yml`：后端 `mvn -B clean package`，前端 `npm ci` / `npm test` / `npm run build`。**尚未在真实 CI 上跑过**；需要 Postgres / S3 的集成用例没有纳入 CI |
| 密钥 / 存储 / 保留 / Provider 走环境变量 | 已完成（35 个配置项全部有文档） |

## 与 PLAN 的其他偏差

- **MinIO 改为 `adobe/s3mock`**：MinIO 官方镜像已从 Docker Hub 下架，`docker pull minio/minio` 返回 access denied。协议不变，换 provider 只需改 endpoint 与凭证。
- **Redis 未使用**：分析队列与认领在 PostgreSQL；限流计数在进程内存（多实例会失效）。见待决定事项 3。
- **前端页面合并**：`AnswerRecorder` 并入 `SessionView`，`PracticeProcessing` 并入 `ReportView` 的轮询，能力未减少。

## 复现命令

```bash
# 单元与集成测试
mvn -B clean package
mvn -B test "-Ddb.integration=true" "-Ds3.integration=true"   # PowerShell 下必须加引号

# 前端
npm --prefix web ci && npm --prefix web run build

# 端到端（兜底供应商，假字节即可）
pwsh scripts/smoke-test.ps1 -Name demo -Code <邀请码> -ExpectAnalysisReady

# 端到端（真实供应商：ASR 需要真实音轨，假字节过不了 ffmpeg）
pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady \
  -MediaPath data/verify-clip.mp4 -AnalysisTimeoutSeconds 300
```

当前规模：后端 **90 个测试**（默认跳过 6 个需要容器/外部服务的用例）、前端 **25 个 Vitest 用例**
（API 客户端 6 / 路由守卫 4 / 媒体捕获 5 / store 3 / 内置题库 7），
冒烟脚本 **24 项断言**（带 `-ExpectAnalysisReady`；不加该开关为 20 项），
真实供应商模式下同样 24 项全过。


### 2026-10-15 修复：说话与不说话的人物大小仍不一致

上一轮把尺寸指标改成"人脸宽度"并按行缩放之后，用户仍反馈两者大小不同。

**实测（同一次浏览器会话、同一布局下拍的两张图）**：

| | 说话中 | 聆听态 |
| --- | --- | --- |
| 脸宽 | 70px | 70px |
| 人物总宽 | 147px | **178px** |

**头是一样大的，差的是肩膀**。继续测源素材发现根本原因：

| 来源 | 脸宽 | 肩宽/脸宽 |
| --- | --- | --- |
| 行 1/2（嘴型帧） | 107-113 | **1.73-1.89** |
| 行 3（状态帧） | 110-113 | **2.60-2.70** |

**第 3 行的人物不是第 1/2 行的等比缩小**：头肩比例本身就差 48%。
这意味着"对齐脸则肩宽近一倍、对齐肩则脸小一圈"，**靠缩放不可能两全**。

**处理**：四个非说话状态改用第 1/2 行的嘴型帧表达
（LISTENING → `mouth-m`、RECORDING → `mouth-closed`、FOLLOW_UP → `mouth-smile`），
尺寸天然一致；状态差异由字幕文案与右上角指示灯表达。
那 4 张状态素材仍生成保留（供换素材对照），但不再预加载、不参与展示。

**验证**（同一次会话的两张截图）：

| | 说话中 | 聆听态 |
| --- | --- | --- |
| 脸宽 | 70px | 69px |
| 人物宽 | 147px | 155px（差异由 21% 降到 5%） |
| 人物高 | 141px | 135px |

剩余 5% 来自源素材各帧裁剪本身的细微差别（例如 `mouth-o` 的包围盒比 `mouth-closed` 宽 13px），
不是缩放问题。


### 2026-10-15 新功能：报告页让面试官念出「示范回答」

用户反馈：报告有分数但**没有例子**，不知道怎么改。评分里其实早就存了 `sampleAnswer`
（报告页也一直显示文字），缺的是**把它念出来**。

**实现**（复用现有播报链路，不新增模型调用）：

- 后端新增 `GET /api/v1/practice-sessions/{id}/questions/{n}/sample-answer` 与 `.../sample-answer/audio`；
  文本直接取评分时存下的 `sampleAnswer`，只做一次语音合成；合成不可用同样是 503。
- `AvatarNarrationRequest` 增加 `audioPath` 字段：原来音频地址在 Provider 里写死成 narration 路径，
  直接复用会把示范回答指向「念题目」的音频。
- 单次合成有长度上限，超长文本按**句子边界**截断（硬切会断在半句），
  响应里用 `truncated` 标记，前端提示「语音只念了前半段」。
- 前端：优秀回答示例面板加「让面试官念一遍」按钮 + 96px 的小数字人，嘴型跟着音频动。

**过程中发现并修掉一个真错误**：报告里的 `questions` **只包含有作答的题**（按题号排序），
所以数组下标 ≠ 题号。我最初按 `report.questions().get(questionIndex)` 取值会取错题，
改成按 `questionIndex` 字段查找。

**验证**：

| 检查项 | 结果 |
| --- | --- |
| 示意回答接口 | `audioAvailable=true`，`truncated=false`，`audioUrl` 指向 sample-answer 路径（不是 narration） |
| 音频合成 | 200、**2.2MB `audio/wav`** |
| 未带 token | **401**（音频仍然鉴权） |
| 越界题号 / 他人会话 | **404 / 404** |
| 浏览器实测 | 点击后进入播放态，小数字人在朗读过程中出现 **4 种嘴型**（normal/e/smile/m） |

测试：后端 126 → **129 个**（新增播报路径、截断与不截断 3 条）。


### 2026-10-15 全站字号：把散落的小字号统一抬到可读区间

**用户反馈**：字体偏小、看着费劲，要求"如果要改就整体统一改"。

**先量化**（全前端 `font-size` 声明统计）：

| 字号 | 处数 |
| --- | --- |
| 7px | 9 |
| 8px | **25** |
| 9px | **35** |
| 10px | **36** |
| 11px | 21 |
| 12px | 51 |
| 13px 及以上 | 其余 |

**共 126 处 ≤11px**。而且**报告页最严重**：`.sample-text`（示范回答正文）和题目列表的题干都只有
**8px**、副标题 7px —— 而其它页面的正文是 12px。所以不只是"整体偏小"，报告页比别处又小了一档。

**改法**：按统一字阶一次性替换（合并过密的档位，而不是各调各的），共 **216 处**：

| 原字号 | 新字号 | 说明 |
| --- | --- | --- |
| 7 / 8 | 11 / 12 | 微标签、脚注 |
| 9 / 10 | 13 | 报告页正文原本就在这一档 |
| 11 / 12 | 14 | 常规正文 |
| 13 / 14 | 15 / 16 | 小标题 |
| 15 以上 | 逐级 +1 | 大号数字（25/29/30px 的分数）保持不变 |

**回归检查**（新增 `web/scripts/check-text-overflow.mjs`）：逐个主要页面扫描
"overflow 为 hidden 但 scrollHeight/scrollWidth 超出容器"的元素，找出被裁切的文字：

| 页面 | 疑似裁切 |
| --- | --- |
| 训练总览 | 1（`.guidance-art`，见下） |
| 创建练习 / 练习记录 / 笔试 / 笔试答题 / 笔试报告 / 练习报告 | **0** |

唯一那处是装饰插画 `.art-person { bottom: -6px }` **故意**让小人底部探出容器外再被裁掉，
改动前后完全一致，与字号无关（顺带给装饰星形 `.art-spark` 固定了字号，
避免装饰图形跟着正文一起缩放）。

另外重新生成了 README 里的全部 11 张界面截图，避免"文档配图还是旧字号"。


### 2026-10-15 调整：示范回答改成「小人 + 按钮 + 逐句字幕」

**用户反馈**：原来的布局不合理（文字堆在上面、小人在最下面孤零零一个）。
期望是「左面小人、右面按钮；点按钮小人开始回答，小人下面出现字幕，最终文字显示完全」。

**改动**：

- 布局改成一行三块：左边 96px 数字人 → 右边「让它回答 / 停止」按钮与一句说明 → 字幕在两者下方；
- **字幕逐句出现**：按播放进度（`timeupdate`）决定已念到第几句，字符数占比近似时间占比
  （中文 TTS 两者相关性够高，且不需要词级时间戳）；
- 点击前字幕显示完整内容（随时可读）；点「让它回答」后从头逐句出现；
  播完或点「停止」都恢复成完整文字；
- 字幕切分按句末标点，过短的残句（"嗯。""好。"，短于 4 字）并进上一句，避免一个字单独弹一行。

**过程中修掉一个自己写错的逻辑**：合并条件写成了"上一句短就并入"，导致连续几个短句被并成一整段；
正确判据是"**当前这句**短则并进上一句"。

**验证**（真实浏览器）：

| 检查项 | 结果 |
| --- | --- |
| 字幕长度随朗读增长 | 采样 41 → 81 → **128**（完整示范回答 128 字） |
| 播放结束时 | 字幕显示完全，状态回到静止 |
| 嘴型 | 朗读过程中出现 4 种帧（e/smile/normal/m） |
| 布局位置 | 小人 x=1025、按钮 x=1133（**在右侧**）、字幕 y=1077（**在两者下方**） |

测试：前端 60 → **67 个**（新增字幕切分 3 条、进度映射 4 条）。


### 2026-10-15 再调整：示范回答「一开始不显示，念完才显示完整」

**用户反馈**：一开始就把全文摊开，就失去了"听面试官作答"的感觉；应该**初始看不见**，念完再显示完整。

**改动**：

- 初始状态只显示一句占位提示，**不显示示范回答正文**；点「让它回答」后从零逐句出现；念完/停止/失败都显示完整文字。
- 后端 `narrate()` 返回的 `text` 从"完整原文"改成"**实际会念出来的文本**"：
  字幕按播放进度逐句出现，若音频被截断（超长时）而字幕用完整原文，字幕会跑到音频前面。
  完整内容前端本来就有（评分里的 `sampleAnswer`），念完后展示那一份。
- 切题时重置为"未播放"状态（新题同样先不显示）。

**过程中我自己写错一处、被浏览器验证抓出来**：computed 里写成 `if (finished)` 而不是 `if (finished.value)`
—— ref 对象恒为真，导致**一开始仍然显示全文**。已修正，并顺手检查了同类写法（无其它遗漏）。

**验证**（真实浏览器）：

| 检查项 | 结果 |
| --- | --- |
| 点击前 | 只显示占位提示（29 字），`.sample-hidden` 存在，**正文不可见** |
| 播放中字幕 | **41 → 81 → 128**（完整示范回答 128 字） |
| 点击后 | 按钮变「停止」，嘴型 4 种帧 |
| 念完 | 字幕显示完整 |


### 2026-10-15 修复：练习中途「Invalid or expired token」

用户截图报错 `Invalid or expired token`。查下来是**两个真缺陷**，第一个是功能性的：

**缺陷 1：令牌只有 10 分钟，而且配置是死的**

```java
// Sign.getToken() 硬编码 600000ms
.withExpiresAt(new Date(System.currentTimeMillis() + 600000))
```

```java
// Sessions.loginUser() 算出 12 小时/30 天，然后**把结果丢掉了**
duration = LONG_SESSION;              // 算了
maxAge = (int) (duration / 1000);     // 也算完没用
return Sign.getToken(name, secret);   // ← 实际仍是 10 分钟
```

一轮模拟面试约 20 分钟，**10 分钟的令牌会在面试中途过期**——录到一半所有请求开始 401。

修复：

- `Sign.getToken` 增加 `ttlMillis` 参数并真正生效（保留 2 参重载，兜底 1 小时）；
- `Sessions.loginUser` 把算好的时长传下去，`rememberMe` 逻辑不再是死代码；
- 新增配置 `auth.token.ttl-hours`（默认 **12 小时**），登录与注册都按它签发。

**缺陷 2：前端不处理 401**，只弹一句 `Invalid or expired token` 把用户晾在当前页面，
之后每个请求继续失败，看起来像"网站坏了"。

修复：`api.js` 增加统一的 `handleUnauthorized()` —— 清掉本地凭证并跳回登录页，
带上 `redirect` 参数，登录后回到原来那一页；登录页本身不重复跳。
三处裸 `fetch`（面试官语音、示范回答语音、录像）也接上同样的处理。

**验证**：

| 检查项 | 结果 |
| --- | --- |
| 令牌有效期 | 解码 JWT：签发 12:49:18 → 过期次日 00:49:18，**12 小时**（原来 600 秒） |
| 坏令牌触发请求 | 自动跳到 `/login?redirect=%2Fpractice%2F...%2Freport` |
| 本地凭证 | `{"token":null,"userName":null}` 已清空 |
| 403 不被误判 | 单测确认 403 时本地凭证保留 |

测试：前端 67 → **69 个**（新增 401 清凭证、403 不误判两条）；
后端 **129 个**。新增 `web/scripts/check-token-expiry.mjs` 作为可复现的验证脚本。

**权衡**：12 小时的令牌存在 localStorage 且服务端没有吊销机制，属于"够用的开发期方案"；
真要多租户上线，建议换成「短访问令牌 + 刷新令牌」或服务端会话表。

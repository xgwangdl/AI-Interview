# 🎯 Light-Interview —— 开源 AI 面试训练系统

**视频面试 + 笔试上机，一条龙跑完：AI 出题 → 浏览器录制 → 语音转写 → 结构化评分 → 可回放的复盘报告。**
而且它**真的会运行你写的代码** —— 跑不过用例，就是不过。

[English](docs/README.md) · [使用手册](docs/USER-GUIDE.md) · [项目架构](docs/ARCHITECTURE.md) · [部署说明](docs/DEPLOYMENT.md) · [实施状态](docs/STATUS.md) · [简历功能需求](docs/RESUME-REQUIREMENTS.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1-6DB33F.svg)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.5-42B883.svg)](https://vuejs.org/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://makeapullrequest.com)

## 📸 看看它长什么样

| 登录与注册 | 训练总览 |
| --- | --- |
| ![登录与注册](docs/images/01-login.png) | ![训练总览](docs/images/02-dashboard.png) |

| 练习配置（AI 出题） | 笔试出题（选题型与语言） |
| --- | --- |
| ![练习配置](docs/images/03-practice-setup.png) | ![笔试出题](docs/images/04-coding-setup.png) |

| 笔试作答（网页 IDE） | 笔试报告（按用例计分） |
| --- | --- |
| ![笔试作答](docs/images/05-coding-exam.png) | ![笔试报告](docs/images/06-coding-report.png) |

| 模拟面试（真人数字人面试官） | 面试官说话中（嘴型随音量变化） |
| --- | --- |
| ![面试间](docs/images/07-session-avatar.png) | ![朗读中](docs/images/08-session-avatar-speaking.png) |

| 训练档案与错题本（顶栏还有今日 AI 额度） | 错题一键重练 |
| --- | --- |
| ![错题本](docs/images/12-mistake-book.png) | ![重练](docs/images/13-mistake-retry.png) |

| 外语口语（英语） | 一轮一句：先接话，再纠错 |
| --- | --- |
| ![外语练习](docs/images/14-language-setup.png) | ![外语对话](docs/images/15-language-session.png) |

| 求职目标与今日训练 | 目标详情与今日 3 题 |
| --- | --- |
| ![目标首页](docs/images/16-dashboard-goal-today.png) | ![目标详情](docs/images/17-goal-detail.png) |

| 连续面试 | 转写校正后重评分 |
| --- | --- |
| ![连续面试](docs/images/18-continuous-interview.png) | ![转写校正](docs/images/19-transcript-correction.png) |

| 回答改写 | before / after |
| --- | --- |
| ![改写](docs/images/20-answer-rewrite.png) | ![版本对比](docs/images/21-before-after.png) |

| 本周报告 | 提醒设置 |
| --- | --- |
| ![周报](docs/images/22-weekly-report.png) | ![提醒](docs/images/23-notification-settings.png) |

| 学习小组（助教） | 学习小组（学员） |
| --- | --- |
| ![助教看板](docs/images/24-cohort-coach.png) | ![学员作业](docs/images/25-cohort-learner.png) |

| 助教反馈 | 匿名互评 |
| --- | --- |
| ![求助反馈](docs/images/26-cohort-help-feedback.png) | ![互评](docs/images/27-peer-review-form.png) |

## 😰 是不是也遇到过这些

- 题刷了几百道，一问到「讲讲你主导的项目」就卡壳
- 想练手，但没人给你出题，更没人给你反馈
- 试过的 AI 面试工具只能聊天，**不敢真跑你的代码** —— 写错了也看不出来

## ✨ 这个项目不一样在哪

| | |
| --- | --- |
| 🎥 **能录下来，也能被真实评分** | 浏览器逐题录制作答 → Paraformer 转写 → 大模型按**固定 JSON 结构**打分（相关性 / 完整性 / 专业性 / STAR / 逻辑 + 语速 / 停顿 / 口头禅）。报告里每条结论都能**点回视频对应的时间点** |
| 💻 **笔试不自欺：代码真的会跑** | Java / Python / C++ / JavaScript / Go 在**本机 Docker 沙箱**里编译执行：禁网、限内存、限 CPU、限时、只读文件系统。跑不过用例就是不过 |
| 🧠 **出题先自检，不糊弄你** | AI 出的每道题，都会先拿**参考解**在沙箱里把用例跑一遍，全过才发给你；跑不通就换一批重试。不会出现「标准答案自己都编译不过」 |
| 🧩 **五种题型一次配齐** | 上机编程、数据库题（临时 SQLite 真实执行）、编程填空、选择题、简答题 |
| 🎨 **界面配色随你** | 松绿 / 藏青 / 赭橙三套配色，选择按账号保存 |
| 🙋 **真人面试官，不是纸片人** | 同一张真人照片切出 12 个表情/状态帧：朗读时嘴型随音量大小变化，播完自动闭嘴，并按提问 / 聆听 / 录制 / 追问切换姿态（**按音量驱动，不是音素级口型同步**） |
| 🔁 **错题本：没答好的那道题，练到过线为止** | 报告里低于 75 分的题自动进本，**题面跟着成绩长期保留**（录像清了题还在）。点一下就为这道题开一场练习，成绩过线即自动出本 |
| 📊 **不只是能跑，还要能运维** | 分析队列带 Prometheus 指标、指数退避重试与死信队列；录像上传支持分片与断点续传 |
| 🛡 **额度护栏，成本不会跑飞** | 每个账号每天有 AI 调用上限（出题 / 追问 / 评分 / 判分 / 简历 / 配音都算），顶栏实时显示剩余；到达上限明确拦住并在次日恢复，排队的分析任务顺延而不是失败 |
| 🗣 **外语口语：一句一句把话聊出来** | 英语，面试、工作沟通、日常三类场景。说完先接话再纠错（"更自然的说法"+ 一句中文解释），结束给四项复盘。配音可选系统语音（快）或服务端音色（统一） |

## 🚀 核心功能

| 模块 | 说明 |
| --- | --- |
| 练习配置 | 目标岗位、公司、JD、简历文件，面试类型 / 面试官风格 / 难度 / 题量与时长 |
| 设备检测 | 摄像头、麦克风授权与实时预览，音量检查通过才允许进入面试 |
| AI 出题 | 上传 PDF / Word 解析简历正文，结合岗位与 JD 生成逐题提问；模型不可用时回退内置题库，简历正文不落库 |
| 模拟面试 | **真人数字人面试官**播报题目：嘴型随音量变化、播完自动闭嘴、按提问/聆听/录制/追问切换姿态（纯文字降级）；每题独立录制、可暂停、可重录 |
| AI 复盘 | 转写 → 结构化评分 → 逐题报告，带可跳转的视频时间点证据 |
| 追问式面试 | 答完一题面试官按回答质量决定是否追问；**同一道题最多追问 2 轮**、一场最多 3 次，追问在报告里单独标出而不算新题 |
| 连续面试 / 转写校正 | 一场连续作答，不逐题重录，单题 3 分钟、整场只能暂停一次；报告里可修正 ASR 错字并只重评这一题 |
| 改写 / 重录 / 版本对比 | 基于原话生成一版更清楚的回答，可编辑后照着重录；旧版本保留，报告里并排对比 |
| 本周报告 / 提醒 | 计划完成率、练习场次、维度变化、最佳 before/after 与下周建议；每日/周报/面试倒计时邮件可分别开关 |
| 学习小组 / 班级 | 助教建组、邀请码加入、按天布置作业、完成看板；作业自动进入学员今日训练，分数共享默认关闭 |
| 求助 / 同伴互评 | 学员可就某题求助，助教写反馈；开启互评后只按转写匿名评价，3 项评分 + 一句建议 |
| 训练档案 / 错题本 | 跨场次趋势与维度短板；低于 75 分的题进错题本，一键单题重练到过线 |
| 求职目标 / 今日训练 | 一个岗位一个目标（公司、JD、轮次、面试日期）；首页直接给"今天练 3 题"，练习完成自动回挂目标与连续记录 |
| 外语口语 | 英语对话练习：每轮同步转写 → 回复 → 最多 2 条改进建议；手打输入作为降级；复盘四项（流利度 / 用词 / 语法 / 任务完成） |
| 笔试练习 | Monaco 网页 IDE 写代码；「运行示例」不计分、「提交本题」跑全部用例（含隐藏）并按通过比例计分 |
| 数据保留 | 录像与笔试默认保留 7 天，可随时手动删除，删除会同时清理媒体、转写与评分 |

## 🎬 项目演示

仓库里带着几次迭代的录屏，可以直接看效果：

| 迭代 | 内容 |
| --- | --- |
| [step1.mp4](docs/step1.mp4) | 项目搭建：登录、练习配置、题目生成 |
| [step2.mp4](docs/step2.mp4) | 模拟面试：数字人播报与逐题录制 |
| [step3.mp4](docs/step3.mp4) | 复盘报告与时间轴证据 |
| [step4.mp4](docs/step4.mp4) | 完整流程演示 |

## 🛠 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21 · Spring Boot 3.4 · Spring AI 1.1.8 · Spring AI Alibaba 1.1.2.4（DashScope） |
| 前端 | Vue 3.5 · Vite 6 · Element Plus · Pinia |
| 存储 | PostgreSQL 16 + pgvector · Redis 7 · S3 兼容对象存储（本地磁盘 / MinIO / AWS S3） |
| 媒体 | 浏览器 `MediaRecorder` 录制 · ffmpeg 抽取音轨 · Paraformer 转写 · Sambert 合成 |

## ⚡ 快速开始

### 方式一：一条命令全跑起来（推荐先试）

```bash
cp .env.example .env          # 只填 AUTH_TOKEN_SECRET 也能起；填上 Ali_API_KEY 才有真实 AI
docker compose --profile full up -d --build
```

等后端健康检查变绿（约 30 秒，首次构建要先下载依赖，耐心一点），然后打开
<http://localhost:8080>，用 `smoke / 123456` 登录即可。

这套 profile 会把 Postgres、Redis、后端、前端（nginx 反代 `/api`）一起起好；
**不加 `--profile full`** 就还是老样子——只起基础设施，应用在本机跑（下面方式二）。
容器里已经装好中文字体（文泉驿），所以简历导出的 PDF 在容器里也能正常生成。
停掉：`docker compose --profile full down`（数据卷保留）。

### 方式二：本机开发

```bash
# 1. 环境变量
cp .env.example .env          # 至少填 Ali_API_KEY 与 AUTH_TOKEN_SECRET
# 后端启动时自动加载仓库根目录的 .env，不需要手动 export

# 2. 基础设施：Postgres + Redis + S3 兼容存储
docker compose up -d

# 3. 构建并启动后端（首次启动会执行 Flyway 迁移）
mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar

# 4. 前端
cd web && npm install && npm run dev
```

打开 <http://localhost:5173>，在登录页点「注册」自己建账号（用户名 + 至少 6 位密码），
注册成功会直接进入工作台。演示账号是 `smoke / 123456`。
需要批量造账号时也可以直接往 `candidates` 表插一行，明文口令首次登录后会自动升级成哈希。

**暂时没有百炼 API Key 也能试**：把 `PRACTICE_ASR_PROVIDER` 与 `PRACTICE_EVALUATION_PROVIDER`
都设为 `stub`，整条流程照常跑完（转写与评分给占位结果）；拿到 Key 后填进 `.env` 即可切换成真实模型。
上手流程见 [docs/USER-GUIDE.md](docs/USER-GUIDE.md)，代码结构与扩展点见
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，完整变量与对象存储切换见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 🧪 测试

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

真实供应商链路可以单独验证（需要有效 `Ali_API_KEY`，会把报告写成审计产物）：

```bash
pwsh scripts/e2e-real-providers.ps1                       # 或用 -MediaPath 指定真人录制的素材
```

笔试模块的代码沙箱需要 Docker（会在临时容器里真跑代码，含禁网与超时用例）：

```bash
mvn -B -pl consumer test -Dtest=CodeRunnerServiceTest -Dcode.runner.integration=true
```

分析队列的指标走 `/actuator/prometheus`（`analysis_queue_length`、`analysis_failure_rate`、
`analysis_dlq_size` 等）；重试到上限的任务进入死信队列，可通过
`GET /api/v1/analysis/dlq` 查看、`POST /api/v1/analysis/dlq/{jobId}/requeue` 重新入队。

没有云端凭证也能跑通整条链路：

```bash
PRACTICE_ASR_PROVIDER=stub PRACTICE_EVALUATION_PROVIDER=stub \
  PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000 PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000 \
  java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
```

## 📁 目录结构

```
consumer/    后端应用（practice / analysis / avatar / coding 笔试与代码沙箱 / storage / security 等模块 + Flyway 迁移）
common/      共享工具：统一响应、业务异常、JWT、CORS 配置
web/         Vue 3 单页应用
scripts/     端到端冒烟脚本
docs/        部署说明与英文 README
docker-compose.yml
```

## 🔌 接口

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

# 笔试模块（AI 出题 + 本机 Docker 沙箱判题）
GET    /api/v1/coding-sessions/capabilities                沙箱是否启用、可用语言与题型
POST   /api/v1/coding-sessions                             出题并建场（含参考解自检，耗时较长）
GET    /api/v1/coding-sessions/{id}                        题目（不含任何答案）与提交概要
POST   /api/v1/coding-sessions/{id}/questions/{q}/run      只跑可见示例，不落库
POST   /api/v1/coding-sessions/{id}/questions/{q}/submit   跑全部用例（含隐藏）并计分
GET    /api/v1/coding-sessions/{id}/report                 笔试报告
DELETE /api/v1/practice-sessions/{id}                   删除练习及其全部媒体
```

## ✅ 测试与验证状态

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

## 📄 致谢与许可

基于 [guangge](https://github.com/xgwangdl) 的 AI-Interview 重构而来，采用 Apache License 2.0。

感谢 Spring Boot、Spring AI、Spring AI Alibaba、Vue、Element Plus、PostgreSQL / pgvector 等开源项目。

---

## ⭐ 如果这个项目对你有帮助

- 点个 **Star** —— 这是对开源作者最直接的鼓励，也能让更多正在准备面试的人看到它
- 提 **Issue** 说说你想要的功能，或者你在使用中踩到的坑
- **PR** 一律欢迎（新的题型、新的语言沙箱、更好的提示词都算）

提交前请先看 [CONTRIBUTING.md](CONTRIBUTING.md)：改动先提到 `feature/practice-interview` 分支，
确认后再合并到 master，并附上自测命令的输出。

> 面试是练出来的，不是背出来的。祝拿到心仪的 offer 🎉

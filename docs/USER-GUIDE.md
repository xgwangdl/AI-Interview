# Light-Interview 使用手册

这份文档面向**使用者**：怎么把系统跑起来、怎么完成一场模拟面试、报告怎么读、出问题怎么查。

- 部署与全部环境变量：见 [DEPLOYMENT.md](DEPLOYMENT.md)
- 代码结构与实现原理：见 [ARCHITECTURE.md](ARCHITECTURE.md)
- 当前完成度与验收证据：见 [STATUS.md](STATUS.md)

---

## 1. 系统能做什么

一个面向求职者的**视频模拟面试系统**。一场练习的完整链路是：

1. **AI 出题**：按岗位、公司、JD、简历生成面试题（也可以自己出题）；
2. **浏览器录制**：逐题作答，摄像头 + 麦克风录制，支持重录；
3. **自动复盘**：后台把录像转成文字（Paraformer 转写），再由大模型按固定 JSON 结构评分（Spring AI）；
4. **结构化报告**：综合得分、5 个维度评分、语速/停顿/口头禅、改进建议、优秀回答示例，以及可点击跳转的视频证据。

数字人面试官会用 TTS 把题目读出来；合成不可用时自动降级为「头像 + 字幕」，不影响作答。

---

## 2. 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 后端运行 |
| Maven | 3.9+ | 构建后端 |
| Node.js | 18+（LTS） | 前端构建与开发服务器 |
| Docker + Docker Compose | 任意较新版本 | Postgres / Redis / S3 兼容存储 |
| 阿里云百炼 API Key | —— | 可选。没有 Key 也能用兜底模式跑通全流程 |
| ffmpeg | 7.x | 真实转写时从录像抽取音轨 |

---

## 3. 启动系统

### 3.1 准备配置

```bash
cp .env.example .env
```

编辑 `.env`，**至少**填两项：

```dotenv
Ali_API_KEY=你的百炼Key
AUTH_TOKEN_SECRET=换一串足够长的随机字符串
```

`.env` 已被 `.gitignore` 忽略，不会进版本库。后端启动时会自动加载仓库根目录（或 `consumer/` 目录）
下的 `.env`，**不需要手动 `export`**。

### 3.2 启动基础设施

```bash
docker compose up -d
docker compose ps        # 三个容器都应是 healthy / Up
```

### 3.3 启动后端

```bash
mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
```

启动成功会看到 `Started ConsumerApplication`，端口 8088。首次启动会自动执行数据库迁移。

> 想用热重载做开发：先跑一次 `mvn -B install -DskipTests`，再用 `mvn -pl consumer spring-boot:run`。
> 直接跑后者会因为只构建 consumer 模块、拿到本地仓库里过期的 `common` 而报
> `NoClassDefFoundError: com/guangge/Interview/vo/CvRequest`。

### 3.4 创建登录账号

系统没有自助注册，账号来自 `candidates` 表。迁移只建表，需要手动插一条：

```bash
docker compose exec -T postgres psql -U postgres -d interview \
  -c "INSERT INTO candidates (candidate_name, invitation_code, status) VALUES ('demo','123456',1);"
```

### 3.5 启动前端

```bash
cd web
npm install
npm run dev
```

浏览器打开 <http://localhost:5173>，用刚创建的账号登录。

### 3.6 没有 API Key 的演示模式

把两个供应商都切成本地兜底实现，就能在没有云端凭证时跑通整条链路：

```dotenv
PRACTICE_ASR_PROVIDER=stub
PRACTICE_EVALUATION_PROVIDER=stub
PRACTICE_ANALYSIS_INITIAL_DELAY_MS=2000
PRACTICE_ANALYSIS_POLL_INTERVAL_MS=2000
```

兜底模式会生成一段占位转写并给出示例评分，用于验证界面与流程。
注意：**`Ali_API_KEY` 仍要填一个非空值**——遗留的 AI 组件在启动时会校验它，留空应用起不来。

---

## 4. 登录

- 登录方式是**姓名 + 邀请码**，校验 `candidates` 表；
- 登录成功后在浏览器 `localStorage` 保存 token，后续请求自动带上；
- 同一用户名连续失败 **10 次**会被锁定 **15 分钟**（`AUTH_LOGIN_MAX_ATTEMPTS` /
  `AUTH_LOGIN_LOCK_WINDOW_MINUTES` 可调）。

演示账号：`smoke / 123456`（若你按 3.4 建的是 `demo / 123456`，就用 demo）。

---

## 5. 界面使用

### 5.1 工作台 `/`

入口页：显示历史练习概览，可新建练习或继续未完成的练习。

### 5.2 新建练习 `/practice/setup`

填写本次练习的背景信息，这些内容会作为出题与评分的上下文：

| 字段 | 说明 |
| --- | --- |
| 岗位 / 公司 | 目标职位与公司 |
| 职位描述（JD） | 粘贴 JD，AI 会据此出题 |
| 简历 | 支持上传 PDF / Word，自动抽取文本；也可直接粘贴 |
| 面试类型 | technical / behavioral 等 |
| 难度 | 标准 / 进阶等 |
| 面试官风格 | balanced 等 |
| 题量、时长 | 题目数量与预计时长 |

点「AI 出题」会调用 `POST /api/v1/practice-sessions/question-plan`，按岗位与 JD 生成题目；
你也可以直接手写题目。注意：**创建练习时题目必须与题量一致**，否则接口返回 400。

### 5.3 设备检测 `/practice/device-check`

检查摄像头与麦克风是否可用。浏览器会弹出授权申请，建议在这里先确认设备正常，
避免录到一半才发现权限没给。

### 5.4 面试间 `/practice/:id/session`

逐题循环：

1. 数字人播报题目（TTS 可用时出声，不可用则只显示字幕）；
2. 点录制开始作答，停止后自动上传；
3. 不满意可以**重录**，以最后一次为准；
4. 全部答完提交，结束整场练习。

断网或上传失败时，录像会先存在浏览器本地，恢复后自动补传——不会因为网络抖动丢作答。

提交整场练习有两个前置条件：**至少完成 1 题**，且**所有已录题目的上传都已结束**。

### 5.5 报告 `/practice/:id/report`

练习提交后进入分析：

- `awaiting_analysis`：还没有可用的分析供应商；
- `pending` / `processing`：后台正在转写与评分；
- `ready`：报告生成完成；
- `analysis_failed`：分析失败，页面上会出现**重试分析**按钮（`POST /api/v1/practice-sessions/{id}/analysis/retry`）。

报告页左侧是逐题视频回放与**回答转写**，可以点击证据里的时间点把视频跳到对应位置；
右侧是本题评分、表达指标、改进建议与优秀回答示例。

### 5.6 历史 `/history`

查看历史练习与报告入口。超过保留期的练习会被自动清理。

---

## 6. 报告怎么读

每道题给出一个综合分和 5 个维度分：

| 维度 | 含义 |
| --- | --- |
| 内容相关性 | 回答是否扣题 |
| 回答完整度 | 要点是否答全 |
| 专业程度 | 术语与技术细节的准确度 |
| STAR 结构 | 是否按 Situation / Task / Action / Result 组织（项目类问题尤其重要） |
| 回答逻辑 | 表达是否连贯、有条理 |

表达层指标（每题）：

| 指标 | 含义 |
| --- | --- |
| 语速 | 字/分 |
| 停顿 | 次数 + 总时长（秒） |
| 口头禅 | 出现次数 |

另外还会给出**改进建议**（可执行条目）、**优秀回答示例**，以及带时间戳的**可定位证据**。

> 系统只评价**回答内容与表达**，不做人脸识别、情绪判断或「是否自信」这类主观推断。

---

## 7. 数据与保留

- 录像按 `MEDIA_PROVIDER` 存到本地磁盘（默认 `./data/media`）或 S3 兼容存储；
- 默认**保留 7 天**（`MEDIA_RETENTION_DAYS`），到期自动清理；
- 在界面上删除一场练习，会同时删掉录像与数据库记录（删除后报告返回 404）；
- 未完成作答会缓存在浏览器 `localStorage`，同一账号在别的机器上看不到。

---

## 8. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 播报没有声音，接口返回 503 | 未配置 Key 或 TTS 合成失败 | 属预期降级：保留字幕继续作答；配置有效 Key 即可恢复语音 |
| 报告一直是 `awaiting_analysis` | 没有可用的分析供应商 | 检查 `Ali_API_KEY` 与 `PRACTICE_ASR_PROVIDER` / `PRACTICE_EVALUATION_PROVIDER` |
| 报告是 `analysis_failed` | 转写或评分失败 | 看报告里的 `analysis.error`（已脱敏），修好后点「重试分析」 |
| 上传返回 415 | 上传的不是视频 | 上传接口要求 `Content-Type` 以 `video/` 开头 |
| 请求返回 401 | token 过期或缺失 | 重新登录 |
| 摄像头打不开 | 权限被拒 / 被别的程序占用 | 在浏览器地址栏检查权限，关掉占用摄像头的软件 |
| 启动报 `NoClassDefFoundError ... CvRequest` | 用了 `mvn -pl consumer spring-boot:run` 但没先装 common | 先执行 `mvn -B install -DskipTests` |
| 启动报 `DashScope API key must be set` | 缺 `Ali_API_KEY` | 在 `.env` 里填一个非空值 |
| PowerShell 下 Maven 报 `Unknown lifecycle phase ".integration=true"` | `-D` 参数被拆分 | 加引号：`mvn -B test "-Ddb.integration=true"` |

---

## 9. 停止与清理

```bash
# 停止后端：Ctrl+C，或
Get-Process java | Stop-Process

# 停止容器（保留数据卷）
docker compose down

# 连数据一起清掉（录像、账号、练习记录全部重置）
docker compose down -v
rm -rf data/media
```

---

## 10. 相关文档

| 文档 | 内容 |
| --- | --- |
| [README.md](../README.md) | 项目总览与快速开始 |
| [DEPLOYMENT.md](DEPLOYMENT.md) | 部署、全部环境变量、对象存储切换 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 代码结构、数据流、扩展点 |
| [STATUS.md](STATUS.md) | 逐项验收状态与验证证据 |

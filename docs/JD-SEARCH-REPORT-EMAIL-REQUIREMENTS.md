# JD 智能搜索导入与面试报告邮件需求文档

| 项目 | 内容 |
| --- | --- |
| 状态 | Draft，待开发确认 |
| 日期 | 2026-11-05 |
| 所属产品 | Light-Interview |
| 关联模块 | 求职雷达、模拟面试、报告、个人资料、邮件通知 |
| 关联文档 | [JOB-RADAR-REQUIREMENTS.md](JOB-RADAR-REQUIREMENTS.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[DEPLOYMENT.md](DEPLOYMENT.md) |

## 1. 背景

当前面试练习要求用户自己填写岗位名称、公司和 JD。真实用户通常还需要：

1. 从招聘网站或企业招聘页找到目标岗位。
2. 判断岗位是否值得投递。
3. 把完整 JD 带入匹配评估和模拟面试。
4. 面试结束后把报告保存下来，方便复盘、对比和后续分析。

本需求把这两类需求拆成两个可以独立交付的能力：

- **JD 搜索与导入**：缩短从“看到岗位”到“开始针对性练习”的路径。
- **报告邮件发送**：把已生成的报告转成稳定文档并发送到用户邮箱。

## 2. 产品结论

### 2.1 做搜索和导入，但不做服务器直连爬取 Boss 直聘

Boss 直聘等招聘平台通常需要登录，并存在反自动化、验证码、账号风控和平台条款限制。服务器直接抓取会在稳定性、合规性和维护成本上形成长期风险。

本项目采用以下数据源策略：

1. 用户粘贴公开职位链接，服务端读取允许访问的页面。
2. 浏览器插件读取用户当前已打开页面，由用户确认后导入。
3. 通过有授权的招聘数据 API、企业招聘官网 API 或公开招聘数据源搜索。
4. AI 只负责筛选、排序、解释和技能抽取，不能编造职位，也不直接操作招聘平台账号。

### 2.2 Function Tool 是后端能力的入口，不是浏览器

Spring AI Function Tool 只能调用后端已经实现的方法。系统需要先实现统一的职位搜索和导入服务，再把该服务暴露成工具：

- `search_jobs`
- `preview_job_import`
- `import_selected_job`

模型可以帮助用户检索、比较和选择，但“搜索到真实职位”由后端数据源适配器保证。模型不允许生成不存在的公司、职位或申请链接。

### 2.3 邮件报告优先落地

系统已有 `spring-boot-starter-mail`、用户邮箱、邮件通知能力、PDFBox 中文字体和结构化面试报告。邮件发送不需要依赖第三方招聘平台，合规和实现风险更低，因此作为 P0 优先交付。

## 3. 目标与非目标

### 3.1 目标

- 用户可以从受支持的数据源搜索职位，并在列表中选中一条 JD。
- 用户可以粘贴公开职位链接或通过浏览器插件导入当前 JD。
- 导入前必须展示职位来源、岗位、公司、抓取时间和文本预览，由用户确认。
- 导入后的 JD 可以直接进入现有求职雷达匹配、模拟面试和题目生成流程。
- AI 可以基于用户技能画像对搜索结果排序，并给出简短、可解释的推荐理由。
- 用户可以把一场已完成面试的报告以 PDF 附件发送到本人已验证邮箱。
- 邮件发送必须支持权限隔离、幂等、失败重试、频率限制和状态追踪。

### 3.2 非目标

- 不在服务器保存 Boss 直聘、猎聘等平台的账号、密码或登录 Cookie。
- 不绕过验证码、登录校验、反爬限制或付费访问限制。
- 不实现自动批量抓取、自动投递、自动联系招聘者。
- 不允许把全站抓取结果作为公开职位库对外发布。
- 首版不发送录音、视频或长期有效的媒体播放链接。
- 不允许用户把报告发送到任意陌生邮箱；首版只发送到本人绑定并验证的邮箱。
- 不把 AI 生成内容当作招聘平台的原始 JD 展示。

## 4. 用户角色与核心流程

### 4.1 场景 A：搜索职位并导入

1. 用户在求职雷达页面输入关键词、城市、薪资和经验要求。
2. 系统根据启用的数据源返回标准化职位卡片。
3. 每张卡片至少显示岗位、公司、城市、薪资、数据来源、获取时间和原文链接。
4. AI 可结合技能画像显示匹配度和推荐原因，但必须保留原始数据来源。
5. 用户点击一条职位并进入导入预览。
6. 系统展示清洗后的 JD 文本、识别出的技能、缺失字段和文本截断提示。
7. 用户点击“确认导入”后创建或复用 `job_lead`。
8. 用户可以在岗位详情页发起匹配评估或针对该 JD 生成模拟面试。

### 4.2 场景 B：粘贴职位链接导入

1. 用户粘贴一个公开职位页面 URL。
2. 后端校验协议、域名、重定向和响应大小，防止 SSRF。
3. 系统优先读取页面中的标准 `JobPosting` 结构化数据，其次读取 Open Graph 和可识别的正文区域。
4. 无法可靠读取时，返回明确提示，并要求用户手动粘贴 JD。
5. 系统不执行页面中的脚本，不渲染未知 HTML。
6. 用户确认预览后才保存职位。

### 4.3 场景 C：浏览器插件导入 Boss 页面

1. 用户在浏览器中自行登录招聘平台并打开职位详情。
2. 用户点击 Light-Interview 插件按钮。
3. 插件只提取当前页面可见的职位标题、公司、城市、薪资、JD 文本和规范 URL。
4. 插件先展示本地预览，用户确认后才上传。
5. 服务端对内容做长度限制、HTML 清理、重复岗位检查和来源标记。
6. 插件不得读取密码输入框、Cookie、浏览器历史或其他标签页内容。
7. 用户可以随时撤销插件授权。

### 4.4 场景 D：面试报告发送邮箱

1. 用户完成面试并等待报告分析状态变为 `completed`。
2. 报告页显示“发送到我的邮箱”按钮。
3. 若邮箱为空或未验证，引导用户前往个人设置完成邮箱验证。
4. 用户确认发送后，服务端创建异步发送任务并立即返回 `202 Accepted`。
5. 后台生成固定版本的 PDF 报告，通过 SMTP 发送到当前账号邮箱。
6. 页面轮询发送状态，并显示成功、生成中或失败原因。
7. 同一报告在默认时间窗口内重复点击不能产生重复邮件。

## 5. 功能需求

### 5.1 数据源注册与能力声明

每个数据源适配器必须声明自己的能力：

| 能力 | 说明 |
| --- | --- |
| `SEARCH` | 支持服务端关键词和城市搜索 |
| `URL_IMPORT` | 支持用户提交公开职位 URL 并读取详情 |
| `BROWSER_CAPTURE` | 支持浏览器插件上传用户确认的页面文本 |
| `LICENSED_API` | 数据来自正式授权接口 |

数据源必须由配置或功能开关控制，接口至少返回：

- `sourceId`
- 展示名称
- 支持的国家和语言
- 支持的能力
- 当前是否可用
- 数据时效说明
- 需要用户跳转/登录的提示

单个数据源故障不能导致整个搜索页不可用。

### 5.2 职位搜索

搜索条件：

- 关键词：岗位名称、技能或公司，必填。
- 城市：可选。
- 薪资下限/上限：可选，统一换算为 K/月并保留原始薪资文本。
- 经验要求：可选。
- 来源：可多选。
- 分页：默认 20 条，最大 50 条。

搜索结果字段：

```json
{
  "sourceId": "licensed-provider-a",
  "externalId": "job-123",
  "title": "Java 后端工程师",
  "company": "示例公司",
  "city": "上海",
  "salaryText": "20-35K·14薪",
  "salaryMinK": 20,
  "salaryMaxK": 35,
  "experience": "3-5年",
  "url": "https://example.com/jobs/123",
  "snippet": "负责交易系统后端开发...",
  "publishedAt": "2026-11-04T08:00:00Z",
  "retrievedAt": "2026-11-05T10:00:00Z",
  "importable": true,
  "importMode": "URL_IMPORT"
}
```

规则：

- 不存在的字段返回 `null`，不能用模型猜测补齐。
- 搜索缓存默认 10 分钟；岗位详情和导入预览不使用过期缓存。
- 数据源返回的原始链接必须保留。
- 结果排序默认按来源相关度；启用 AI 排序时必须允许用户切回原始顺序。
- AI 推荐理由最多 80 个中文字符，并明确它基于哪些用户技能。

### 5.3 导入预览与确认

预览必须包含：

- 来源与原始 URL。
- 岗位、公司、城市、薪资。
- 清洗后的 JD 文本。
- 文本长度、是否被截断、抓取时间。
- 结构化技能提取结果。
- 可能缺失或识别不可靠的字段。
- 与当前用户已有岗位的去重提示。

确认导入规则：

- JD 最少 80 字，最大 20000 字。
- 用户必须在预览页确认，不能由 AI 自动直接保存。
- 确认后创建 `job_lead`，并记录 `source`、`source_url`、`external_job_id`、`source_fetched_at`。
- 与现有 `owner + company + role + jd_hash` 去重逻辑保持一致。
- 同一页面的原文不得作为模型系统提示的一部分执行，只能作为不可信数据块处理。

### 5.4 AI 与 Function Tool 集成

后端服务应先提供普通 REST API，再在同一服务上封装 Function Tool。UI 不依赖模型才能搜索，模型也不直接写数据库。

建议工具定义：

#### `search_jobs`

输入：

```json
{
  "keyword": "Java 后端",
  "city": "上海",
  "salaryMinK": 20,
  "experience": "3-5年",
  "limit": 10
}
```

输出：标准化职位结果和来源状态。工具不得返回模型自行生成的职位。

#### `preview_job_import`

输入：

```json
{
  "sourceId": "browser-extension",
  "externalId": "page-hash",
  "url": "https://example.com/job/123"
}
```

输出：短期 `previewToken`、预览内容和风险提示。`previewToken` 默认 10 分钟过期。

#### `import_selected_job`

输入：`previewToken`。

该工具只能由用户明确确认后的动作触发。模型不能在同一轮对话里自行搜索、选择和保存，除非 UI 已记录用户确认。

工具安全要求：

- 用户上下文和 owner 由服务端鉴权注入，不接受模型传入 owner。
- URL 必须通过协议、域名、IP 和重定向校验。
- 工具参数必须做长度、枚举和范围校验。
- 工具结果只返回结构化数据，不返回原始 HTML。
- 记录工具调用来源、耗时和结果数量，不记录完整 JD 和用户隐私文本。

### 5.5 浏览器插件

首版只要求 Chrome/Edge Chromium 插件。

功能要求：

- 仅对用户主动点击的当前标签页工作。
- 提取前显示“将读取当前页面职位内容”的提示。
- 提取后必须由用户确认上传。
- 使用短期导入令牌，不保存招聘平台登录态。
- 令牌可撤销，默认 30 天过期。
- 插件只提取白名单字段，不采集整页 HTML、Cookie、密码框和其他标签页。
- 支持选择页面的正文区域作为人工兜底。

插件不属于 P0；在授权数据源无法覆盖国内主流招聘平台时优先开发。

### 5.6 邮箱验证

发送报告前，账号邮箱必须满足：

- 格式合法。
- 已通过验证码或验证链接确认。
- `email_verified_at` 非空。

流程：

1. 用户在个人设置提交邮箱。
2. 服务端发送 6 位验证码或一次性链接。
3. 验证码默认 10 分钟过期，最多尝试 5 次。
4. 同一邮箱每分钟最多发送 1 次，每天最多 5 次。
5. 验证成功写入 `email_verified_at`。
6. 邮箱变更后清空验证状态。

### 5.7 报告文档生成

首版输出 PDF，复用 PDFBox 和中文字体能力。

报告内容：

- 报告标题、岗位、公司、面试类型、完成时间。
- 总体得分和分析状态。
- 各维度平均分、语速、停顿、口头禅等指标。
- 每道题的问题、回答摘要、评分和改进建议。
- AI 示范回答，若存在则包含。
- 下一轮训练建议。
- 可选：与上一次同岗位报告的分数变化。

不得包含：

- 录音和视频文件。
- 已过期的签名媒体链接。
- 招聘平台完整 HTML。
- SMTP、账号或其他内部配置信息。

推荐文件名：

```text
Light-Interview-{role}-{yyyyMMdd-HHmm}.pdf
```

PDF 必须可以直接阅读中文；生成失败时返回明确错误，不能发送缺字或空报告。

### 5.8 邮件发送

首版只允许发送到账号已验证邮箱，不接受任意 `to` 参数。

邮件结构：

- 标题：`Light-Interview 面试报告 - {role}`
- 正文：报告已生成、面试时间、总体分、查看报告的站内链接。
- 附件：PDF。
- 页脚：数据保留说明和隐私说明。

发送规则：

- 报告状态不是 `completed` 时禁止发送。
- 用户必须拥有报告对应 `practice_session`。
- 同一报告同一版本 10 分钟内重复请求返回已有任务，不重复发送。
- 每个用户每天最多发送 10 次；每份报告每天最多 3 次。
- 附件最大 10 MB。
- SMTP 发送失败自动重试 3 次，指数退避。
- 最终失败保留任务记录，并允许用户手动重试。

## 6. 页面与交互

### 6.1 求职雷达搜索页

在现有 `/jobs` 页面增加：

- “搜索职位”主入口。
- 搜索表单：关键词、城市、薪资、经验、来源。
- 职位结果卡片。
- “打开原文”和“导入预览”两个动作。
- AI 排序开关，默认关闭。
- 数据源不可用时的说明和手动粘贴入口。

交互要求：

- 搜索按钮等待期间禁用。
- 导入预览不会直接创建岗位。
- 用户从结果返回时保留查询条件和滚动位置。
- 移动端卡片改为单列，不出现横向滚动。

### 6.2 报告页

在报告操作区增加“发送到我的邮箱”：

- 邮箱已配置且已验证：可点击。
- 邮箱为空或未验证：显示“先验证邮箱”。
- 发送中：按钮禁用并显示状态。
- 已发送：显示发送时间和再次发送限制。
- 失败：显示可理解的错误和重试入口。

不把邮箱地址完整显示在公开页面，只显示脱敏形式，例如 `u***@example.com`。

## 7. API 设计

所有 API 使用现有 `/api/v1` 前缀和 Bearer Token。

### 7.1 数据源与搜索

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/job-sources` | 返回启用的数据源和能力 |
| `POST` | `/api/v1/job-sources/search` | 搜索职位 |
| `POST` | `/api/v1/job-sources/import-preview` | 通过来源和外部 ID 获取导入预览 |
| `POST` | `/api/v1/job-sources/import-preview/url` | 通过公开 URL 获取预览 |
| `POST` | `/api/v1/job-sources/browser-capture` | 插件提交用户确认后的页面内容 |
| `POST` | `/api/v1/job-sources/import` | 使用 previewToken 确认导入 |

错误码约定：

- `400` 参数不合法或 URL 不受支持。
- `401` 未登录。
- `403` 来源未授权或功能关闭。
- `404` 来源任务不存在或无权访问。
- `409` 重复岗位。
- `422` 页面可读取但没有可靠 JD。
- `429` 搜索或导入频率超限。
- `502` 上游数据源错误。
- `504` 上游数据源超时。

### 7.2 邮箱验证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/me/email-verification` | 发送验证码或验证链接 |
| `POST` | `/api/v1/me/email-verification/confirm` | 确认验证码 |
| `GET` | `/api/v1/me/email-verification` | 返回当前邮箱和验证状态 |

### 7.3 报告邮件

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/practice-sessions/{id}/report/email` | 创建报告邮件任务，返回 202 |
| `GET` | `/api/v1/report-deliveries/{deliveryId}` | 查询发送状态 |
| `POST` | `/api/v1/report-deliveries/{deliveryId}/retry` | 对最终失败任务重试 |

创建请求支持 `Idempotency-Key` 请求头。相同 key 在 24 小时内返回同一任务。

## 8. 数据模型

实施时使用当时下一个可用的 Flyway 迁移号。当前工作区中 V21 已被求职雷达占用，不能重复使用。

### 8.1 扩展 `job_leads`

建议新增：

- `external_job_id varchar(200)`
- `source_fetched_at timestamptz`
- `source_metadata jsonb`
- `import_mode varchar(30)`

已有 `source` 和 `source_url` 继续使用。

### 8.2 `job_source_capture_tokens`

用于浏览器插件授权：

- `id uuid primary key`
- `owner_id varchar(255) not null`
- `token_hash varchar(128) not null unique`
- `expires_at timestamptz not null`
- `revoked_at timestamptz`
- `created_at timestamptz not null`

数据库不保存明文令牌。

### 8.3 `email_verifications`

- `id uuid primary key`
- `owner_id varchar(255) not null`
- `email varchar(255) not null`
- `code_hash varchar(128) not null`
- `attempts int not null default 0`
- `expires_at timestamptz not null`
- `consumed_at timestamptz`
- `created_at timestamptz not null`

### 8.4 `report_email_deliveries`

- `id uuid primary key`
- `owner_id varchar(255) not null`
- `session_id uuid not null`
- `report_snapshot_json jsonb not null`
- `snapshot_hash varchar(64) not null`
- `recipient_email_hash varchar(64) not null`
- `status varchar(20) not null`
- `attempts int not null default 0`
- `provider_message_id varchar(255)`
- `last_error varchar(1000)`
- `idempotency_key varchar(100)`
- `created_at timestamptz not null`
- `started_at timestamptz`
- `sent_at timestamptz`
- `updated_at timestamptz not null`

状态：`queued`、`generating`、`sending`、`sent`、`failed`。

报告快照用于保证重试时内容不因后续编辑而变化。成功发送后快照按报告数据保留周期清理；任务元数据默认保留 90 天。

## 9. 服务与代码落点

### 9.1 后端

建议新增或扩展：

- `jobsearch/JobSourceProvider.java`：数据源统一接口。
- `jobsearch/JobSourceRegistry.java`：启停和配置。
- `jobsearch/JobImportService.java`：清洗、校验、去重和预览 token。
- `jobsearch/BrowserCaptureController.java`：浏览器插件导入。
- `jobsearch/JobSearchTool.java`：Spring AI Function Tool 封装。
- `report/ReportDocumentService.java`：从报告快照生成 PDF。
- `report/ReportEmailDeliveryService.java`：幂等、额度、任务创建和状态查询。
- `report/ReportEmailWorker.java`：异步生成和发送。
- 扩展 `MailService`：支持内存附件，不落到共享临时文件。
- 扩展 `Candidates` 和 `ProfileService`：保存邮箱验证状态。

所有业务服务必须继续使用当前 `owner` 注入方式，不能信任前端传入的用户 ID。

### 9.2 前端

- 扩展 `JobListView.vue`：搜索入口、结果列表和来源状态。
- 新增 `JobSearchView.vue` 或独立搜索结果组件。
- 新增导入预览对话框或页面。
- 扩展 `ReportView.vue`：邮箱验证提示、发送按钮和任务状态。
- 扩展个人设置：邮箱验证入口。
- 新增 `reportDelivery.js` 和 `jobSource.js` 服务层。
- 同步补齐 `zh-CN`、`en-US`、`ja-JP` 文案。

### 9.3 插件

建议独立目录：

```text
extensions/job-importer/
```

插件不依赖页面可注入脚本执行任意远程代码，构建产物需要固定版本和权限清单。

## 10. 安全与合规

### 10.1 招聘数据

- 只访问公开页面或已授权 API。
- 对 URL 做 SSRF 防护：禁止 localhost、内网 IP、云元数据地址和非 HTTP(S) 协议。
- 限制重定向次数、响应大小和超时。
- 不执行页面 JavaScript。
- 不保存招聘平台密码、Cookie 或登录令牌。
- 对平台明确禁止自动访问的数据源立即通过功能开关下线。
- 只保存用户实际选择导入的 JD，不建立全平台职位镜像库。

### 10.2 Prompt Injection

- 网页文本视为不可信数据，不拼接到系统指令区。
- 删除脚本、样式、隐藏文本和表单。
- 限制 JD 长度为 20000 字符。
- 模型输出必须通过结构化 schema 校验。
- 模型不能改变工具权限、owner、URL 注入规则或保存条件。

### 10.3 邮件与隐私

- 只发送到账号绑定并验证的邮箱。
- SMTP 授权码只通过环境变量提供。
- 生产环境必须关闭 `spring.mail.debug`。
- 日志中不记录验证码、完整邮箱、完整报告正文和 SMTP 响应中的敏感字段。
- 配置 SPF、DKIM、DMARC，降低进入垃圾箱概率。
- 邮件正文提供站内报告入口和隐私说明。
- 删除账号或报告时，同步取消尚未发送的邮件任务。

## 11. 性能与可用性

目标部署仍为 2 vCPU / 2 GB 单机。

- 搜索接口目标 P95 小于 5 秒，上游超时默认 8 秒。
- 导入预览目标 P95 小于 8 秒。
- 邮件创建接口不等待 PDF 和 SMTP，目标响应小于 500 ms。
- 邮件后台并发默认 1，可通过配置调整。
- 同一用户同时只允许 1 个报告发送任务处于执行中。
- 数据源和 SMTP 故障不得阻塞应用启动。
- 功能通过 `JOB_SEARCH_ENABLED` 和 `REPORT_EMAIL_ENABLED` 独立开关控制。

## 12. 配置项

建议新增：

```dotenv
JOB_SEARCH_ENABLED=false
JOB_SEARCH_PROVIDERS=
JOB_SEARCH_CACHE_TTL_SECONDS=600
JOB_SEARCH_MAX_RESULTS=50
JOB_SEARCH_REQUEST_TIMEOUT_SECONDS=8

REPORT_EMAIL_ENABLED=false
REPORT_EMAIL_DAILY_LIMIT=10
REPORT_EMAIL_PER_REPORT_DAILY_LIMIT=3
REPORT_EMAIL_MAX_ATTEMPTS=3

MAIL_FROM=
SPRING_MAIL_DEBUG=false
```

现有 SMTP 配置继续通过环境变量注入，不在仓库中保存真实账号和授权码。

## 13. 优先级与交付阶段

### P0：面试报告 PDF 邮件

- 邮箱验证。
- PDF 生成。
- 邮件异步发送、幂等、重试和频率限制。
- 报告页发送入口和状态展示。

理由：价值明确、风险低、现有基础最多，可以直接形成留存闭环。

### P1：公开 URL 导入

- URL 安全校验。
- `JobPosting`、Open Graph 和正文提取。
- 导入预览、去重和保存。

首版只承诺支持能够稳定读取的公开招聘页面，不承诺 Boss 等登录页面可直接读取。

### P1：浏览器插件导入

- Chrome/Edge 插件配对。
- 当前页面职位内容提取。
- 用户确认上传和短期令牌。

适用范围以平台条款和用户授权为前提，不做静默采集。

### P2：授权搜索数据源与 Function Tool

- 接入有授权的搜索 API。
- AI 基于技能画像排序和解释。
- 封装 `search_jobs`、`preview_job_import`、`import_selected_job`。

## 14. 验收标准

### 14.1 JD 搜索与导入

- 未配置任何数据源时，页面显示手动粘贴入口，不出现空白或报错页。
- 搜索结果所有条目都包含来源和原文链接，模型不能生成不存在的条目。
- 用户未确认预览时，数据库中不会新增 `job_lead`。
- 重复导入同一岗位时返回已有岗位或明确的 409，不产生重复记录。
- SSRF 测试集全部拒绝，包括 localhost、`127.0.0.1`、内网网段和云元数据地址。
- 含提示注入文本的 JD 不能改变系统指令、用户身份或导入权限。
- 浏览器插件只能读取当前用户主动打开的标签页，用户可以撤销授权。
- 中文、英文、日文界面文案 key 完整，构建通过。

### 14.2 邮箱与报告

- 未验证邮箱不能发送报告。
- 其他用户不能读取或重试不属于自己的报告任务。
- 重复点击发送只产生一封邮件。
- 报告未完成、已删除或评分失败时不能发送。
- PDF 中文正常，内容与报告快照一致。
- SMTP 暂时失败会重试，任务状态和错误可查询。
- 用户每天和每份报告的发送次数受限。
- 邮件中不包含录音、视频或长期有效媒体签名链接。
- 报告删除后，未发送任务被取消。

### 14.3 自动化测试

- 数据源适配器和搜索聚合单元测试。
- URL 校验、HTML 清洗和提示注入测试。
- 导入去重和用户隔离测试。
- 邮箱验证、限流和过期测试。
- 报告快照、PDF 生成和幂等发送测试。
- SMTP 使用 mock，不依赖真实外部邮箱。
- PostgreSQL 集成测试覆盖迁移、并发任务领取和幂等唯一约束。
- Playwright 覆盖搜索、导入预览、邮箱验证、发送状态和失败重试。

## 15. 待确认事项

以下事项在开发前需要产品或负责人确认：

1. 首批授权搜索数据源使用哪一家供应商，是否需要采购费用。
2. 浏览器插件是否进入首版范围，还是先完成 URL 导入。
3. 邮件服务使用阿里云邮件推送、企业邮箱还是现有 163 SMTP。
4. 报告是否需要同时提供 Word；本文首版只要求 PDF。
5. 报告邮件是否允许用户额外填写一个收件邮箱；本文首版不允许。
6. 数据源缓存和职位原文的保留周期是否统一采用 30 天。

## 16. 实施建议

先完成 P0 邮件报告，再完成公开 URL 导入和浏览器插件。招聘网站搜索应建立在合规数据源之上，不建议为了短期效果把服务器爬虫写进主流程。这样既能尽快交付用户能感知的价值，也不会让系统长期受制于反爬和平台规则变化。

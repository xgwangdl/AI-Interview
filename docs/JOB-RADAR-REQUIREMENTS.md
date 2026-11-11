# Light-Interview 求职雷达与面试回填需求

> 状态：MVP 已实现，等待真实 PostgreSQL 集成与浏览器验收
> 更新日期：2026-11-02
> 来源：`C:\deepseek\JOB-RADAR-REQUIREMENTS.md` 与 Agent 化总体改造 PRD。本文以当前代码为准，并记录落地时的规格偏差。

## 1. 目标

把「看岗位、判断匹配度、准备面试、复盘真实面试」串成可重复使用的闭环：

1. 用户粘贴一份完整 JD，系统给出可解释的匹配分、证据和缺口。
2. 用户可以从岗位详情一键生成针对该 JD 的模拟面试题。
3. 用户回填真实面试经历，系统提取问题并生成训练短板。
4. 后续练习在同一维度达到 75 分时自动关闭短板；用户也可以手动关闭。
5. 工作台展示数字员工周报，让用户看到本周处理了多少岗位、哪些能力仍有缺口。

## 2. 锁定边界

| 项目 | 决定 |
| --- | --- |
| 数据来源 | 只接受用户手动粘贴 JD；不抓取招聘网站、不模拟登录、不自动投递 |
| 技术栈 | 保持 Java 21 / Spring Boot 3.4 / Spring AI 1.1.8 / Vue 3，不重写为 Python |
| 评分 | Java `MatchScorer` 做确定性评分；AI 只抽取 JD 结构、生成解释和解析回填 |
| 任务表 | `job_agent_runs`，不占用未来通用平台可能使用的 `agent_runs` |
| 执行模型 | PostgreSQL 租约任务，不引入通用 Agent 运行时、Graph 或 MCP |
| 并发 | 默认 1，适配 2 vCPU / 2 GB 的单机部署 |
| 画像 | 提取结果仅是预览；用户确认并 `PUT` 后才影响评分 |
| 隐私 | 回填必须显式确认；原文不进入 Agent 输入或日志；删除是物理删除 |
| 保留期 | 训练短板长期保留；任务执行记录默认保留 90 天；代码提交沿用 7 天 |

## 3. 用户流程

### 3.1 岗位评估

1. 用户进入 `/jobs`，粘贴 JD、公司、岗位、城市和薪资。
2. 系统校验 JD 为 80–20000 字、薪资上下限合法。
3. 创建 `job_leads` 记录并加入 `job_agent_runs`。
4. worker 原子领取任务，按 `owner + company + role + JD hash` 保证同一用户不重复建同一条岗位记录。
5. Java 规则计算技能、年限、薪资、地点、新鲜度五部分得分。
6. 模型可用时补充结构抽取和不超过 300 字的解释；模型失败时保留规则结果并标记 `degraded=true`。
7. 前端轮询任务，完成后显示分数、命中技能、缺口、建议投递结论和「生成针对练习」入口。

### 3.2 技能画像

1. 用户可以在 `/jobs/profile` 手写技能、等级、权重、年限、地点和期望薪资。
2. 粘贴简历后的提取仅做别名与年限规则预填，不保存、不改变已有评分。
3. 用户逐项检查并点击确认后，画像写入 `candidates.skill_profile_json`。
4. 画像版本由规范化内容生成 SHA-256；岗位评估用该 hash 去重，画像改变后允许重新评估。

### 3.3 真实面试回填

1. 用户进入 `/debrief/new`，填写日期、轮次、结果和面试回忆。
2. 用户必须勾选隐私确认，前端和后端都校验。
3. 原文写入 `interview_debriefs.raw_text`，同时保存 SHA-256、长度和确认时间。
4. Agent 输入只包含 `debriefId` 和 `rawTextLength`，不复制原文。
5. 解析服务读取原文并调用模型，但校验器会核对每个问题是否能在原文中找到依据，丢弃编造内容。
6. 用户可在详情页手动修正题目、标记难题、补充难点；明确标记的难题和备注会生成训练短板。
7. 删除回填时物理删除原文、题目和关联短板，二次确认后不可恢复。

## 4. 数据模型

迁移文件：`consumer/src/main/resources/db/migration/V21__job_radar_and_debrief.sql`。

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `job_leads` | 用户岗位台账 | `owner_id + dedup_key` 唯一；薪资上下限检查 |
| `job_evaluations` | 评估卡版本 | 岗位外键级联；同一 JD/画像/Prompt 版本唯一 |
| `interview_debriefs` | 真实面试回填 | 隐私确认时间非空；岗位删除时 `SET NULL` |
| `training_weak_points` | 训练短板 | 回填删除时级联；同维度达到 75 分可自动关闭 |
| `job_agent_runs` | 异步任务、租约和审计 | 幂等键唯一；同一 owner/kind/subject 同时只允许一个活动任务 |

JSON 字段使用 PostgreSQL `jsonb`，JPA 使用 `JsonNode` 映射。`candidates` 新增 `skill_profile_json jsonb`。

## 5. API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET/PUT` | `/api/v1/me/skill-profile` | 读取或确认保存画像 |
| `POST` | `/api/v1/me/skill-profile/extract` | 仅生成规则化画像预览 |
| `GET/POST` | `/api/v1/job-leads` | 分页查询或创建岗位 |
| `GET/PATCH/DELETE` | `/api/v1/job-leads/{id}` | 详情、编辑、物理删除 |
| `POST` | `/api/v1/job-leads/{id}/evaluate` | 重新评估 |
| `POST` | `/api/v1/job-leads/{id}/practice` | 针对 JD 生成练习 |
| `GET/POST` | `/api/v1/interview-debriefs` | 回填列表或创建 |
| `GET/PATCH/DELETE` | `/api/v1/interview-debriefs/{id}` | 详情、手动纠题、物理删除 |
| `GET/PATCH` | `/api/v1/training/weak-points` | 短板列表与关闭 |
| `GET` | `/api/v1/agent/work-report` | 数字员工周报 |
| `GET` | `/api/v1/agent/runs` | 当前用户最近任务，不暴露内部输入 |

所有接口都通过 Bearer Token 解析 owner。跨用户访问统一返回 404，不能通过 403/404 差异推断资源是否存在。

## 6. 异步任务与恢复

状态：`pending`、`running`、`succeeded`、`failed`、`deferred`、`cancelled`。

1. worker 用 PostgreSQL `FOR UPDATE SKIP LOCKED` 原子领取到期任务。
2. 领取时写入 `lease_expires_at`、`last_heartbeat_at` 和 `attempts + 1`。
3. 任务执行期间每 30 秒续租；进程中断后，过期租约自动回到 `pending`。
4. 普通失败按指数退避重试，达到上限后进入 `failed`。
5. 额度不足进入 `deferred`，还原本次尝试次数并在额度重置后恢复。
6. 岗位评估终态失败会恢复任务输入中记录的原状态，不覆盖用户并发归档。
7. 删除岗位或回填时，活动任务变为 `cancelled` 并清除输入。

## 7. 隐私与删除

- 不把回填原文写入 `job_agent_runs.input_json`、应用日志或浏览器错误提示。
- 模型调用本身必须读取原文，但只用于本次解析；提示词和响应正文不得进入普通日志。
- 回填详情只返回原文长度、确认时间、结构化问题和短板，不回传原文。
- 删除回填执行数据库物理删除；`training_weak_points.debrief_id` 使用 `ON DELETE CASCADE`。
- 删除岗位会删除评估并取消活动任务；已生成的练习不回删，以免误删用户训练记录。

## 8. 前端

| 路由 | 页面 | 能力 |
| --- | --- | --- |
| `/jobs` | 岗位列表 | 状态、岗位筛选、分页、评估结果与任务状态 |
| `/jobs/profile` | 技能画像 | 规则预填、技能编辑、期望薪资、确认保存 |
| `/jobs/:id` | 岗位详情 | 分项得分、命中、缺口、重新评估、生成练习 |
| `/debrief` | 回填列表 | 按日期分页，展示解析状态 |
| `/debrief/new` | 新建回填 | 隐私确认、原文录入、关联岗位/练习 |
| `/debrief/:id` | 回填详情 | 解析轮询、手动纠题、短板关闭、物理删除 |

页面文案提供简体中文、English、日本語；所有按钮等待期间必须禁用，避免重复提交。

## 9. 验收标准

### 后端

- V21 可以在已有 V1–V20 数据库上顺序应用，JSONB、唯一索引和外键级联在真实 PostgreSQL 上通过。
- 同一 owner 的公司/岗位/JD 规范化结果只产生一条岗位；修改公司或岗位时重新计算去重键，冲突返回 409。
- 不同 owner 可以使用相同去重键，互相读取返回 404。
- 画像提取不写数据库；确认保存后评分使用新 hash。
- 未经隐私确认的回填返回 400；Agent 输入序列化结果不包含原文。
- 长任务运行期间心跳持续更新；过期租约最终失败时岗位不会卡在 `evaluating`。
- 删除回填后，回填和关联短板均不存在。

### 前端

- 所有新路由在桌面与移动宽度无横向溢出。
- 列表分页、筛选、错误提示和加载状态可用。
- 画像预览和已确认状态清楚区分。
- 隐私确认、删除二次确认和解析轮询可用。
- 中文、英文、日文 key 集合一致，构建通过。

## 10. 不在本期范围

- 招聘网站爬虫、模拟登录或自动投递。
- 通用 Agent 编排器、Graph、MCP、Agent Skills 平台化。
- 向量化岗位推荐、自动批量投递、薪资预测。
- 回填原文的长期归档或对外分享。
- 训练短板的跨设备实时协作。

## 11. SPEC-DELTA

| 原规格 | 当前实现 | 原因 |
| --- | --- | --- |
| `V20__job_radar_and_debrief.sql` | `V21__job_radar_and_debrief.sql` | 仓库已有 V20 语言迁移 |
| `agent_runs` | `job_agent_runs` | 避免占用通用 Agent 表名 |
| 按 `owner + jd_hash` 去重 | `owner + company + role + jd_hash` | 同一份通用 JD 可能对应不同公司和岗位 |
| 通用并发配置 | 默认 1 | 2 vCPU / 2 GB 部署稳定性优先 |
| 画像可由 AI 提取并直接使用 | 规则预览 + 用户确认 `PUT` | 防止错误抽取直接影响评分 |
| 回填只在 UI 提示隐私 | 前后端强制确认 + 原文隔离 + 物理删除 | 满足个人信息最小化与可删除原则 |
| 仅写任务状态 | 租约 + 心跳 + 过期回收 + 额度顺延 | 防止进程中断后任务永久卡住或重复执行 |

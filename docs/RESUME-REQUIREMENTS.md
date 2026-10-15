# 简历功能需求文档

| | |
| --- | --- |
| 状态 | 简历库 / 录入解析 / AI 优化 / 逐段采纳 / 三格式导出（md、docx、pdf）**已实现并验证**；仅剩 FR-6「创建练习时从简历库选一份」待做 |
| 目标 | 用户可以把简历存进系统，按目标岗位让 AI 优化，并把结果用于面试练习 |
| 关联 | `PLAN.md`、[STATUS.md](STATUS.md) 的「遗留接口迁移还是删除」待办 |

---

## 1. 先说清一件事：项目里已经有半套

这次**不是从零做**：仓库里有一套遗留的"简历优化"，代码在、链路能跑通一半，但从没真正可用过。

### 1.1 遗留实现（现状）

| 项 | 现状 |
| --- | --- |
| 接口 | `POST /resume/upload`（上传文件 + JD）、`POST /resume/create`（结构化表单） |
| 链路 | Tika 抽正文 → **Python 脚本抽 JD 关键词** → 大模型改写 → Markdown → HTML → **Python 脚本转 PDF** → 返回 `pdf/xxx.pdf` |
| 提示词 | `resume-optimizer-system-prompt.st` / `resume-optimizer-user-prompt.st` |
| 结构化模型 | `CvRequest`：姓名/性别/生日/邮箱/电话 + 教育/工作/项目 三段列表 |
| 数据表 | `resumes`、`resume_vectors` 只存在于 `ddl/*.sql`，**没有纳入 Flyway** |
| 外部依赖 | `scripts/extract_keywords.py`、`scripts/html2pdf.py`（本机 Python） |
| 前端 | 旧前端 `frontend/frontend/views/{ResumeForm,NewCv,ModifyCv}.tsx`，**该目录已不在当前分支** |

**为什么现在调不通**：`resumes` / `resume_vectors` 表不在 Flyway 管理范围内，新库上根本没有这两张表，
调用直接 500。这与 STATUS 里记录的「遗留接口依赖未被 Flyway 管理的表」是同一件事。

### 1.2 新体系里已有的、能复用的能力

| 能力 | 位置 | 说明 |
| --- | --- | --- |
| 简历正文抽取 | `POST /api/v1/resume/extract`（`ResumeExtractionService`） | Tika 解析 PDF/DOCX，**只用于出题、不落库**；已有大小与长度保护 |
| 按简历出题 | 练习创建的 `question-plan` | 已把简历正文作为出题上下文 |
| 结构化输出 + 校验 | `EvaluationResultValidator` 那套思路 | 模型必须输出固定 JSON，校验不过就拒绝落库 |
| 所有权隔离 | `OwnerResolver` | 按账号隔离，越权返回 404 |

### 1.3 结论

**把遗留的"改写"重建到新体系上**：结构化存储 + 可回看/可编辑 + 可导出 + 与练习打通。
遗留的 `/resume/*` 接口、`CvRequest` 那套模板拼装、以及两个 Python 脚本一并退役
（关键词抽取改用模型，PDF 生成改用 Java 侧方案，见 §8.3）。

---

## 2. 范围

### 2.1 本期做

1. **简历库**：新增 / 查看 / 编辑 / 删除 / 列表
2. **两种录入**：上传文件解析，或手动填写（表单 / 粘贴）
3. **AI 优化**：按目标岗位与 JD 定向优化
4. **对比采纳**：原文与优化后并排对比，逐段采纳
5. **导出**：Markdown / Word（.docx）/ PDF
6. **与练习打通**：创建练习时从简历库选一份，自动带入正文出题

### 2.2 本期不做

- 投递跟踪 / 多岗位投递管理
- 在线 WYSIWYG 排版编辑器、简历模板市场
- "还原原简历版式"的 PDF 生成（我们按统一模板生成，不模仿原文件排版）
- 招聘方（HR）侧功能

---

## 3. 用户故事

| # | 故事 |
| --- | --- |
| US-1 | 作为求职者，我把 PDF 简历传上来，系统帮我存好，下次不用重复上传 |
| US-2 | 作为求职者，我贴一份目标 JD，让 AI 按它优化我的简历，并告诉我**为什么这么改** |
| US-3 | 作为求职者，我想看到**改前 / 改后逐段对照**，自己决定采纳哪些，而不是被整体替换 |
| US-4 | 作为求职者，我要把优化后的简历导出成文件，拿去投递 |
| US-5 | 作为求职者，我创建模拟面试时直接选我的简历，题目就能围绕它来问 |

---

## 4. 功能需求

### 4.1 简历库（FR-1）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-1.1 | 简历列表 | 显示标题、更新时间、来源（上传/手填）、是否优化过 |
| FR-1.2 | 新增简历 | 支持上传解析与手动录入两条路径 |
| FR-1.3 | 查看/编辑 | 可编辑正文；编辑后版本号 +1 并记录更新时间 |
| FR-1.4 | 删除 | 删除简历及其优化记录；再次访问返回 404 |
| FR-1.5 | 归属隔离 | 只能看到自己的简历，越权一律 404（不泄露存在性） |
| FR-1.6 | 一账号多份 | 允许保存多份（对不同岗位），列表里可区分（标题可改） |

### 4.2 录入与解析（FR-2）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-2.1 | 上传解析 | 支持 PDF / DOCX / TXT；单文件 ≤ 8MB（与现有上传一致） |
| FR-2.2 | 解析结果可编辑 | 解析出的正文先给用户过目、可修改，再保存（避免解析残缺直接入库） |
| FR-2.3 | 解析失败 | 明确提示"这份文件读不出文字"（扫描件/纯图片），引导改用手动粘贴 |
| FR-2.4 | 手动录入 | 可只粘一大段正文；也支持填写结构化字段（姓名/联系方式/教育/工作/项目） |
| FR-2.5 | 长度保护 | 正文上限（建议 20,000 字），超出截断并提示 |
| FR-2.6 | **解析必须保住行结构** | 一行一条内容：小节标题单独占一行、每个字段各占一行；PDF 里被排版折断的行接回上一行；项目符号统一成 `- `。**不允许把整篇压成一段** —— 早期实现把 `\s+` 全替换成空格，一份排版清楚的 PDF 被读成一坨「…基本信息 ● 年龄：24岁 ● 地址：…」，AI 优化与导出只能在这个糊掉的版本上继续，用户评价是"优化以后就是个垃圾" |
| FR-2.7 | 项目符号与小节名还原 | 行首的 `● / • / ▪ / ◆ / ■ / ※` 归一成 `- `；已经是"整篇一行"的旧数据，导出时按行内项目符号与小节名断开，至少让老数据也能读 |

### 4.3 AI 优化（FR-3）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-3.1 | 按 JD 优化 | 输入目标岗位 + JD，输出优化后的简历正文 |
| FR-3.2 | 结构化输出 | 模型必须输出固定 JSON（见 §8.1），校验不通过就失败重试，不落库脏数据 |
| FR-3.3 | 不编造事实 | **缺数据处用占位符**（如 `【请补充：QPS 数值】`），并在前端高亮提醒，不得编造经历或数字 |
| FR-3.4 | 改动可解释 | 每段给出改动理由（一句话）与类型：措辞优化 / 结构重排 / 补充要点 / 建议删除 |
| FR-3.5 | 保留个人信息 | 姓名、联系方式、教育背景不得删减（沿用遗留提示词的这条约束） |
| FR-3.6 | 超时与失败 | 单次调用有超时；失败给出可读原因并可重试，不静默失败 |
| FR-3.7 | 优化记录 | 每次优化保留一条记录（JD 摘要、时间、结果），可回看历史 |

### 4.4 对比与采纳（FR-4）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-4.1 | 并排对比 | 左原文 / 右优化后，**按段落对齐**（对齐失败时降级为整段列表） |
| FR-4.2 | 逐段采纳 | 每段可"采用优化版"或"保留原文" |
| FR-4.3 | 整体替换 | 提供"全部采用"，但要二次确认（会覆盖正文） |
| FR-4.4 | 采纳后落库 | 采纳结果写回简历正文并 +1 版本；未采纳的优化记录仍保留 |

### 4.5 导出（FR-5）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-5.1 | 导出 Markdown | 下载 `.md`，便于继续编辑或再喂给优化链路 |
| FR-5.2 | 导出 Word | 下载 `.docx`，用 Apache POI 直接生成 OOXML，打开后还能改（投递前微调最方便） |
| FR-5.3 | 导出 PDF | 下载 `.pdf`，用 PDFBox 在 Java 侧排版（**不依赖本机 Python**，见 §8.3） |
| FR-5.4 | 三种格式同一接口 | `GET /{id}/export?format=md\|docx\|pdf`，内容都取当前版本的正文；非法格式返回 400 并说明可选值 |
| FR-5.5 | 导出走鉴权 | 导出地址同样要带 token，不能是可猜的静态路径（遗留实现就是静态 `pdf/xxx.pdf`，要改） |
| FR-5.6 | 待补充内容可见 | 正文里的 `【请补充：…】` 在三个格式里都用琥珀色标出，打印前一眼能看到没填的地方 |

### 4.6 与练习打通（FR-6）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-6.1 | 创建练习时选简历 | 可从简历库选一份，自动带入正文 |
| FR-6.2 | 兼容旧路径 | 直接上传文件的路径保留（现状即如此） |
| FR-6.3 | 只传正文 | 出题只用正文，仍不把简历写进练习上下文之外的地方 |

### 4.7 隐私与保留（FR-7）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-7.1 | 不记正文日志 | 日志里只记长度与条数（沿用 `LogSanitizer` 与现有做法） |
| FR-7.2 | 可随时删除 | 用户删除即物理删除，不留副本 |
| FR-7.3 | 保留策略 | **待你定**：简历是用户资产，建议长期保存（与录像的 7 天保留**分开**） |

---

## 5. 数据模型（新增迁移 V8）

```sql
-- 简历本体
CREATE TABLE resumes (
    id            UUID PRIMARY KEY,
    owner_id      VARCHAR(255) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    source        VARCHAR(32)  NOT NULL,   -- upload | manual
    file_name     VARCHAR(255),
    raw_text      TEXT         NOT NULL,   -- 当前生效的正文
    version       INTEGER      NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_resumes_owner ON resumes (owner_id, updated_at DESC);

-- 每次优化的记录（可回看、可对比、可采纳）
CREATE TABLE resume_optimizations (
    id             UUID PRIMARY KEY,
    resume_id      UUID NOT NULL REFERENCES resumes(id) ON DELETE CASCADE,
    owner_id       VARCHAR(255) NOT NULL,
    role           VARCHAR(200),
    job_description TEXT,
    status         VARCHAR(32) NOT NULL,   -- pending | completed | failed
    optimized_text TEXT,
    changes_json   TEXT,                   -- 逐段改动与理由
    placeholders_json TEXT,                -- 需要用户补充的占位符
    error          TEXT,
    model          VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_resume_optimizations_resume ON resume_optimizations (resume_id, created_at DESC);
```

要迁移的遗留表：`resumes`（旧结构）与 `resume_vectors` 与上表**不兼容**，
按 §1.3 的结论**不迁移**；如确认删除遗留功能，一并删除 `ddl/resumes*.sql`、`data/Resume*.java` 等。

---

## 6. 接口设计（`/api/v1/resumes`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/resumes` | 新建（body：`{title, source, rawText}`） |
| `POST` | `/api/v1/resumes/parse` | 上传文件解析出正文（**不落库**，先给用户过目） |
| `GET` | `/api/v1/resumes` | 列表 |
| `GET` | `/api/v1/resumes/{id}` | 详情 |
| `PUT` | `/api/v1/resumes/{id}` | 保存编辑（版本 +1） |
| `DELETE` | `/api/v1/resumes/{id}` | 删除 |
| `POST` | `/api/v1/resumes/{id}/optimizations` | 发起优化（body：`{role, jobDescription}`） |
| `GET` | `/api/v1/resumes/{id}/optimizations` | 优化历史 |
| `GET` | `/api/v1/resumes/{id}/optimizations/{optId}` | 单次结果与逐段改动 |
| `POST` | `/api/v1/resumes/{id}/optimizations/{optId}/apply` | 采纳（body：`{sections:[...]}`，空数组=全部） |
| `GET` | `/api/v1/resumes/{id}/export?format=md\|docx\|pdf` | 导出（带鉴权，默认 `md`） |

沿用现有约定：Bearer Token、按 `owner_id` 隔离、越权 404、错误统一走 `RestExceptionAdvice`。

---

## 7. 前端设计

- **导航**：左侧栏在「笔试练习」附近新增**「我的简历」**（与现有导航同级）。
- **列表页** `/resumes`：卡片式列表（标题、更新时间、来源、是否优化过）+「新建」按钮。
- **详情页** `/resumes/:id`：
  - 顶部：标题、版本、导出（Markdown / PDF）、删除
  - 正文区：可直接编辑并保存
  - 「按岗位优化」区：填岗位 + JD → 发起优化 → **左原文 / 右优化后逐段对比** → 逐段勾选采纳
- **新建页** `/resumes/new`：两个页签（上传解析 / 手动填写），解析结果先预览再保存。
- 复用现有视觉规范（Element Plus、现有字号与配色、主题色跟随用户设置）。
- **占位符高亮**：优化结果里的 `【请补充：…】` 用醒目样式标出，并给出"共 N 处待补充"的提示。

---

## 8. AI 与生成设计

### 8.1 优化输出结构（必须走校验）

```json
{
  "summary": "一句话总评：这份简历相对该 JD 的主要短板",
  "sections": [
    {
      "kind": "work | project | education | skills | summary | other",
      "original": "原文该段（没有就空）",
      "optimized": "优化后该段",
      "reason": "为什么这么改（一句话）",
      "changeType": "rephrase | restructure | add_detail | remove | keep"
    }
  ],
  "placeholders": ["【请补充：订单量级】", "【请补充：QPS】"],
  "keywordCoverage": { "matched": ["Spring Boot"], "missing": ["Kubernetes"] }
}
```

校验规则（沿用 `EvaluationResultValidator` 的思路）：
`sections` 非空；每段 `optimized` 非空；`changeType` 在白名单内；`placeholders` 必须与
`optimized` 里出现的占位符一致（防止模型声称没编造、实际编了）。

### 8.2 提示词

在遗留 `resume-optimizer-*` 基础上补三条硬约束：

1. **不得编造经历、公司、时间、数字**；缺数据一律用 `【请补充：…】` 占位；
2. 姓名 / 联系方式 / 教育背景不得删减；
3. 只输出 JSON，不输出解释文字、不用 Markdown 代码块。

### 8.3 去掉 Python 依赖

| 遗留做法 | 替换方案 | 理由 |
| --- | --- | --- |
| `scripts/extract_keywords.py` 抽 JD 关键词 | 让模型在同一次调用里输出 `keywordCoverage` | 少一个外部进程、少一次往返，且关键词与改写出自同一判断 |
| `scripts/html2pdf.py` 转 PDF | Java 侧生成：Word 用 **Apache POI**（`poi-ooxml`），PDF 用 **PDFBox** 直接排版（`resume/export/ResumePdfExporter`） | 不再依赖本机装 Python 与 `PyMuPDF`，也便于容器化部署 |
| 静态目录 `external/static/pdf/` 直接可下载 | 导出接口走鉴权 | 简历是隐私内容，不能放在可猜的静态路径上 |

**关于 PDF 中文字体**：浏览器不会替我们内嵌字体，PDFBox 也不会自动找字体。`ResumePdfFonts`
先按配置项 `resume.export.pdf-font` / `resume.export.pdf-font-bold` 找，再依次试
Windows（SimHei / 微软雅黑）、macOS（PingFang）、Linux（Noto CJK / 文泉驿 / AR PL）的常见路径，
一个都没有时**明确报错**并提示怎么配，而不是生成一份乱码 PDF。
只支持 `.ttf` / `.ttc`（PDFBox 3 不支持 CFF 轮廓的 `.otf`）。

---

## 9. 非功能需求

| 类别 | 要求 |
| --- | --- |
| 隐私 | 简历属个人敏感信息：不写进日志正文、不落静态可猜路径、导出需鉴权、删除即物理删除 |
| 隔离 | 与练习一致：跨账号一律 404 |
| 容量 | 正文 ≤ 20,000 字；上传 ≤ 8MB；PDF 导出内容长度设上限 |
| 可用性 | 模型不可用时：解析功能仍可用；优化返回可读错误并允许重试，不静默失败 |
| 性能 | 优化是同步长请求（预计 10-30 秒）；前端要有进度与超时提示 |
| 可测性 | 优化链路的 JSON 校验、占位符一致性、越权隔离都要有单测；导出要有端到端断言 |

---

## 10. 验收标准

| # | 验收项 | 通过判据 |
| --- | --- | --- |
| A1 | 上传解析 | 一份真实 PDF 能解析出正文并保存；扫描件给出"读不出文字"的明确提示 |
| A2 | 手动录入 | 只粘正文也能建简历并出现在列表 |
| A3 | 隔离 | 未带 token 401；访问他人简历 404 |
| A4 | 优化（真实供应商） | 有 `Ali_API_KEY` 时，真实调用返回结构化结果，`sections` 与占位符校验通过 |
| A5 | 不编造 | 构造一份"没有量化数据"的简历，优化结果必须出现占位符，且不出现凭空数字 |
| A6 | 逐段采纳 | 只采纳其中一段后，简历正文只变那一段，版本 +1 |
| A7 | 导出 | Markdown / Word / PDF 三种格式都能下载且内容为当前版本（`.md` 开头是 `#`、`.docx` 是 `PK` 开头的 zip、`.pdf` 是 `%PDF`）；非法 `format` 返回 400 并列出可选项；**不带 token 的导出请求被拒绝（401）** |
| A8 | 与练习打通 | 创建练习时选择简历库里的简历，出题带上了该简历内容 |
| A9 | 删除 | 删除后详情/导出/优化历史全部 404 |
| A10 | 降级 | 模型不可用时返回明确错误，界面可重试，无"假成功" |

---

## 11. 分期建议

| 阶段 | 内容 | 说明 |
| --- | --- | --- |
| **一（MVP，已完成）** | FR-1 简历库 + FR-2 录入解析 + FR-3 优化 + FR-5 导出（Markdown / Word / PDF） | 能存、能优化、能拿到可直接投递的文件 |
| 二（进行中） | FR-4 逐段对比采纳 ✅ + FR-6 与练习打通（待做：创建练习时从简历库选一份） | 可用性完整 |
| 三 | 历史版本对比、按多个 JD 分别优化、投递跟踪 | 后话 |

---

## 12. 需要你决定的问题

| # | 问题 | 我的建议 |
| --- | --- | --- |
| Q1 | 简历**保留期**：长期保存还是跟练习一样 7 天？ | **长期**（用户资产），删除权交给用户 |
| Q2 | 导出格式：Markdown / Word / PDF / 都要？ | **已定：三种都要**（用户 2026-10-15 明确要求 PDF/Word，已实现：`format=md\|docx\|pdf`，前端是「导出」下拉菜单） |
| Q3 | 采纳方式：逐段采纳 vs 整体替换？ | **逐段**为主，整体替换放二次确认 |
| Q4 | 遗留 `/resume/*` 与两个 Python 脚本：删除还是留着？ | **删除**（与 STATUS 待办 4 一起决定） |
| Q5 | 是否要"结构化字段"（教育/工作/项目分栏）？ | 阶段一不做，只存正文 —— 分栏会让编辑与对比复杂一大截 |
| Q6 | 允许 AI"合理补全"到什么程度？ | **严格占位**：只补措辞与结构，数字与事实一律留占位待用户填 |

---

## 13. 这份文档和现状的关系

- 本文档**不改变**现有练习流程：出题用简历正文的能力保持不变（`/api/v1/resume/extract`）。
- 本期落地后，STATUS 里「遗留接口迁移还是删除」这一条可以缩小到只处理 `/resume/*`（本次一并退役）。
- 迁移 `V8` 是新增表，**不改动** `practice_*` / `analysis_jobs` / `candidates` 现有结构，
  对已验证的练习与分析链路零影响。

# P0 与留存四杠杆需求文档

| | |
| --- | --- |
| 文档版本 | v1.0（待确认） |
| 日期 | 2026-10-17 |
| 状态 | **Phase 1-4 已实现并验证；Phase 5 暂停（未来小程序/App）；Phase 6A（学习小组 + 作业 + 完成看板 + 分数共享）已实现并验证**；6B/6C 待做 |
| 目标 | 把 Light-Interview 从"一次性面试练习工具"变成"围绕真实求职目标的训练系统" |
| 关联 | [PRODUCT-GAP-ANALYSIS.md](PRODUCT-GAP-ANALYSIS.md)、[PRACTICE-ARCHIVE-REQUIREMENTS.md](PRACTICE-ARCHIVE-REQUIREMENTS.md)、[MISTAKE-BOOK-REQUIREMENTS.md](MISTAKE-BOOK-REQUIREMENTS.md) |

---

## 0. 这份文档要解决什么

当前产品能完成"配置 → 录题 → 评分 → 报告 → 错题重练"，但用户没有理由第二天回来。
本需求同时做两件事：

1. **P0 七项真实可用能力**：求职目标、每日训练、连续面试、转写纠错、改写重录、信任与隐私、手机可用；
2. **留存四杠杆**：目标锚定、每日短任务、可见进步、外部约束（提醒 + 同伴/班级/助教）。

完成后的核心循环：

```
录入目标岗位与面试日期
        ↓
生成 7 天计划（每天 10-15 分钟）
        ↓
每日 3 题 → 连续面试/单题 → 转写纠错 → 改写重录
        ↓
周报（进步证据 + 下周计划）
        ↓
真实面试复盘 → 调整目标 → 下一个岗位
```

---

## 1. 用户与场景

| 用户 | 典型场景 | 最在意 |
| --- | --- | --- |
| 在校生（校招） | 距面试 3-14 天，时间碎片化，预算低 | 岗位真题、能不能过、有人督促 |
| 转行/社招 | 在职，只有晚上 30 分钟 | 项目追问、系统设计、效率 |
| 班级/训练营学员 | 助教布置作业，有截止日期 | 作业完成、反馈、进度 |
| 助教/老师（P2 角色） | 管理 20-100 人 | 谁没练、谁需要帮助、批量报告 |

三个关键用户故事：

1. 作为求职者，我下周三要面 A 公司 Java 后端，我希望系统告诉我**今天练哪 3 题**。
2. 作为求职者，我答完一题后，希望**先改掉语音转写里的错字再评分**，否则我会觉得评分不公平。
3. 作为训练营学员，我希望助教能看到我**是否完成今天的作业**，并在我标记"需要帮助"时给我反馈。

---

## 2. 产品原则

1. **目标优先于功能**：没有 Goal 的练习只能是"快速练习"，不进入计划与留存统计。
2. **短任务优先于长流程**：默认一次 10-15 分钟、3 题；长面试是可选模式。
3. **先规则后模型**：每日计划、提醒、连续记录先用可解释规则，模型只做个性化补充。
4. **评分必须可解释、可修正**：任何分数都能看到依据；转写可纠错；不编造发音/情绪结论。
5. **隐私默认最小化**：班级只默认看到"完成/未完成"，分数共享必须用户主动开启。
6. **外部约束是可选增强**：提醒、班级、互评都可以关闭。

---

## 3. 范围

### 3.1 本期做

| 编号 | 能力 | 对应差距 |
| --- | --- | --- |
| A | 求职目标（Goal） | P0-1 / 杠杆 1 |
| B | 每日训练计划 + 首页今日任务 | P0-2 / 杠杆 2 |
| C | 连续面试模式 | P0-3 |
| D | 转写纠错 + 单题重评分 | P0-4 |
| E | 回答改写 + 重录（before/after） | P0-5 / 杠杆 3 |
| F | 周报与进步证据 | 杠杆 3 |
| G | 提醒与连续记录 | 杠杆 4（D1） |
| H | 学习小组/班级 + 作业 + 同伴/助教反馈 | 杠杆 4（D2/D3） |
| I | 隐私与信任 | P0-6 |
| J | 手机端录制与续练 | P0-7 |

### 3.2 本期不做

| 不做 | 原因 |
| --- | --- |
| 实时面试 Copilot | 作弊性质，和训练定位冲突 |
| 人脸/情绪/微表情评分 | 无可靠依据 + 生物特征合规风险 |
| 自研数字人视频生成 | 成本高、收益低 |
| L3 全双工语音 | 供应商能力不匹配；L2 仍未打通 |
| 企业 ATS 集成 | 没有 B 端客户前投入产出比低 |
| 公开排行榜/分数墙 | 容易制造焦虑，先做"只和自己比" |

---

## 4. 信息架构与路由

```
工作空间
├── 训练总览（首页）
│   ├── 我的目标
│   ├── 今天练什么（3 题）
│   ├── 本周进度（连续天数、完成率）
│   └── 训练档案 / 错题本（现有）
├── 求职目标 /goals
├── 今日训练 /today
├── 连续面试 /practice/:id/session?mode=continuous
├── 周报 /progress/weekly
├── 学习小组 /cohorts
├── 助教工作台 /cohorts/:id/manage
└── 提醒设置 /settings/notifications
```

---

## 5. 数据模型

> 迁移从 `V13` 开始；所有表按 `owner_id` 归属。

### 5.1 求职目标

```sql
job_goals (
  id uuid pk,
  owner_id varchar(160) not null,
  company varchar(200),
  role varchar(160) not null,
  job_description text,
  resume_id uuid,
  stage varchar(32) not null,        -- oa / phone / technical / system_design / hr / final
  interview_at timestamptz,
  status varchar(16) not null,       -- preparing / applied / interviewing / done / archived
  notes text,
  created_at, updated_at, archived_at
)
```

### 5.2 训练计划

```sql
study_plans (
  id uuid pk, goal_id uuid, owner_id varchar(160),
  start_date date, end_date date,
  status varchar(16),                -- active / completed / archived
  created_at
)

study_plan_items (
  id uuid pk, plan_id uuid, owner_id varchar(160),
  plan_date date not null,
  kind varchar(24),                  -- weak_dimension / frequent / mistake / english / assignment
  session_id uuid,
  question_ref varchar(120),
  title varchar(200),
  estimated_minutes int,
  status varchar(16),                -- pending / in_progress / completed / skipped
  position int,
  created_at, completed_at,
  unique(plan_id, plan_date, position)
)
```

### 5.3 练习扩展

```sql
alter table practice_sessions
  add column goal_id uuid,
  add column mode varchar(16) not null default 'per_question',
  add column paused_count int not null default 0;

alter table practice_answers
  add column attempt int not null default 1,
  add column is_current boolean not null default true,
  add column transcript_original text,
  add column transcript_edited_at timestamptz,
  add column rewrite_text text,
  add column rewrite_source varchar(16),
  add column rewrite_at timestamptz;

-- 原唯一约束 (session_id, question_index) 改为
-- unique(session_id, question_index, attempt)
```

### 5.4 审计与改写

```sql
answer_transcript_revisions (
  id uuid pk, answer_id uuid, owner_id varchar(160),
  before_text text, after_text text, edited_at timestamptz
)

answer_rewrites (
  id uuid pk, answer_id uuid, owner_id varchar(160),
  rewrite_text text, source varchar(16), created_at
)
```

> 原始转写必须留档，否则无法解释"为什么分数变了"。

### 5.5 周报

```sql
weekly_reports (
  id uuid pk, owner_id varchar(160),
  week_start date not null, week_end date not null,
  plan_completion numeric(5,2),
  sessions_count int,
  answers_count int,
  dimension_delta_json text,
  before_after_json text,
  next_week_json text,
  created_at,
  unique(owner_id, week_start)
)
```

### 5.6 提醒

```sql
notification_preferences (
  owner_id varchar(160) pk,
  email_enabled boolean not null default true,
  daily_reminder boolean not null default true,
  weekly_report boolean not null default true,
  interview_countdown boolean not null default true,
  reminder_time time not null default '20:00',
  timezone varchar(64) not null default 'Asia/Shanghai',
  updated_at
)

notification_log (
  id uuid pk, owner_id varchar(160), kind varchar(32), channel varchar(16),
  sent_at timestamptz, status varchar(16), error varchar(300)
)
```

### 5.7 小组

```sql
cohorts (
  id uuid pk, name varchar(120), invite_code varchar(16) unique,
  owner_id varchar(160), created_at, archived_at
)

cohort_members (
  cohort_id uuid, owner_id varchar(160),
  role varchar(16),                 -- learner / coach
  joined_at,
  primary key (cohort_id, owner_id)
)

assignments (
  id uuid pk, cohort_id uuid, title varchar(200),
  description text, due_at timestamptz,
  plan_template_json text,
  created_by varchar(160), created_at
)

assignment_submissions (
  assignment_id uuid, owner_id varchar(160),
  status varchar(16),                -- not_started / in_progress / completed
  completed_items int, total_items int,
  submitted_at,
  primary key (assignment_id, owner_id)
)
```

### 5.8 反馈

```sql
answer_reviews (
  id uuid pk, answer_id uuid, reviewer_id varchar(160),
  reviewer_role varchar(16),         -- peer / coach
  status varchar(16),                -- requested / completed / declined
  rubric_json text,
  comment text,
  created_at, completed_at
)
```

### 5.9 隐私

```sql
consent_records (
  id uuid pk, owner_id varchar(160), purpose varchar(32),
  version varchar(16), accepted_at timestamptz, ip_hash varchar(64)
)

data_export_requests (
  id uuid pk, owner_id varchar(160), status varchar(16),
  requested_at, completed_at, file_key varchar(200)
)
```

---

## 6. 功能需求

### 6.1 A / 求职目标（Goal）

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-A1 | 新建目标 | 公司、岗位必填；JD、简历、轮次、面试日期选填 |
| FR-A2 | 目标状态 | preparing / applied / interviewing / done / archived |
| FR-A3 | 倒计时 | 有日期时显示"距面试 N 天"；过期显示"已面试，去复盘" |
| FR-A4 | 练习关联 | 从目标发起练习自动带入 role、company、JD、resume，记录回挂 goal_id |
| FR-A5 | 目标详情 | 已练题数、覆盖维度、最近分数、计划完成率、下一步建议 |
| FR-A6 | 多目标 | 可同时存在多个；首页默认展示最近/最近面试的一个 |
| FR-A7 | 归档 | 归档后不进首页与计划，历史仍可查 |
| FR-A8 | 无目标兜底 | 快速练习可用，但明确"不计入计划与周报" |

### 6.2 B / 每日训练计划

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-B1 | 今日 3 题 | 默认 3 题、10-15 分钟；可设 2-5 题 |
| FR-B2 | 组成规则 | 1 道薄弱维度 + 1 道目标高频 + 1 道到期错题；不足用专项补 |
| FR-B3 | 计划生成 | 每天首次打开生成；同一天不重复；可换题，每天上限 2 次 |
| FR-B4 | 去重 | 同一题 7 天内不重复（错题重练除外） |
| FR-B5 | 完成状态 | 完成 ≥2 题算今日完成 |
| FR-B6 | 断点续练 | 中途关闭，回来继续未完成题；已录内容不丢 |
| FR-B7 | 跳过 | 记录 skipped，不计完成 |
| FR-B8 | 无目标兼容 | 用最近目标；没有则生成通用训练 |

### 6.3 C / 连续面试模式

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-C1 | 入口 | 创建练习可选"连续面试"；默认 5 题 / 20 分钟 |
| FR-C2 | 连续流程 | 自动出题、自动计时、答完自动下一题；不显示"重录" |
| FR-C3 | 追问 | 当前题内联追问，不打断整体计时 |
| FR-C4 | 暂停 | 允许暂停 1 次，记录 paused_count |
| FR-C5 | 超时 | 单题超时自动下一题；总时长到自动结束 |
| FR-C6 | 录制 | MVP 仍按题分段录制，但用户只按一次"开始面试"；不暴露重录 |
| FR-C7 | 结束 | 统一分析；报告复用现有评分与证据 |
| FR-C8 | 标识 | 报告与历史标注"连续面试" |

> MVP 不合并单文件全程录制，避免改动现有分析链路；第二阶段再合并为一条视频时间轴。

### 6.4 D / 转写纠错

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-D1 | 编辑转写 | 报告页每题可编辑；显示"原始 / 已修正" |
| FR-D2 | 重评分 | 保存后只重算该题；旧评分保留 |
| FR-D3 | 审计 | 记录 before/after、时间、编辑人；最多 3 次/题 |
| FR-D4 | 标注 | 使用修正文本的评分标"人工修正" |
| FR-D5 | 失败回滚 | 重评分失败保留旧分数并可重试 |

### 6.5 E / 改写与重录

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-E1 | 基于原话改写 | 保留原意，不编造经历；给出改写理由 |
| FR-E2 | 用户可编辑 | 改写可改后保存；可恢复 AI 原版 |
| FR-E3 | 一键重录 | 从改写页直接重录同一题，创建 attempt 2 |
| FR-E4 | before/after | 报告并列两版视频、转写、分数、维度变化 |
| FR-E5 | 当前版本 | 列表/趋势用 is_current；历史可查 |
| FR-E6 | 次数限制 | 单题最多 3 次 attempt |
| FR-E7 | 诚实校验 | 只做表达优化，不生成不存在的项目/数据 |

### 6.6 F / 周报

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-F1 | 周报生成 | 每周一生成上周报告；可手动刷新 |
| FR-F2 | 内容 | 完成率、场次、答题数、维度变化、最佳 before/after、下周 3 条建议 |
| FR-F3 | 对比 | 与上周/上月对比；无历史时说明"第一周" |
| FR-F4 | 目标视角 | 有 Goal 按 Goal 汇总；无 Goal 按账号 |
| FR-F5 | 分享图 | 生成可分享卡片，隐藏敏感转写 |

### 6.7 G / 提醒与连续记录

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-G1 | 每日提醒 | 默认 20:00；当天完成 ≥2 题不发；可关闭 |
| FR-G2 | 周报提醒 | 每周一 09:00 |
| FR-G3 | 面试倒计时 | D-7 / D-3 / D-1，附今日计划 |
| FR-G4 | 渠道 | 邮件（现有 SMTP）+ 站内；预留微信/短信 |
| FR-G5 | 连续记录 | 完成 ≥2 题记 1 天；断签不清最长连续 |
| FR-G6 | 退订 | 一键关闭全部提醒；退订链接可用 |

### 6.8 H / 小组、作业与反馈

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-H1 | 创建小组 | 助教创建，生成邀请码；可归档 |
| FR-H2 | 加入小组 | 用邀请码加入；可退出 |
| FR-H3 | 布置作业 | 计划模板 + 截止时间；站内/邮件提醒 |
| FR-H4 | 完成情况 | 学员看自己；助教看完成/未完成，默认不看分数 |
| FR-H5 | 分数共享 | 学员主动开启；默认关闭 |
| FR-H6 | 求助 | 学员标记"需要帮助"，进入助教队列 |
| FR-H7 | 同伴互评 | 匿名、结构化 3 项 + 一句建议；可关闭 |
| FR-H8 | 助教反馈 | 一条文字反馈；不直接改 AI 分数 |
| FR-H9 | 滥用防护 | 限同组、限次数、可举报 |

### 6.9 I / 隐私与信任

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-I1 | 录制同意 | 首次录制前必须同意；记录版本与时间 |
| FR-I2 | 用途说明 | 明确用于转写、评分与本人复盘；不做人脸/情绪判断 |
| FR-I3 | 保留期 | 录像/音频 7 天；成绩长期；可配置 |
| FR-I4 | 导出 | 一键导出账号数据（JSON + 报告 PDF） |
| FR-I5 | 删除 | 一键删除账号与全部媒体 |
| FR-I6 | 评分标准页 | 每个维度 0-100 的含义与证据要求 |
| FR-I7 | 审计 | 谁在何时查看了共享报告，可查 |

### 6.10 J / 手机端

| 编号 | 需求 | 验收要点 |
| --- | --- | --- |
| FR-J1 | 手机录制 | iPhone Safari / Android Chrome 可录 |
| FR-J2 | 断点续练 | 锁屏/切后台后回来可继续，已录内容不丢 |
| FR-J3 | 报告可读 | 375px 宽度可读 |
| FR-J4 | 弱网 | 上传失败本地缓存，恢复后补传 |
| FR-J5 | 权限引导 | 摄像头/麦克风被拒时给操作步骤 |

---

## 7. 关键规则

### 7.1 每日计划选择

按优先级取题直到 3 题：

1. 逾期未完成计划项；
2. 到期错题（3/7/14 天间隔）；
3. 目标岗位薄弱维度题；
4. 目标公司高频题；
5. 目标要求的英语题；
6. 通用高频题兜底。

排除：同一题 7 天内已练（错题重练除外）；当天跳过的题不再出现。

### 7.2 连续面试

- 默认 5 题 / 20 分钟；单题建议 3 分钟；
- 追问最多 2 轮/题、3 次/场；
- 只允许暂停 1 次，暂停不计时；
- 不提供重录；结束后统一分析。

### 7.3 转写修正

- 只改文本，不改视频；
- 每题最多 3 次；
- 重评分；旧分数保留；
- 报告标注"基于人工修正文本"。

### 7.4 改写

- 必须基于原话；禁止新增不存在的公司/项目/数字；
- 用户编辑后以用户版本为准；
- 重录生成新 attempt，旧 attempt 保留。

### 7.5 连续记录

- 时区默认 Asia/Shanghai；
- 当天完成 ≥2 题记 1 天；
- 断签不清零"最长连续"；
- 不做惩罚、不做付费补签。

### 7.6 提醒

- 当天已完成 ≥2 题则跳过每日提醒；
- 周报每周一 09:00；
- 倒计时 D-7 / D-3 / D-1；
- 退订立即生效；同类提醒每天最多 1 条。

### 7.7 小组隐私

- 默认只共享完成状态；
- 分数共享需主动开启；
- 同伴互评匿名，助教反馈实名；
- 举报 24h 内处理（运营规则）。

---

## 8. 接口

### 8.1 Goals

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/goals` | 列表 |
| POST | `/api/v1/goals` | 新建 |
| GET | `/api/v1/goals/{id}` | 详情（含进度摘要） |
| PUT | `/api/v1/goals/{id}` | 编辑 |
| POST | `/api/v1/goals/{id}/archive` | 归档 |

### 8.2 Plans

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/plans/today` | 今日 3 题（无则生成） |
| POST | `/api/v1/plans` | 生成 7 天计划 |
| PATCH | `/api/v1/plans/items/{id}` | 完成/跳过/换题 |
| GET | `/api/v1/plans?goalId=` | 计划列表 |

### 8.3 Sessions / Answers

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/practice-sessions` | 增加 goalId、mode |
| POST | `/api/v1/practice-sessions/{id}/pause` | 连续模式暂停 |
| POST | `/api/v1/practice-sessions/{id}/resume` | 继续 |
| POST | `/api/v1/practice-sessions/{id}/advance` | 下一题 |
| PATCH | `/api/v1/answers/{id}/transcript` | 修正转写 |
| POST | `/api/v1/answers/{id}/re-evaluate` | 单题重评分 |
| POST | `/api/v1/answers/{id}/rewrite` | 生成改写 |
| PUT | `/api/v1/answers/{id}/rewrite` | 保存改写 |
| POST | `/api/v1/answers/{id}/attempts` | 新建重录 |
| GET | `/api/v1/answers/{id}/attempts` | 历史版本 |

### 8.4 Progress / Notifications

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/progress/weekly` | 周报 |
| GET | `/api/v1/progress/streak` | 连续记录 |
| GET | `/api/v1/progress/goals/{id}` | 目标进度 |
| GET | `/api/v1/me/notifications` | 提醒偏好 |
| PUT | `/api/v1/me/notifications` | 修改 |
| POST | `/api/v1/me/notifications/test` | 测试邮件 |
| GET | `/api/v1/notifications/unsubscribe?token=` | 退订 |

### 8.5 Cohorts / Privacy

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/cohorts` | 创建（coach） |
| POST | `/api/v1/cohorts/join` | 邀请码加入 |
| GET | `/api/v1/cohorts` | 我的小组 |
| GET | `/api/v1/cohorts/{id}` | 详情 |
| POST | `/api/v1/cohorts/{id}/assignments` | 布置作业 |
| GET | `/api/v1/cohorts/{id}/progress` | 成员完成情况 |
| POST | `/api/v1/cohorts/{id}/reviews` | 请求/提交反馈 |
| POST | `/api/v1/me/consents` | 记录同意 |
| GET | `/api/v1/me/export` | 导出任务 |
| DELETE | `/api/v1/me` | 删除账号 |
| GET | `/api/v1/privacy/scoring-rubric` | 评分标准 |

---

## 9. 页面规格

### 9.1 训练总览（改版）

首屏从上到下：

1. **目标卡**：公司/岗位、距面试天数、进度、"继续今天训练"；
2. **今日 3 题**：题号、类型、预计分钟、状态、开始；
3. **本周进度**：连续天数、完成率、维度变化一句话；
4. 现有训练档案/错题本下移。

空状态：没有目标 → "添加目标岗位" / "先快速练一题"；有目标没计划 → "生成 7 天计划"。

### 9.2 目标详情

头部信息、进度、按天计划、关联练习、编辑/归档/开始连续面试。

### 9.3 连续面试页

顶部题号/计时/暂停；中部数字人与题目；底部录制状态与自动进入下一题；不显示重录。

### 9.4 报告页新增

转写编辑、改写这句、照着重录、before/after 对照。

### 9.5 周报页

本周概览、维度变化、最佳 before/after、下周建议、分享卡片。

### 9.6 小组页

我的作业/完成度/截止时间、互评入口、助教反馈；助教视图含成员完成状态与待反馈队列。

---

## 10. 权限

| 角色 | 能力 |
| --- | --- |
| learner | 自己的 Goal、计划、练习、报告、提醒、加入小组、提交作业、请求反馈 |
| coach | 创建小组、布置作业、查看成员完成状态、查看被共享的报告、提交反馈 |
| admin（后续） | 管理用户、内容、合规审计 |

鉴权统一走现有 `OwnerResolver`；coach 访问学员数据必须校验成员关系与共享开关。

---

## 11. 指标与埋点

| 指标 | 定义 | 目标 |
| --- | --- | --- |
| 激活率 | 注册 24h 内完成首场 ≥3 题 | ≥60% |
| 首场完成率 | 开始练习后完成 ≥3 题 | ≥70% |
| 今日计划完成率 | 当天完成 ≥2 题 / 有计划的用户 | ≥50% |
| 人均每周场次 | 每周完成练习场次 | ≥1.8 |
| D7 留存 | 注册第 7 天回访 | ≥25% |
| 周报打开率 | 周报邮件打开/发送 | ≥35% |
| 改写转化率 | 查看改写后点击重录 | ≥20% |
| 目标完成率 | 有 Goal 的用户中完成面试复盘 | ≥40% |

埋点：`goal_created`、`plan_generated`、`plan_item_started`、`plan_item_completed`、
`continuous_session_started`、`continuous_session_finished`、`transcript_edited`、
`answer_rewritten`、`answer_rerecorded`、`weekly_report_opened`、`reminder_sent`、
`reminder_clicked`、`cohort_joined`、`assignment_completed`、`review_requested`、`review_completed`。

---

## 12. 验收标准

| # | 验收项 | 判据 |
| --- | --- | --- |
| A1 | Goal 闭环 | 新建 Goal → 生成计划 → 从计划开始练习 → 报告回挂 Goal |
| A2 | 今日计划 | 首页 2 次点击内开始第一题；完成 2 题显示"今日完成" |
| A3 | 连续面试 | 5 题连续完成；无重录；暂停仅 1 次；报告标注连续模式 |
| A4 | 转写纠错 | 修改一个错词后重评分；分数变化可见；原始转写可查 |
| A5 | 改写重录 | 同一题出现 attempt 1/2；报告并列 before/after |
| A6 | 周报 | 生成上周数据；维度变化与建议可读；可分享 |
| A7 | 提醒 | 每日/周报/倒计时各 1 次；完成后不打扰；退订生效 |
| A8 | 小组 | 邀请码加入；助教布置作业；完成状态可见；默认看不到分数 |
| A9 | 互评/助教 | 学员请求反馈；助教收到队列；反馈可见；匿名互评可用 |
| A10 | 隐私 | 首次录制必须同意；导出可用；删除后无媒体残留 |
| A11 | 手机 | iPhone/Android 各完成一场 3 题练习；锁屏后恢复 |
| A12 | 无目标兜底 | 快速练习可用，但不进计划与周报，界面有说明 |

---

## 13. 分期

| 阶段 | 周期 | 交付 |
| --- | --- | --- |
| Phase 1 | 第 1-2 周 | Goal、首页目标卡、今日 3 题、练习关联 |
| Phase 2 | 第 3-4 周 | 连续面试、转写纠错、单题重评分 |
| Phase 3 | 第 5 周 | 改写重录、attempt 模型、before/after |
| Phase 4 | 第 6 周 | 周报、提醒、连续记录 |
| Phase 5 | 第 6-7 周 | 手机端、隐私导出/删除、评分标准 |
| Phase 6 | 第 7-8 周 | 小组、作业、助教/同伴反馈 |

依赖：Phase 3 的 attempt 迁移必须早于改写重录；Phase 6 依赖 Phase 1 的 Goal。

---

## 14. 非功能需求

| 项 | 要求 |
| --- | --- |
| 性能 | 首页 P95 < 1s（不含 AI）；今日计划 < 500ms；连续面试切题 < 300ms |
| 可用性 | 练习中断可恢复；提醒失败不轰炸；邮件失败记日志 |
| 安全 | coach 访问校验成员关系；导出/删除二次确认；token 过期处理 |
| 隐私 | 录像/音频 7 天；成绩长期；导出/删除可审计 |
| 兼容 | Chrome/Edge/Safari 最近两个大版本；iOS 16+ / Android 12+ |
| 可观测 | 计划生成、提醒发送、周报生成、连续面试完成率有指标 |
| 成本 | 每日计划优先规则，不额外调用模型；周报可用规则 + 一次模型 |

---

## 15. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 范围过大 | 8 周做不完 | 严格按 Phase；每期可独立上线 |
| 连续模式录制不稳 | 丢答案 | 分段录制 + 本地缓存；MVP 不合并单文件 |
| 转写纠错被滥用 | 改写成"标准答案" | 保留原文与 revision；评分标注人工修正 |
| 小组隐私争议 | 不愿加入 | 默认只共享完成状态；分数 opt-in |
| 邮件送达率 | 提醒无效 | 邮件 + 站内；后续微信/短信；退订合规 |
| attempt 迁移破坏旧数据 | 报告/趋势错乱 | 回填 attempt=1、is_current=true；回归全部报告用例 |
| 2C2G 资源不足 | 小组/提醒拖慢 | 定时任务错峰；数据库外部化；先限 100 人试点 |

---

## 16. 待确认问题

| # | 问题 | 建议 |
| --- | --- | --- |
| Q1 | 先做 B2C 还是学校/训练营？ | 先 B2C 验证留存，同时找一个班级试点 Phase 6 |
| Q2 | 每日计划固定 3 题？ | 默认 3，设置可 2-5；首版不做智能调参 |
| Q3 | 连续面试默认几题？ | 5 题 / 20 分钟；可按岗位模板调整 |
| Q4 | 小组显示排名？ | 不显示；只显示完成状态 |
| Q5 | 分数共享默认开还是关？ | 默认关，用户主动开 |
| Q6 | 提醒渠道先做哪个？ | 邮件 + 站内；微信/短信后续 |
| Q7 | 周报长期保留？ | 保留摘要长期；原始录像仍 7 天 |
| Q8 | 账号删除立即还是冷静期？ | 立即删除媒体与个人数据；保留匿名统计 |

---

## 17. 测试策略

1. 单元测试：计划选题、连续记录、attempt、权限、提醒去重；
2. 集成测试：Goal → Plan → Session → Report 全链路；coach 越权 404；
3. 迁移测试：V13 回填 attempt=1、is_current=true；旧报告可打开；
4. 浏览器：桌面 + iPhone Safari + Android Chrome；
5. 邮件：每日/周报/倒计时/退订；
6. 压测：20/50 并发首页与计划；连续面试 20 人同时录制；
7. 隐私：删除后数据库与对象存储无残留；导出完整。

---

## 18. Definition of Done

每个 FR 完成需同时满足：

- 代码 + 迁移 + 接口 + 前端；
- 单测/集成测试覆盖；
- 文档更新（用户手册 + 架构 + STATUS）；
- 真实浏览器验证（涉及手机需真机）；
- 埋点可查；
- 失败与空状态有可读文案；
- 不破坏现有练习、错题本、笔试、简历、外语功能。

---

## 19. 下一步

按 Phase 1 开工，第一件事：

1. 建 `job_goals` 与 `study_plans` 迁移；
2. 做 Goal CRUD 与首页目标卡；
3. 做规则版"今日 3 题"；
4. 练习关联 Goal；
5. 用 5 个真实岗位跑通"建目标 → 今日计划 → 完成一题 → 回挂进度"。

Phase 1 验收通过后，再进入连续面试与转写纠错。

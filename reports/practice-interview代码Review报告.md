# practice-interview 分支代码 Review 报告

- 审查分支：`feature/practice-interview`（与 `origin/feature/practice-interview` 同步）
- 审查时间：2026-10-18（Asia/Shanghai）
- 工作区：`C:\workspace\AI-Interview\AI-Interview`
- 审查方式：静态阅读关键 Controller/Service/媒体/鉴权/音频代码，结合本轮已执行的 HTTP 冒烟结果；未修改业务代码。
- 结论：发现 3 个需要处理的问题，其中 1 个高风险、2 个中风险；另有自动化环境风险。

## Findings

### CR-001 [P1] 分片合并信任客户端 `totalBytes`，媒体大小可被伪造

- 文件：[ChunkedUploadService.java](../consumer/src/main/java/com/guangge/Interview/practice/media/ChunkedUploadService.java)，约 108-116 行。
- 现状：`merge()` 按分片实际内容打开合并流，但调用 `storeAnswerMedia(..., request.totalBytes(), merged)`，数据库记录的大小来自客户端 JSON，而不是合并流实际读取的字节数。
- 影响：客户端可提交任意 `totalBytes`，导致 `mediaSizeBytes` 与对象实际大小不一致；后续配额、清理、下载审计、报告展示可能使用错误元数据。若 `totalBytes` 很大，还可能触发存储层错误或配额绕过。
- 复现思路：上传 1 个真实分片，merge 请求把 `totalBytes` 改成 `1` 或超大值；请求仍会按实际分片写入，但返回/持久化大小取伪造值。
- 建议：合并时统计实际字节数并与 `request.totalBytes` 比较；不一致返回 400；持久化始终使用实际值。增加边界测试（0、负数、超大、少报、多报）。

### CR-002 [P1] FFmpeg 路径默认硬编码，音频分析在多数本地环境不可用

- 文件：[AudioConverter.java](../consumer/src/main/java/com/guangge/Interview/audio/services/AudioConverter.java)，约 24-38 行。
- 现状：Windows 默认使用 `C:\software\ffmpeg-7.1-full_build\bin\ffmpeg.exe`，Linux/macOS 默认 `/usr/bin/ffmpeg`；未配置时不会从 PATH 查找，也没有启动时健康检查。
- 证据：本轮真实冒烟上传媒体后，后台分析最终 `analysis_failed`，错误为 `FFmpeg 转换失败`；项目文档虽允许 `FFMPEG_PATH`，但默认配置依赖机器上的特定 Windows 路径。
- 影响：用户可以完成录制和上传，但异步报告才失败，核心“回答 -> 转写 -> 评分”链路不可用；失败延迟且重试 3 次，浪费队列和用户等待时间。
- 建议：默认使用可执行文件 PATH 查找，配置显式路径作为覆盖；启动时检测版本并在 health/info 暴露状态；上传或入队前尽早校验媒体可解码性；错误消息区分“媒体无效”和“FFmpeg 未安装”。

### CR-003 [P2] ASR 异常被吞掉并返回空字符串，可能被误判为成功转写

- 文件：[SpeechToTextService.java](../consumer/src/main/java/com/guangge/Interview/audio/services/SpeechToTextService.java)，约 41-49 行。
- 现状：`recognizer.call()`、JSON 解析、`sentences.get(0)` 任一异常都会 `printStackTrace()` 后返回初始空字符串；调用方无法区分“确实无语音内容”和“供应商/响应解析失败”。
- 影响：空文本可能被写入完成状态，后续评分得到空答案或生成误导性报告；同时日志可能泄露供应商响应上下文，且没有结构化错误码。
- 建议：抛出明确的领域异常并交给分析任务重试/失败状态；对缺少 `sentences`、空列表、供应商错误分别处理；使用受控 logger，不打印栈到标准输出；增加 provider 异常和空结果测试。

## Positive observations

- `OwnerResolver` 对 practice API 要求 Bearer Token，并按 owner 查询实体；本轮已实际验证未认证 401、跨用户读取 404。
- 媒体签名 URL 使用 HMAC-SHA256、带过期时间和常量时间比较；签发前先做归属校验。
- 练习删除会删除媒体和回答，且本轮实际验证删除后报告返回 404。
- 分片接口检查 owner、分片序号、分片数量和单片大小，缺失分片不会合并。

## Verification

已执行：

```powershell
pwsh scripts/smoke-test.ps1 -BaseUrl http://localhost:8088 -Name smoke -Code 123456 -ExpectAnalysisReady -AnalysisTimeoutSeconds 45 -ReportOut reports/evidence/api/smoke-report.json
```

结果：登录、建练习、鉴权、跨用户隔离、播报、回答、上传、完成、签名播放、Range 下载、删除清理通过；分析 ready 相关检查失败，实际状态为 `analysis_failed`，原因是 FFmpeg 无法解码脚本默认的 17 字节占位媒体。

未执行：完整 Maven/前端单测与 Playwright 浏览器 E2E。本轮未修改配置或安装业务依赖。

## Priority order

1. CR-002：先修复 FFmpeg 可发现性和分析失败前置反馈，否则核心闭环在常见环境不可用。
2. CR-001：修正分片大小信任问题，避免元数据/配额不一致。
3. CR-003：保留 ASR 错误语义，避免空转写污染评分结果。

# 开发约定

## 分支流程（重要）

**所有开发先提交到 `feature/practice-interview`，确认没问题后再合并到 master。**

```
feature/practice-interview   ← 日常开发都在这里
        │
        │  自测通过 + 人工确认
        ▼
      master                 ← 主干，只有确认过的改动才进来
```

- 不要直接把改动提交到 `master`。
- 合并前先在分支上跑通测试（见下方命令），并把要看的点讲清楚。
- 合并用 `--ff-only`：能快进就快进，需要人工解冲突时会直接失败，而不是悄悄产生一个合并提交。

开工前先把分支同步到主干，避免下次合并又冲突：

```bash
git checkout feature/practice-interview
git merge --ff-only master      # 分支落后于主干时执行
```

合并到主干：

```bash
git checkout master
git merge --ff-only feature/practice-interview
git push origin master
```

### 回滚

`bak_1114` 是 2026-11-14 合并前的 master 备份。要整体回退：

```bash
git checkout master && git reset --hard bak_1114
```

只撤销某次改动用 `git revert <commit>` 更安全。

## 提交信息

- 标题一行，`类型(范围): 做了什么`，例如 `fix(coding): 选择题按对象读取答案`。
- 正文写**为什么**这么改，尤其是踩过的坑和取舍；不要只重复标题。
- 一个提交只做一件事，便于回滚。

## 提交前必须自测

```bash
# 后端：单元 + 需要容器的集成用例
mvn -B clean package
mvn -B test -Ddb.integration=true -Ds3.integration=true

# 后端：代码沙箱（需要 Docker）
mvn -B -pl consumer test -Dtest=CodeRunnerServiceTest -Dcode.runner.integration=true

# 前端
npm --prefix web test
npm --prefix web run build

# 端到端（需要后端在跑；-ExpectAnalysisReady 会等分析跑完）
pwsh scripts/smoke-test.ps1 -Name smoke -Code 123456 -ExpectAnalysisReady
```

## 提交前先自查

- 密钥不要进库：`Ali_API_KEY` 等只写在 `.env`（已被忽略）。
- 文档跟着改：功能变了就更新 `docs/USER-GUIDE.md`；结构变了更新 `docs/ARCHITECTURE.md`；
  状态与验证证据记到 `docs/STATUS.md`。README 只用**真实存在**的功能描述，
  不要写代码里没有的能力（之前主干 README 写过的 GPT-4 / OpenCV 情绪识别都属此类）。
- 素材类改动（数字人切帧）跑 `python scripts/split-avatar-sheet.py --preview-only` 看预览图，
  确认没有残留底色或人脸偏移再正式输出。

## 已知需要显式打开的能力

代码沙箱默认关闭，因为它会在本机执行任意用户代码：

```dotenv
CODE_RUNNER_ENABLED=true
```

只在自己可控的机器上打开；公网部署要换成独立执行节点。

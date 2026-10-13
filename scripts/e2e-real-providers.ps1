<#
.SYNOPSIS
  真实供应商端到端验证（GitHub issue #11）。

.DESCRIPTION
  在同一场练习里跑完整闭环：登录 → 建练习 → 上传真实音轨 → 结束练习 →
  真实 ASR 转写（Paraformer）→ 真实评分（Spring AI）→ 报告 ready，
  并把报告 JSON 落成审计产物。

  与 scripts/smoke-test.ps1 的区别：冒烟脚本可以用假字节 + stub 供应商跑通，
  这个脚本要求真实凭据与真实音轨，失败会明确退出码非 0。

  前置条件：
    1. .env 或环境变量里有非空的 Ali_API_KEY；
    2. 后端已启动（真实供应商配置）；
    3. ffmpeg 可用。

.EXAMPLE
  docker compose up -d
  java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
  pwsh scripts/e2e-real-providers.ps1
  pwsh scripts/e2e-real-providers.ps1 -MediaPath data/my-recording.webm   # 用真人录制的素材
#>
param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Name = 'smoke',
    [string]$Code = '123456',
    [string]$Secret = 'change-this-development-secret',
    [string]$MediaPath,
    [string]$ArtifactDir = 'artifacts/e2e',
    [int]$AnalysisTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Get-ConfiguredKey {
    if ($env:Ali_API_KEY) { return $env:Ali_API_KEY }
    $envFile = Join-Path $repoRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*Ali_API_KEY\s*=\s*(\S.*)$') { return $Matches[1].Trim() }
    }
    return $null
}

Write-Host 'Light-Interview 真实供应商 E2E' -ForegroundColor Cyan

# 1) 凭据
$key = Get-ConfiguredKey
if (-not $key) {
    Write-Host '  FAIL 未检测到 Ali_API_KEY。请在 .env 中填写，或设置同名环境变量。' -ForegroundColor Red
    Write-Host '       没有 Key 时请改用 stub 模式：pwsh scripts/smoke-test.ps1' -ForegroundColor DarkGray
    exit 2
}
Write-Host "  PASS 凭据就绪（Key 长度 $($key.Length)，不回显内容）" -ForegroundColor Green

# 2) 后端可达
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
} catch {
    Write-Host "  FAIL 后端不可达：$BaseUrl（先启动 jar）" -ForegroundColor Red
    exit 3
}
Write-Host "  PASS 后端健康：$($health.status)" -ForegroundColor Green

# 3) 素材：优先显式传入，其次复用 data/e2e-clip.mp4，必要时从 docs/step*.mp4 截一段
function Resolve-Media {
    param([string]$Explicit)
    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit)) { throw "指定的素材不存在：$Explicit" }
        return (Resolve-Path -LiteralPath $Explicit).Path
    }
    $cached = Join-Path $repoRoot 'data/e2e-clip.mp4'
    if (Test-Path -LiteralPath $cached) { return $cached }

    $source = Get-ChildItem -Path (Join-Path $repoRoot 'docs') -Filter 'step*.mp4' -File -ErrorAction SilentlyContinue |
        Sort-Object Length -Descending | Select-Object -First 1
    if (-not $source) { return $null }
    if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { return $null }

    New-Item -ItemType Directory -Path (Split-Path -Parent $cached) -Force | Out-Null
    Write-Host "  INFO 从 $($source.Name) 截取 15 秒作为测试素材" -ForegroundColor DarkGray
    & ffmpeg -hide_banner -loglevel error -ss 20 -t 15 -i $source.FullName `
        -c:v libx264 -preset veryfast -c:a aac -y $cached
    if ($LASTEXITCODE -ne 0) { throw 'ffmpeg 截取素材失败' }
    return $cached
}

$media = Resolve-Media -Explicit $MediaPath
if (-not $media) {
    Write-Host '  FAIL 找不到可用素材：请用 -MediaPath 指定一段真人录制的 webm/mp4。' -ForegroundColor Red
    exit 4
}
Write-Host "  PASS 使用素材 $media（$((Get-Item -LiteralPath $media).Length) 字节）" -ForegroundColor Green

# 4) 跑完整流程并把报告落成 artifact
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$artifactRoot = Join-Path $repoRoot $ArtifactDir
$reportPath = Join-Path $artifactRoot "report-$stamp.json"

$shell = if (Get-Command pwsh -ErrorAction SilentlyContinue) { 'pwsh' } else { 'powershell' }
& $shell -NoProfile -File (Join-Path $PSScriptRoot 'smoke-test.ps1') `
    -BaseUrl $BaseUrl -Name $Name -Code $Code -Secret $Secret -ExpectAnalysisReady `
    -MediaPath $media -AnalysisTimeoutSeconds $AnalysisTimeoutSeconds `
    -ReportOut $reportPath -KeepSession
$exitCode = $LASTEXITCODE

if (Test-Path -LiteralPath $reportPath) {
    $artifact = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    Write-Host ''
    Write-Host '真实供应商 E2E 结果：' -ForegroundColor Cyan
    Write-Host "  会话=$($artifact.sessionId)"
    Write-Host "  报告状态=$($artifact.reportStatus) 分析=$($artifact.analysisStatus) 已评分=$($artifact.evaluatedCount)/$($artifact.questionCount)"
    if ($artifact.analysisError) { Write-Host "  分析错误=$($artifact.analysisError)" -ForegroundColor Yellow }
    $first = @($artifact.report.questions)[0]
    if ($first -and $first.transcriptText) {
        $preview = $first.transcriptText
        if ($preview.Length -gt 60) { $preview = $preview.Substring(0, 60) + '...' }
        Write-Host "  第 1 题转写=$preview"
        Write-Host "  第 1 题得分=$($first.evaluation.overallScore)"
    }
    Write-Host "  审计产物=$reportPath" -ForegroundColor DarkGray
}

if ($exitCode -ne 0) {
    Write-Host "真实供应商 E2E FAILED (exit=$exitCode)" -ForegroundColor Red
    exit $exitCode
}

Write-Host '真实供应商 E2E PASSED' -ForegroundColor Green
Write-Host '注意：浏览器真摄像头录制属于人工验收，见 docs/STATUS.md。' -ForegroundColor DarkGray
exit 0

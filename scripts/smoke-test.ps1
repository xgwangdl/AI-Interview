<#
.SYNOPSIS
  对运行中的 Light-Interview 后端做一次端到端冒烟测试。

.DESCRIPTION
  覆盖：创建练习 → 逐题保存回答 → 上传录像 → 结束练习 → 读取报告 →
  签发播放地址 → 用签名地址下载录像 → 删除练习 → 确认数据不可访问。

  需要后端已启动，且 AUTH_TOKEN_SECRET 与 -Secret 一致。

.EXAMPLE
  docker compose up -d postgres redis
  java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar
  pwsh scripts/smoke-test.ps1
#>
param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Secret = 'change-this-development-secret',
    [string]$User = 'smoke',
    [string]$Name,
    [string]$Code,
    [switch]$ExpectAnalysisReady,
    [string]$MediaPath,
    [int]$AnalysisTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$api = "$BaseUrl/api/v1"
$failures = 0

function Assert-Equal($expected, $actual, $label) {
    if ("$expected" -eq "$actual") {
        Write-Host "  PASS $label ($actual)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL $label expected=[$expected] actual=[$actual]" -ForegroundColor Red
        $script:failures++
    }
}

function New-Token([string]$userName, [string]$secret) {
    $enc = [System.Text.Encoding]::UTF8
    $b64 = { param($bytes) [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
    $header = & $b64 $enc.GetBytes('{"alg":"HS256","typ":"JWT"}')
    $exp = [int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 900
    $payload = & $b64 $enc.GetBytes((@{ userName = $userName; exp = $exp } | ConvertTo-Json -Compress))
    $signingInput = "$header.$payload"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new()
    $hmac.Key = $enc.GetBytes($secret)
    return "$signingInput." + (& $b64 $hmac.ComputeHash($enc.GetBytes($signingInput)))
}

Write-Host "Light-Interview smoke test against $BaseUrl" -ForegroundColor Cyan

if ($Name -and $Code) {
    # 走真实登录接口，顺带验证身份链路。
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -Body @{ name = $Name; code = $Code }
    $token = $login.data.token
    Assert-Equal 200 $login.code '使用姓名与邀请码登录'
    if (-not $token) { throw '登录未返回 token' }
} else {
    $token = New-Token $User $Secret
}
$headers = @{ Authorization = "Bearer $token" }

$questions = @(
    @{ id = 'q-0'; text = '请介绍一个你主导的项目'; category = 'design' },
    @{ id = 'q-1'; text = '讲一个你解决过的线上问题'; category = 'incident' }
)
$sessionBody = @{
    role = 'Java 后端'; company = 'Light'; jobDescription = '负责订单系统'; resumeName = 'resume.pdf'
    interviewType = 'technical'; difficulty = 'standard'; interviewerStyle = 'balanced'
    questionCount = 2; durationMinutes = 10; questions = $questions
} | ConvertTo-Json -Depth 6 -Compress

$session = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions" -Headers $headers -ContentType 'application/json' -Body $sessionBody
Assert-Equal 2 $session.questions.Count '创建练习返回题目数'
Assert-Equal 'in_progress' $session.status '新建练习状态'

# 未带凭证与换个用户，都不应该读到这次练习。
try {
    $null = Invoke-RestMethod -Uri "$api/practice-sessions"
    Write-Host '  FAIL 未认证请求竟然成功' -ForegroundColor Red
    $failures++
} catch {
    Assert-Equal 401 ([int]$_.Exception.Response.StatusCode) '未认证请求被拒绝'
}

$intruderHeaders = @{ Authorization = "Bearer $(New-Token "$User-intruder" $Secret)" }
try {
    $null = Invoke-RestMethod -Uri "$api/practice-sessions/$($session.id)" -Headers $intruderHeaders
    Write-Host '  FAIL 其他用户读到了不属于他的练习' -ForegroundColor Red
    $failures++
} catch {
    Assert-Equal 404 ([int]$_.Exception.Response.StatusCode) '其他用户无法读取该练习'
}

# 数字人播报：文本必须拿得到；音频要么合成成功，要么干净地降级为 503，绝不能是 500。
$narration = Invoke-RestMethod -Uri "$api/practice-sessions/$($session.id)/questions/0/narration" -Headers $headers
Assert-Equal $questions[0].text $narration.text '播报文本与题目一致'
try {
    $narrationAudio = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl$($narration.audioUrl)" -Headers $headers
    Assert-Equal 200 ([int]$narrationAudio.StatusCode) '语音合成可用时返回音频'
} catch {
    $narrationStatus = [int]$_.Exception.Response.StatusCode
    if ($narrationStatus -eq 503) {
        Write-Host '  PASS 语音合成不可用时降级为 503（客户端隐藏播报控件、保留字幕）' -ForegroundColor Green
    } else {
        Write-Host "  FAIL 播报音频返回 $narrationStatus，应为 200 或 503" -ForegroundColor Red
        $script:failures++
    }
}

$answerIds = @()
foreach ($index in 0..1) {
    $answerBody = @{
        questionIndex = $index; questionId = "q-$index"
        durationSeconds = 30; startedAtOffsetSeconds = $index * 30
    } | ConvertTo-Json -Compress
    $answer = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions/$($session.id)/answers" -Headers $headers -ContentType 'application/json' -Body $answerBody
    $answerIds += $answer.id
}
Assert-Equal 2 $answerIds.Count '保存回答数'

$videoBytes = [System.Text.Encoding]::UTF8.GetBytes('smoke-video-bytes')
$videoName = 'answer.webm'
$videoContentType = 'video/webm'
if ($MediaPath) {
    # 真实供应商（ASR/TTS）需要真实音轨，假字节过不了 ffmpeg。
    $resolvedMedia = (Resolve-Path -LiteralPath $MediaPath).Path
    $videoBytes = [System.IO.File]::ReadAllBytes($resolvedMedia)
    $videoName = [System.IO.Path]::GetFileName($resolvedMedia)
    if ([System.IO.Path]::GetExtension($resolvedMedia) -ieq '.mp4') { $videoContentType = 'video/mp4' }
    Write-Host "  INFO 使用真实素材 $videoName（$($videoBytes.Length) 字节）" -ForegroundColor DarkGray
}
$client = [System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
foreach ($answerId in $answerIds) {
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $fileContent = [System.Net.Http.ByteArrayContent]::new($videoBytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($videoContentType)
    $form.Add($fileContent, 'file', $videoName)
    $response = $client.PostAsync("$api/answers/$answerId/media", $form).Result
    Assert-Equal 200 ([int]$response.StatusCode) "上传录像 $answerId"
}

$completed = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions/$($session.id)/complete" -Headers $headers
Assert-Equal 'completed' $completed.status '结束练习状态'
Assert-Equal $videoBytes.Length $completed.answers[0].mediaSizeBytes '录像字节数已记录'

$report = Invoke-RestMethod -Uri "$api/practice-sessions/$($session.id)/report" -Headers $headers
Assert-Equal 2 $report.questions.Count '报告题目数'
Write-Host "  INFO 报告状态=$($report.status)（未接分析供应商时为 awaiting_analysis）" -ForegroundColor DarkGray

# 用 stub 供应商（PRACTICE_ASR_PROVIDER=stub / PRACTICE_EVALUATION_PROVIDER=stub）时，
# 整条转写+评分链路应当真的跑完，报告进入 ready。
if ($ExpectAnalysisReady) {
    # 真实供应商下 ASR 是异步的：pending 与 processing 都算「还没完」，都要继续等。
    $deadline = (Get-Date).AddSeconds($AnalysisTimeoutSeconds)
    do {
        Start-Sleep -Seconds 3
        $report = Invoke-RestMethod -Uri "$api/practice-sessions/$($session.id)/report" -Headers $headers
    } while (($report.analysis.status -eq 'pending' -or $report.analysis.status -eq 'processing') -and (Get-Date) -lt $deadline)
    if ($report.status -ne 'ready') {
        Write-Host "  INFO 分析状态=$($report.analysis.status) 报告状态=$($report.status) 尝试次数=$($report.analysis.attempts)" -ForegroundColor DarkGray
        if ($report.analysis.error) {
            Write-Host "  INFO 分析错误=$($report.analysis.error)" -ForegroundColor DarkGray
        }
    }
    Assert-Equal 'ready' $report.status '分析完成后报告状态'
    Assert-Equal 2 $report.evaluatedCount '已评分题目数'
    if ($report.questions[0].transcriptText) {
        Write-Host '  PASS 已生成回答转写' -ForegroundColor Green
        $preview = $report.questions[0].transcriptText
        if ($preview.Length -gt 60) { $preview = $preview.Substring(0, 60) + '...' }
        Write-Host "  INFO 转写预览：$preview" -ForegroundColor DarkGray
        Write-Host "  INFO 第 1 题得分：$($report.questions[0].evaluation.overallScore)" -ForegroundColor DarkGray
    } else {
        Write-Host '  FAIL 缺少回答转写' -ForegroundColor Red
        $failures++
    }
    if ($report.questions[0].evaluation.improvementSuggestions.Count -gt 0) {
        Write-Host '  PASS 已生成改进建议' -ForegroundColor Green
    } else {
        Write-Host '  FAIL 缺少改进建议' -ForegroundColor Red
        $failures++
    }
}

$signed = Invoke-RestMethod -Uri "$api/media/$($answerIds[0])/signed-url" -Headers $headers
if ($signed.url -like "/api/v1/media/*") {
    Write-Host '  PASS 签发播放地址' -ForegroundColor Green
} else {
    Write-Host "  FAIL 签发播放地址 [$($signed.url)]" -ForegroundColor Red
    $failures++
}

$download = Join-Path $env:TEMP "light-interview-smoke-$([guid]::NewGuid()).webm"
Invoke-WebRequest -Uri "$BaseUrl$($signed.url)" -OutFile $download | Out-Null
$downloaded = [System.IO.File]::ReadAllBytes($download)
Remove-Item -LiteralPath $download -Force
Assert-Equal $videoBytes.Length $downloaded.Length '签名地址下载字节数'
$sameBytes = $downloaded.Length -eq $videoBytes.Length
if ($sameBytes) {
    for ($i = 0; $i -lt $videoBytes.Length; $i++) {
        if ($downloaded[$i] -ne $videoBytes[$i]) { $sameBytes = $false; break }
    }
}
if ($sameBytes) {
    Write-Host '  PASS 下载内容与上传一致' -ForegroundColor Green
} else {
    Write-Host '  FAIL 下载内容与上传不一致' -ForegroundColor Red
    $failures++
}

$rangeRequest = [System.Net.Http.HttpRequestMessage]::new('GET', "$BaseUrl$($signed.url)")
$rangeRequest.Headers.Range = [System.Net.Http.Headers.RangeHeaderValue]::new(0, 9)
$rangeResponse = $client.SendAsync($rangeRequest).Result
$rangeBody = $rangeResponse.Content.ReadAsByteArrayAsync().Result
Assert-Equal 206 ([int]$rangeResponse.StatusCode) '媒体端点支持 HTTP Range'
Assert-Equal 10 $rangeBody.Length 'Range 请求返回的字节数'

$null = Invoke-RestMethod -Method Delete -Uri "$api/practice-sessions/$($session.id)" -Headers $headers
Write-Host '  PASS 删除练习' -ForegroundColor Green

try {
    $null = Invoke-RestMethod -Uri "$api/practice-sessions/$($session.id)/report" -Headers $headers
    Write-Host '  FAIL 删除后报告仍可访问' -ForegroundColor Red
    $failures++
} catch {
    Assert-Equal 404 ([int]$_.Exception.Response.StatusCode) '删除后报告不可访问'
}

if ($failures -eq 0) {
    Write-Host 'smoke test PASSED' -ForegroundColor Green
    exit 0
}
Write-Host "smoke test FAILED ($failures checks)" -ForegroundColor Red
exit 1

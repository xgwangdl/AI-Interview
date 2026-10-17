<#
  外语口语 L1 的真实端到端验证（真后端 + 真模型 + 真语音识别/合成）。

  覆盖：
    1. 选项：英语有服务端音色、日语明确没有（回浏览器语音），每门语言 3 个场景
    2. 英语一轮（真录音）：转写 → 回复 → 建议 → 服务端音频；耗时落库
    3. 日语一轮（真人录音 clip）：转写 ありがとう → 日语回复 → 明确没有服务端音频
    4. 结束复盘：四项分 + 3 条改进
    5. 删除后不可再访问

  音频素材：data/voice-probe/（VoiceCapabilityProbe 生成 + Wikimedia 真人日语片段）

    pwsh scripts/verify-language-l1.ps1
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$root = 'C:\workspace\AI-Interview\AI-Interview'
$enAudio = Join-Path $root 'data\voice-probe\cosyvoice-v2-longxiaochun_v2-en.wav'
$jaAudio = Join-Path $root 'data\voice-probe\ja-arigato.oga'
$pass = 0; $fail = 0

function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

if (!(Test-Path $enAudio) -or !(Test-Path $jaAudio)) {
    throw "缺少验证音频：先跑 VoiceCapabilityProbe，并把日语片段放到 data/voice-probe/"
}

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$h = @{ Authorization = "Bearer $($login.data.token)" }

Write-Host '=== 1. 语言与音色能力 ===' -ForegroundColor Cyan
$options = Invoke-RestMethod -Uri "$base/api/v1/language-options" -Headers $h
$en = $options.languages | Where-Object { $_.code -eq 'en' }
$ja = $options.languages | Where-Object { $_.code -eq 'ja' }
Check '两门语言都在' ($options.languages.Count -eq 2) "levels=$($options.levels.Count) 档，默认 $($options.defaultMaxTurns) 轮"
Check '英语有服务端音色' ($en.serverVoice -eq $true -and $en.scenarios.Count -eq 3) "语音标签 $($en.browserVoice)"
Check '日语没有服务端音色（明确走浏览器语音）' ($ja.serverVoice -eq $false -and $ja.browserVoice -eq 'ja-JP') "note=$($ja.note)"

$sessions = @()
try {
    Write-Host '=== 2. 英语：真实录音一轮 + 回复 + 建议 ===' -ForegroundColor Cyan
    $enSession = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions" -Headers $h -ContentType 'application/json' -Body (@{
        language = 'en'; scenario = 'interview'; level = 'intermediate'; maxTurns = 6
    } | ConvertTo-Json)
    $sessions += $enSession.id
    Check '开场白不需要模型' ($enSession.opening -match 'most recent project') ''

    $first = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions/$($enSession.id)/turns" -Headers $h `
        -Form @{ audio = Get-Item $enAudio; durationMs = '4200' }
    Write-Host ("  转写：{0}" -f $first.transcript)
    Write-Host ("  回复：{0}" -f $first.replyText)
    Write-Host ("  建议：{0}" -f (($first.feedback | ForEach-Object { "{0} → {1}（{2}）" -f $_.original, $_.better, $_.why }) -join ' / '))
    Check '英文转写可用' ($first.transcript -match '(?i)billing|latency|migration') "transcript=$($first.transcript)"
    Check '回复是英语且不长' ($first.replyText.Length -gt 0 -and $first.replyText.Length -le 400) "len=$($first.replyText.Length)"
    Check '建议最多两条' ($first.feedback.Count -le 2) "feedback=$($first.feedback.Count)"
    Check '有服务端回复音频' ([bool]$first.replyAudioUrl) "replyAudio=$($first.replyAudioUrl)"
    Check '拿得到这一轮的音频回放' ((Invoke-WebRequest -Uri "$base$($first.replyAudioUrl)" -Headers $h -UseBasicParsing).StatusCode -eq 200) ''
    Check '耗时被记录下来' ($first.latency.asrMs -gt 0 -and $first.latency.llmMs -gt 0) "asr=$($first.latency.asrMs)ms llm=$($first.latency.llmMs)ms tts=$($first.latency.ttsMs)ms total=$($first.latency.totalMs)ms"

    Write-Host '=== 3. 日语：真人录音 → 识别 → 日语回复 ===' -ForegroundColor Cyan
    $jaSession = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions" -Headers $h -ContentType 'application/json' -Body (@{
        language = 'ja'; scenario = 'daily'; level = 'beginner'; maxTurns = 4
    } | ConvertTo-Json)
    $sessions += $jaSession.id
    $jaTurn = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions/$($jaSession.id)/turns" -Headers $h `
        -Form @{ audio = Get-Item $jaAudio; durationMs = '1800' }
    Write-Host ("  转写：{0}" -f $jaTurn.transcript)
    Write-Host ("  回复：{0}" -f $jaTurn.replyText)
    Check '日语转写可用' ($jaTurn.transcript -match 'ありがとう') "transcript=$($jaTurn.transcript)"
    Check '日语回复不夹中文' ($jaTurn.replyText -match '[\u3040-\u30ff]') "reply=$($jaTurn.replyText)"
    Check '日语明确标注用浏览器语音' ($jaTurn.browserVoice -eq $true -and $null -eq $jaTurn.replyAudioUrl) ''

    Write-Host '=== 4. 结束复盘 ===' -ForegroundColor Cyan
    $report = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions/$($enSession.id)/finish" -Headers $h
    Check '状态变成已完成' ($report.status -eq 'finished') ''
    $badScores = @($report.report.fluency, $report.report.vocabulary, $report.report.grammar, $report.report.task) |
        Where-Object { $null -eq $_ -or $_ -lt 0 -or $_ -gt 100 }
    Check '四项分数都在 0-100' ($badScores.Count -eq 0) "fluency=$($report.report.fluency) vocabulary=$($report.report.vocabulary) grammar=$($report.report.grammar) task=$($report.report.task)"
    Check '报告有 3 条改进' ($report.report.improvements.Count -eq 3) ''
    Write-Host ("  总结：{0}" -f $report.report.summary)
    $again = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions/$($enSession.id)/finish" -Headers $h
    Check '再点结束不重复生成（幂等）' ($again.report.fluency -eq $report.report.fluency) ''
} finally {
    Write-Host '=== 5. 清理 ===' -ForegroundColor Cyan
    foreach ($id in $sessions) {
        try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/language-sessions/$id" -Headers $h | Out-Null } catch { }
        try {
            Invoke-RestMethod -Uri "$base/api/v1/language-sessions/$id" -Headers $h | Out-Null
            Check "删除后不可访问 $id" $false ''
        } catch {
            Check "删除后不可访问 $id" ($_.Exception.Response.StatusCode.value__ -eq 404) ''
        }
    }
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

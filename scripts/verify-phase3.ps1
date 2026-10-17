<#
  Phase 3 真实验证：回答改写 → 用户编辑 → 新建 attempt → 上传媒体 → before/after 报告。

  pwsh scripts/verify-phase3.ps1
  跑完删除验证用的练习。
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$video = 'C:\workspace\AI-Interview\AI-Interview\docs\step3.mp4'
$pass = 0; $fail = 0
function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$sessionId = $null

try {
    Write-Host '=== 1. 准备一条带转写的回答 ===' -ForegroundColor Cyan
    $session = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions" -Headers $h -ContentType 'application/json' -Body (@{
        role = 'Java 后端工程师'; company = 'Light'; interviewType = 'technical'; difficulty = 'standard'
        interviewerStyle = 'balanced'; questionCount = 1; durationMinutes = 4; followUpEnabled = $false
        mode = 'per_question'
        questions = @(@{ id = 'p3-q1'; text = '讲一个你优化过的接口。'; category = '项目' })
    } | ConvertTo-Json -Depth 5)
    $sessionId = $session.id
    $answer = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/answers" -Headers $h -ContentType 'application/json' -Body (@{
        questionIndex = 0; questionId = 'p3-q1'; durationSeconds = 60; startedAtOffsetSeconds = 0
    } | ConvertTo-Json)
    docker exec light-interview-postgres psql -U postgres -d interview -c "update practice_answers set transcript_text='I lead a billing migration. I cut the checkout latency from 900ms to 100ms.', transcript_status='completed', media_key='p3-dummy', media_content_type='video/webm' where id='$($answer.id)';" | Out-Null
    Check '回答准备完成' ([bool]$answer.id) "answer=$($answer.id)"

    Write-Host '=== 2. AI 改写 + 用户编辑 ===' -ForegroundColor Cyan
    $rewrite = Invoke-RestMethod -Method Post -Uri "$base/api/v1/answers/$($answer.id)/rewrite" -Headers $h
    Check 'AI 改写返回内容' ([bool]$rewrite.rewriteText -and $rewrite.source -eq 'ai') "source=$($rewrite.source)"
    $edited = Invoke-RestMethod -Method Put -Uri "$base/api/v1/answers/$($answer.id)/rewrite" -Headers $h -ContentType 'application/json' -Body (@{
        text = 'I led the billing migration in 2024 and cut checkout latency from 900ms to 100ms.'
    } | ConvertTo-Json)
    Check '用户编辑保留为 user 来源' ($edited.source -eq 'user' -and $edited.rewriteText -match '2024') ''

    Write-Host '=== 3. 新建 attempt 并上传媒体 ===' -ForegroundColor Cyan
    $attempt = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/questions/0/attempts" -Headers $h
    Check '创建第 2 个 attempt' ($attempt.attempt -eq 2 -and $attempt.isCurrent -eq $false) "attempt=$($attempt.attempt) current=$($attempt.isCurrent)"
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/answers/$($attempt.id)/media" -Headers $h -Form @{ file = Get-Item $video } | Out-Null
    $afterUpload = Invoke-RestMethod -Uri "$base/api/v1/practice-sessions/$sessionId" -Headers $h
    Check '上传后当前版本切到 attempt 2' ($afterUpload.answers[0].attempt -eq 2 -and $afterUpload.answers[0].isCurrent -eq $true) ''

    Write-Host '=== 4. before/after 报告 ===' -ForegroundColor Cyan
    $report = Invoke-RestMethod -Uri "$base/api/v1/practice-sessions/$sessionId/report" -Headers $h
    Check '报告返回两个版本' ($report.questions[0].attempts.Count -eq 2) "attempts=$($report.questions[0].attempts.Count)"
    Check '一个当前、一个历史' (($report.questions[0].attempts | Where-Object { $_.current }).Count -eq 1) ''
    Check '当前版本带改写文本' ([bool]$report.questions[0].rewriteText) ''
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    if ($sessionId) {
        try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$sessionId" -Headers $h | Out-Null } catch { }
    }
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

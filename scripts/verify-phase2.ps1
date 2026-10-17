<#
  Phase 2 真实验证：连续面试模式 + 暂停一次 + 转写修正 + 单题重评分。

  pwsh scripts/verify-phase2.ps1
  跑完删除验证用的练习与回答。
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$pass = 0; $fail = 0
function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$sessionId = $null
$answerId = $null

try {
    Write-Host '=== 1. 连续面试模式 ===' -ForegroundColor Cyan
    $session = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions" -Headers $h -ContentType 'application/json' -Body (@{
        role = 'Java 后端工程师'
        company = 'Light'
        jobDescription = '高并发订单系统'
        interviewType = 'technical'
        difficulty = 'standard'
        interviewerStyle = 'balanced'
        questionCount = 3
        durationMinutes = 12
        followUpEnabled = $false
        mode = 'continuous'
        questions = @(
            @{ id = 'phase2-q1'; text = '请讲一个你主导的项目。'; category = '项目' },
            @{ id = 'phase2-q2'; text = '接口变慢时你怎么排查？'; category = '排查' },
            @{ id = 'phase2-q3'; text = '如何设计一个高并发任务服务？'; category = '设计' }
        )
    } | ConvertTo-Json -Depth 6)
    $sessionId = $session.id
    Check '创建了连续面试场次' ($session.mode -eq 'continuous') "mode=$($session.mode)"
    Check '初始暂停次数为 0' ($session.pausedCount -eq 0) ''

    $paused = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/pause" -Headers $h
    Check '第一次暂停成功' ($paused.pausedCount -eq 1) "paused=$($paused.pausedCount)"
    try {
        Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/pause" -Headers $h | Out-Null
        Check '第二次暂停被拒绝' $false ''
    } catch {
        Check '第二次暂停被拒绝' ($_.Exception.Response.StatusCode.value__ -eq 409) "status=$($_.Exception.Response.StatusCode.value__)"
    }

    Write-Host '=== 2. 转写修正 ===' -ForegroundColor Cyan
    $answer = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/answers" -Headers $h -ContentType 'application/json' -Body (@{
        questionIndex = 0; questionId = 'phase2-q1'; durationSeconds = 60; startedAtOffsetSeconds = 0
    } | ConvertTo-Json)
    $answerId = $answer.id
    # 不需要真转写服务：把一条"原始 ASR"写进库，验证人工修正链路
    docker exec light-interview-postgres psql -U postgres -d interview -c "update practice_answers set transcript_text='I lead a billing migration last year.', transcript_status='completed', media_key='phase2-dummy', media_content_type='video/webm', evaluation_status='completed', evaluation_json=null where id='$answerId';" | Out-Null

    $edited = Invoke-RestMethod -Method Patch -Uri "$base/api/v1/answers/$answerId/transcript" -Headers $h -ContentType 'application/json' -Body (@{
        text = 'I led a billing migration last year.'
    } | ConvertTo-Json)
    Check '修正后保留原转写' ($edited.transcriptOriginal -eq 'I lead a billing migration last year.') "original=$($edited.transcriptOriginal)"
    Check '修正次数为 1' ($edited.revisions -eq 1 -and $edited.maxRevisions -eq 3) "revisions=$($edited.revisions)/$($edited.maxRevisions)"
    $report = Invoke-RestMethod -Uri "$base/api/v1/practice-sessions/$sessionId/report" -Headers $h
    Check '旧评分被清掉，等待重评分' ($report.questions[0].evaluation -eq $null) "evaluation=$($report.questions[0].evaluation)"

    Write-Host '=== 3. 单题重评分 ===' -ForegroundColor Cyan
    $response = Invoke-WebRequest -Method Post -Uri "$base/api/v1/answers/$answerId/re-evaluate" -Headers $h -UseBasicParsing
    Check '重评分入队返回 202' ($response.StatusCode -eq 202) "status=$($response.StatusCode)"
    $jobStatus = (docker exec light-interview-postgres psql -U postgres -d interview -t -A -c "select status from analysis_jobs where answer_id='$answerId'").Trim()
    Check '分析任务被重新排队' ($jobStatus -eq 'pending') "status=$jobStatus"

    Write-Host '=== 4. 修正次数上限 ===' -ForegroundColor Cyan
    Invoke-RestMethod -Method Patch -Uri "$base/api/v1/answers/$answerId/transcript" -Headers $h -ContentType 'application/json' -Body (@{ text = 'I led the billing migration last year.' } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod -Method Patch -Uri "$base/api/v1/answers/$answerId/transcript" -Headers $h -ContentType 'application/json' -Body (@{ text = 'I led the billing migration in 2025.' } | ConvertTo-Json) | Out-Null
    try {
        Invoke-RestMethod -Method Patch -Uri "$base/api/v1/answers/$answerId/transcript" -Headers $h -ContentType 'application/json' -Body (@{ text = 'I led the billing migration in 2025 successfully.' } | ConvertTo-Json) | Out-Null
        Check '第 4 次修正被拒绝' $false ''
    } catch {
        Check '第 4 次修正被拒绝' ($_.Exception.Response.StatusCode.value__ -eq 409) "status=$($_.Exception.Response.StatusCode.value__)"
    }
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    if ($sessionId) {
        try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$sessionId" -Headers $h | Out-Null } catch { }
        try {
            Invoke-RestMethod -Uri "$base/api/v1/practice-sessions/$sessionId" -Headers $h | Out-Null
            Check '删除后练习不可访问' $false ''
        } catch {
            Check '删除后练习不可访问' ($_.Exception.Response.StatusCode.value__ -eq 404) ''
        }
    }
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

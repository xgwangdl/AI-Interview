<#
  Phase 1 真实接口验证：目标 → 今日 3 题 → 开始一题 → 练习回挂目标 → 连续记录。

  pwsh scripts/verify-goals-phase1.ps1
  跑完删除验证用的目标与练习，库里不留测试数据。
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
$goalId = $null
$sessionId = $null

try {
    Write-Host '=== 1. 创建目标 ===' -ForegroundColor Cyan
    $interviewAt = (Get-Date).AddDays(10).ToString('o')
    $goal = Invoke-RestMethod -Method Post -Uri "$base/api/v1/goals" -Headers $h -ContentType 'application/json' -Body (@{
        company = 'Light'
        role = 'Java 后端工程师'
        jobDescription = '负责高并发订单系统，要求熟悉 Spring Boot、PostgreSQL 与缓存设计。'
        stage = 'technical'
        interviewAt = $interviewAt
        status = 'preparing'
        notes = 'Phase 1 验证目标'
    } | ConvertTo-Json)
    $goalId = $goal.id
    Check '创建目标返回 201/对象' ([bool]$goal.id) "goal=$goalId"
    Check '默认轮次与技术面一致' ($goal.stage -eq 'technical') ''
    Check '倒计时约为 10 天' ($goal.daysUntilInterview -ge 9 -and $goal.daysUntilInterview -le 10) "days=$($goal.daysUntilInterview)"
    Check '进度从 0 开始' ($goal.progress.sessions -eq 0 -and $goal.progress.planItems -eq 0) ''

    Write-Host '=== 2. 今日 3 题 ===' -ForegroundColor Cyan
    $plan = Invoke-RestMethod -Uri "$base/api/v1/plans/today?goalId=$goalId" -Headers $h
    Check '生成 3 条计划' ($plan.items.Count -eq 3) "date=$($plan.date)"
    Check '第一条是薄弱项' ($plan.items[0].kind -eq 'weak_dimension') "kind=$($plan.items[0].kind)"
    Check '包含岗位高频题' (($plan.items | Where-Object { $_.kind -eq 'frequent' }).Count -ge 1) ''
    Check '计划带目标信息' ($plan.goalId -eq $goalId -and $plan.goalTitle -like '*Java 后端*') "title=$($plan.goalTitle)"

    Write-Host '=== 3. 开始一题 → 练习回挂目标 ===' -ForegroundColor Cyan
    $first = $plan.items[0]
    $session = Invoke-RestMethod -Method Post -Uri "$base/api/v1/plans/items/$($first.id)/start" -Headers $h
    $sessionId = $session.id
    Check '创建了单题练习' ($session.questionCount -eq 1) "session=$sessionId"
    Check '练习挂到目标上' ($session.goalId -eq $goalId) "goalId=$($session.goalId)"
    Check '题目来自计划模板' ($session.questions[0].id -like 'plan-*') "qid=$($session.questions[0].id)"
    $afterStart = Invoke-RestMethod -Uri "$base/api/v1/plans/today?goalId=$goalId" -Headers $h
    $startedItem = $afterStart.items | Where-Object { $_.id -eq $first.id }
    Check '计划项变为 in_progress 并记住 session' ($startedItem.status -eq 'in_progress' -and $startedItem.sessionId -eq $sessionId) ''

    Write-Host '=== 4. 完成 2 题 → 连续记录 ===' -ForegroundColor Cyan
    Invoke-RestMethod -Method Patch -Uri "$base/api/v1/plans/items/$($afterStart.items[1].id)" -Headers $h -ContentType 'application/json' -Body (@{ status='completed' } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod -Method Patch -Uri "$base/api/v1/plans/items/$($afterStart.items[2].id)" -Headers $h -ContentType 'application/json' -Body (@{ status='completed' } | ConvertTo-Json) | Out-Null
    $streak = Invoke-RestMethod -Uri "$base/api/v1/progress/streak" -Headers $h
    Check '今天完成 2 题，连续 1 天' ($streak.current -eq 1) "current=$($streak.current) longest=$($streak.longest)"
    $goalAfter = Invoke-RestMethod -Uri "$base/api/v1/goals/$goalId" -Headers $h
    Check '目标进度同步计划完成数' ($goalAfter.progress.planCompleted -eq 2) "completed=$($goalAfter.progress.planCompleted)"

    Write-Host '=== 5. 目标 CRUD ===' -ForegroundColor Cyan
    $updated = Invoke-RestMethod -Method Put -Uri "$base/api/v1/goals/$goalId" -Headers $h -ContentType 'application/json' -Body (@{
        company = 'Light'
        role = 'Java 后端工程师'
        stage = 'system_design'
        status = 'interviewing'
        notes = '第二轮验证'
    } | ConvertTo-Json)
    Check '编辑目标生效' ($updated.stage -eq 'system_design' -and $updated.status -eq 'interviewing') ''
    $sessions = Invoke-RestMethod -Uri "$base/api/v1/goals/$goalId/sessions" -Headers $h
    Check '目标下能查到练习' ($sessions.Count -eq 1) "count=$($sessions.Count)"
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    if ($sessionId) { try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$sessionId" -Headers $h | Out-Null } catch { } }
    if ($goalId) {
        try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/goals/$goalId" -Headers $h | Out-Null } catch { }
        try {
            Invoke-RestMethod -Uri "$base/api/v1/goals/$goalId" -Headers $h | Out-Null
            Check '删除后目标不可访问' $false ''
        } catch {
            Check '删除后目标不可访问' ($_.Exception.Response.StatusCode.value__ -eq 404) ''
        }
    }
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

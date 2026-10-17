<#
  Phase 6A 真实验证：助教建组 → 学员邀请码加入 → 布置作业 → 作业进入今日训练 →
  学员完成一项 → 助教看板更新 → 分数共享开关。
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$pass = 0; $fail = 0
$learner = "p6u$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$cohortId = $null
$assignmentId = $null
function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

try {
    $coachLogin = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
    $coach = @{ Authorization = "Bearer $($coachLogin.data.token)" }
    $learnerLogin = Invoke-RestMethod -Method Post -Uri "$base/register" -Body @{ name = $learner; code = '123456' }
    $student = @{ Authorization = "Bearer $($learnerLogin.data.token)" }

    Write-Host '=== 1. 创建小组与加入 ===' -ForegroundColor Cyan
    $cohort = Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts" -Headers $coach -ContentType 'application/json' -Body (@{
        name = 'Phase 6A 验证小组'
    } | ConvertTo-Json)
    $cohortId = $cohort.id
    Check '助教创建小组并拿到邀请码' ($cohort.role -eq 'coach' -and $cohort.inviteCode.Length -eq 8) "code=$($cohort.inviteCode)"

    $joined = Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/join" -Headers $student -ContentType 'application/json' -Body (@{
        inviteCode = $cohort.inviteCode
    } | ConvertTo-Json)
    Check '学员用邀请码加入' ($joined.role -eq 'learner' -and $joined.memberCount -eq 2) "members=$($joined.memberCount)"

    Write-Host '=== 2. 布置作业 → 进入今日训练 ===' -ForegroundColor Cyan
    $assignment = Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/$cohortId/assignments" -Headers $coach -ContentType 'application/json' -Body (@{
        title = '5 天项目追问训练'; description = '每天 2 题，重点练 STAR'; days = 5; itemsPerDay = 2
    } | ConvertTo-Json)
    $assignmentId = $assignment.id
    Check '作业创建成功（只把学员算作被布置者）' ($assignment.memberCount -eq 1) "learners=$($assignment.memberCount)"
    $learnerAssignments = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/assignments" -Headers $student
    Check '学员能看到作业与个人进度' ($learnerAssignments.Count -eq 1 -and $learnerAssignments[0].myTotal -eq 10) "myTotal=$($learnerAssignments[0].myTotal)"
    $today = Invoke-RestMethod -Uri "$base/api/v1/plans/today" -Headers $student
    $assignmentItems = @($today.items | Where-Object { $_.kind -eq 'assignment' })
    Check '作业项进入今日训练' ($assignmentItems.Count -ge 2) "assignmentItems=$($assignmentItems.Count)"

    Write-Host '=== 3. 完成一项 → 助教看板 ===' -ForegroundColor Cyan
    Invoke-RestMethod -Method Patch -Uri "$base/api/v1/plans/items/$($assignmentItems[0].id)" -Headers $student -ContentType 'application/json' -Body (@{ status='completed' } | ConvertTo-Json) | Out-Null
    $after = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/assignments" -Headers $coach
    $bob = $after[0].progress | Where-Object { $_.owner -eq $learner }
    Check '助教看到学员完成 1 项' ($bob.completed -eq 1) "completed=$($bob.completed)/$($bob.total)"
    Check '默认不共享分数' ($bob.shareScores -eq $false -and $null -eq $bob.averageScore) ''

    Invoke-RestMethod -Method Put -Uri "$base/api/v1/cohorts/$cohortId/members/me/score-sharing" -Headers $student -ContentType 'application/json' -Body (@{ shareScores = $true } | ConvertTo-Json) | Out-Null
    $shared = (Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/assignments" -Headers $coach)[0].progress |
        Where-Object { $_.owner -eq $learner }
    Check '学员可主动共享分数' ($shared.shareScores -eq $true) ''
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    $sql = "delete from study_plan_items where assignment_id='$assignmentId'; delete from study_plans where owner_id='$learner'; delete from cohorts where id='$cohortId'; delete from candidates where candidate_name='$learner'; delete from notification_preferences where owner_id='$learner';"
    docker exec light-interview-postgres psql -U postgres -d interview -c $sql | Out-Null
    Write-Host '  验证数据已清理' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

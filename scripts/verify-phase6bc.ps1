<#
  Phase 6B/6C 真实验证：求助 → 助教反馈；匿名同伴互评（只看转写）。
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$pass = 0; $fail = 0
$alice = "p6a$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$bob = "p6b$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$cohortId = $null
$aliceSession = $null
$bobSession = $null
function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

function New-Session($headers, $qid) {
    return Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions" -Headers $headers -ContentType 'application/json' -Body (@{
        role = 'Java 后端'; interviewType = 'technical'; difficulty = 'standard'; interviewerStyle = 'balanced'
        questionCount = 1; durationMinutes = 4; followUpEnabled = $false; mode = 'per_question'
        questions = @(@{ id = $qid; text = '请讲一个你优化过的接口。'; category = '项目' })
    } | ConvertTo-Json -Depth 5)
}

try {
    $coachLogin = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
    $coach = @{ Authorization = "Bearer $($coachLogin.data.token)" }
    $aLogin = Invoke-RestMethod -Method Post -Uri "$base/register" -Body @{ name = $alice; code = '123456' }
    $bLogin = Invoke-RestMethod -Method Post -Uri "$base/register" -Body @{ name = $bob; code = '123456' }
    $a = @{ Authorization = "Bearer $($aLogin.data.token)" }
    $b = @{ Authorization = "Bearer $($bLogin.data.token)" }

    $cohort = Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts" -Headers $coach -ContentType 'application/json' -Body (@{ name = 'P6BC 验证小组' } | ConvertTo-Json)
    $cohortId = $cohort.id
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/join" -Headers $a -ContentType 'application/json' -Body (@{ inviteCode=$cohort.inviteCode } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/join" -Headers $b -ContentType 'application/json' -Body (@{ inviteCode=$cohort.inviteCode } | ConvertTo-Json) | Out-Null

    Write-Host '=== 6B 求助与助教反馈 ===' -ForegroundColor Cyan
    $aliceSession = New-Session $a 'p6bc-a'
    $aliceAnswer = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$($aliceSession.id)/answers" -Headers $a -ContentType 'application/json' -Body (@{ questionIndex=0; questionId='p6bc-a'; durationSeconds=60; startedAtOffsetSeconds=0 } | ConvertTo-Json)
    docker exec light-interview-postgres psql -U postgres -d interview -c "update practice_sessions set status='completed' where id='$($aliceSession.id)'; update practice_answers set transcript_text='我优化了订单接口，但说不清具体怎么做的。', transcript_status='completed' where id='$($aliceAnswer.id)';" | Out-Null

    $help = Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/$cohortId/help" -Headers $a -ContentType 'application/json' -Body (@{
        sessionId=$aliceSession.id; questionIndex=0; answerId=$aliceAnswer.id; note='我不知道怎么把优化过程说清楚'
    } | ConvertTo-Json)
    Check '学员发出求助' ($help.status -eq 'open') "id=$($help.id)"
    $queue = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/help?status=open" -Headers $coach
    Check '助教看到求助队列' ($queue.Count -eq 1 -and $queue[0].owner -eq $alice) "count=$($queue.Count)"
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/$cohortId/help/$($help.id)/feedback" -Headers $coach -ContentType 'application/json' -Body (@{ text='先讲瓶颈，再讲你做了什么改动，最后给数字结果。' } | ConvertTo-Json) | Out-Null
    $mine = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/help" -Headers $a
    Check '学员看到助教反馈' ($mine[0].status -eq 'answered' -and $mine[0].feedback -like '*瓶颈*') ''

    Write-Host '=== 6C 匿名同伴互评 ===' -ForegroundColor Cyan
    $bobSession = New-Session $b 'p6bc-b'
    $bobAnswer = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$($bobSession.id)/answers" -Headers $b -ContentType 'application/json' -Body (@{ questionIndex=0; questionId='p6bc-b'; durationSeconds=60; startedAtOffsetSeconds=0 } | ConvertTo-Json)
    docker exec light-interview-postgres psql -U postgres -d interview -c "update practice_sessions set status='completed' where id='$($bobSession.id)'; update practice_answers set transcript_text='我把三次查询合并成一次，接口从 900ms 降到 100ms。', transcript_status='completed' where id='$($bobAnswer.id)';" | Out-Null

    Invoke-RestMethod -Method Put -Uri "$base/api/v1/cohorts/$cohortId/members/me/peer-review" -Headers $a -ContentType 'application/json' -Body (@{ enabled=$true } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod -Method Put -Uri "$base/api/v1/cohorts/$cohortId/members/me/peer-review" -Headers $b -ContentType 'application/json' -Body (@{ enabled=$true } | ConvertTo-Json) | Out-Null
    $tasks = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/peer-reviews/tasks?limit=5" -Headers $a
    Check 'A 看到 B 的匿名待评价转写' ($tasks.Count -eq 1 -and $tasks[0].transcript -like '*900ms*') "count=$($tasks.Count)"
    Check '任务只暴露匿名别名' ($tasks[0].alias -like '同伴*') "alias=$($tasks[0].alias)"
    Invoke-RestMethod -Method Post -Uri "$base/api/v1/cohorts/$cohortId/peer-reviews" -Headers $a -ContentType 'application/json' -Body (@{
        answerId=$bobAnswer.id; clarity=4; relevance=5; depth=4; suggestion='结果很清楚，可以再补一句这个优化对线上稳定性的影响。'
    } | ConvertTo-Json) | Out-Null
    $received = Invoke-RestMethod -Uri "$base/api/v1/cohorts/$cohortId/peer-reviews/received" -Headers $b
    Check 'B 收到匿名互评' ($received.Count -eq 1 -and $received[0].suggestion -like '*稳定性*') ''
    Check '收到的评价不暴露评价者用户名' ($received[0].reviewerAlias -like '同伴*') "alias=$($received[0].reviewerAlias)"
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    foreach ($s in @($aliceSession, $bobSession)) {
        if ($s) { try { Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$($s.id)" -Headers $coach | Out-Null } catch { } }
    }
    $sql = "delete from study_plan_items where assignment_id is not null and owner_id in ('$alice','$bob'); delete from study_plans where owner_id in ('$alice','$bob'); delete from cohorts where id='$cohortId'; delete from candidates where candidate_name in ('$alice','$bob');"
    docker exec light-interview-postgres psql -U postgres -d interview -c $sql | Out-Null
    Write-Host '  验证数据已清理' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

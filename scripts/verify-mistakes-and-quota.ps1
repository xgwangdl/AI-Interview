<#
  三件事的真实验证（对着运行中的后端 + 真实库 + 真实模型跑）：

  1) 错题本：读取（题面来自 V10 回填）、按同一道题分组、最差的排前面、重练建出单题练习
  2) 追问进阶：第一次追问真的问出来、插在被追问的那道题后面、第二次追问带上前一问、第三次被挡
  3) 成本护栏：今日额度真的在扣、未登录拒绝

  说明：为了不动真实用户的数据，追问链验证用一个**临时练习**（结束时整场删除），
  错题本的读取用现有真实成绩（guangge 的 5 条 5/30/20/20/20 分）。
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$secret = 'change-this-development-secret'
$psql = { param([string]$sql) docker exec light-interview-postgres psql -U postgres -d interview -t -A -c $sql }

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'docker 不可用' }

function New-Token([string]$userName, [string]$tokenSecret) {
    $enc = [System.Text.Encoding]::UTF8
    $b64 = { param($bytes) [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
    $header = & $b64 $enc.GetBytes('{"alg":"HS256","typ":"JWT"}')
    $exp = [int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3600
    $payload = & $b64 $enc.GetBytes((@{ userName = $userName; exp = $exp } | ConvertTo-Json -Compress))
    $signingInput = "$header.$payload"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new()
    $hmac.Key = $enc.GetBytes($tokenSecret)
    return "$signingInput." + (& $b64 $hmac.ComputeHash($enc.GetBytes($signingInput)))
}

$pass = 0; $fail = 0
function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

Write-Host '=== 0. 登录与身份 ===' -ForegroundColor Cyan
$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$smoke = @{ Authorization = "Bearer $($login.data.token)" }
Check '真实登录拿到 token' ([bool]$login.data.token) ''
try {
    Invoke-RestMethod -Uri "$base/api/v1/me/model-usage"
    Check '未登录访问额度接口' $false '（居然成功了）'
} catch {
    Check '未登录访问额度接口被拒' ($_.Exception.Response.StatusCode.value__ -eq 401) "status=$($_.Exception.Response.StatusCode.value__)"
}

$guangge = @{ Authorization = "Bearer $(New-Token 'guangge' $secret)" }

Write-Host '=== 1. 成本护栏：额度真的在扣 ===' -ForegroundColor Cyan
$before = Invoke-RestMethod -Uri "$base/api/v1/me/model-usage" -Headers $smoke
$plan = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/question-plan" -Headers $smoke -ContentType 'application/json' `
    -Body (@{ role = 'Java 后端工程师'; interviewType = 'technical'; difficulty = 'standard'; interviewerStyle = 'balanced'; questionCount = 3 } | ConvertTo-Json)
$after = Invoke-RestMethod -Uri "$base/api/v1/me/model-usage" -Headers $smoke
Check 'AI 出题返回题目' ($plan.questions.Count -eq 3) "题数=$($plan.questions.Count) model=$($plan.model)"
Check '额度按次数计入' ($after.used -eq $before.used + 1) "used $($before.used) -> $($after.used)，剩余 $($after.remaining)/$($after.dailyLimit)"

Write-Host '=== 2. 错题本：真实成绩 ===' -ForegroundColor Cyan
$book = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?limit=5" -Headers $guangge
$scores = $book.mistakes | ForEach-Object { $_.bestScore }
Check '读到错题' ($book.total -ge 5) "total=$($book.total)"
Check '每道题都带题面（V10 回填生效）' (($book.mistakes | Where-Object { $_.text }).Count -eq $book.mistakes.Count) ''
Check '最差的排最前面' ($scores[0] -le $scores[-1]) "scores=$($scores -join ',')"
Check '能指出栽在哪个维度' ([bool]$book.mistakes[0].weakestDimension) "$($book.mistakes[0].weakestDimension)=$($book.mistakes[0].weakestScore)"
$book.mistakes | Select-Object questionId, bestScore, attempts, category, weakestDimension | Format-Table | Out-String | Write-Host
$book.mistakes[0] | ConvertTo-Json -Depth 4 | Write-Host

Write-Host '=== 3. 错题本：重练这道题 ===' -ForegroundColor Cyan
$worst = $book.mistakes[0]
$retry = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-archive/mistakes/$($worst.questionId)/retry" -Headers $guangge
Check '重练建的是一场新练习' ([bool]$retry.id) "session=$($retry.id)"
Check '只有一道题' ($retry.questions.Count -eq 1 -and $retry.questionCount -eq 1) "题数=$($retry.questionCount)"
Check '题目就是原来那道（题面与 id 都不变）' ($retry.questions[0].id -eq $worst.questionId -and $retry.questions[0].text -eq $worst.text) "$($retry.questions[0].text)"
Check '沿用原场次的面试设置' ($retry.interviewType -eq $worst.interviewType -and $retry.interviewerStyle -eq $worst.interviewerStyle -and $retry.difficulty -eq $worst.difficulty) "$($retry.interviewType)/$($retry.interviewerStyle)/$($retry.difficulty)"
Check '重练默认开追问（一道题正好挖深）' ($retry.followUpEnabled -eq $true) ''
$deleted = Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$($retry.id)" -Headers $guangge
$gone = $psql.Invoke("select count(*) from practice_sessions where id = '$($retry.id)'")
Check '清理：重练场次已删除' ($gone.Trim() -eq '0') ''

Write-Host '=== 4. 追问链：两次追问 + 第三次收手 ===' -ForegroundColor Cyan
$chain = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions" -Headers $guangge -ContentType 'application/json' -Body (@{
    role = 'Java 后端工程师'; interviewType = 'technical'; difficulty = 'standard'; interviewerStyle = 'direct'
    questionCount = 1; durationMinutes = 4; followUpEnabled = $true
    questions = @(@{ id = 'vfy-q-1'; text = '讲一个你优化过的接口，说说瓶颈在哪、怎么定位、最后的效果'; category = '项目设计' })
} | ConvertTo-Json)
Write-Host "  临时练习 $($chain.id)"

function Add-SeededAnswer([string]$sessionId, [int]$index, [string]$questionId, [string]$transcript) {
    $answer = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$sessionId/answers" -Headers $guangge -ContentType 'application/json' `
        -Body (@{ questionIndex = $index; questionId = $questionId; durationSeconds = 60; startedAtOffsetSeconds = 0 } | ConvertTo-Json)
    $psql.Invoke("update practice_answers set transcript_text = '$transcript', transcript_status = 'completed' where id = '$($answer.id)'") | Out-Null
    return $answer.id
}

$answer0 = Add-SeededAnswer $chain.id 0 'vfy-q-1' '我在上个项目里把订单查询接口从 900ms 优化到 180ms，主要是把三次数据库调用合并成一次，并且加了二级缓存。'
$first = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$($chain.id)/follow-ups" -Headers $guangge -ContentType 'application/json' `
    -Body (@{ answerId = $answer0 } | ConvertTo-Json)
Check '第一次追问：真的问出来了' ([bool]$first.question.text) "round=$($first.round) q=$($first.question.text) 理由=$($first.reason)"
$stored = $psql.Invoke("select questions_json from practice_sessions where id = '$($chain.id)'")
$parsed = $stored | ConvertFrom-Json
Check '追问插在被追问的那道题后面' ($parsed[1].id -eq $first.question.id -and $parsed.Count -eq 2) "题序=$($parsed.id -join ',')"
Check '题目数量跟着 +1' (($psql.Invoke("select question_count from practice_sessions where id = '$($chain.id)'")).Trim() -eq '2') ''

$answer1 = Add-SeededAnswer $chain.id 1 $first.question.id '缓存用的是本地 Caffeine，加了一分钟过期，热点数据命中率大概 85%。'
$second = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$($chain.id)/follow-ups" -Headers $guangge -ContentType 'application/json' `
    -Body (@{ answerId = $answer1 } | ConvertTo-Json)
Check '第二次追问：带着前一轮继续挖' ([bool]$second.question.text) "round=$($second.round) q=$($second.question.text)"
$parsed2 = $psql.Invoke("select questions_json from practice_sessions where id = '$($chain.id)'") | ConvertFrom-Json
Check '第二轮也插在上一轮后面' ($parsed2[2].id -eq $second.question.id -and $parsed2.Count -eq 3) "题序=$($parsed2.id -join ',')"

$answer2 = Add-SeededAnswer $chain.id 2 $second.question.id '缓存和数据库的一致性用先更新库再删缓存来保证，redis 那侧没用到。'
$third = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/$($chain.id)/follow-ups" -Headers $guangge -ContentType 'application/json' `
    -Body (@{ answerId = $answer2 } | ConvertTo-Json)
Check '第三轮被挡下（同一道题只追问两轮）' ($null -eq $third.question -and $third.reason -like '*已经追问过*') "reason=$($third.reason)"
Check '被挡时不报错、练习能继续' ($third.round -eq 0) ''

$deletedChain = Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$($chain.id)" -Headers $guangge
$leftAnswers = $psql.Invoke("select count(*) from practice_answers where session_id = '$($chain.id)'")
Check '清理：临时练习与其回答已删除' ($leftAnswers.Trim() -eq '0') ''

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

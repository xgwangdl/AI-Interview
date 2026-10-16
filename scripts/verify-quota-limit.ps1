<#
  成本护栏的额度验证：把每日上限临时压到 2，确认真的是"第 3 次被拦住"，
  而且拦得清楚（429 + 人话），不是 500 也不是悄悄失败。跑完把后端按默认上限（120）重启回来。

  用 -SkipHttpErrorCheck 读 429 的响应体：不靠异常，顺带验证返回的是统一错误体 {code, message}。
  Redis 里的计数先清掉，脚本才可以反复跑。
#>
$ErrorActionPreference = 'Stop'
$root = 'C:\workspace\AI-Interview\AI-Interview'
$base = 'http://localhost:8088'
$jar = 'consumer/target/consumer-0.0.1-SNAPSHOT.jar'
$pass = 0; $fail = 0

function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

function Restart-Backend([string]$dailyLimit) {
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 3
    if ($dailyLimit) { $env:QUOTA_MODEL_DAILY_LIMIT = $dailyLimit } else { Remove-Item Env:\QUOTA_MODEL_DAILY_LIMIT -ErrorAction SilentlyContinue }
    Start-Process -FilePath 'java' -ArgumentList '-jar', $jar -WorkingDirectory $root `
        -WindowStyle Hidden -RedirectStandardOutput "$root\data\backend.log" -RedirectStandardError "$root\data\backend.err.log"
    foreach ($attempt in 1..40) {
        Start-Sleep -Seconds 3
        try { if ((Invoke-WebRequest -Uri "$base/actuator/health" -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200) { return } } catch { }
    }
    throw '后端没起来'
}

function Reset-QuotaKeys() {
    $keys = @(docker exec light-interview-redis redis-cli --raw keys 'quota:model*')
    if ($keys.Count -gt 0) { docker exec light-interview-redis redis-cli del @keys | Out-Null }
    return $keys.Count
}

Write-Host '=== 计数确实在 Redis（多实例共享、重启不丢）===' -ForegroundColor Cyan
$cleared = Reset-QuotaKeys
$before = @(docker exec light-interview-redis redis-cli --raw keys 'quota:model*')
Write-Host "  （重置了 $cleared 个额度键，剩下 $($before.Count) 个；脚本因此可以反复跑）"

Write-Host '=== 把每日上限压到 2 后重启 ===' -ForegroundColor Cyan
Restart-Backend '2'

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$usage = Invoke-RestMethod -Uri "$base/api/v1/me/model-usage" -Headers $h
Check '上限读到了环境变量' ($usage.dailyLimit -eq 2) "dailyLimit=$($usage.dailyLimit)"

$body = @{ role = 'Java 后端工程师'; interviewType = 'technical'; difficulty = 'standard'; interviewerStyle = 'balanced'; questionCount = 2 } | ConvertTo-Json
$first = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/question-plan" -Headers $h -ContentType 'application/json' -Body $body
$second = Invoke-RestMethod -Method Post -Uri "$base/api/v1/practice-sessions/question-plan" -Headers $h -ContentType 'application/json' -Body $body
Check '前两次照常出题（真模型）' ($first.questions.Count -eq 2 -and $second.questions.Count -eq 2) "model=$($first.model)"

$third = Invoke-WebRequest -Method Post -Uri "$base/api/v1/practice-sessions/question-plan" -Headers $h `
    -ContentType 'application/json' -Body $body -SkipHttpErrorCheck
$thirdBody = $third.Content | ConvertFrom-Json
Check '第三次被挡下，状态码 429' ($third.StatusCode -eq 429) "status=$($third.StatusCode)"
Check '返回统一错误体 + 人话' ($thirdBody.code -eq 429 -and $thirdBody.message -like '*额度*') "message=$($thirdBody.message)"

$after = Invoke-RestMethod -Uri "$base/api/v1/me/model-usage" -Headers $h
Check '剩余额度归零（被拒的那次没算进去）' ($after.remaining -eq 0 -and $after.used -eq 2) "used=$($after.used)/$($after.dailyLimit)"

$redisKeys = @(docker exec light-interview-redis redis-cli --raw keys 'quota:model*')
Check '计数落在 Redis（重启也不会清零）' ($redisKeys.Count -ge 1) ($redisKeys -join ' ')

Write-Host '=== 其它花钱的入口同样被挡住（语音合成）===' -ForegroundColor Cyan
$session = (Invoke-RestMethod -Uri "$base/api/v1/practice-sessions" -Headers $h | Where-Object { $_.answers.Count -gt 0 })[0]
$tts = Invoke-WebRequest -Uri "$base/api/v1/practice-sessions/$($session.id)/questions/0/narration/audio" -Headers $h `
    -UseBasicParsing -SkipHttpErrorCheck -TimeoutSec 20
Check '额度用完时语音合成也是 429（不是 500）' ($tts.StatusCode -eq 429) "status=$($tts.StatusCode)"

Write-Host '=== 恢复默认上限并重启 ===' -ForegroundColor Cyan
Restart-Backend $null
$login2 = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$back = Invoke-RestMethod -Uri "$base/api/v1/me/model-usage" -Headers @{ Authorization = "Bearer $($login2.data.token)" }
Check '默认上限恢复' ($back.dailyLimit -eq 120) "dailyLimit=$($back.dailyLimit)"
Check '当天已用次数跟着 Redis 一起活下来了' ($back.used -eq 2) "used=$($back.used)（重启不清零，额度按自然日算）"

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

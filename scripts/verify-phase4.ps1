<#
  Phase 4 真实验证：周报接口 + 提醒偏好 + 周报落库。
  pwsh scripts/verify-phase4.ps1
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

try {
    Write-Host '=== 1. 周报接口 ===' -ForegroundColor Cyan
    $weekly = Invoke-RestMethod -Uri "$base/api/v1/progress/weekly" -Headers $h
    Check '返回周起始/结束' ([bool]$weekly.weekStart -and [bool]$weekly.weekEnd) "$($weekly.weekStart) → $($weekly.weekEnd)"
    Check '周起始是周一' (([DateTime]$weekly.weekStart).DayOfWeek -eq 'Monday') "day=$(([DateTime]$weekly.weekStart).DayOfWeek)"
    Check '字段完整' ($null -ne $weekly.planTotal -and $null -ne $weekly.completionRate -and $null -ne $weekly.suggestions) ''
    Check '至少给出一条下周建议' ($weekly.suggestions.Count -ge 1) "$($weekly.suggestions[0])"
    $stored = docker exec light-interview-postgres psql -U postgres -d interview -t -A -c "select count(*) from weekly_reports where owner_id='smoke' and week_start='$($weekly.weekStart)';"
    Check '周报落库（同周一条）' ($stored.Trim() -eq '1') "rows=$($stored.Trim())"

    Write-Host '=== 2. 提醒偏好 ===' -ForegroundColor Cyan
    $defaults = Invoke-RestMethod -Uri "$base/api/v1/me/notifications" -Headers $h
    Check '默认每日提醒开启' ($defaults.dailyReminder -eq $true -and $defaults.weeklyReport -eq $true) "time=$($defaults.reminderTime)"
    $saved = Invoke-RestMethod -Method Put -Uri "$base/api/v1/me/notifications" -Headers $h -ContentType 'application/json' -Body (@{
        emailEnabled = $true; dailyReminder = $false; weeklyReport = $true
        interviewCountdown = $false; reminderTime = '21:15'; timezone = 'Asia/Shanghai'
    } | ConvertTo-Json)
    Check '保存提醒时间与开关' ($saved.dailyReminder -eq $false -and $saved.reminderTime -eq '21:15') ''
    $reloaded = Invoke-RestMethod -Uri "$base/api/v1/me/notifications" -Headers $h
    Check '重新读取仍为保存值' ($reloaded.dailyReminder -eq $false -and $reloaded.interviewCountdown -eq $false) ''
    $test = Invoke-RestMethod -Method Post -Uri "$base/api/v1/me/notifications/test" -Headers $h
    Check '测试邮件接口返回结果' ($null -ne $test.sent) "sent=$($test.sent)（无邮箱时为 false）"
} finally {
    Write-Host '=== 清理 ===' -ForegroundColor Cyan
    docker exec light-interview-postgres psql -U postgres -d interview -c "delete from notification_preferences where owner_id='smoke'; delete from weekly_reports where owner_id='smoke';" | Out-Null
    Write-Host '  验证数据已清理' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

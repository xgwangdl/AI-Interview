<#
.SYNOPSIS
  验证个人资料、外观偏好和修改密码接口。

.DESCRIPTION
  默认使用演示账号 smoke / 123456，并在结束时恢复原资料和密码。
  需要后端已在 -BaseUrl 启动，且账号密码与参数一致。
#>
param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Name = 'smoke',
    [string]$Code = '123456'
)

$ErrorActionPreference = 'Stop'
$api = "$BaseUrl/api/v1"
$temporaryCode = 'ProfileCheck123!'
$passwordChanged = $false
$originalProfile = $null
$headers = $null
$failures = 0

function Assert-Value($expected, $actual, $label) {
    if ("$expected" -eq "$actual") {
        Write-Host "  PASS $label ($actual)" -ForegroundColor Green
    } else {
        Write-Host "  FAIL $label expected=[$expected] actual=[$actual]" -ForegroundColor Red
        $script:failures++
    }
}

function Login([string]$password) {
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -Body @{
        name = $Name
        code = $password
    }
    if (-not $response.data.token) { throw '登录未返回 token' }
    return $response.data.token
}

function ProfileBody($profile) {
    return @{
        displayName = $profile.displayName
        email = $profile.email
        timezone = $profile.timezone
        fontScale = $profile.fontScale
        reduceMotion = [bool]$profile.reduceMotion
        theme = $profile.theme
    } | ConvertTo-Json -Compress
}

Write-Host "Light-Interview profile settings check against $BaseUrl" -ForegroundColor Cyan

try {
    $token = Login $Code
    $headers = @{ Authorization = "Bearer $token" }
    $originalProfile = Invoke-RestMethod -Uri "$api/me/profile" -Headers $headers
    Assert-Value $Name $originalProfile.loginName '登录名保持为账号归属键'

    $updated = Invoke-RestMethod -Method Put -Uri "$api/me/profile" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            displayName = '冒烟测试用户'
            email = 'smoke-profile@example.com'
            timezone = 'Asia/Tokyo'
            fontScale = 'large'
            reduceMotion = $true
            theme = 'blue'
        } | ConvertTo-Json -Compress)
    Assert-Value '冒烟测试用户' $updated.displayName '昵称已保存'
    Assert-Value 'Asia/Tokyo' $updated.timezone '时区已保存'
    Assert-Value 'large' $updated.fontScale '界面字号已保存'
    Assert-Value 'blue' $updated.theme '主题色已保存'
    Assert-Value $true $updated.reduceMotion '减弱动效已保存'

    $reloaded = Invoke-RestMethod -Uri "$api/me/profile" -Headers $headers
    Assert-Value 'smoke-profile@example.com' $reloaded.email '邮箱刷新后仍存在'

    Invoke-RestMethod -Method Put -Uri "$api/me/password" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            newPassword = $temporaryCode
        } | ConvertTo-Json -Compress) | Out-Null
    $passwordChanged = $true

    $newToken = Login $temporaryCode
    Assert-Value $true ([bool]$newToken) '新密码可以登录'
} finally {
    if ($headers) {
        if ($passwordChanged) {
            try {
                Invoke-RestMethod -Method Put -Uri "$api/me/password" -Headers $headers `
                    -ContentType 'application/json' -Body (@{
                        newPassword = $Code
                    } | ConvertTo-Json -Compress) | Out-Null
                Write-Host '  CLEAN 原密码已恢复' -ForegroundColor DarkGray
            } catch {
                Write-Host "  CLEAN FAIL 恢复原密码失败：$($_.Exception.Message)" -ForegroundColor Red
                $failures++
            }
        }
        if ($originalProfile) {
            try {
                Invoke-RestMethod -Method Put -Uri "$api/me/profile" -Headers $headers `
                    -ContentType 'application/json' -Body (ProfileBody $originalProfile) | Out-Null
                Write-Host '  CLEAN 原个人资料已恢复' -ForegroundColor DarkGray
            } catch {
                Write-Host "  CLEAN FAIL 恢复个人资料失败：$($_.Exception.Message)" -ForegroundColor Red
                $failures++
            }
        }
    }
}

if ($failures -gt 0) { throw "$failures profile setting checks failed" }
Write-Host 'Profile settings verification passed.' -ForegroundColor Green

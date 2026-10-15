# 简历导出的可复现检查：三种格式的响应头与文件魔数、非法格式、未授权访问。
#
# 用法：先起后端（8088）并准备一份简历，再执行
#   pwsh scripts/verify-resume-export.ps1 -ResumeId <uuid> [-Name smoke] [-Code 123456]
param(
    [Parameter(Mandatory = $true)][string]$ResumeId,
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Name = '',
    [string]$Code = ''
)

$ErrorActionPreference = 'Stop'
$api = "$BaseUrl/api/v1"
$failures = 0

function Assert-Equal($expected, $actual, $message) {
    if ("$expected" -eq "$actual") {
        Write-Host "  OK   $message" -ForegroundColor Green
    } else {
        Write-Host "  FAIL $message (expected $expected, got $actual)" -ForegroundColor Red
        $script:failures++
    }
}

$headers = @{}
if ($Name -and $Code) {
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -Body @{ name = $Name; code = $Code }
    if (-not $login.data.token) { throw 'login did not return a token' }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    Write-Host "以 $Name 的身份登录" -ForegroundColor Cyan
} else {
    Write-Host '未提供账号：只验证匿名访问会被拒绝' -ForegroundColor Yellow
}

$expected = @{
    md   = @{ type = 'text/markdown; charset=UTF-8'; head = '#'; ext = 'md' }
    docx = @{ type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'; head = 'PK'; ext = 'docx' }
    pdf  = @{ type = 'application/pdf'; head = '%PDF'; ext = 'pdf' }
}

foreach ($format in 'md', 'docx', 'pdf') {
    $out = Join-Path $env:TEMP "resume-export-check.$($expected[$format].ext)"
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$api/resumes/$ResumeId/export?format=$format" `
        -Headers $headers -OutFile $out -PassThru
    $bytes = [System.IO.File]::ReadAllBytes($out)
    $headLength = [Math]::Min($expected[$format].head.Length, $bytes.Length)
    $head = [System.Text.Encoding]::ASCII.GetString($bytes[0..($headLength - 1)])

    Write-Host "format=$format" -ForegroundColor Cyan
    Assert-Equal 200 $response.StatusCode 'HTTP 200'
    Assert-Equal $expected[$format].type ([string]$response.Headers['Content-Type'][0]) 'Content-Type'
    Assert-Equal $expected[$format].head $head '文件魔数'
    Assert-Equal 'True' ("$($response.Headers['Content-Disposition'])" -like "*.$($expected[$format].ext)*") '下载文件名后缀'
    Assert-Equal 'True' ($bytes.Length -gt 100) '文件非空'
    Remove-Item $out -Force
}

Write-Host 'format=xml' -ForegroundColor Cyan
try {
    $null = Invoke-WebRequest -UseBasicParsing -Uri "$api/resumes/$ResumeId/export?format=xml" -Headers $headers
    Assert-Equal 400 'no error' '非法格式应被拒绝'
} catch {
    Assert-Equal 400 $_.Exception.Response.StatusCode.value__ '非法格式返回 400'
    Assert-Equal 'True' ("$($_.ErrorDetails.Message)" -match 'md|docx|pdf') '错误消息列出可选项'
}

Write-Host '匿名访问' -ForegroundColor Cyan
try {
    $null = Invoke-WebRequest -UseBasicParsing -Uri "$api/resumes/$ResumeId/export?format=pdf"
    Assert-Equal 401 'no error' '未授权访问应被拒绝'
} catch {
    Assert-Equal 401 $_.Exception.Response.StatusCode.value__ '未授权返回 401'
}

if ($failures -gt 0) {
    Write-Host "$failures 项检查未通过" -ForegroundColor Red
    exit 1
}
Write-Host '简历导出检查全部通过' -ForegroundColor Green

<#
.SYNOPSIS
  验证非法媒体在上传阶段被拒绝，而不是进入后台分析。

  需要后端已启动，并使用 smoke/123456 演示账号。脚本创建的练习会最终删除。
#>
param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Name = 'smoke',
    [string]$Code = '123456'
)

$ErrorActionPreference = 'Stop'
$api = "$BaseUrl/api/v1"
$sessionId = $null

try {
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -Body @{ name = $Name; code = $Code }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }

    $session = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            role = '媒体校验'
            interviewType = 'technical'
            difficulty = 'standard'
            interviewerStyle = 'balanced'
            questionCount = 1
            durationMinutes = 4
            followUpEnabled = $false
            mode = 'per_question'
            questions = @(@{ id = 'q-media'; text = '验证非法媒体'; category = 'qa' })
        } | ConvertTo-Json -Depth 5 -Compress)
    $sessionId = $session.id

    $answer = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions/$sessionId/answers" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            questionIndex = 0
            questionId = 'q-media'
            durationSeconds = 3
            startedAtOffsetSeconds = 0
        } | ConvertTo-Json -Compress)

    $placeholder = [System.Text.Encoding]::UTF8.GetBytes('not-a-real-video')
    $client = [System.Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
        'Bearer', $login.data.token)
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $file = [System.Net.Http.ByteArrayContent]::new($placeholder)
    $file.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('video/webm')
    $form.Add($file, 'file', 'answer.webm')
    $response = $client.PostAsync("$api/answers/$($answer.id)/media", $form).Result
    $body = $response.Content.ReadAsStringAsync().Result

    if ([int]$response.StatusCode -ne 400) {
        throw "expected HTTP 400, actual=$([int]$response.StatusCode), body=$body"
    }
    if ($body -notmatch '录制文件过小或不完整') {
        throw "expected actionable media message, body=$body"
    }
    Write-Host 'PASS invalid media rejected at upload with actionable message' -ForegroundColor Green
} finally {
    if ($sessionId) {
        try {
            Invoke-RestMethod -Method Delete -Uri "$api/practice-sessions/$sessionId" -Headers $headers | Out-Null
            Write-Host 'CLEAN test session removed' -ForegroundColor DarkGray
        } catch {
            Write-Warning "cleanup failed: $($_.Exception.Message)"
        }
    }
}

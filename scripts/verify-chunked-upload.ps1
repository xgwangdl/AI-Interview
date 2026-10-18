<#
.SYNOPSIS
  验证分片合并使用服务端实际字节数，而不是信任客户端 totalBytes。

  需要后端已启动，并使用 smoke/123456 演示账号。脚本会删除创建的练习。
#>
param(
    [string]$BaseUrl = 'http://localhost:8088',
    [string]$Name = 'smoke',
    [string]$Code = '123456'
)

$ErrorActionPreference = 'Stop'
$api = "$BaseUrl/api/v1"
$sessionId = $null

function New-Chunk([bool]$withHeader) {
    $bytes = New-Object byte[] 256
    [Array]::Fill($bytes, [byte]0x42)
    if ($withHeader) {
        $bytes[0] = 0x1a
        $bytes[1] = 0x45
        $bytes[2] = 0xdf
        $bytes[3] = 0xa3
    }
    return $bytes
}

try {
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/login" -Body @{ name = $Name; code = $Code }
    $headers = @{ Authorization = "Bearer $($login.data.token)" }
    $session = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            role = '分片校验'
            interviewType = 'technical'
            difficulty = 'standard'
            interviewerStyle = 'balanced'
            questionCount = 1
            durationMinutes = 4
            followUpEnabled = $false
            mode = 'per_question'
            questions = @(@{ id = 'q-chunk'; text = '验证分片大小'; category = 'qa' })
        } | ConvertTo-Json -Depth 5 -Compress)
    $sessionId = $session.id

    $answer = Invoke-RestMethod -Method Post -Uri "$api/practice-sessions/$sessionId/answers" -Headers $headers `
        -ContentType 'application/json' -Body (@{
            questionIndex = 0
            questionId = 'q-chunk'
            durationSeconds = 3
            startedAtOffsetSeconds = 0
        } | ConvertTo-Json -Compress)

    $first = New-Chunk $true
    $second = New-Chunk $false
    $all = New-Object byte[] 512
    [Array]::Copy($first, 0, $all, 0, 256)
    [Array]::Copy($second, 0, $all, 256, 256)
    $sha = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($all)).ToLowerInvariant()

    $client = [System.Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
        'Bearer', $login.data.token)
    foreach ($entry in @(@{ index = 0; bytes = $first }, @{ index = 1; bytes = $second })) {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Put,
            "$api/answers/$($answer.id)/media/chunks/$($entry.index)?totalChunks=2")
        $request.Content = [System.Net.Http.ByteArrayContent]::new($entry.bytes)
        $request.Content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse(
            'application/octet-stream')
        $response = $client.SendAsync($request).Result
        if ([int]$response.StatusCode -ne 200) {
            throw "chunk $($entry.index) upload failed: $([int]$response.StatusCode)"
        }
    }

    $wrongBody = @{
        totalChunks = 2
        totalBytes = 511
        contentType = 'video/webm'
        checksumSha256 = $sha
    } | ConvertTo-Json -Compress
    $wrong = Invoke-WebRequest -SkipHttpErrorCheck -Method Post `
        -Uri "$api/answers/$($answer.id)/media/merge" -Headers $headers `
        -ContentType 'application/json' -Body $wrongBody
    if ([int]$wrong.StatusCode -ne 400 -or $wrong.Content -notmatch '实际 512') {
        throw "forged totalBytes was not rejected: status=$([int]$wrong.StatusCode), body=$($wrong.Content)"
    }
    Write-Host 'PASS forged totalBytes rejected with actual byte count' -ForegroundColor Green

    $rightBody = @{
        totalChunks = 2
        totalBytes = 512
        contentType = 'video/webm'
        checksumSha256 = $sha
    } | ConvertTo-Json -Compress
    $merged = Invoke-RestMethod -Method Post -Uri "$api/answers/$($answer.id)/media/merge" `
        -Headers $headers -ContentType 'application/json' -Body $rightBody
    if ($merged.mediaSizeBytes -ne 512) {
        throw "merged media size mismatch: $($merged.mediaSizeBytes)"
    }
    Write-Host 'PASS merge stores the server-measured size (512)' -ForegroundColor Green
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

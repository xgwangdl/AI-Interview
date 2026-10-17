<#
  L2 实时对话的真实验证：WebSocket 推 PCM → 边识别 → 边生成 → 首句语音。

  断言：
    1. 握手要 token（没有 token 连不上）
    2. 识别是"边推边出"（commit 之前就收到 partial）
    3. 回复是流式的（多个 reply 增量）
    4. 第一句语音在回复结束前就开始到达（audio 帧）
    5. 轮次落库，且 llmFirstMs / audioFirstMs 有值
  跑完删除验证用的会话。

  pwsh scripts/verify-language-l2.ps1
#>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8088'
$wsBase = 'ws://localhost:8088'
$wav = 'C:\workspace\AI-Interview\AI-Interview\data\voice-probe\user-16k.wav'
$pass = 0; $fail = 0

function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

function New-Client([string]$url) {
    $ws = [System.Net.WebSockets.ClientWebSocket]::new()
    $ws.ConnectAsync([Uri]$url, [Threading.CancellationToken]::None).Wait()
    return $ws
}

function Send-Text($ws, [string]$json) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $ws.SendAsync([ArraySegment[byte]]::new($bytes),
        [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
        [Threading.CancellationToken]::None).Wait()
}

function Send-Binary($ws, [byte[]]$bytes) {
    $ws.SendAsync([ArraySegment[byte]]::new($bytes),
        [System.Net.WebSockets.WebSocketMessageType]::Binary, $true,
        [Threading.CancellationToken]::None).Wait()
}

function Receive-Message($ws, [int]$timeoutMs) {
    $buffer = New-Object byte[] 131072
    $cts = [Threading.CancellationTokenSource]::new($timeoutMs)
    try {
        $task = $ws.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cts.Token)
        $task.Wait()
        if ($task.Result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) { return $null }
        $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $task.Result.Count)
        return $text | ConvertFrom-Json
    } catch {
        return $null
    } finally {
        $cts.Dispose()
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$token = $login.data.token
$h = @{ Authorization = "Bearer $token" }

Write-Host '=== 1. 握手要 token ===' -ForegroundColor Cyan
$denied = $false
try {
    $bad = New-Client "$wsBase/ws/language"
    $denied = ($bad.State -eq [System.Net.WebSockets.WebSocketState]::Aborted)
} catch { $denied = $true }
Check '没有 token 连不上' $denied ''

$session = Invoke-RestMethod -Method Post -Uri "$base/api/v1/language-sessions" -Headers $h -ContentType 'application/json' -Body (@{
    language = 'en'; scenario = 'interview'; level = 'intermediate'; maxTurns = 4
} | ConvertTo-Json)

try {
    Write-Host '=== 2. 推音频 → 边识别 ===' -ForegroundColor Cyan
    $ws = New-Client "$wsBase/ws/language?token=$token"
    $ready = Receive-Message $ws 8000
    Check '连上后收到 ready' ($ready.type -eq 'ready') ''
    Send-Text $ws (@{ type = 'start'; sessionId = $session.id } | ConvertTo-Json)
    $afterStart = Receive-Message $ws 2500
    if ($afterStart) { Write-Host ("  start 之后：{0}" -f ($afterStart | ConvertTo-Json -Compress)) }
    Write-Host ("  连接状态：{0}" -f $ws.State)

    $raw = [System.IO.File]::ReadAllBytes($wav)
    # ffmpeg 写的 WAV 带 LIST 元数据块，data 不在固定 44 字节处；按块头找
    $head = [Text.Encoding]::ASCII.GetString($raw, 0, [Math]::Min(200, $raw.Length))
    $dataOffset = $head.IndexOf('data') + 8
    $pcmLength = $raw.Length - $dataOffset
    $pcm = New-Object byte[] $pcmLength
    [Array]::Copy($raw, $dataOffset, $pcm, 0, $pcmLength)
    # 100ms 一帧（16k × 2 字节 × 0.1s = 3200B）：实时识别推荐的小帧，也避开容器 8KB 的边界
    $chunkSize = 3200
    $partialBeforeCommit = 0
    $firstPartialAt = 0
    $clock = [Diagnostics.Stopwatch]::StartNew()
    for ($offset = 0; $offset -lt $pcm.Length; $offset += $chunkSize) {
        $size = [Math]::Min($chunkSize, $pcm.Length - $offset)
        $frame = New-Object byte[] $size
        [Array]::Copy($pcm, $offset, $frame, 0, $size)
        Send-Binary $ws $frame
        Start-Sleep -Milliseconds 120
        while ($true) {
            $message = Receive-Message $ws 60
            if (-not $message) { break }
            if ($message.type -eq 'partial') {
                $partialBeforeCommit++
                if ($firstPartialAt -eq 0) { $firstPartialAt = $clock.ElapsedMilliseconds }
                Write-Host ("  partial({0}ms)：{1}" -f $clock.ElapsedMilliseconds, $message.text)
            }
        }
    }
    Check 'commit 之前就收到 partial（边说边识别）' ($partialBeforeCommit -gt 0) "partials=$partialBeforeCommit 首个=${firstPartialAt}ms"

    Write-Host '=== 3. commit → 流式回复 + 首句语音 ===' -ForegroundColor Cyan
    $commitAt = $clock.ElapsedMilliseconds
    Send-Text $ws (@{ type = 'commit' } | ConvertTo-Json)
    $replyDeltas = 0; $reply = ''; $audioChunks = 0; $firstReplyAt = 0; $firstAudioAt = 0
    $transcript = ''; $turn = $null; $feedback = $null
    $deadline = [Diagnostics.Stopwatch]::StartNew()
    while ($deadline.ElapsedMilliseconds -lt 40000) {
        $message = Receive-Message $ws 4000
        if (-not $message) { continue }
        $now = $clock.ElapsedMilliseconds
        switch ($message.type) {
            'transcript' { $transcript = $message.text; Write-Host "  transcript($now ms)：$transcript" }
            'reply' {
                if ($firstReplyAt -eq 0) { $firstReplyAt = $now; Write-Host "  首字 $now ms：$($message.delta)" }
                $replyDeltas++; $reply += $message.delta
            }
            'audio' {
                if ($firstAudioAt -eq 0) { $firstAudioAt = $now; Write-Host "  第一帧音频 $now ms（seq=$($message.seq)）" }
                $audioChunks++
            }
            'feedback' { $feedback = $message.items }
            'turn' { $turn = $message; break }
            'error' { Write-Host "  error：$($message.message)" -ForegroundColor Red; break }
        }
        if ($turn) { break }
    }
    Check '拿到转写' ($transcript -match '(?i)billing|latency|migration') "transcript=$transcript"
    Check '回复是流式的（多个增量）' ($replyDeltas -gt 2) "deltas=$replyDeltas 首字=${firstReplyAt}ms"
    Check '首句语音在回复结束前就开播' ($audioChunks -gt 0 -and $firstAudioAt -gt 0) "chunks=$audioChunks 首帧=${firstAudioAt}ms"
    Check '落库的轮次带首字/首帧耗时' ($turn.latency.llmFirstMs -gt 0 -and $turn.latency.audioFirstMs -gt 0) "llmFirst=$($turn.latency.llmFirstMs)ms audioFirst=$($turn.latency.audioFirstMs)ms"
    Write-Host ("  回复全文：{0}" -f $turn.replyText)

    Write-Host '=== 4. 反馈随后到 ===' -ForegroundColor Cyan
    $fb = Receive-Message $ws 15000
    $items = if ($fb.type -eq 'feedback') { $fb.items } else { $null }
    Check '反馈单独到达（不挡回复）' ($null -ne $items) "条数=$(@($items).Count)"
    $stored = Invoke-RestMethod -Uri "$base/api/v1/language-sessions/$($session.id)" -Headers $h
    Check '轮次已落库且反馈写入' ($stored.turns.Count -eq 1 -and $stored.turns[0].replyText.Length -gt 0) ''
    $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', [Threading.CancellationToken]::None).Wait()
} finally {
    Invoke-RestMethod -Method Delete -Uri "$base/api/v1/language-sessions/$($session.id)" -Headers $h | Out-Null
    Write-Host '  清理：会话已删除' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

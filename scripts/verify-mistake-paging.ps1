<#
  错题本分页与岗位筛选的验证（接口 + 界面，真实库、真实后端）。

  步骤：临时给 smoke 造 25 条错题（15 条 Java 后端 + 10 条前端）→ 接口分页/筛选断言 →
  跑浏览器脚本看界面 → **把造出来的数据与界面点出来的练习全部删掉**，并核对库里回到原样。

  造出来的题目 id 统一带 `vfy-pg-` 前缀，任何一步失败都可以用同一条件清干净：
    delete from practice_score_archive where question_id like 'vfy-pg-%';

  用法（后端 8088 与前端 5173 都在跑）：
    pwsh scripts/verify-mistake-paging.ps1
#>
$ErrorActionPreference = 'Stop'
$root = 'C:\workspace\AI-Interview\AI-Interview'
$base = 'http://localhost:8088'
$psql = { param([string]$sql) docker exec light-interview-postgres psql -U postgres -d interview -t -A -c $sql }
$pass = 0; $fail = 0

function Check($label, $condition, $detail) {
    if ($condition) { Write-Host "  PASS $label $detail" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "  FAIL $label $detail" -ForegroundColor Red; $script:fail++ }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/login" -Body @{ name = 'smoke'; code = '123456' }
$h = @{ Authorization = "Bearer $($login.data.token)" }
$beforeSeeded = $psql.Invoke("select count(*) from practice_score_archive where owner_id = 'smoke' and question_id like 'vfy-pg-%'")
Check '起点干净（没有上次残留）' ($beforeSeeded.Trim() -eq '0') "残留=$($beforeSeeded.Trim())"

Write-Host '=== 造 25 条错题（15 后端 + 10 前端），跑完会删掉 ===' -ForegroundColor Cyan
$seed = @"
insert into practice_score_archive (id, owner_id, session_id, session_created_at, question_index, question_id,
    question_text, question_category, role, difficulty, interview_type, interviewer_style,
    overall_score, relevance, completeness, professionalism, star_structure, logic, evaluated_at)
select gen_random_uuid(), 'smoke', gen_random_uuid(), now(), 0, 'vfy-pg-' || n,
    '分页验证题 ' || n || '：讲讲你在这个项目里的取舍', '项目设计',
    case when n <= 15 then 'Java 后端工程师' else '前端工程师' end, 'standard', 'technical', 'balanced',
    10 + n, 40, 30, 35, 25, 30, now() - (n || ' minutes')::interval
from generate_series(1, 25) as n;
"@
$psql.Invoke($seed) | Out-Null
$seeded = $psql.Invoke("select count(*) from practice_score_archive where owner_id = 'smoke' and question_id like 'vfy-pg-%'")
Check '数据已写入' ($seeded.Trim() -eq '25') "seeded=$($seeded.Trim())"

Write-Host '=== 接口：分页与筛选 ===' -ForegroundColor Cyan
$all = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?limit=5" -Headers $h
Check '总数是筛选后的全部错题' ($all.total -eq 25) "total=$($all.total)"
Check '总览页拿到 5 条' ($all.mistakes.Count -eq 5) "rows=$($all.mistakes.Count)"
Check '返回 offset/limit 便于前端算页' ($all.offset -eq 0 -and $all.limit -eq 5) "offset=$($all.offset) limit=$($all.limit)"

$page1 = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?offset=0&limit=20" -Headers $h
$page2 = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?offset=20&limit=20" -Headers $h
$beyond = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?offset=100&limit=20" -Headers $h
Check '第一页 20 条' ($page1.mistakes.Count -eq 20) "rows=$($page1.mistakes.Count)"
Check '第二页 5 条' ($page2.mistakes.Count -eq 5) "rows=$($page2.mistakes.Count)"
Check '两页不重复' ($page1.mistakes[0].questionId -ne $page2.mistakes[0].questionId) "$($page1.mistakes[0].questionId) vs $($page2.mistakes[0].questionId)"
Check '越界返回空列表而不是报错' ($beyond.mistakes.Count -eq 0 -and $beyond.total -eq 25) "rows=$($beyond.mistakes.Count) total=$($beyond.total)"

$filtered = Invoke-RestMethod -Uri "$base/api/v1/practice-archive/mistakes?role=%E5%89%8D%E7%AB%AF%E5%B7%A5%E7%A8%8B%E5%B8%88&limit=20" -Headers $h
Check '按岗位筛选：只回该岗位的题' ($filtered.total -eq 10 -and $filtered.role -eq '前端工程师') "total=$($filtered.total) role=$($filtered.role)"
Check '筛选选项仍然基于全部错题' ($filtered.roles.Count -eq 2) "roles=$($filtered.roles | ForEach-Object { "$($_.role):$($_.count)" } | Join-String -Separator ', ')"
Check '筛选结果里没有别的岗位' (($filtered.mistakes | Where-Object { $_.role -ne '前端工程师' }).Count -eq 0) ''

Write-Host '=== 界面：真浏览器走一遍 ===' -ForegroundColor Cyan
$output = & node "$root\web\scripts\verify-mistake-paging.mjs" 2>&1
$output | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
$created = ($output | Select-String -Pattern '^CREATED_SESSION=(.+)$').Matches.Groups[1].Value
$uiFailed = ($output | Select-String -Pattern '界面检查：全部通过').Count -eq 0
Check '界面检查通过' (-not $uiFailed) ''

Write-Host '=== 清理 ===' -ForegroundColor Cyan
if ($created) {
    Invoke-RestMethod -Method Delete -Uri "$base/api/v1/practice-sessions/$created" -Headers $h | Out-Null
    $goneSession = $psql.Invoke("select count(*) from practice_sessions where id = '$created'")
    Check '界面点出来的练习已删除' ($goneSession.Trim() -eq '0') "session=$created"
} else {
    Check '界面点出来的练习已删除' $false '（没拿到 session id）'
}
$psql.Invoke("delete from practice_score_archive where question_id like 'vfy-pg-%'") | Out-Null
$after = $psql.Invoke("select count(*) from practice_score_archive where owner_id = 'smoke'")
Check '造出来的错题已删除，库回到原样' ($after.Trim() -eq '1') "smoke 剩余 $($after.Trim()) 条（原本 1 条）"

Write-Host ''
Write-Host "结果：$pass 通过 / $fail 失败" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

# 真机冒烟测试：在隔离目录里启动真实 Paper 1.21.11，验证插件能加载、api-version 通过、
# 指令可用、prefixes.yml 往返读写正常。真实玩家聊天需要真人进服，见 README 的手工清单。
#
# 用法: .\smoke-test.ps1 [-ServerDir <目录>] [-JavaExe <java路径>]
# 首次运行会下载 Paper 1.21.11 服务端 jar（约 52 MB）；若本机已有
# PlayerInsight/.testserver/12111/cache/mojang_1.21.11.jar 会自动复用，避免走慢链路。
param(
    [string]$ServerDir = "",
    [string]$JavaExe = "C:\Program Files\Java\jdk-21\bin\java.exe",
    [int]$StartupWait = 70,
    [int]$CommandGap = 3,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$tests = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Split-Path -Parent $tests
$base = Join-Path $project "1.21.11"
if ([string]::IsNullOrWhiteSpace($ServerDir)) {
    $ServerDir = Join-Path $project ".testserver\12111"
}
if (-not (Test-Path $JavaExe)) { $JavaExe = "java" }

$paperUrl = "https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar"
$paperBuild = "paper-1.21.11-132"

Write-Host "== 准备测试服 $ServerDir =="
New-Item -ItemType Directory -Force -Path $ServerDir, "$ServerDir\plugins", "$ServerDir\cache" | Out-Null

if (-not (Test-Path "$ServerDir\server.jar")) {
    Write-Host "下载 $paperBuild …"
    curl.exe -sL -o "$ServerDir\server.jar" --retry 5 --retry-all-errors $paperUrl
    if (-not (Test-Path "$ServerDir\server.jar")) {
        Write-Host "服务端下载失败" -ForegroundColor Red
        exit 1
    }
}

# 复用别处缓存好的原版 server.jar，省掉 paperclip 的慢速下载
$cacheJar = "$ServerDir\cache\mojang_1.21.11.jar"
if (-not (Test-Path $cacheJar)) {
    $known = @(
        (Join-Path $project "..\PlayerInsight\.testserver\12111\cache\mojang_1.21.11.jar"),
        (Join-Path $env:USERPROFILE "Downloads\mojang_1.21.11.jar")
    )
    foreach ($candidate in $known) {
        if (Test-Path $candidate) {
            Copy-Item $candidate $cacheJar -Force
            Write-Host "复用已缓存的原版 jar: $candidate"
            break
        }
    }
}

Set-Content -Path "$ServerDir\eula.txt" -Value "eula=true" -Encoding ASCII
$props = @(
    "online-mode=false",
    "server-port=25599",
    "level-type=flat",
    "level-name=smokeworld",
    "view-distance=4",
    "simulation-distance=4",
    "spawn-protection=0",
    "max-players=5",
    "motd=ChatPrefix smoke test",
    "sync-chunk-writes=false"
)
Set-Content -Path "$ServerDir\server.properties" -Value $props -Encoding ASCII

# 放插件
Copy-Item "$base\dist\ChatPrefix-1.0.0-1.21.11.jar" "$ServerDir\plugins\" -Force
New-Item -ItemType Directory -Force -Path "$ServerDir\plugins\ChatPrefix" | Out-Null
# 预置两条记录：验证 prefixes.yml 的中文与颜色代码能读进来（玩家不在线也能被指令查到）
$seed = @"
players:
  00000000-0000-0000-0000-0000000000aa:
    name: TesterA
    prefix: '&c本服'
  00000000-0000-0000-0000-0000000000bb:
    name: TesterB
    prefix: '&e大佬'
"@
Set-Content -Path "$ServerDir\plugins\ChatPrefix\prefixes.yml" -Value $seed -Encoding UTF8

$log = "$ServerDir\console-output.txt"
Remove-Item -Force $log -ErrorAction SilentlyContinue

$commands = @(
    "cp",
    "cp list",
    "cp get TesterA",
    "cp remove TesterB",
    "cp list",
    "cp set NobodyX hi",
    "cp reload",
    "cp list"
)

$parts = @("chcp 65001 >nul", "ping -n $StartupWait 127.0.0.1 >nul")
foreach ($c in $commands) {
    $parts += "echo $c"
    $parts += "ping -n $CommandGap 127.0.0.1 >nul"
}
$parts += "echo stop"
$feed = $parts -join " & "

# stdout.encoding/file.encoding 固定成 UTF-8，保证重定向出来的中文日志可读、可断言
$cmd = "( $feed ) | `"$JavaExe`" -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -Xms1G -Xmx1500M -jar `"$ServerDir\server.jar`" --nogui > `"$log`" 2>&1"

Write-Host "== 启动服务器（约 $StartupWait 秒后开始喂指令）=="
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd -WorkingDirectory $ServerDir -PassThru -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if ($proc.HasExited) { break }
}
if (-not $proc.HasExited) {
    Write-Host "!! 超时，只强制结束本次测试启动的那个 java 进程"
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$ServerDir*" } |
        ForEach-Object {
            Write-Host ("   kill PID " + $_.ProcessId)
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
    Start-Sleep -Seconds 3
}

if (-not (Test-Path $log)) {
    Write-Host "没有拿到控制台输出" -ForegroundColor Red
    exit 1
}
$text = Get-Content -LiteralPath $log -Raw -Encoding UTF8
# 控制台日志里夹着 ANSI 颜色码（插件发的是带样式的组件），断言前先剔掉，否则句子会被切断
$esc = [char]27
$text = $text -replace ([regex]::Escape($esc) + "\[[0-9;]*[A-Za-z]"), ""

# 期望出现的片段（插件启动 + 指令回显）
$expect = @(
    "Done (",
    "已启用：前缀 2 条",
    "共 2 条前缀",
    "TesterA 的前缀是",
    "已删除 TesterB 的前缀",
    "共 1 条前缀",
    "找不到玩家 NobodyX",
    "已重载 config.yml 与 prefixes.yml，当前 1 条前缀"
)
# 不该出现的片段（加载失败 / 运行时异常）
$forbid = @(
    "Exception",
    "Could not load",
    "Unsupported API version",
    "NoClassDefFoundError",
    "ClassNotFoundException",
    "无效的插件",
    "Cannot load plugin"
)

Write-Host ""
Write-Host "===== 关键字统计 ====="
$failed = 0
foreach ($k in $expect) {
    $count = ([regex]::Matches($text, [regex]::Escape($k))).Count
    $mark = if ($count -gt 0) { "OK  " } else { $failed++; "FAIL" }
    Write-Host ("{0} 期望出现  {1,-52} x{2}" -f $mark, $k, $count)
}
foreach ($k in $forbid) {
    $count = ([regex]::Matches($text, [regex]::Escape($k))).Count
    $mark = if ($count -eq 0) { "OK  " } else { $failed++; "FAIL" }
    Write-Host ("{0} 不应出现  {1,-52} x{2}" -f $mark, $k, $count)
}

# 原版 flat 世界会刷 "No key layers in MapLike[{}]" 这类 ERROR，不属于插件问题；
# 所以只要求「日志里的 ERROR 行不能与 ChatPrefix 有关」
$errorLines = ($text -split "`n") | Where-Object { $_ -match "ERROR|SEVERE" }
$pluginErrorLines = $errorLines | Where-Object { $_ -match "ChatPrefix" }
Write-Host ("{0} 期望不出现  {1,-52} x{2}" -f $(if ($pluginErrorLines.Count -eq 0) { "OK  " } else { $failed++; "FAIL" }),
    "与 ChatPrefix 有关的错误行", $pluginErrorLines.Count)
Write-Host ("     （日志里其他 ERROR 行共 {0} 条，均为原版/服务端噪声）" -f $errorLines.Count)

Write-Host ""
Write-Host "===== plugins\ChatPrefix 目录产物 ====="
Get-ChildItem "$ServerDir\plugins\ChatPrefix" -ErrorAction SilentlyContinue | Select-Object Name, Length | Format-Table | Out-String | Write-Host

$config = Get-Content -LiteralPath "$ServerDir\plugins\ChatPrefix\config.yml" -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
if ($config -and $config.Contains('format: "{prefix}{dimension}{name}{separator}{message}"')) {
    Write-Host "OK   config.yml 已按默认值生成"
} else {
    Write-Host "FAIL config.yml 没有生成或内容不符" -ForegroundColor Red
    $failed++
}

$after = Get-Content -LiteralPath "$ServerDir\plugins\ChatPrefix\prefixes.yml" -Raw -Encoding UTF8 -ErrorAction SilentlyContinue
if ($after -and $after.Contains("TesterA") -and -not $after.Contains("TesterB")) {
    Write-Host "OK   prefixes.yml 已写回：TesterB 被删除、TesterA 保留"
} else {
    Write-Host "FAIL prefixes.yml 写回结果不符" -ForegroundColor Red
    $failed++
}

Write-Host ""
if ($failed -eq 0) {
    Write-Host "===== 冒烟测试通过（日志：$log）=====" -ForegroundColor Green
    exit 0
} else {
    Write-Host "===== 冒烟测试 $failed 项未通过（日志：$log）=====" -ForegroundColor Red
    exit 1
}

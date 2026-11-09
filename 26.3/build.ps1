# 编译 + 打包 ChatPrefix（Paper 26.3，Java 25 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
# 需要：JDK 25 或更高（26.3 的 paper-api 是 class 版本 69，javac 21 读不了）；依赖 jar 放在 lib/
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
# 26.3 起服务端要求 Java 25，--release 25 也需要 javac 25 以上
$jdk = $null
foreach ($candidate in @("C:\Program Files\Java\jdk-25", "C:\Program Files\Java\jdk-27.0.1.0.2", "C:\Program Files\Java\jdk-26.0.1")) {
    if (Test-Path "$candidate\bin\javac.exe") {
        $jdk = $candidate
        break
    }
}
if ($jdk) {
    $javac = "$jdk\bin\javac.exe"
    $jar = "$jdk\bin\jar.exe"
    Write-Host "使用 JDK: $jdk"
} else {
    $javac = "javac"
    $jar = "jar"
    Write-Host "未找到 JDK 25+，改用 PATH 里的 javac/jar（需要 25 以上）" -ForegroundColor Yellow
}

$libs = @(
    "paper-api-26.3.build.241-stable.jar",
    "adventure-api-5.2.0.jar",
    "adventure-key-5.2.0.jar",
    "adventure-text-minimessage-5.2.0.jar",
    "adventure-text-serializer-plain-5.2.0.jar",
    "bungeecord-chat-1.21.jar",
    "guava-33.6.0-jre.jar",
    "failureaccess-1.0.2.jar"
)
foreach ($lib in $libs) {
    if (-not (Test-Path "$base\lib\$lib")) {
        Write-Host "缺少依赖: lib\$lib" -ForegroundColor Red
        exit 1
    }
}
$classpath = ($libs | ForEach-Object { "$base\lib\$_" }) -join ";"

$classes = "$base\build\classes"
$stage = "$base\build\stage"

Write-Host "== 清理 =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host "== 编译 =="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
$javacOut = & $javac --release 25 -encoding UTF-8 -Xlint:-options -classpath $classpath -d $classes $src 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host $javacOut
    Write-Host "编译失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 打包 =="
Copy-Item -Recurse "$classes\*" $stage -Force
Copy-Item "$base\resources\plugin.yml" $stage -Force
Copy-Item "$base\resources\config.yml" $stage -Force
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
$outJar = "$base\dist\ChatPrefix-1.4.0-26.3.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .
if ($LASTEXITCODE -ne 0) {
    Write-Host "打包失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length

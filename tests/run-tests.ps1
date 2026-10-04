# 离线断言：不启动服务器，直接用 1.21.11/lib 与 1.21.11/build/classes 跑 FormatTest。
# 用法：在 PowerShell 中执行  .\run-tests.ps1
$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$tests = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Split-Path -Parent $tests
$base = Join-Path $project "1.21.11"

$jdk = "C:\Program Files\Java\jdk-21"
$javac = "$jdk\bin\javac.exe"
$java = "$jdk\bin\java.exe"
if (-not (Test-Path $javac)) {
    $javac = "javac"
    $java = "java"
    Write-Host "未找到 jdk-21，改用 PATH 里的 javac/java" -ForegroundColor Yellow
}

# 每次都重新编译插件，避免拿着旧的 class 跑断言
& "$base\build.ps1" | Out-Null
if (-not (Test-Path "$base\build\classes")) {
    Write-Host "插件编译失败，测试无法继续" -ForegroundColor Red
    exit 1
}

$libs = (Get-ChildItem "$base\lib" -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"
$cp = "$base\build\classes;$libs"
$out = "$tests\out"
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null

Set-Location $tests

Write-Host "== 编译测试 =="
$compileOut = & $javac --release 21 -encoding UTF-8 -Xlint:-options -cp $cp -d $out "$tests\FormatTest.java" 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host $compileOut
    Write-Host "测试代码编译失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 运行断言 =="
& $java "-Dfile.encoding=UTF-8" -cp "$out;$cp" FormatTest
$code = $LASTEXITCODE
Write-Host "exit=$code"
exit $code

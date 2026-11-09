# 打包 ChatPrefix 彩虹前缀资源包（裁剪自 TheSalt_ 的 Text Effects，26.3 版）
# 用法：在 PowerShell 中执行  .\build-pack.ps1
# 产物：dist\ChatPrefix-Rainbow-<version>.zip
#
# 注意：这里用 .NET ZipArchive 显式写入正斜杠条目名，刻意不用 Compress-Archive ——
# Windows PowerShell 5.1 的 Compress-Archive 会把条目名写成反斜杠（assets\minecraft\...），
# 而 Minecraft 是按正斜杠找资源的，那样打出来的包会直接失效。
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = "1.3.0"
$stage = "$base\build\stage"
$out = "$base\dist\ChatPrefix-Rainbow-$version.zip"

Write-Host "== 检查必备文件 =="
$required = @(
    "pack.mcmeta",
    "CREDITS.md",
    "LICENSE-Text_Effects.txt",
    "assets\minecraft\shaders\include\_config.glsl",
    "assets\minecraft\shaders\core\text.vsh",
    "assets\minecraft\shaders\core\text.fsh"
)
$missing = 0
foreach ($item in $required) {
    if (-not (Test-Path "$base\$item")) {
        Write-Host "缺少 $item" -ForegroundColor Red
        $missing++
    }
}
if ($missing -gt 0) {
    exit 1
}

Write-Host "== 检查没有残留 1.21.11 时代的核心着色器 =="
$stale = @(Get-ChildItem -Path "$base\assets\minecraft\shaders\core" -Filter "rendertype_text*" -ErrorAction SilentlyContinue)
if ($stale.Count -gt 0) {
    Write-Host "  core 里还有 $($stale.Count) 个 rendertype_text* 文件，26.3 已改用 core/text.*，必须删掉" -ForegroundColor Red
    exit 1
}
Write-Host "  干净"

Write-Host "== 校验 pack.mcmeta 是合法 JSON =="
try {
    $meta = Get-Content -LiteralPath "$base\pack.mcmeta" -Raw -Encoding UTF8 | ConvertFrom-Json
    $min = $meta.pack.min_format -join "."
    $max = $meta.pack.max_format -join "."
    Write-Host "   min_format = $min，max_format = $max（26.3 = 97.1）"
    if ($min -ne "97.1") {
        Write-Host "  min_format 不是 97.1，资源包不会在 26.3 上被认作兼容" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "pack.mcmeta 不是合法 JSON：$_" -ForegroundColor Red
    exit 1
}

Write-Host "== 暂存到 build\stage =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item "$base\pack.mcmeta" $stage -Force
Copy-Item "$base\CREDITS.md" $stage -Force
Copy-Item "$base\LICENSE-Text_Effects.txt" $stage -Force
Copy-Item -Recurse "$base\assets" $stage -Force

Write-Host "== 打包（正斜杠条目名）=="
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
Remove-Item -Force $out -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($out, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -Recurse -File -LiteralPath $stage) {
        $relative = $file.FullName.Substring($stage.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative) | Out-Null
    }
} finally {
    $archive.Dispose()
}

Write-Host "== 校验 zip 结构 =="
$zip = [System.IO.Compression.ZipFile]::OpenRead($out)
try {
    $names = @($zip.Entries | ForEach-Object { $_.FullName })
    $hasRootMeta = $names -contains "pack.mcmeta"
    $bad = @($names | Where-Object { $_ -like '*\*' })
    $coreCount = @($names | Where-Object { $_ -like "assets/minecraft/shaders/core/*" }).Count
    $includeCount = @($names | Where-Object { $_ -like "assets/minecraft/shaders/include/*" }).Count
    $staleCount = @($names | Where-Object { $_ -like "*rendertype_text*" }).Count
    Write-Host "   pack.mcmeta 在根目录：$hasRootMeta"
    Write-Host "   条目总数：$($names.Count)"
    Write-Host "   core 文件：$coreCount（应为 2：text.vsh + text.fsh）"
    Write-Host "   include 文件：$includeCount（应为 31）"
    Write-Host "   残留 rendertype_text：$staleCount（应为 0）"
    Write-Host "   含反斜杠的条目：$($bad.Count)（应为 0）"
    if (-not $hasRootMeta -or $bad.Count -ne 0 -or $coreCount -ne 2 -or $includeCount -ne 31 -or $staleCount -ne 0) {
        Write-Host "zip 结构不对，拒绝产出" -ForegroundColor Red
        exit 1
    }
} finally {
    $zip.Dispose()
}

Write-Host "== 输出 CraftEngine 自托管版（resources/<包>/resourcepack/ 结构）=="
# CraftEngine 会把 plugins/CraftEngine/resources/<包>/resourcepack/ 里的文件按相对路径原样
# 合并进它生成并自动托管的资源包（exclude-core-shaders 默认 false，不会删着色器），
# pack.mcmeta 由 CE 自己生成校验，所以这里刻意不放；pack.yml 照官方内置包的写法。
$ceRoot = "$base\dist\ce-resources"
$ceDir = "$ceRoot\chatprefix-rainbow"
Remove-Item -Recurse -Force $ceRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$ceDir\resourcepack" | Out-Null
Copy-Item -Recurse "$base\assets" "$ceDir\resourcepack\assets" -Force
Copy-Item "$base\LICENSE-Text_Effects.txt" $ceDir -Force
Copy-Item "$base\CREDITS.md" $ceDir -Force   # CE 默认排除 md 扩展名，不会进包，仅随文件夹留说明
@"
author: ChatPrefix
version: $version
description: ChatPrefix rainbow text shader (trimmed Text_Effects for 26.3)
namespace: minecraft
enable: true
"@ | Set-Content -LiteralPath "$ceDir\pack.yml" -Encoding Ascii

# 与 zip 同样的结构断言（不含 pack.mcmeta，多一个 pack.yml + 两个说明文件）
$ceAssets = Get-ChildItem -Recurse -File "$ceDir\resourcepack\assets"
$ceCore = @($ceAssets | Where-Object { $_.FullName -like "*shaders\core\*" })
$ceInclude = @($ceAssets | Where-Object { $_.FullName -like "*shaders\include\*" })
$ceStale = @($ceAssets | Where-Object { $_.Name -like "rendertype_text*" })
if ($ceCore.Count -ne 2 -or $ceInclude.Count -ne 31 -or $ceStale.Count -ne 0) {
    Write-Host "CE resources 文件夹结构不对（core=$($ceCore.Count) include=$($ceInclude.Count) 残留=$($ceStale.Count)），拒绝产出" -ForegroundColor Red
    exit 1
}
Write-Host "   -> $ceDir（core=2，include=31）"

Write-Host "== 打包 CE 自托管版 zip（解压后得到 chatprefix-rainbow/ 文件夹）=="
# GitHub Release 不能挂文件夹，所以额外打一个带顶层目录的 zip；内容与上面的文件夹逐字节一致。
$ceZip = "$base\dist\ChatPrefix-Rainbow-$version-CE.zip"
Remove-Item -Force $ceZip -ErrorAction SilentlyContinue
$ceArchive = [System.IO.Compression.ZipFile]::Open($ceZip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -Recurse -File -LiteralPath $ceDir) {
        $relative = "chatprefix-rainbow/" + $file.FullName.Substring($ceDir.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($ceArchive, $file.FullName, $relative) | Out-Null
    }
} finally {
    $ceArchive.Dispose()
}
$ceEntries = @(([System.IO.Compression.ZipFile]::OpenRead($ceZip)).Entries | ForEach-Object { $_.FullName })
$ceBad = @($ceEntries | Where-Object { $_ -notlike "chatprefix-rainbow/*" -or $_ -like '*\*' })
if ($ceBad.Count -ne 0 -or $ceEntries.Count -ne 36) {
    Write-Host "CE zip 结构不对（条目 $($ceEntries.Count)，异常 $($ceBad.Count)），拒绝产出" -ForegroundColor Red
    exit 1
}
Write-Host "   -> $ceZip（36 个条目，全部在 chatprefix-rainbow/ 下）"

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
Get-ChildItem -Recurse "$base\dist\ce-resources" -File | Select-Object FullName, Length | Format-Table -AutoSize | Out-String -Width 200
Write-Host ""
Write-Host "== SHA1（server.properties 的 resource-pack-sha1 / Paper 的 resource-pack 用这个）=="
(Get-FileHash $out -Algorithm SHA1).Hash.ToLower()

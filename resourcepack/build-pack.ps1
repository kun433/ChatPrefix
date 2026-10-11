# 打包 ChatPrefix 彩虹前缀资源包（裁剪自 TheSalt_ 的 Text Effects）
# 用法：在 PowerShell 中执行  .\build-pack.ps1
# 产物：dist\ChatPrefix-Rainbow-<version>.zip
#
# 注意：这里用 .NET ZipArchive 显式写入正斜杠条目名，刻意不用 Compress-Archive ——
# Windows PowerShell 5.1 的 Compress-Archive 会把条目名写成反斜杠（assets\minecraft\...），
# 而 Minecraft 是按正斜杠找资源的，那样打出来的包会直接失效。
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = "1.2.0"
$stage = "$base\build\stage"
$out = "$base\dist\ChatPrefix-Rainbow-$version.zip"

Write-Host "== 检查必备文件 =="
$required = @(
    "pack.mcmeta",
    "CREDITS.md",
    "LICENSE-Text_Effects.txt",
    "assets\minecraft\shaders\include\_config.glsl",
    "assets\minecraft\shaders\core\rendertype_text.vsh",
    "assets\minecraft\shaders\core\rendertype_text.fsh"
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

Write-Host "== 校验 pack.mcmeta 是合法 JSON =="
try {
    $meta = Get-Content -LiteralPath "$base\pack.mcmeta" -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host "   min_format = $($meta.pack.min_format)，max_format = $($meta.pack.max_format)"
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
    Write-Host "   pack.mcmeta 在根目录：$hasRootMeta"
    Write-Host "   条目总数：$($names.Count)"
    Write-Host "   core 文件：$coreCount（应为 8）"
    Write-Host "   include 文件：$includeCount（应为 30）"
    Write-Host "   含反斜杠的条目：$($bad.Count)（应为 0）"
    if (-not $hasRootMeta -or $bad.Count -ne 0 -or $coreCount -ne 8) {
        Write-Host "zip 结构不对，拒绝产出" -ForegroundColor Red
        exit 1
    }
} finally {
    $zip.Dispose()
}

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
Write-Host ""
Write-Host "== SHA1（server.properties 的 resource-pack-sha1 / Paper 的 resource-pack 用这个）=="
(Get-FileHash $out -Algorithm SHA1).Hash.ToLower()

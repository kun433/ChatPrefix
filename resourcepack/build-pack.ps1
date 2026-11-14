# 打包 ChatPrefix 彩虹前缀资源包 —— 纯 CraftEngine 自托管版（裁剪自 TheSalt_ 的 Text Effects，26.3 版）
#
# 源码结构即 CE 包结构（pack.yml + resourcepack/assets/...），产物只有 CE 自托管这一条交付路径：
#   dist\ce-resources\chatprefix-rainbow\   整个文件夹复制到服务器 plugins/CraftEngine/resources/ 下
#   dist\ChatPrefix-Rainbow-<version>-CE.zip 上面文件夹的 zip 版（Release 附件用，解压后得到 chatprefix-rainbow/）
#
# 独立资源包 zip 已废弃：装了 CE 的服务器资源包槽位被 CE 占用，独立包发不出去；
# 没装 CE 的服务器彩虹走逐消息相位，本来也不需要着色器包。
#
# 注意：这里用 .NET ZipArchive 显式写入正斜杠条目名，刻意不用 Compress-Archive ——
# Windows PowerShell 5.1 的 Compress-Archive 会把条目名写成反斜杠（resourcepack\assets\...），
# 而 Minecraft 是按正斜杠找资源的，那样打出来的包会直接失效。
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$version = "1.3.0"
$out = "$base\dist\ChatPrefix-Rainbow-$version-CE.zip"
$ceRoot = "$base\dist\ce-resources"
$ceDir = "$ceRoot\chatprefix-rainbow"

Write-Host "== 检查必备文件 =="
$required = @(
    "pack.yml",
    "CREDITS.md",
    "LICENSE-Text_Effects.txt",
    "resourcepack\assets\minecraft\shaders\include\_config.glsl",
    "resourcepack\assets\minecraft\shaders\core\text.vsh",
    "resourcepack\assets\minecraft\shaders\core\text.fsh"
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

Write-Host "== 校验 pack.yml =="
$packYml = Get-Content -LiteralPath "$base\pack.yml" -Raw -Encoding UTF8
foreach ($line in @("author: ChatPrefix", "namespace: minecraft", "enable: true")) {
    if ($packYml -notmatch [regex]::Escape($line)) {
        Write-Host "pack.yml 缺少 '$line'" -ForegroundColor Red
        exit 1
    }
}
Write-Host "   author / namespace / enable 齐全"

Write-Host "== 检查没有残留 1.21.11 时代的核心着色器 =="
$stale = @(Get-ChildItem -Path "$base\resourcepack\assets\minecraft\shaders\core" -Filter "rendertype_text*" -ErrorAction SilentlyContinue)
if ($stale.Count -gt 0) {
    Write-Host "  core 里还有 $($stale.Count) 个 rendertype_text* 文件，26.3 已改用 core/text.*，必须删掉" -ForegroundColor Red
    exit 1
}
Write-Host "  干净"

Write-Host "== 暂存到 dist\ce-resources\chatprefix-rainbow =="
Remove-Item -Recurse -Force $ceRoot -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$ceDir\resourcepack" | Out-Null
Copy-Item "$base\pack.yml" $ceDir -Force
Copy-Item "$base\CREDITS.md" $ceDir -Force   # CE 默认排除 md 扩展名，不会进包，仅随文件夹留说明
Copy-Item "$base\LICENSE-Text_Effects.txt" $ceDir -Force
Copy-Item -Recurse "$base\resourcepack\assets" "$ceDir\resourcepack\assets" -Force

# 结构断言（pack.yml + 两个说明文件 + core=2 + include=31）
$ceAssets = Get-ChildItem -Recurse -File "$ceDir\resourcepack\assets"
$ceCore = @($ceAssets | Where-Object { $_.FullName -like "*shaders\core\*" })
$ceInclude = @($ceAssets | Where-Object { $_.FullName -like "*shaders\include\*" })
$ceStale = @($ceAssets | Where-Object { $_.Name -like "rendertype_text*" })
if ($ceCore.Count -ne 2 -or $ceInclude.Count -ne 31 -or $ceStale.Count -ne 0) {
    Write-Host "CE resources 文件夹结构不对（core=$($ceCore.Count) include=$($ceInclude.Count) 残留=$($ceStale.Count)），拒绝产出" -ForegroundColor Red
    exit 1
}
Write-Host "   -> $ceDir（core=2，include=31）"

Write-Host "== 打包 CE zip（正斜杠条目名，解压后得到 chatprefix-rainbow/ 文件夹）=="
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
Remove-Item -Force $out -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($out, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -Recurse -File -LiteralPath $ceDir) {
        $relative = "chatprefix-rainbow/" + $file.FullName.Substring($ceDir.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$entries = @(([System.IO.Compression.ZipFile]::OpenRead($out)).Entries | ForEach-Object { $_.FullName })
$bad = @($entries | Where-Object { $_ -notlike "chatprefix-rainbow/*" -or $_ -like '*\*' })
if ($bad.Count -ne 0 -or $entries.Count -ne 36) {
    Write-Host "CE zip 结构不对（条目 $($entries.Count)，异常 $($bad.Count)），拒绝产出" -ForegroundColor Red
    exit 1
}
Write-Host "   -> $out（36 个条目，全部在 chatprefix-rainbow/ 下）"

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
Get-ChildItem -Recurse "$ceRoot" -File | Measure-Object | ForEach-Object { Write-Host "ce-resources 文件数：$($_.Count)（应为 36）" }
Write-Host ""
Write-Host "== SHA1（Release 附件校验用）=="
(Get-FileHash $out -Algorithm SHA1).Hash.ToLower()

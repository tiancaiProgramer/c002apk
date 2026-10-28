param(
    [string]$VersionName = '0.1.6',
    [int]$VersionCode = 278,
    [string]$TestApplicationId = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $buildArgs = @(':app:assembleRelease', "-PreleaseVersionName=$VersionName", "-PreleaseVersionCode=$VersionCode")
    if ($TestApplicationId) { $buildArgs += "-PtestApplicationId=$TestApplicationId" }
    & .\gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    $metadata = Get-Content -LiteralPath 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
    $element = $metadata.elements[0]
    $apkPath = Join-Path 'app/build/outputs/apk/release' $element.outputFile
    $apk = Get-Item -LiteralPath $apkPath
    if ($apk.Length -gt 10MB) {
        throw 'Release APK exceeds 10 MiB. Check R8/resource shrinking and avoid debug or disableR8 builds.'
    }
    New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
    $target = Join-Path 'dist' "c002apk-$($element.versionName).apk"
    Copy-Item -LiteralPath $apk.FullName -Destination $target
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($target))" | Set-Content -LiteralPath 'dist/SHA256SUMS' -Encoding utf8
    Write-Output ('Built {0} ({1:F2} MB, R8 enabled by default)' -f $target, ($apk.Length / 1000000))
} finally {
    Pop-Location
}

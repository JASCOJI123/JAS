<#
.SYNOPSIS
  Builds, tests and publishes Ultimate PC Optimizer, then (optionally) compiles the installer.

.EXAMPLE
  ./scripts/publish.ps1 -Version 1.2.0
  ./scripts/publish.ps1 -Version 1.2.0 -SkipInstaller
#>
param(
    [string]$Version = "1.0.0",
    [string]$Configuration = "Release",
    [switch]$SkipTests,
    [switch]$SkipInstaller
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$publishDir = Join-Path $root "artifacts/publish"
$installerDir = Join-Path $root "artifacts/installer"

Push-Location $root
try {
    if (-not $SkipTests) {
        dotnet test tests/UltimatePcOptimizer.Tests -c $Configuration
        if ($LASTEXITCODE -ne 0) { throw "Tests failed." }
    }

    if (Test-Path $publishDir) { Remove-Item $publishDir -Recurse -Force }
    dotnet publish src/UltimatePcOptimizer.App -c $Configuration -r win-x64 --self-contained true `
        -p:Version=$Version -p:DebugType=none -o $publishDir
    if ($LASTEXITCODE -ne 0) { throw "Publish failed." }

    if (-not $SkipInstaller) {
        $iscc = @(
            "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
            "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
        ) | Where-Object { Test-Path $_ } | Select-Object -First 1
        if (-not $iscc) { throw "Inno Setup 6 (ISCC.exe) not found. Install it or use -SkipInstaller." }

        & $iscc "/DAppVersion=$Version" "/DPublishDir=$publishDir" "/O$installerDir" (Join-Path $root "installer/UltimatePcOptimizer.iss")
        if ($LASTEXITCODE -ne 0) { throw "Installer build failed." }
        Write-Host "Installer: $installerDir"
    }
}
finally {
    Pop-Location
}

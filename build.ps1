#!/usr/bin/env pwsh

Param(
    [Alias('p')]
    [string]$ProfileArg = "all",

    [Alias('X')]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

function Show-Usage {
@"
 OPTIONS
  -p [all(a)/desktop(d)/console(c)]       select profile
  -X                                      enable debug log

 EXAMPLES
  build.ps1 -p desktop -X
  build.ps1 -p c -X
  build.ps1
"@ | Write-Output
}

$SelectedProfile = switch ($ProfileArg.ToLower()) {
    'a' { 'all' }
    'd' { 'desktop' }
    'c' { 'console' }
    Default { $_ }
}

if ($SelectedProfile -notin @('all','desktop','console')) {
    Show-Usage
    exit 1
}

$Dir                      = (Get-Location).Path
$Target                   = Join-Path $Dir "target"
$ProductTarget            = Join-Path $Dir "com.cubrid.cubridmigration.product/target"
$ConsoleTarget            = Join-Path $Dir "com.cubrid.cubridmigration.console/target"
$VersionFilePath          = Join-Path $Dir "VERSION"
$ReleaseVersionFilePath   = Join-Path $Dir "com.cubrid.cubridmigration.ui/version.properties"
$ReleaseVersion           = ""

$CmtProductName           = "CUBRID-Migration-Toolkit"
$CmtConsoleName           = "$CmtProductName-console"
$CmtSiteName              = "$CmtProductName-site"

function Resolve-Maven {
    $Cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($Cmd) { return $Cmd.Source }

    if ($env:MAVEN_HOME) {
        $Path = Join-Path $env:MAVEN_HOME "bin" "mvn"
        if (Test-Path $Path) { return $Path }
    }
    Write-Error "Maven not found in PATH or MAVEN_HOME"
}

function Show-Env {
    if ($env:JAVA_HOME) { Write-Output "JAVA_HOME: $($env:JAVA_HOME)" }
    if ($env:MAVEN_HOME) { Write-Output "MAVEN_HOME: $($env:MAVEN_HOME)" }
}

function Update-BuildVersion {
    [CmdletBinding(SupportsShouldProcess=$true)]
    param()
    if (-not $PSCmdlet.ShouldProcess("version.properties", "update build version")) { return }

    Write-Output "Version File Update....  (com.cubrid.cubridmigration.ui/version.properties)"

    $CommitNumber = if (Test-Path ".git") {
        "{0:D4}" -f [int](& git rev-list --count HEAD).Trim()
    } else { "0000" }
    $Version = ((Get-Content $VersionFilePath | Select-String "^version=").ToString().Split('=')[1]).Trim()
    $ReleaseVersion = "$Version.$CommitNumber"

    (Get-Content $ReleaseVersionFilePath |
        Where-Object { $_ -notmatch "^(releaseVersion|buildVersionId)=" }) |
        Set-Content $ReleaseVersionFilePath
    Add-Content $ReleaseVersionFilePath "releaseVersion=$Version"
    Add-Content $ReleaseVersionFilePath "buildVersionId=$ReleaseVersion"

    Write-Output "VERSION= $Version"
    Write-Output "COMMIT_NUMBER= $CommitNumber"
    Write-Output "RELEASE_VERSION= $ReleaseVersion"
}

function Copy-DesktopCMTToDirectory {
    $CmtLinux = Join-Path $ProductTarget "$CmtProductName-$ReleaseVersion-linux-x86_64.tar.gz"
    if (Test-Path $CmtLinux) { Copy-Item $CmtLinux -Destination $Target -Force -Verbose }

    $CmtMac = Join-Path $ProductTarget "$CmtProductName-$ReleaseVersion-macosx-cocoa-x86_64.tar.gz"
    if (Test-Path $CmtMac) { Copy-Item $CmtMac -Destination $Target -Force -Verbose }

    $CmtWindows = Join-Path $ProductTarget "$CmtProductName-$ReleaseVersion-windows-x64.zip"
    if (Test-Path $CmtWindows) { Copy-Item $CmtWindows -Destination $Target -Force -Verbose }

    $CmtSiteTargz = Join-Path $ProductTarget "$CmtSiteName-$ReleaseVersion.tar.gz"
    if (Test-Path $CmtSiteTargz) { Copy-Item $CmtSiteTargz -Destination $Target -Force -Verbose }

    $CmtSiteZip = Join-Path $ProductTarget "$CmtSiteName-$ReleaseVersion.zip"
    if (Test-Path $CmtSiteZip) { Copy-Item $CmtSiteZip -Destination $Target -Force -Verbose }
}

function Copy-ConsoleCMTToDirectory {
    $ConsoleLinux = Join-Path $ConsoleTarget "$CmtConsoleName-$ReleaseVersion-linux.tar.gz"
    if (Test-Path $ConsoleLinux) { Copy-Item $ConsoleLinux -Destination $Target -Force -Verbose }

    $ConsoleWindows = Join-Path $ConsoleTarget "$CmtConsoleName-$ReleaseVersion-windows.zip"
    if (Test-Path $ConsoleWindows) { Copy-Item $ConsoleWindows -Destination $Target -Force -Verbose }
}

function Copy-CMTToDirectory {
    if (-not (Test-Path $Target)) { New-Item -ItemType Directory -Path $Target | Out-Null }
    switch ($SelectedProfile) {
        "all" { Copy-DesktopCMTToDirectory; Copy-ConsoleCMTToDirectory }
        "desktop" { Copy-DesktopCMTToDirectory }
        "console" { Copy-ConsoleCMTToDirectory }
    }
}

function Show-CMTBanner {
@'

 ____                   ______
/\  _`\     /`\_/`\    /\__  _\
\ \ \/\_\  /\      \   \/_\/\ \/
 \ \ \/_/_ \ \ \__\ \     \ \ \
  \ \ \L\ \ \ \ \_/\ \     \ \ \
   \ \____/  \ \_\\ \_\     \ \_\
    \/___/    \/_/ \/_/      \/_/


'@ | Write-Output
}

# ----------------------------- MAIN ----------------------------- #
Show-CMTBanner
$Mvn = Resolve-Maven
$MvnDebug = if ($Debug) { @("-Dtycho.debug.resolver=true", "-X") } else { @() }
Show-Env
Update-BuildVersion

switch ($SelectedProfile) {
    "all" {
        & $Mvn clean package "-Dcubridmigration-version=$ReleaseVersion" -Pdesktop $MvnDebug
        & $Mvn clean package "-Dcubridmigration-version=$ReleaseVersion" -Pconsole $MvnDebug
    }
    "desktop" {
        & $Mvn clean package "-Dcubridmigration-version=$ReleaseVersion" -Pdesktop $MvnDebug
    }
    "console" {
        & $Mvn clean package "-Dcubridmigration-version=$ReleaseVersion" -Pconsole $MvnDebug
    }
}

Copy-CMTToDirectory

#!/usr/bin/env pwsh

Param(
    [Alias('p')]
    [string]$ProfileArg = "all",

    [Alias('X')]
    [switch]$Debug
)

$ErrorActionPreference = "Stop"

function show_usage {
@"
 OPTIONS
  -p [all(a)/desktop(d)/console(c)]       select profile
  -X                                      enable debug log

 EXAMPLES
  build.ps1 -p desktop -X
  build.ps1 -p c -X
  build.ps1
"@ | Write-Host
}

$Profile = switch ($ProfileArg.ToLower()) {
    'a' { 'all' }
    'd' { 'desktop' }
    'c' { 'console' }
    Default { $_ }
}

if ($Profile -notin @('all','desktop','console')) {
    show_usage
    exit 1
}

$DIR                       = (Get-Location).Path
$TARGET                    = Join-Path $DIR "target"
$PRODUCT_TARGET            = Join-Path $DIR "com.cubrid.cubridmigration.product/target"
$CONSOLE_TARGET            = Join-Path $DIR "com.cubrid.cubridmigration.console/target"
$VERSION_FILE_PATH         = Join-Path $DIR "VERSION"
$RELEASE_VERSION_FILE_PATH = Join-Path $DIR "com.cubrid.cubridmigration.ui/version.properties"
$RELEASE_VERSION           = ""
$CMT_PRODUCT_NAME          = "CUBRID-Migration-Toolkit"
$CMT_CONSOLE_NAME          = "$CMT_PRODUCT_NAME-console"
$CMT_SITE_NAME             = "$CMT_PRODUCT_NAME-site"

function resolve_maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    if ($env:MAVEN_HOME) {
        $path = Join-Path $env:MAVEN_HOME "bin" "mvn"
        if (Test-Path $path) { return $path }
    }

    Write-Error "Maven not found in PATH or MAVEN_HOME"
}

function print_env {
    if ($env:JAVA_HOME) { Write-Host "JAVA_HOME: $($env:JAVA_HOME)" }
    if ($env:MAVEN_HOME) { Write-Host "MAVEN_HOME: $($env:MAVEN_HOME)" }
}

function update_build_version {
    Write-Host "Version File Update....  (com.cubrid.cubridmigration.ui/version.properties)"

    $COMMIT_NUMBER = if (Test-Path ".git") {
        "{0:D4}" -f [int](& git rev-list --count HEAD).Trim()
    } else { "0000" }

    $VERSION = ((Get-Content $VERSION_FILE_PATH | Select-String "^version=").ToString().Split('=')[1]).Trim()

    (Get-Content $RELEASE_VERSION_FILE_PATH |
        Where-Object { $_ -notmatch "^(releaseVersion|buildVersionId)=" }) |
        Set-Content $RELEASE_VERSION_FILE_PATH

    Add-Content $RELEASE_VERSION_FILE_PATH "releaseVersion=$VERSION"

    $script:RELEASE_VERSION = "$VERSION.$COMMIT_NUMBER"
    Add-Content $RELEASE_VERSION_FILE_PATH "buildVersionId=$RELEASE_VERSION"

    Write-Host "VERSION= $VERSION"
    Write-Host "COMMIT_NUMBER= $COMMIT_NUMBER"
    Write-Host "RELEASE_VERSION= $RELEASE_VERSION"
}

function copy_desktopcmt_to_directory {
    $CMT_LINUX = Join-Path $PRODUCT_TARGET "$CMT_PRODUCT_NAME-$RELEASE_VERSION-linux-x86_64.tar.gz"
    if (Test-Path $CMT_LINUX) { Copy-Item $CMT_LINUX -Destination $TARGET -Force -Verbose }

    $CMT_MAC = Join-Path $PRODUCT_TARGET "$CMT_PRODUCT_NAME-$RELEASE_VERSION-macosx-cocoa-x86_64.tar.gz"
    if (Test-Path $CMT_MAC) { Copy-Item $CMT_MAC -Destination $TARGET -Force -Verbose }

    $CMT_WINDOWS = Join-Path $PRODUCT_TARGET "$CMT_PRODUCT_NAME-$RELEASE_VERSION-windows-x64.zip"
    if (Test-Path $CMT_WINDOWS) { Copy-Item $CMT_WINDOWS -Destination $TARGET -Force -Verbose }

    $CMT_SITE_TAR_GZ = Join-Path $PRODUCT_TARGET "$CMT_SITE_NAME-$RELEASE_VERSION.tar.gz"
    if (Test-Path $CMT_SITE_TAR_GZ) { Copy-Item $CMT_SITE_TAR_GZ -Destination $TARGET -Force -Verbose }

    $CMT_SITE_ZIP = Join-Path $PRODUCT_TARGET "$CMT_SITE_NAME-$RELEASE_VERSION.zip"
    if (Test-Path $CMT_SITE_ZIP) { Copy-Item $CMT_SITE_ZIP -Destination $TARGET -Force -Verbose }
}

function copy_consolecmt_to_directory {
    $CONSOLE_LINUX = Join-Path $CONSOLE_TARGET "$CMT_CONSOLE_NAME-$RELEASE_VERSION-linux.tar.gz"
    if (Test-Path $CONSOLE_LINUX) { Copy-Item $CONSOLE_LINUX -Destination $TARGET -Force -Verbose }

    $CONSOLE_WINDOWS = Join-Path $CONSOLE_TARGET "$CMT_CONSOLE_NAME-$RELEASE_VERSION-windows.zip"
    if (Test-Path $CONSOLE_WINDOWS) { Copy-Item $CONSOLE_WINDOWS -Destination $TARGET -Force -Verbose }
}

function copy_cmt_to_directory {
    if (-not (Test-Path $TARGET)) { New-Item -ItemType Directory -Path $TARGET | Out-Null }
    switch ($Profile) {
        "all" { copy_desktopcmt_to_directory; copy_consolecmt_to_directory }
        "desktop" { copy_desktopcmt_to_directory }
        "console" { copy_consolecmt_to_directory }
    }
}

function cmt_banner {
@'

 ____                   ______
/\  _`\     /`\_/`\    /\__  _\
\ \ \/\_\  /\      \   \/_\/\ \/
 \ \ \/_/_ \ \ \__\ \     \ \ \
  \ \ \L\ \ \ \ \_/\ \     \ \ \
   \ \____/  \ \_\\ \_\     \ \_\
    \/___/    \/_/ \/_/      \/_/


'@ | Write-Host
}

# ----------------------------- MAIN ----------------------------- #
cmt_banner
$MVN = resolve_maven
$MVN_DEBUG = if ($Debug) { @("-Dtycho.debug.resolver=true", "-X") } else { @() }
print_env
update_build_version

switch ($Profile) {
    "all" {
        & $MVN clean package "-Dcubridmigration-version=$RELEASE_VERSION" -Pdesktop $MVN_DEBUG
        & $MVN clean package "-Dcubridmigration-version=$RELEASE_VERSION" -Pconsole $MVN_DEBUG
    }
    "desktop" {
        & $MVN clean package "-Dcubridmigration-version=$RELEASE_VERSION" -Pdesktop $MVN_DEBUG
    }
    "console" {
        & $MVN clean package "-Dcubridmigration-version=$RELEASE_VERSION" -Pconsole $MVN_DEBUG
    }
}

copy_cmt_to_directory

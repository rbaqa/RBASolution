# ============================================================================
# setup-env.ps1 - RBA QA automation: reproducible environment setup (Windows)
#
# Checks / prepares the environment required by the task and INSTALLS any
# missing or outdated component automatically (elevates to Administrator only
# when an installation is required):
#   * Java JDK 8+ (JAVA_HOME)                    -> installs Eclipse Temurin 8
#   * Google Chrome + matching ChromeDriver       -> installs Chrome; the
#                                                    matching driver is resolved
#                                                    by WebDriverManager at test
#                                                    runtime
#   * Maven (pinned 3.9.6)                        -> provided by the committed
#                                                    Maven wrapper (mvnw), which
#                                                    self-downloads on first run
#
# Installations are done with winget (preferred) or Chocolatey (fallback).
# Maven frameworks (Selenium / TestNG / REST Assured / Jackson / Log4j2 /
# Allure) need no install: Maven resolves the exact versions pinned in pom.xml
# on the first build (requires internet).
#
# USAGE:
#   powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
#   powershell -ExecutionPolicy Bypass -File .\setup-env.ps1 -SkipInstall
#   powershell -ExecutionPolicy Bypass -File .\setup-env.ps1 -Uninstall
#
# -SkipInstall : validate only; never install, hard-fail on missing software.
#
# -Uninstall   : teardown - removes ONLY what this script installed earlier
#                (tracked in reports/.setup-installed.json) plus downloaded
#                Maven caches, returning a machine to its clean pre-setup state.
#                Pre-existing software is never touched. reports/ is preserved.
#
# Idempotent: safe to run multiple times.
# ============================================================================

param(
    [switch]$SkipInstall,
    [switch]$ElevatedInstall,  # internal: install phase only, used on UAC re-run
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg)  { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "  [WARN] $msg" -ForegroundColor Yellow }
function Write-Fail($msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red }

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user    = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = ($machine, $user | Where-Object { $_ }) -join ';'
}

function Test-Command([string]$Name) {
    return ($null -ne (Get-Command $Name -ErrorAction SilentlyContinue))
}

function Test-Admin {
    $principal = New-Object Security.Principal.WindowsPrincipal(
        [Security.Principal.WindowsIdentity]::GetCurrent())
    return $principal.IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-JavaMajor {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $cmd) { return 0 }
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $rec = & java -version 2>&1 | Select-Object -First 1
    $ErrorActionPreference = $oldEap
    $line = if ($rec) { $rec.ToString() } else { '' }
    if ($line -match '"(?:1\.)?(\d+)[._]') { return [int]$Matches[1] }
    return 0
}

function Get-JavaVersionLine {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $cmd) { return '(not found on PATH)' }
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $rec = & java -version 2>&1 | Select-Object -First 1
    $ErrorActionPreference = $oldEap
    $line = if ($rec) { $rec.ToString().Trim() } else { '' }
    if (-not $line) { return '(no version output)' }
    return $line
}

function Get-ChromePath {
    foreach ($hive in @(
            'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe',
            'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\chrome.exe')) {
        $reg = Get-ItemProperty -Path $hive -ErrorAction SilentlyContinue
        if ($reg -and $reg.'(default)' -and (Test-Path $reg.'(default)')) {
            return $reg.'(default)'
        }
    }
    foreach ($p in @(
            "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
            "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
            "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe")) {
        if (Test-Path $p) { return $p }
    }
    return $null
}

function Get-JavaHomeFromPath {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) {
        $bin  = Split-Path -Parent $cmd.Source
        $home = Split-Path -Parent $bin
        if (Test-Path (Join-Path $home 'bin\java.exe')) { return $home }
    }
    return $null
}

function Get-PomVersion([string]$Key) {
    $m = Select-String -Path '.\pom.xml' -Pattern "<$Key>(.*?)</$Key>" `
         -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($m) { return $m.Matches[0].Groups[1].Value }
    return '-'
}

function Test-Connectivity {
    try {
        $r = Invoke-WebRequest -Uri 'https://www.rba.hr' -Method Head `
             -UseBasicParsing -TimeoutSec 15
        return "reachable (HTTP $($r.StatusCode))"
    } catch {
        return $null
    }
}

function Get-PackageManager {
    if (Test-Command 'winget') { return 'winget' }
    if (Test-Command 'choco')  { return 'choco' }
    return ''
}

function Get-MarkerPath {
    return (Join-Path $PSScriptRoot 'reports\.setup-installed.json')
}

function Save-Marker {
    param($Components)
    $dir = Split-Path -Parent (Get-MarkerPath)
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $state = @{ components = @($Components) }
    $state | ConvertTo-Json -Depth 6 | Set-Content -Path (Get-MarkerPath) -Encoding UTF8
}

function Load-Marker {
    $p = Get-MarkerPath
    if (-not (Test-Path $p)) { return @() }
    $state = Get-Content -Path $p -Raw | ConvertFrom-Json
    if ($null -eq $state -or $null -eq $state.components) { return @() }
    return @($state.components)
}

function Install-Package {
    param([string]$WingetId, [string]$ChocoId)
    if (Test-Command 'winget') {
        Write-Host "    winget install --id $WingetId --silent ..."
        try { & winget install --id $WingetId --silent --accept-package-agreements `
            --accept-source-agreements --disable-interactivity 2>&1 | Out-Null }
        catch { }
        return ($LASTEXITCODE -eq 0)
    }
    if (Test-Command 'choco') {
        Write-Host "    choco install -y $ChocoId ..."
        try { & choco install -y "$ChocoId" 2>&1 | Out-Null } catch { }
        return ($LASTEXITCODE -eq 0)
    }
    Write-Fail "No package manager found (winget or Chocolatey). Install winget"
    Write-Fail "(App Installer from the Microsoft Store) or Chocolatey, then re-run."
    return $false
}

function Install-Java {
    Write-Host "  Installing Eclipse Temurin JDK 8..."
    if (-not (Install-Package -WingetId 'EclipseAdoptium.Temurin.8.JDK' -ChocoId 'temurin8')) {
        return $false
    }
    Refresh-Path
    $jh = Get-JavaHomeFromPath
    if ($jh) {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $jh, 'Machine')
        $env:JAVA_HOME = $jh
        Write-Ok "JAVA_HOME = $jh (persisted for future sessions)."
    }
    return $true
}

function Install-Chrome {
    Write-Host "  Installing Google Chrome..."
    if (-not (Install-Package -WingetId 'Google.Chrome' -ChocoId 'googlechrome')) {
        return $false
    }
    return $true
}

function Perform-Install {
    param($NeedInstall)
    Write-Step "Installing missing / outdated components"
    $installed = @()

    foreach ($item in $NeedInstall) {
        Write-Host "  -> $item"
        if ($item -eq 'Java') {
            if (-not (Install-Java)) {
                Write-Fail "Failed to install $item. See the message above."
                exit 1
            }
            $installed += @{
                name      = 'java'
                manager   = Get-PackageManager
                packageId = 'EclipseAdoptium.Temurin.8.JDK'
                chocoId   = 'temurin8'
                javaHome  = $env:JAVA_HOME
            }
        } elseif ($item -eq 'Google Chrome') {
            if (-not (Install-Chrome)) {
                Write-Fail "Failed to install $item. See the message above."
                exit 1
            }
            $installed += @{
                name      = 'chrome'
                manager   = Get-PackageManager
                packageId = 'Google.Chrome'
                chocoId   = 'googlechrome'
            }
        } else {
            Write-Fail "Unknown component: $item"
            exit 1
        }
        Write-Ok "$item installed."
    }

    if ($installed.Count -gt 0) { Save-Marker $installed }
}

function Perform-Uninstall {
    Write-Step "Teardown - removing the environment created by setup-env.ps1"

    $marker = Load-Marker
    if ($marker.Count -eq 0) {
        Write-Ok "Nothing was installed by setup-env.ps1 (clean baseline) - nothing to remove."
        return
    }

    foreach ($c in $marker) {
        if ($c.manager -eq 'winget' -and $c.packageId) {
            Write-Host "  Uninstalling '$($c.packageId)' via winget..."
            try { & winget uninstall --id $c.packageId --silent `
                --accept-source-agreements 2>&1 | Out-Null } catch { }
            if ($LASTEXITCODE -eq 0) { Write-Ok "Removed $($c.packageId)." }
            else { Write-Warn "winget could not fully uninstall $($c.packageId) (exit $LASTEXITCODE)." }
        } elseif ($c.manager -eq 'choco' -and $c.chocoId) {
            Write-Host "  Uninstalling '$($c.chocoId)' via Chocolatey..."
            try { & choco uninstall -y "$($c.chocoId)" 2>&1 | Out-Null } catch { }
            if ($LASTEXITCODE -eq 0) { Write-Ok "Removed $($c.chocoId)." }
            else { Write-Warn "choco could not fully uninstall $($c.chocoId) (exit $LASTEXITCODE)." }
        } else {
            Write-Warn "Unknown package manager in marker for $($c.name) - skipped."
        }

        if ($c.name -eq 'java' -and $c.javaHome) {
            if ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') -eq $c.javaHome) {
                [Environment]::SetEnvironmentVariable('JAVA_HOME', $null, 'Machine')
                Write-Ok "Cleared JAVA_HOME ($($c.javaHome))."
            }
            $bin = Join-Path $c.javaHome 'bin'
            $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
            if ($machinePath -and ($machinePath.Split(';') -contains $bin)) {
                $newPath = ($machinePath.Split(';') | Where-Object { $_ -ne $bin }) -join ';'
                [Environment]::SetEnvironmentVariable('Path', $newPath, 'Machine')
                Write-Ok "Removed $bin from PATH."
            }
        } elseif ($c.name -eq 'chrome') {
            $chromeData = Join-Path $env:LOCALAPPDATA 'Google\Chrome'
            if (Test-Path $chromeData) {
                Remove-Item -Recurse -Force $chromeData -ErrorAction SilentlyContinue
                Write-Ok "Removed Chrome user data: $chromeData"
            }
        }
    }

    foreach ($cache in @(
            (Join-Path $env:USERPROFILE '.m2\wrapper'),
            (Join-Path $env:USERPROFILE '.m2\repository'))) {
        if (Test-Path $cache) {
            Remove-Item -Recurse -Force $cache -ErrorAction SilentlyContinue
            Write-Ok "Removed cache: $cache"
        }
    }

    Remove-Item (Get-MarkerPath) -Force -ErrorAction SilentlyContinue
    Write-Ok "Environment restored to clean state; reports/ left untouched."
}

# --- bootstrap: pick up already-installed tools in this session ---
Refresh-Path

$javaMajor   = Get-JavaMajor
$javaLine    = Get-JavaVersionLine
$chromePath  = Get-ChromePath

$needInstall = @()
if ($javaMajor -lt 8)   { $needInstall += 'Java' }
if (-not $chromePath)   { $needInstall += 'Google Chrome' }

# --- internal elevated install phase (spawned by the parent below) ---
if ($ElevatedInstall) {
    if ($needInstall.Count -gt 0) { Perform-Install $needInstall }
    Write-Host "`n>>> Elevated installation finished." -ForegroundColor Cyan
    exit 0
}

# --- teardown: remove only what this script installed, restore clean state ---
if ($Uninstall) {
    if ((Test-Path (Get-MarkerPath)) -and -not (Test-Admin)) {
        Write-Host "Administrator rights are required to uninstall the components." `
                   "Launching an elevated teardown..."
        $argList = '-NoProfile -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -Uninstall'
        try {
            Start-Process -FilePath 'powershell.exe' -ArgumentList $argList `
                           -Verb RunAs -Wait -ErrorAction Stop | Out-Null
        } catch {
            Write-Fail "Elevation was declined or failed: $($_.Exception.Message)"
            exit 1
        }
    }
    Perform-Uninstall
    exit 0
}

# --- elevation: only when something actually needs installing ---
if ($needInstall.Count -gt 0 -and -not $SkipInstall -and -not (Test-Admin)) {
    Write-Host "Administrator rights are required to install the missing" `
               "components. Launching an elevated setup..."
    $argList = '-NoProfile -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -ElevatedInstall'
    try {
        Start-Process -FilePath 'powershell.exe' -ArgumentList $argList `
                       -Verb RunAs -Wait -ErrorAction Stop | Out-Null
    } catch {
        Write-Fail "Elevation was declined or failed: $($_.Exception.Message)"
        exit 1
    }
    # child process already installed; re-sync this session
    Refresh-Path
    $javaMajor  = Get-JavaMajor
    $javaLine   = Get-JavaVersionLine
    $chromePath = Get-ChromePath
} elseif ($needInstall.Count -gt 0 -and -not $SkipInstall) {
    # already elevated - install inline
    Perform-Install $needInstall
    $javaMajor  = Get-JavaMajor
    $javaLine   = Get-JavaVersionLine
    $chromePath = Get-ChromePath
} elseif ($needInstall.Count -gt 0) {
    Write-Host "  -SkipInstall given: not installing, validating as-is."
}

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " RBA QA Automation - Environment setup" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# --- 1. Java ---
Write-Step "Checking Java"
if ($javaMajor -ge 8) {
    Write-Ok "Java found: $javaLine   (JAVA_HOME = $env:JAVA_HOME)"
} else {
    Write-Fail "Java 8+ is required but unavailable (found: $javaLine)."
    Write-Fail "Re-run without -SkipInstall to install it automatically."
    exit 1
}

# --- 2. Maven wrapper ---
Write-Step "Checking Maven wrapper"
if (-not (Test-Path '.\mvnw.cmd') -or -not (Test-Path '.\.mvn\wrapper\maven-wrapper.properties')) {
    Write-Fail "Maven wrapper files not found. Run from the project root."
    exit 1
}
$mvnOut     = (& .\mvnw.cmd -v 2>&1 | Out-String)
$mvnLine    = ($mvnOut -split '\r?\n' | Select-String 'Apache Maven' | Select-Object -First 1)
if ($LASTEXITCODE -eq 0 -and $mvnLine) {
    Write-Ok "Maven wrapper works: $(($mvnLine.ToString()).Trim())"
} else {
    Write-Fail "Maven wrapper could not fetch the pinned Maven (exit $LASTEXITCODE)."
    Write-Fail "Check internet connectivity, then re-run."
    exit 1
}

# --- 3. Google Chrome ---
Write-Step "Checking Google Chrome"
if ($chromePath) {
    $chromeVersion = (Get-Item $chromePath).VersionInfo.ProductVersion
    Write-Ok "Chrome found: $chromePath (version $chromeVersion)"
    Write-Ok "ChromeDriver is resolved automatically by WebDriverManager at test runtime."
} else {
    Write-Fail "Google Chrome not found."
    Write-Fail "Re-run without -SkipInstall to install it automatically."
    exit 1
}

# --- 4. Configuration & connectivity ---
Write-Step "Validating configuration"
if (Test-Path '.\config\test-config.properties') {
    Write-Ok "Configuration present: .\config\test-config.properties"
} else {
    Write-Warn "config\test-config.properties not found; will fall back to classpath default."
}

Write-Step "Connectivity sanity check (optional)"
$conn = Test-Connectivity
if ($conn) {
    Write-Ok "rba.hr $conn"
} else {
    Write-Warn "rba.hr not reachable right now; tests hitting the live site will fail."
}

# --- 5. Toolchain summary ---
Write-Step "Toolchain summary"
Write-Host "  Java             : $javaLine"
Write-Host "  Maven            : $(($mvnLine.ToString()).Trim())  (pinned by Maven wrapper)"
Write-Host "  Chrome           : $chromeVersion (WebDriverManager picks the matching driver)"
Write-Host "  Selenium         : $(Get-PomVersion 'selenium.version')"
Write-Host "  WebDriverManager : $(Get-PomVersion 'webdrivermanager.version')"
Write-Host "  TestNG           : $(Get-PomVersion 'testng.version')"
Write-Host "  REST Assured     : $(Get-PomVersion 'restassured.version')"
Write-Host "  Jackson          : $(Get-PomVersion 'jackson.version')"
Write-Host "  Log4j2           : $(Get-PomVersion 'log4j.version')"
Write-Host "  Allure           : $(Get-PomVersion 'allure.version')"
Write-Host "  (Framework versions are fixed in pom.xml and resolve on the first Maven build.)"

Write-Host "`n===================================================" -ForegroundColor Cyan
Write-Host " Environment ready." -ForegroundColor Cyan
Write-Host " Next: run the suite with:  .\mvnw.cmd test" -ForegroundColor Cyan
Write-Host " API + unit only (no browser): .\mvnw.cmd -Dsuite.file=verify-unit-api.xml test" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

exit 0
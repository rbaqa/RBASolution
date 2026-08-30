# ============================================================================
# setup-env.ps1 - RBA QA automation: reproducible environment setup (Windows)
#
# Checks / prepares the environment required by the task:
#   * Java JDK 8+ (JAVA_HOME)
#   * Maven (via the committed Maven wrapper - no global install required)
#   * Google Chrome + matching ChromeDriver (via WebDriverManager at runtime)
#
# USAGE:
#   powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
#
# Idempotent: safe to run multiple times.
# ============================================================================

$ErrorActionPreference = 'Stop'

function Write-Step($msg)  { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "  [WARN] $msg" -ForegroundColor Yellow }
function Write-Fail($msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red }

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host " RBA QA Automation - Environment setup" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

# --- 1. Java ---
Write-Step "Checking Java"
$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    $ver = (& java -version 2>&1 | Select-Object -First 1)
    $major = if ($ver -match '"(?:1\.)?(\d+)') { [int]$Matches[1] } else { 0 }
    if ($major -ge 8) {
        Write-Ok "Java found: $ver"
    } else {
        Write-Fail "Java found but is older than 8: $ver"
    }
} else {
    Write-Fail "Java not found on PATH. Install JDK 8+ and set JAVA_HOME, then re-run."
    exit 1
}
if ($env:JAVA_HOME) {
    Write-Ok "JAVA_HOME = $env:JAVA_HOME"
} else {
    Write-Warn "JAVA_HOME is not set. Some builds may require it."
}

# --- 2. Maven wrapper ---
Write-Step "Checking Maven wrapper"
if (Test-Path .\mvnw.cmd) {
    Write-Ok "Maven wrapper present (.\mvnw.cmd) - no global Maven required."
} else {
    Write-Fail "mvnw.cmd not found. Run from the project root."
    exit 1
}
try {
    $mvnv = & .\mvnw.cmd -v 2>&1 | Select-String "Apache Maven" | Select-Object -First 1
    Write-Ok "Maven wrapper works: $mvnv"
} catch {
    Write-Warn "Maven wrapper needs internet for its first download (it caches afterwards)."
}

# --- 3. Google Chrome ---
Write-Step "Checking Google Chrome"
$chromePaths = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)
$found = $false
foreach ($p in $chromePaths) {
    if (Test-Path $p) {
        $v = (Get-Item $p).VersionInfo.ProductVersion
        Write-Ok "Chrome found: $p (version $v)"
        Write-Ok "ChromeDriver is resolved automatically by WebDriverManager at test runtime."
        $found = $true
        break
    }
}
if (-not $found) {
    Write-Fail "Google Chrome not found. Install Chrome, then re-run."
    exit 1
}

# --- 4. Configuration & connectivity ---
Write-Step "Validating configuration"
if (Test-Path .\config\test-config.properties) {
    Write-Ok "Configuration present: .\config\test-config.properties"
} else {
    Write-Warn "config\test-config.properties not found; will fall back to classpath default."
}

Write-Step "Connectivity sanity check (optional)"
try {
    $rba = Invoke-WebRequest -Uri 'https://www.rba.hr' -Method Head -UseBasicParsing -TimeoutSec 15
    Write-Ok "rba.hr reachable (HTTP $($rba.StatusCode))"
} catch {
    Write-Warn "rba.hr not reachable right now: $($_.Exception.Message)"
}

Write-Host "`n===================================================" -ForegroundColor Cyan
Write-Host " Environment check complete." -ForegroundColor Cyan
Write-Host " Next: run the suite with:  .\mvnw.cmd test" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

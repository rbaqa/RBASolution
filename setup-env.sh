#!/usr/bin/env bash
# ============================================================================
# setup-env.sh - RBA QA automation: reproducible environment setup (Linux)
#
# Bash twin of setup-env.ps1 (Windows). Checks / prepares everything the task
# requires and INSTALLS any missing component automatically (via apt, using
# sudo only when an installation is needed):
#   * Java JDK 8+ (JAVA_HOME)          -> installs Eclipse Temurin 8
#   * Google Chrome + matching driver  -> installs Google Chrome stable; the
#                                         matching ChromeDriver is resolved by
#                                         WebDriverManager at test runtime
#   * Maven (pinned 3.9.6)             -> provided by the committed Maven
#                                         wrapper (mvnw), self-downloading
#
# Maven frameworks (Selenium / TestNG / REST Assured / Jackson / Log4j2 /
# Allure) need no install: Maven resolves the exact versions pinned in pom.xml
# on the first build (requires internet).
#
# USAGE:
#   ./setup-env.sh
#   ./setup-env.sh --skip-install     # validate only; never install, fail on missing
#   ./setup-env.sh --uninstall        # teardown: remove ONLY what this script
#                                     # installed (tracked in reports/.setup-installed.json)
#                                     # plus downloaded Maven/WebDriver caches.
#                                     # reports/ is always preserved.
#
# Idempotent: safe to run multiple times. CI-aware: when run inside GitHub
# Actions it exports JAVA_HOME / PATH via $GITHUB_ENV / $GITHUB_PATH so later
# job steps see the provisioned toolchain. On a local machine JAVA_HOME is
# persisted to ~/.bashrc inside a removable marker block (cleaned up by
# --uninstall).
# ============================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MARKER_PATH="${SCRIPT_DIR}/reports/.setup-installed.json"
# Declared unconditionally so "set -u" never trips on them.
MARKER_COMPONENTS=()
need_install=()

cyan()  { printf '\033[36m%s\033[0m\n' "$*"; }
green() { printf '  [OK] %s\n' "$*"; }
warn()  { printf '  [WARN] %s\n' "$*"; }
fail()  { printf '  [FAIL] %s\n' "$*"; }

SKIP_INSTALL=0
UNINSTALL=0
for arg in "$@"; do
    case "$arg" in
        --skip-install) SKIP_INSTALL=1 ;;
        --uninstall)    UNINSTALL=1 ;;
        --help|-h)
            sed -n '2,30p' "$0"
            exit 0 ;;
        *) fail "Unknown option: $arg"; exit 1 ;;
    esac
done

# --- detection helpers -------------------------------------------------------

java_major() {
    command -v java >/dev/null 2>&1 || return 0
    local v
    v="$(java -version 2>&1 | head -n 1)"
    echo "$v" | sed -n 's/.*"\(1\.\)\?\([0-9][0-9]*\).*/\2/p'
}

java_version_line() {
    command -v java >/dev/null 2>&1 || { echo '(not found on PATH)'; return; }
    java -version 2>&1 | head -n 1
}

chrome_binary() {
    for c in google-chrome google-chrome-stable chromium chromium-browser; do
        if command -v "$c" >/dev/null 2>&1; then
            echo "$c"; return 0
        fi
    done
    for p in "/usr/bin/google-chrome" "/opt/google/chrome/chrome"; do
        if [ -x "$p" ]; then echo "$p"; return 0; fi
    done
    return 1
}

detect_java_home() {
    # Prefer an existing JAVA_HOME with a working java/javac.
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME"; return 0
    fi
    for d in /usr/lib/jvm/*; do
        if [ -x "$d/bin/java" ]; then
            local major
            major="$("$d/bin/java" -version 2>&1 | head -n 1 \
                | sed -n 's/.*"\(1\.\)\?\([0-9][0-9]*\).*/\2/p')"
            if [ "$major" = "8" ]; then echo "$d"; return 0; fi
        fi
    done
    # Fall back to whatever javac/java resolves to (symlink-aware).
    if command -v java >/dev/null 2>&1; then
        local bin
        bin="$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)"
        echo "$(cd "$(dirname "$bin")/.." && pwd)"; return 0
    fi
    return 1
}

have_sudo() {
    [ "$(id -u)" -eq 0 ] && return 0
    command -v sudo >/dev/null 2>&1
}

run_sudo() {
    if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

# --- marker (JSON, same contract as setup-env.ps1) ---------------------------

load_marker() {
    MARKER_COMPONENTS=()
    [ -f "$MARKER_PATH" ] || return 0
    local line
    while IFS= read -r line; do
        if printf '%s' "$line" | grep -q '"name"'; then
            MARKER_COMPONENTS+=("$(printf '%s' "$line" | sed -n 's/.*\({.*}\).*/\1/p')")
        fi
    done < "$MARKER_PATH"
}

save_marker() {
    mkdir -p "$(dirname "$MARKER_PATH")"
    {
        printf '{\n  "components": [\n'
        local n=${#MARKER_COMPONENTS[@]}
        for i in "${!MARKER_COMPONENTS[@]}"; do
            [ "$i" -ge 1 ] && printf ',\n'
            printf '    %s' "${MARKER_COMPONENTS[$i]}"
        done
        printf '\n  ]\n}\n'
    } > "$MARKER_PATH"
}

# --- installers ---------------------------------------------------------------

install_java() {
    # Ensure a full JDK is available: compile needs javac.
    if [ "$(java_major)" -ge 8 ]; then
        local home
        home="$(detect_java_home)" && export JAVA_HOME="$home"
        return 0
    fi
    echo "  Installing Eclipse Temurin JDK 8..."
    local key=/etc/apt/trusted.gpg.d/adoptium.gpg
    if have_sudo \
        && curl -fsSL -o /tmp/adoptium.gpg https://packages.adoptium.net/artifactory/api/gpg/key/public \
        && run_sudo install -o root -g root -m 644 /tmp/adoptium.gpg "$key" \
        && echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
           | run_sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null \
        && run_sudo apt-get update -qq \
        && run_sudo apt-get install -y temurin-8-jdk; then
        local home
        home="$(detect_java_home)" || { fail "Java installed but JAVA_HOME could not be located."; return 1; }
        export JAVA_HOME="$home"
        MARKER_COMPONENTS+=("{\"name\":\"java\",\"manager\":\"apt\",\"packageId\":\"temurin-8-jdk\",\"javaHome\":\"$home\",\"bin\":\"$home/bin\",\"repoFile\":\"/etc/apt/sources.list.d/adoptium.list\",\"aptKey\":\"$key\"}")
        green "JAVA_HOME = $home"
        return 0
    fi
    fail "Failed to install Temurin 8 (needs apt, sudo and internet)."
    return 1
}

install_chrome() {
    if chrome_binary >/dev/null 2>&1; then return 0; fi
    echo "  Installing Google Chrome (stable)..."
    local key=/etc/apt/trusted.gpg.d/google-chrome.gpg
    if have_sudo \
        && curl -fsSL -o /tmp/google-chrome.gpg https://dl.google.com/linux/linux_signing_key.pub \
        && run_sudo install -o root -g root -m 644 /tmp/google-chrome.gpg "$key" \
        && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" \
           | run_sudo tee /etc/apt/sources.list.d/google-chrome.list >/dev/null \
        && run_sudo apt-get update -qq \
        && run_sudo apt-get install -y google-chrome-stable; then
        MARKER_COMPONENTS+=("{\"name\":\"chrome\",\"manager\":\"apt\",\"packageId\":\"google-chrome-stable\",\"repoFile\":\"/etc/apt/sources.list.d/google-chrome.list\",\"aptKey\":\"$key\"}")
        green "Google Chrome installed."
        return 0
    fi
    fail "Failed to install Google Chrome (needs apt, sudo and internet)."
    return 1
}

# --- persistence --------------------------------------------------------------

persist_java_home() {
    # GitHub Actions: make JAVA_HOME/PATH visible to subsequent job steps.
    if [ -n "${GITHUB_ENV:-}" ] && [ -n "${JAVA_HOME:-}" ]; then
        echo "JAVA_HOME=$JAVA_HOME" >> "$GITHUB_ENV"
        echo "$JAVA_HOME/bin" >> "${GITHUB_PATH:-/dev/null}"
        return
    fi
    # Local machine: persistent, marked so --uninstall can clean it up.
    local rc="$HOME/.bashrc" block="# >>> RBA task JAVA_HOME >>>"
    if [ -n "${JAVA_HOME:-}" ] && [ -f "$rc" ] && ! grep -qF "$block" "$rc"; then
        printf '\n%s\nexport JAVA_HOME=%s\nexport PATH="$JAVA_HOME/bin:$PATH"\n# <<< RBA task JAVA_HOME <<<\n' \
            "$block" "$JAVA_HOME" >> "$rc"
        green "JAVA_HOME persisted to $rc (marker block)."
    elif [ -n "${JAVA_HOME:-}" ]; then
        warn "JAVA_HOME=$JAVA_HOME (session only; $rc already contains the marker block)."
    fi
}

remove_persisted_java_home() {
    local rc="$HOME/.bashrc"
    [ -f "$rc" ] || return 0
    sed -i '/# >>> RBA task JAVA_HOME >>>/,/# <<< RBA task JAVA_HOME <<</d' "$rc"
}

# --- teardown ----------------------------------------------------------------

do_uninstall() {
    cyan "Teardown - removing the environment created by setup-env.sh"
    load_marker
    if [ "${#MARKER_COMPONENTS[@]}" -eq 0 ]; then
        green "Nothing was installed by setup-env.sh (clean baseline) - nothing to remove."
        return 0
    fi
    local -A seen=()
    for c in "${MARKER_COMPONENTS[@]}"; do
        local pkg repo key
        pkg="$(echo "$c" | sed -n 's/.*"packageId": "\([^"]*\)".*/\1/p')"
        repo="$(echo "$c" | sed -n 's/.*"repoFile": "\([^"]*\)".*/\1/p')"
        key="$(echo "$c" | sed -n 's/.*"aptKey": "\([^"]*\)".*/\1/p')"
        if [ -n "$pkg" ] && ! [ "${seen[$pkg]:-}" ]; then
            seen[$pkg]=1
            if have_sudo; then
                run_sudo apt-get purge -y "$pkg" >/dev/null 2>&1 \
                    && green "Removed $pkg." \
                    || warn "apt could not fully purge $pkg."
            fi
        fi
        [ -n "$repo" ] && [ -f "$repo" ] && run_sudo rm -f "$repo" && green "Removed $repo"
        [ -n "$key" ] && [ -f "$key" ] && run_sudo rm -f "$key" && green "Removed $key"
    done
    for cache in "$HOME/.m2/wrapper" "$HOME/.m2/repository" "$HOME/.cache/selenium"; do
        if [ -d "$cache" ]; then
            rm -rf "$cache" && green "Removed cache: $cache"
        fi
    done
    remove_persisted_java_home
    rm -f "$MARKER_PATH"
    green "Environment restored to clean state; reports/ left untouched."
}

# --- main ---------------------------------------------------------------------

if [ "$UNINSTALL" -eq 1 ]; then
    do_uninstall
    exit 0
fi

cyan "==================================================="
cyan " RBA QA Automation - Environment setup (Linux)"
cyan "==================================================="

need_install=()
if [ "$(java_major)" -lt 8 ]; then need_install+=(java); fi
if ! chrome_binary >/dev/null 2>&1; then need_install+=(chrome); fi

if [ ${#need_install[@]} -gt 0 ] && [ "$SKIP_INSTALL" -eq 1 ]; then
    fail "--skip-install given: missing required components: ${need_install[*]}"
    exit 1
fi

installed_any=0
if [ "${#need_install[@]}" -gt 0 ]; then
    for component in "${need_install[@]}"; do
        echo "  -> $component"
        case "$component" in
            java)   install_java || exit 1 ;;
            chrome) install_chrome || exit 1 ;;
        esac
        installed_any=1
    done
    save_marker
fi

# --- 1. Java ------------------------------------------------------------------
cyan "Checking Java"
if [ "$(java_major)" -ge 8 ]; then
    if java_home="$(detect_java_home)"; then export JAVA_HOME="$java_home"; fi
    green "Java found: $(java_version_line)   (JAVA_HOME = ${JAVA_HOME:-not set})"
else
    fail "Java 8+ is required but unavailable (found: $(java_version_line))."
    fail "Re-run without --skip-install to install it automatically."
    exit 1
fi

# --- 2. Maven wrapper ----------------------------------------------------------
cyan "Checking Maven wrapper"
if [ ! -f "$SCRIPT_DIR/mvnw" ] || [ ! -f "$SCRIPT_DIR/.mvn/wrapper/maven-wrapper.properties" ]; then
    fail "Maven wrapper files not found. Run from the project root."
    exit 1
fi
mvn_out="$(cd "$SCRIPT_DIR" && ./mvnw -v 2>&1)"
mvn_line="$(printf '%s\n' "$mvn_out" | grep 'Apache Maven' | head -n 1)"
if [ -n "$mvn_line" ]; then
    green "Maven wrapper works: $(printf '%s\n' "$mvn_line" | tr -s ' ' | sed 's/^ *//')"
else
    fail "Maven wrapper could not fetch the pinned Maven."
    fail "Check internet connectivity, then re-run."
    exit 1
fi

# --- 3. Google Chrome ----------------------------------------------------------
cyan "Checking Google Chrome"
if cb="$(chrome_binary)"; then
    green "Chrome found: $cb"
    green "ChromeDriver is resolved automatically by WebDriverManager at test runtime."
else
    fail "Google Chrome not found."
    fail "Re-run without --skip-install to install it automatically."
    exit 1
fi

# --- 4. Configuration & connectivity ------------------------------------------
cyan "Validating configuration"
if [ -f "$SCRIPT_DIR/config/test-config.properties" ]; then
    green "Configuration present: $SCRIPT_DIR/config/test-config.properties"
else
    warn "config/test-config.properties not found; will fall back to classpath default."
fi

cyan "Connectivity sanity check (optional)"
if curl -fsSI --max-time 15 https://www.rba.hr >/dev/null 2>&1; then
    green "rba.hr reachable"
else
    warn "rba.hr not reachable right now; tests hitting the live site will fail."
fi

persist_java_home

# --- 5. Toolchain summary -------------------------------------------------------
cyan "Toolchain summary"
echo "  Java             : $(java_version_line)"
echo "  Maven            : $(printf '%s\n' "$mvn_line" | tr -s ' ' | sed 's/^ *//')  (pinned by Maven wrapper)"
echo "  Chrome           : $cb (WebDriverManager picks the matching driver)"
echo "  (Framework versions are fixed in pom.xml and resolve on the first Maven build.)"

printf '\n\033[36m===================================================\033[0m\n'
printf '\033[36m Environment ready. Next: ./mvnw -B -Dsuite.file=testng.xml test\033[0m\n'
printf '\033[36m API + unit only: ./mvnw -B -Dsuite.file=verify-unit-api.xml test\033[0m\n'
printf '\033[36m===================================================\033[0m\n'

exit 0
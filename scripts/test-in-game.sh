#!/usr/bin/env bash
# End-to-end local-test setup for the Minecraft Fabric MCP Server mod.
#
# Builds the mod jar for the requested Minecraft target (or reuses an existing
# build), downloads + installs Fabric Loader and Fabric API into an isolated
# game directory, registers a Minecraft Launcher profile, and (with --launch)
# opens the launcher with that profile pre-selected.
#
# Designed to be re-run safely -- caches the Fabric installer + Fabric API
# jar under scripts/.cache/ and reuses them on subsequent runs.
#
# Requirements: bash 4+, curl, jq, java (any JRE 17+ that can run the Fabric
# installer jar -- the build itself uses the JDK 21 / 25 toolchain Gradle picks).

set -euo pipefail

# --- usage ------------------------------------------------------------------

usage() {
    cat <<EOF
Usage: $(basename "$0") -v VERSION [options]

Required:
  -v, --version VERSION    Minecraft target (1.21.11 | 26.1.1 | 26.1.2)

Options:
  --mc-root PATH           User's existing Minecraft root (default: OS-specific)
  --game-dir PATH          Isolated per-test game directory (default: alongside
                           --mc-root with an -mcp-<version> suffix)
  --loader VERSION         Override Fabric Loader version (default: latest
                           compatible from meta.fabricmc.net)
  --skip-build             Skip the Gradle build step
  --launch                 Open the Minecraft Launcher after setup
  -h, --help               Show this help

Examples:
  $(basename "$0") -v 1.21.11
  $(basename "$0") -v 26.1.1 --skip-build --launch

OS-specific defaults for --mc-root:
  macOS:   ~/Library/Application Support/minecraft
  Linux:   ~/.minecraft
EOF
}

# --- argument parsing -------------------------------------------------------

VERSION=""
MC_ROOT=""
GAME_DIR=""
LOADER_VERSION=""
SKIP_BUILD=0
LAUNCH=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--version)
            VERSION="$2"; shift 2 ;;
        --mc-root)
            MC_ROOT="$2"; shift 2 ;;
        --game-dir)
            GAME_DIR="$2"; shift 2 ;;
        --loader)
            LOADER_VERSION="$2"; shift 2 ;;
        --skip-build)
            SKIP_BUILD=1; shift ;;
        --launch)
            LAUNCH=1; shift ;;
        -h|--help)
            usage; exit 0 ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 2 ;;
    esac
done

if [[ -z "$VERSION" ]]; then
    echo "Error: --version is required" >&2
    usage
    exit 2
fi

case "$VERSION" in
    1.21.11|26.1.1|26.1.2) ;;
    *)
        echo "Error: unsupported version '$VERSION'. Must be one of: 1.21.11, 26.1.1, 26.1.2." >&2
        exit 2 ;;
esac

# --- platform detection -----------------------------------------------------

OS="$(uname -s)"
case "$OS" in
    Darwin)
        DEFAULT_MC_ROOT="$HOME/Library/Application Support/minecraft"
        DEFAULT_GAME_PARENT="$HOME/Library/Application Support"
        ;;
    Linux)
        DEFAULT_MC_ROOT="$HOME/.minecraft"
        DEFAULT_GAME_PARENT="$HOME"
        ;;
    *)
        echo "Error: unsupported OS '$OS'. This script targets macOS + Linux; Windows users should run scripts/test-in-game.ps1 instead." >&2
        exit 2 ;;
esac

MC_ROOT="${MC_ROOT:-$DEFAULT_MC_ROOT}"
GAME_DIR="${GAME_DIR:-$DEFAULT_GAME_PARENT/minecraft-mcp-$VERSION}"

# --- resolve script + repo roots --------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CACHE_DIR="$SCRIPT_DIR/.cache"

# --- presentation -----------------------------------------------------------

# Colors if attached to a TTY. Plain text otherwise.
if [[ -t 1 ]]; then
    BOLD=$'\033[1m'; CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RESET=$'\033[0m'
else
    BOLD=""; CYAN=""; GREEN=""; YELLOW=""; RESET=""
fi

step()  { printf '\n%s==>%s %s\n' "$CYAN" "$RESET" "$1"; }
info()  { printf '    %s\n' "$1"; }
ok()    { printf '    %sOK%s: %s\n' "$GREEN" "$RESET" "$1"; }
warn()  { printf '    %sWARN%s: %s\n' "$YELLOW" "$RESET" "$1"; }

# --- dependency checks ------------------------------------------------------

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Error: required command '$1' is not on PATH." >&2
        if [[ "$1" == "jq" ]]; then
            case "$OS" in
                Darwin) echo "       Install with: brew install jq" >&2 ;;
                Linux)  echo "       Install with: sudo apt-get install jq  (Debian/Ubuntu)" >&2
                        echo "                  or: sudo dnf install jq      (Fedora)" >&2 ;;
            esac
        fi
        exit 1
    fi
}

require_cmd curl
require_cmd jq

# --- helpers ----------------------------------------------------------------

find_java() {
    # Toolchain JDK is set by Gradle internally. Here we just need any JRE that
    # can run a JAR (Fabric installer). Prefer 21/25 if available so the user
    # doesn't accidentally end up on an older JRE.
    local candidates=()
    [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME/bin/java")
    [[ -n "${JAVA_HOME_21_X64:-}" ]] && candidates+=("$JAVA_HOME_21_X64/bin/java")
    [[ -n "${JAVA_HOME_25_X64:-}" ]] && candidates+=("$JAVA_HOME_25_X64/bin/java")
    if [[ "$OS" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
        for v in 25 21 17; do
            local home
            home=$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)
            [[ -n "$home" ]] && candidates+=("$home/bin/java")
        done
    fi
    if [[ "$OS" == "Linux" ]]; then
        for d in /usr/lib/jvm/temurin-25* /usr/lib/jvm/temurin-21* /usr/lib/jvm/java-25* /usr/lib/jvm/java-21*; do
            [[ -x "$d/bin/java" ]] && candidates+=("$d/bin/java")
        done
    fi
    candidates+=("$(command -v java 2>/dev/null || true)")
    for c in "${candidates[@]}"; do
        if [[ -n "$c" && -x "$c" ]]; then
            echo "$c"
            return 0
        fi
    done
    echo "Error: couldn't locate a Java runtime. Install Temurin/Corretto 21+ or set JAVA_HOME." >&2
    exit 1
}

find_jdk_home() {
    # Locate the install root of a specific JDK major version. Echoes the path
    # if found, empty otherwise. Used by set_gradle_toolchain_env so the
    # toolchain locator declared in gradle.properties
    # (org.gradle.java.installations.fromEnv = JDK_21,JDK_25,...) actually
    # resolves to something on the user's machine.
    local major="$1"
    # 1) Already in env? Trust it.
    local v
    for v in "JDK_$major" "JAVA_HOME_${major}_X64"; do
        local val="${!v:-}"
        if [[ -n "$val" && -x "$val/bin/java" ]]; then
            echo "$val"; return
        fi
    done
    # 2) Probe common locations.
    if [[ "$OS" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local home
        home=$(/usr/libexec/java_home -v "$major" 2>/dev/null || true)
        if [[ -n "$home" && -x "$home/bin/java" ]]; then
            echo "$home"; return
        fi
    fi
    if [[ "$OS" == "Linux" ]]; then
        for d in /usr/lib/jvm/temurin-${major}* /usr/lib/jvm/java-${major}* /usr/lib/jvm/java-1.${major}* /usr/lib/jvm/adoptium-${major}* /usr/lib/jvm/zulu${major}*; do
            if [[ -x "$d/bin/java" ]]; then
                echo "$d"; return
            fi
        done
    fi
    echo ""
}

set_gradle_toolchain_env() {
    # Populate the env vars Gradle's toolchain locator reads (per
    # gradle.properties:org.gradle.java.installations.fromEnv). Without these,
    # the compile step fails with "no toolchain matching languageVersion=21" --
    # a confusing error that's actually just a missing-env-var issue.
    local jdk21 jdk25
    jdk21=$(find_jdk_home 21)
    jdk25=$(find_jdk_home 25)
    if [[ -n "$jdk21" ]]; then
        export JDK_21="$jdk21"
        export JAVA_HOME_21_X64="$jdk21"
        info "JDK 21: $jdk21"
    else
        info "JDK 21: not found (Gradle will reject if a 1.21.x target needs it)"
    fi
    if [[ -n "$jdk25" ]]; then
        export JDK_25="$jdk25"
        export JAVA_HOME_25_X64="$jdk25"
        info "JDK 25: $jdk25"
    else
        info "JDK 25: not found (Gradle will reject if a 26.1.x target needs it)"
    fi
}

resolve_loader_version() {
    local mc="$1"
    if [[ -n "$LOADER_VERSION" ]]; then
        echo "$LOADER_VERSION"
        return
    fi
    local url="https://meta.fabricmc.net/v2/versions/loader/$mc"
    # Pick stable=true first, fall back to newest.
    local v
    v=$(curl -fsSL "$url" \
        | jq -r '[.[] | select(.loader.stable==true)] | .[0].loader.version // (.[0].loader.version)')
    if [[ -z "$v" || "$v" == "null" ]]; then
        v=$(curl -fsSL "$url" | jq -r '.[0].loader.version')
    fi
    if [[ -z "$v" || "$v" == "null" ]]; then
        echo "Error: Fabric meta returned no loader entries for $mc" >&2
        exit 1
    fi
    echo "$v"
}

resolve_installer_version() {
    local v
    v=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer \
        | jq -r '[.[] | select(.stable==true)] | .[0].version // (.[0].version)')
    if [[ -z "$v" || "$v" == "null" ]]; then
        v=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | jq -r '.[0].version')
    fi
    echo "$v"
}

cache_fabric_installer() {
    local installer_ver="$1"
    mkdir -p "$CACHE_DIR"
    local jar="$CACHE_DIR/fabric-installer-$installer_ver.jar"
    if [[ -f "$jar" ]]; then
        info "fabric-installer-$installer_ver.jar (cached)"
        echo "$jar"
        return
    fi
    local url="https://maven.fabricmc.net/net/fabricmc/fabric-installer/$installer_ver/fabric-installer-$installer_ver.jar"
    info "downloading $url"
    curl -fsSL "$url" -o "$jar"
    echo "$jar"
}

cache_fabric_api() {
    local mc="$1"
    mkdir -p "$CACHE_DIR"
    info "querying Modrinth for Fabric API matching $mc"
    # Modrinth expects URL-encoded JSON arrays in query params.
    local url="https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${mc}%22%5D&loaders=%5B%22fabric%22%5D"
    local json
    json=$(curl -fsSL -H "User-Agent: minecraft-fabric-mcp-server/test-script" "$url")
    local file_url file_name
    file_url=$(echo "$json" | jq -r '.[0].files[] | select(.primary==true) | .url' | head -1)
    file_name=$(echo "$json" | jq -r '.[0].files[] | select(.primary==true) | .filename' | head -1)
    if [[ -z "$file_url" || "$file_url" == "null" ]]; then
        # Fall back to the first non-primary file (some projects don't mark a primary).
        file_url=$(echo "$json" | jq -r '.[0].files[0].url')
        file_name=$(echo "$json" | jq -r '.[0].files[0].filename')
    fi
    if [[ -z "$file_url" || "$file_url" == "null" ]]; then
        echo "Error: Modrinth returned no Fabric API release for $mc" >&2
        exit 1
    fi
    local cached="$CACHE_DIR/$file_name"
    if [[ -f "$cached" ]]; then
        info "$file_name (cached)"
        echo "$cached"
        return
    fi
    info "downloading $file_url"
    curl -fsSL "$file_url" -o "$cached"
    echo "$cached"
}

invoke_fabric_installer() {
    local java="$1" installer="$2" mc="$3" loader="$4" dir="$5"
    info "running fabric-installer (mc=$mc loader=$loader dir=$dir)"
    # `client` subcommand installs into <dir>/versions/fabric-loader-<loader>-<mc>/
    # `-noprofile` skips the default profile so we can register our own pointing
    # at the isolated gameDir.
    "$java" -jar "$installer" client \
        -mcversion "$mc" \
        -loader "$loader" \
        -dir "$dir" \
        -noprofile 2>&1 | sed 's/^/    | /'
    local expected="$dir/versions/fabric-loader-$loader-$mc"
    if [[ ! -d "$expected" ]]; then
        echo "Error: fabric-installer claimed success but $expected does not exist." >&2
        exit 1
    fi
    ok "Fabric Loader installed at $expected"
    echo "fabric-loader-$loader-$mc"
}

sync_launcher_profile() {
    local profiles_path="$1" name="$2" version_id="$3" game_dir="$4"
    local profile_key="mcp-test-$VERSION"

    if [[ ! -f "$profiles_path" ]]; then
        info "launcher_profiles.json not found at $profiles_path; creating a minimal one"
        mkdir -p "$(dirname "$profiles_path")"
        printf '{"profiles":{},"settings":{"enableSnapshots":false,"keepLauncherOpen":true},"version":3}' \
            > "$profiles_path"
    fi
    local now
    now=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
    # Preserve existing `created` if the profile exists.
    local created
    created=$(jq -r --arg k "$profile_key" '.profiles[$k].created // ""' "$profiles_path")
    [[ -z "$created" ]] && created="$now"

    local tmp="$profiles_path.tmp"
    jq --arg key "$profile_key" \
       --arg name "$name" \
       --arg versionId "$version_id" \
       --arg gameDir "$game_dir" \
       --arg created "$created" \
       --arg lastUsed "$now" \
       '.profiles[$key] = {
             name: $name,
             type: "custom",
             lastVersionId: $versionId,
             gameDir: $gameDir,
             javaArgs: "-Xmx2G -XX:+UseG1GC",
             created: $created,
             lastUsed: $lastUsed,
             icon: "Furnace_On"
         }' \
       "$profiles_path" > "$tmp"
    mv "$tmp" "$profiles_path"
    info "registered launcher profile '$profile_key'"
    echo "$profile_key"
}

find_launcher() {
    case "$OS" in
        Darwin)
            if [[ -d "/Applications/Minecraft.app" ]]; then
                echo "/Applications/Minecraft.app"
                return
            fi
            ;;
        Linux)
            for c in \
                "$(command -v minecraft-launcher 2>/dev/null || true)" \
                /usr/bin/minecraft-launcher \
                /opt/minecraft-launcher/minecraft-launcher \
                "$HOME/.local/bin/minecraft-launcher"; do
                [[ -x "$c" ]] && { echo "$c"; return; }
            done
            ;;
    esac
    echo ""
}

# --- main -------------------------------------------------------------------

printf '\n%s================================================================%s\n' "$CYAN" "$RESET"
printf '%s Minecraft Fabric MCP Server -- local test setup%s\n' "$BOLD" "$RESET"
printf '%s Minecraft target: %s%s\n' "$CYAN" "$VERSION" "$RESET"
printf '%s Game dir:         %s%s\n' "$CYAN" "$GAME_DIR" "$RESET"
printf '%s================================================================%s\n' "$CYAN" "$RESET"

# --- 1) Build the mod jar ---------------------------------------------------

MOD_JAR="$REPO_ROOT/versions/$VERSION/build/libs/minecraft-fabric-mcp-0.1.0+$VERSION.jar"
if [[ "$SKIP_BUILD" -eq 1 ]]; then
    step "Skipping mod build (--skip-build)"
    if [[ ! -f "$MOD_JAR" ]]; then
        echo "Error: --skip-build requested but $MOD_JAR is missing. Run without --skip-build first." >&2
        exit 1
    fi
    info "Using existing $MOD_JAR"
else
    step "Building mod jar for $VERSION"
    set_gradle_toolchain_env
    # Stream Gradle output straight through so compile / toolchain errors are
    # visible in real time. The earlier `2>&1 | sed` indirection swallowed
    # them and made "no toolchain matching languageVersion=21" look like a
    # generic build failure.
    (cd "$REPO_ROOT" && ./gradlew ":${VERSION}:build" --stacktrace)
    if [[ ! -f "$MOD_JAR" ]]; then
        echo "Error: Gradle reported success but $MOD_JAR is missing." >&2
        exit 1
    fi
    ok "Mod jar: $MOD_JAR"
fi

# --- 2) Resolve Fabric versions ---------------------------------------------

step "Resolving Fabric versions"
LOADER=$(resolve_loader_version "$VERSION")
info "Loader:    $LOADER"
INSTALLER_VER=$(resolve_installer_version)
info "Installer: $INSTALLER_VER"

JAVA_BIN=$(find_java)
info "Java:      $JAVA_BIN"

# --- 3) Cache + install Fabric Loader ---------------------------------------

step "Preparing isolated game dir $GAME_DIR"
mkdir -p "$GAME_DIR/mods"
info "mods/ at $GAME_DIR/mods"

step "Caching fabric-installer-$INSTALLER_VER.jar"
INSTALLER_JAR=$(cache_fabric_installer "$INSTALLER_VER")

step "Installing Fabric Loader for Minecraft $VERSION into $MC_ROOT"
# IMPORTANT: install the version under the launcher's WORK directory ($MC_ROOT),
# not the per-profile $GAME_DIR. The Minecraft Launcher resolves every profile's
# `lastVersionId` against the work dir's versions/ folder; a version dropped
# only under a gameDir gets filtered out as "unplayable" and the profile
# silently disappears from the dropdown. The gameDir is only for worlds / mods
# / options / saves per profile.
VERSION_ID=$(invoke_fabric_installer "$JAVA_BIN" "$INSTALLER_JAR" "$VERSION" "$LOADER" "$MC_ROOT")

# --- 4) Cache + copy Fabric API ---------------------------------------------

step "Downloading Fabric API for $VERSION"
FABRIC_API=$(cache_fabric_api "$VERSION")
cp -f "$FABRIC_API" "$GAME_DIR/mods/$(basename "$FABRIC_API")"
ok "Fabric API: $GAME_DIR/mods/$(basename "$FABRIC_API")"

# --- 5) Copy the locally-built mod jar --------------------------------------

step "Copying MCP mod jar into mods/"
cp -f "$MOD_JAR" "$GAME_DIR/mods/$(basename "$MOD_JAR")"
ok "Mod jar:    $GAME_DIR/mods/$(basename "$MOD_JAR")"

# Drop a tiny README in the isolated dir so future-you knows what this is.
cat > "$GAME_DIR/WHAT_IS_THIS.txt" <<EOF
This game directory was created by scripts/test-in-game.sh in the
minecraft-java-fabric-mcp-server repo. It exists so MCP-mod testing
doesn't touch your default Minecraft worlds or mod list.

Minecraft version: $VERSION
Fabric Loader:     $LOADER
Mods directory:    ./mods/

To clean up, delete this directory and remove the 'mcp-test-$VERSION'
profile from $MC_ROOT/launcher_profiles.json.

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

# --- 6) Register launcher profile -------------------------------------------

step "Registering Minecraft Launcher profile"
PROFILES_PATH="$MC_ROOT/launcher_profiles.json"
PROFILE_NAME="MCP Test $VERSION"
PROFILE_KEY=$(sync_launcher_profile "$PROFILES_PATH" "$PROFILE_NAME" "$VERSION_ID" "$GAME_DIR")
ok "Profile '$PROFILE_NAME' (key: $PROFILE_KEY)"

# --- 7) Launch or print next steps ------------------------------------------

printf '\n%s================================================================%s\n' "$GREEN" "$RESET"
printf '%s Setup complete.%s\n' "$GREEN$BOLD" "$RESET"
printf '%s================================================================%s\n\n' "$GREEN" "$RESET"

echo "  Profile:   $PROFILE_NAME"
echo "  Game dir:  $GAME_DIR"
echo "  Mods:"
for jar in "$GAME_DIR"/mods/*.jar; do
    [[ -e "$jar" ]] && echo "    - $(basename "$jar")"
done
echo
echo "Next steps:"
echo "  1. Open Minecraft Launcher."
echo "  2. Pick the profile '$PROFILE_NAME' from the dropdown (top-left)."
echo "  3. Click Play. The integrated server boots on world load -- that's"
echo "     when the MCP HTTP listener binds (default http://127.0.0.1:8765)."
echo "  4. Verify with: curl http://127.0.0.1:8765/healthz"
echo

if [[ "$LAUNCH" -eq 1 ]]; then
    step "Opening Minecraft Launcher"
    LAUNCHER=$(find_launcher)
    case "$OS" in
        Darwin)
            if [[ -n "$LAUNCHER" ]]; then
                info "$LAUNCHER"
                open "$LAUNCHER" &
            else
                warn "Couldn't find /Applications/Minecraft.app; using URL handler"
                open minecraft:// 2>/dev/null || true
            fi
            ;;
        Linux)
            if [[ -n "$LAUNCHER" ]]; then
                info "$LAUNCHER"
                ( "$LAUNCHER" >/dev/null 2>&1 & )
            else
                warn "Couldn't auto-locate the Minecraft Launcher on this system."
                warn "Open it manually and pick the '$PROFILE_NAME' profile."
            fi
            ;;
    esac
fi

#Requires -Version 5.1
<#
.SYNOPSIS
    End-to-end local-test setup for the Minecraft Fabric MCP Server mod.

.DESCRIPTION
    Builds the mod jar for the requested Minecraft target (or reuses an existing
    build), downloads Fabric Loader + Fabric API into a cache, sets up an
    isolated .minecraft game directory so the test doesn't touch the user's
    existing worlds/mods, registers a launcher profile, and (with -Launch)
    opens the Minecraft Launcher with that profile preselected.

    Designed to be re-run safely -- second run with the same -Version reuses
    the cached Fabric installer + Fabric API jar and only rebuilds the mod
    if Gradle says it's out of date.

.PARAMETER Version
    Which Minecraft target to set up. One of 1.21.11, 26.1.1, 26.1.2.

.PARAMETER MinecraftRoot
    The user's default Minecraft directory. Used to locate the launcher and
    its launcher_profiles.json. Defaults to %APPDATA%\.minecraft.

.PARAMETER GameDir
    The isolated per-test game directory. Defaults to
    %APPDATA%\.minecraft-mcp-<Version>. This is where mods and worlds live.

.PARAMETER LoaderVersion
    Explicit Fabric Loader version. Default: latest compatible build from
    Fabric's meta API.

.PARAMETER SkipBuild
    Skip the Gradle build step. Useful when iterating on Fabric setup itself.

.PARAMETER Launch
    Open the Minecraft Launcher after setup. The user still has to click Play.

.EXAMPLE
    .\test-in-game.ps1 -Version 1.21.11

.EXAMPLE
    .\test-in-game.ps1 -Version 26.1.1 -Launch
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('1.21.11', '26.1.1', '26.1.2')]
    [string]$Version,

    [string]$MinecraftRoot = (Join-Path $env:APPDATA '.minecraft'),

    [string]$GameDir = (Join-Path $env:APPDATA ".minecraft-mcp-$Version"),

    [string]$LoaderVersion,

    [switch]$SkipBuild,

    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Resolve script + repo roots regardless of where the user invokes the script from.
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$CacheDir = Join-Path $ScriptDir '.cache'

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Info {
    param([string]$Message)
    Write-Host "    $Message"
}

function Write-Ok {
    param([string]$Message)
    Write-Host "    OK: $Message" -ForegroundColor Green
}

function Ensure-Dir {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Find-Java {
    # Toolchain JDK: 1.21.x -> JDK 21, 26.1.x -> JDK 25. We only need a working
    # JRE to RUN the Fabric installer jar -- the heavy compilation toolchain
    # lookup happens inside Gradle separately (see Set-GradleToolchainEnv).
    $candidates = @(
        $env:JAVA_HOME,
        $env:JAVA_HOME_21_X64,
        $env:JAVA_HOME_25_X64,
        'C:\Program Files\Amazon Corretto\jdk21.0.10_7',
        'C:\Program Files\Amazon Corretto\jdk25.0.3_9'
    ) | Where-Object { $_ } | Select-Object -Unique

    # NOTE: avoid $home as a loop variable -- it shadows the built-in read-only
    # $HOME automatic variable in PowerShell and fails with
    # "Cannot overwrite variable HOME because it is read-only or constant".
    foreach ($jdkHome in $candidates) {
        $javaExe = Join-Path $jdkHome 'bin\java.exe'
        if (Test-Path -LiteralPath $javaExe) {
            return $javaExe
        }
    }
    # Fall back to PATH.
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Couldn't locate a Java runtime. Set JAVA_HOME or install Corretto 21/25."
}

function Find-JdkHome {
    # Locate the install root of a specific JDK major version. Returns $null
    # if not found. Used to set the env vars Gradle's toolchain locator reads
    # (gradle.properties declares org.gradle.java.installations.fromEnv =
    # JDK_21,JDK_25,JAVA_HOME_21_X64,JAVA_HOME_25_X64). Without these, Gradle
    # auto-detection on Windows usually misses Corretto and compileJava fails
    # with "No Java installations matching: { languageVersion=21 }".
    param([int]$Major)

    # 1) Already in env? Trust the user.
    $envName1 = "JDK_$Major"
    $envName2 = "JAVA_HOME_${Major}_X64"
    foreach ($name in @($envName1, $envName2)) {
        $value = [System.Environment]::GetEnvironmentVariable($name)
        if ($value -and (Test-Path -LiteralPath (Join-Path $value 'bin\java.exe'))) {
            return $value
        }
    }

    # 2) Probe common install locations on Windows.
    $patterns = @(
        "$env:ProgramFiles\Amazon Corretto\jdk$Major*",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-$Major*",
        "$env:ProgramFiles\Microsoft\jdk-$Major*",
        "$env:ProgramFiles\Java\jdk-$Major*"
    )
    foreach ($pat in $patterns) {
        $hits = Get-ChildItem -Path $pat -ErrorAction SilentlyContinue -Directory | Sort-Object Name -Descending
        foreach ($hit in $hits) {
            if (Test-Path -LiteralPath (Join-Path $hit.FullName 'bin\java.exe')) {
                return $hit.FullName
            }
        }
    }
    return $null
}

function Set-GradleToolchainEnv {
    # Populate the env vars Gradle's toolchain locator reads (per
    # gradle.properties:org.gradle.java.installations.fromEnv). Skips an entry
    # if no matching JDK is installed; Gradle will then error out with a
    # readable "no toolchain matching language version N" instead of failing
    # somewhere deeper.
    $jdk21 = Find-JdkHome -Major 21
    $jdk25 = Find-JdkHome -Major 25
    if ($jdk21) {
        $env:JDK_21 = $jdk21
        $env:JAVA_HOME_21_X64 = $jdk21
        Write-Info "JDK 21: $jdk21"
    } else {
        Write-Info "JDK 21: not found (Gradle will reject if a 1.21.x target needs it)"
    }
    if ($jdk25) {
        $env:JDK_25 = $jdk25
        $env:JAVA_HOME_25_X64 = $jdk25
        Write-Info "JDK 25: $jdk25"
    } else {
        Write-Info "JDK 25: not found (Gradle will reject if a 26.1.x target needs it)"
    }
}

function Get-LatestStable {
    param(
        [Parameter(Mandatory)] [string]$Url,
        [Parameter(Mandatory)] [string]$Label
    )
    # Fabric meta endpoints return arrays ordered newest-first.
    try {
        $resp = Invoke-RestMethod -Uri $Url -UseBasicParsing
    } catch {
        throw "Failed to query Fabric meta for $Label at $Url : $_"
    }
    if (-not $resp -or $resp.Count -eq 0) {
        throw "Fabric meta returned no $Label entries from $Url"
    }
    $stable = $resp | Where-Object { $_.stable -eq $true } | Select-Object -First 1
    if ($stable) { return $stable }
    return $resp[0]  # fall back to newest, even if labelled unstable
}

function Resolve-LoaderVersion {
    param([string]$McVersion)
    if ($LoaderVersion) { return $LoaderVersion }
    $url = "https://meta.fabricmc.net/v2/versions/loader/$McVersion"
    $latest = Get-LatestStable -Url $url -Label "loader for $McVersion"
    return $latest.loader.version
}

function Resolve-InstallerVersion {
    $url = 'https://meta.fabricmc.net/v2/versions/installer'
    return (Get-LatestStable -Url $url -Label 'installer').version
}

function Ensure-FabricInstaller {
    param([string]$InstallerVersion)
    Ensure-Dir $CacheDir
    $jar = Join-Path $CacheDir "fabric-installer-$InstallerVersion.jar"
    if (Test-Path -LiteralPath $jar) {
        Write-Info "fabric-installer-$InstallerVersion.jar (cached)"
        return $jar
    }
    $url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/$InstallerVersion/fabric-installer-$InstallerVersion.jar"
    Write-Info "downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing
    return $jar
}

function Ensure-FabricApi {
    param([string]$McVersion)
    Ensure-Dir $CacheDir
    # Modrinth project slug for Fabric API.
    $url = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$McVersion%22%5D&loaders=%5B%22fabric%22%5D"
    Write-Info "querying Modrinth for Fabric API matching $McVersion"
    $versions = Invoke-RestMethod -Uri $url -UseBasicParsing -Headers @{ 'User-Agent' = 'minecraft-fabric-mcp-server/test-script' }
    if (-not $versions -or $versions.Count -eq 0) {
        throw "Modrinth returned no Fabric API release for $McVersion. Check that this MC version has a Fabric API yet (try a release/beta if it's a snapshot)."
    }
    # Pick the newest version. Modrinth orders newest-first.
    $picked = $versions[0]
    $file = $picked.files | Where-Object { $_.primary -eq $true } | Select-Object -First 1
    if (-not $file) { $file = $picked.files[0] }
    $cached = Join-Path $CacheDir $file.filename
    if (Test-Path -LiteralPath $cached) {
        Write-Info "$($file.filename) (cached)"
        return $cached
    }
    Write-Info "downloading $($file.url)"
    Invoke-WebRequest -Uri $file.url -OutFile $cached -UseBasicParsing
    return $cached
}

function Invoke-FabricInstaller {
    param(
        [string]$Java,
        [string]$InstallerJar,
        [string]$McVersion,
        [string]$Loader,
        [string]$Dir
    )
    Write-Info "running fabric-installer (mc=$McVersion loader=$Loader dir=$Dir)"
    # `client` subcommand installs into <dir>/versions/fabric-loader-<loader>-<mc>/
    # `-noprofile` skips creating a launcher profile -- we register our own profile
    #              afterwards so we can point gameDir at the isolated install.
    $args = @(
        '-jar', $InstallerJar,
        'client',
        '-mcversion', $McVersion,
        '-loader', $Loader,
        '-dir', $Dir,
        '-noprofile'
    )
    & $Java @args 2>&1 | ForEach-Object {
        # Pipe installer output through with indent so it's clearly nested.
        Write-Host "    | $_"
    }
    if ($LASTEXITCODE -ne 0) {
        throw "fabric-installer exited with code $LASTEXITCODE"
    }
    $expectedDir = Join-Path $Dir "versions\fabric-loader-$Loader-$McVersion"
    if (-not (Test-Path -LiteralPath $expectedDir)) {
        throw "fabric-installer claimed success but $expectedDir does not exist. Inspect installer output above."
    }
    Write-Ok "Fabric Loader installed at $expectedDir"
    return "fabric-loader-$Loader-$McVersion"
}

function Sync-LauncherProfile {
    param(
        [string]$ProfilesPath,
        [string]$Name,
        [string]$VersionId,
        [string]$GameDir
    )
    if (-not (Test-Path -LiteralPath $ProfilesPath)) {
        Write-Info "launcher_profiles.json not found at $ProfilesPath; creating a minimal one"
        $minimal = @{
            profiles = @{}
            settings = @{ enableSnapshots = $false; keepLauncherOpen = $true }
            version  = 3
        }
        $minimal | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ProfilesPath -Encoding UTF8
    }
    $json = Get-Content -LiteralPath $ProfilesPath -Raw | ConvertFrom-Json
    if (-not $json.profiles) {
        $json | Add-Member -NotePropertyName profiles -NotePropertyValue (New-Object PSObject) -Force
    }
    $now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.000Z")
    $profileKey = "mcp-test-$Version"
    $profile = [PSCustomObject]@{
        name           = $Name
        type           = 'custom'
        lastVersionId  = $VersionId
        gameDir        = $GameDir
        javaArgs       = '-Xmx2G -XX:+UseG1GC'
        created        = $now
        lastUsed       = $now
        icon           = 'Furnace_On'
    }
    if ($json.profiles.PSObject.Properties[$profileKey]) {
        # Preserve `created` from existing profile if present.
        $existing = $json.profiles.$profileKey
        if ($existing.created) { $profile.created = $existing.created }
        $json.profiles.$profileKey = $profile
        Write-Info "updated launcher profile '$profileKey'"
    } else {
        $json.profiles | Add-Member -NotePropertyName $profileKey -NotePropertyValue $profile -Force
        Write-Info "added launcher profile '$profileKey'"
    }
    $json | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ProfilesPath -Encoding UTF8
    return $profileKey
}

function Find-MinecraftLauncher {
    $candidates = @(
        "$env:ProgramFiles\Minecraft Launcher\MinecraftLauncher.exe",
        "${env:ProgramFiles(x86)}\Minecraft Launcher\MinecraftLauncher.exe",
        "$env:LOCALAPPDATA\Programs\Minecraft Launcher\MinecraftLauncher.exe",
        # Newer launcher branding
        "$env:ProgramFiles\WindowsApps\Microsoft.4297127D64EC6_*\MinecraftLauncher.exe"
    )
    foreach ($pat in $candidates) {
        $found = Get-ChildItem -Path $pat -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

function Test-LauncherRunning {
    # The Minecraft Launcher writes its in-memory state back to
    # launcher_profiles.json on shutdown and on certain profile changes. Any
    # edits made while it is running get silently overwritten. Detect a running
    # process by name; covers the legacy ("MinecraftLauncher") and Microsoft
    # Store ("Minecraft") variants.
    $names = @('MinecraftLauncher', 'Minecraft Launcher', 'Minecraft')
    foreach ($n in $names) {
        if (Get-Process -Name $n -ErrorAction SilentlyContinue) {
            return $true
        }
    }
    return $false
}

# --- main --------------------------------------------------------------------

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " Minecraft Fabric MCP Server -- local test setup"                -ForegroundColor Cyan
Write-Host " Minecraft target: $Version"                                     -ForegroundColor Cyan
Write-Host " Game dir:         $GameDir"                                     -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

if (Test-LauncherRunning) {
    Write-Host ""
    Write-Host "ERROR: Minecraft Launcher is currently running." -ForegroundColor Red
    Write-Host "       The launcher rewrites launcher_profiles.json on close / profile change," -ForegroundColor Red
    Write-Host "       which silently overwrites the MCP profile this script adds." -ForegroundColor Red
    Write-Host "       Close the launcher window completely, then re-run the script." -ForegroundColor Red
    exit 1
}

# --- 1) Build the mod jar ----------------------------------------------------

$modJar = Join-Path $RepoRoot "versions\$Version\build\libs\minecraft-fabric-mcp-0.1.0+$Version.jar"
if ($SkipBuild) {
    Write-Step "Skipping mod build (-SkipBuild)"
    if (-not (Test-Path -LiteralPath $modJar)) {
        throw "-SkipBuild was requested but $modJar does not exist. Run without -SkipBuild first."
    }
    Write-Info "Using existing $modJar"
} else {
    Write-Step "Building mod jar for $Version"
    # Populate JDK_21 / JDK_25 / JAVA_HOME_*_X64 so Gradle's toolchain locator
    # finds Corretto / Adoptium installs the user may have. Without these,
    # gradle.properties:org.gradle.java.installations.fromEnv has nothing to
    # match against and compileJava fails with "no toolchain matching
    # languageVersion=21" -- a notoriously confusing error.
    Set-GradleToolchainEnv
    Push-Location $RepoRoot
    try {
        # Run gradlew.bat directly via PowerShell's native call operator (&) so
        # compile errors and stack traces stream to the terminal in real time
        # instead of being swallowed by `cmd /c "..." 2>&1 |` indirection.
        $gradlewBat = Join-Path $RepoRoot 'gradlew.bat'
        & $gradlewBat ":${Version}:build" --stacktrace
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build for :${Version}:build failed with code $LASTEXITCODE. See the output above for the underlying compiler / task error."
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $modJar)) {
        throw "Gradle reported success but $modJar is missing. Check versions/$Version/build/libs/."
    }
    Write-Ok "Mod jar: $modJar"
}

# --- 2) Resolve Fabric Loader + installer versions ---------------------------

Write-Step "Resolving Fabric versions"
$loader = Resolve-LoaderVersion -McVersion $Version
Write-Info "Loader:    $loader"
$installerVer = Resolve-InstallerVersion
Write-Info "Installer: $installerVer"

$java = Find-Java
Write-Info "Java:      $java"

# --- 3) Cache + install Fabric Loader ---------------------------------------

Write-Step "Preparing isolated game dir $GameDir"
Ensure-Dir $GameDir
Ensure-Dir (Join-Path $GameDir 'mods')
Write-Info "mods/ at $(Join-Path $GameDir 'mods')"

Write-Step "Caching fabric-installer-$installerVer.jar"
$installerJar = Ensure-FabricInstaller -InstallerVersion $installerVer

Write-Step "Installing Fabric Loader for Minecraft $Version into $MinecraftRoot"
# IMPORTANT: install the version under the launcher's WORK directory
# ($MinecraftRoot), not the per-profile $GameDir. The Minecraft Launcher resolves
# every profile's `lastVersionId` against the work dir's versions/ folder; a
# version dropped only under a gameDir gets filtered out as "unplayable" and
# the profile silently disappears from the dropdown. The gameDir is solely for
# worlds / mods / options / saves per profile.
$versionId = Invoke-FabricInstaller -Java $java -InstallerJar $installerJar -McVersion $Version -Loader $loader -Dir $MinecraftRoot

# --- 4) Cache + copy Fabric API ---------------------------------------------

Write-Step "Downloading Fabric API for $Version"
$fabricApi = Ensure-FabricApi -McVersion $Version
$fabricApiTarget = Join-Path $GameDir "mods\$(Split-Path -Leaf $fabricApi)"
Copy-Item -LiteralPath $fabricApi -Destination $fabricApiTarget -Force
Write-Ok "Fabric API: $fabricApiTarget"

# --- 5) Copy the locally-built mod jar --------------------------------------

Write-Step "Copying MCP mod jar into mods/"
$modJarTarget = Join-Path $GameDir "mods\$(Split-Path -Leaf $modJar)"
Copy-Item -LiteralPath $modJar -Destination $modJarTarget -Force
Write-Ok "Mod jar:    $modJarTarget"

# Drop a tiny README in the isolated dir so future-you knows what this is.
$readme = Join-Path $GameDir 'WHAT_IS_THIS.txt'
$readmeBody = @"
This game directory was created by scripts/test-in-game.ps1 in the
minecraft-java-fabric-mcp-server repo. It exists so MCP-mod testing
doesn't touch your default .minecraft worlds or mod list.

Minecraft version: $Version
Fabric Loader:     $loader
Mods directory:    .\mods\

To clean up, delete this directory and remove the 'mcp-test-$Version'
profile from $MinecraftRoot\launcher_profiles.json.

Generated: $((Get-Date).ToUniversalTime().ToString('o'))
"@
Set-Content -LiteralPath $readme -Value $readmeBody -Encoding UTF8

# --- 6) Register launcher profile -------------------------------------------

Write-Step "Registering Minecraft Launcher profile"
$profilesPath = Join-Path $MinecraftRoot 'launcher_profiles.json'
$profileName = "MCP Test $Version"
$profileKey = Sync-LauncherProfile -ProfilesPath $profilesPath -Name $profileName -VersionId $versionId -GameDir $GameDir
Write-Ok "Profile '$profileName' (key: $profileKey)"

# --- 7) Launch or print next steps ------------------------------------------

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " Setup complete."                                                 -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Profile:   $profileName"
Write-Host "  Game dir:  $GameDir"
Write-Host "  Mods:"
Get-ChildItem -LiteralPath (Join-Path $GameDir 'mods') -Filter '*.jar' | ForEach-Object {
    Write-Host "    - $($_.Name)"
}
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Open Minecraft Launcher."
Write-Host "  2. Pick the profile '$profileName' from the dropdown (top-left)."
Write-Host "  3. Click Play. The integrated server boots on world load -- that's"
Write-Host "     when the MCP HTTP listener binds (default http://127.0.0.1:8765)."
Write-Host "  4. Verify with: curl http://127.0.0.1:8765/healthz"
Write-Host ""

if ($Launch) {
    Write-Step "Opening Minecraft Launcher"
    $launcher = Find-MinecraftLauncher
    if ($launcher) {
        Write-Info $launcher
        Start-Process -FilePath $launcher
    } else {
        Write-Info "Couldn't locate the launcher .exe; using shell association"
        Start-Process "minecraft://"  # Generally registered by the launcher install
    }
}

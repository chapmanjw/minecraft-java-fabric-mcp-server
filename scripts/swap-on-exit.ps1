# Non-admin jar swap for an NSSM-supervised server (ASCII only).
#
# We cannot stop/start the Windows service without elevation, but NSSM auto-restarts
# the managed process when it exits. So the caller triggers a graceful in-game /stop
# via MCP; THIS script tight-polls until the old jar's file lock releases (process
# gone), swaps in the new jar, and lets NSSM restart onto it. Non-destructive: the
# old jar is backed up first, and if the swap window is missed NSSM just restarts the
# old jar (retry). Run this in the BACKGROUND, then issue /stop.
param(
    [string]$ServerDir = "C:\fabric-mcp",
    [string]$RepoDir   = (Split-Path $PSScriptRoot -Parent),
    [string]$NewJar    = "minecraft-fabric-mcp-1.0.0+26.1.2.jar",
    [int]$TimeoutSec   = 120
)
$ErrorActionPreference = "Continue"
$modsDir = Join-Path $ServerDir "mods"
$backupDir = Join-Path $ServerDir "mods-backup"
$src = Join-Path $RepoDir ("versions\26.1.2\build\libs\" + $NewJar)

if (-not (Test-Path $src)) { Write-Host ("NO_SRC: " + $src); exit 3 }

# back up current mcp jar(s)
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$slot = Join-Path $backupDir ((Get-Date -Format "yyyyMMdd_HHmmss") + "_swap")
New-Item -ItemType Directory -Force -Path $slot | Out-Null
$old = Get-ChildItem $modsDir -Filter "minecraft-fabric-mcp-*.jar"
foreach ($j in $old) { Copy-Item $j.FullName (Join-Path $slot $j.Name) }
Write-Host ("backed up to " + $slot)
Write-Host "watching for process exit (jar unlock)..."

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    $swapped = $true
    # Remove ANY current mcp jar (any version) once it unlocks -- supports a
    # same-version (0.4.0 -> 0.4.0) redeploy as well as a version bump. The
    # fabric-api jar is left alone (different prefix).
    foreach ($j in (Get-ChildItem $modsDir -Filter "minecraft-fabric-mcp-*.jar")) {
        try {
            Remove-Item $j.FullName -Force -ErrorAction Stop
        } catch {
            $swapped = $false   # still locked => process alive
        }
    }
    if ($swapped) {
        # old jar(s) gone => drop the freshly-built one in (force-overwrite to be safe)
        $dest = Join-Path $modsDir $NewJar
        Copy-Item $src $dest -Force
        Write-Host ("SWAPPED -> " + $NewJar)
        exit 0
    }
    Start-Sleep -Milliseconds 200
}
Write-Host "TIMEOUT: jar never unlocked (server did not exit). No change made."
exit 1

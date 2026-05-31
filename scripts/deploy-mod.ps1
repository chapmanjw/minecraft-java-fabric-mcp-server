# Safe deploy of the minecraft-fabric-mcp jar to the live NSSM service.
# ASCII ONLY (no em-dashes / smart quotes) so the PowerShell parser is happy.
#
# Stops the service, backs up the current mcp jar, installs the freshly-built jar,
# starts the service, and polls the MCP health endpoint. If the new jar fails to
# come healthy within the timeout, it AUTO-ROLLS-BACK to the backup and restarts,
# so a bad build can never strand the server. Pure service/file ops; needs no MCP.
#
# Usage (from the mod repo root, after gradlew build):
#   powershell -ExecutionPolicy Bypass -File scripts/deploy-mod.ps1 -JarGlob "minecraft-fabric-mcp-0.4.0+26.1.2.jar"
#
param(
    [string]$Service   = "MinecraftFabricMCP",
    [string]$ServerDir = "C:\fabric-mcp",
    [string]$RepoDir   = (Split-Path $PSScriptRoot -Parent),
    [string]$JarGlob   = "minecraft-fabric-mcp-*+26.1.2.jar",
    [string]$HealthUrl = "http://127.0.0.1:8765/healthz",
    [int]$HealthTimeoutSec = 90
)

$ErrorActionPreference = "Stop"
$nssm = Join-Path $ServerDir "nssm.exe"
$modsDir = Join-Path $ServerDir "mods"
$backupDir = Join-Path $ServerDir "mods-backup"

function Find-BuiltJar {
    $candidates = @(
        (Join-Path $RepoDir "versions\26.1.2\build\libs"),
        (Join-Path $RepoDir "build\libs")
    ) | Where-Object { Test-Path $_ }
    foreach ($d in $candidates) {
        $jar = Get-ChildItem $d -Filter $JarGlob -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch "sources|javadoc|dev|-slim" } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($jar) { return $jar.FullName }
    }
    throw "No built jar matching '$JarGlob'. Run gradlew build first."
}

function Test-Health {
    try {
        $r = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 5 -UseBasicParsing
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300)
    } catch {
        return $false
    }
}

function Wait-Healthy([int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Health) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "=== deploy-mod: locating built jar ==="
$newJar = Find-BuiltJar
Write-Host ("new jar: " + $newJar)

$currentJars = Get-ChildItem $modsDir -Filter "minecraft-fabric-mcp-*.jar"
Write-Host ("current mcp jar(s): " + ($currentJars.Name -join ", "))

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupSlot = Join-Path $backupDir $stamp
New-Item -ItemType Directory -Force -Path $backupSlot | Out-Null
foreach ($j in $currentJars) { Copy-Item $j.FullName (Join-Path $backupSlot $j.Name) }
Write-Host ("backed up to " + $backupSlot)

Write-Host ("=== stopping " + $Service + " ===")
& $nssm stop $Service | Out-Host
Start-Sleep -Seconds 3

foreach ($j in $currentJars) { Remove-Item $j.FullName -Force }
Copy-Item $newJar (Join-Path $modsDir (Split-Path $newJar -Leaf))
Write-Host ("installed " + (Split-Path $newJar -Leaf))

Write-Host ("=== starting " + $Service + " ===")
& $nssm start $Service | Out-Host
Write-Host ("polling " + $HealthUrl + " (up to " + $HealthTimeoutSec + "s)...")
if (Wait-Healthy $HealthTimeoutSec) {
    Write-Host "=== HEALTHY - deploy succeeded ==="
    exit 0
}

Write-Host "!!! new jar did not become healthy - ROLLING BACK !!!"
& $nssm stop $Service | Out-Host
Start-Sleep -Seconds 3
Get-ChildItem $modsDir -Filter "minecraft-fabric-mcp-*.jar" | Remove-Item -Force
foreach ($j in (Get-ChildItem $backupSlot -Filter "*.jar")) {
    Copy-Item $j.FullName (Join-Path $modsDir $j.Name)
}
& $nssm start $Service | Out-Host
if (Wait-Healthy 60) {
    Write-Host "=== rolled back to previous jar; server healthy again ==="
    exit 1
}
Write-Host ("!!! ROLLBACK ALSO UNHEALTHY - manual help needed. Backup at " + $backupSlot)
exit 2

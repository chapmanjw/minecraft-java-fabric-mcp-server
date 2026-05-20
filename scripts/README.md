# Scripts

Local developer tooling.

## End-to-end local-test setup

Both scripts do the same thing for their respective platforms: build the mod jar for a requested Minecraft target, download + cache Fabric Loader and Fabric API, copy both into an isolated game directory, register a Minecraft Launcher profile, and (optionally) open the launcher.

| Platform | Script |
| --- | --- |
| Windows | [`test-in-game.ps1`](./test-in-game.ps1) |
| macOS + Linux | [`test-in-game.sh`](./test-in-game.sh) |

### What both scripts do

1. Build the mod jar for the requested Minecraft target (skippable with `--skip-build` / `-SkipBuild`).
2. Look up the latest compatible Fabric Loader version via Fabric's meta API.
3. Download and cache the Fabric installer + Fabric API matching that target (cached under `scripts/.cache/`).
4. Create an isolated game directory so testing doesn't touch the user's existing worlds or mod list:
   - Windows: `%APPDATA%\.minecraft-mcp-<version>\`
   - macOS:   `~/Library/Application Support/minecraft-mcp-<version>/`
   - Linux:   `~/minecraft-mcp-<version>/`
5. Install Fabric Loader into that directory.
6. Copy Fabric API + the locally-built MCP mod jar into `<gameDir>/mods/`.
7. Register a Minecraft Launcher profile (`MCP Test <version>`) pointing at the isolated dir.
8. (Optional, `--launch` / `-Launch`) Open the Minecraft Launcher.

### Windows (`test-in-game.ps1`)

```powershell
# First-time setup for the most-tested target.
.\scripts\test-in-game.ps1 -Version 1.21.11

# Skip the Gradle rebuild and open the launcher immediately.
.\scripts\test-in-game.ps1 -Version 1.21.11 -SkipBuild -Launch

# Test against 26.1.x.
.\scripts\test-in-game.ps1 -Version 26.1.1
```

Requirements:
- PowerShell 5.1 or newer.
- A Java runtime (the Fabric installer is a jar). The script discovers JDK 21 / 25 at the standard Corretto paths, `JAVA_HOME`, or `PATH`.
- Minecraft Launcher installed in the standard location (auto-located on `-Launch`).

### macOS + Linux (`test-in-game.sh`)

```bash
# First-time setup.
./scripts/test-in-game.sh -v 1.21.11

# Skip the Gradle rebuild and open the launcher immediately.
./scripts/test-in-game.sh -v 1.21.11 --skip-build --launch

# Test against 26.1.x on Linux with a custom isolated dir.
./scripts/test-in-game.sh -v 26.1.1 --game-dir ~/dev/mcp-test
```

Requirements:
- bash 4+ (recent macOS ships 3.2 by default; install GNU bash via `brew install bash` if your shebang dispatch picks the system bash and dies).
- `curl` (universal).
- `jq` -- install with `brew install jq` (macOS) or `sudo apt-get install jq` / `sudo dnf install jq` (Linux). The script checks for these up front and prints the install command.
- A Java runtime -- discovered via `JAVA_HOME`, `/usr/libexec/java_home -v 21|25` (macOS), `/usr/lib/jvm/temurin-*` (Linux), or `PATH`.
- Minecraft Launcher -- auto-detected at `/Applications/Minecraft.app` (macOS) or `minecraft-launcher` on `PATH` (Linux). If absent, the script prints next steps without launching.

### After the script finishes

1. Open the Minecraft Launcher (already open if you passed `--launch` / `-Launch`).
2. Pick **MCP Test \<version\>** from the profile dropdown.
3. Click **Play** and create or load a world.
4. Verify the server is up: `curl http://127.0.0.1:8765/healthz` should return `{"status":"ok"}`.

### Cleanup

Delete the isolated game directory and remove the `mcp-test-<version>` entry from the user's `launcher_profiles.json`. The script is idempotent -- re-running with the same version reuses or refreshes everything.

### Caching

Downloaded artifacts (Fabric installer jar, Fabric API jar) cache under `scripts/.cache/`. This directory is git-ignored. Delete it to force fresh downloads.

### Limitations

- The launcher itself still requires manual sign-in and a click on Play.
- For Minecraft 26.1.x: Fabric Loader's beta channel must include a build that supports the target. The script queries Fabric meta at runtime, so it picks up new builds automatically -- but if Modrinth has no compatible Fabric API release yet, the script will fail at the API-download step. In that case try a 1.21.x target instead, or pass a known-working `--loader` / `-LoaderVersion`.

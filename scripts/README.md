# Scripts

Local developer tooling.

## `test-in-game.ps1`

End-to-end "set up an isolated Minecraft install with the freshly-built mod and launch it" script for Windows.

### What it does

1. Builds the mod jar for the requested Minecraft target (skippable with `-SkipBuild`).
2. Looks up the latest compatible Fabric Loader version via Fabric's meta API.
3. Downloads and caches the Fabric installer + Fabric API matching that target (cached under `scripts/.cache/`).
4. Creates an isolated game directory at `%APPDATA%\.minecraft-mcp-<version>\` so testing doesn't touch the user's existing worlds or mod list.
5. Installs Fabric Loader into that directory.
6. Copies Fabric API + the locally-built MCP mod jar into `<gameDir>/mods/`.
7. Registers a Minecraft Launcher profile (`MCP Test <version>`) pointing at the isolated dir.
8. (Optional, `-Launch`) Opens the Minecraft Launcher.

### Usage

```powershell
# First-time setup for the most-tested target.
.\scripts\test-in-game.ps1 -Version 1.21.11

# Skip the Gradle rebuild and open the launcher immediately.
.\scripts\test-in-game.ps1 -Version 1.21.11 -SkipBuild -Launch

# Test against 26.1.x.
.\scripts\test-in-game.ps1 -Version 26.1.1
```

After the script finishes:

1. Open the Minecraft Launcher (it's open if you passed `-Launch`).
2. Pick **MCP Test \<version\>** from the profile dropdown.
3. Click **Play** and create or load a world.
4. Verify the server is up: `curl http://127.0.0.1:8765/healthz` should return `{"status":"ok"}`.

### Cleanup

Delete `%APPDATA%\.minecraft-mcp-<version>\` and remove the `mcp-test-<version>` entry from `%APPDATA%\.minecraft\launcher_profiles.json`. The script is idempotent — re-running with the same version reuses or refreshes everything.

### Caching

Downloaded artifacts (Fabric installer jar, Fabric API jar) cache under `scripts/.cache/`. This directory is git-ignored. Delete it to force fresh downloads.

### Requirements

- Windows + PowerShell 5.1 or newer
- A Java runtime (the Fabric installer is a jar). The script discovers JDK 21 / 25 at the standard Corretto paths, JAVA_HOME, or PATH. Build itself uses Gradle's toolchain, which expects JDK 21 for 1.21.x and JDK 25 for 26.1.x.
- The Minecraft Launcher installed in the standard location (the script auto-finds it on `-Launch`).
- Internet access on first run (subsequent runs work offline if everything's cached).

### Limitations

- Windows only. Could be ported to bash/zsh for macOS/Linux with the same logic (Fabric installer + Modrinth + launcher profile JSON are platform-agnostic).
- The script registers a profile but the Minecraft Launcher itself still requires manual sign-in and a click on Play.

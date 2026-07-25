# Single-player setup

Goal: from "I have Minecraft Java" to "Claude is editing my world" in under 10 minutes, with no
token configuration and no firewall changes.

## Prerequisites

- Minecraft: Java Edition account and launcher installed
- Java 21+ installed and on your `PATH` (needed to run the Fabric installer in Step 1).
  [Temurin](https://adoptium.net/) is the easiest install on all platforms.
- An MCP client — Claude Code with the companion
  [`minecraft-java-fabric-claude-plugin`](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin)
  is recommended, but Claude Desktop, Cursor, Windsurf, or any agent that speaks MCP over
  Streamable HTTP works too
- About 5 minutes

## Step 1 — Install Fabric Loader

Download the official installer from <https://fabricmc.net/use/installer/> and run it from the
command line, replacing `<version>` with your target Minecraft version (`1.21.11`, `26.1.1`,
`26.1.2`, or `26.2`):

**macOS:**
```sh
java -jar fabric-installer-*.jar client -mcversion <version> -dir ~/Library/Application\ Support/minecraft
```

**Windows:**
```sh
java -jar fabric-installer-*.jar client -mcversion <version> -dir "%appdata%\.minecraft"
```

**Linux:**
```sh
java -jar fabric-installer-*.jar client -mcversion <version> -dir ~/.minecraft
```

The output **must end with "Creating profile"** — if it only shows "Done" without that line,
the Fabric profile was not created and Step 3 will fail. Re-run the command if that happens.

## Step 2 — Download the mods

Get **two** jars and place them both in your `.minecraft/mods/` folder:

- **This mod's jar** — from
  [Releases](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/releases), pick the
  file ending in `+<your-mc-version>.jar`
- **Fabric API** — from [Modrinth](https://modrinth.com/mod/fabric-api/versions), pick the
  matching Loader version

Platform paths:
- **macOS**: `~/Library/Application Support/minecraft/mods/`
- **Windows**: `%appdata%\.minecraft\mods\`
- **Linux**: `~/.minecraft/mods/`

Your mods folder should look like:

```
mods/
├── fabric-api-<api-version>+<mc-version>.jar
└── minecraft-fabric-mcp-<mod-version>+<mc-version>.jar
```

Both `<mc-version>` suffixes must be the SAME and must match your game. The mod jar declares
an exact `depends.minecraft`, so a mismatched pair is refused at load with a clear message.
The Fabric API build must also be at least the one the mod was built against — see the
matrix in [version-compatibility.md](version-compatibility.md), currently `0.141.5+1.21.11`,
`0.145.4+26.1.1`, `0.155.2+26.1.2` and `0.155.2+26.2`.

## Step 3 — Launch the game with Fabric

1. Open the **Minecraft Launcher**
2. In the left sidebar, click **MINECRAFT: JAVA EDITION**
3. Click the **Installations** tab at the top of the window
4. Find **fabric-loader-\<version\>** in the list and click **Play** next to it
5. Create or open any world and get a character into it

> **Don't see the fabric-loader-\<version\> profile in Installations?** The installer didn't
> create it in Step 1. Re-run the installer command and confirm the output ends with
> "Creating profile", then check Installations again.

Check the game log. You should see:

```
[minecraft_fabric_mcp] MCP server config loaded from .../config/minecraft_fabric_mcp/config.json
[minecraft_fabric_mcp] Registered N MCP tools (M skipped due to version/module constraints)
[minecraft_fabric_mcp] MCP server listening at http://127.0.0.1:8765 (host=127.0.0.1, port=8765, auth=false, tls=false)
```

Quick liveness check:

```sh
curl http://localhost:8765/healthz
# → {"status":"ok"}
```

## Step 4 — Connect Claude

Pick the client you use. Claude Code via the plugin is the quickest; Claude Desktop is a few
lines of JSON.

### Option A — Claude Code, via the plugin (recommended)

The companion
[`minecraft-java-fabric-claude-plugin`](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin)
registers the MCP connection to `http://localhost:8765/mcp` and adds the setup and building
skills/agents in one shot. From a Claude Code session, run:

```
/plugin marketplace add chapmanjw/minecraft-java-fabric-claude-plugin
/plugin install minecraft-java@minecraft-java-claude
```

Restart Claude Code. The `minecraft-java` MCP tools and the plugin's skills are now available.

(Prefer the raw tools without the plugin? Run
`claude mcp add --transport http minecraft-java http://localhost:8765/mcp` instead.)

### Option B — Claude Desktop

Edit Claude Desktop's config file:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

Add:

```json
{
  "mcpServers": {
    "minecraft-java": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8765/mcp"]
    }
  }
}
```

Save, then quit and reopen Claude Desktop. The MCP tool list appears in the model's tool tray.

## Try it

Open a new chat in your connected client, with at least one player loaded in the Minecraft world:

> "Spawn a chicken in front of me."

> "What's the time and weather right now? Set it to clear and to noon."

> "List the players online and what each is holding."

> "Spawn a ring of armor stands around me, then give me a diamond pickaxe."

> "Build a small stone-brick house at (100, 64, 100) — find a flat spot first."

If something goes wrong, see [docs/troubleshooting.md](troubleshooting.md).

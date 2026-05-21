# Single-player setup

Goal: from "I have Minecraft Java" to "Claude is editing my world" in under 10 minutes, with no
token configuration and no firewall changes.

## Prerequisites

- Minecraft: Java Edition account and launcher.
- Java 21 (for the 1.21.11 jar) or Java 25 (for the 26.1.x jars) installed. Match the mod jar
  you download to the right JDK — the Minecraft launcher's Fabric Loader profile uses its own
  bundled JRE so this is only relevant if you launch Minecraft outside the official launcher.
- An MCP client. Claude Code (with the companion
  [`minecraft-java-fabric-claude-plugin`](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin))
  is the smoothest, but Claude Desktop, Cursor, Windsurf, or any agent that speaks MCP over
  Streamable HTTP works too.
- About 5 minutes.

## Step 1 — Install Fabric Loader

Download the official installer from <https://fabricmc.net/use/installer/>.

- macOS / Linux: `java -jar fabric-installer-*.jar`
- Windows: double-click the `.exe`.

Pick **Client**, select your target Minecraft version (1.21.11, 26.1.1, or 26.1.2), keep the
default install location, and click **Install**.

Open the Minecraft Launcher — you should see a new **Fabric Loader** profile. Launch it once,
then exit the game. This creates the `.minecraft/mods/` folder you'll drop jars into below.

## Step 2 — Download the mods

Get **two** jars and place them both in your `.minecraft/mods/` folder:

- **This mod's jar**, matching your Minecraft version. From
  [Releases](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/releases) pick the file
  ending in `+<your-mc-version>.jar`.
- **Fabric API** at the same Minecraft version. From
  [Modrinth](https://modrinth.com/mod/fabric-api/versions) pick the matching Loader Version.

Your mods folder should now contain something like:

```
mods/
├── fabric-api-0.149.1+26.1.2.jar
└── minecraft-fabric-mcp-0.1.0+26.1.2.jar
```

The platform-specific path:

- **Windows**: `%appdata%\.minecraft\mods\`
- **macOS**: `~/Library/Application Support/minecraft/mods/`
- **Linux**: `~/.minecraft/mods/`

## Step 3 — Launch the game

Open the Minecraft Launcher, pick the Fabric profile, click **Play**. Load any world.

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

Pick the client you use. Claude Code via the plugin is the quickest; Claude Desktop is a few lines
of JSON.

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

> "What's the time and weather right now? Set it to clear and to noon."

> "List the players online and what each is holding."

> "Spawn a ring of armor stands around me, then give me a diamond pickaxe."

> "Build a small stone-brick house at (100, 64, 100) — find a flat spot first."

If something goes wrong, see [docs/troubleshooting.md](troubleshooting.md).

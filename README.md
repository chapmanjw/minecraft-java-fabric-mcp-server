<p align="center">
  <img src="docs/images/logo.png" width="200" alt="Minecraft Java Fabric MCP Server logo">
</p>

# Minecraft Java MCP Server (Fabric)

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that runs **inside Minecraft
Java Edition as a Fabric mod**. It exposes the server-side Minecraft API and the Fabric API as MCP
tools so an MCP client — Claude Desktop, Cursor, or any agent that speaks MCP — can read and
manipulate a live world programmatically.

![A voxel mascot built in a live world over MCP](docs/images/bean.png)

*An agent built this over MCP — authored as a parametric voxel model, then placed
in a single `block_fill_batch` and verified with `block_render_region` (the
server-side render that produced no screenshot dependency — it reads block map
colours straight from the world).*

![Ghasticlawd, a voxel Ghast-and-Claude mascot, built in a live world](docs/images/ghasticlawd.png)

*And **Ghasticlawd** — the project's Ghast × Claude mascot — voxelized, placed via
`block_fill_batch`, and confirmed with `block_render_region`.*

![A layered red-rock canyon inspired by the national parks of the American West, built in a live world over MCP](docs/images/canyon.png)

*And terrain, too — this red-rock canyon, inspired by the national parks of the
American West, was generated as a hydraulically eroded heightfield,
render-checked, then materialized into the world through `block_fill_batch` and
verified with `block_render_region`.*

The mod ships separate jars for each supported Minecraft version. The build matrix in v0.2.0 is
**1.21.11**, **26.1.1**, and **26.1.2**.

## ⚠️ Built on Minecraft and Fabric API internals

This mod depends on Minecraft's server-side internals via the Fabric API. The API surface evolves
between Minecraft versions and a Minecraft update can change, deprecate, or remove methods this
mod relies on. Pin your Minecraft installation, your Fabric API jar, and this mod's jar to known-good
versions and upgrade them together. Treat the stack as **experimental**.

## The sibling projects

This server pairs with a companion Claude Code plugin, and the whole thing is the Java Edition
counterpart of an existing Bedrock stack:

| Repository | Edition | Role |
| --- | --- | --- |
| **`minecraft-java-fabric-mcp-server`** (this repo) | Java Edition | The MCP server, packaged as a Fabric mod. Loads inside Minecraft and exposes the Java API as MCP tools. |
| [`minecraft-java-fabric-claude-plugin`](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin) | Java Edition | The companion Claude Code plugin. Bundles the MCP connection plus setup and builder skills/agents that drive this server. |
| [`minecraft-bedrock-mcp-server`](https://github.com/chapmanjw/minecraft-bedrock-mcp-server) | Bedrock Edition | The Node/TypeScript MCP server for the Bedrock Dedicated Server. |
| [`minecraft-bedrock-mcp-behavior-pack`](https://github.com/chapmanjw/minecraft-bedrock-mcp-behavior-pack) | Bedrock Edition | The companion behavior pack for the Bedrock setup. |

The plugin is optional — any MCP client (Claude Desktop, Cursor, a hand-configured `claude mcp add`)
can talk to this server directly — but on Claude Code it's the fastest path: it wires up the
connection and ships the building skills and agents. See
[Connect an MCP client](#7-connect-an-mcp-client) below.

The Java and Bedrock stacks are independent — installing one has no effect on the other. They share
a tone and configuration style but expose different (overlapping) tool surfaces because the Java and
Bedrock Script APIs differ substantially.

## How it works

```
MCP client (Claude Desktop, Cursor, …)
   │   MCP over Streamable HTTP, localhost-only by default
   ▼
[ Fabric mod: HTTP transport ]   ←—— Host / Origin / optional bearer validation
   │
   ▼
[ Protocol layer ] ←—— JSON-RPC 2.0 over Streamable HTTP, tool registry
   │
   ▼
[ Runtime: MinecraftMainThreadExecutor ]   ←—— marshals every world touch onto the server main thread
   │
   ▼
[ Adapter layer ]   ←—— version-stable interface; per-version implementations
   │
   ▼
the loaded Minecraft world (integrated single-player, or dedicated server)
```

Every tool call:

1. Arrives on an HTTP thread of the embedded JDK `HttpServer`.
2. Passes Host / Origin / (optional) bearer / body-size / rate-limit checks.
3. Is dispatched by the MCP layer to a tool implementation.
4. The tool submits any Minecraft API access through the main-thread executor.
5. The executor completes the work on the next server tick and returns the result.
6. The HTTP thread serializes the result to JSON and responds.

See [docs/architecture.md](docs/architecture.md) for the detailed layering rationale.

## Quick start (single-player, default config)

End-to-end: a clean machine to "Claude making it rain chicken jockeys in your village." The default
configuration binds to `127.0.0.1:8765`, **no token required**, with strict Host/Origin validation
that defends against DNS-rebinding and CSRF.

### 1. Install Java

The mod runs inside the Minecraft launcher's JVM, which the launcher itself manages — for typical
single-player play you don't need to install Java separately. (You only need a system JDK if you're
[building from source](#building-from-source).)

### 2. Install Minecraft Java Edition and the launcher

If you don't already have it, get the Minecraft Launcher from
[minecraft.net/download](https://www.minecraft.net/download). Launch it once, sign in, and load a
vanilla world to confirm the install works.

### 3. Install Fabric Loader

Download the [Fabric Loader installer](https://fabricmc.net/use/installer/) and run it. Pick the
Minecraft version you want (one of **1.21.11**, **26.1.1**, or **26.1.2** — the versions this mod
ships jars for) and click Install. The installer registers a new "fabric-loader-…" profile in the
Minecraft Launcher and creates a `mods/` folder under your game directory.

### 4. Download the mod and Fabric API

- This mod: grab the matching jar from the
  [Releases page](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/releases). Pick the
  file whose suffix matches your Minecraft version, e.g. `minecraft-fabric-mcp-0.2.0+1.21.11.jar`.
- [Fabric API](https://modrinth.com/mod/fabric-api): pick the version that matches the same MC
  version.

### 5. Install both jars

Drop both jars into your Minecraft `mods/` directory:

- **Windows**: `%APPDATA%\.minecraft\mods\`
- **macOS**: `~/Library/Application Support/minecraft/mods/`
- **Linux**: `~/.minecraft/mods/`

### 6. Launch the Fabric profile

Open the Minecraft Launcher, select the **fabric-loader-…** profile from the dropdown, click Play,
and open any world (existing or new). When the world finishes loading, the integrated server starts
and the MCP listener binds to `http://127.0.0.1:8765/mcp`. Confirm with:

```sh
curl http://127.0.0.1:8765/healthz
# → {"status":"ok"}
```

### 7. Connect an MCP client

The mod speaks MCP over **Streamable HTTP**. Connect from whichever client you use.

**Claude Code (CLI) — via the plugin (recommended):** the companion
[`minecraft-java-fabric-claude-plugin`](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin)
wires up the MCP connection *and* installs the setup and building skills/agents. From a Claude Code
session, run:

```
/plugin marketplace add chapmanjw/minecraft-java-fabric-claude-plugin
/plugin install minecraft-java@minecraft-java-claude
```

Restart Claude Code afterward. See the plugin's README for what each skill and agent does.

**Claude Code (CLI) — manual:** if you'd rather connect the raw tools without the plugin, run once
from a terminal —

```sh
claude mcp add --transport http minecraft-java http://localhost:8765/mcp
```

**Claude Desktop:** edit the desktop app's `claude_desktop_config.json` (Settings → Developer → Edit
Config) and add:

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

Then restart Claude Desktop. For authenticated / remote setups, see
[docs/claude-desktop-integration.md](docs/claude-desktop-integration.md) and
[docs/cursor-integration.md](docs/cursor-integration.md).

### 8. Try it

In any Claude session with the MCP server connected, ask it natural-language things like:

- _"Who's online in Minecraft right now?"_ → `player_list_online`
- _"Give me 64 diamonds."_ → `player_give_item`
- _"Set it to noon and clear the weather."_ → `level_set_time`, `level_set_weather`
- _"Build me a 5×5×5 box of glass at my feet."_ → `block_fill_region`
- _"Find a nearby village."_ → `entity_query` for `minecraft:villager`
- _"Make it rain chicken jockeys over the village."_ → 20× `command_execute` with
  `summon minecraft:chicken <x> <y> <z> {Passengers:[{id:"minecraft:zombie",IsBaby:1b}]}` plus
  `level_set_weather` set to `thunder` for atmosphere.

That last one is the canonical smoke test. If chickens with baby zombie riders rain down from the
sky over your village while a thunderstorm rolls in, the whole stack is wired up correctly.

The full single-player walkthrough (with dedicated-server and LAN variants) lives in
[docs/setup-singleplayer.md](docs/setup-singleplayer.md). For a dedicated server with bearer auth
and remote access, see [docs/setup-dedicated-server.md](docs/setup-dedicated-server.md).

## Configuration

All configuration is JSON-on-disk at `<game dir>/config/minecraft_fabric_mcp/config.json`, with every field
overridable by an environment variable (`MCP_<UPPER_SNAKE_FIELD>`). Defaults are baked into the mod —
the file only needs to exist if you're overriding something.

| Field | Default | Description |
| --- | --- | --- |
| `host` | `127.0.0.1` | Bind address. Non-loopback requires `auth_required=true` and `allow_remote=true`. |
| `port` | `8765` | Listen port. |
| `auth_required` | `false` | When true, every request must carry `Authorization: Bearer <bearer_token>`. |
| `bearer_token` | _(null)_ | When `auth_required=true` and this is null, the mod generates a 32-byte hex token and writes it back into the config file. |
| `allow_remote` | `false` | Required true when `host` is non-loopback. Belt-and-suspenders so you don't accidentally expose the listener. |
| `allowed_origins` | `[]` | Browser-style `Origin` allow-list. Default rejects any request carrying an `Origin` header. |
| `command_timeout_ms` | `15000` | Per-tool wait for main-thread work. |
| `rate_limit_rpm` | `60` | Per-client requests per minute. |
| `max_body_bytes` | `16777216` | Max request body. |
| `event_buffer_size` | `1024` | Default per-subscription ring buffer size for `events_*` tools. |
| `queue_max` | `256` | Max concurrent async jobs (huge bulk operations). |
| `log_level` | `info` | SLF4J log level. |
| `tls_cert_path` / `tls_key_path` | _(null)_ | If both set, the listener serves HTTPS. Both must be set, or both null. |
| `metrics_enabled` | `false` | Reserved for the Prometheus `/metrics` endpoint (v0.2.0). |
| `included_categories` | `[]` | If non-empty, only tools in these categories are registered. Categories: `world`, `actors`, `gameplay`, `registries`, `server`. |
| `excluded_categories` | `[]` | Tools in these categories are dropped. Applied after `included_categories`. |
| `exclude_write_tools` | `false` | When true, only read-only inspection tools are exposed. Useful for observer agents. |

See [docs/configuration.md](docs/configuration.md) for the full reference and recipe-style examples.

## Security model summary

- The default bind is `127.0.0.1` with no token. Anything on the same machine can talk to the MCP
  endpoint; nothing else can.
- The `Host` header is validated against the bind address — DNS rebinding attacks that resolve an
  attacker-controlled domain to 127.0.0.1 still get rejected because their `Host` header is wrong.
- The `Origin` header is validated against `allowed_origins` (empty by default). Browsers always
  send `Origin` on cross-origin requests; legitimate MCP clients (`mcp-remote`, Cursor) do not.
- Binding non-loopback requires both `allow_remote: true` and `auth_required: true`. On first run
  with auth enabled, the mod generates a 32-byte hex bearer token, writes it back to the config
  file (with POSIX 600 permissions where supported), and logs it once.

Full threat model in [docs/security.md](docs/security.md).

## Tool surface

The mod registers tools whose names follow the **Minecraft Java API class** convention rather than
the Bedrock `mc_<domain>_<action>` style. Tools group by the class or concept they operate on:

| Domain | Examples |
| --- | --- |
| `server_*` | `server_get_status`, `server_set_motd`, `server_save_all_worlds` |
| `level_*` | `level_set_time`, `level_set_weather`, `level_create_explosion`, `level_get_biome_at` |
| `block_*` | `block_get_state`, `block_fill_region`, `block_fill_batch`, `block_scan_summary`, `block_render_region` |
| `block_entity_*` | `block_entity_get_nbt`, `block_entity_set_nbt` |
| `entity_*` | `entity_summon`, `entity_teleport`, `entity_apply_effect` |
| `player_*` | `player_list_online`, `player_give_item`, `player_send_title` |
| `inventory_*` | `inventory_get`, `inventory_set_slot`, `inventory_count_items` |
| `itemstack_*` | `itemstack_describe`, `itemstack_drop_at` |
| `command_*` | `command_execute`, `command_execute_as` |
| `scoreboard_*` | `scoreboard_add_objective`, `scoreboard_set_score`, `scoreboard_add_team` |
| `data_*` | `data_storage_get`, `data_attachment_set` |
| `structure_*` | `structure_save_from_world`, `structure_load_to_world` |
| `datapack_*` | `datapack_enable`, `datapack_disable` |
| `loot_table_*` / `recipe_*` / `tag_*` / `resource_loader_*` | registry-style read tools |
| `events_*` | `events_subscribe`, `events_poll`, `events_unsubscribe` |

A full reference with JSON Schemas and per-version annotations lives in [docs/tools.md](docs/tools.md).

## Version compatibility

The mod ships **one jar per Minecraft version**. The v0.2.0 build matrix:

| Minecraft | Required JDK | Mappings        | Mod jar suffix |
| --------- | ------------ | --------------- | -------------- |
| 1.21.11   | 21           | Mojang official | `+1.21.11`     |
| 26.1.1    | 25           | unobfuscated    | `+26.1.1`      |
| 26.1.2    | 25           | unobfuscated    | `+26.1.2`      |

`./gradlew chiseledBuild` produces all three jars in one invocation. Each Stonecutter version
subproject has its own `versions/<ver>/build.gradle.kts` because the Fabric Loom plugin ID
differs across Minecraft versions (legacy `fabric-loom` for 1.21.x; `net.fabricmc.fabric-loom`
LoomNoRemap variant for 26.1+).

Each tool declares the Minecraft and Fabric API constraints it needs via the `@McpTool`
annotation, and the registration layer filters tools at startup. A tool that requires
`fabric-biome-api-v1` only registers when that module is loaded; a tool restricted to MC 26.1+
won't appear in the tool list on 1.21.11. Run `tools/list` against your specific build to see what's
available.

Full detail in [docs/version-compatibility.md](docs/version-compatibility.md).

## Claude Desktop integration

The mod speaks MCP over **Streamable HTTP**. Claude Desktop connects via the
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote) adapter, which bridges a stdio entry to
the remote HTTP endpoint.

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

That's the default-config form (localhost, no token). For the authenticated form and the path to
Claude Desktop's config file on macOS / Windows, see
[docs/claude-desktop-integration.md](docs/claude-desktop-integration.md). For Cursor,
see [docs/cursor-integration.md](docs/cursor-integration.md).

## Endpoints

| Route | Method(s) | Purpose |
| --- | --- | --- |
| `/mcp` | `POST` | Single JSON-RPC request → JSON-RPC response |
| `/mcp` | `GET` | Server-to-client SSE stream (no spontaneous messages in v0.2.0) |
| `/mcp` | `DELETE` | Close session (no-op in the stateless v0.2.0 dispatcher) |
| `/mcp` | `OPTIONS` | CORS preflight |
| `/healthz` | `GET` | Liveness probe (no auth) |

## Building from source

```sh
# Build every version in the matrix (produces 3 jars)
./gradlew chiseledBuild

# Build a single version
./gradlew :1.21.11:build
./gradlew :26.1.2:build

# Switch the Stonecutter active subproject (used as the default for plain `./gradlew build`)
./gradlew "Reset active project" -Pversion=1.21.11
./gradlew build
```

Output jars land in `versions/<mcver>/build/libs/minecraft-fabric-mcp-<modver>+<mcver>.jar`.

**Requirements**: Gradle 9.4.0+ (the wrapper bundles it). The build needs both **JDK 21**
(toolchain target for 1.21.x) and **JDK 25** (toolchain target for 26.1.x; the Gradle daemon
must also run on JDK 25 because Loom enforces it at configure time). Set them via standard
env vars: `JDK_21` and `JDK_25`, or `JAVA_HOME_21_X64` and `JAVA_HOME_25_X64`. Gradle's
toolchain locator picks each one up automatically on CI (`actions/setup-java` exposes them)
and on developer machines (Corretto's Windows installer installs both into
`C:\Program Files\Amazon Corretto\`).

## Stability and versioning

This mod is a stable foundation for separately built MCP clients and agents. Its **public
contract**, governed by semantic versioning, is:

- tool **names**,
- tool **input schemas**,
- tool **output** (the `result` field of the response envelope),
- the MCP wire protocol revision (`2025-06-18` in v0.2.0),
- the configuration schema and environment variable names.

Internal layering (transport, runtime, adapter implementations) is not part of the contract and
may change at any time.

The **underlying Minecraft + Fabric API surface is not part of our contract** — a Minecraft update
can still break behavior even when our public contract is unchanged. Pin versions; upgrade them in
lock-step.

## Development

```sh
./gradlew spotlessApply       # auto-format
./gradlew check               # spotless, checkstyle, tests
./gradlew chiseledTest        # unit tests across every version
./gradlew "Reset active project" -Pversion=26.1.2
./gradlew runServer           # launches the Fabric dev server with the mod loaded
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow, code-style notes, and PR
checklist.

## Security disclosures

See [SECURITY.md](SECURITY.md). Use GitHub Security Advisories — don't open a public issue.

## License

MIT — see [LICENSE](LICENSE).

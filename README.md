# Minecraft Java MCP Server (Fabric)

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that runs **inside Minecraft
Java Edition as a Fabric mod**. It exposes the server-side Minecraft API and the Fabric API as MCP
tools so an MCP client — Claude Desktop, Cursor, or any agent that speaks MCP — can read and
manipulate a live world programmatically.

The mod ships separate jars for each supported Minecraft version. The build matrix in v0.1.0 is
**1.21.11**, **26.1.1**, and **26.1.2**.

## ⚠️ Built on Minecraft and Fabric API internals

This mod depends on Minecraft's server-side internals via the Fabric API. The API surface evolves
between Minecraft versions and a Minecraft update can change, deprecate, or remove methods this
mod relies on. Pin your Minecraft installation, your Fabric API jar, and this mod's jar to known-good
versions and upgrade them together. Treat the stack as **experimental**.

## The sibling projects

This is the Java Edition counterpart of an existing Bedrock stack:

| Repository | Edition | Role |
| --- | --- | --- |
| **`minecraft-java-fabric-mcp-server`** (this repo) | Java Edition | The MCP server, packaged as a Fabric mod. Loads inside Minecraft and exposes the Java API as MCP tools. |
| [`minecraft-bedrock-mcp-server`](https://github.com/chapmanjw/minecraft-bedrock-mcp-server) | Bedrock Edition | The Node/TypeScript MCP server for the Bedrock Dedicated Server. |
| [`minecraft-bedrock-mcp-behavior-pack`](https://github.com/chapmanjw/minecraft-bedrock-mcp-behavior-pack) | Bedrock Edition | The companion behavior pack for the Bedrock setup. |

The two stacks are independent — installing one has no effect on the other. They share a tone and
configuration style but expose different (overlapping) tool surfaces because the Java and Bedrock
Script APIs differ substantially.

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

The default configuration binds to `127.0.0.1:8765`, **no token required**, with strict Host and
Origin validation that defends against DNS-rebinding and CSRF.

1. Install Fabric Loader via the [official installer](https://fabricmc.net/use/installer/).
2. Download the matching mod jar from this repo's [Releases](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/releases) — pick the file whose name ends in `+<your-MC-version>`.
3. Download [Fabric API](https://modrinth.com/mod/fabric-api) at the same Minecraft version.
4. Drop both jars into your `.minecraft/mods/` folder (created by the Fabric installer).
5. Launch the Fabric profile in the Minecraft launcher and open any world.
6. Configure Claude Desktop to talk to `http://localhost:8765/mcp` (see [Claude Desktop integration](docs/claude-desktop-integration.md)).

That's it. No token, no firewall changes, no environment variables.

The full single-player walkthrough lives in [docs/setup-singleplayer.md](docs/setup-singleplayer.md).
For a dedicated server (and LAN/internet access with bearer auth), see
[docs/setup-dedicated-server.md](docs/setup-dedicated-server.md).

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
| `block_*` | `block_get_state`, `block_set_state`, `block_fill_region`, `block_clone_region` |
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

The mod ships **one jar per Minecraft version**. The v0.1.0 build matrix:

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
| `/mcp` | `GET` | Server-to-client SSE stream (no spontaneous messages in v0.1.0) |
| `/mcp` | `DELETE` | Close session (no-op in the stateless v0.1.0 dispatcher) |
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
- the MCP wire protocol revision (`2025-06-18` in v0.1.0),
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

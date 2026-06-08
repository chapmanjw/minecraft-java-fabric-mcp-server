# Configuration reference

Configuration lives in `<game dir>/config/minecraft_fabric_mcp/config.json`. The file is optional — every
field has a default that's safe for single-player. Environment variables override file values; the
naming convention is `MCP_<UPPER_SNAKE_CASE_FIELD>`, e.g. `MCP_PORT=8770`.

## Schema

```json
{
  "host": "127.0.0.1",
  "port": 8765,
  "auth_required": false,
  "bearer_token": null,
  "allow_remote": false,
  "allowed_origins": [],
  "command_timeout_ms": 15000,
  "rate_limit_rpm": 60,
  "max_body_bytes": 16777216,
  "event_buffer_size": 1024,
  "queue_max": 256,
  "log_level": "info",
  "tls_cert_path": null,
  "tls_key_path": null,
  "metrics_enabled": false,
  "included_categories": [],
  "excluded_categories": [],
  "max_access": "write",
  "exclude_write_tools": false
}
```

## Field reference

| Field | Type | Default | Env var | Notes |
| --- | --- | --- | --- | --- |
| `host` | string | `127.0.0.1` | `MCP_HOST` | Bind address. Non-loopback requires `allow_remote=true` AND `auth_required=true`. |
| `port` | integer | `8765` | `MCP_PORT` | Listen port (1..65535). |
| `auth_required` | boolean | `false` | `MCP_AUTH_REQUIRED` | When true, every request must carry `Authorization: Bearer <bearer_token>`. |
| `bearer_token` | string\|null | `null` | `MCP_BEARER_TOKEN` | When `auth_required=true` and this is null, the mod generates a new 32-byte hex token and writes it back into the config file. |
| `allow_remote` | boolean | `false` | `MCP_ALLOW_REMOTE` | Belt-and-suspenders opt-in for non-loopback binding. |
| `allowed_origins` | string[] | `[]` | `MCP_ALLOWED_ORIGINS` (CSV) | Origins to accept from browsers. Empty means "reject anything that has an `Origin` header". |
| `command_timeout_ms` | integer | `15000` | `MCP_COMMAND_TIMEOUT_MS` | Range 100..600000 (10 minutes). |
| `rate_limit_rpm` | integer | `60` | `MCP_RATE_LIMIT_RPM` | Per-client requests per minute. ≥1. |
| `max_body_bytes` | integer | `16777216` | `MCP_MAX_BODY_BYTES` | Max request body size. ≥1024. |
| `event_buffer_size` | integer | `1024` | `MCP_EVENT_BUFFER_SIZE` | Ring buffer size per event subscription. ≥16. |
| `queue_max` | integer | `256` | `MCP_QUEUE_MAX` | Maximum concurrent async jobs. ≥1. |
| `log_level` | string | `info` | `MCP_LOG_LEVEL` | SLF4J level (trace/debug/info/warn/error). |
| `tls_cert_path` | string\|null | `null` | `MCP_TLS_CERT_PATH` | PEM cert path. Must be paired with `tls_key_path`. |
| `tls_key_path` | string\|null | `null` | `MCP_TLS_KEY_PATH` | PKCS8 key path. Must be paired with `tls_cert_path`. |
| `metrics_enabled` | boolean | `false` | `MCP_METRICS_ENABLED` | Reserved for `/metrics`. v0.2.0. |
| `included_categories` | string[] | `[]` | `MCP_INCLUDED_CATEGORIES` (CSV) | If non-empty, this is the category allowlist. If empty, the default-on categories apply (see [Tool categories](#tool-categories)). |
| `excluded_categories` | string[] | `[]` | `MCP_EXCLUDED_CATEGORIES` (CSV) | Tools in any listed category are dropped. Applied after the include resolution. |
| `max_access` | string | `write` | `MCP_MAX_ACCESS` | Access cap: `read`, `write`, or `admin`. Tools whose access exceeds the cap are dropped. Admin tools are opt-in (`admin`). |
| `exclude_write_tools` | boolean | `false` | `MCP_EXCLUDE_WRITE_TOOLS` | Legacy alias for `max_access=read`. When true, lowers the effective cap to `read` (only read-only tools are exposed). |

## Tool categories

The server registers tools in eleven categories that track the underlying Minecraft subsystems.
On a dedicated/headless server, seven are **on by default**; three (`players`, `gameplay`,
`registries`) are **opt-in** so a fresh install exposes a lean, builder-focused surface (~102 of
the full surface). The eleventh, `client`, exists **only on the client-side MCP server**
(`minecraft-java-client`) — see [Two MCP servers: world + inspection](#two-mcp-servers-world--inspection).
Operators trim or widen the surface by including / excluding categories (lower-case wire names
below):

| Category | Default | What it covers | Example tools |
| --- | --- | --- | --- |
| `blocks` | on | Blocks and block entities | `block_set_state`, `block_fill_region`, `block_entity_get_nbt` |
| `structures` | on | Saved/loaded structure templates and the structure file store | `structure_load_to_world`, `structure_save_from_world` |
| `world` | on | Level state and the world border | `level_set_time`, `level_get_biome_at`, `worldborder_get` |
| `entities` | on | Entities | `entity_summon`, `entity_teleport`, `entity_apply_effect` |
| `items` | on | Inventory slots, item stacks, item modifiers | `inventory_set_slot`, `itemstack_describe`, `item_modify_block_slot` |
| `scripting` | on | Commands, functions, schedules, events, data storage/attachments | `command_execute`, `function_run`, `events_subscribe` |
| `server` | on | Server lifecycle/admin, datapacks | `server_get_status`, `server_save_all_worlds`, `datapack_list_available` |
| `players` | opt-in | Player info, inventory, messaging, gamemode, spawn, screens | `player_give_item`, `player_set_gamemode`, `player_send_message` |
| `gameplay` | opt-in | Scoreboards, bossbars, advancements | `scoreboard_add_objective`, `bossbar_add`, `advancement_grant` |
| `registries` | opt-in | Recipes, loot tables, tags, content registries, resource loading, fluid storage | `recipe_list`, `loot_table_generate`, `tag_get_members` |
| `client` | client server only | Client-side inspection: capture the rendered first-person frame + read perception. Registered only by `McpClientMod`, never on a dedicated server. | `view_capture`, `sense_crosshair`, `client_status` |

When `included_categories` is empty the default-on set above is the starting point; set
`included_categories` to override it with an explicit allowlist. `excluded_categories` is then
subtracted from whichever set applies.

Unknown category names in the config are logged as warnings at boot and ignored — the rest of the
list still applies.

### Access levels

Every tool has one of three access levels, and `max_access` caps which register:

| Level | Meaning |
| --- | --- |
| `read` | Inspects state without mutating it (matched by the read-verb name heuristic below). |
| `write` | Mutates ordinary world / entity / item / scoreboard state. The default cap. |
| `admin` | World-wide, server-lifecycle, destructive, or command-tree operations. Opt-in. |

The default cap is `write`, so admin tools do not register unless you set `max_access=admin`.
The admin set: `worldborder_set_size`, `worldborder_add_size`, `worldborder_set_center`,
`worldborder_set_warning_blocks`, `worldborder_set_warning_time`, `worldborder_set_damage_amount`,
`worldborder_set_damage_buffer`, `level_set_difficulty`, `level_set_game_rule`,
`level_create_explosion`, `command_register`, `server_reload_resources`, `datapack_enable`,
`datapack_disable`, `player_kick`.

> `command_execute` stays `write`. It is the workhorse command runner and can technically dispatch
> any slash command, including ones equivalent to admin tools — keep that in mind when capping at
> `write`.

Read-only is decided by:

1. A name-pattern heuristic that matches read verbs: `_get_`, `_list_`, `_find_`, `_check_`,
   `_describe`, `_scan_`, `_query`, `_evaluate`, `_count_`, `_read`, `_is_`, `_status`, `_info`,
   `_definition`, `_namespaces`. Tools whose names contain one of these fragments are `read`.
2. Tool authors can override with `@McpTool(readOnly = true)` for tools the heuristic misses, or
   `@McpTool(admin = true)` to mark an admin tool.

Effective access is `admin ? admin : (readOnly || heuristic ? read : write)`.

## Validation rules

The mod refuses to start the HTTP listener (the Minecraft server itself keeps running) if:

1. `host != 127.0.0.1 && host != ::1 && host != localhost` AND `allow_remote != true`.
2. Same as above, AND `auth_required != true`.
3. `(tls_cert_path == null) != (tls_key_path == null)`.
4. Any numeric field is outside its documented range.
5. `max_access` is not one of `read` / `write` / `admin`.

A startup-time validation error is logged with a clear "what to change" message and a usable
default that would work — fix the config and reload the world.

## Common recipes

### Default localhost, no token

Delete or leave the config file empty.

### LAN access for friends on the same network

```json
{
  "host": "0.0.0.0",
  "allow_remote": true,
  "auth_required": true
}
```

Restart the world. The mod logs a freshly generated token; share it with your friends.

### Custom port to avoid clashes

Single env var:

```sh
MCP_PORT=8770
```

### Forced reset of the generated token

Delete `bearer_token` from the config file (or set it to null) and restart. A new token is
generated on next launch.

### TLS at the mod

```json
{
  "host": "0.0.0.0",
  "allow_remote": true,
  "auth_required": true,
  "tls_cert_path": "/etc/letsencrypt/live/mc.example.com/fullchain.pem",
  "tls_key_path": "/etc/letsencrypt/live/mc.example.com/privkey.pem"
}
```

For more involved TLS setups, terminate at a reverse proxy and run the mod on plain HTTP bound
to `127.0.0.1`.

### Tightening rate limit

```json
{ "rate_limit_rpm": 30 }
```

Useful when sharing access with multiple agents — keeps a misbehaving one from starving the others.

### Slimming the tool surface for an observer agent

Only expose read-only tools across blocks + world + entities:

```json
{
  "included_categories": ["blocks", "world", "entities"],
  "max_access": "read"
}
```

The client sees only inspection tools (no `_set_*`, no spawn / kick / fill / replace). Equivalent
via env vars:

```sh
MCP_INCLUDED_CATEGORIES=blocks,world,entities
MCP_MAX_ACCESS=read
```

### Full operator surface, admin included

The lean default hides the opt-in domains and all admin tools. To expose everything an operator
might need:

```json
{
  "included_categories": ["blocks", "structures", "world", "entities", "items", "players", "gameplay", "scripting", "registries", "server"],
  "max_access": "admin"
}
```

### Builder-only surface, no server admin

```json
{ "excluded_categories": ["server"] }
```

Drops datapack + server-lifecycle tools; everything else in the default-on set stays.

## Two MCP servers: world + inspection

The mod ships **two entrypoints from one jar**:

- **`McpServerMod`** (the `main` entrypoint) — runs wherever a `MinecraftServer` exists: a
  dedicated server, or a single-player client's integrated server. It serves the **world** tools
  (`level_*`, `block_*`, `entity_*`, …). This is the `minecraft-java` endpoint, default port
  **8765**, config `config/minecraft_fabric_mcp/config.json`, env prefix `MCP_*`. Unchanged from
  earlier versions.
- **`McpClientMod`** (the `client` entrypoint) — runs inside a real, rendered client (single-player
  or a client joined to a remote server). It serves the **inspection** tools (the `client`
  category: `view_capture`, `sense_*`, `client_status`). This is the `minecraft-java-client`
  endpoint, default port **8766**, config `config/minecraft_fabric_mcp/client.json`, env prefix
  `MCP_CLIENT_*`. The client config defaults `included_categories` to `["client"]`, so the endpoint
  exposes only the inspection surface — that is the flag that keeps it a pure "fill the gaps" view
  while the world endpoint stays authoritative for everything else.

Capturing the framebuffer needs a real GPU render context, so the **client must run in a normal
(non-headless) client window** — not a stubbed/headless launcher and not a session-0 service. The
client config keys are identical to the server's (see the [schema](#schema)); only the file name,
default port, and env prefix differ.

### Supported patterns

- **Server-only** — a dedicated headless server. Only `minecraft-java` (8765, world tools). No
  inspection tools (the client entrypoint never runs without a client). This is the classic setup.
- **Client-only** — launch a single-player client (or a client joined to a server). In
  single-player the integrated server runs `McpServerMod` too, so **one process exposes both
  endpoints**: `minecraft-java` (8765, world) and `minecraft-java-client` (8766, inspection).
  "Run everything with just the client" = this — point Claude at both ports.
- **Server + client combo** — a dedicated server runs `minecraft-java` (8765); a separate client
  joins it over loopback and runs `minecraft-java-client` (8766). Claude connects to both. Use the
  server endpoint to position/aim a player (`entity_teleport` / `tp x y z yaw pitch`) and build;
  use the client endpoint to capture what that player sees.

### Example client config

`config/minecraft_fabric_mcp/client.json` (optional — these are the defaults):

```json
{
  "port": 8766,
  "included_categories": ["client"]
}
```

Or via env (note the `MCP_CLIENT_` prefix so it never collides with the server's `MCP_PORT` when
both run in one process):

```sh
MCP_CLIENT_PORT=8766
```

Remote/authenticated client endpoints follow the same rules as the server endpoint
(`auth_required`, `bearer_token`, `allow_remote`, TLS) — see the [schema](#schema) and
[validation rules](#validation-rules).

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
| `included_categories` | string[] | `[]` | `MCP_INCLUDED_CATEGORIES` (CSV) | If non-empty, only tools whose category is in this list are registered. See [Tool categories](#tool-categories). |
| `excluded_categories` | string[] | `[]` | `MCP_EXCLUDED_CATEGORIES` (CSV) | Tools in any listed category are dropped. Applied after `included_categories`. |
| `exclude_write_tools` | boolean | `false` | `MCP_EXCLUDE_WRITE_TOOLS` | When true, only read-only tools are exposed — useful for observer agents. |

## Tool categories

The server registers tools in five categories. Operators trim the surface by including / excluding
categories (lower-case wire names below):

| Category | What it covers | Example tools |
| --- | --- | --- |
| `world` | Blocks, block entities, structures, level state, world border | `block_get_state`, `level_set_time`, `structure_place` |
| `actors` | Entities, players, inventories, item stacks, item modifiers | `entity_spawn`, `player_kick`, `inventory_set_slot` |
| `gameplay` | Scoreboards, bossbars, advancements, schedules, functions, commands, events | `scoreboard_add_score`, `command_execute`, `events_subscribe` |
| `registries` | Recipes, loot tables, tags, content registries, fluid/data storage | `recipe_list`, `loot_table_generate`, `tag_get_members` |
| `server` | Server lifecycle/admin, datapacks | `server_get_status`, `datapack_enable` |

Unknown category names in the config are logged as warnings at boot and ignored — the rest of the
list still applies. If `included_categories` is non-empty but contains *only* invalid names, the
effective set is empty and no inclusion filter applies (every tool passes).

### Read-only classification

`exclude_write_tools=true` strips every mutating tool. Read-only is decided by:

1. A name-pattern heuristic that matches read verbs: `_get_`, `_list_`, `_find_`, `_check_`,
   `_describe`, `_scan_`, `_query`, `_evaluate`, `_count_`, `_read`, `_is_`, `_status`, `_info`,
   `_definition`, `_namespaces`. Tools whose names contain one of these fragments are read-only.
2. Tool authors can override with `@McpTool(readOnly = true)` for tools the heuristic misses.

The effective `readOnly` value is the OR of both — set the annotation only when the heuristic missed
the tool. The heuristic already catches all standard `*_get_*`, `*_list_*`, `*_describe`, etc.
patterns.

## Validation rules

The mod refuses to start the HTTP listener (the Minecraft server itself keeps running) if:

1. `host != 127.0.0.1 && host != ::1 && host != localhost` AND `allow_remote != true`.
2. Same as above, AND `auth_required != true`.
3. `(tls_cert_path == null) != (tls_key_path == null)`.
4. Any numeric field is outside its documented range.

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

Only expose read-only tools across world + actors:

```json
{
  "included_categories": ["world", "actors"],
  "exclude_write_tools": true
}
```

The client sees only inspection tools (no `_set_*`, no spawn / kick / fill / replace). Equivalent
via env vars:

```sh
MCP_INCLUDED_CATEGORIES=world,actors
MCP_EXCLUDE_WRITE_TOOLS=true
```

### Builder-only surface, no server admin

```json
{ "excluded_categories": ["server"] }
```

Drops datapack + server-lifecycle tools; everything else stays.

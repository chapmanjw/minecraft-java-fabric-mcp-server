# Tools reference

Every tool registered by this mod, grouped by domain. For the canonical JSON Schema of each
tool's input, query `tools/list` against your running server — the schemas are emitted by the
mod itself from the same code that validates incoming arguments.

```sh
curl -s -XPOST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | jq .result.tools
```

## Conventions

- Tool names are `snake_case` and start with the **class / concept** they operate on
  (`level_set_time`, not `mc_world_set_time` — Java mod readers will recognize the lineage).
- Tools that need a specific Fabric API module declare it in their `@McpTool` annotation. The
  registration filter drops tools whose dependencies aren't loaded, so the tool list returned to
  clients always reflects what is actually callable.
- "Version range" columns: empty means "no constraint". Compared via Fabric Loader's semantic
  version logic — `1.21.11` < `26.1.1`.
- Threading: every tool's handler submits its Minecraft-API work through the main-thread
  executor. From the client's perspective every call is fire-and-forget request/response over
  HTTP; the timeout is `command_timeout_ms` from the config (default 15s).

## Server

| Name | Description | Required modules | Min MC | Max MC |
| --- | --- | --- | --- | --- |
| `server_get_status` | Uptime, TPS/MSPT, online player count, dimensions, mod/MC versions, registered tool count. | — | — | — |
| `server_get_motd` | Returns the current server MOTD. | — | — | — |
| `server_set_motd` | Sets the server MOTD (non-persistent). | — | — | — |
| `server_save_all_worlds` | Saves all loaded worlds. | — | — | — |
| `server_reload_resources` | Reloads datapacks and resources. | — | — | — |

## Level

| Name | Description | Required modules |
| --- | --- | --- |
| `level_list_dimensions` | Lists every loaded dimension. | — |
| `level_get_dimension_info` | Dimension type, height range, biome source. | — |
| `level_get_info` | Time, weather, difficulty, default game mode, spawn point. | — |
| `level_get_time` / `level_set_time` | Read/write time of day. | — |
| `level_get_weather` / `level_set_weather` | Read/write weather + duration. | — |
| `level_get_difficulty` / `level_set_difficulty` | World difficulty. | — |
| `level_get_spawn_point` / `level_set_spawn_point` | Per-dimension world spawn. | — |
| `level_play_sound` | Plays a sound at a position for all players. | — |
| `level_spawn_particle` | Spawns particles at a position. | `fabric-particles-v1` (for custom particles only) |
| `level_lightning_strike` | Summons lightning. | — |
| `level_create_explosion` | Creates an explosion. | — |
| `level_get_game_rule` / `level_set_game_rule` / `level_list_game_rules` | Game-rule access. | `fabric-game-rule-api-v1` (set) |
| `level_get_biome_at` | Biome at a block. | `fabric-biome-api-v1` |
| `level_list_biomes_in_dimension` | Every biome registered for a dimension. | `fabric-biome-api-v1` |
| `level_place_feature` | Grows a vanilla worldgen feature at a position (`/place feature`) — trees, vegetation, ore veins, geodes, dripstone. Adds natural detail without stamping identical copies. | — |
| `level_place_features_batch` | Grows many vanilla worldgen features in one call — the batch form of `level_place_feature` and the throughput path for a vegetation/detail scatter (`features[]` of `{feature, x, y, z}`, optional `stop_on_error`). One main-thread submission, one rate-limit slot for the whole list. Capped at 4096 entries/call; reports per-entry `placed`/`failed`. | — |
| `level_fill_biome` | Paints the biome of a region (`/fillbiome`) — foliage/water tint, mob spawns, climate; optional `replace_filter`. | — |

## Block

| Name | Description |
| --- | --- |
| `block_get_state` | Block id, blockstate properties, light, hardness, block-entity NBT. |
| `block_set_state` | Place a single block with optional state properties / NBT. |
| `block_fill_region` | Bulk fill (`replace`, `destroy`, `hollow`, `outline`, `keep` modes). Auto-tiles any volume past the vanilla 32,768 `/fill` cap server-side (so large fills never silently no-op); hollow/outline are decomposed into faces. Returns total blocks changed. |
| `block_fill_batch` | Apply many fills in one call — the efficient way to place a generated/voxelized build. Each entry is `{from:[x,y,z], to:[x,y,z], block:"id[state]", mode?}`; each is auto-tiled. Bounded to 8192 entries/call. |
| `block_fill_columns` | Materialise a per-column heightmap into terrain in one call — send a compact height grid + small palette instead of thousands of box fills (no 8192-entry cap). Fills stone → subsurface → surface and floods to `sea_level`. Columns capped at 65,536/call. |
| `block_fill_columns_strata` | Like `block_fill_columns` but bands the deep mass below the subsurface into geological strata (the canyon / mesa / badlands signature) instead of one stone block: `strata[]` of `{block, thickness}` top→bottom, `base_stone` below the deepest band, optional `jitter_amplitude`/`jitter_freq` for smooth non-flat band boundaries. Same 65,536-column cap. |
| `block_erode_region` | Thermal-erode an existing terrain region (synchronous): reads the live surface, runs talus collapse, then re-materialises surface + subsurface to the new profile. `protect_box` (with a smoothstep `apron`) shields built structures so terrain naturalises into them; `dry_run` reports max/mean height delta with no writes. Same 65,536-column cap. |
| `block_erode_hydraulic_start` / `block_erode_hydraulic_status` / `block_erode_hydraulic_result` | Async hydraulic (rain-droplet) erosion on the job engine: `_start` surveys the surface, simulates droplets carving channels/valleys on a worker thread, then (unless `dry_run`) writes the result back chunked across server ticks, returning a `job_id`; poll `_status` for state (`ERODING`/`WRITING`/`DONE`/`FAILED`) + progress, then read `_result` once `DONE`. `protect_box` + `apron` shield built structures. Region default 256×256, hard cap 512×512. |
| `block_clone_region` | Copy blocks from one box to another (cross-dimension supported). |
| `block_replace_in_region` | Replace matching blocks within a box. |
| `block_get_top_y` | Highest Y at an `(x, z)` column for a `heightmap` (`WORLD_SURFACE` default, `OCEAN_FLOOR`, `MOTION_BLOCKING`, …). |
| `block_scan_region` | Scan a bounded region for matching blocks (volume capped at 65,536). |
| `block_scan_summary` | Aggregate scan of a box (≤ 1,048,576): material histogram, non-air count, and non-air bounding box — server-side, so no per-block rows flood the client. |
| `block_get_map_color` | Base map colour of a block: packed `rgb`, `#RRGGBB` hex, r/g/b, palette id. |
| `block_render_region` | Render a region to a PNG (`iso`/`side`/`front`/`top`/`hillshade`) from block map colours — server-side, no client needed. `hillshade` is a relief-shaded plan view for terrain (terraces/ziggurats show as flat bands). `step` downsamples; `scale` is pixels per voxel. Returns an `image` content block. |

## BlockEntity

| Name | Description |
| --- | --- |
| `block_entity_get_nbt` | Read block-entity NBT as SNBT. |
| `block_entity_set_nbt` | Merge SNBT into a block entity. |
| `block_entity_clear_inventory` | Clear container block contents. |

## Entity

| Name | Description |
| --- | --- |
| `entity_summon` | Summon an entity at a position with optional SNBT. |
| `entity_get` | Look up an entity by UUID. |
| `entity_query` | Query entities by selector. |
| `entity_get_components` | Return the entity's component map. |
| `entity_get_nbt` / `entity_set_nbt` | NBT read/write. |
| `entity_teleport` | Teleport (cross-dimension; optional facing target). Uses `fabric-dimensions-v1` for cross-dimension teleports. |
| `entity_apply_damage` | Apply damage from a named source. |
| `entity_set_velocity` | Set motion vector. |
| `entity_apply_effect` / `entity_remove_effect` / `entity_get_effects` | Status effect mgmt. |
| `entity_kill` | Kill via the standard damage pipeline. |
| `entity_despawn` | Silent remove. |
| `entity_add_tag` / `entity_remove_tag` / `entity_list_tags` | Scoreboard tags. |

## Player

| Name | Description |
| --- | --- |
| `player_list_online` | Online players with position, stats, latency. |
| `player_get_info` | Full state for one player. |
| `player_get_inventory` | Player inventory contents. |
| `player_give_item` | Give an item stack. |
| `player_clear_inventory_slot` / `player_clear_all_inventory` | Inventory clearing. |
| `player_set_gamemode` | Set survival / creative / adventure / spectator. |
| `player_kick` | Disconnect with a reason. |
| `player_send_message` / `player_send_actionbar` / `player_send_title` | Send text. Uses `fabric-message-api-v1` for chat hooks. |
| `player_play_sound` | Player-only sound. |
| `player_set_spawn_point` | Set per-player spawn. |
| `player_grant_xp` / `player_set_xp_level` | XP control. |
| `player_set_camera` | Vanilla `/spectate` semantics. |

## Inventory

| Name | Description |
| --- | --- |
| `inventory_get` | Read container contents. Target: `player:<uuid>` \| `entity:<uuid>` \| `block:<dim>:<x>:<y>:<z>`. |
| `inventory_set_slot` | Set a single slot. |
| `inventory_clear_slot` | Clear a single slot. |
| `inventory_swap_slots` | Swap two slots (v0.2.0). |
| `inventory_count_items` | Total stacks of an item across an inventory. |

## ItemStack

| Name | Description |
| --- | --- |
| `itemstack_describe` | Returns max stack size, durability, components for a given spec. Validates item id. |
| `itemstack_drop_at` | Spawn a dropped-item entity at a position. |

## Command

| Name | Description | Modules |
| --- | --- | --- |
| `command_execute` | Run a slash command as the console source. | `fabric-command-api-v2` |
| `command_execute_as` | Run as a specific entity (vanilla `/execute as`). | `fabric-command-api-v2` |
| `command_register` | _Reserved for a future release._ | `fabric-command-api-v2` |

## Scoreboard

| Name | Description |
| --- | --- |
| `scoreboard_list_objectives` / `scoreboard_get_objective` | Read objectives. |
| `scoreboard_add_objective` / `scoreboard_remove_objective` | Manage objectives. |
| `scoreboard_set_display_slot` | Assign an objective to a display slot. |
| `scoreboard_get_score` / `scoreboard_set_score` / `scoreboard_add_score` | Score values. |
| `scoreboard_reset_participant` | Reset a participant's score on an objective. |
| `scoreboard_list_teams` / `scoreboard_add_team` / `scoreboard_remove_team` | Manage teams. |
| `scoreboard_team_add_member` / `scoreboard_team_remove_member` | Membership. |

## Data

| Name | Description | Modules |
| --- | --- | --- |
| `data_storage_get` / `data_storage_set` / `data_storage_remove` / `data_storage_list_namespaces` | Vanilla `/data … storage`. | — |
| `data_attachment_get` / `data_attachment_set` / `data_attachment_remove` / `data_attachment_list_keys` | Fabric data attachments. | `fabric-data-attachment-api-v1` |

## Structure

| Name | Description |
| --- | --- |
| `structure_save_from_world` | Capture a region into a saved template. |
| `structure_load_to_world` | Place a saved template with optional rotation/mirror. |
| `structure_list` / `structure_get_info` / `structure_delete` | In-memory + on-disk templates. |
| `structure_file_read` / `structure_file_write` / `structure_file_list` / `structure_file_delete` | Raw file access (bytes returned as base64). |

## Datapack

| Name | Description |
| --- | --- |
| `datapack_list_available` / `datapack_list_enabled` | List datapacks. |
| `datapack_enable` / `datapack_disable` | Toggle a datapack. |

## Loot / Recipe / Tag / Resource

| Name | Description | Modules |
| --- | --- | --- |
| `loot_table_list` / `loot_table_get_definition` / `loot_table_generate` | Loot tables. | `fabric-loot-api-v3` |
| `recipe_list` / `recipe_get_definition` / `recipe_find_by_result` / `recipe_find_by_ingredient` | Recipes. | `fabric-recipe-api-v1` |
| `tag_list_in_registry` / `tag_get_members` / `tag_check_membership` | Registry tags. | `fabric-convention-tags-v2` |
| `resource_loader_list_namespaces` / `resource_loader_get_resource` | Read resources. | `fabric-resource-loader-v0` |

## Advancement

| Name | Description | Modules |
| --- | --- | --- |
| `advancement_grant` | Grant an advancement (or single criterion) to a player. | — |
| `advancement_revoke` | Revoke an advancement (or single criterion) from a player. | — |
| `advancement_list_player` | Returns granted + in-progress advancements with per-criterion progress for a player. | — |
| `advancement_list_all` | List every registered advancement id. | — |
| `advancement_get_definition` | Return the JSON definition of an advancement. | — |

## Bossbar

Eleven tools wrapping the vanilla `/bossbar` command surface plus a direct read of
`MinecraftServer#getCustomBossEvents()`:

| Name | Description | Modules |
| --- | --- | --- |
| `bossbar_list` | List every registered custom boss bar with current value/max/color/style/visibility/players. | — |
| `bossbar_add` / `bossbar_remove` | Create or delete a boss bar. | — |
| `bossbar_get` | Read a single boss bar by id. | — |
| `bossbar_set_value` / `bossbar_set_max` | Mutate progress numerics. | — |
| `bossbar_set_name` | Update the display name. | — |
| `bossbar_set_color` / `bossbar_set_style` | Update color (pink/blue/red/green/yellow/purple/white) or style (progress/notched_6/notched_10/notched_12/notched_20). | — |
| `bossbar_set_visible` | Show/hide the bar. | — |
| `bossbar_set_players` | Replace the visible-to player list. | — |

## Content Registry

Read and (mostly) write access to Fabric's content registries for fuel values, fire
behaviour, and composter chance.

| Name | Description | Modules |
| --- | --- | --- |
| `content_registry_get_fuel` | Read a fuel's burn-duration in ticks (0 = not fuel). | `fabric-content-registries-v0` |
| `content_registry_set_fuel` | Stubbed — Fabric exposes fuel registration only via build-time events. | `fabric-content-registries-v0` |
| `content_registry_is_flammable_block` / `content_registry_set_flammable_block` | Read or override per-block burn / spread chances. | `fabric-content-registries-v0` |
| `content_registry_is_compostable` / `content_registry_set_compostable` | Read or override composter level-up chance. | `fabric-content-registries-v0` |

## Fluid Storage

| Name | Description | Modules |
| --- | --- | --- |
| `fluid_storage_get` | Read the first fluid tank a block exposes on the given side. | `fabric-transfer-api-v1` |
| `fluid_storage_list_at` | List every tank the block at a position publishes. | `fabric-transfer-api-v1` |

## Function

| Name | Description | Modules |
| --- | --- | --- |
| `function_run` | Execute a datapack function, optionally as another entity. | — |
| `function_list` | List every loaded function id, optionally filtered by namespace. | — |
| `function_get_definition` | Return a textual representation of a function body. | — |

## Item Modify

| Name | Description | Modules |
| --- | --- | --- |
| `item_modify_entity_slot` | Apply a vanilla item modifier to an entity slot. | — |
| `item_modify_block_slot` | Apply a vanilla item modifier to a block container slot. | — |

## Player Screen

| Name | Description | Modules |
| --- | --- | --- |
| `player_screen_open_menu` | Open one of the standard menu screens (anvil, crafting_table, enchanting_table, loom, stonecutter, grindstone, smithing_table, cartography_table). | `fabric-screen-handler-api-v1` |
| `player_screen_open_container` | Open the block container at the given position for a player. | `fabric-screen-handler-api-v1` |
| `player_screen_close` | Close whatever screen the player has open. | `fabric-screen-handler-api-v1` |

## Resource Condition

| Name | Description | Modules |
| --- | --- | --- |
| `resource_condition_evaluate` | Decode a Fabric ResourceCondition JSON object and evaluate it against the live registry; returns `{ matches, condition_id }`. | `fabric-resource-conditions-api-v1` |

## Schedule

| Name | Description | Modules |
| --- | --- | --- |
| `schedule_function` | Schedule a function to run after N ticks with append/replace conflict policy. | — |
| `schedule_clear` | Clear pending entries for a function. | — |
| `schedule_list` | List pending scheduled-function entries. | — |

## World Border

| Name | Description | Modules |
| --- | --- | --- |
| `worldborder_get` | Read center/size/warning/damage parameters and any active size-lerp target. | — |
| `worldborder_set_size` / `worldborder_add_size` | Adjust border size, optionally over a transition window. | — |
| `worldborder_set_center` | Move the border center. | — |
| `worldborder_set_warning_blocks` / `worldborder_set_warning_time` | Configure the warning zone. | — |
| `worldborder_set_damage_amount` / `worldborder_set_damage_buffer` | Configure outside-border damage parameters. | — |

## Events

| Name | Description | Modules |
| --- | --- | --- |
| `events_subscribe` | Register a subscription for one or more event types. | `fabric-lifecycle-events-v1` |
| `events_poll` | Drain pending events for a subscription. | — |
| `events_list_subscriptions` | List active subscriptions. | — |
| `events_unsubscribe` | Remove a subscription. | — |

Supported event types (each can be subscribed independently):

- Server: `server.tick`, `server.starting`, `server.started`, `server.stopping`, `server.stopped`
- Player: `player.join`, `player.leave`, `player.chat`, `player.respawn`, `player.death`
- Entity: `entity.spawn`, `entity.death`, `entity.load`, `entity.unload`
- Block: `block.break`, `block.place`, `block.use`
- Item: `item.use`, `item.craft`
- Container: `container.open`, `container.close`

Event payloads are domain-specific JSON objects with at minimum the keys `type` (matching the
subscribed event type) and `timestamp` (ISO-8601). See the wire-shape documented at the call
site of each event-type publisher in `tools/events/EventWiring.java`.

## Filters on `events_subscribe`

The optional `filters` object on `events_subscribe` is an exact-match map applied to the event
payload. Example: subscribe to chat messages from one player only:

```json
{
  "event_types": ["player.chat"],
  "filters": { "player_uuid": "<uuid>" }
}
```

Filters use direct `JsonNode.equals` semantics — typed values must match exactly. Use no filters
to receive all events of the subscribed types.

## Client (inspection — `minecraft-java-client` server only)

These tools belong to the **`client`** category and are registered **only by the client-side
entrypoint** (`McpClientMod`), which runs inside a real, rendered Minecraft client. They are not
present on a dedicated/headless server's `minecraft-java` endpoint (they are not in
`ToolRegistration.ALL_TOOL_CLASSES`). They let Claude SEE and INSPECT the world the way a player
does — the actual rendered frame, plus client-side perception the headless server cannot provide.
See [configuration.md](configuration.md#two-mcp-servers-world--inspection) for how to run the
world (server) and inspection (client) endpoints together.

They are deliberately **read-only**: they do not move or aim the player. Position and aim the
camera from the **server** surface (`entity_teleport`, or `command_execute` with
`tp <player> <x> <y> <z> <yaw> <pitch>`), then capture here. Full player agency is a separate,
future scope.

| Name | Description |
| --- | --- |
| `view_capture` | Capture the local player's current first-person frame as a PNG `image` content block — the real client render (textures, lighting, sky, fog, water, entities). Optional `downscale` (1–8, default 1) shrinks the frame to keep the inline image small. `close_screen` (default true) dismisses any open GUI — notably the pause/Esc menu that opens when the window loses focus — and lets a clean frame render before capturing, so the shot shows the world; set false to capture the current GUI. (To stop the menu opening on focus loss at all, toggle Pause on Lost Focus off in-game with **F3 + P**.) Large windows make large PNGs — for the inline-image path (~1 MB cap in some clients) raise `downscale` or use a smaller window. Returns an error if the client is not in a world (window must not be minimized). |
| `client_status` | Local player + session status: `in_game`, dimension, position, facing (yaw/pitch), health, hunger, held item, and the connected server (or `singleplayer`). |
| `sense_crosshair` | What the crosshair points at right now: `NONE`/`MISS`, a block (position, face, block id), or an entity (type, name). |
| `sense_raycast` | Raycast from the eye along the current facing; first hit (block or entity) within `max_distance` (default 20), `include_fluids` optional. |
| `sense_entities` | Entities the client renders within `radius` (default 16) of the player, with type, name, position, distance; optional `type` substring filter. |
| `sense_screen` | Current GUI state: open screen (class + title) and, when a container other than the inventory is open, a summary of its slot contents. |

## Known limitations

All 183 registered tools have working adapter implementations against the live Minecraft
server. The notable nuances:

- `entity_get_components` returns an empty map. Vanilla `Entity` doesn't expose a typed
  component view the way `ItemStack` does in modern Minecraft; a future revision will surface
  the Fabric data-attachment map here.
- `command_register` accepts the call but does not actually register a runtime command — the
  v0.2.0 wire schema doesn't carry a webhook target. Custom commands will arrive in a later
  revision via a `command.*` event channel.
- `level_set_time` / `level_set_weather` dispatch through `/time set …` / `/weather …` on
  the 26.1.x targets because the typed setters were removed from `ServerLevel` in 26.1
  (state moved to `ServerClockManager` and `WeatherData`). Functionally identical to the
  typed path on 1.21.11.

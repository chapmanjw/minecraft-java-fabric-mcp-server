# Architecture

This document explains how the mod is structured and why. Read it before adding a tool, changing
the threading model, or modifying the security filter — the load-bearing constraints are subtle.

## Layered design

Code is organized into five layers. Each layer depends only on layers above it; nothing in the
upper layers depends on a lower layer.

```
            ┌──────────────────────┐
            │  Tools (~100 classes)│   business logic, one per MCP tool
            └──────────┬───────────┘
                       │
            ┌──────────▼───────────┐
            │  MinecraftAdapter    │   single seam to the Minecraft API
            └──────────┬───────────┘
                       │
            ┌──────────▼───────────┐
            │  Runtime + Compat    │   main-thread executor, event bus, tool filter
            └──────────┬───────────┘
                       │
            ┌──────────▼───────────┐
            │  Protocol (MCP)      │   JSON-RPC dispatch, tool registry, schemas
            └──────────┬───────────┘
                       │
            ┌──────────▼───────────┐
            │  Transport (HTTP)    │   JDK HttpServer + security filter + rate limit
            └──────────────────────┘
```

### Transport (`com.chapmanjw.minecraft.fabric.mcp.transport`)

Embedded `com.sun.net.httpserver.HttpServer`. Hosts the MCP endpoint, applies Host / Origin /
bearer / body-size / rate-limit checks via `SecurityFilter`, and routes to registered route
handlers. Zero Minecraft references.

We chose JDK `HttpServer` over Javalin or Jetty for three reasons:

1. **No classloader pain.** Loom and Fabric have strict classloader isolation. Bringing in a
   servlet container would mean shadowing Jetty's modular `jakarta.servlet` jars and dealing
   with Fabric's mixin / mod-jar boundary. The JDK HttpServer just works.
2. **Smaller jar.** The mod ships ~1.5 MB instead of ~6 MB once Jackson and the JDK HTTP layer
   are weighed against Jetty + servlet + websocket-jakarta.
3. **Smaller threat surface.** The features we don't have — async-context proxying, EL parsing,
   chunked compression — are also features we can't accidentally mis-configure into a
   vulnerability.

The MCP "Streamable HTTP" spec maps cleanly onto `HttpExchange`. SSE is implemented in
`StreamingResponseImpl` inside `HttpTransport` — it writes the response headers eagerly and then
streams `event: …\nid: …\ndata: …\n\n` frames until the handler returns.

### Protocol (`com.chapmanjw.minecraft.fabric.mcp.protocol`)

Implements the MCP wire protocol directly — JSON-RPC 2.0 over Streamable HTTP per the
[2025-06-18 spec revision](https://modelcontextprotocol.io/specification). The `McpDispatcher` is
the canonical entry point: it handles `initialize`, `tools/list`, `tools/call`, `ping`, and
notifications.

We considered taking a hard dependency on `io.modelcontextprotocol.sdk:mcp` (the official Java
SDK) but rejected it for v0.2.0:

- The SDK's transport SPI is designed for Servlet / WebFlux runtimes. We use JDK HttpServer,
  which would force a custom transport adapter anyway — so we'd be wrapping the SDK's transport
  abstraction to do less than what we need.
- The SDK pulls in a 1–2 MB transitive footprint we can avoid.
- The wire protocol is small, stable, and entirely testable against the spec doc.

The `ToolRegistry` holds the active tool set after the compat filter runs at startup. Tools are
registered exactly once and shared across all calls (they're stateless).

### Runtime (`com.chapmanjw.minecraft.fabric.mcp.runtime`)

Three independent collaborators:

- `MinecraftMainThreadExecutor` — wraps `MinecraftServer::execute` in a CompletableFuture and
  exposes a blocking `submitBlocking(work, timeout)`. **This is the only correct way to call a
  Minecraft API method from an HTTP thread.**
- `EventRingBuffer<E>` — bounded, drop-oldest, lock-based ring buffer. One instance per active
  event subscription; the bus shards events into them on publish.
- `AsyncJobRegistry` — for tools that can't complete in one main-thread call. Reserved for v0.2.0
  use cases like enormous `block_fill_region` operations.

### Compatibility (`com.chapmanjw.minecraft.fabric.mcp.compat`)

Captures the running Minecraft version and the set of loaded Fabric API modules at startup, then
evaluates each `@McpTool` annotation against that environment. Tools whose constraints fail are
de-registered with a single-line INFO log; surviving tools enter the `ToolRegistry`.

The `VersionRange` helper uses Fabric Loader's `VersionPredicate.parse(...)` so the supported
range syntax matches the rest of the Fabric ecosystem (`">=14.0.0"`, `"[1.0.0,2.0.0)"`, etc.).

### Adapter (`com.chapmanjw.minecraft.fabric.mcp.adapter`)

The single seam between tool implementations and Minecraft. The interface
`MinecraftAdapter` exposes ~100 methods, all of which return primitive types or DTOs from
`com.chapmanjw.minecraft.fabric.mcp.adapter.dto.*` — never raw `BlockState`, `Entity`, or `ServerLevel`.

The production implementation `MinecraftAdapterImpl` follows a pragmatic philosophy:

- **Reads** use direct Minecraft API calls (e.g. `level.getOverworldClockTime()`).
- **Writes** dispatch through Brigadier (e.g. `level_set_time` builds the string
  `"time set <ticks>"` and runs it as console). The cost of one extra parse is negligible next
  to the HTTP round trip, and command-based writes are radically more stable across minor
  Minecraft versions — vanilla commands change roughly once every 18 months while the
  underlying API surface churns continuously.

Where the API surface diverges between 1.21.11 and 26.1.x (`Difficulty.getKey()` →
`getSerializedName()`, `ServerLevel.setDayTime` removed in favor of `ServerClockManager`,
`Entity.getTags()` → `entityTags()`, `BlockState.getValues()` switched return type, etc.),
`MinecraftAdapterImpl` uses Stonecutter preprocessor blocks: `//? if mc_gte_26 { … } //?} else
{ /*…*/ //?}`. The `mc_gte_26` boolean constant is defined in `mod-build.gradle.kts` and made
available to Stonecutter's evaluator at preprocessing time.

### Client entrypoint (`McpClientMod`) and the `ClientAccess` seam

The same jar ships a second entrypoint. `McpServerMod` (the `main` entrypoint) is the world server
described above. `McpClientMod` (the `client` entrypoint) runs inside a real, rendered Minecraft
client and serves the **`client`** tool category — read-only inspection: capture the rendered
first-person frame (`view_capture`) and read client-side perception (`sense_*`, `client_status`).
It reuses Transport / Protocol / Config / Compat unchanged; only two things differ:

- **The seam.** Server tools go through `MinecraftAdapter`; client tools go through
  `ClientAccess` — the same interface-returning-DTOs pattern, but for the client. `ClientAccess`
  carries **no `net.minecraft.client.*` types in its signatures**, so `ToolContext` (which now
  holds an optional `ClientAccess`) stays loadable on a dedicated server. The only class that
  imports client/render types is `adapter.client.ClientAccessImpl`, instantiated solely from the
  client entrypoint, so a headless server never classloads it. Likewise the client tool list lives
  in `ClientToolRegistration` (not `ToolRegistration.ALL_TOOL_CLASSES`), keeping the two surfaces
  separate by construction.
- **The thread.** `MinecraftClient` is also a `ReentrantThreadExecutor`, so the same
  `MinecraftMainThreadExecutor` is attached to `Minecraft.getInstance()::execute` instead of
  `MinecraftServer::execute`. Framebuffer reads and world reads marshal onto the client thread the
  same way server work marshals onto the server main thread.

The client endpoint binds on `ClientLifecycleEvents.CLIENT_STARTED` and runs on its own port
(default 8766) and config (`client.json`, env prefix `MCP_CLIENT_*`) so it can coexist with the
world endpoint in a single-player process. See [version-compatibility.md](version-compatibility.md)
for the post-26.1 mappings note on the render/capture symbols.

## Threading model

Minecraft's server runs all world logic on **one main thread**. Touching a world from any other
thread is undefined behavior. The entire design pivots around this constraint.

Every tool handler follows the same pattern:

1. HTTP request arrives on a thread of `HttpTransport`'s fixed-size thread pool.
2. The handler parses arguments and validates schema on that thread.
3. The handler submits world-touching work via
   `context.mainThreadExecutor().submitBlocking(supplier, timeoutMs)`.
4. The work runs on the main thread; the executor completes the future.
5. The handler serializes the DTO result to JSON and writes the response.

Reads obey the same rule. Iterating `level.getAllEntities()` from an HTTP thread is undefined
behavior; you must marshal the read, copy what you need into a DTO, and return.

The `BaseTool.onMainThread(context, fn)` helper encapsulates the pattern and converts the
checked exceptions (`TimeoutException`, `MainThreadWorkException`) into MCP-format error
responses.

### Event delivery

Minecraft events (block break, player join, chat, …) fire on the main thread via Fabric API
callbacks. `EventWiring` registers each callback and pushes the resulting envelope into the
`EventBus`. The bus routes the envelope to every matching subscription's `EventRingBuffer`.

`events_poll` drains a subscription's buffer atomically — the polling thread sees a consistent
snapshot. The buffer is drop-oldest at the configured `event_buffer_size` to prevent unbounded
memory growth when a client stops polling.

## Lifecycle

| Event | What happens |
| --- | --- |
| Mod load (`onInitialize`) | Registers Fabric lifecycle handlers; does NOT bind the HTTP listener. |
| `SERVER_STARTING` | Reads config, captures `McEnvironment`, builds the filtered `ToolRegistry`, instantiates runtime / protocol / transport, installs event wiring. Does not yet bind. |
| `SERVER_STARTED` | Binds the HTTP listener. The endpoint is live. |
| `END_SERVER_TICK` | Reserved hook (currently no-op) for future tick-aligned bookkeeping. |
| `SERVER_STOPPING` | Drains in-flight HTTP requests (up to 5 seconds) and closes the listener. |
| `SERVER_STOPPED` | Unbinds the adapter; detaches the main-thread executor. |

In single-player, the listener is live only while a world is loaded. This is correct behavior —
there's no Minecraft world to talk to between sessions, so the endpoint should be down.

## Why the adapter goes through Brigadier for writes

This is the most "surprising" design choice in the project. The trade-off is:

| | Direct API | Brigadier dispatch |
| --- | --- | --- |
| Performance | ~10–100× faster per call | One extra parse step (~10 μs) |
| API stability across mappings | Different class/method names per mapping | Stable string command grammar |
| API stability across minor versions | Frequently breaks | Vanilla commands change once per 18 months |
| Test surface | Each method is its own integration risk | One command source path, well-trodden |
| Verifiability | Requires running Minecraft | Vanilla commands are documented |

For a tool that the user invokes maybe a dozen times per session, raw performance is irrelevant.
The maintenance cost of direct-API writes — keeping ~100 method calls correct across MC 1.21 ↔
26.1 ↔ future — is enormous, especially when 26.1 dropped or moved many of the setters used in
1.21.x. Brigadier dispatch hides nearly all of that.

The exceptions (where direct API genuinely wins) are reads: scanning a region, iterating a player
list, decoding NBT. Those don't have a command equivalent that returns structured data, so the
adapter uses direct API for them.

If you find a write method that's a real bottleneck — bulk block fills are the obvious one — the
v0.2.0 plan is to add a direct-API fast path behind a feature flag, with the Brigadier path as the
fallback.

## See also

- [docs/tools.md](tools.md) — every tool, its schema, and per-version annotations.
- [docs/security.md](security.md) — threat model.
- [docs/version-compatibility.md](version-compatibility.md) — how Stonecutter and the compat
  filter work together.

# Troubleshooting

## "Tools list is empty in Claude Desktop / Cursor"

1. Check the Minecraft log for `MCP server listening at …`. If absent, the mod did not start —
   check earlier in the log for an exception during `SERVER_STARTING`.
2. `curl http://localhost:8765/healthz` — should return `{"status":"ok"}`. If it doesn't, the
   listener never bound; see [Listener failed to bind](#listener-failed-to-bind) below.
3. Confirm the URL in your client config matches the bind address from the log line
   (`MCP server listening at http://127.0.0.1:8765`).
4. If you've enabled auth, confirm the bearer token matches.

## Listener failed to bind

Common causes:

- **Port already in use.** Default 8765 is rarely in use, but a second world or a non-MCP process
  may have grabbed it. Pick a new port via `MCP_PORT=8770` or `config.json`.
- **Permission denied below port 1024.** On Linux, ports below 1024 require root. Use 8765 or
  similar.
- **Invalid TLS config.** If `tls_cert_path` and `tls_key_path` are partially set or point to
  non-existent / unparseable files, the listener refuses to bind. Either set both to null or fix
  the paths.
- **Non-loopback bind without `allow_remote=true` and `auth_required=true`.** The mod logs an
  explicit message about which flag is missing.

The error always appears in the Minecraft log before the world finishes loading. Search for
`MCP server` to find it.

## Inspection client (`minecraft-java-client`) issues

These apply to the client-side inspection endpoint (default port 8766) served by the client
entrypoint — see [configuration.md](configuration.md#two-mcp-servers-world--inspection).

- **`view_capture` returns the pause / Game menu.** The client opens the pause menu when its
  window loses focus (e.g. when you alt-tab away), and a capture grabs whatever was last rendered.
  `view_capture` defaults to `close_screen: true`, which dismisses the menu and renders a clean
  frame before capturing, so this is normally handled for you. To stop the menu opening on focus
  loss in the first place, toggle **Pause on Lost Focus** off in-game with **F3 + P** (the
  `key.debug.focusPause` debug binding), or set `pauseOnLostFocus:false` in the instance's
  `options.txt`.
- **`view_capture` says "No frame available" or times out.** The client must be in a world and the
  window must **not** be minimized — a minimized window stops rendering, so the GPU readback never
  completes. Keep the window visible (occluded behind another app is fine).
- **The captured PNG is too large.** A large game window produces a large image; over the
  inline-image path some MCP clients cap returned content near ~1 MB. Raise `downscale` (e.g. 3–4)
  or use a smaller window.
- **`minecraft-java-client` shows "failed to connect".** Nothing is serving 8766 yet — that
  endpoint binds only while a real client is running. Launch the client and join a world.

## "Host header '…' not in allowed set"

The MCP client is reaching the listener but its `Host` header doesn't match the bind address.
This is by design — it blocks DNS rebinding (see [docs/security.md](security.md)).

Most common cause: the client URL uses a hostname the mod doesn't expect. On loopback, accepted
values are exactly:

- `localhost:<port>`
- `127.0.0.1:<port>`
- `[::1]:<port>`

If you've named the host something else in `/etc/hosts` (e.g. `myhost.local`), point the client
at `localhost` or `127.0.0.1` instead.

## "Origin '…' not in allowed_origins"

A browser (or browser-style client) reached the endpoint and was rejected because the default
`allowed_origins` is empty. Either:

- Use a non-browser client (Claude Desktop, Cursor — neither sends `Origin`), or
- Add the origin to `allowed_origins` in `config.json` if you really need browser access.

## "401 Unauthorized" with `auth_required=false`

A request had an `Authorization` header but the mod's filter rejected it as a "wrong scheme" or
malformed value. The default-config endpoint doesn't require auth; the filter ignores
`Authorization` entirely when `auth_required: false`. If you're seeing 401, recheck the config
file (especially env-var overrides).

## "Tool X is not registered"

Either:

- The tool depends on a Fabric API module you don't have installed. Check the boot log for
  `Skipping tool 'X': required module 'Y' is not installed`.
- The tool is version-restricted (min or max Minecraft version). The boot log says so explicitly.
- The tool name is misspelled in the client request.

Use `tools/list` to enumerate what's actually registered:

```sh
curl -s -XPOST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## "Main thread work timed out"

A tool blocked the main thread for longer than `command_timeout_ms`. Common causes:

- Bulk operations (`block_fill_region` with a huge box) — split into smaller boxes, or wait for
  v0.2.0's async job mode.
- Server is overloaded (lots of mobs, lag); the tool's main-thread work doesn't get to run within
  the timeout. Reduce the load or raise `command_timeout_ms` (up to 600000 = 10 minutes).
- The tool is deadlocked — investigate the stack trace logged on timeout.

## "Connection refused" after the world loads

The endpoint binds in `SERVER_STARTED`. If the world is still on the loading screen, the
endpoint isn't up yet. Once gameplay starts, the listener is live and `/healthz` responds.

## Mod fails to load with NoSuchMethodError / ClassNotFound

Mapping/API mismatch — you've installed a jar built for a different Minecraft version. Verify the
jar name's `+<mc-version>` suffix matches your Fabric Loader profile's Minecraft version, and that
your Fabric API jar's `+<mc-version>` matches too.

## "command_register: reserved for v0.2.0"

The `command_register` tool exists in the tool list but the handler intentionally throws — custom
slash command registration is a v0.2.0 feature. Use `command_execute` for one-off command
invocations.

## Where to get help

- Open a GitHub issue with the contents of the Minecraft log around `[minecraft_fabric_mcp]`.
- For potential security issues, use GitHub Security Advisories — don't open a public issue.

# Claude Desktop integration

Claude Desktop talks to MCP servers through the
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote) adapter — a small shim that bridges
Claude Desktop's stdio-based MCP entry to a remote HTTP endpoint. (Newer versions of Claude
Desktop also support remote endpoints natively in **Settings → Connectors**; either is fine.)

## Config file location

| OS | Path |
| --- | --- |
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |

If the file doesn't exist, create it. After every change, **quit Claude Desktop fully and
restart it** — restarting only the active conversation isn't enough.

## Default config (localhost, no token)

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

This is the configuration that matches the default mod config (`host=127.0.0.1`,
`auth_required=false`).

## Authenticated config (remote endpoint with bearer token)

```json
{
  "mcpServers": {
    "minecraft-java": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://YOUR-SERVER-HOST:8765/mcp",
        "--header",
        "Authorization:${AUTH_HEADER}"
      ],
      "env": {
        "AUTH_HEADER": "Bearer 9c1f9a…the-rest-of-your-token"
      }
    }
  }
}
```

The token is passed through the `AUTH_HEADER` env var so its space (`Bearer ` + the token) isn't
mangled by JSON argument parsing. Replace `YOUR-SERVER-HOST` with the host running the mod.

## Native connector (Claude Desktop ≥ recent versions)

In **Settings → Connectors → Add**:

- URL: `http://localhost:8765/mcp`
- Auth header (when applicable): `Authorization: Bearer <token>`

No `mcp-remote` shim needed.

## Verifying

After restart, the model's tool tray (🔌 icon in the chat input) lists each `<domain>_<action>`
tool — `server_get_status`, `level_set_time`, etc. Run a quick probe in chat:

> "Use `server_get_status` and tell me what Minecraft version is running."

If the tool list is empty:

1. Check the Minecraft log for `MCP server listening at …`. If absent, the mod didn't start.
2. `curl http://localhost:8765/healthz` should return `{"status":"ok"}` — confirms the listener
   is up.
3. Check Claude Desktop's logs (Help → Logs) for `mcp-remote` errors.
4. If you're using auth, confirm the token in `env.AUTH_HEADER` matches the one logged by the
   mod (or the one in `config/minecraft_fabric_mcp/config.json`).

See [docs/troubleshooting.md](troubleshooting.md) for more.

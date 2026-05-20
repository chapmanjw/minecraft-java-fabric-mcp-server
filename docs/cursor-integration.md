# Cursor integration

Cursor speaks MCP natively. Configure its MCP servers from **Settings → Cursor → Tools & MCP →
Add new MCP server**.

## Default config (localhost, no token)

```json
{
  "mcpServers": {
    "minecraft-java": {
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

## Authenticated config

```json
{
  "mcpServers": {
    "minecraft-java": {
      "url": "http://YOUR-SERVER-HOST:8765/mcp",
      "headers": {
        "Authorization": "Bearer 9c1f9a…the-rest-of-your-token"
      }
    }
  }
}
```

After saving, restart the Cursor MCP servers section (toggle off/on) to force a reconnect.
Cursor's tool palette then lists the `<domain>_<action>` tools.

## Notes specific to Cursor

- Cursor's MCP support uses Streamable HTTP without the `mcp-remote` shim, so the configuration
  is shorter than the Claude Desktop form.
- Cursor occasionally cold-starts the connection; the first tool call after a long idle can be a
  little slower than subsequent calls.
- Cursor logs MCP errors to its developer console (Help → Toggle Developer Tools).

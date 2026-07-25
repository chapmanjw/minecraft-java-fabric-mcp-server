# Dedicated server setup

This walkthrough covers running the MCP server inside a **Fabric dedicated server** — i.e. a
headless Minecraft server that other clients connect to. Use this when:

- You're running a small server for friends and want an MCP agent to help maintain or build in it.
- You want the MCP endpoint to keep running 24/7 without your gaming PC being on.

## Prerequisites

- A Linux or Windows host with the right JDK for your Minecraft target:
  - **Java 21** for the 1.21.11 jar.
  - **Java 25** for the 26.1.1 / 26.1.2 / 26.2 jars.
- About 20 minutes.

## Step 1 — Install Fabric dedicated server

Download the Fabric Server installer from <https://fabricmc.net/use/server/>. Place it in a
dedicated directory and run:

```sh
mkdir -p /opt/fabric-mcp
cd /opt/fabric-mcp
java -jar fabric-server-installer-*.jar server -mcversion 26.1.2 -downloadMinecraft
```

This populates the directory with `fabric-server-launch.jar` and downloads the Minecraft server
jar. First run to generate `server.properties` and the EULA:

```sh
java -Xmx2G -jar fabric-server-launch.jar nogui
```

Accept the EULA: edit `eula.txt` and change `eula=false` to `eula=true`.

## Step 2 — Install the mods

Create the `mods/` folder and drop in two jars (same names you'd use for single-player):

```sh
mkdir -p mods
# Place:
#   mods/fabric-api-<...>+<mc-version>.jar
#   mods/minecraft-fabric-mcp-<modver>+<mc-version>.jar
```

## Step 3 — Configure the MCP server

Create `config/minecraft_fabric_mcp/config.json`. For LAN/internet access with auth:

```json
{
  "host": "0.0.0.0",
  "port": 8765,
  "allow_remote": true,
  "auth_required": true,
  "rate_limit_rpm": 120
}
```

Leave `bearer_token` unset — the mod generates one on first boot and logs it once.

For loopback-only access (Claude Desktop running on the same host as the dedicated server, no
remote clients), keep the defaults from the single-player setup — no config file needed.

## Step 4 — First start

```sh
java -Xmx4G -jar fabric-server-launch.jar nogui
```

In the log you should see the generated bearer token if you enabled auth:

```
[minecraft_fabric_mcp] Generated bearer token for MCP server. Save this value — it is shown only once:
[minecraft_fabric_mcp]   Authorization: Bearer 9c1f9a…
```

Copy that token; you'll need it in Claude Desktop.

## Step 5 — Run as a service

### Linux (systemd)

Create a dedicated unprivileged user and hand it the install:

```sh
sudo useradd --system --no-create-home --shell /usr/sbin/nologin minecraft
sudo chown -R minecraft:minecraft /opt/fabric-mcp
```

Create `/etc/systemd/system/fabric-mcp.service`:

```ini
[Unit]
Description=Fabric Minecraft server with MCP mod
After=network.target

[Service]
Type=simple
User=minecraft
Group=minecraft
WorkingDirectory=/opt/fabric-mcp
ExecStart=/usr/bin/java -Xmx4G -jar fabric-server-launch.jar nogui
Restart=on-failure
RestartSec=10
StandardInput=null
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start:

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now fabric-mcp
sudo systemctl status fabric-mcp
journalctl -u fabric-mcp -f
```

### Windows (NSSM)

Install [NSSM](https://nssm.cc/) — drop `nssm.exe` somewhere on `PATH`.

```powershell
nssm install FabricMcp "C:\Program Files\Java\jdk-21\bin\javaw.exe" -Xmx4G -jar fabric-server-launch.jar nogui
nssm set FabricMcp AppDirectory "C:\fabric-mcp"
nssm set FabricMcp AppStdout "C:\fabric-mcp\logs\nssm-stdout.log"
nssm set FabricMcp AppStderr "C:\fabric-mcp\logs\nssm-stderr.log"
nssm start FabricMcp
```

Stop with `nssm stop FabricMcp` or `Stop-Service FabricMcp`.

## Step 6 — Connect Claude Desktop

Edit `claude_desktop_config.json` (paths in [docs/claude-desktop-integration.md](claude-desktop-integration.md)):

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
        "AUTH_HEADER": "Bearer 9c1f9a…"
      }
    }
  }
}
```

Replace `YOUR-SERVER-HOST` with the dedicated server's hostname or IP. The bearer token is passed
through an env var so its space (`Bearer <space>token`) isn't mangled by argument parsing.

Quit and reopen Claude Desktop. The tools appear once it reconnects.

## TLS

For internet-facing deployments, use TLS. Either configure the mod directly with `tls_cert_path`
and `tls_key_path`, or terminate TLS at a reverse proxy (Caddy / Traefik / nginx) and have the
mod listen on plain HTTP bound to `127.0.0.1`. See [docs/security.md](security.md) for the
trade-offs.

## Backups

Treat the world directory and `config/minecraft_fabric_mcp/config.json` (which holds the bearer token) as
sensitive. Back up the world, but skip the bearer token in backups you share — rotate it by
deleting `bearer_token` from the config and restarting.

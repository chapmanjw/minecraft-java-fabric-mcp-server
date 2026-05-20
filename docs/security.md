# Security model

## Threat model

The MCP endpoint exposes the full Minecraft world to whoever can talk to it. The default deployment
binds to `127.0.0.1` with no token because that's safe enough for the dominant case (single-player
on a personal machine), but anything beyond that needs explicit opt-in.

Threats considered:

| # | Threat | Default-config defense |
| --- | --- | --- |
| 1 | Process on the same machine connects and grants itself creative items / explodes the world | Acceptable — same-machine processes already control the user account. |
| 2 | Malicious website opens `http://localhost:8765/mcp` via `fetch()` and sends commands | `Origin` header check rejects browser requests. |
| 3 | Malicious website uses DNS rebinding to talk to `localhost` from a non-local origin | `Host` header check rejects requests not addressed to `localhost:8765` / `127.0.0.1:8765` / `[::1]:8765`. |
| 4 | Attacker on the same LAN talks to the listener | Default `host=127.0.0.1` doesn't accept LAN connections. |
| 5 | Attacker on the public internet talks to the listener | Same as above. |
| 6 | Attacker captures network traffic and replays | Default loopback has no network path. Non-loopback deployments must enable TLS. |
| 7 | Attacker brute-forces the bearer token | 32-byte (256-bit) hex token + constant-time compare. |

## Default posture

- `host: "127.0.0.1"`, `port: 8765` — loopback only.
- `auth_required: false` — no token.
- `allowed_origins: []` — reject any request that carries an `Origin` header.
- `allow_remote: false` — refuse to start with a non-loopback `host` unless this is true.

The combination of points (1) and (3) above is the load-bearing design: the default is safe even
though it has no token, because the only way to reach the listener is from a same-machine process
that doesn't go through a browser. Browsers either don't send `Origin` (legitimate non-browser
clients, allowed) or send an `Origin` that fails the allow-list (rejected).

## DNS rebinding in detail

DNS rebinding is the canonical attack against unauthenticated `localhost` HTTP services:

1. Attacker registers `attacker.example.com` with their own DNS.
2. Victim visits a page on `attacker.example.com`.
3. The page tells the browser to `fetch("http://attacker.example.com:8765/mcp", …)`.
4. Attacker's DNS first returns the public IP (passing browser same-origin); then after a low
   TTL elapses, returns `127.0.0.1` for the same host. The browser now talks to the victim's
   local MCP listener while still considering it the same origin.
5. Without a defense, the listener responds — same browser → no `Origin` mismatch.

We block this at step 4: the browser sends `Host: attacker.example.com:8765`, but our
`SecurityFilter` only accepts `Host: localhost:8765` (or `127.0.0.1:8765`, or `[::1]:8765`) on
loopback binds. The request is rejected with 403.

## Origin header policy

The `Origin` header is sent automatically by browsers on cross-origin requests. Legitimate
non-browser MCP clients (`mcp-remote`, Cursor's MCP integration) do not send it.

Default `allowed_origins: []` rejects every request that carries `Origin` — i.e. every browser
cross-origin call. To grant access to a specific web page (advanced use only), add its origin to
the list:

```json
{ "allowed_origins": ["https://myapp.example.com"] }
```

This is rarely the right answer. Prefer connecting through a server-side proxy that strips the
`Origin` header.

## Bearer authentication

`auth_required: true` enables `Authorization: Bearer <token>` checks on every request except
`/healthz`.

Token lifecycle:

1. If `bearer_token` is set in config, the mod uses it.
2. If `bearer_token` is null but `auth_required: true`, the mod generates a fresh 32-byte hex
   token at startup, writes it back to the config file (tightening file permissions to user-only
   on POSIX), and logs the value once at INFO.

```
[minecraft_fabric_mcp] Generated bearer token for MCP server. Save this value — it is shown only once:
[minecraft_fabric_mcp]   Authorization: Bearer 8f3c…
```

Token validation uses `ConstantTimeEquals.equals(...)`: a length-aware compare loop that XORs
every byte. The running time is independent of how much of the supplied token matches.

## Non-loopback binding

To accept connections from outside `localhost`, set `host` to `0.0.0.0` (any local interface) or
a specific LAN address, AND:

- `allow_remote: true` — required explicit opt-in.
- `auth_required: true` — required, the mod refuses to start otherwise.

The combination prevents the most common foot-gun: deploying a dedicated server with the default
config thinking it's localhost-only.

## Rate limiting

`rate_limit_rpm` caps requests per minute per client. Bucket key:

- If `auth_required: true`: first 16 chars of the token (we don't log the full token).
- Otherwise: remote IP address.

The limiter is token-bucket; idle clients accumulate burst capacity up to `rate_limit_rpm`.
Excess requests get `HTTP 429` with a `Retry-After: 60` hint.

## TLS

For non-loopback deployments, use TLS. Either configure the mod with `tls_cert_path` and
`tls_key_path` (both must be set, or both null), or terminate TLS at a reverse proxy and run the
mod on plain HTTP bound to `127.0.0.1`.

The mod's TLS support is intentionally simple (PEM cert + PKCS8 key). For anything more
sophisticated — mTLS, custom cipher suites, OCSP stapling — use a reverse proxy.

## Body size and command timeout

`max_body_bytes` defaults to 16 MiB. The transport caps reads at `max_body_bytes + 1`, so a
malicious sender can't stream forever before the size check fires.

`command_timeout_ms` (default 15 seconds) bounds how long a tool can wait for main-thread work
before the executor abandons it. Tools that need to do bulk work should use the
`AsyncJobRegistry` pattern (planned for v0.2.0) instead of long blocking calls.

## Reporting a vulnerability

Use GitHub Security Advisories — see [SECURITY.md](../SECURITY.md). Don't open a public issue.

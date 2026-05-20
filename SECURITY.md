# Security

## Reporting a vulnerability

**Do not** open a public GitHub issue for security problems. Instead, use
[GitHub Security Advisories](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server/security/advisories/new)
on this repository. That keeps the discussion private until a fix lands and a coordinated
disclosure window can be arranged.

## What's in scope

- The MCP HTTP listener — Host / Origin / bearer validation, rate limiting, TLS, body size.
- The MCP dispatcher and tool handlers — input validation, structured error reporting,
  authorization checks.
- The Minecraft adapter — anything that could allow an authenticated MCP client to corrupt world
  state in ways a vanilla operator command couldn't.
- The configuration loader — anything that could leak the bearer token, allow path traversal,
  or accept a config that the documented validation rules say should be refused.
- The Gradle build — supply-chain considerations: pinned dependency versions, signed artifacts.

## What's not in scope

- Vulnerabilities in upstream dependencies (Minecraft, Fabric Loader, Fabric API, Jackson) —
  report those to their respective projects.
- Attacks that require a user to install a malicious mod next to this one. Mod-loading is a
  trusted-code-execution boundary by definition.
- Resource exhaustion on a single-player deployment with the default config (loopback only).
  Same-machine processes already control the user account.
- Vulnerabilities in MCP clients (Claude Desktop, Cursor, `mcp-remote`) — report those to those
  projects.

## Severity ratings

We follow [CVSS 3.1](https://www.first.org/cvss/calculator/3.1) for severity. Typical mappings:

| Score | Example |
| --- | --- |
| Critical (≥9.0) | Pre-auth RCE in the mod. Bearer token leak through a public endpoint. |
| High (7.0–8.9) | DNS-rebinding bypass. Constant-time-equals regression that's exploitable in practice. |
| Medium (4.0–6.9) | Information disclosure that an authenticated MCP client should not have. Resource exhaustion that crashes the Minecraft server. |
| Low (<4.0) | Denial of MCP service (the world itself remains up). |

## Response targets

| Severity | Acknowledge | Patch + advisory |
| --- | --- | --- |
| Critical | 24 hours | 7 days |
| High | 72 hours | 14 days |
| Medium | 7 days | 30 days |
| Low | 14 days | Best effort |

## Disclosure

After a patched release ships, we publish a GitHub Security Advisory with credit (unless the
reporter prefers anonymity), and we cross-link from the release notes.

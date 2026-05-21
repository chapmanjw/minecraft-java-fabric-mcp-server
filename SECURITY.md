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

## Build-time vs. shipped dependencies

This mod is a single Fabric jar. The only third-party code it bundles is **Jackson** (shaded via
Loom's `include` configuration); everything else it needs at runtime — Minecraft, the Fabric Loader,
the Fabric API, SLF4J — is provided by the host game, not packaged by us.

The Gradle build, by contrast, pulls in a large transitive tree through the **Fabric Loom** and
**Stonecutter** plugins: netty, lz4-java, jgit, plexus-utils, log4j-core, commons-* and friends.
These are **build-time tooling only** — they run on a maintainer's machine or in CI to download,
remap, and package Minecraft. **None of them are bundled in or reachable from the published mod
jar** (you can confirm: `unzip -l minecraft-fabric-mcp-*.jar` lists no `netty`, `log4j`, `jgit`,
etc.).

Consequently:

- We treat dependency vulnerabilities by **where the code runs**, not merely by their presence in
  the dependency graph. A CVE in a build-classpath transitive dependency of a Gradle plugin is **not
  a vulnerability in the shipped mod** and poses no risk to anyone running it. We dismiss such
  Dependabot alerts as *tolerable risk* with a note pointing here, and we rely on upstream plugin
  releases to advance those transitives.
- Dependabot version updates are scoped to **direct** dependencies (see
  [`.github/dependabot.yml`](.github/dependabot.yml)) — the deps we actually declare and pin.
- A CVE in a dependency we **ship** (today: Jackson) or that runs as part of the **mod's runtime**
  is in scope and handled per the response targets below.

If you believe a build-time dependency is actually reachable from the published artifact (e.g. we
started shading something new), that's a real finding — please report it.

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

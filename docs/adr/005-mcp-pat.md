# ADR-005: Model Context Protocol (MCP) & Personal Access Token Authentication

- **Status:** Accepted (M8)

## Context
AI assistants (Claude Desktop, Cursor IDE, Claude Code) require an interface to query and modify calendars on behalf of users. Interactive web authentication (short-lived JWTs, OAuth browser redirects, cookies) is incompatible with background AI processes running on developer desktops.

## Decision
1. **Embedded MCP Server:**
   - Implement the Model Context Protocol directly inside the Spring Boot backend (`McpController` and `McpService`), compliant with specification version `2024-11-05`.
   - Support both **Streamable HTTP JSON-RPC 2.0** (`POST /api/mcp`) and **Server-Sent Events (SSE)** (`GET /api/mcp/sse`).
2. **Personal Access Tokens (PATs):**
   - Provide user-managed API tokens generated in the web application's Settings modal.
   - Format: 32 bytes of secure random entropy with `zoner_pat_` prefix.
   - Plaintext tokens are returned to the user exactly once and never stored in the database.
   - Tokens are hashed with SHA-256 before persistence in the `personal_access_tokens` table.
   - Support prefix masking (e.g. `zoner_pat_4f2b...`), configurable expiration dates, and immediate revocation.
3. **Multi-Token Authentication Filter:**
   - `JwtAuthenticationFilter` inspects incoming `Authorization: Bearer` headers: if prefixed with `zoner_pat_`, it authenticates via `TokenService`; otherwise, it validates the JWT.

## Consequences
- Frictionless integration with Claude Desktop, Cursor, and Claude Code using standard CLI command wrappers (`mcp-remote`).
- Database compromise does not leak plaintext PAT tokens.
- Immediate revocation capability for compromised tokens.

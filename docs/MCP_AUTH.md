# Model Context Protocol (MCP) Authentication & Architecture

This document describes the authentication flow, security model, and tool integration architecture for Zoner's Model Context Protocol (MCP) server.

---

## 1. Overview

Zoner exposes a **Model Context Protocol (MCP)** server enabling AI assistants—such as **Claude Desktop**, **Cursor**, and **Claude Code**—to interact with calendars, events, and schedules on behalf of authenticated users.

All AI interactions are mediated through **Personal Access Tokens (PATs)** and the standard MCP protocol version `2024-11-05`.

---

## 2. Authentication Flow

```
+----------------+          +-------------------+          +-----------------------+
|   AI Client    |          |   Zoner Backend   |          |    PostgreSQL (Neon)  |
| (Claude/Cursor)|          | (JwtAuthFilter)   |          | (personal_access_tok) |
+--------+-------+          +---------+---------+          +-----------+-----------+
         |                            |                                |
         |  POST /api/mcp             |                                |
         |  Auth: Bearer zoner_pat_.. |                                |
         |--------------------------->|                                |
         |                            |  SHA-256(token)                |
         |                            |  Lookup hash                   |
         |                            |------------------------------->|
         |                            |<-------------------------------|
         |                            |  Check:                        |
         |                            |  - exists                      |
         |                            |  - !revoked                    |
         |                            |  - not expired                 |
         |                            |                                |
         |                            |  Record last_used_at           |
         |                            |  Resolve UserPrincipal         |
         |                            |  Set in SecurityContext        |
         |                            |                                |
         |                            |  Execute McpService with       |
         |                            |  authenticated user.getId()    |
         |  JSON-RPC 2.0 Response     |                                |
         |<---------------------------|                                |
```

### Step-by-Step Breakdown:
1. **Token Generation**:
   - The user opens the web application and navigates to **User Menu → Settings & MCP → Generate Token**.
   - The user assigns a descriptive name (e.g. `"Cursor IDE"`, `"Claude Desktop"`) and chooses an optional expiration window (e.g., 30 days, 90 days, 1 year, or no expiration).
   - The backend generates 32 bytes of cryptographically secure random entropy formatted as 64 hex characters with the prefix `zoner_pat_`:
     `zoner_pat_4f2b91c...`
   - The token is hashed with **SHA-256**:
     `SHA-256("zoner_pat_...") -> 64-char hex digest`
   - The **plaintext token is returned exactly once** to the client. Only the SHA-256 hash and a masked prefix (e.g., `zoner_pat_4f2b...`) are persisted in the database.
2. **Client Authorization**:
   - The MCP client connects using standard HTTP or Server-Sent Events (SSE), supplying the HTTP header:
     `Authorization: Bearer zoner_pat_<token>`
3. **Filter Resolution (`JwtAuthenticationFilter`)**:
   - The authentication filter detects the `zoner_pat_` prefix.
   - It hashes the incoming bearer token using SHA-256 and queries `personal_access_tokens` by `token_hash`.
   - It verifies that the token is **not revoked** (`revoked == false`) and **not expired** (`expires_at == null || expires_at > now`).
   - It records the current timestamp in `last_used_at` for auditability.
   - It resolves the associated `User`, constructs a `UserPrincipal`, and populates the Spring `SecurityContextHolder`.
4. **Service & Policy Enforcement**:
   - No MCP tool accepts a `userId` argument. Identity is derived strictly from the authenticated principal in `SecurityContext`.
   - Tool execution flows through the same domain services (`EventService`, `CalendarService`, `AvailabilityService`) and obeys the central `AccessPolicy` security gate.

---

## 3. Security Principles & Anti-Enumeration

- **Zero Secret Storage**: Plaintext tokens are never stored in the database or written to application logs. Database compromises expose only one-way SHA-256 digests.
- **Identity Isolation**: All calendar and event access is strictly scoped to the authenticated user and explicitly shared calendars.
- **Anti-Enumeration (404 Not Found)**:
  - When an AI client requests an event or calendar that belongs to another user (without a share grant), the backend responds with **404 Not Found**, never 403 Forbidden.
  - This prevents malicious or inquisitive agents from confirming the existence of resources across accounts.
- **Explicit View-Only Protection**:
  - Attempting to schedule an event on a shared calendar with `VIEW` permission returns an informative permission rejection:
    *"Cannot create event on calendar 'Team Calendar'. You only have view-only access."*
- **Instant Revocation**:
  - Revoking a token in the Settings UI immediately invalidates all subsequent calls using that token.

---

## 4. Supported Calendar Tools (9 AI Tools)

| Tool Name | Type | Description |
|---|---|---|
| `list_calendars` | Read | Lists accessible calendars (owned & shared) with permissions, IDs, and colors. |
| `create_calendar` | Write | Creates a new calendar with name, description, and color. |
| `list_events` | Read | Range query (`start`, `end`, optional `calendarIds`) expanding all recurrences. |
| `get_event` | Read | Full event inspection by ID. |
| `create_event` | Write | Creates an event. Accepts calendar by name or ID (defaults to default calendar). |
| `update_event` | Write | Updates an event. Supports `editMode`: `THIS`, `THIS_AND_FOLLOWING`, `ALL`. |
| `delete_event` | Destructive | Deletes an event or recurrence occurrence. Prompted as destructive for safety. |
| `search_events` | Read | Fuzzy search across titles, descriptions, and locations. |
| `check_availability` | Read | Checks proposed window; returns exact conflicting events and calendars if busy. |

---

## 5. Client Configuration Snippets

> [!TIP]
> **Local vs Production URLs**:
> - **In Local Dev**: The server runs at `http://localhost:8080` (or `http://localhost:5173` via Vite proxy).
> - **After Deployment**: Replace `http://localhost:8080` with your deployed domain (e.g., `https://zoner.onrender.com`).
> - Inside Zoner's **Settings & MCP** dialog in the browser, the config snippets automatically adapt to your live production domain in real time.

### A. Cursor (`.cursor/mcp.json`)
```json
{
  "mcpServers": {
    "zoner-calendar": {
      "url": "http://localhost:8080/api/mcp",
      "headers": {
        "Authorization": "Bearer zoner_pat_YOUR_TOKEN_HERE"
      }
    }
  }
}
```

### B. Claude Desktop (`claude_desktop_config.json`)
Claude Desktop supports remote HTTP MCP servers via `mcp-remote`:
```json
{
  "mcpServers": {
    "zoner-calendar": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://localhost:8080/api/mcp/sse",
        "--header",
        "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE"
      ]
    }
  }
}
```

### C. Claude Code
```bash
claude mcp add --transport http zoner-calendar http://localhost:8080/api/mcp --header "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE"
```

### D. Direct cURL Smoke Test
```bash
# 1. Initialize MCP
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# 2. List Tools
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# 3. Call Check Availability
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer zoner_pat_YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "check_availability",
      "arguments": {
        "start": "2026-10-05T14:00:00Z",
        "end": "2026-10-05T15:00:00Z"
      }
    }
  }'
```

---

## 6. Production Roadmap: OAuth 2.1 with PKCE

For multi-tenant SaaS deployments where end-users shouldn't handle raw tokens, Zoner's architecture is ready to extend from PATs to **OAuth 2.1 with Authorization Code + PKCE**:
- MCP client redirects browser to `GET /oauth2/authorize?client_id=...&code_challenge=...`
- User approves calendar read/write scopes.
- Client exchanges code at `POST /oauth2/token` for scoped short-lived access tokens and refresh tokens.

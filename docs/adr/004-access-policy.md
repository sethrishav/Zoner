# ADR-004: Centralized Authorization & Anti-Enumeration Security

- **Status:** Accepted (M2)

## Context
Authorization rules govern who can view, create, edit, or delete calendars and events. In a dual-access architecture where both human users (via web REST API) and AI agents (via MCP JSON-RPC) interact with domain services, authorization logic must not drift between protocols. Furthermore, error responses must not leak information about private resources to unauthorized users.

## Decision
1. **Single Source of Truth (`AccessPolicy`):**
   - All authorization rules are encapsulated in a central `AccessPolicy` bean.
   - REST controllers and MCP tool adapters call this exact same policy layer before delegating to repositories.
2. **Anti-Enumeration via `404 Not Found`:**
   - When User A requests or attempts to mutate a calendar or event owned by User B without share permissions, the system returns `404 Not Found` (never `403 Forbidden`).
   - Returning `403` informs an attacker that the resource exists, enabling ID scanning. Returning `404` preserves confidentiality.
3. **Explicit `403 Forbidden` for Read-Only Collaborators:**
   - If User A has `VIEW` permission on a shared calendar, User A already knows the calendar exists. If User A attempts a mutating operation (create, edit, delete), the system explicitly returns `403 Forbidden`.

## Consequences
- Impossible for MCP AI tools to bypass web application authorization rules.
- Defense against ID enumeration attacks across all endpoints.

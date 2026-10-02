# AI Usage

This file is a running log, updated as work happens (not reconstructed at the end).

## Tools used
| Tool | Used for |
|---|---|
| Claude (claude.ai chat) | Planning, architecture and data-model design, scaffolding code, review of design decisions |
| (add others: Cursor, Claude Code, Copilot, ...) | |

## What AI generated vs what I reviewed or changed
Be specific. For every milestone, record: what was generated, what I read line by line, what I changed, and why.

| Milestone | AI-generated | Reviewed / changed by me | Notes |
|---|---|---|---|
| Planning | Initial engineering plan (PLAN.md), ADR drafts | Chose the stack, the time model and the free-tier hosting approach | Decisions are in `docs/adr/` |
| M0 | Project skeleton, error model, correlation-id filter, baseline tests | Reviewed all error handling classes, added missing `.github/workflows/ci.yml`, fixed `.gitignore` to prevent tracking build targets and OS files, added Swagger UI test assertion in `ApplicationSmokeTest`, and ran the full test suite with Testcontainers against Docker PostgreSQL 16 | All 16 tests passing, `/healthz`, `/healthz/liveness`, `/swagger-ui.html`, and `/v3/api-docs` verified. |

## Notable prompts and workflows
- Gave the AI the assignment brief and my CV, asked for a senior-level plan with milestones and "done when" criteria, then worked milestone by milestone.
- Ran Testcontainers against PostgreSQL 16 Alpine container locally to verify database migrations and health probes before writing domain logic.

## Things I verified myself
- Verified Docker daemon connectivity and PostgreSQL 16 container health.
- Ran `./mvnw test` locally; verified Flyway migration execution, `CorrelationIdFilter`, `GlobalExceptionHandler`, Swagger UI 302->200 redirect, and Actuator `/healthz`.
- Verified `.github/workflows/ci.yml` syntax for GitHub Actions automated build and test runs.

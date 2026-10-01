# ADR-001: Technology stack

- **Status:** Accepted (M0)

## Context
The brief requires a Java backend, a relational database, a public deployment and an MCP server.
Constraints we chose: zero hosting cost, one reviewer-friendly URL, and a codebase one person can explain end to end.

## Decision
| Area | Choice |
|---|---|
| Language / runtime | Java 21 (brief requires 17+) |
| Framework | Spring Boot **3.5.x**, Spring Data JPA, Spring Security (from M1) |
| Database | PostgreSQL 16 (Docker locally, Testcontainers in tests, Neon free tier in production) |
| Migrations | Flyway. Hibernate runs with `ddl-auto: validate` so the schema is owned by SQL migrations |
| API docs | springdoc-openapi 2.8.x (Swagger UI) |
| Frontend | React (JavaScript) + Vite, built in M6 |
| Packaging | One Docker image serving API and SPA (M9) |

## Why Spring Boot 3.5 and not 4.0
Spring Boot 4.0 is available, with springdoc 3.0.x as its matching line. We chose 3.5.x for M0 because
the surrounding ecosystem (springdoc 2.8, Testcontainers integration, the MCP server libraries) is best
proven on it, and a stable base matters more than novelty for a time-boxed build.
**Follow-up:** before M8 we check which Boot line the MCP server library supports. If it needs Boot 4,
we upgrade then; the code in `common/` and the migrations are unaffected by that move.

## Consequences
- Tests need Docker (Testcontainers). CI uses GitHub's ubuntu runners, which include it.
- Free-tier hosting shapes some choices (small JVM heap, catch-up reminders). See PLAN.md section 12.

# Zoner

> A calendar that never loses track of time zones. _(Working tagline. Final product copy is decided in M6.)_

Zoner is a calendar platform with Month/Week/Day views, recurring events, shared calendars, reminders and an
MCP server so AI assistants can read and manage your calendar.

**Status:** M0 (foundations). See [docs/PLAN.md](docs/PLAN.md) for the roadmap and
[docs/adr/](docs/adr/) for design decisions.

## Repository layout
```
backend/    Java 21, Spring Boot 3.5, PostgreSQL, Flyway
frontend/   React (JavaScript) + Vite   (added in M6)
docs/       Plan, architecture decision records
```

## Prerequisites
- JDK 21
- Maven 3.9+ (the Maven wrapper is generated once, see below)
- Docker (for local Postgres and for the integration tests)

## Local development

### 1. Start PostgreSQL
```bash
cp .env.example .env          # optional; defaults work without it
docker compose up -d db
```

### 2. Run the backend
```bash
cd backend
mvn -N wrapper:wrapper        # one time: creates mvnw so others do not need Maven installed
./mvnw spring-boot:run        # or: mvn spring-boot:run
```
The `dev` profile is active by default and connects to the compose database.

### 3. Check it works
| URL | Expected |
|---|---|
| http://localhost:8080/healthz | `{"status":"UP"}` |
| http://localhost:8080/healthz/liveness | `{"status":"UP"}` (does not touch the database) |
| http://localhost:8080/swagger-ui.html | Swagger UI for the Zoner API |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/api/nothing | 404 in the standard error shape, with a `traceId` |

### 4. Run the tests
```bash
cd backend
./mvnw verify                 # needs Docker running (Testcontainers starts a real Postgres)
```

## Configuration
All configuration comes from environment variables. See [.env.example](.env.example). No secrets are committed.

| Variable | Purpose | Default (dev only) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` or `prod` | `dev` |
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5433/zoner` |
| `DB_USER` / `DB_PASSWORD` | Database credentials | local compose values |
| `DB_POOL_SIZE` | Connection pool size | `5` |
| `PORT` | HTTP port | `8080` |

## API error format
Every error uses one JSON shape, so the UI can show friendly messages from the `code` field:
```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "We could not find what you asked for.",
  "traceId": "3f1c...",
  "timestamp": "2026-10-02T10:15:30Z"
}
```
Validation errors add a `details` array of `{ field, message }`.

_Architecture diagram, deployment and MCP connection instructions are added in later milestones._

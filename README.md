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
- Maven 3.9+ (the Maven wrapper `./mvnw` is included)

## Local development

### 1. Configure the database
Copy `.env.example` to `.env` and fill in your deployed PostgreSQL connection details (e.g. Neon free Postgres):
```bash
cp .env.example .env
```
Edit `.env`:
```bash
DB_URL=jdbc:postgresql://<HOST>:<PORT>/<DATABASE>?sslmode=require
DB_USER=<USER>
DB_PASSWORD=<PASSWORD>
DB_POOL_SIZE=5
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

### 2. Run the backend
```bash
cd backend
./mvnw spring-boot:run
```
The backend automatically loads `.env`, applies Flyway migrations to your deployed PostgreSQL database, and starts the API on port 8080.

### 3. Check it works
| URL | Expected |
|---|---|
| http://localhost:8080/healthz | `{"status":"UP"}` |
| http://localhost:8080/healthz/liveness | `{"status":"UP"}` (fast probe, does not ping DB) |
| http://localhost:8080/swagger-ui.html | Swagger UI for the Zoner API |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |
| http://localhost:8080/api/nothing | 404 in the standard error shape, with a `traceId` |

### 4. Run the tests
```bash
cd backend
./mvnw verify
```

## Configuration
All configuration comes from environment variables or the root `.env` file. See [.env.example](.env.example). No secrets are committed.

| Variable | Purpose | Example |
|---|---|---|
| `DB_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://ep-...-pooler...neon.tech/neondb?sslmode=require` |
| `DB_USER` / `DB_PASSWORD` | Database credentials | (from your Neon console) |
| `DB_POOL_SIZE` | Connection pool size | `5` |
| `PORT` | HTTP port | `8080` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins | `http://localhost:5173,https://your-frontend.vercel.app` |

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

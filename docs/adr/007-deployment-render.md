# ADR-007: Zero-Cost Cloud Deployment on Render & Neon

- **Status:** Accepted (M9)

## Context
The project requires a publicly deployed production URL at ₹0 monthly cost while providing high availability, independent CI/CD triggers, and robust memory management.

## Decision
1. **Separated Deployments:**
   - **Frontend:** Render Static Site (`zoner-6uv2.onrender.com`), serving pre-compiled React 18 production assets from edge CDN caches with SPA rewrite rules (`/* -> /index.html`).
   - **Backend:** Render Web Service (`zoner-backend.onrender.com`), running a containerized Spring Boot 3.5 application on Java 21 Alpine.
   - **Database:** Neon Serverless PostgreSQL 16 with automated connection pooling and SSL encryption.
2. **Infrastructure as Code (Render Blueprint):**
   - Configured in root `render.yaml` declaring the web service, static site, environment variables, health check paths (`/healthz`), and build commands.
3. **JVM Memory Tuning for 512 MB Free Tier:**
   - Configured in `backend/Dockerfile`:
     `-Xms128m -Xmx350m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m`
   - Using Serial GC prevents garbage collector multithreading overhead, ensuring memory stays strictly within the 512 MB container ceiling.
4. **Idempotent Data Seeder:**
   - Implemented `DataSeeder.java` to automatically provision reviewer accounts (`demo@zoner.app` and `colleague@zoner.app`) and sample shared calendars on initial boot.

## Consequences
- Zero hosting costs across all environments.
- Completely isolated frontend and backend deployment pipelines.
- Instant evaluation for reviewers without manual database setup.

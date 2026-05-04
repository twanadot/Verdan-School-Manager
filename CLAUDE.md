# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## Project: Verdan Skolesystem

Java 21 / Maven backend exposing a Javalin REST API backed by Hibernate + MySQL, plus a React + Vite web client in `verdan-web/`.

### Common commands

Backend (run from repo root):
- `mvn compile exec:java` — start the REST API. Main class is `no.example.verdan.api.ApiServer` (configured via `exec.mainClass` in `pom.xml`).
- `mvn package` — builds jar and copies runtime deps to `target/lib/` (used by `Dockerfile`).
- `mvn test` — runs JUnit 5 / Mockito / RestAssured tests. Note: `src/test/java` does not currently exist in the tree, so `mvn test` is a no-op until tests are added back.

Frontend (run from `verdan-web/`):
- `npm install` then `npm run dev` — Vite dev server.
- `npm run build` — production bundle (output to `verdan-web/dist/`).

Docker:
- `docker-compose up -d --build` — builds and runs `verdan-api` and `verdan-web`. The committed compose file points `DB_HOST` at an external AWS RDS MySQL instance with credentials baked into the file; the local `mysql-db` service is commented out. If you need a local DB, uncomment that block and override `DB_HOST=mysql-db`.

### Database / persistence

- JPA unit `verdanPU` defined in `src/main/resources/META-INF/persistence.xml`. Entities must be explicitly listed there (`<exclude-unlisted-classes>true</exclude-unlisted-classes>`) — adding a new `@Entity` class also requires adding a `<class>` line, otherwise Hibernate ignores it.
- Connection is configured via env vars with defaults: `DB_HOST` (localhost), `DB_USER` (root), `DB_PASS` (secret), database `verdan_db` on port 3306. Schema is auto-managed by `hibernate.hbm2ddl.auto=update` — no migration tool.
- `no.example.verdan.app.DataSeeder` seeds demo users (admin/admin123, teacher/teacher123, student/student123) and other reference data on startup.

### Architecture

Layered backend under `no.example.verdan`:
- `model/` — JPA entities (the source of truth for the schema, given `hbm2ddl=update`).
- `dao/` — per-entity data access; all DAOs extend `BaseDao` which owns the shared `EntityManagerFactory` for unit `verdanPU`. Use `BaseDao.inTx(...)` / `read(...)` rather than opening EMs directly.
- `service/` — business logic, validation, error mapping.
- `dto/` — request/response shapes; do not leak entities across the API boundary.
- `api/` — Javalin controllers + `ApiServer` (route wiring, CORS, OpenAPI, WebSocket setup), `AuthMiddleware` (JWT), `MetricsMiddleware`. Controller surface is broader than the README documents — current modules include users, subjects, grades, attendance, rooms, bookings, programs, admissions, applications, institutions, portal (announcements/files/submissions), files, chat (REST + `ChatWebSocket`), and promotion.
- `auth/` — password hashing (BCrypt) and session helpers.
- `security/` — JWT signing/verification, input validation, rate limiting.
- `util/` — shared helpers.

Auth flow: `POST /api/login` issues a short-lived JWT access token + refresh token; `AuthMiddleware` validates `Authorization: Bearer <token>` on protected routes and enforces role checks (ADMIN / TEACHER / STUDENT). Refresh via `POST /api/auth/refresh`.

Chat uses Javalin WebSockets (`ChatWebSocket`) alongside the REST `ChatApiController`; both share the same JPA entities (`ChatRoom`, `ChatMessage`, `ChatMember`, `ChatAttachment`, `ChatReaction`).

### Repo notes / drift to be aware of

- The JavaFX desktop client was removed; the project is web-only now (React in `verdan-web/` against the Javalin API). The README still references it (`fx/Launcher.java`, `mvn javafx:run`) — that text is stale. Do not reintroduce JavaFX code, dependencies, or the `fx/` package.
- The committed `docker-compose.yml` contains plaintext AWS RDS credentials; flag before pushing changes that would expose them further (e.g. publishing the repo).
- macOS resource-fork files (`._*`) appear as deleted in `git status` — those are AppleDouble metadata, not real source files; ignore them.

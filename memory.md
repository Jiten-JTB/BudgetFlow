# Budget Flow — Project Memory

Durable rules and decisions. Read before any work.

## Working Rules
- Answers: short and precise.
- **User runs all git commands.** Never commit/push/branch.
- **Test-first:** unit + integration tests before implementation.
- Every database: `db/schema.sql` (source of truth) + `db/reset.sql` (drops whole schema).
- Use Spring Boot wherever a service can use it.
- Enterprise grade: security, observability, idempotency, audit, coverage gates.
- Web first; Android/iOS later (keep APIs & TS packages platform-neutral).

## Key Decisions
- Microservices, Saga = **Orchestration** (`orchestrator-service`).
- Java 25 + Spring Boot 4.1, Maven multi-module (`./mvnw`).
- PostgreSQL (DB per service), RabbitMQ, Redis.
- Outbox/Inbox for messaging reliability.
- Money = `NUMERIC(19,4)` + ISO currency, `BigDecimal`; IDs = UUIDv7.
- Web: **Next.js** (BFF, httpOnly cookies); Mobile later: Expo.
- UI skills in `.claude/skills/`: frontend-design, ui-ux-pro-max, taste-skill (design-taste-frontend), impeccable (incl. `animate`).
- Deploy: Dokploy on Hostinger VPS.
- **Deploy target (2026-11-10): Dokploy `admin.conceptkart.co.in`, VPS 89.116.21.116** (was `dokploy.conceptkart.cloud`, never used). Ask the user before ANY panel action; nothing critical or experimental.
- Domains: `budgetflow.` (web), `budgetflow-api.` (gateway), `budgetflow-auth.` (auth) on `jitenjtb.com`.

- **Schema changes are post-production now:** never edit `db/migrations/V1__baseline.sql`. Add a `V{n}__*.sql` migration and the same change in `db/schema.sql`; `DatabaseScriptsIT` checks they match.

## UI Work
- Read `PRODUCT.md` (root) and `frontend/apps/web/DESIGN.md` before any UI change; impeccable artifacts live in `frontend/apps/web/.impeccable/`.
- Money in the UI is always a decimal string through `@budgetflow/money`; never floats.
- Never import constants from a `"use client"` module into Server Components; keep reference lists (currencies, time zones) static.

## Docs
- `architecture.md` — system design
- `plan.md` — phased roadmap
- `current_state.md` — progress (update after each task)

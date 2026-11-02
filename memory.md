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

## Docs
- `architecture.md` — system design
- `plan.md` — phased roadmap
- `current_state.md` — progress (update after each task)

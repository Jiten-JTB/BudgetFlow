# Budget Flow — Current State

_Last updated: 2026-10-27_

**Phase:** 0 — Foundation ✅ complete. Next: Phase 1 (identity-service, api-gateway).

## Done
- Docs: `architecture.md`, `plan.md`, `memory.md`, `CLAUDE.md`
- UI skills in `.claude/skills/`: frontend-design, ui-ux-pro-max, taste-skill, impeccable
- `backend/` Maven multi-module (Spring Boot 4.1.2, Java 25, `./mvnw`)
  - Quality gates: enforcer (Java 25, dependency convergence), unit (`*Test`) / integration (`*IT`) split, JaCoCo ≥80% line
- `libs/common-core` (pure Java): `Money` (NUMERIC(19,4)), `UuidV7`, `DomainException`/`ErrorCode`/`ErrorCategory`, ArchUnit "no framework" rule — 19 tests
- `libs/common-messaging`: transactional `OutboxWriter`, `OutboxRelay` (confirms, SKIP LOCKED, ordered, retry+park), background scheduler, `IdempotentConsumer` (inbox), exchanges `budgetflow.{commands,replies,events}`, `schema-fragment.sql` — 11 tests (Testcontainers Postgres + RabbitMQ)
- `libs/common-security`: stateless JWT resource server autoconfig, `@CurrentUserId` — 5 tests
- `infra/docker-compose.yml`: Postgres 17 (7 DBs, one role each, isolated), RabbitMQ 4, Redis 8 — smoke-tested
- `.github/workflows/backend.yml`, `.gitignore`, `.editorconfig`

## Commands
- Build + all tests: `cd backend && ./mvnw verify`
- Local infra: `docker compose -f infra/docker-compose.yml up -d`

## Next
- Phase 1: `identity-service` (schema.sql/reset.sql, register/login/refresh, OAuth2 issuer) → `api-gateway`

## Open Decisions
- Production domain (needs: `app.`, `api.`, `auth.`, `dokploy.`, `grafana.` + root redirect) — user to provide
- Email provider for notification-service

## Notes
- Outbox payload column is `JSON` (not `JSONB`) so messages are relayed byte-for-byte.
- Every service `db/schema.sql` must include the tables from `common-messaging/.../schema-fragment.sql`.

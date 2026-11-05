# Budget Flow — Current State

_Last updated: 2026-11-04 (Phase 2)_

**Phase:** 2 — Core Domain ✅ complete. Next: Phase 3 (orchestrator-service + sagas).

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
- `libs/common-web`: RFC 9457 problem+json handler (`code`, field `errors`, no internals on 500) — 6 tests
- Parent POM: Spring Cloud 2025.1.4 (stable; 2026.0 still RC), BouncyCastle, profile copying `db/schema.sql` → Flyway `V1__baseline.sql`
- `services/identity-service` (port 9000, mgmt 9001): Spring Authorization Server 7.1 (OAuth2/OIDC, Auth Code + PKCE required, rotating refresh tokens, JDBC stores, RS256 key from PEM secret), registration + `/api/v1/users/me` (GET/PATCH), Argon2id (19 MiB, t=2), lockout (5 fails → 15 min), auth audit trail, `UserRegistered` event via outbox, expired-authorization cleanup job, `db/schema.sql` + `db/reset.sql` — 38 unit + 18 integration tests
- `services/api-gateway` (port 8080, mgmt 8081): Spring Cloud Gateway (WebFlux), JWT validation (issuer + `budgetflow.api` scope), Redis rate limits (registration 5/min per IP; 10/s per user), CORS allow-list, safe `X-Request-Id` — 10 unit + 8 integration tests
- Dockerfiles (non-root, layered, healthcheck); `infra/docker-compose.yml` `apps` profile runs both services
- Phase 1 end-to-end smoke test passed on real containers: register → duplicate 409 → 401 → login → PKCE code → token → `/me` via gateway → registration 429 after 5; outbox event published to RabbitMQ

- Shared libs (Phase 2):
  - `common-security` now requires the `SCOPE_budgetflow.api` authority.
  - `common-messaging`: `QueueTopology` (quorum queue + DLQ per consumer); consumer retries (Boot 4 `max-retries`) then dead-letter.
  - New `common-test`: `ServiceContainers`, `DatabaseScripts.assertRebuildable`, `ApiTokens.user`.
- `services/account-service` (8110): accounts (7 types, negative balances allowed), append-only balance ledger, idempotent `apply`/`reverse` with tombstones, manual adjustments (`Idempotency-Key`), default Cash account on `UserRegistered` — 15 unit + 16 integration tests
- `services/transaction-service` (8120): income/expense/transfer recorded `PENDING` (202) + `TransactionRequested` event, required `Idempotency-Key` with reuse detection, keyset-paged search, 2-level categories, income sources, idempotent `complete`/`cancel`, 18 default categories on `UserRegistered` — 21 unit + 15 integration tests
- `services/budget-service` (8130): weekly/monthly/yearly soft budgets per category (incl. parent) or overall, usage per period, idempotent `consume`/`release`, threshold/exceeded alerts once per period, status API — 16 unit + 14 integration tests
- Gateway routes for all three; Dockerfiles; compose `apps` profile runs all 5 services
- Phase 2 end-to-end smoke test passed on real containers: default Cash account + 18 categories created via events; account, income source, expense (202 → replay 200), budget status and idempotent adjustment all through the gateway; all outboxes drained, DLQs empty, no errors in any service log
- Full backend build: 223 tests green

## Commands
- Build + all tests: `cd backend && ./mvnw verify`
- Local infra: `docker compose -f infra/docker-compose.yml up -d`
- Local infra + services: `docker compose -f infra/docker-compose.yml --profile apps up -d --build` (identity `:9000`, gateway `:8080`)

## Next
- Phase 3: `orchestrator-service` (saga state, timeouts) + command/reply handlers in account, budget and transaction services

## Open Decisions
- Grafana: **on hold** (user's call). Domain `budgetflow-grafana.jitenjtb.com` exists.
- Email provider for notification-service

## Notes
- Production secrets needed (Dokploy env, when deploying): `IDENTITY_SIGNING_KEY_PEM` (RSA 2048+ PKCS#8), `IDENTITY_WEB_CLIENT_SECRET`, DB/RabbitMQ/Redis passwords. Issuer = `https://budgetflow-auth.jitenjtb.com`.
- Transactions stay `PENDING` until Phase 3: balances and budgets are not yet updated by recorded transactions.
- budget-service accepts any category id (it cannot check transaction-service's categories); the saga supplies real ids.
- Not done yet: email verification (blocked on email provider), MFA, branded login page, OpenAPI docs, Spring Session for multi-instance identity.
- Login page is Spring's default form for now.
- Outbox payload column is `JSON` (not `JSONB`) so messages are relayed byte-for-byte.
- Every service `db/schema.sql` must include the tables from `common-messaging/.../schema-fragment.sql`.
- impeccable's launcher downloads a native binary from its GitHub releases on first use (checksum-verified); approved by user (2026-11-04): allowed to download on first use; edit hook NOT installed.
- `.github/workflows/backend.yml` has not run yet (needs a push).

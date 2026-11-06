# Budget Flow — Architecture

Finance tracking app: income sources, spending, accounts, budgets.
Targets: **Web first**, then Android & iOS.

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Backend services | Java 25 (LTS), Spring Boot 4.1, Maven multi-module (wrapper) | Enterprise standard; virtual threads, AOT/CDS for low memory on VPS |
| API Gateway | Spring Cloud Gateway (reactive) | Single entry, JWT validation, rate limiting, routing |
| Auth | `identity-service` (Spring Security + Spring Authorization Server, OAuth2/OIDC, JWT RS256) | Same tokens work for Web, Android, iOS (PKCE) |
| Saga orchestration | `orchestrator-service` (Spring Boot, persisted state machine) | Required pattern: Orchestration |
| Messaging | RabbitMQ (commands + replies + events) | Lighter than Kafka for a single VPS; DLQ + retries built-in |
| Reliability | Transactional Outbox + Inbox (idempotent consumers) | No lost / duplicate messages between DB and broker |
| Database | PostgreSQL 17, **one database per service** (single instance on VPS) | Service data isolation; scale out later |
| Cache | Redis | Rate-limit buckets, token blacklist, read caching |
| Web frontend | Next.js (App Router) + TypeScript, TanStack Query, Tailwind, Zod; standalone Docker output | Next.js server acts as BFF: tokens kept in httpOnly cookies, never in browser JS |
| Mobile (later) | React Native (Expo) reusing `packages/api-client`, `packages/domain` | One language & API client across platforms |
| API contracts | OpenAPI 3.1 per service (springdoc) → generated TS client | Typed, contract-first |
| Observability | Micrometer → Prometheus + Grafana, OpenTelemetry traces, structured JSON logs (Loki) | Correlation ID across saga steps |
| Deploy | Docker images → Dokploy (Docker Swarm/Compose) on Hostinger VPS, Traefik TLS | Per user requirement |
| CI | GitHub Actions: test → build image → push → Dokploy webhook | |

## 2. Services

| Service | Responsibility | DB |
|---|---|---|
| `api-gateway` (8080) | Routing, JWT validation, CORS, rate limit, request ID | — |
| `identity-service` (9000) | Users, registration, login, refresh tokens, MFA, profile/preferences (currency, timezone) | `identity_db` |
| `account-service` (8110) | Money accounts/wallets (bank, cash, card), balances, balance ledger | `account_db` |
| `transaction-service` (8120) | Income & expense entries, categories, income sources, recurring rules, attachments metadata | `transaction_db` |
| `budget-service` (8130) | Budgets per category/period, limits, consumption tracking | `budget_db` |
| `orchestrator-service` (8140) | Saga definitions, saga state, step/compensation dispatch, timeouts | `orchestrator_db` |
| `notification-service` (8150) | Email/push/in-app alerts (budget exceeded, saga failure) | `notification_db` |
| `reporting-service` (8160) | Read models (CQRS) built from events: monthly summaries, trends, dashboards | `reporting_db` |

Inter-service sync calls are avoided for writes; writes that span services go through a saga.
Management (health, Prometheus) runs on port + 1, internal only.

Events consumed so far: `identity.UserRegistered` → account-service opens a "Cash" account in the base currency; transaction-service seeds 6 income and 12 expense categories.
Each consumer has its own durable quorum queue `<service>.<event>` plus `<queue>.dlq`. Consumers retry 1 + 4 times with exponential backoff, then dead-letter.

## 3. Sagas (Orchestration)

All sagas: orchestrator persists state → sends command → awaits reply → next step or compensate. Each step is idempotent (keyed by an operation key such as `txn:<id>:apply`). Timeouts trigger compensation.

**RecordTransaction (income or expense)**: started by transaction-service, run by the orchestrator.
0. `transaction-service` stores the transaction `PENDING` (API returns `202`) and emits `transaction.TransactionRequested` (outbox).
1. `account-service` applies the signed amount, key `txn:<id>:apply`  ↺ reverse
2. `budget-service` consumes matching budgets (expenses only), key `txn:<id>:budget`  ↺ release
3. `transaction-service` marks `COMPLETED`  ↺ (on failure) marks `CANCELLED` with the failure code
→ `TransactionCompleted` / `TransactionCancelled`, `BalanceChanged`, `BudgetThresholdReached` / `BudgetExceeded` events → reporting, notifications.

Participant rules (built in Phase 2):
- Every step is idempotent by operation key, and calls on the same key are serialised (Postgres advisory locks).
- Compensation is safe in any order: compensating a step that never ran leaves a tombstone, so a late original is refused (`OPERATION_CANCELLED`).
- Budgets are soft limits: they never fail a saga, they raise alert events.

**Saga wire contract (built in Phase 3)**
- Commands go to the `budgetflow.commands` exchange. Type is `<service>.<CommandName>`, routing key is `<service>.<command-name>`, and the body carries `sagaId`, `commandId` and fields.
  - account: `ApplyBalanceChange`, `ReverseBalanceChange`
  - budget: `ConsumeBudget`, `ReleaseBudget`
  - transaction: `CompleteTransaction`, `CancelTransaction`
- Replies go to `budgetflow.replies` with routing key `orchestrator.saga-reply`, body `{sagaId, commandId, commandType, outcome SUCCESS|FAILURE, errorCode, errorMessage}`.
- Participants (`SagaParticipant`): the business work, inbox record and reply commit together. A `DomainException` rolls the work back and sends a FAILURE reply with the error code. Other errors are retried, then dead-lettered, and the step timeout takes over.
- Orchestrator: one persisted state machine per saga (`saga_instance`), with a full audit trail (`saga_event`).
  - Step timeout: 30s. A forward step that times out is compensated, including the timed-out step itself (safe because of tombstones). `COMPLETE_TRANSACTION` is resent instead, up to 5 attempts.
  - A failed compensation sets the saga to `FAILED` and logs `SAGA FAILED - manual intervention required` (alert on it).

**TransferBetweenAccounts**: create transfer `PENDING` → debit source ↺ credit back → credit target ↺ debit back → complete.

**UpdateTransaction / DeleteTransaction**: same steps applying the delta between old and new values.

**DeleteUser (GDPR)**: lock identity → purge each service's data → confirm (no compensation; retry until done).

## 4. Data Rules (all services)

- Money: `NUMERIC(19,4)` + `CHAR(3)` ISO-4217 currency. Never float. `BigDecimal` in Java, string-decimal in JSON.
- IDs: UUIDv7 (time-ordered). Every row: `created_at`, `updated_at`, `version` (optimistic locking).
- Every table scoped by `user_id`; queries always filter by it (row ownership enforced in service layer).
- Each service DB has `outbox` and `inbox` tables.
- **`schema.sql` is the source of truth** per DB; `reset.sql` drops everything in that DB.
  Post-production changes go through Flyway migrations, and `schema.sql` is updated to match.

## 5. Repository Layout

```
budgetFlow/
├── backend/
│   ├── pom.xml                     # parent POM (versions, plugins, quality gates)
│   ├── libs/
│   │   ├── common-core/            # Money, errors, IDs, problem+json
│   │   ├── common-messaging/       # outbox/inbox, RabbitMQ contracts, saga messages
│   │   └── common-security/        # JWT resource-server config
│   └── services/<service>/
│       ├── src/main/... src/test/...
│       ├── db/schema.sql
│       ├── db/reset.sql
│       └── Dockerfile
├── frontend/
│   ├── apps/web/                   # Next.js
│   ├── apps/mobile/                # later (Expo)
│   └── packages/{api-client,domain,ui-tokens}
├── infra/
│   ├── docker-compose.yml          # local dev (postgres, rabbitmq, redis, all services)
│   ├── dokploy/                    # prod compose / stack files
│   └── observability/
└── docs/adr/                       # architecture decision records
```

## 5b. Web App (built in Phase 4)

- `frontend/` pnpm workspace: `packages/money` (exact decimal formatting/parsing), `packages/api-client` (typed, zod-validated client; reused by mobile later), `apps/web` (Next.js 16 App Router).
- **BFF auth:** Next.js is the confidential OAuth client. Login = Authorization Code + PKCE; tokens live only in an encrypted httpOnly cookie (JWE, A256GCM). `proxy.ts` refreshes tokens a minute before expiry (rotating refresh tokens, one refresh in flight per token). Browser calls go to `/api/bff/*`, which checks Origin (CSRF), allows only `/api/v1/*`, and adds the bearer token server-side. A 401 from the API sends the user through sign-in and back.
- Sign-up is a server action that forwards the visitor's IP so the gateway's per-IP limit applies to them.
- Visual system: `frontend/apps/web/DESIGN.md` (tokens in `src/app/globals.css`); product context: `PRODUCT.md`.
- Deploy: `frontend/apps/web/Dockerfile` (standalone output, non-root). Env: `AUTH_ISSUER`, `AUTH_INTERNAL_URL`, `AUTH_CLIENT_ID`, `AUTH_CLIENT_SECRET`, `APP_URL`, `API_URL`, `SESSION_SECRET` (32+ chars), `SECURE_COOKIES`.

## 6. Service Internal Structure (Hexagonal)

```
domain/        entities, value objects, domain services (no Spring)
application/   use cases, ports (in/out)
adapter/in/    REST controllers, message listeners
adapter/out/   JPA repositories, RabbitMQ publishers, external clients
config/
```

## 7. Testing Strategy (test-first)

| Level | Tooling | Where |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito | domain + application layers |
| Integration | Spring Boot Test + Testcontainers (Postgres, RabbitMQ) | adapters, schema.sql validation, outbox |
| Contract | Spring Cloud Contract / OpenAPI validation | service ↔ service messages & APIs |
| Saga | Testcontainers multi-service flow: happy path + every compensation | orchestrator |
| Architecture | ArchUnit | layer rules |
| Frontend unit | Vitest + React Testing Library + MSW | components, hooks |
| E2E | Playwright | critical user journeys |

Coverage gate: 80% line on domain/application.

## 8. Security

- OAuth2 Authorization Code + PKCE (web & mobile), access token 15 min, rotating refresh tokens.
- Passwords: Argon2id. Optional TOTP MFA.
- Gateway validates JWT; services re-validate (zero trust) and read `user_id` from `sub`.
- Services not exposed publicly; only gateway via Traefik + TLS.
- Secrets via Dokploy environment secrets, never in repo.
- OWASP headers, input validation (Bean Validation), audit log for money-changing actions.

## 9. Deployment (Hostinger VPS + Dokploy)

- Traefik routing:
  - `budgetflow-api.` → api-gateway `:8080`.
  - `budgetflow-auth.` → identity-service `:9000`, **path allow-list only**: `/oauth2/`, `/.well-known/`, `/connect/`, `/userinfo`, `/login`, `/logout`, `/error`, `/css/`, `/fonts/` (branded sign-in page assets). `/api/**` must go through the gateway (rate limits).
  - Management ports (`:8081`, `:9001`: health, Prometheus) are never routed.
  - Traefik must overwrite client `X-Forwarded-For` (default when `forwardedHeaders.insecure` is off); the gateway's per-IP rate limit relies on it.
- Each service = Docker image (layered jar, `eclipse-temurin:25-jre` / CDS-enabled; GraalVM native optional for low-traffic services).
- JVM tuned for containers: `-XX:MaxRAMPercentage=75`, virtual threads on.
- Dokploy manages: Postgres, RabbitMQ, Redis, services, Traefik domains (see §10).
- Domains: see §10.
- Daily Postgres backups (Dokploy backup → S3-compatible storage).
- Health: Spring Actuator liveness/readiness used by Dokploy.

## 10. Domains

| Domain | Purpose | Public |
|---|---|---|
| `budgetflow.jitenjtb.com` | Next.js web app | Yes |
| `budgetflow-api.jitenjtb.com` | API gateway (web + mobile clients) | Yes |
| `budgetflow-auth.jitenjtb.com` | identity-service OAuth2/OIDC issuer (login pages, tokens) | Yes |
| `dokploy.conceptkart.cloud` | Dokploy panel (**shared, not fully owned: ask first, nothing critical or experimental**) | Admin only |
| `budgetflow-grafana.jitenjtb.com` | Monitoring dashboards (proposed, not confirmed) | Admin only |

DNS: A records to the VPS. TLS via Traefik + Let's Encrypt. RabbitMQ/Postgres/Redis admin stay internal (SSH tunnel).

## 11. Database Bootstrapping

- `db/schema.sql` = full current schema (source of truth); `db/reset.sql` drops it.
- Pre-production: Flyway runs `schema.sql` as baseline `V1` (copied at build time), so schema.sql stays the single file to edit.
- Post-production: new changes = Flyway `V2+` migrations **and** schema.sql updated to the resulting state.

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
| `api-gateway` | Routing, JWT validation, CORS, rate limit, request ID | — |
| `identity-service` | Users, registration, login, refresh tokens, MFA, profile/preferences (currency, timezone) | `identity_db` |
| `account-service` | Money accounts/wallets (bank, cash, card), balances, balance ledger | `account_db` |
| `transaction-service` | Income & expense entries, categories, income sources, recurring rules, attachments metadata | `transaction_db` |
| `budget-service` | Budgets per category/period, limits, consumption tracking | `budget_db` |
| `orchestrator-service` | Saga definitions, saga state, step/compensation dispatch, timeouts | `orchestrator_db` |
| `notification-service` | Email/push/in-app alerts (budget exceeded, saga failure) | `notification_db` |
| `reporting-service` | Read models (CQRS) built from events: monthly summaries, trends, dashboards | `reporting_db` |

Inter-service sync calls are avoided for writes; writes that span services go through a saga.

## 3. Sagas (Orchestration)

All sagas: orchestrator persists state → sends command → awaits reply → next step or compensate. Each step is idempotent (keyed by `saga_id + step`). Timeouts trigger compensation.

**RecordTransaction (income or expense)**
1. `transaction-service`: create transaction `PENDING`  ↺ mark `CANCELLED`
2. `account-service`: apply balance delta (credit/debit)  ↺ reverse delta
3. `budget-service`: consume budget (expense only; may emit `BudgetThresholdReached`)  ↺ release consumption
4. `transaction-service`: mark `COMPLETED`
→ events published → `reporting-service`, `notification-service`

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
  - `budgetflow-auth.` → identity-service `:9000`, **path allow-list only**: `/oauth2/`, `/.well-known/`, `/connect/`, `/userinfo`, `/login`, `/logout`, `/error`. `/api/**` must go through the gateway (rate limits).
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

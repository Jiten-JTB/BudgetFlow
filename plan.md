# Budget Flow — Implementation Plan

Rule for every phase: **write unit + integration tests first, then implement** until green.

## Phase 0 — Foundation
- [x] Monorepo scaffold (Maven multi-module + wrapper)
- [x] Parent POM quality gates (Java 25, JaCoCo, unit/IT split, Testcontainers)
- [x] `infra/docker-compose.yml` (Postgres, RabbitMQ, Redis)
- [x] Shared libs: `common-core`, `common-messaging` (outbox/inbox), `common-security`
- [x] CI pipeline (GitHub Actions)

## Phase 1 — Identity & Gateway
- [ ] `identity-service`: `db/schema.sql`, `db/reset.sql`, register/login/refresh, JWT
- [ ] `api-gateway`: routing, JWT validation, rate limiting, CORS

## Phase 2 — Core Domain
- [ ] `account-service`: accounts, balance ledger, schema/reset SQL
- [ ] `transaction-service`: income/expense, categories, income sources, schema/reset SQL
- [ ] `budget-service`: budgets, consumption, schema/reset SQL

## Phase 3 — Saga Orchestration
- [ ] `orchestrator-service`: saga engine, state persistence, timeouts, schema/reset SQL
- [ ] Sagas: RecordTransaction, TransferBetweenAccounts, Update/DeleteTransaction
- [ ] Saga tests: happy path + every compensation path

## Phase 4 — Web App
- [x] UI skills installed in `.claude/skills/`
- [ ] Next.js app scaffold (pnpm workspace) + design system via UI skills
- [ ] Generated API client from OpenAPI
- [ ] Screens: auth, dashboard, accounts, transactions, income sources, budgets, settings
- [ ] Vitest + Playwright tests

## Phase 5 — Supporting Services
- [ ] `reporting-service` (read models, charts data)
- [ ] `notification-service` (budget alerts, email)

## Phase 6 — Production
- [ ] Observability stack (Prometheus, Grafana, Loki, OTel)
- [ ] Dokploy deployment on Hostinger VPS, domains, TLS, backups
- [ ] Security review, load test, runbooks

## Phase 7 — Mobile
- [ ] Expo app reusing `packages/*`

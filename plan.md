# Budget Flow — Implementation Plan

Rule for every phase: **write unit + integration tests first, then implement** until green.

## Phase 0 — Foundation
- [x] Monorepo scaffold (Maven multi-module + wrapper)
- [x] Parent POM quality gates (Java 25, JaCoCo, unit/IT split, Testcontainers)
- [x] `infra/docker-compose.yml` (Postgres, RabbitMQ, Redis)
- [x] Shared libs: `common-core`, `common-messaging` (outbox/inbox), `common-security`
- [x] CI pipeline (GitHub Actions)

## Phase 1 — Identity & Gateway
- [x] `identity-service`: `db/schema.sql`, `db/reset.sql`, register/login/refresh, JWT
- [x] `api-gateway`: routing, JWT validation, rate limiting, CORS
- [ ] Follow-ups: email verification, MFA (TOTP), branded login page (Phase 4), Spring Session (Redis) before running >1 identity instance

## Phase 2 — Core Domain
- [x] `account-service`: accounts, balance ledger, schema/reset SQL
- [x] `transaction-service`: income/expense, categories, income sources, schema/reset SQL
- [x] `budget-service`: budgets, consumption, schema/reset SQL
- [x] Gateway routes, Dockerfiles, compose, end-to-end smoke test

## Phase 3 — Saga Orchestration
- [x] `orchestrator-service`: saga engine, state persistence, timeouts, schema/reset SQL
- [x] Saga command/reply handlers in account, budget and transaction services
- [x] Sagas: RecordTransaction (income/expense), TransferBetweenAccounts
- [x] Saga tests: happy path + every compensation path, timeouts, stale replies; full-stack run with real compensations
- [ ] Sagas: UpdateTransaction / DeleteTransaction (move to Phase 4 with the edit/delete UI)
- [ ] Ops: admin view or endpoint for FAILED sagas + retry

## Phase 4 — Web App
- [x] UI skills installed in `.claude/skills/`
- [x] Next.js app (pnpm workspace) + design system via UI skills (impeccable: PRODUCT.md, direction "Bahi-Khata Ledger", DESIGN.md)
- [x] Typed API client (`@budgetflow/api-client`, zod-validated against the backend contracts). OpenAPI generation deferred.
- [x] Screens: sign-up, branded sign-in, overview, transactions, accounts, budgets, income sources, categories, settings
- [x] Vitest + Playwright tests (incl. axe WCAG 2.2 AA), web Docker image, frontend CI
- [ ] Follow-ups: edit/delete transaction sagas + UI; OpenAPI docs; ESLint; faster saga confirmation (wake the outbox relay after commit); dark theme

## Phase 5 — Supporting Services
- [ ] `reporting-service` (read models, charts data)
- [ ] `notification-service` (budget alerts, email)

## Phase 6 — Production
- [x] Observability stack: Prometheus, Grafana, Loki, Alloy (OTel traces and alert contact point still to do)
- [x] Dokploy deployment on Hostinger VPS, domains, TLS, backups (live 2026-11-10 at admin.conceptkart.co.in)
- [ ] Security review, load test, runbooks

## Phase 7 — Mobile
- [ ] Expo app reusing `packages/*`

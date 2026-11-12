# Budget Flow — Current State

_Last updated: 2026-11-10 (deployed + Grafana)_

**Phase:** 6 — Production. v0.1.0 is **live** on Dokploy `admin.conceptkart.co.in` (VPS 89.116.21.116; project MicroServices / production, service budgetFlow), checked from outside on 2026-11-10. Grafana stack is built and verified locally; it needs release v0.1.1 plus `GRAFANA_ADMIN_PASSWORD` in Dokploy.

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

- Phase 3:
  - `common-messaging/saga`: `SagaContract`, `SagaReply`, `SagaParticipant` (exactly-once command + reply; business failure gives a FAILURE reply), `CommandFields`.
  - `common-test`: `SagaCommands` helper.
  - Participants: account (apply/reverse), budget (consume/release), transaction (complete/cancel). `TransactionRequested` now carries `parentCategoryId`.
  - `services/orchestrator-service` (8140, internal only): `saga_instance` + `saga_event`; RecordTransaction and TransferBetweenAccounts; reverse-order compensation then cancel; step timeouts (compensate, or resend for the final step); stale-reply protection; duplicate-trigger protection — 17 unit + 6 integration tests.
  - Full-stack run on real containers: expense, income and transfer all COMPLETED with correct balances and budget usage.
  - Bad transfer (INR to USD account): debit reversed, transaction CANCELLED `ACCOUNT_CURRENCY_MISMATCH`.
  - Expense on archived account: CANCELLED `ACCOUNT_ARCHIVED`.
  - Budget threshold and exceeded alerts each fired once. All outboxes drained, all queues empty, 0 ERROR log lines.
- Full backend build: 262 tests green

## Phase 4 progress (resume here)
- [x] `PRODUCT.md` written (impeccable init interview): solo individuals, desktop-first, WCAG 2.2 AA, locale-aware amounts (Indian grouping), English only, never gamified.
- [x] Visual direction chosen by user: **Bahi-Khata Ledger**. Contract in `.impeccable/surfaces/frontend-apps-web.md` (signature: facing pages, money in left / money out right, balance at the spine). Sketches: `.impeccable/mocks/decision/`.
- [x] 4a Frontend monorepo (`frontend/`, pnpm) + `@budgetflow/money` + `@budgetflow/api-client` (zod) with tests
- [x] 4b Auth BFF in Next.js (PKCE login, callback, refresh, logout; encrypted httpOnly session; BFF proxy with Origin check) — 20 unit tests; live sign-in to be verified in 4c
- [x] 4c Design tokens (`globals.css`, AA-checked: indigo focus ring on light 11.4:1, turmeric only on the rail), vermilion rail + mobile top bar (native disclosure menu), overview with facing pages (`build-overview.ts` view-model), live sign-in verified in a real browser (httpOnly session, no console errors). Screens: `frontend/apps/web/.impeccable/review/`
- [x] 4d All screens: add transaction (live confirmation, safe retry), transactions list with filters/paging, accounts (list/open/ledger/adjust/rename/archive), budgets (status/create/limit/archive), income sources, categories (2 levels), settings, public sign-up (server action, forwards visitor IP for rate limits). 78 web unit tests; browser journey (new user sign-up → every page) passes with 0 console errors.
- [x] 4e Branded identity-service sign-in page (Thymeleaf; self-hosted Mukta + OFL licence; one generic error for wrong password and locked account; links to web sign-up via `IDENTITY_WEB_SIGNUP_URL`) — `LoginPageIT` 4 tests; identity suite 49 green
- [x] 4f Playwright E2E `frontend/apps/web/e2e/` — 8 tests incl. axe WCAG 2.2 AA on 10 screens, passing twice in a row; run with stack started using `SIGNUP_RATE_TOKENS=1`, then `corepack pnpm e2e`. web Dockerfile (standalone, non-root, 302 MB) + compose `web` service — E2E 8/8 against the production container. frontend CI workflow (`.github/workflows/frontend.yml`, not yet run on GitHub); impeccable detector run (1 accepted exception) and finish review (inline, fallback procedure) → fix → verdict `ship`; transaction-service gained `incomeSourceId` and parent-inclusive `categoryId` filters so every overview line opens its transactions. `DESIGN.md` + `.impeccable/design.json` written from the built code (North Star \"The Merchant's Ledger\", controls precise and quiet).
- Fixed during 4f: identity throwaway (local) signing keys now get a unique key id per restart (verifiers cached the old key under the reused id → 401s); server pages turn an API 401 into sign-in + return; sign-out now lands on `/signed-out` (identity `IDENTITY_WEB_POST_LOGOUT_URIS` updated).
- impeccable artifacts live in `frontend/apps/web/.impeccable/` (brief: `surfaces/frontend-apps-web-src-app.md`, read with `impeccable surface-brief read frontend/apps/web/src/app`); `frontend/apps/web/PRODUCT.md` is a symlink to the root `PRODUCT.md`. Face: Mukta (Hind lacks tabular figures).
- Dev loop: backend `docker compose -f infra/docker-compose.yml -p bf-web --profile apps up -d` with ports POSTGRES 56432, RABBITMQ 55672/55673, REDIS 56379, IDENTITY 59000, GATEWAY 58080, `WEB_PORT=3300` (port 3000 is taken on this machine); web `cd frontend/apps/web && corepack pnpm exec next dev --port 3300` with `.env.local` (see `.env.local.example`). Restart `next dev` if Tailwind classes in a newly created file are missing.
- Follow-up: saga confirmation takes ~3.4 s locally because each of ~6 message hops waits for the 500 ms outbox poll; wake the relay right after commit (afterCommit hook) to make logging feel instant.
- Lessons: never import constants from a `"use client"` module into Server Components (they become client references); keep currency/time-zone lists static (`src/lib/reference-data/`) because Node and browser ICU lists differ and break hydration.
- Notes: impeccable telemetry disabled via `IMPECCABLE_NO_TELEMETRY=1`; never `pkill -f serve-question` (kills own shell).

- Totals at end of Phase 4: backend 267 tests; frontend 102 unit tests (money 11, api-client 9, web 82); 8 Playwright E2E tests incl. axe WCAG 2.2 AA on 10 screens.

## Deployment prep (resume here)
- `infra/deploy/docker-compose.prod.yml`: one Dokploy Compose service. GHCR images at `IMAGE_TAG`, no host ports, memory caps (~5 GB total, ~1.6 GB idle), log rotation. Only web, gateway and identity join `dokploy-network`; internal calls use `budgetflow-*` aliases on a private network. Traefik labels cover HTTP→HTTPS, letsencrypt, HSTS and the auth path allow-list. Postgres init creates a role per service with its own password. `postgres-backup` sidecar writes daily dumps with 14-day retention.
- `infra/deploy/generate-secrets.sh`: hex secrets plus a single-line 3072-bit PKCS#8 signing key, written to `.env.prod` (git-ignored, mode 600).
- `.github/workflows/release.yml`: runs on a `v*.*.*` tag, reruns backend and frontend CI (both now `workflow_call`), then pushes 7 amd64 images to `ghcr.io/jiten-jtb/budgetflow-*`.
- `infra/deploy/test-deploy.sh`: 49 static checks, all green.
- Verified locally: prod compose with retagged images behind Traefik 3.6 (Dokploy-like entrypoints and `redirect-to-https@file`).
  - All 11 containers healthy.
  - 301 to https; the auth allow-list serves login, css and fonts and returns 404 for `/api` and `/actuator`.
  - Issuer is `https://budgetflow-auth.jitenjtb.com`; HSTS is set.
  - DB role isolation holds; dumps were written and restored into a fresh database.
  - E2E 8/8 over HTTPS with the real hostnames mapped locally.
- Next, the user's steps:
  1. Run the preflight on the VPS.
  2. Generate secrets.
  3. Push tag `v0.1.0`.
  4. Make the GHCR packages public.
  5. Create the Compose service in Dokploy.
  6. Verify.
- Off-VPS backup copy is still open; Dokploy S3 backup needs the panel owner's OK.

## Observability (resume here)
- Added to the prod compose: Prometheus v3.5.0, Loki 3.5.0, Alloy v1.10.0 and Grafana 12.1.1. Configs live in `infra/deploy/observability/`. Grafana is routed at `budgetflow-grafana.jitenjtb.com`; the rest is internal.
- JVM services get `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` and http.server.requests histograms through `SPRING_APPLICATION_JSON`, with no image change. Orchestrator gained the alias `budgetflow-orchestrator`.
- Backend fix (requires a release):
  - Resource servers and the gateway returned 401 to Prometheus on the management port.
  - Actuator is now permitted only when the connection's local port is the separate management port. Code: `ManagementPort` / `actuatorOnManagementPort` in common-security, plus the gateway equivalent.
  - The public port still returns 401.
  - Tests: `ManagementPortSecurityTest` (3) and 2 new tests in `GatewayIT`.
- Fixed a flaky `GatewayIT` registration rate-limit test (the limiter refills in whole seconds, so a sign-up now costs 60 tokens).
- Backend: 272 tests green. `test-deploy.sh`: all checks pass.
- Verified locally: all 15 containers up, 7/7 targets up, every dashboard query returns data, E2E money flows pass, log levels extracted.
- Open: alert contact point (email or Telegram?) and OTel traces.

## Amount input grouping (2026-11-10)
- Amounts are grouped as you type in all 5 amount fields: transaction, opening balance, adjustment, new budget limit, and edit budget limit.
  - en-IN: 17,500 and 1,75,000. Other locales: 175,000.
  - The separator is always ",", so `parseAmountInput` reads it back exactly.
- `@budgetflow/money` adds `formatAmountInput`, `countSignificantBefore` and `caretAfterFormat`. Formatting is exact (BigInt) and never rounds.
- New `src/components/amount-input.tsx`:
  - Keeps the caret in place.
  - Backspace or Delete on a comma removes the neighbouring digit.
  - Accepts a minus only with `allowNegative`, and those fields use `inputMode="text"` because phone decimal pads have no minus key.
- `navigatorLocale` moved to `src/lib/navigator-locale.ts`. The edit-budget form groups the saved limit after mount to avoid a hydration mismatch.
- Tests: money 31, web 88; E2E 8/8 (money flow types `110000` digit by digit and expects `1,10,000`). Ships with the next release tag.

## First-run guidance (2026-11-10)
- The overview shows a "Set up your books" checklist (`features/overview/setup-steps.ts`, `setup-checklist.tsx`), only for the current month. Steps:
  1. Accounts: done when there is a second account or any balance is not zero, because every user starts with an empty Cash account.
  2. Income sources (optional).
  3. Budget (optional).
  4. First transaction: checked with one extra `transactions.list({limit:1})` call when this month is empty.
- The checklist hides when complete, or through "Hide these steps" (server action `app/(app)/setup-actions.ts`). The `bf_setup_hidden` cookie holds the user id, so hiding is per user.
- Transaction form: an empty Income source field shows "None yet. Add an income source", and a transfer with one account explains it needs two.
- Tests: web 102 unit; E2E 9/9 (new `e2e/onboarding.spec.ts`; axe passes on the overview with the checklist showing). Detector clean. DESIGN.md updated. Ships as v0.1.3.

## v0.1.4 work (2026-11-10): buttons, verification, account deletion, landing page
- **Buttons:** actions that open a screen are `ActionLink` (a secondary button that is still a link). Empty states use `EmptyState`. Each open checklist step has its own action button.
- **Flyway post-production:**
  - V1 is frozen per service as `db/migrations/V1__baseline.sql`, byte-identical to what production ran, and the POM packages `db/migrations/`.
  - `DatabaseScriptsIT.migrationsBuildExactlyTheSchemaInSchemaSql` proves that the migrations equal `schema.sql`.
  - The V1 → V2 upgrade was verified on a live local database: all 89 existing users were backfilled as verified.
- **Email verification (identity V2):**
  - SHA-256-hashed one-time tokens, valid 24 hours, sent through an `email_outbox` table and an SMTP relay (SKIP LOCKED, backoff, row deleted once sent).
  - The login check runs after the password, so it never reveals whether an address has an account.
  - Resend is throttled to one per minute and silent.
  - New public endpoints `POST /api/v1/users/email-verifications` and `/resend`, sharing the gateway's sign-up limit per IP.
  - Web pages: `/signup/check-email` (the pending address is kept in a 1-hour httpOnly cookie, never in the URL) and `/verify-email` (a button, not confirm-on-load).
- **Delete account:**
  - `DELETE /api/v1/users/me {password}`: identity erases the user, tokens, queued mail, OAuth authorizations and their audit trail. One `USER_DELETED` audit row is kept, with no email.
  - It publishes `identity.UserDeleted`. The shared `UserDataEraser` (common-messaging) erases account, transaction, budget and orchestrator data, plus published outbox rows that mention the user.
  - `DeletedUserSessionFilter` ends the identity sign-in session of an erased user.
  - Web: Settings → Delete my account (confirmed with the password) → `/account-deleted`.
- **BFF fix:** 204/205/304 responses from the gateway were turned into 502s, because a body-less status can't carry a body.
- **Landing page:** `/` for signed-out visitors is rewritten to `/welcome`. Direction is "Two questions", locked by the user (seed:fa54717c842d905f, brief `surfaces/frontend-apps-web-src-app-welcome.md`). Example data reconciles (tested). The finish review says ship (`.impeccable/review/landing-finish-review.md`). DESIGN.md is updated.
- **Auth domain root:** `/` redirects to the web home (added to the Traefik allow-list).
- **Tests:**
  - Backend: everything green, including coverage gates.
  - Web: 127 unit tests, and 10 api-client tests.
  - E2E 12/12 with real email through Mailpit.
  - `test-deploy.sh` passes.
- **Ships as v0.1.4.** Needs `SMTP_PASSWORD` in Dokploy and Hostinger mail DNS (SPF/DKIM/DMARC).

## Commands
- Build + all tests: `cd backend && ./mvnw verify`
- Local infra: `docker compose -f infra/docker-compose.yml up -d`
- Local infra + services + web: `docker compose -f infra/docker-compose.yml --profile apps up -d --build` (web `:3000`, identity `:9000`, gateway `:8080`)
- Frontend: `cd frontend && corepack pnpm test` / `typecheck` / `build`; E2E: start the stack with `SIGNUP_RATE_TOKENS=1`, then `cd frontend/apps/web && corepack pnpm e2e`

## Next
- Phase 4: Next.js web app (pnpm workspace, BFF auth, design system via UI skills); Update/Delete transaction sagas with the edit UI

## Open Decisions
- Grafana: built (2026-11-10). Open: alert contact point (email or Telegram).
- Email provider: Hostinger SMTP (jiten@jitenjtb.com) for identity; notification-service later

## Notes
- Production secrets needed (Dokploy env, when deploying): `IDENTITY_SIGNING_KEY_PEM` (RSA 2048+ PKCS#8), `IDENTITY_WEB_CLIENT_SECRET`, DB/RabbitMQ/Redis passwords. Issuer = `https://budgetflow-auth.jitenjtb.com`.
- Cross-currency transfers are not supported (the credit step refuses; saga compensates).
- No admin view yet for `FAILED` sagas (alert on the `SAGA FAILED` log line).
- **Fixed 2026-11-06:** `.gitignore` rule `out/` matched every `adapter/out/` source package, so those 21 files were missing from the Phase 1-2 commits. Now anchored to `/out/` and `/build/`.
- budget-service accepts any category id (it cannot check transaction-service's categories); the saga supplies real ids.
- Not done yet: email verification (blocked on email provider), MFA, branded login page, OpenAPI docs, Spring Session for multi-instance identity.
- Login page is Spring's default form for now.
- Outbox payload column is `JSON` (not `JSONB`) so messages are relayed byte-for-byte.
- Every service `db/schema.sql` must include the tables from `common-messaging/.../schema-fragment.sql`.
- impeccable's launcher downloads a native binary from its GitHub releases on first use (checksum-verified); approved by user (2026-11-04): allowed to download on first use; edit hook NOT installed.
- `.github/workflows/backend.yml` has not run yet (needs a push).

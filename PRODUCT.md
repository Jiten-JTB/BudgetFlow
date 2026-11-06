# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

(Android and iOS follow later as separate apps; the web app ships first. APIs and shared TypeScript packages stay platform-neutral.)

## Users

Individuals managing their own money, alone. One person per account: their salary and other income, bank accounts, cards, wallets and cash. They open the app daily to log spending and check where they stand, and periodically to review a month.

## Product Purpose

Budget Flow tracks where money comes from and where it goes, keeps every account balance exact, and warns before a budget is overspent. Success: logging an expense becomes a seconds-long daily habit, and the user trusts every number on screen without double-checking it elsewhere.

## Positioning

- **Income-source clarity:** income is tracked by named source (employer, client, rental, investment), not only spending.
- **Trustworthy numbers:** every balance change is a ledger entry; corrections are visible reversals; nothing is silently lost or double-counted (the backend guarantees this with idempotent, compensated sagas).
- **Fast daily logging:** recording a transaction must take seconds.
- **Budgets that guide:** calm, early warnings per category and period, never blocking.

## Operating Context

- Daily: log an expense or income right after it happens, often on a phone browser; glance at balances and budget status.
- Periodic: review a month (income by source, spending by category, budgets), reconcile an account against a bank statement with a manual adjustment.
- New transactions are accepted as *pending* and confirmed within a second or two by the backend saga; a transaction can end *cancelled* with a reason (for example, the account is archived). The UI must show these states honestly.

## Capabilities and Constraints

- Backend (built): accounts (bank, cash, credit card, wallet, savings, investment, loan; negative balances allowed), balance ledger and manual adjustments, income and expense transactions, transfers between same-currency accounts, two-level categories (18 defaults seeded), income sources, weekly/monthly/yearly budgets with threshold and exceeded alerts, profile with base currency and timezone.
- Not supported yet: editing or deleting transactions, cross-currency transfers, shared/household accounts, email verification, MFA, reports.
- Amounts are exact decimals (4 places stored); never use floating point in the UI.
- Auth: OAuth2 Authorization Code + PKCE against budgetflow-auth; the Next.js server holds tokens (httpOnly cookies), the browser never sees them.
- Domains: web `budgetflow.jitenjtb.com`, API `budgetflow-api.jitenjtb.com`, auth `budgetflow-auth.jitenjtb.com`.

## Brand Commitments

- Name: Budget Flow.
- No logo or brand assets exist yet; do not invent a mark beyond the wordmark.

## Evidence on Hand

None. No testimonials, user counts, press or ratings exist; never fabricate them.

## Product Principles

1. Numbers are sacred: exact, consistently formatted, traceable to their ledger entries.
2. Logging is the core loop: the fastest path in the app is "add transaction".
3. Honest states: pending, completed and cancelled are always visible, with the reason when something fails.
4. Calm guidance: warnings inform, they don't alarm or block.
5. Income matters as much as spending.

## Accessibility & Inclusion

- WCAG 2.2 AA is a hard requirement: contrast, full keyboard operation, visible focus, screen-reader labels (amounts read correctly, signs not conveyed by colour alone), reduced motion respected.
- International from day one: numbers, currency and dates follow the user's locale; Indian grouping (₹1,50,000; lakh/crore) must work fully for Indian users. Multi-currency accounts are supported.
- English only for now; keep copy externalisable for later translation.

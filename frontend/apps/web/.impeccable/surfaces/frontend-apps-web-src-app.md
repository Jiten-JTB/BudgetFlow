---
version: 1
slug: "frontend-apps-web-src-app"
primary_target: "frontend/apps/web/src/app"
related_targets: []
---

# Budget Flow web app (signed-in product)

Scope: the signed-in web app (overview, transactions, accounts, budgets, income sources, categories, settings) plus sign-up. Visitor mode: Operate. Desktop first; phone browser must work fully.

Audience and job: one person managing their own money; logs transactions daily and reviews the month. The overview must answer "am I on track this month?" and "how much do I have?" at one glance. Never gamified.

Constraints: WCAG 2.2 AA; amounts are exact decimal strings formatted per locale (Indian grouping for en-IN); pending, completed and cancelled states always visible with the reason; English only.

## Direction contract

THESIS: Money kept the way a merchant keeps the bahi-khata: what came in on one page, what went out on the facing page, the balance struck at the spine. Refuses the KPI-card-and-donut fintech dashboard.

OWN-WORLD: Vermilion bookcloth rail (#9E2A20) as the navigation spine with a turmeric thread edge (#E0A526); quiet page ground (#F4F3EF) with white working panels; indigo ink (#26305C) for figures and headings; vermilion for money out, indigo for money in, always with a sign. Turmeric only on the primary action, focus and budget warnings. Mukta type (Ek Type), tabular figures via its tnum feature, one fixed five-step money scale. Standard web controls only; no ruled-paper costume.

STORY: The user sees what they have and whether the month is on track, trusts every figure because each opens to its ledger entries, and adds a transaction in seconds.

FIRST VIEWPORT: Left: vermilion rail (wordmark, nav, "Add transaction" in turmeric at the foot). Top of work area: month title and period switch. Then the facing pages: left page "Money in" by income source, right page "Money out" by category, meeting at a centre spine that strikes the balance ("Kept this month"). Below: account balances with the total, budgets, recent transactions with live states.

FORM: Bahi-khata facing-page ledger, my top-ranked grounded candidate (1 of 7), chosen over the assigned Ulm Precision; seed: b596dd40c7770505. Kept disciplines: rows change state in place inside fixed columns; one fixed money type ramp; nothing labelled twice; charts across periods share one scale; whole-module grid reflow.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved
- Resolved: Hind lacks tnum (proportional digits), so the face is Mukta, which ships tnum.

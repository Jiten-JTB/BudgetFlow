Finish review run inline (the `impeccable-finish-reviewer` agent is not installed in this harness), following `reference/degraded/finish-reviewer.md`.
Inputs: request and confirmed answers (PRODUCT.md), direction contract (`.impeccable/surfaces/frontend-apps-web-src-app.md`), captures `desktop.png` (1440×1392) and `mobile.png` (390×2337) from the production container, detector output (1 finding: side-tab on the sign-in panel), critique reference `mocks/decision/model-pick.html` (code-led build, no approved comp). No QUALITY BAR card exists: the chosen direction is my own grounded candidate, not a catalog world. Seed reference corroborated against the roll output printed in-session (`seed:b596dd40c7770505`); the local roll record was removed when `.impeccable/` moved into the app folder.

disposition: fix

## persistence
pass. PRODUCT.md present (symlinked into the app root). Code-led build: no build state, spec or comp round required. Decision files under `mocks/decision/` are the dealt hand and imply no approval.

## fidelity
- THESIS facing pages (money in left, money out right, balance struck at the spine): match.
- TYPE: adaptation. Mukta instead of the sketch's Hind, cited: Hind has no tabular figures (checked with fontTools), and amounts must align.
- MATERIAL: match. Flat bookcloth and thread colours, no imitation texture, bevel or emboss.
- GROUND: match. Page ground #F4F3EF as OWN-WORLD names it.
- FIRST VIEWPORT: match, with one adaptation. "You have …" total sits in the header row, so "how much do I have?" is answered above the fold (confirmed user answer: the glance must answer both questions).
- Critique reference: the sketch dared a strong indigo rule under the month title and a "Books opened 1 April" line (the financial-year ritual). Neither is adopted; noted, not required.

## ceiling
No catalog card to measure against. Unused native device of the world: the financial-year opening ritual (books opened in April) could anchor yearly views later.

## material_fixes
1. STORY promise "every figure opens to its ledger entries": the facing-page rows (income by source, spending by category) are not links. The API cannot yet filter transactions by income source, or by a category including its sub-categories, so linking now would show wrong lists. Needs the transaction-service filters first.
2. Floor (glyph standing in for an icon): transfer rows draw direction with a "→" character, which screen readers announce as "right arrow". Draw it as an icon with the word "to" for assistive tech.

## keep
The facing-page spread with the struck balance at the spine: never collapse it into KPI cards.

---

# Verdict pass (after the fix batch)

## verdict
1. STORY promise, spread lines open their transactions: resolved. Income lines link to `incomeSourceId`, spending lines to `categoryId` (sub-categories included via the new transaction-service filter); unassigned and folded lines stay plain. E2E clicks "Groceries" on the overview and lands on exactly its one transaction.
2. Transfer direction glyph: resolved. Drawn `ArrowRight` icon (aria-hidden) with "to" for screen readers; unit test asserts no "→" remains.

## remaining
clear (for the scored fixes; the detector's side-tab finding on the rail's turmeric thread is a deliberate world signature, recorded as an exception).

disposition: ship

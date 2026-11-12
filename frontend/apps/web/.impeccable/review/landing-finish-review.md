Finish review of the landing page, run inline (no finish-reviewer agent in this harness).
Inputs:
- The direction contract in `.impeccable/surfaces/frontend-apps-web-src-app-welcome.md`, locked by the user from the decision page (`questions.html`, seed:fa54717c842d905f).
- Captures of the production container at 1440×900, full page, and 390 wide.
- Detector output.

## fidelity
- THESIS (two questions on facing pages): match. Both questions are h2 regions on white pages; there is no hero-over-cards layout.
- OWN-WORLD: match. The palette and type are the app's own; the vermilion spine is edged with turmeric thread; budget states are given in words.
- FIRST VIEWPORT: match.
  - Accounts and total on the left, the spine with the wordmark and actions, budgets on the right.
  - Adaptation: the page is no longer forced to full height, so the next spread starts above the fold.
  - The sketch's account total was wrong (₹2,41,560). The build shows the computed ₹2,26,560, and `example-ledger.test.ts` locks it.
- STORY: the visitor sees both answers, then how the numbers are kept (the money in/out spread and the ledger), then how fast logging is, the privacy note and what's coming next. The actions sit at the spine and are repeated at the close and in the footer.

## floor
- WCAG: axe (WCAG 2.2 AA) passes on the landing page in E2E. The spine h1 inherited indigo (1.69:1) until fixed to cloth ink.
- Claims: there is no pricing, user count or testimonial (a test enforces this). The privacy lines are true of the built system.
- Detector: the turmeric thread on the phone spine is an accepted world signature, the same exception as the mobile bar. The landing question size is documented in DESIGN.md.

disposition: ship

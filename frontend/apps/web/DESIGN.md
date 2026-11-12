---
name: Budget Flow
description: Money kept like a merchant's ledger, in income and spending facing each other.
colors:
  vermilion-cloth: "#9e2a20"
  vermilion-deep: "#7f2018"
  cloth-ink: "#f6e6d8"
  cloth-mute: "#ecc8b8"
  turmeric-thread: "#e0a526"
  turmeric-ink: "#7a5300"
  indigo-ink: "#26305c"
  indigo-deep: "#1d2549"
  page: "#f4f3ef"
  panel: "#ffffff"
  ink: "#33322e"
  mute: "#66635c"
  rule: "#e3e0d9"
  rule-strong: "#cfcbc2"
typography:
  display:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  money-xl:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2.5rem"
    fontWeight: 600
    letterSpacing: "-0.025em"
    fontFeature: "\"tnum\" 1, \"lnum\" 1"
  money-lg:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 600
    fontFeature: "\"tnum\" 1, \"lnum\" 1"
  money-md:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    fontFeature: "\"tnum\" 1, \"lnum\" 1"
  money-sm:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 500
    fontFeature: "\"tnum\" 1, \"lnum\" 1"
  money-xs:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    fontFeature: "\"tnum\" 1, \"lnum\" 1"
  title:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.2
  body:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Mukta, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
    lineHeight: 1.5
rounded:
  control: "6px"
  sheet: "10px"
  pill: "9999px"
spacing:
  row: "10px"
  field: "24px"
  sheet: "24px"
  section: "32px"
  page-x: "40px"
  page-x-mobile: "16px"
components:
  button-primary:
    backgroundColor: "{colors.indigo-ink}"
    textColor: "{colors.panel}"
    rounded: "{rounded.control}"
    padding: "10px 20px"
  button-primary-hover:
    backgroundColor: "{colors.indigo-deep}"
  button-add:
    backgroundColor: "{colors.turmeric-thread}"
    textColor: "{colors.indigo-ink}"
    rounded: "{rounded.control}"
    padding: "12px 16px"
  button-quiet:
    textColor: "{colors.indigo-ink}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-danger:
    textColor: "{colors.vermilion-cloth}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  input:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
  sheet:
    backgroundColor: "{colors.panel}"
    rounded: "{rounded.sheet}"
    padding: "24px"
  nav-item:
    textColor: "{colors.cloth-mute}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  nav-item-current:
    backgroundColor: "{colors.vermilion-deep}"
    textColor: "{colors.cloth-ink}"
---

# Design System: Budget Flow

Source of truth for tokens: `src/app/globals.css` (`@theme`). This file describes the system as built; update both together.

## Overview

**Creative North Star: "The Merchant's Ledger"**

Budget Flow keeps money the way a merchant keeps the bahi-khata: what came in on one page, what went out on the facing page, and the balance struck at the spine. A vermilion bookcloth rail with a turmeric thread binds the app; the work happens on quiet white sheets laid on a pale ledger page, written in indigo ink.

The system is precise and quiet. Controls are standard and calm, so the numbers carry the weight: exact, tabular, signed, and always traceable to their ledger lines. Colour does real jobs (the direction of money, state, the one primary action) and nothing is decoration. It is never gamified.

**Key Characteristics:**
- Facing-page spread with the balance struck at the spine
- Vermilion rail, turmeric thread, indigo ink on a pale page
- Mukta with tabular figures and one fixed money ramp
- States said in words, never by colour alone
- Standard web controls; no ledger costume

## Colors

A committed shell (vermilion rail) around a restrained working surface (white sheets on a pale page), with indigo and vermilion doing the money's two directions.

### Primary
- **Vermilion Bookcloth** (#9e2a20): the navigation rail and the mobile top bar; money out (amounts, budget overruns, errors). 6.7:1 on the page, 7.5:1 on white.
- **Ledger Spine Red** (#7f2018): the current rail item and rail hovers.

### Secondary
- **Turmeric Thread** (#e0a526): the rail's 4px binding edge, the "Add transaction" fill, budget-warning bars, the confirming dot, and focus rings on the rail (3.4:1 on cloth). Never text or focus on light surfaces (2.2:1 on white).
- **Turmeric Ink** (#7a5300): warning text on light surfaces (6.2:1 on the page).

### Tertiary
- **Indigo Ledger Ink** (#26305c): headings, money in, the primary button, links, and the focus ring on light surfaces (11.4:1 on the page).
- **Night Indigo** (#1d2549): primary button hover.

### Neutral
- **Ledger Page** (#f4f3ef): the app ground.
- **Clean Sheet** (#ffffff): working sheets and inputs.
- **Lamp-black Ink** (#33322e): body text (11.6:1 on the page).
- **Pencil Grey** (#66635c): secondary text, column heads, hints (5.4:1 on the page, 6.0:1 on white).
- **Ruled Line** (#e3e0d9) and **Pressed Rule** (#cfcbc2): row dividers and control borders.
- **Parchment Ink** (#f6e6d8) and **Faded Cloth Ink** (#ecc8b8): text on the rail (6.1:1 and 4.8:1 on cloth).

### Named Rules
**The One Thread Rule.** Turmeric marks exactly one thing per screen: the primary action (Add transaction), plus the rail's binding edge and budget warnings. It is never text or a focus ring on light surfaces (2.2:1).

**The Signed Amount Rule.** Money out is vermilion, money in indigo, and every amount also carries its sign or sits under a heading that names its direction. Colour never carries meaning alone.

## Typography

**Body and display font:** Mukta (Ek Type), self-hosted, with ui-sans-serif / system-ui fallback.

**Character:** a single Indian-designed sans in several weights: calm in prose, firm in figures. Mukta was chosen over Hind because Hind has no tabular figures.

### Hierarchy
- **Display** (600, 1.875rem, 1.2, -0.025em): the one page title (month or section name).
- **Title** (600, 1.125rem, 1.2): the heading at the top of each sheet.
- **Body** (400, 0.9375rem, 1.5): prose, table rows and controls.
- **Label** (500, 0.875rem): field labels and column heads, sentence case.
- **Landing question** (600, 2.25rem on phones / 2.75rem from 640px, 1.05, -0.025em): only the two questions that open the signed-out landing page. Persuade-surface step; never used inside the app.
- **Money ramp** (tabular, lining figures): xl 2.5rem/600 (the struck balance, an account's balance), lg 1.75rem/600 (page totals, "You have"), md 1.25rem/600 (account rows on lists), sm 0.9375rem/500 (table amounts), xs 0.8125rem (footnotes).

### Named Rules
**The Fixed Ramp Rule.** Amounts use only the five money sizes, always with tabular lining figures and through `formatMoney`. Hierarchy is never improvised.

## Layout

Desktop first. A 240px vermilion rail sits beside the work area, pinned while the page scrolls. The work area has 40px side padding (16px on phones) and a 1240px maximum width. Sections stack with 32px gaps; sheets have 24px padding (28px for the spread's pages). The lower overview uses a 12-column grid (accounts 5, budgets 7). Below 1024px the rail folds into a sticky top bar with Add and a native disclosure menu; the spread stacks money in, the spine, then money out; the account column of tables moves under each description below 640px.

## Elevation & Depth

Flat, with one depth: sheets lift off the page.

### Shadow Vocabulary
- **Sheet** (`box-shadow: 0 1px 2px rgb(38 48 92 / 0.06), 0 8px 24px -12px rgb(38 48 92 / 0.18)`): every working sheet.
- **Add button** (`box-shadow: 0 2px 6px -2px rgb(0 0 0 / 0.35)`): lifts the turmeric action off the rail.

### Named Rules
**The Quiet Sheet Rule.** Sheets lift off the page with one soft indigo-tinted shadow; nothing else casts one. Depth says "this is a page of the book", never "click me".

## Shapes

Soft but not rounded: 10px sheets, 6px controls, full pills only for small state chips. Hairline rules divide rows; the only thick line is the 4px turmeric thread along the rail (or under the mobile bar), a deliberate world signature. Dashed turmeric marks the spine of the spread.

## Components

### Buttons
- **Shape:** 6px corners.
- **Primary:** indigo with white text, 10px × 20px, semibold. Hover Night Indigo. Labels name the action ("Save expense", "Open account", "Record adjustment").
- **Add transaction:** the turmeric button, indigo text, plus icon, at the foot of the rail and in the mobile bar.
- **Quiet:** indigo text, no fill, hover on the ruled-line tone.
- **Danger:** vermilion outline that fills on hover; always behind a confirming second step that says what happens.
- **Focus:** 2px indigo ring, 2px offset (turmeric on the rail).

### Cards / Containers (sheets)
- **Corner Style:** 10px.
- **Background:** Clean Sheet on the Ledger Page.
- **Shadow Strategy:** the sheet shadow only.
- **Internal Padding:** 24px.

### Inputs / Fields
- **Style:** white, 1px Pressed Rule border, 6px corners, 10px × 12px; vermilion caret.
- **Label, hint, error:** label above; hint beside it (`aria-describedby`, not part of the name); error below in vermilion, linked, with `aria-invalid`.
- **Amounts:** text inputs with `inputmode="decimal"` and tabular figures; parsing accepts grouping and currency symbols.

### Navigation
- Vermilion rail; items in Faded Cloth Ink, the current page on Ledger Spine Red with `aria-current="page"`; lucide icons at 18px, 1.75 stroke. Mobile: sticky top bar, "Menu" opens a full-width panel.

### Facing Pages (signature)
The overview's spread: "Money in" by income source on the left, "Money out" by top-level category on the right, each with its total; the spine between them shows "Kept this month" (or "Overspent this month") on a dashed turmeric thread. Each line opens its transactions; unassigned and folded lines stay plain text.

### Budget Line
Name, "spent of limit" in tabular figures, an 8px bar (indigo on track, turmeric at the warning point, vermilion over the limit), and the state in words ("₹7,120.00 left", "₹1,900.00 over"); exposed as a progressbar with a value text.

### Setup checklist
- **What it is:** a first-run sheet on the overview titled "Set up your books", between the header and the spread. It only appears on the current month.
- **Steps:** an ordered list of four:
  1. Accounts.
  2. Income sources (optional).
  3. A budget (optional).
  4. A first transaction.
- **Ticking off:** each step completes itself from the user's real data. Open steps are indigo links with a one-line reason and "Optional" in words. Finished steps show a lucide check and "Done", never a strike-through, because strike-through means cancelled.
- **Progress:** stated in words ("1 of 4 done"), with no bar.
- **Hiding:** the sheet disappears when every step is done, or through "Hide these steps", which is stored per user in a cookie.
- **Never gamified.**

Empty choices point to where they are filled: the Income source field says "None yet. Add an income source", and a transfer with a single account explains that it needs two.

### Landing page (signed-out home)
- **Structure:** one ledger opened. The first spread puts "How much do I have?" (accounts and their total) on the left page and "Am I on track this month?" (budgets) on the right page.
- **Spine:** a vermilion bookcloth spine edged with turmeric thread carries the wordmark as the h1, one line of purpose, and the actions: Create your account (turmeric) and Sign in. On phones the spine becomes the top band, with the thread under it.
- **Below the fold:** further spreads, never cards: the facing-page money in/out with Kept at a dashed spine, a ledger excerpt with balance after each entry, the add-transaction form as it looks, then privacy and Coming next on facing pages with a dashed thread gutter.
- **Example data:** every figure comes from `features/welcome/example-ledger.ts`, is labelled "Example", and reconciles; a test proves it.
- **Motion:** one moment only. The budget bars fill once from the left (720ms ease-out-expo, staggered 90ms), and stay static under reduced motion.

### Buttons that navigate
Actions that open another screen (Open an account, Set a budget, Add an income source) are links drawn as secondary buttons: white, Pressed Rule border, indigo text (`components/action-link.tsx`). Empty states say what is missing, then offer that one action (`EmptyState`). Navigation links (All accounts, Show older) stay text links.

### Status note
Pending transactions show "Confirming" with a small pulsing turmeric dot (still under reduced motion: the animation stops); cancelled ones are struck through with "Cancelled: <reason>" in vermilion.

## Do's and Don'ts

### Do:
- **Do** format every amount with `formatMoney` (exact decimal strings, the reader's locale, a true minus sign).
- **Do** say states in words: Confirming, Cancelled: <reason>, ₹1,900.00 over.
- **Do** use the indigo focus ring (2px, 2px offset) on light surfaces and turmeric only on the vermilion rail.
- **Do** keep controls standard web controls: native selects, date inputs, radio groups, disclosure menus.
- **Do** give every money list a total, and let every line open to its transactions.

### Don't:
- **Don't** use ruled-paper textures, stitched borders or other ledger costume on working screens.
- **Don't** put eyebrow labels above headings or join meta with middle dots.
- **Don't** draw icons with Unicode glyphs or emoji; use the lucide set at 1.75 stroke.
- **Don't** gamify: no streaks, badges, confetti or mascots.
- **Don't** spread turmeric beyond the primary action, the rail edge and warnings.

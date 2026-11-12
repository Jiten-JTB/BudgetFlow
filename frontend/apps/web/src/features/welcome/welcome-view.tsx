import { formatMoney } from "@budgetflow/money";
import { Check, Smartphone } from "lucide-react";
import Link from "next/link";
import { Money } from "@/components/money";
import { EXAMPLE } from "./example-ledger";

const L = EXAMPLE.locale;
const C = EXAMPLE.currency;
const fmt = (amount: string) => formatMoney(amount, C, { locale: L });

const page = "bg-panel shadow-sheet";
const createClass =
  "inline-flex w-full items-center justify-center rounded-md bg-thread px-5 py-3 font-semibold text-indigo shadow-[0_2px_6px_-2px_rgb(0_0_0/0.35)] hover:brightness-105";

/**
 * The signed-out home page. One ledger, opened: the two questions people bring to a money app on facing pages, the
 * book's vermilion spine carrying the actions, and the next spreads below showing how the numbers are kept.
 * Every figure is example data, labelled as such, and reconciles (see example-ledger.ts).
 */
export function WelcomeView() {
  return (
    <main className="bg-page">
      <FirstSpread />
      <div className="mx-auto flex max-w-[1240px] flex-col gap-16 px-4 pb-20 sm:px-10 lg:gap-24">
        <WhereItWent />
        <KeptExactly />
        <LogItFast />
        <Closing />
      </div>
      <footer className="border-t-4 border-thread bg-cloth px-4 py-8 text-cloth-ink sm:px-10">
        <div className="on-cloth mx-auto flex max-w-[1240px] flex-wrap items-center justify-between gap-4">
          <span className="text-lg font-bold tracking-tight">Budget Flow</span>
          <nav aria-label="Account" className="flex flex-wrap items-center gap-3">
            <Link href="/api/auth/login" className="rounded-md px-4 py-2 font-medium text-cloth-ink hover:bg-cloth-deep">Sign in</Link>
            <Link href="/signup" className="rounded-md bg-thread px-4 py-2 font-semibold text-indigo hover:brightness-105">Create your account</Link>
          </nav>
        </div>
      </footer>
    </main>
  );
}

/** First viewport: How much do I have? | spine | Am I on track this month? */
function FirstSpread() {
  return (
    <div className="mx-auto grid max-w-[1240px] gap-0 px-4 pt-4 pb-16 sm:px-10 sm:pt-10 lg:grid-cols-[1fr_17rem_1fr] lg:pb-24">
      <section aria-labelledby="have" className={`${page} order-2 rounded-t-sheet p-7 sm:p-10 lg:order-1 lg:rounded-l-sheet lg:rounded-tr-none lg:p-12`}>
        <h2 id="have" className="text-[2.25rem] leading-[1.05] font-semibold tracking-tight sm:text-[2.75rem]">How much do I have?</h2>
        <p className="mt-2 text-lg text-mute">Every account, its exact balance, one total.</p>
        <p className="mt-8 mb-3">
          <Money amount={EXAMPLE.accountsTotal} currency={C} locale={L} size="xl" tone="in" />
          <span className="ml-2 text-mute">across {EXAMPLE.accounts.length} accounts</span>
        </p>
        <ul>
          {EXAMPLE.accounts.map((a) => (
            <li key={a.name} className="flex items-baseline justify-between gap-4 border-t border-rule py-3">
              <span>{a.name}</span>
              <Money amount={a.balance} currency={C} locale={L} tone="auto" />
            </li>
          ))}
        </ul>
        <p className="mt-4 text-sm text-mute">Example figures. A card balance is money owed, so it counts against the total.</p>
      </section>

      <div className="on-cloth order-1 flex flex-col items-center justify-between gap-8 rounded-t-sheet border-x-0 border-b-4 border-thread bg-cloth px-7 py-8 text-center text-cloth-ink lg:order-2 lg:rounded-none lg:border-x-4 lg:border-b-0 lg:px-7 lg:py-12">
        <h1 className="text-[1.75rem] font-bold tracking-tight text-cloth-ink lg:text-[1.875rem]">Budget Flow</h1>
        <p className="max-w-xs text-lg leading-snug">
          A merchant’s ledger for your own money: what came in, what went out, and what you kept.
        </p>
        <div className="flex w-full max-w-xs flex-col gap-2">
          <Link href="/signup" className={createClass}>Create your account</Link>
          <Link href="/api/auth/login" className="rounded-md px-5 py-3 font-medium text-cloth-ink hover:bg-cloth-deep">Sign in</Link>
        </div>
      </div>

      <section aria-labelledby="track" className={`${page} order-3 rounded-b-sheet p-7 sm:p-10 lg:rounded-r-sheet lg:rounded-bl-none lg:p-12`}>
        <h2 id="track" className="text-[2.25rem] leading-[1.05] font-semibold tracking-tight sm:text-[2.75rem]">Am I on track this month?</h2>
        <p className="mt-2 text-lg text-mute">Budgets that warn early, in words, and never block a purchase.</p>
        <ul className="mt-8 flex flex-col gap-6">
          {EXAMPLE.budgets.map((b, i) => <BudgetLine key={b.name} budget={b} index={i} />)}
        </ul>
        <p className="mt-6 text-sm text-mute">Example figures for November.</p>
      </section>
    </div>
  );
}

function BudgetLine({ budget: b, index }: { budget: (typeof EXAMPLE.budgets)[number]; index: number }) {
  const over = b.state === "EXCEEDED";
  const detail = over ? `${fmt(b.remaining.replace("-", ""))} over` : `${fmt(b.remaining)} left`;
  const fill = over ? "bg-cloth" : b.state === "WARNING" ? "bg-thread" : "bg-indigo";
  const note = b.state === "WARNING" ? `${detail}, past your 80% mark` : detail;
  return (
    <li className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-4">
        <span className="font-medium">{b.name}</span>
        <span className="num text-sm text-mute">{fmt(b.spent)} of {fmt(b.limit)}</span>
      </div>
      <div role="progressbar" aria-label={b.name} aria-valuemin={0} aria-valuemax={Math.max(100, b.percent)} aria-valuenow={b.percent}
        aria-valuetext={`${fmt(b.spent)} of ${fmt(b.limit)}, ${detail}`} className="h-2 overflow-hidden rounded-full bg-rule">
        <div className={`fill-in h-full rounded-full ${fill}`} style={{ width: `${Math.min(100, b.percent)}%`, animationDelay: `${150 + index * 90}ms` }} />
      </div>
      <span className={`num text-sm ${over ? "font-medium text-cloth" : b.state === "WARNING" ? "text-thread-ink" : "text-mute"}`}>{note}</span>
    </li>
  );
}

/** The app's signature: money in by source, money out by category, the balance struck at the spine. */
function WhereItWent() {
  return (
    <section aria-labelledby="where" className="flex flex-col gap-6">
      <SpreadHeading id="where" title="Where it came from, where it went"
        lede="Income is kept by its source, not lumped together; spending by what it was for. What’s left is struck between them." />
      <div className="grid overflow-hidden rounded-sheet bg-panel shadow-sheet md:grid-cols-[1fr_13rem_1fr]">
        <LedgerPage title="Money in" total={EXAMPLE.moneyIn.total} rows={EXAMPLE.moneyIn.rows} tone="in" />
        <div className="flex flex-col items-center justify-center gap-1 border-y-2 border-dashed border-thread bg-page px-6 py-8 text-center md:border-x-2 md:border-y-0">
          <span className="text-sm text-mute">Kept this month</span>
          <Money amount={EXAMPLE.kept} currency={C} locale={L} size="xl" tone="in" />
        </div>
        <LedgerPage title="Money out" total={EXAMPLE.moneyOut.total} rows={EXAMPLE.moneyOut.rows} tone="out" />
      </div>
      <p className="text-sm text-mute">Example month. In the app, every line opens to the transactions behind it.</p>
    </section>
  );
}

function LedgerPage({ title, total, rows, tone }: { title: string; total: string; rows: { label: string; amount: string }[]; tone: "in" | "out" }) {
  return (
    <div className="flex flex-col gap-4 p-7">
      <div className="flex items-baseline justify-between gap-4">
        <h3 className="text-lg font-semibold">{title}</h3>
        <Money amount={total} currency={C} locale={L} size="lg" tone={tone} />
      </div>
      <ul>
        {rows.map((r) => (
          <li key={r.label} className="flex items-baseline justify-between gap-4 border-t border-rule py-2.5">
            <span>{r.label}</span>
            <Money amount={r.amount} currency={C} locale={L} />
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Trust: every balance change is a ledger line, with the balance after it. */
function KeptExactly() {
  return (
    <section aria-labelledby="exact" className="grid gap-6 lg:grid-cols-[minmax(0,26rem)_1fr] lg:gap-12">
      <div className="flex flex-col gap-4 lg:pt-2">
        <h2 id="exact" className="text-[1.875rem] leading-tight font-semibold tracking-tight">Every change is a line in the ledger</h2>
        <p className="text-lg text-mute">Balances are kept exact to the paisa, never rounded along the way.</p>
        <ul className="flex flex-col gap-3">
          {[
            "A correction is its own visible line, never a silent edit.",
            "Each entry shows the balance it left behind, so you can check it against your bank.",
            "If something can’t be recorded, it says so and why, and moves no money.",
          ].map((t) => (
            <li key={t} className="flex gap-3"><Check aria-hidden size={20} strokeWidth={1.75} className="mt-0.5 shrink-0 text-indigo" />{t}</li>
          ))}
        </ul>
      </div>
      <figure className="rounded-sheet bg-panel p-6 shadow-sheet sm:p-7">
        <figcaption className="mb-3 flex items-baseline justify-between gap-4">
          <span className="text-lg font-semibold text-indigo">{EXAMPLE.ledger.account}</span>
          <span className="text-sm text-mute">Example ledger</span>
        </figcaption>
        <table className="w-full text-left">
          <thead className="text-sm text-mute">
            <tr>
              <th scope="col" className="w-20 pb-2 font-medium">Date</th>
              <th scope="col" className="pb-2 font-medium">Entry</th>
              <th scope="col" className="pb-2 text-right font-medium">Amount</th>
              <th scope="col" className="hidden pb-2 text-right font-medium sm:table-cell">Balance</th>
            </tr>
          </thead>
          <tbody>
            {EXAMPLE.ledger.entries.map((e) => (
              <tr key={e.date + e.description} className="border-t border-rule">
                <td className="num py-2.5 text-sm text-mute">{e.date}</td>
                <td className="py-2.5 pr-3">{e.description}</td>
                <td className="py-2.5 text-right"><Money amount={e.amount} currency={C} locale={L} tone={e.amount.startsWith("-") ? "out" : "in"} sign="always" /></td>
                <td className="hidden py-2.5 text-right text-mute sm:table-cell"><Money amount={e.balanceAfter} currency={C} locale={L} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </figure>
    </section>
  );
}

/** Speed: the one form people use every day, shown as it looks. */
function LogItFast() {
  return (
    <section aria-labelledby="log" className="grid gap-6 lg:grid-cols-[1fr_minmax(0,26rem)] lg:gap-12">
      <figure className="order-2 rounded-sheet bg-panel p-6 shadow-sheet sm:p-7 lg:order-1">
        <figcaption className="sr-only">Example of the Add a transaction form, with ₹17,500 typed as an expense</figcaption>
        <div aria-hidden className="flex flex-col gap-5">
          <div className="inline-flex w-fit rounded-md border border-rule-strong p-1 text-sm font-medium text-mute">
            <span className="rounded bg-indigo px-4 py-1.5 text-white">Expense</span>
            <span className="px-4 py-1.5">Income</span>
            <span className="px-4 py-1.5">Transfer</span>
          </div>
          <div>
            <span className="text-sm font-medium">Amount <span className="text-mute">INR</span></span>
            <div className="num mt-1.5 max-w-xs rounded-md border-2 border-indigo px-3 py-2.5 text-money-lg font-semibold text-indigo">17,500</div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Fake label="Account" value="HDFC Savings (INR)" />
            <Fake label="Category" value="Housing › Rent" />
          </div>
          <span className="w-fit rounded-md bg-indigo px-5 py-2.5 font-semibold text-white">Save expense</span>
        </div>
      </figure>
      <div className="order-1 flex flex-col gap-4 lg:order-2 lg:pt-2">
        <h2 id="log" className="text-[1.875rem] leading-tight font-semibold tracking-tight">Write it down as it happens</h2>
        <p className="text-lg text-mute">
          Type the amount the way you say it; 1,75,000 stays 1,75,000. Pick the account and what it was for, and save.
        </p>
        <p className="text-mute">It works in your phone’s browser, so the entry gets made while you still remember it.</p>
        <p className="mt-2 text-sm text-mute">Example form.</p>
      </div>
    </section>
  );
}

function Fake({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-sm font-medium">{label}</span>
      <div className="mt-1.5 rounded-md border border-rule-strong px-3 py-2.5">{value}</div>
    </div>
  );
}

/** Privacy and what's next, then the last call to open the books. */
function Closing() {
  return (
    <div className="grid overflow-hidden rounded-sheet bg-panel shadow-sheet md:grid-cols-[1fr_2px_1fr]">
      <section aria-labelledby="privacy" className="flex flex-col gap-4 p-7 sm:p-9">
        <h2 id="privacy" className="text-[1.875rem] leading-tight font-semibold tracking-tight">Your money, your data</h2>
        <ul className="flex flex-col gap-3">
          {[
            "Your sign-in tokens stay on our server and never reach your browser’s scripts.",
            "We confirm your email before your books open.",
            "Delete your account from Settings at any time, and everything in it is erased.",
          ].map((t) => (
            <li key={t} className="flex gap-3"><Check aria-hidden size={20} strokeWidth={1.75} className="mt-0.5 shrink-0 text-indigo" />{t}</li>
          ))}
        </ul>
      </section>
      <div aria-hidden className="bg-[repeating-linear-gradient(var(--color-thread)_0_10px,transparent_10px_18px)] max-md:h-0.5 max-md:bg-[repeating-linear-gradient(90deg,var(--color-thread)_0_10px,transparent_10px_18px)]" />
      <section aria-labelledby="next" className="flex flex-col gap-4 p-7 sm:p-9">
        <h2 id="next" className="text-[1.875rem] leading-tight font-semibold tracking-tight">Coming next</h2>
        <p className="flex gap-3">
          <Smartphone aria-hidden size={20} strokeWidth={1.75} className="mt-0.5 shrink-0 text-indigo" />
          Android and iOS apps are on the way. Until then, Budget Flow works in your phone’s browser.
        </p>
        <div className="mt-auto flex flex-wrap items-center gap-3 pt-4">
          <Link href="/signup" className="rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549]">
            Create your account
          </Link>
          <Link href="/api/auth/login" className="rounded-md px-4 py-2.5 font-medium text-indigo hover:bg-rule">Sign in</Link>
        </div>
      </section>
    </div>
  );
}

function SpreadHeading({ id, title, lede }: { id: string; title: string; lede: string }) {
  return (
    <div className="flex max-w-2xl flex-col gap-2">
      <h2 id={id} className="text-[1.875rem] leading-tight font-semibold tracking-tight">{title}</h2>
      <p className="text-lg text-mute">{lede}</p>
    </div>
  );
}

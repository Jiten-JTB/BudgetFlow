import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { Money } from "@/components/money";
import { BudgetRow } from "@/components/budget-row";
import { TransactionRow } from "@/components/transaction-row";
import { shiftMonth } from "@/lib/month";
import type { LedgerPage, Overview } from "./build-overview";

type Props = {
  overview: Overview;
  month: string;
  monthTitle: string;
  locale: string;
  accountNames: Record<string, string>;
  categoryNames: Record<string, string>;
  isCurrentMonth: boolean;
};

export function OverviewView({ overview: o, month, monthTitle, locale, accountNames, categoryNames, isCurrentMonth }: Props) {
  const accountCount = o.accounts.items.length;
  const [primaryTotal, ...otherTotals] = o.accounts.totals;
  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-wrap items-end justify-between gap-6">
        <div className="flex items-center gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">{monthTitle}</h1>
          <nav aria-label="Month" className="flex gap-1">
            <MonthStep href={`/?month=${shiftMonth(month, -1)}`} label="Previous month"><ChevronLeft aria-hidden size={18} /></MonthStep>
            {isCurrentMonth ? (
              <span aria-hidden className="grid size-8 place-items-center text-rule-strong"><ChevronRight size={18} /></span>
            ) : (
              <MonthStep href={`/?month=${shiftMonth(month, 1)}`} label="Next month"><ChevronRight aria-hidden size={18} /></MonthStep>
            )}
          </nav>
        </div>
        {primaryTotal && (
          <p className="text-mute">
            You have{" "}
            <Money amount={primaryTotal.total} currency={primaryTotal.currency} locale={locale} size="lg" tone="auto" whole />
            {otherTotals.map((t) => (
              <span key={t.currency}> and <Money amount={t.total} currency={t.currency} locale={locale} size="md" tone="auto" whole /></span>
            ))}{" "}
            across {accountCount} {accountCount === 1 ? "account" : "accounts"}
          </p>
        )}
      </header>

      <div className="flex flex-col gap-3">
        <div className="grid overflow-hidden rounded-sheet bg-panel shadow-sheet md:grid-cols-[1fr_13rem_1fr]">
          <LedgerSide id="money-in" title="Money in" page={o.moneyIn} currency={o.currency} locale={locale} tone="in"
            hrefFor={(key) => (key === "none" ? null : `/transactions?month=${month}&incomeSourceId=${key}`)}
            empty={{ text: `No income recorded in ${monthTitle} yet.`, action: "Record income", href: "/transactions/new?kind=INCOME" }} />
          <Spine kept={o.kept} currency={o.currency} locale={locale} />
          <LedgerSide id="money-out" title="Money out" page={o.moneyOut} currency={o.currency} locale={locale} tone="out"
            hrefFor={(key) => (key === "none" || key === "rest" ? null : `/transactions?month=${month}&kind=EXPENSE&categoryId=${key}`)}
            empty={{ text: `Nothing spent in ${monthTitle} yet.`, action: "Record an expense", href: "/transactions/new?kind=EXPENSE" }} />
        </div>
        {o.pendingCount > 0 && (
          <p className="text-sm text-mute">
            {o.pendingCount === 1
              ? "1 transaction is still being confirmed and isn’t in these totals yet."
              : `${o.pendingCount} transactions are still being confirmed and aren’t in these totals yet.`}
          </p>
        )}
        {o.otherCurrencies.map((c) => (
          <p key={c.currency} className="text-sm text-mute">
            Also in {c.currency}: <Money amount={c.moneyIn} currency={c.currency} locale={locale} size="xs" tone="in" /> in,{" "}
            <Money amount={c.moneyOut} currency={c.currency} locale={locale} size="xs" tone="out" /> out (not included above).
          </p>
        ))}
      </div>

      <div className="grid gap-8 lg:grid-cols-12">
        <section aria-labelledby="accounts-heading" className="rounded-sheet bg-panel p-6 shadow-sheet lg:col-span-5">
          <div className="mb-3 flex items-baseline justify-between">
            <h2 id="accounts-heading" className="text-lg font-semibold">Accounts</h2>
            <Link href="/accounts" className="text-sm text-indigo underline">All accounts</Link>
          </div>
          {o.accounts.items.length === 0 ? (
            <p className="text-mute">No accounts yet. <Link href="/accounts/new" className="text-indigo underline">Open an account</Link></p>
          ) : (
            <ul>
              {o.accounts.items.map((a) => (
                <li key={a.id} className="flex items-baseline justify-between gap-4 border-t border-rule py-2.5 first:border-t-0">
                  <Link href={`/accounts/${a.id}`} className="truncate hover:underline">{a.name}</Link>
                  <Money amount={a.balance} currency={a.currency} locale={locale} tone="auto" />
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="budgets-heading" className="rounded-sheet bg-panel p-6 shadow-sheet lg:col-span-7">
          <div className="mb-3 flex items-baseline justify-between">
            <h2 id="budgets-heading" className="text-lg font-semibold">Budgets</h2>
            <Link href="/budgets" className="text-sm text-indigo underline">All budgets</Link>
          </div>
          {o.budgets.length === 0 ? (
            <p className="text-mute">No budgets yet. <Link href="/budgets/new" className="text-indigo underline">Set a budget</Link> to get a warning before you overspend.</p>
          ) : (
            <ul className="flex flex-col gap-4">
              {o.budgets.map((b) => <BudgetRow key={b.budget.id} status={b} locale={locale} />)}
            </ul>
          )}
        </section>
      </div>

      <section aria-labelledby="recent-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 id="recent-heading" className="text-lg font-semibold">Recent transactions</h2>
          <Link href="/transactions" className="text-sm text-indigo underline">All transactions</Link>
        </div>
        {o.recent.length === 0 ? (
          <p className="text-mute">No transactions this month.</p>
        ) : (
          <table className="w-full text-left">
            <thead className="text-sm text-mute">
              <tr>
                <th scope="col" className="w-24 pb-2 font-medium">Date</th>
                <th scope="col" className="pb-2 font-medium">Description</th>
                <th scope="col" className="hidden pb-2 font-medium sm:table-cell">Account</th>
                <th scope="col" className="pb-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody>
              {o.recent.map((t) => (
                <TransactionRow key={t.id} t={t} locale={locale} accountNames={accountNames} categoryNames={categoryNames} />
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

function MonthStep({ href, label, children }: { href: string; label: string; children: React.ReactNode }) {
  return (
    <Link href={href} aria-label={label} className="grid size-8 place-items-center rounded-md text-indigo hover:bg-rule">
      {children}
    </Link>
  );
}

function LedgerSide({ id, title, page, currency, locale, tone, empty, hrefFor }: {
  id: string; title: string; page: LedgerPage; currency: string; locale: string; tone: "in" | "out";
  /** Where a line opens to; null when no single filter matches it (unassigned, folded remainder). */
  hrefFor: (key: string) => string | null;
  empty: { text: string; action: string; href: string };
}) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4 p-7">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id={id} className="text-lg font-semibold">{title}</h2>
        <Money amount={page.total} currency={currency} locale={locale} size="lg" tone={tone} />
      </div>
      {page.rows.length === 0 ? (
        <p className="text-mute">
          {empty.text} <Link href={empty.href} className="text-indigo underline">{empty.action}</Link>
        </p>
      ) : (
        <ul>
          {page.rows.map((r) => (
            <li key={r.key} className="border-t border-rule">
              {(() => {
                const href = hrefFor(r.key);
                const line = (
                  <>
                    <span className="truncate">{r.label}</span>
                    <Money amount={r.amount} currency={currency} locale={locale} tone={tone} />
                  </>
                );
                return href ? (
                  <Link href={href} className="-mx-2 flex items-baseline justify-between gap-4 rounded px-2 py-2.5 hover:bg-page">{line}</Link>
                ) : (
                  <div className="flex items-baseline justify-between gap-4 py-2.5">{line}</div>
                );
              })()}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Where the two pages meet: the turmeric thread, and the balance struck between them. */
function Spine({ kept, currency, locale }: { kept: string; currency: string; locale: string }) {
  const overspent = kept.startsWith("-");
  return (
    <div className="relative flex flex-col items-center justify-center gap-1 border-y border-rule bg-page/60 px-4 py-8 text-center md:border-x md:border-y-0">
      <span aria-hidden className="absolute inset-y-0 left-1/2 hidden w-0 border-l-2 border-dashed border-thread md:block" />
      <div className="relative flex flex-col items-center gap-1 bg-page px-2 py-3">
        <span className="text-sm text-mute">{overspent ? "Overspent this month" : "Kept this month"}</span>
        <Money amount={kept} currency={currency} locale={locale} size="xl" tone={overspent ? "out" : "in"} />
      </div>
    </div>
  );
}


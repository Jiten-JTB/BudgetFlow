import Link from "next/link";
import { TransactionRow } from "@/components/transaction-row";
import { requestLocale } from "@/lib/locale";
import { currentMonth, monthLabel, monthRange, parseMonth } from "@/lib/month";
import { ActionLink, EmptyState } from "@/components/action-link";
import { serverApi } from "@/server/api";

export const metadata = { title: "Transactions" };

type Search = { month?: string; kind?: string; accountId?: string; categoryId?: string; incomeSourceId?: string; cursor?: string };
const KINDS = [["", "All types"], ["EXPENSE", "Expenses"], ["INCOME", "Income"], ["TRANSFER", "Transfers"]] as const;

export default async function TransactionsPage({ searchParams }: { searchParams: Promise<Search> }) {
  const q = await searchParams;
  const api = serverApi();
  const [me, locale] = await Promise.all([api.me.get(), requestLocale()]);
  const month = parseMonth(q.month) ?? currentMonth(me.timezone);
  const { from, to } = monthRange(month);
  const kind = KINDS.some(([v]) => v === q.kind) ? q.kind : undefined;

  const [page, accounts, categories, sources] = await Promise.all([
    api.transactions.list({ from, to, kind, accountId: q.accountId || undefined, categoryId: q.categoryId || undefined,
                            incomeSourceId: q.incomeSourceId || undefined, cursor: q.cursor || undefined, limit: 50 }),
    api.accounts.list({ includeArchived: true }),
    api.categories.list({ includeArchived: true }),
    api.incomeSources.list({ includeArchived: true }),
  ]);
  const source = q.incomeSourceId ? sources.find((s) => s.id === q.incomeSourceId) : undefined;
  const accountNames = Object.fromEntries(accounts.map((a) => [a.id, a.name]));
  const categoryNames = Object.fromEntries(categories.map((c) => [c.id, c.name]));
  const filters = new URLSearchParams(Object.entries({ month, kind, accountId: q.accountId, categoryId: q.categoryId, incomeSourceId: q.incomeSourceId })
    .filter((e): e is [string, string] => Boolean(e[1])));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-semibold tracking-tight">Transactions</h1>
        <Link href="/transactions/new" className="rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549]">Add transaction</Link>
      </div>

      <form method="get" className="flex flex-wrap items-end gap-3 rounded-sheet bg-panel p-4 shadow-sheet" aria-label="Filter transactions">
        <Filter label="Month">
          <input type="month" name="month" defaultValue={month} className={filterClass} />
        </Filter>
        <Filter label="Type">
          <select name="kind" defaultValue={kind ?? ""} className={filterClass}>
            {KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </Filter>
        <Filter label="Account">
          <select name="accountId" defaultValue={q.accountId ?? ""} className={filterClass}>
            <option value="">All accounts</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}{a.status === "ARCHIVED" ? " (archived)" : ""}</option>)}
          </select>
        </Filter>
        <Filter label="Category">
          <select name="categoryId" defaultValue={q.categoryId ?? ""} className={filterClass}>
            <option value="">All categories</option>
            {categories.filter((c) => !c.archived).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Filter>
        {source && <input type="hidden" name="incomeSourceId" value={source.id} />}
        <button type="submit" className="rounded-md border border-indigo px-4 py-2 font-medium text-indigo hover:bg-indigo hover:text-white">Show</button>
        {source && (
          <p className="flex items-center gap-2 text-sm">
            Income from <strong className="font-semibold">{source.name}</strong>
            <Link href={`/transactions?month=${month}`} className="text-indigo underline">Show all</Link>
          </p>
        )}
      </form>

      <section aria-labelledby="list-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <h2 id="list-heading" className="mb-3 text-lg font-semibold">{monthLabel(month, locale)}</h2>
        {page.items.length === 0 ? (
          <EmptyState text="No transactions match these filters." action="Add a transaction" href="/transactions/new" />
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
              {page.items.map((t) => <TransactionRow key={t.id} t={t} locale={locale} accountNames={accountNames} categoryNames={categoryNames} />)}
            </tbody>
          </table>
        )}
        {page.nextCursor && (
          <Link href={`/transactions?${filters.toString()}&cursor=${encodeURIComponent(page.nextCursor)}`}
            className="mt-4 inline-block font-medium text-indigo underline">Show older</Link>
        )}
      </section>
    </div>
  );
}

const filterClass = "rounded-md border border-rule-strong bg-panel px-3 py-2 text-ink";

function Filter({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-sm font-medium text-ink">
      {label}
      {children}
    </label>
  );
}

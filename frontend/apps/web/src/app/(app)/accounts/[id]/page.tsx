import { ApiError } from "@budgetflow/api-client";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Money } from "@/components/money";
import { AdjustBalanceForm, ArchiveAccountButton, RenameAccountForm } from "@/features/accounts/account-forms";
import { ACCOUNT_TYPES } from "@/features/accounts/account-types";
import { requestLocale } from "@/lib/locale";
import { serverApi } from "@/server/api";

const TYPE_LABEL = Object.fromEntries(ACCOUNT_TYPES);
const ENTRY_LABEL = { OPENING: "Opening balance", TRANSACTION: "Transaction", TRANSFER: "Transfer", ADJUSTMENT: "Adjustment", REVERSAL: "Reversal" } as const;

export default async function AccountPage({ params, searchParams }: {
  params: Promise<{ id: string }>; searchParams: Promise<{ before?: string }>;
}) {
  const { id } = await params;
  const { before } = await searchParams;
  const api = serverApi();
  const locale = await requestLocale();
  const [account, entries] = await Promise.all([
    api.accounts.get(id).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404) notFound(); throw e; }),
    api.accounts.entries(id, { before, limit: 50 }),
  ]);
  const timeFormat = new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", year: "numeric" });
  const archived = account.status === "ARCHIVED";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <Link href="/accounts" className="text-sm text-indigo underline">All accounts</Link>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h1 className="text-3xl font-semibold tracking-tight">
            {account.name}
            <span className="ml-3 text-base font-normal text-mute">{TYPE_LABEL[account.type]}</span>
            {archived && <span className="ml-3 rounded-full border border-rule-strong px-2 py-0.5 align-middle text-sm font-normal text-mute">Archived</span>}
          </h1>
          <Money amount={account.balance} currency={account.currency} locale={locale} size="xl" tone="auto" />
        </div>
      </div>

      <section aria-labelledby="ledger-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <h2 id="ledger-heading" className="mb-3 text-lg font-semibold">Ledger</h2>
        {entries.items.length === 0 ? <p className="text-mute">No entries yet.</p> : (
          <table className="w-full text-left">
            <thead className="text-sm text-mute">
              <tr>
                <th scope="col" className="pb-2 font-medium">Date</th>
                <th scope="col" className="pb-2 font-medium">Entry</th>
                <th scope="col" className="pb-2 text-right font-medium">Amount</th>
                <th scope="col" className="hidden pb-2 text-right font-medium sm:table-cell">Balance after</th>
              </tr>
            </thead>
            <tbody>
              {entries.items.map((e) => (
                <tr key={e.id} className="border-t border-rule">
                  <td className="num py-2.5 pr-3 text-sm whitespace-nowrap text-mute">{timeFormat.format(new Date(e.createdAt))}</td>
                  <td className="py-2.5">
                    {e.description ?? ENTRY_LABEL[e.kind]}
                    {e.kind === "REVERSAL" && <span className="ml-2 text-xs text-mute">undoes an earlier entry</span>}
                  </td>
                  <td className="py-2.5 text-right"><Money amount={e.amount} currency={account.currency} locale={locale} tone="auto" sign="always" /></td>
                  <td className="hidden py-2.5 text-right text-mute sm:table-cell"><Money amount={e.balanceAfter} currency={account.currency} locale={locale} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {entries.nextCursor && (
          <Link href={`/accounts/${id}?before=${entries.nextCursor}`} className="mt-4 inline-block font-medium text-indigo underline">Show older</Link>
        )}
      </section>

      {!archived && (
        <div className="grid gap-6 lg:grid-cols-2">
          <section aria-labelledby="adjust-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
            <h2 id="adjust-heading" className="mb-3 text-lg font-semibold">Correct the balance</h2>
            <AdjustBalanceForm account={account} />
          </section>
          <section aria-labelledby="manage-heading" className="flex flex-col gap-6 rounded-sheet bg-panel p-6 shadow-sheet">
            <h2 id="manage-heading" className="text-lg font-semibold">Manage</h2>
            <RenameAccountForm account={account} />
            <ArchiveAccountButton account={account} />
          </section>
        </div>
      )}
    </div>
  );
}

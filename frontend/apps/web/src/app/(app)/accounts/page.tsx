import { sumAmounts } from "@budgetflow/money";
import Link from "next/link";
import { Money } from "@/components/money";
import { ACCOUNT_TYPES } from "@/features/accounts/account-types";
import { requestLocale } from "@/lib/locale";
import { ActionLink, EmptyState } from "@/components/action-link";
import { serverApi } from "@/server/api";

export const metadata = { title: "Accounts" };
const TYPE_LABEL = Object.fromEntries(ACCOUNT_TYPES);

export default async function AccountsPage() {
  const [accounts, locale] = await Promise.all([serverApi().accounts.list({ includeArchived: true }), requestLocale()]);
  const active = accounts.filter((a) => a.status === "ACTIVE");
  const archived = accounts.filter((a) => a.status === "ARCHIVED");
  const currencies = [...new Set(active.map((a) => a.currency))];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-semibold tracking-tight">Accounts</h1>
        <Link href="/accounts/new" className="rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549]">Open account</Link>
      </div>

      <section aria-labelledby="active-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-4">
          <h2 id="active-heading" className="text-lg font-semibold">Active</h2>
          <p className="text-mute">
            Total{" "}
            {currencies.map((c, i) => (
              <span key={c}>{i > 0 && " and "}<Money amount={sumAmounts(active.filter((a) => a.currency === c).map((a) => a.balance))}
                currency={c} locale={locale} size="md" tone="auto" /></span>
            ))}
          </p>
        </div>
        {active.length === 0 ? (
          <EmptyState text="No active accounts." action="Open an account" href="/accounts/new" />
        ) : <AccountList accounts={active} locale={locale} />}
      </section>

      {archived.length > 0 && (
        <section aria-labelledby="archived-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
          <h2 id="archived-heading" className="mb-2 text-lg font-semibold">Archived</h2>
          <AccountList accounts={archived} locale={locale} />
        </section>
      )}
    </div>
  );
}

function AccountList({ accounts, locale }: { accounts: Awaited<ReturnType<ReturnType<typeof serverApi>["accounts"]["list"]>>; locale: string }) {
  return (
    <ul>
      {accounts.map((a) => (
        <li key={a.id} className="border-t border-rule first:border-t-0">
          <Link href={`/accounts/${a.id}`} className="flex items-baseline justify-between gap-4 py-3 hover:bg-page/60">
            <span>
              <span className="font-medium">{a.name}</span>
              <span className="ml-2 text-sm text-mute">{TYPE_LABEL[a.type] ?? a.type}</span>
            </span>
            <Money amount={a.balance} currency={a.currency} locale={locale} size="md" tone="auto" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

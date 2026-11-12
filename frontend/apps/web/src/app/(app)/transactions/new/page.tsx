import { TransactionForm } from "@/features/transactions/transaction-form";
import { ActionLink, EmptyState } from "@/components/action-link";
import { serverApi } from "@/server/api";

export const metadata = { title: "Add a transaction" };

type Kind = "EXPENSE" | "INCOME" | "TRANSFER";

export default async function NewTransactionPage({ searchParams }: { searchParams: Promise<{ kind?: string }> }) {
  const api = serverApi();
  const [me, accounts, categories, incomeSources] = await Promise.all([
    api.me.get(), api.accounts.list(), api.categories.list(), api.incomeSources.list(),
  ]);
  const requested = (await searchParams).kind;
  const kind: Kind = requested === "INCOME" || requested === "TRANSFER" ? requested : "EXPENSE";
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: me.timezone }).format(new Date());

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Add a transaction</h1>
      {accounts.length === 0 ? (
        <div className="rounded-sheet bg-panel p-7 shadow-sheet">
          <EmptyState text="You need an account to record money against." action="Open an account" href="/accounts/new" />
        </div>
      ) : (
        <TransactionForm
          initialKind={kind}
          today={today}
          accounts={accounts.map(({ id, name, currency }) => ({ id, name, currency }))}
          categories={categories.map(({ id, name, kind: k, parentId }) => ({ id, name, kind: k, parentId }))}
          incomeSources={incomeSources.map(({ id, name, defaultAccountId }) => ({ id, name, defaultAccountId }))}
        />
      )}
    </div>
  );
}

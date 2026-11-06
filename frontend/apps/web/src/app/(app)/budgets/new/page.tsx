import { CreateBudgetForm } from "@/features/budgets/budget-forms";
import { serverApi } from "@/server/api";

export const metadata = { title: "Set a budget" };

export default async function NewBudgetPage() {
  const api = serverApi();
  const [me, categories] = await Promise.all([api.me.get(), api.categories.list({ kind: "EXPENSE" })]);
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Set a budget</h1>
      <CreateBudgetForm baseCurrency={me.baseCurrency}
        categories={categories.map(({ id, name, kind, parentId }) => ({ id, name, kind, parentId }))} />
    </div>
  );
}

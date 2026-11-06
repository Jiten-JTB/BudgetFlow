import Link from "next/link";
import { BudgetRow } from "@/components/budget-row";
import { ArchiveBudgetButton, BudgetLimitForm } from "@/features/budgets/budget-forms";
import { requestLocale } from "@/lib/locale";
import { rangeLabel } from "@/lib/month";
import { serverApi } from "@/server/api";

export const metadata = { title: "Budgets" };
const PERIOD = { WEEKLY: "This week", MONTHLY: "This month", YEARLY: "This year" } as const;

export default async function BudgetsPage() {
  const [statuses, locale] = await Promise.all([serverApi().budgets.statuses(), requestLocale()]);
  const order = { EXCEEDED: 0, WARNING: 1, ON_TRACK: 2 } as const;
  const sorted = [...statuses].sort((a, b) => order[a.state] - order[b.state] || b.percentUsed - a.percentUsed);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-3xl font-semibold tracking-tight">Budgets</h1>
        <Link href="/budgets/new" className="rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549]">Set a budget</Link>
      </div>
      {sorted.length === 0 ? (
        <p className="rounded-sheet bg-panel p-7 text-mute shadow-sheet">
          No budgets yet. A budget warns you before a category runs over, without ever blocking a purchase.{" "}
          <Link href="/budgets/new" className="text-indigo underline">Set your first budget</Link>
        </p>
      ) : (
        <ul className="flex flex-col gap-4">
          {sorted.map((s) => (
            <li key={s.budget.id} className="flex flex-col gap-4 rounded-sheet bg-panel p-6 shadow-sheet">
              <p className="text-sm text-mute">{PERIOD[s.budget.period]}: {rangeLabel(s.periodStart, s.periodEnd, locale)}</p>
              <ul><BudgetRow status={s} locale={locale} /></ul>
              <details className="group">
                <summary className="w-fit cursor-pointer text-sm font-medium text-indigo">Change limit or archive</summary>
                <div className="mt-4 flex flex-wrap items-end justify-between gap-4">
                  <BudgetLimitForm budget={{ id: s.budget.id, limit: s.budget.limit, alertThresholdPercent: s.budget.alertThresholdPercent, currency: s.budget.currency }} />
                  <ArchiveBudgetButton budgetId={s.budget.id} name={s.budget.name} />
                </div>
              </details>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

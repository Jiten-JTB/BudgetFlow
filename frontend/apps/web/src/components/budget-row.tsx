import type { BudgetStatus } from "@budgetflow/api-client";
import { formatMoney } from "@budgetflow/money";

/** Spent against the limit for one period, with the state said in words (never colour alone). */
export function BudgetRow({ status: s, locale }: { status: BudgetStatus; locale: string }) {
  const over = s.state === "EXCEEDED";
  const fill = over ? "bg-cloth" : s.state === "WARNING" ? "bg-thread" : "bg-indigo";
  const fmt = (a: string) => formatMoney(a, s.budget.currency, { locale });
  const detail = over ? `${fmt(s.remaining.replace("-", ""))} over` : `${fmt(s.remaining)} left`;
  return (
    <li className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-4">
        <span className="font-medium">{s.budget.name}</span>
        <span className="num text-sm text-mute">{fmt(s.spent)} of {fmt(s.budget.limit)}</span>
      </div>
      <div
        role="progressbar"
        aria-label={s.budget.name}
        aria-valuemin={0}
        aria-valuemax={Math.max(100, s.percentUsed)}
        aria-valuenow={s.percentUsed}
        aria-valuetext={`${fmt(s.spent)} of ${fmt(s.budget.limit)}, ${detail}`}
        className="h-2 overflow-hidden rounded-full bg-rule"
      >
        <div className={`h-full rounded-full ${fill}`} style={{ width: `${Math.min(100, s.percentUsed)}%` }} />
      </div>
      <span className={`num text-sm ${over ? "font-medium text-cloth" : s.state === "WARNING" ? "text-thread-ink" : "text-mute"}`}>
        {detail}
      </span>
    </li>
  );
}


import type { Transaction } from "@budgetflow/api-client";
import { ArrowRight } from "lucide-react";
import { Money } from "@/components/money";
import { StatusNote } from "@/components/status-note";
import { dayLabel } from "@/lib/month";

/** One ledger line: date, what it was (with its live state), where the money moved, and the signed amount. */
export function TransactionRow({ t, locale, accountNames, categoryNames }: {
  t: Transaction; locale: string; accountNames: Record<string, string>; categoryNames: Record<string, string>;
}) {
  const from = accountNames[t.accountId] ?? "Account";
  const account = t.kind === "TRANSFER" ? (
    <span className="inline-flex flex-wrap items-center gap-1">
      {from}
      <ArrowRight aria-hidden size={14} strokeWidth={1.75} className="text-mute" />
      <span className="sr-only"> to </span>
      {accountNames[t.counterAccountId ?? ""] ?? "Account"}
    </span>
  ) : from;
  const signed = t.kind === "EXPENSE" ? `-${t.amount}` : t.amount;
  const label = t.description ?? (t.categoryId ? categoryNames[t.categoryId] : undefined) ?? kindLabel(t.kind);
  return (
    <tr className={`border-t border-rule ${t.status === "CANCELLED" ? "text-mute" : ""}`}>
      <td className="num py-2.5 pr-3 align-top text-sm whitespace-nowrap text-mute">{dayLabel(t.occurredOn, locale)}</td>
      <td className="py-2.5 align-top">
        <span className={t.status === "CANCELLED" ? "line-through decoration-rule-strong" : ""}>{label}</span>
        <StatusNote transaction={t} />
        <span className="block text-xs text-mute sm:hidden">{account}</span>
      </td>
      <td className="hidden py-2.5 text-sm sm:table-cell">{account}</td>
      <td className="py-2.5 pl-3 text-right align-top">
        <Money amount={signed} currency={t.currency} locale={locale} sign={t.kind === "INCOME" ? "always" : "auto"}
          tone={t.status === "CANCELLED" ? "plain" : t.kind === "INCOME" ? "in" : t.kind === "EXPENSE" ? "out" : "plain"} />
      </td>
    </tr>
  );
}

function kindLabel(kind: Transaction["kind"]): string {
  return kind === "INCOME" ? "Income" : kind === "EXPENSE" ? "Expense" : "Transfer";
}

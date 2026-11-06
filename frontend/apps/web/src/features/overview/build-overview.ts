import type { Account, BudgetStatus, Category, IncomeSource, Transaction } from "@budgetflow/api-client";
import { compareAmounts, sumAmounts } from "@budgetflow/money";

export type LedgerRow = { key: string; label: string; amount: string };
export type LedgerPage = { total: string; rows: LedgerRow[] };

export type Overview = {
  currency: string;
  moneyIn: LedgerPage;
  moneyOut: LedgerPage;
  /** Money in minus money out: what the month kept (negative when overspent). */
  kept: string;
  /** Accepted transactions still being applied; not yet in any total. */
  pendingCount: number;
  otherCurrencies: { currency: string; moneyIn: string; moneyOut: string }[];
  accounts: { totals: { currency: string; total: string }[]; items: Account[] };
  budgets: BudgetStatus[];
  recent: Transaction[];
};

export type OverviewInput = {
  baseCurrency: string;
  transactions: Transaction[];
  categories: Category[];
  incomeSources: IncomeSource[];
  accounts: Account[];
  budgets: BudgetStatus[];
};

const MAX_OUT_ROWS = 6;
const RECENT_COUNT = 8;
const URGENCY: Record<BudgetStatus["state"], number> = { EXCEEDED: 0, WARNING: 1, ON_TRACK: 2 };

/** The month on one spread: income by source on the left page, spending by category on the right. */
export function buildOverview(input: OverviewInput): Overview {
  const completed = input.transactions.filter((t) => t.status === "COMPLETED" && t.kind !== "TRANSFER");
  const inBase = completed.filter((t) => t.currency === input.baseCurrency);

  const sourceNames = new Map(input.incomeSources.map((s) => [s.id, s.name]));
  const categoriesById = new Map(input.categories.map((c) => [c.id, c]));

  const incomeRows = group(inBase.filter((t) => t.kind === "INCOME"), (t) =>
    t.incomeSourceId && sourceNames.has(t.incomeSourceId)
      ? { key: t.incomeSourceId, label: sourceNames.get(t.incomeSourceId)! }
      : { key: "none", label: "No income source" });

  const spendingRows = group(inBase.filter((t) => t.kind === "EXPENSE"), (t) => {
    const category = t.categoryId ? categoriesById.get(t.categoryId) : undefined;
    const top = category?.parentId ? categoriesById.get(category.parentId) ?? category : category;
    return top ? { key: top.id, label: top.name } : { key: "none", label: "Uncategorised" };
  });

  const moneyIn = { total: sumAmounts(incomeRows.map((r) => r.amount)), rows: incomeRows };
  const moneyOut = { total: sumAmounts(spendingRows.map((r) => r.amount)), rows: foldTail(spendingRows) };

  return {
    currency: input.baseCurrency,
    moneyIn,
    moneyOut,
    kept: sumAmounts([moneyIn.total, negate(moneyOut.total)]),
    pendingCount: input.transactions.filter((t) => t.status === "PENDING").length,
    otherCurrencies: otherCurrencies(completed, input.baseCurrency),
    accounts: accountsSummary(input.accounts, input.baseCurrency),
    budgets: [...input.budgets].sort((a, b) => URGENCY[a.state] - URGENCY[b.state] || b.percentUsed - a.percentUsed),
    recent: [...input.transactions]
      .sort((a, b) => b.occurredOn.localeCompare(a.occurredOn) || b.createdAt.localeCompare(a.createdAt))
      .slice(0, RECENT_COUNT),
  };
}

function group(transactions: Transaction[], keyOf: (t: Transaction) => { key: string; label: string }): LedgerRow[] {
  const buckets = new Map<string, { label: string; amounts: string[] }>();
  for (const t of transactions) {
    const { key, label } = keyOf(t);
    const bucket = buckets.get(key) ?? { label, amounts: [] };
    bucket.amounts.push(t.amount);
    buckets.set(key, bucket);
  }
  return [...buckets.entries()]
    .map(([key, b]) => ({ key, label: b.label, amount: sumAmounts(b.amounts) }))
    .sort((a, b) => compareAmounts(b.amount, a.amount) || a.label.localeCompare(b.label));
}

function foldTail(rows: LedgerRow[]): LedgerRow[] {
  if (rows.length <= MAX_OUT_ROWS + 1) return rows;
  const rest = rows.slice(MAX_OUT_ROWS);
  return [...rows.slice(0, MAX_OUT_ROWS), { key: "rest", label: "Everything else", amount: sumAmounts(rest.map((r) => r.amount)) }];
}

function otherCurrencies(completed: Transaction[], base: string) {
  const currencies = [...new Set(completed.map((t) => t.currency).filter((c) => c !== base))].sort();
  return currencies.map((currency) => {
    const of = (kind: Transaction["kind"]) =>
      sumAmounts(completed.filter((t) => t.currency === currency && t.kind === kind).map((t) => t.amount));
    return { currency, moneyIn: of("INCOME"), moneyOut: of("EXPENSE") };
  });
}

function accountsSummary(accounts: Account[], base: string) {
  const items = accounts.filter((a) => a.status === "ACTIVE");
  const currencies = [...new Set(items.map((a) => a.currency))]
    .sort((a, b) => (a === base ? -1 : b === base ? 1 : a.localeCompare(b)));
  return {
    totals: currencies.map((currency) => ({
      currency, total: sumAmounts(items.filter((a) => a.currency === currency).map((a) => a.balance)),
    })),
    items,
  };
}

function negate(amount: string): string {
  return amount.startsWith("-") ? amount.slice(1) : `-${amount}`;
}

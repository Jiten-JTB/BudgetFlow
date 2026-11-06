import type { Account, BudgetStatus, Category, IncomeSource, Transaction } from "@budgetflow/api-client";
import { describe, expect, it } from "vitest";
import { buildOverview } from "./build-overview";

const cat = (id: string, name: string, kind: "INCOME" | "EXPENSE", parentId: string | null = null): Category =>
  ({ id, name, kind, parentId, icon: null, color: null, archived: false, createdAt: "2026-11-01T00:00:00Z" });
const src = (id: string, name: string): IncomeSource =>
  ({ id, name, type: "SALARY", defaultAccountId: null, notes: null, archived: false, createdAt: "2026-11-01T00:00:00Z" });
let seq = 0;
const txn = (p: Partial<Transaction>): Transaction => ({
  id: `t${++seq}`, kind: "EXPENSE", status: "COMPLETED", accountId: "a1", counterAccountId: null, amount: "0",
  currency: "INR", categoryId: null, incomeSourceId: null, occurredOn: "2026-11-05", description: null, payee: null,
  failureCode: null, failureReason: null, createdAt: "2026-11-05T10:00:00Z", completedAt: null, ...p,
});
const acct = (id: string, name: string, balance: string, currency = "INR", status: "ACTIVE" | "ARCHIVED" = "ACTIVE"): Account =>
  ({ id, name, type: "BANK", currency, balance, status, createdAt: "2026-11-01T00:00:00Z", updatedAt: "2026-11-01T00:00:00Z" });

const categories = [
  cat("food", "Food & Dining", "EXPENSE"), cat("dining", "Restaurants", "EXPENSE", "food"),
  cat("rent", "Housing", "EXPENSE"), cat("salary", "Salary", "INCOME"),
];
const sources = [src("acme", "Acme Corp"), src("lumen", "Lumen Studio")];

describe("buildOverview", () => {
  it("strikes the month's balance from completed income and spending in the base currency", () => {
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, accounts: [], budgets: [],
      transactions: [
        txn({ kind: "INCOME", amount: "110000.0000", incomeSourceId: "acme" }),
        txn({ kind: "INCOME", amount: "14500.0000", incomeSourceId: "lumen" }),
        txn({ kind: "EXPENSE", amount: "640.0000", categoryId: "dining" }),
        txn({ kind: "EXPENSE", amount: "2315.0000", categoryId: "food" }),
        txn({ kind: "EXPENSE", amount: "25000.0000", categoryId: "rent" }),
      ],
    });

    expect(o.moneyIn.total).toBe("124500.0000");
    expect(o.moneyIn.rows).toEqual([
      { key: "acme", label: "Acme Corp", amount: "110000.0000" },
      { key: "lumen", label: "Lumen Studio", amount: "14500.0000" },
    ]);
    expect(o.moneyOut.total).toBe("27955.0000");
    expect(o.moneyOut.rows).toEqual([
      { key: "rent", label: "Housing", amount: "25000.0000" },
      { key: "food", label: "Food & Dining", amount: "2955.0000" }, // Restaurants rolls up into its parent
    ]);
    expect(o.kept).toBe("96545.0000");
  });

  it("ignores transfers, cancelled ones, and holds pending ones apart", () => {
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, accounts: [], budgets: [],
      transactions: [
        txn({ kind: "TRANSFER", amount: "5000", counterAccountId: "a2" }),
        txn({ kind: "EXPENSE", amount: "999", status: "CANCELLED", categoryId: "food" }),
        txn({ kind: "EXPENSE", amount: "300", status: "PENDING", categoryId: "food" }),
        txn({ kind: "EXPENSE", amount: "100", categoryId: "food" }),
      ],
    });

    expect(o.moneyOut.total).toBe("100.0000");
    expect(o.moneyIn.total).toBe("0.0000");
    expect(o.pendingCount).toBe(1);
    expect(o.kept).toBe("-100.0000");
  });

  it("names unassigned income and uncategorised spending plainly", () => {
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, accounts: [], budgets: [],
      transactions: [txn({ kind: "INCOME", amount: "50" }), txn({ kind: "EXPENSE", amount: "20" })],
    });

    expect(o.moneyIn.rows).toEqual([{ key: "none", label: "No income source", amount: "50.0000" }]);
    expect(o.moneyOut.rows).toEqual([{ key: "none", label: "Uncategorised", amount: "20.0000" }]);
  });

  it("keeps the six largest categories and folds the rest into one line", () => {
    const many = Array.from({ length: 8 }, (_, i) => cat(`c${i}`, `Cat ${i}`, "EXPENSE"));
    const o = buildOverview({
      baseCurrency: "INR", categories: many, incomeSources: [], accounts: [], budgets: [],
      transactions: many.map((c, i) => txn({ kind: "EXPENSE", amount: String((i + 1) * 100), categoryId: c.id })),
    });

    expect(o.moneyOut.rows).toHaveLength(7);
    expect(o.moneyOut.rows[0]).toEqual({ key: "c7", label: "Cat 7", amount: "800.0000" });
    expect(o.moneyOut.rows[6]).toEqual({ key: "rest", label: "Everything else", amount: "300.0000" });
  });

  it("reports other currencies separately instead of mixing them in", () => {
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, accounts: [], budgets: [],
      transactions: [
        txn({ kind: "EXPENSE", amount: "100", categoryId: "food" }),
        txn({ kind: "EXPENSE", amount: "12.5", currency: "USD", categoryId: "food" }),
        txn({ kind: "INCOME", amount: "40", currency: "USD" }),
      ],
    });

    expect(o.moneyOut.total).toBe("100.0000");
    expect(o.otherCurrencies).toEqual([{ currency: "USD", moneyIn: "40.0000", moneyOut: "12.5000" }]);
  });

  it("totals active accounts per currency, base currency first", () => {
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, budgets: [], transactions: [],
      accounts: [
        acct("a1", "HDFC Savings", "518400.0000"), acct("a2", "ICICI Card", "-24385.0000"),
        acct("a3", "USD Wallet", "100.0000", "USD"), acct("a4", "Old Cash", "50.0000", "INR", "ARCHIVED"),
      ],
    });

    expect(o.accounts.totals).toEqual([{ currency: "INR", total: "494015.0000" }, { currency: "USD", total: "100.0000" }]);
    expect(o.accounts.items.map((a) => a.name)).toEqual(["HDFC Savings", "ICICI Card", "USD Wallet"]);
  });

  it("orders budgets by urgency and lists recent activity newest first with every state", () => {
    const budget = (name: string, state: BudgetStatus["state"], percentUsed: number): BudgetStatus => ({
      budget: { id: name, name, categoryId: null, period: "MONTHLY", limit: "100", currency: "INR", alertThresholdPercent: 80,
                startsOn: "2026-11-01", archived: false, createdAt: "2026-11-01T00:00:00Z" },
      periodStart: "2026-11-01", periodEnd: "2026-11-30", spent: String(percentUsed), remaining: String(100 - percentUsed),
      percentUsed, state,
    });
    const o = buildOverview({
      baseCurrency: "INR", categories, incomeSources: sources, accounts: [],
      budgets: [budget("Calm", "ON_TRACK", 20), budget("Over", "EXCEEDED", 130), budget("Close", "WARNING", 85)],
      transactions: [
        txn({ id: "old", occurredOn: "2026-11-01", createdAt: "2026-11-01T09:00:00Z", amount: "1" }),
        txn({ id: "new", occurredOn: "2026-11-06", status: "PENDING", amount: "1" }),
        txn({ id: "mid", occurredOn: "2026-11-03", status: "CANCELLED", amount: "1" }),
      ],
    });

    expect(o.budgets.map((b) => b.budget.name)).toEqual(["Over", "Close", "Calm"]);
    expect(o.recent.map((t) => t.id)).toEqual(["new", "mid", "old"]);
  });
});

// @vitest-environment jsdom
import type { Account, Transaction } from "@budgetflow/api-client";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Overview } from "./build-overview";
import { OverviewView } from "./overview-view";

const txn = (p: Partial<Transaction>): Transaction => ({
  id: "t", kind: "EXPENSE", status: "COMPLETED", accountId: "a1", counterAccountId: null, amount: "640.0000",
  currency: "INR", categoryId: null, incomeSourceId: null, occurredOn: "2026-11-06", description: "Swiggy dinner",
  payee: null, failureCode: null, failureReason: null, createdAt: "2026-11-06T10:00:00Z", completedAt: null, ...p,
});
const hdfc: Account = { id: "a1", name: "HDFC Savings", type: "BANK", currency: "INR", balance: "518400.0000",
  status: "ACTIVE", createdAt: "2026-11-01T00:00:00Z", updatedAt: "2026-11-01T00:00:00Z" };

const base: Overview = {
  currency: "INR",
  moneyIn: { total: "124500.0000", rows: [{ key: "acme", label: "Acme Corp", amount: "110000.0000" }, { key: "l", label: "Lumen Studio", amount: "14500.0000" }] },
  moneyOut: { total: "86320.0000", rows: [{ key: "rent", label: "Housing", amount: "25000.0000" }] },
  kept: "38180.0000",
  pendingCount: 0,
  otherCurrencies: [],
  accounts: { totals: [{ currency: "INR", total: "518400.0000" }], items: [hdfc] },
  budgets: [],
  recent: [],
};

function view(overview: Partial<Overview> = {}) {
  return render(<OverviewView overview={{ ...base, ...overview }} month="2026-11" monthTitle="November 2026"
    locale="en-IN" accountNames={{ a1: "HDFC Savings" }} categoryNames={{}} isCurrentMonth />);
}

describe("OverviewView", () => {
  it("opens on the facing pages: money in, money out, and the balance struck at the spine", () => {
    view();

    expect(screen.getByRole("heading", { level: 1, name: "November 2026" })).toBeInTheDocument();
    const inPage = screen.getByRole("region", { name: "Money in" });
    expect(within(inPage).getByText("₹1,24,500.00")).toBeInTheDocument();
    expect(within(inPage).getByText("Acme Corp")).toBeInTheDocument();
    const outPage = screen.getByRole("region", { name: "Money out" });
    expect(within(outPage).getByText("₹86,320.00")).toBeInTheDocument();
    expect(screen.getByText("Kept this month")).toBeInTheDocument();
    expect(screen.getByText("₹38,180.00")).toBeInTheDocument();
  });

  it("opens each line of the spread to exactly its transactions", () => {
    view({
      moneyIn: { total: "124550.0000", rows: [{ key: "acme", label: "Acme Corp", amount: "110000.0000" },
                                              { key: "none", label: "No income source", amount: "50.0000" }] },
      moneyOut: { total: "25300.0000", rows: [{ key: "rent", label: "Housing", amount: "25000.0000" },
                                              { key: "rest", label: "Everything else", amount: "300.0000" }] },
    });

    expect(screen.getByRole("link", { name: /Acme Corp/ })).toHaveAttribute("href", "/transactions?month=2026-11&incomeSourceId=acme");
    expect(screen.getByRole("link", { name: /Housing/ })).toHaveAttribute("href", "/transactions?month=2026-11&kind=EXPENSE&categoryId=rent");
    expect(screen.queryByRole("link", { name: /No income source/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Everything else/ })).not.toBeInTheDocument();
  });

  it("answers how much I have, across accounts", () => {
    view();
    expect(screen.getByText(/You have/)).toHaveTextContent("You have ₹5,18,400 across 1 account");
  });

  it("says plainly when the month overspent", () => {
    view({ kept: "-1500.0000" });
    expect(screen.getByText("Overspent this month")).toBeInTheDocument();
    expect(screen.getByText("−₹1,500.00")).toBeInTheDocument();
  });

  it("invites the first entry when a page is empty", () => {
    view({ moneyIn: { total: "0.0000", rows: [] }, moneyOut: { total: "0.0000", rows: [] } });
    expect(screen.getByRole("link", { name: "Record income" })).toHaveAttribute("href", "/transactions/new?kind=INCOME");
    expect(screen.getByRole("link", { name: "Record an expense" })).toHaveAttribute("href", "/transactions/new?kind=EXPENSE");
  });

  it("is honest about transactions still being confirmed and about cancellations", () => {
    view({
      pendingCount: 2,
      recent: [
        txn({ id: "p", status: "PENDING", description: "Swiggy dinner" }),
        txn({ id: "c", status: "CANCELLED", description: "Uber", failureCode: "ACCOUNT_ARCHIVED", failureReason: "Account is archived" }),
      ],
    });

    expect(screen.getByText("2 transactions are still being confirmed and aren’t in these totals yet.")).toBeInTheDocument();
    expect(screen.getByText("Confirming")).toBeInTheDocument();
    expect(screen.getByText("Cancelled: account is archived")).toBeInTheDocument();
  });

  it("states budget overruns in words, not only colour", () => {
    view({
      budgets: [{
        budget: { id: "b", name: "Shopping", categoryId: null, period: "MONTHLY", limit: "6000.0000", currency: "INR",
                  alertThresholdPercent: 80, startsOn: "2026-11-01", archived: false, createdAt: "2026-11-01T00:00:00Z" },
        periodStart: "2026-11-01", periodEnd: "2026-11-30", spent: "7900.0000", remaining: "-1900.0000", percentUsed: 131, state: "EXCEEDED",
      }],
    });

    expect(screen.getByText("₹1,900.00 over")).toBeInTheDocument();
    expect(screen.getByRole("progressbar", { name: "Shopping" })).toHaveAttribute("aria-valuenow", "131");
  });
});

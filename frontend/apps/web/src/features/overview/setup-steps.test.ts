import type { Account } from "@budgetflow/api-client";
import { describe, expect, it } from "vitest";
import { buildSetupSteps } from "./setup-steps";

const acct = (p: Partial<Account> = {}): Account => ({
  id: "cash", name: "Cash", type: "CASH", currency: "INR", balance: "0.0000", status: "ACTIVE",
  createdAt: "2026-11-01T00:00:00Z", updatedAt: "2026-11-01T00:00:00Z", ...p,
});
const fresh = { accounts: [acct()], incomeSourceCount: 0, budgetCount: 0, hasTransactions: false };
const done = (steps: ReturnType<typeof buildSetupSteps>["steps"]) => steps.filter((s) => s.done).map((s) => s.id);

describe("buildSetupSteps", () => {
  it("starts a new user (only the empty Cash account every user gets) with nothing done", () => {
    const setup = buildSetupSteps(fresh);
    expect(setup.steps.map((s) => s.id)).toEqual(["accounts", "income-sources", "budget", "first-transaction"]);
    expect(done(setup.steps)).toEqual([]);
    expect(setup.complete).toBe(false);
  });

  it("marks accounts done once there is a second account, or the Cash account holds money", () => {
    expect(done(buildSetupSteps({ ...fresh, accounts: [acct(), acct({ id: "hdfc", name: "HDFC", type: "BANK" })] }).steps)).toEqual(["accounts"]);
    expect(done(buildSetupSteps({ ...fresh, accounts: [acct({ balance: "1200.0000" })] }).steps)).toEqual(["accounts"]);
    expect(done(buildSetupSteps({ ...fresh, accounts: [acct({ balance: "-50.0000" })] }).steps)).toEqual(["accounts"]);
  });

  it("ignores archived accounts", () => {
    const archived = acct({ id: "old", name: "Old", type: "BANK", status: "ARCHIVED", balance: "10.0000" });
    expect(done(buildSetupSteps({ ...fresh, accounts: [acct(), archived] }).steps)).toEqual([]);
  });

  it("marks each remaining step from real data", () => {
    const setup = buildSetupSteps({ ...fresh, incomeSourceCount: 1, budgetCount: 2, hasTransactions: true });
    expect(done(setup.steps)).toEqual(["income-sources", "budget", "first-transaction"]);
  });

  it("says which steps are optional, and is complete only when all are done", () => {
    const setup = buildSetupSteps({ accounts: [acct({ balance: "1.0000" })], incomeSourceCount: 1, budgetCount: 1, hasTransactions: true });
    expect(setup.steps.filter((s) => s.optional).map((s) => s.id)).toEqual(["income-sources", "budget"]);
    expect(setup.complete).toBe(true);
  });

  it("links every step to the screen where it is done, with the action named", () => {
    expect(buildSetupSteps(fresh).steps.map((s) => s.href)).toEqual(["/accounts/new", "/income-sources", "/budgets/new", "/transactions/new"]);
    expect(buildSetupSteps(fresh).steps.map((s) => s.action)).toEqual(["Open an account", "Add an income source", "Set a budget", "Add a transaction"]);
  });
});

import { compareAmounts, sumAmounts } from "@budgetflow/money";
import { describe, expect, it } from "vitest";
import { EXAMPLE } from "./example-ledger";

const eq = (a: string, b: string) => expect(compareAmounts(a, b)).toBe(0);

describe("the landing page's example month", () => {
  it("adds up: the accounts total is the sum of the accounts", () => {
    eq(EXAMPLE.accountsTotal, sumAmounts(EXAMPLE.accounts.map((a) => a.balance)));
    eq(EXAMPLE.accountsTotal, "226560");
  });

  it("adds up: kept is money in minus money out", () => {
    eq(EXAMPLE.moneyIn.total, sumAmounts(EXAMPLE.moneyIn.rows.map((r) => r.amount)));
    eq(EXAMPLE.moneyOut.total, sumAmounts(EXAMPLE.moneyOut.rows.map((r) => r.amount)));
    eq(EXAMPLE.kept, sumAmounts([EXAMPLE.moneyIn.total, `-${EXAMPLE.moneyOut.total}`]));
  });

  it("adds up: the ledger's running balance ends at the account's balance", () => {
    const hdfc = EXAMPLE.accounts.find((a) => a.name === "HDFC Savings")!;
    let balance = EXAMPLE.ledger.opening;
    for (const e of EXAMPLE.ledger.entries) {
      balance = sumAmounts([balance, e.amount]);
      eq(e.balanceAfter, balance);
    }
    eq(balance, hdfc.balance);
  });

  it("states each budget the way the app does", () => {
    expect(EXAMPLE.budgets.map((b) => [b.name, b.state])).toEqual([
      ["Groceries", "WARNING"], ["Eating out", "ON_TRACK"], ["Shopping", "EXCEEDED"], ["Travel", "ON_TRACK"]]);
  });
});

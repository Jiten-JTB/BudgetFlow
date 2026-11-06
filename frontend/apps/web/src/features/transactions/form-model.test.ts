import { describe, expect, it } from "vitest";
import { type FormState, toRecordInput } from "./form-model";

const accounts = [
  { id: "hdfc", name: "HDFC Savings", currency: "INR" },
  { id: "cash", name: "Cash", currency: "INR" },
  { id: "usd", name: "USD Wallet", currency: "USD" },
];
const base: FormState = {
  kind: "EXPENSE", amount: "1,860", accountId: "hdfc", counterAccountId: "", categoryId: "food",
  incomeSourceId: "", occurredOn: "2026-11-06", description: "  Dinner at Toit  ",
};

describe("toRecordInput", () => {
  it("builds an expense in the account's currency with a canonical amount", () => {
    expect(toRecordInput(base, accounts)).toEqual({
      ok: true,
      input: { kind: "EXPENSE", accountId: "hdfc", amount: "1860", currency: "INR", categoryId: "food",
               occurredOn: "2026-11-06", description: "Dinner at Toit" },
    });
  });

  it("keeps the income source for income and drops fields that don't apply", () => {
    const r = toRecordInput({ ...base, kind: "INCOME", incomeSourceId: "acme", counterAccountId: "cash" }, accounts);
    expect(r).toMatchObject({ ok: true, input: { kind: "INCOME", incomeSourceId: "acme", categoryId: "food" } });
    expect(r.ok && "counterAccountId" in r.input).toBe(false);
  });

  it("builds a transfer without category or income source", () => {
    const r = toRecordInput({ ...base, kind: "TRANSFER", counterAccountId: "cash", incomeSourceId: "acme" }, accounts);
    expect(r).toEqual({ ok: true, input: { kind: "TRANSFER", accountId: "hdfc", counterAccountId: "cash", amount: "1860",
                                           currency: "INR", occurredOn: "2026-11-06", description: "Dinner at Toit" } });
  });

  it("explains every problem next to its field", () => {
    const r = toRecordInput({ ...base, amount: "12.34567", accountId: "", occurredOn: "" }, accounts);
    expect(r).toEqual({ ok: false, errors: {
      amount: "Enter an amount like 1250 or 1250.50 (up to 4 decimal places)",
      accountId: "Choose an account",
      occurredOn: "Choose a date",
    } });
    expect(toRecordInput({ ...base, amount: "0" }, accounts))
      .toEqual({ ok: false, errors: { amount: "Enter an amount greater than zero" } });
  });

  it("refuses transfers to the same account or across currencies, with the reason", () => {
    expect(toRecordInput({ ...base, kind: "TRANSFER", counterAccountId: "" }, accounts))
      .toEqual({ ok: false, errors: { counterAccountId: "Choose the account the money goes to" } });
    expect(toRecordInput({ ...base, kind: "TRANSFER", counterAccountId: "hdfc" }, accounts))
      .toEqual({ ok: false, errors: { counterAccountId: "Choose a different account" } });
    expect(toRecordInput({ ...base, kind: "TRANSFER", counterAccountId: "usd" }, accounts))
      .toEqual({ ok: false, errors: { counterAccountId: "Transfers need two accounts in the same currency (INR and USD here)" } });
  });
});

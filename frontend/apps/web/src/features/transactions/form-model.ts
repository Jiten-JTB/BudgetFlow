import type { RecordTransactionInput } from "@budgetflow/api-client";
import { compareAmounts, MoneyInputError, parseAmountInput } from "@budgetflow/money";

export type Kind = "EXPENSE" | "INCOME" | "TRANSFER";

/** Raw form values, exactly as typed; empty string means "not chosen". */
export type FormState = {
  kind: Kind;
  amount: string;
  accountId: string;
  counterAccountId: string;
  categoryId: string;
  incomeSourceId: string;
  occurredOn: string;
  description: string;
};

export type FieldErrors = Partial<Record<keyof FormState, string>>;
type AccountOption = { id: string; name: string; currency: string };

/** Validates the form and shapes the API request; every error is phrased for the field it sits under. */
export function toRecordInput(state: FormState, accounts: AccountOption[]):
    { ok: true; input: RecordTransactionInput } | { ok: false; errors: FieldErrors } {
  const errors: FieldErrors = {};

  let amount = "";
  try {
    amount = parseAmountInput(state.amount);
    if (compareAmounts(amount, "0") <= 0) errors.amount = "Enter an amount greater than zero";
  } catch (e) {
    errors.amount = e instanceof MoneyInputError ? e.message : "Enter a valid amount";
  }

  const account = accounts.find((a) => a.id === state.accountId);
  if (!account) errors.accountId = "Choose an account";

  if (state.kind === "TRANSFER") {
    const target = accounts.find((a) => a.id === state.counterAccountId);
    if (!target) errors.counterAccountId = "Choose the account the money goes to";
    else if (target.id === state.accountId) errors.counterAccountId = "Choose a different account";
    else if (account && target.currency !== account.currency) {
      errors.counterAccountId = `Transfers need two accounts in the same currency (${account.currency} and ${target.currency} here)`;
    }
  }

  if (!state.occurredOn) errors.occurredOn = "Choose a date";
  if (Object.keys(errors).length > 0 || !account) return { ok: false, errors };

  const input: RecordTransactionInput = {
    kind: state.kind, accountId: account.id, amount, currency: account.currency, occurredOn: state.occurredOn,
  };
  if (state.kind === "TRANSFER") input.counterAccountId = state.counterAccountId;
  if (state.kind !== "TRANSFER" && state.categoryId) input.categoryId = state.categoryId;
  if (state.kind === "INCOME" && state.incomeSourceId) input.incomeSourceId = state.incomeSourceId;
  const description = state.description.trim();
  if (description) input.description = description;
  return { ok: true, input };
}

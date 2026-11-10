import type { Account } from "@budgetflow/api-client";
import { compareAmounts } from "@budgetflow/money";

export type SetupStepId = "accounts" | "income-sources" | "budget" | "first-transaction";
export type SetupStep = { id: SetupStepId; title: string; detail: string; href: string; optional: boolean; done: boolean };
export type Setup = { steps: SetupStep[]; complete: boolean };

export type SetupInput = {
  accounts: Account[];
  incomeSourceCount: number;
  budgetCount: number;
  hasTransactions: boolean;
};

/**
 * First-run steps, each ticked from the user's real data (nothing to mark by hand). Every user starts with an empty
 * Cash account, so "accounts" counts as done once there is a second account or any balance is not zero.
 */
export function buildSetupSteps(input: SetupInput): Setup {
  const active = input.accounts.filter((a) => a.status === "ACTIVE");
  const accountsDone = active.length >= 2 || active.some((a) => compareAmounts(a.balance, "0") !== 0);
  const steps: SetupStep[] = [
    {
      id: "accounts", title: "Add the accounts you use", href: "/accounts/new", optional: false, done: accountsDone,
      detail: "Banks, cards and wallets, each with today’s balance. A Cash account is already here.",
    },
    {
      id: "income-sources", title: "Name where your money comes from", href: "/income-sources", optional: true,
      done: input.incomeSourceCount > 0,
      detail: "Your employer, clients or rent, so the overview shows income by source.",
    },
    {
      id: "budget", title: "Set a budget", href: "/budgets/new", optional: true, done: input.budgetCount > 0,
      detail: "A monthly limit for a category you want to watch. It warns you, never blocks.",
    },
    {
      id: "first-transaction", title: "Record your first transaction", href: "/transactions/new", optional: false,
      done: input.hasTransactions,
      detail: "An expense or income from today. After this, logging takes seconds.",
    },
  ];
  return { steps, complete: steps.every((s) => s.done) };
}

// @vitest-environment jsdom
import { ApiError, type Transaction } from "@budgetflow/api-client";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TransactionForm, type FormApi } from "./transaction-form";

const accounts = [
  { id: "hdfc", name: "HDFC Savings", currency: "INR" },
  { id: "cash", name: "Cash", currency: "INR" },
];
const categories = [
  { id: "food", name: "Food & Dining", kind: "EXPENSE" as const, parentId: null },
  { id: "rest", name: "Restaurants", kind: "EXPENSE" as const, parentId: "food" },
  { id: "salary", name: "Salary", kind: "INCOME" as const, parentId: null },
];
const incomeSources = [{ id: "acme", name: "Acme Corp", defaultAccountId: "hdfc" }];
const txn = (status: Transaction["status"], extra: Partial<Transaction> = {}) =>
  ({ id: "t1", status, kind: "EXPENSE", amount: "1860.0000", currency: "INR", failureReason: null, ...extra }) as Transaction;

function setup(api: Partial<FormApi> = {}, initialKind: "EXPENSE" | "INCOME" | "TRANSFER" = "EXPENSE",
               data: { accounts?: typeof accounts; incomeSources?: typeof incomeSources } = {}) {
  const full: FormApi = {
    record: vi.fn(async () => ({ transaction: txn("PENDING"), replayed: false })),
    get: vi.fn(async () => txn("COMPLETED")),
    ...api,
  };
  let n = 0;
  render(<TransactionForm accounts={data.accounts ?? accounts} categories={categories} incomeSources={data.incomeSources ?? incomeSources} today="2026-11-06"
    initialKind={initialKind} api={full} newKey={() => `key-${++n}-0000`} pollIntervalMs={1} />);
  return { api: full, user: userEvent.setup() };
}

describe("TransactionForm", () => {
  it("starts on the amount, dated today", () => {
    setup();
    expect(screen.getByLabelText("Amount")).toHaveFocus();
    expect(screen.getByLabelText("Date")).toHaveValue("2026-11-06");
    expect(screen.getByRole("radio", { name: "Expense" })).toBeChecked();
  });

  it("shows the fields each kind needs", async () => {
    const { user } = setup();
    expect(screen.getByRole("option", { name: "Food & Dining › Restaurants" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Income source")).not.toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Income" }));
    expect(screen.getByLabelText("Income source")).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Food & Dining" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Transfer" }));
    expect(screen.getByLabelText("To account")).toBeInTheDocument();
    expect(screen.queryByLabelText("Category")).not.toBeInTheDocument();
  });

  it("fills the account from the income source's usual account", async () => {
    const { user } = setup({}, "INCOME");
    await user.selectOptions(screen.getByLabelText("Income source"), "acme");
    expect(screen.getByLabelText("Account")).toHaveValue("hdfc");
  });

  it("explains invalid input beside the field and sends nothing", async () => {
    const { user, api } = setup();
    await user.type(screen.getByLabelText("Amount"), "12.34567");
    await user.click(screen.getByRole("button", { name: "Save expense" }));

    const amount = screen.getByLabelText("Amount");
    expect(amount).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Enter an amount like 1250 or 1250.50 (up to 4 decimal places)")).toBeInTheDocument();
    expect(screen.getByText("Choose an account")).toBeInTheDocument();
    expect(api.record).not.toHaveBeenCalled();
  });

  it("saves, follows the confirmation, then offers another entry with a fresh key", async () => {
    const { user, api } = setup();
    await user.type(screen.getByLabelText("Amount"), "1,860");
    await user.selectOptions(screen.getByLabelText("Account"), "hdfc");
    await user.selectOptions(screen.getByLabelText("Category"), "rest");
    await user.type(screen.getByLabelText("Description"), "Dinner at Toit");
    await user.click(screen.getByRole("button", { name: "Save expense" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Expense saved"));
    expect(api.record).toHaveBeenCalledWith(
      { kind: "EXPENSE", accountId: "hdfc", amount: "1860", currency: "INR", categoryId: "rest", occurredOn: "2026-11-06", description: "Dinner at Toit" },
      "key-1-0000");

    await user.click(screen.getByRole("button", { name: "Add another" }));
    expect(screen.getByLabelText("Amount")).toHaveValue("");
    expect(screen.getByLabelText("Account")).toHaveValue("hdfc"); // account kept for quick repeat entries
    await user.type(screen.getByLabelText("Amount"), "50");
    await user.click(screen.getByRole("button", { name: "Save expense" }));
    await waitFor(() => expect(api.record).toHaveBeenCalledTimes(2));
    expect(vi.mocked(api.record).mock.calls[1]?.[1]).toBe("key-2-0000");
  });

  it("retries a failed send with the same key so it can never be recorded twice", async () => {
    const record = vi.fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce({ transaction: txn("PENDING"), replayed: true });
    const { user } = setup({ record });
    await user.type(screen.getByLabelText("Amount"), "100");
    await user.selectOptions(screen.getByLabelText("Account"), "cash");
    await user.click(screen.getByRole("button", { name: "Save expense" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn’t reach Budget Flow. Nothing was saved twice; try again.");
    await user.click(screen.getByRole("button", { name: "Save expense" }));
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Expense saved"));
    expect(record.mock.calls[0]?.[1]).toBe(record.mock.calls[1]?.[1]);
  });

  it("reports a cancelled transaction with its reason", async () => {
    const { user } = setup({ get: vi.fn(async () => txn("CANCELLED", { failureReason: "Account is archived" })) });
    await user.type(screen.getByLabelText("Amount"), "99");
    await user.selectOptions(screen.getByLabelText("Account"), "cash");
    await user.click(screen.getByRole("button", { name: "Save expense" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Not saved: account is archived.");
  });

  it("shows the API's own explanation for refused requests", async () => {
    const record = vi.fn().mockRejectedValue(new ApiError(422, "CATEGORY_ARCHIVED", "Category is archived"));
    const { user } = setup({ record });
    await user.type(screen.getByLabelText("Amount"), "99");
    await user.selectOptions(screen.getByLabelText("Account"), "cash");
    await user.click(screen.getByRole("button", { name: "Save expense" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Category is archived"));
  });

  it("points to where income sources are added when there are none yet", () => {
    setup({}, "INCOME", { incomeSources: [] });
    expect(screen.getByRole("link", { name: "Add an income source" })).toHaveAttribute("href", "/income-sources");
  });

  it("does not show that hint once income sources exist", () => {
    setup({}, "INCOME");
    expect(screen.queryByRole("link", { name: "Add an income source" })).toBeNull();
  });

  it("explains that a transfer needs a second account", () => {
    setup({}, "TRANSFER", { accounts: [{ id: "cash", name: "Cash", currency: "INR" }] });
    expect(screen.getByText(/Transfers move money between two of your accounts/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open another account" })).toHaveAttribute("href", "/accounts/new");
  });
});

// @vitest-environment jsdom
import { ApiError } from "@budgetflow/api-client";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AdjustBalanceForm, ArchiveAccountButton, OpenAccountForm } from "./account-forms";

const account = { id: "a1", name: "HDFC Savings", type: "SAVINGS" as const, currency: "INR", balance: "500765.0000",
  status: "ACTIVE" as const, createdAt: "2026-11-01T00:00:00Z", updatedAt: "2026-11-01T00:00:00Z" };

describe("OpenAccountForm", () => {
  it("opens an account in the base currency by default and goes to it", async () => {
    const open = vi.fn(async () => account);
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<OpenAccountForm baseCurrency="INR" api={{ open }} navigate={navigate} />);

    expect(screen.getByLabelText("Currency")).toHaveValue("INR");
    await user.type(screen.getByLabelText("Name"), "HDFC Savings");
    await user.selectOptions(screen.getByLabelText("Type"), "SAVINGS");
    await user.type(screen.getByLabelText("Current balance"), "4,12,000");
    await user.click(screen.getByRole("button", { name: "Open account" }));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/accounts/a1"));
    expect(open).toHaveBeenCalledWith({ name: "HDFC Savings", type: "SAVINGS", currency: "INR", openingBalance: "412000" });
  });

  it("allows a negative starting balance for cards and loans, and checks the name", async () => {
    const open = vi.fn(async () => account);
    const user = userEvent.setup();
    render(<OpenAccountForm baseCurrency="INR" api={{ open }} navigate={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Open account" }));
    expect(screen.getByText("Give the account a name")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Name"), "ICICI Card");
    await user.type(screen.getByLabelText("Current balance"), "-12,289");
    await user.click(screen.getByRole("button", { name: "Open account" }));
    await waitFor(() => expect(open).toHaveBeenCalledWith(expect.objectContaining({ openingBalance: "-12289" })));
  });

  it("shows the API's reason when the name is taken", async () => {
    const open = vi.fn(async () => { throw new ApiError(409, "DUPLICATE_ACCOUNT_NAME", "An active account with this name already exists"); });
    const user = userEvent.setup();
    render(<OpenAccountForm baseCurrency="INR" api={{ open }} navigate={vi.fn()} />);
    await user.type(screen.getByLabelText("Name"), "Cash");
    await user.click(screen.getByRole("button", { name: "Open account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("An active account with this name already exists");
  });
});

describe("AdjustBalanceForm", () => {
  it("records a signed correction with one idempotency key across retries", async () => {
    const adjust = vi.fn()
      .mockRejectedValueOnce(new TypeError("Failed to fetch"))
      .mockResolvedValueOnce({ entry: {}, replayed: false });
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(<AdjustBalanceForm account={account} api={{ adjust }} onDone={onDone} newKey={() => "adj-key-0001"} />);

    await user.type(screen.getByLabelText("Change by"), "-75.25");
    await user.type(screen.getByLabelText("Note"), "Cash count");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));

    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(adjust).toHaveBeenNthCalledWith(1, "a1", { amount: "-75.25", description: "Cash count" }, "adj-key-0001");
    expect(adjust).toHaveBeenNthCalledWith(2, "a1", { amount: "-75.25", description: "Cash count" }, "adj-key-0001");
  });

  it("refuses a zero change", async () => {
    const adjust = vi.fn();
    const user = userEvent.setup();
    render(<AdjustBalanceForm account={account} api={{ adjust }} onDone={vi.fn()} />);
    await user.type(screen.getByLabelText("Change by"), "0");
    await user.click(screen.getByRole("button", { name: "Record adjustment" }));
    expect(screen.getByText("Enter how much to add (or a negative amount to remove)")).toBeInTheDocument();
    expect(adjust).not.toHaveBeenCalled();
  });
});

describe("ArchiveAccountButton", () => {
  it("asks for confirmation, explaining what archiving does, before archiving", async () => {
    const archive = vi.fn(async () => ({ ...account, status: "ARCHIVED" as const }));
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(<ArchiveAccountButton account={account} api={{ archive }} onDone={onDone} />);

    await user.click(screen.getByRole("button", { name: "Archive account" }));
    expect(screen.getByText(/stops accepting new transactions/)).toBeInTheDocument();
    expect(archive).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Archive HDFC Savings" }));
    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(archive).toHaveBeenCalledWith("a1");
  });
});

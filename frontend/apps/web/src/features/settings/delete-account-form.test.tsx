// @vitest-environment jsdom
import { ApiError } from "@budgetflow/api-client";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DeleteAccountForm } from "./delete-account-form";

function setup(remove = vi.fn(async () => undefined)) {
  const finished = vi.fn(async () => undefined);
  render(<DeleteAccountForm api={{ delete: remove }} onDeleted={finished} />);
  return { remove, finished, user: userEvent.setup() };
}

describe("DeleteAccountForm", () => {
  it("asks first, and says exactly what will be erased", async () => {
    const { user, remove } = setup();
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    expect(screen.getByText(/accounts, transactions, budgets, income sources and categories/)).toBeInTheDocument();
    expect(screen.getByText(/can’t be undone/)).toBeInTheDocument();
    expect(remove).not.toHaveBeenCalled();
  });

  it("can be called off", async () => {
    const { user } = setup();
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    await user.click(screen.getByRole("button", { name: "Keep my account" }));
    expect(screen.queryByLabelText("Your password")).toBeNull();
  });

  it("needs the password before it sends anything", async () => {
    const { user, remove } = setup();
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    await user.click(screen.getByRole("button", { name: "Delete everything permanently" }));
    expect(screen.getByText("Enter your password to confirm")).toBeInTheDocument();
    expect(remove).not.toHaveBeenCalled();
  });

  it("deletes with the password, then signs out", async () => {
    const { user, remove, finished } = setup();
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    await user.type(screen.getByLabelText("Your password"), "correct horse battery");
    await user.click(screen.getByRole("button", { name: "Delete everything permanently" }));
    await waitFor(() => expect(finished).toHaveBeenCalled());
    expect(remove).toHaveBeenCalledWith("correct horse battery");
  });

  it("says plainly when the password is wrong, and keeps the account", async () => {
    const { user, finished } = setup(vi.fn(async () => { throw new ApiError(400, "INCORRECT_PASSWORD", "x"); }));
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    await user.type(screen.getByLabelText("Your password"), "wrong one");
    await user.click(screen.getByRole("button", { name: "Delete everything permanently" }));
    expect(await screen.findByText("That password isn’t right")).toBeInTheDocument();
    expect(screen.getByLabelText("Your password")).toHaveAttribute("aria-invalid", "true");
    expect(finished).not.toHaveBeenCalled();
  });
});

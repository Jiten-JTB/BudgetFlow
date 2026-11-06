// @vitest-environment jsdom
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BudgetLimitForm, CreateBudgetForm } from "./budget-forms";

const categories = [
  { id: "food", name: "Food & Dining", kind: "EXPENSE" as const, parentId: null },
  { id: "rest", name: "Restaurants", kind: "EXPENSE" as const, parentId: "food" },
  { id: "sal", name: "Salary", kind: "INCOME" as const, parentId: null },
];

describe("CreateBudgetForm", () => {
  it("creates a monthly budget for a top-level spending category, named after it", async () => {
    const create = vi.fn(async () => ({}) as never);
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(<CreateBudgetForm baseCurrency="INR" categories={categories} api={{ create }} onDone={onDone} />);

    expect(screen.queryByRole("option", { name: "Salary" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Restaurants/ })).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Spending"), "food");
    expect(screen.getByLabelText("Name")).toHaveValue("Food & Dining");
    await user.type(screen.getByLabelText("Limit"), "10,000");
    await user.click(screen.getByRole("button", { name: "Create budget" }));

    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(create).toHaveBeenCalledWith({ name: "Food & Dining", categoryId: "food", period: "MONTHLY", limit: "10000",
                                          currency: "INR", alertThresholdPercent: 80 });
  });

  it("supports a budget for all spending and checks the limit", async () => {
    const create = vi.fn(async () => ({}) as never);
    const user = userEvent.setup();
    render(<CreateBudgetForm baseCurrency="INR" categories={categories} api={{ create }} onDone={vi.fn()} />);

    expect(screen.getByLabelText("Spending")).toHaveValue("");
    expect(screen.getByLabelText("Name")).toHaveValue("All spending");
    await user.click(screen.getByRole("button", { name: "Create budget" }));
    expect(screen.getByText("Enter the most you want to spend")).toBeInTheDocument();
    expect(create).not.toHaveBeenCalled();
  });
});

describe("BudgetLimitForm", () => {
  it("changes the limit and warning point", async () => {
    const update = vi.fn(async () => ({}) as never);
    const user = userEvent.setup();
    render(<BudgetLimitForm budget={{ id: "b1", limit: "10000.0000", alertThresholdPercent: 80, currency: "INR" }} api={{ update }} onDone={vi.fn()} />);

    const limit = screen.getByLabelText("Limit");
    expect(limit).toHaveValue("10000");
    await user.clear(limit);
    await user.type(limit, "12500");
    await user.clear(screen.getByLabelText("Warn me at"));
    await user.type(screen.getByLabelText("Warn me at"), "90");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(update).toHaveBeenCalledWith("b1", { limit: "12500", alertThresholdPercent: 90 }));
  });

  it("keeps the warning point between 1 and 100 percent", async () => {
    const update = vi.fn();
    const user = userEvent.setup();
    render(<BudgetLimitForm budget={{ id: "b1", limit: "100.0000", alertThresholdPercent: 80, currency: "INR" }} api={{ update }} onDone={vi.fn()} />);
    await user.clear(screen.getByLabelText("Warn me at"));
    await user.type(screen.getByLabelText("Warn me at"), "150");
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(screen.getByText("Choose a percentage from 1 to 100")).toBeInTheDocument();
    expect(update).not.toHaveBeenCalled();
  });
});

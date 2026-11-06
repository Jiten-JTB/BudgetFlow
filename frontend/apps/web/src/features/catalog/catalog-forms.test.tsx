// @vitest-environment jsdom
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CategoryForm, IncomeSourceForm } from "./catalog-forms";

describe("IncomeSourceForm", () => {
  it("adds a named source with its usual account", async () => {
    const create = vi.fn(async () => ({}) as never);
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(<IncomeSourceForm accounts={[{ id: "hdfc", name: "HDFC Savings" }]} api={{ create }} onDone={onDone} />);

    await user.click(screen.getByRole("button", { name: "Add income source" }));
    expect(screen.getByText("Name where the money comes from")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Name"), "Lumen Studio");
    await user.selectOptions(screen.getByLabelText("Type"), "FREELANCE");
    await user.selectOptions(screen.getByLabelText("Usually paid into"), "hdfc");
    await user.click(screen.getByRole("button", { name: "Add income source" }));

    await waitFor(() => expect(onDone).toHaveBeenCalled());
    expect(create).toHaveBeenCalledWith({ name: "Lumen Studio", type: "FREELANCE", defaultAccountId: "hdfc" });
  });
});

describe("CategoryForm", () => {
  it("adds a sub-category under a parent of the same kind", async () => {
    const create = vi.fn(async () => ({}) as never);
    const user = userEvent.setup();
    render(<CategoryForm parents={[
      { id: "food", name: "Food & Dining", kind: "EXPENSE" }, { id: "sal", name: "Salary", kind: "INCOME" },
    ]} api={{ create }} onDone={vi.fn()} />);

    await user.type(screen.getByLabelText("Name"), "Street food");
    expect(screen.queryByRole("option", { name: "Salary" })).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Inside"), "food");
    await user.click(screen.getByRole("button", { name: "Add category" }));

    await waitFor(() => expect(create).toHaveBeenCalledWith({ name: "Street food", kind: "EXPENSE", parentId: "food" }));
  });
});

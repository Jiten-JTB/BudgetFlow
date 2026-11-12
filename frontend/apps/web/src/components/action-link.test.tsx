// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ActionLink, EmptyState } from "./action-link";

describe("ActionLink", () => {
  it("is still a link (it navigates), drawn as a button", () => {
    render(<ActionLink href="/budgets/new">Set a budget</ActionLink>);
    const link = screen.getByRole("link", { name: "Set a budget" });
    expect(link).toHaveAttribute("href", "/budgets/new");
    expect(link.className).toMatch(/rounded-md/);
    expect(link.className).toMatch(/border/);
  });
});

describe("EmptyState", () => {
  it("says what is missing, then offers the action as a button", () => {
    render(<EmptyState text="No budgets yet." action="Set a budget" href="/budgets/new" />);
    expect(screen.getByText("No budgets yet.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Set a budget" })).toHaveAttribute("href", "/budgets/new");
  });
});

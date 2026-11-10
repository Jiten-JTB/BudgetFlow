// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { SetupChecklist } from "./setup-checklist";
import type { SetupStep } from "./setup-steps";

const step = (p: Partial<SetupStep>): SetupStep =>
  ({ id: "accounts", title: "Add the accounts you use", detail: "Banks and wallets.", href: "/accounts/new", optional: false, done: false, ...p });
const steps: SetupStep[] = [
  step({ done: true }),
  step({ id: "income-sources", title: "Name where your money comes from", href: "/income-sources", optional: true }),
  step({ id: "first-transaction", title: "Record your first transaction", href: "/transactions/new" }),
];

describe("SetupChecklist", () => {
  it("is a labelled section with an ordered list and progress in words", () => {
    render(<SetupChecklist steps={steps} hideAction={vi.fn()} />);
    const region = screen.getByRole("region", { name: "Set up your books" });
    expect(within(region).getAllByRole("listitem")).toHaveLength(3);
    expect(region).toHaveTextContent("1 of 3 done");
  });

  it("links the steps still to do, and says Done (not a link) for finished ones", () => {
    render(<SetupChecklist steps={steps} hideAction={vi.fn()} />);
    expect(screen.queryByRole("link", { name: /Add the accounts you use/ })).toBeNull();
    const [first] = screen.getAllByRole("listitem");
    expect(first).toHaveTextContent("Done");
    expect(screen.getByRole("link", { name: /Name where your money comes from/ })).toHaveAttribute("href", "/income-sources");
    expect(screen.getByRole("link", { name: /Record your first transaction/ })).toHaveAttribute("href", "/transactions/new");
  });

  it("marks optional steps in words", () => {
    render(<SetupChecklist steps={steps} hideAction={vi.fn()} />);
    const items = screen.getAllByRole("listitem");
    expect(items[1]).toHaveTextContent("Optional");
    expect(items[2]).not.toHaveTextContent("Optional");
  });

  it("offers a way to hide it", () => {
    render(<SetupChecklist steps={steps} hideAction={vi.fn()} />);
    expect(screen.getByRole("button", { name: "Hide these steps" })).toHaveAttribute("type", "submit");
  });
});

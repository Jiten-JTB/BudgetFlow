// @vitest-environment jsdom
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { WelcomeView } from "./welcome-view";

describe("WelcomeView", () => {
  it("opens on the two questions, answered on facing pages, with the actions at the spine", () => {
    render(<WelcomeView />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Budget Flow");
    const have = screen.getByRole("region", { name: "How much do I have?" });
    const track = screen.getByRole("region", { name: "Am I on track this month?" });
    expect(have).toHaveTextContent("₹2,26,560.00");
    expect(within(have).getAllByRole("listitem")).toHaveLength(5);
    expect(within(track).getByRole("progressbar", { name: "Shopping" })).toHaveAttribute("aria-valuetext", "₹4,250.00 of ₹4,000.00, ₹250.00 over");
    expect(track).toHaveTextContent("₹2,160.00 left");
  });

  it("offers Create your account and Sign in, and they go to the real flows", () => {
    render(<WelcomeView />);
    for (const link of screen.getAllByRole("link", { name: "Create your account" })) expect(link).toHaveAttribute("href", "/signup");
    for (const link of screen.getAllByRole("link", { name: "Sign in" })) expect(link).toHaveAttribute("href", "/api/auth/login");
  });

  it("labels every invented figure as an example", () => {
    render(<WelcomeView />);
    expect(screen.getAllByText(/Example/).length).toBeGreaterThanOrEqual(3);
  });

  it("shows where money came from and went, and the balance struck between", () => {
    render(<WelcomeView />);
    const spread = screen.getByRole("region", { name: "Where it came from, where it went" });
    expect(spread).toHaveTextContent("Acme Corp, salary");
    expect(spread).toHaveTextContent("₹75,035.00");
  });

  it("says plainly how data is handled and what is coming, without claims it can't back", () => {
    render(<WelcomeView />);
    const privacy = screen.getByRole("region", { name: "Your money, your data" });
    expect(privacy).toHaveTextContent(/never reach your browser/);
    expect(privacy).toHaveTextContent(/Delete your account/);
    expect(screen.getByRole("region", { name: "Coming next" })).toHaveTextContent(/Android and iOS/);
    expect(document.body.textContent).not.toMatch(/\bfree\b|trusted by|\busers\b|★/i);
  });
});

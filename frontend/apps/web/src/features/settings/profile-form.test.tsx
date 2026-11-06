// @vitest-environment jsdom
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProfileForm } from "./profile-form";

const me = { id: "u", email: "ananya@example.com", displayName: "Ananya Rao", baseCurrency: "INR", timezone: "Asia/Kolkata", createdAt: "2026-11-01T00:00:00Z" };

describe("ProfileForm", () => {
  it("sends only what changed and confirms the save", async () => {
    const update = vi.fn(async () => ({ ...me, displayName: "Ananya R." }));
    const user = userEvent.setup();
    render(<ProfileForm me={me} api={{ update }} />);

    expect(screen.getByLabelText("Email")).toHaveValue("ananya@example.com");
    expect(screen.getByLabelText("Email")).toHaveAttribute("readonly");
    await user.clear(screen.getByLabelText("Name"));
    await user.type(screen.getByLabelText("Name"), "Ananya R.");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(update).toHaveBeenCalledWith({ displayName: "Ananya R." }));
    expect(await screen.findByRole("status")).toHaveTextContent("Changes saved");
  });

  it("says when there is nothing to save instead of disabling the button", async () => {
    const update = vi.fn();
    const user = userEvent.setup();
    render(<ProfileForm me={me} api={{ update }} />);

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(screen.getByRole("status")).toHaveTextContent("Nothing to save");
    expect(update).not.toHaveBeenCalled();
  });
});

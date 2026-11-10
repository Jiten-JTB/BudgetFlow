// @vitest-environment jsdom
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { AmountInput } from "./amount-input";

function Harness({ locale = "en-IN", allowNegative, initial = "", onValue }: {
  locale?: string; allowNegative?: boolean; initial?: string; onValue?: (v: string) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <label htmlFor="amt">Amount</label>
      <AmountInput id="amt" locale={locale} allowNegative={allowNegative} value={value}
        onValueChange={(v) => { setValue(v); onValue?.(v); }} />
    </>
  );
}

const amount = () => screen.getByLabelText<HTMLInputElement>("Amount");

describe("AmountInput", () => {
  it("groups digits as they are typed, Indian style in en-IN", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Harness onValue={onValue} />);
    await user.type(amount(), "17500");
    expect(amount()).toHaveValue("17,500");
    await user.type(amount(), "0");
    expect(amount()).toHaveValue("1,75,000");
    expect(onValue).toHaveBeenLastCalledWith("1,75,000");
  });

  it("groups in thousands for other locales", async () => {
    const user = userEvent.setup();
    render(<Harness locale="en-US" />);
    await user.type(amount(), "175000.5");
    expect(amount()).toHaveValue("175,000.5");
  });

  it("keeps the caret where the person is typing when commas move", async () => {
    const user = userEvent.setup();
    render(<Harness initial="17,500" />);
    await user.type(amount(), "0", { initialSelectionStart: 2, initialSelectionEnd: 2 }); // "17|,500"
    expect(amount()).toHaveValue("1,70,500");
    expect(amount().selectionStart).toBe(4); // "1,70|,500"
  });

  it("Backspace on a comma deletes the digit before it", async () => {
    const user = userEvent.setup();
    render(<Harness initial="17,500" />);
    await user.type(amount(), "{Backspace}", { initialSelectionStart: 3, initialSelectionEnd: 3 }); // "17,|500"
    expect(amount()).toHaveValue("1,500");
  });

  it("accepts a minus sign only where negatives are allowed", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<Harness />);
    await user.type(amount(), "-250");
    expect(amount()).toHaveValue("250");
    unmount();
    render(<Harness allowNegative />);
    await user.type(amount(), "-2500");
    expect(amount()).toHaveValue("-2,500");
  });

  it("is a text field with a decimal keypad, or a full keyboard when a minus is needed", () => {
    const { unmount } = render(<Harness />);
    expect(amount()).toHaveAttribute("inputmode", "decimal");
    unmount();
    render(<Harness allowNegative />);
    expect(amount()).toHaveAttribute("inputmode", "text"); // phone decimal pads have no minus key
  });
});

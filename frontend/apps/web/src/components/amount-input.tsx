"use client";

import { caretAfterFormat, countSignificantBefore, formatAmountInput } from "@budgetflow/money";
import { useLayoutEffect, useRef } from "react";
import { navigatorLocale } from "@/lib/navigator-locale";

type InputProps = Omit<React.ComponentProps<"input">, "value" | "onChange" | "type" | "inputMode">;

/**
 * Amount field that groups digits as they are typed (17500 → 17,500; Indian grouping in en-IN). The value stays a
 * plain string that parseAmountInput reads exactly; the caret keeps its place when commas are inserted or removed.
 */
export function AmountInput({ value, onValueChange, allowNegative = false, locale, ref, onKeyDown, ...rest }: InputProps & {
  value: string;
  onValueChange: (value: string) => void;
  allowNegative?: boolean;
  locale?: string;
}) {
  const input = useRef<HTMLInputElement | null>(null);
  const pendingCaret = useRef<number | null>(null);

  useLayoutEffect(() => {
    const el = input.current;
    if (el && pendingCaret.current !== null && el.ownerDocument.activeElement === el) {
      el.setSelectionRange(pendingCaret.current, pendingCaret.current);
    }
    pendingCaret.current = null;
  });

  function setRef(el: HTMLInputElement | null) {
    input.current = el;
    if (typeof ref === "function") ref(el);
    else if (ref) ref.current = el;
  }

  return (
    <input
      {...rest}
      ref={setRef}
      type="text"
      // Phone decimal keypads have no minus key, so fields that take negatives get the full keyboard.
      inputMode={allowNegative ? "text" : "decimal"}
      autoComplete="off"
      value={value}
      onKeyDown={(e) => {
        onKeyDown?.(e);
        const el = e.currentTarget;
        const at = el.selectionStart;
        if (at === null || at !== el.selectionEnd) return;
        // Deleting a separator would only bring it back: step over it so the neighbouring digit goes instead.
        if (e.key === "Backspace" && el.value[at - 1] === ",") el.setSelectionRange(at - 1, at - 1);
        if (e.key === "Delete" && el.value[at] === ",") el.setSelectionRange(at + 1, at + 1);
      }}
      onChange={(e) => {
        const el = e.target;
        const before = countSignificantBefore(el.value, el.selectionStart ?? el.value.length);
        const next = formatAmountInput(el.value, { locale: locale ?? navigatorLocale(), allowNegative });
        pendingCaret.current = caretAfterFormat(next, before);
        onValueChange(next);
      }}
    />
  );
}

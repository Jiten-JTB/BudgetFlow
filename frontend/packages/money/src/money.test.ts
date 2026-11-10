import { describe, expect, it } from "vitest";
import { caretAfterFormat, compareAmounts, countSignificantBefore, formatAmountInput, formatMoney, isNegative, parseAmountInput, sumAmounts, MoneyInputError } from "./index";

describe("formatMoney", () => {
  it("uses Indian lakh/crore grouping for en-IN", () => {
    expect(formatMoney("150000.0000", "INR", { locale: "en-IN" })).toBe("₹1,50,000.00");
    expect(formatMoney("12500000", "INR", { locale: "en-IN" })).toBe("₹1,25,00,000.00");
  });

  it("follows the locale's grouping elsewhere", () => {
    expect(formatMoney("1234.5", "USD", { locale: "en-US" })).toBe("$1,234.50");
    // Symbol and amount are joined by a non-breaking space so they never wrap apart.
    expect(formatMoney("1234.5", "EUR", { locale: "de-DE" })).toBe("1.234,50\u00a0€");
  });

  it("respects each currency's minor units", () => {
    expect(formatMoney("1234.0000", "JPY", { locale: "en-US" })).toBe("¥1,234");
    expect(formatMoney("1.2345", "KWD", { locale: "en-US" })).toBe("KWD\u00a01.235");
  });

  it("uses a true minus sign and optional explicit plus", () => {
    expect(formatMoney("-640.0000", "INR", { locale: "en-IN" })).toBe("−₹640.00");
    expect(formatMoney("110000", "INR", { locale: "en-IN", sign: "always" })).toBe("+₹1,10,000.00");
    expect(formatMoney("0", "INR", { locale: "en-IN", sign: "always" })).toBe("₹0.00");
  });

  it("never loses precision on large values (no floating point)", () => {
    expect(formatMoney("123456789012345.67", "USD", { locale: "en-US" })).toBe("$123,456,789,012,345.67");
  });

  it("can drop the minor units for compact headline figures", () => {
    expect(formatMoney("150000.4999", "INR", { locale: "en-IN", wholeUnits: true })).toBe("₹1,50,000");
  });
});

describe("parseAmountInput", () => {
  it("accepts what people type and returns a canonical decimal string", () => {
    expect(parseAmountInput("1,50,000.5")).toBe("150000.5");
    expect(parseAmountInput(" 2315 ")).toBe("2315");
    expect(parseAmountInput("₹ 640.00")).toBe("640.00");
    expect(parseAmountInput("0.0001")).toBe("0.0001");
  });

  it("rejects malformed, negative unless allowed, and over-precise amounts", () => {
    for (const bad of ["", "abc", "1.2.3", "12,34.5x", ".", "1.23456"]) {
      expect(() => parseAmountInput(bad)).toThrow(MoneyInputError);
    }
    expect(() => parseAmountInput("-5")).toThrow(MoneyInputError);
    expect(parseAmountInput("-5", { allowNegative: true })).toBe("-5");
  });

  it("rejects values beyond NUMERIC(19,4)", () => {
    expect(() => parseAmountInput("1000000000000000")).toThrow(MoneyInputError);
    expect(parseAmountInput("999999999999999.9999")).toBe("999999999999999.9999");
  });
});

describe("arithmetic on decimal strings", () => {
  it("sums exactly", () => {
    expect(sumAmounts(["0.1", "0.2"])).toBe("0.3000");
    expect(sumAmounts(["518400.0000", "-24385.0000", "8900.0000", "140000.0000"])).toBe("642915.0000");
    expect(sumAmounts([])).toBe("0.0000");
  });

  it("compares and reports sign", () => {
    expect(compareAmounts("10.0000", "9.9999")).toBe(1);
    expect(compareAmounts("-1", "1")).toBe(-1);
    expect(compareAmounts("5", "5.0000")).toBe(0);
    expect(isNegative("-0.0001")).toBe(true);
    expect(isNegative("0.0000")).toBe(false);
  });
});

describe("formatAmountInput", () => {
  it.each([
    ["17500", "en-IN", "17,500"],
    ["175000", "en-IN", "1,75,000"],
    ["10000000", "en-IN", "1,00,00,000"],
    ["175000", "en-US", "175,000"],
    ["1234567", "de-DE", "1,234,567"],          // separator is always "," so parseAmountInput reads it back
    ["1234.5", "en-IN", "1,234.5"],
    ["1234.", "en-US", "1,234."],               // keep the point while the person is still typing
    ["0.75", "en-IN", "0.75"],
    ["007", "en-IN", "7"],
    ["0", "en-IN", "0"],
    ["₹ 1,50,000", "en-IN", "1,50,000"],          // pasted with symbol and old grouping
    ["1.2.3", "en-IN", "1.23"],                  // only the first point counts
    ["12a3", "en-IN", "123"],
    ["abc", "en-IN", ""],
    ["", "en-IN", ""],
    ["123456789012345", "en-US", "123,456,789,012,345"], // exact past float precision
  ])("%s in %s → %s", (raw, locale, expected) => {
    expect(formatAmountInput(raw, { locale })).toBe(expected);
  });

  it("keeps a leading minus only when negatives are allowed", () => {
    expect(formatAmountInput("-2500", { locale: "en-IN", allowNegative: true })).toBe("-2,500");
    expect(formatAmountInput("-", { locale: "en-IN", allowNegative: true })).toBe("-");
    expect(formatAmountInput("-2500", { locale: "en-IN" })).toBe("2,500");
    expect(formatAmountInput("25-00", { locale: "en-IN", allowNegative: true })).toBe("2,500");
  });

  it("round-trips through parseAmountInput", () => {
    for (const raw of ["1250", "1250.50", "175000", "0.0001", "999999999999999.9999"]) {
      expect(parseAmountInput(formatAmountInput(raw, { locale: "en-IN" }))).toBe(raw);
    }
  });
});

describe("caretAfterFormat", () => {
  it("puts the caret after the same number of typed characters, skipping inserted commas", () => {
    // typed "1750|0" → "17,50|0": 4 characters were before the caret
    expect(caretAfterFormat("17,500", 4)).toBe(5);
    expect(caretAfterFormat("1,75,000", 6)).toBe(8);
    expect(caretAfterFormat("1,75,000", 0)).toBe(0);
    expect(caretAfterFormat("1,75,000", 1)).toBe(1);
    expect(caretAfterFormat("-2,500", 2)).toBe(2);
  });
});

describe("countSignificantBefore", () => {
  it("counts digits, point and minus before the caret, ignoring separators and junk", () => {
    expect(countSignificantBefore("17,5000", 5)).toBe(4);
    expect(countSignificantBefore("₹ 1,2", 5)).toBe(2);
    expect(countSignificantBefore("-1,234.5", 8)).toBe(7);
  });
});

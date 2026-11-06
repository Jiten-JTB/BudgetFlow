import { describe, expect, it } from "vitest";
import { compareAmounts, formatMoney, isNegative, parseAmountInput, sumAmounts, MoneyInputError } from "./index";

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

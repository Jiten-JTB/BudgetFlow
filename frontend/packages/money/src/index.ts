/**
 * Money on the client is always an exact decimal string (the API's NUMERIC(19,4)), never a float.
 * Formatting goes through Intl with the string itself, so no digit is ever lost to binary floating point.
 */

const SCALE = 4;
const SCALE_FACTOR = 10n ** BigInt(SCALE);
const MAX_INTEGER_DIGITS = 15; // NUMERIC(19,4)
const DECIMAL = /^-?\d+(\.\d+)?$/;
const INPUT = /^-?\d+(\.\d{1,4})?$/;

export type FormatOptions = {
  locale?: string;
  /** "always" prefixes + on positive amounts (zero stays unsigned). */
  sign?: "auto" | "always";
  /** Drop minor units, e.g. for headline figures. */
  wholeUnits?: boolean;
};

export class MoneyInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "MoneyInputError";
  }
}

const formatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(amount: string, currency: string, options: FormatOptions = {}): string {
  if (!DECIMAL.test(amount)) {
    throw new MoneyInputError(`Not a decimal amount: ${amount}`);
  }
  const key = `${options.locale ?? ""}|${currency}|${options.sign ?? "auto"}|${options.wholeUnits ?? false}`;
  let formatter = formatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(options.locale, {
      style: "currency",
      currency,
      signDisplay: options.sign === "always" ? "exceptZero" : "auto",
      ...(options.wholeUnits ? { minimumFractionDigits: 0, maximumFractionDigits: 0 } : {}),
    });
    formatters.set(key, formatter);
  }
  // Intl formats decimal strings exactly; typography wants a true minus sign (read as "minus" by screen readers).
  return formatter.format(amount as Intl.StringNumericLiteral).replace("-", "−");
}

/**
 * Parses what a person typed ("1,50,000.5", "₹ 640.00") into a canonical decimal string.
 * Grouping separators and currency symbols are ignored; anything else invalid is rejected.
 */
export function parseAmountInput(raw: string, options: { allowNegative?: boolean } = {}): string {
  const cleaned = raw.replace(/[\s\p{Sc}]/gu, "").replace(/,/g, "");
  if (!INPUT.test(cleaned)) {
    throw new MoneyInputError("Enter an amount like 1250 or 1250.50 (up to 4 decimal places)");
  }
  if (cleaned.startsWith("-") && !options.allowNegative) {
    throw new MoneyInputError("Amount must be positive");
  }
  const integerDigits = cleaned.replace("-", "").split(".")[0]!.replace(/^0+(?=\d)/, "");
  if (integerDigits.length > MAX_INTEGER_DIGITS) {
    throw new MoneyInputError("Amount is too large");
  }
  return cleaned;
}

/** Exact sum, returned at the API's scale of 4. */
export function sumAmounts(amounts: readonly string[]): string {
  return fromUnits(amounts.reduce((total, amount) => total + toUnits(amount), 0n));
}

export function compareAmounts(a: string, b: string): -1 | 0 | 1 {
  const diff = toUnits(a) - toUnits(b);
  return diff === 0n ? 0 : diff > 0n ? 1 : -1;
}

export function isNegative(amount: string): boolean {
  return toUnits(amount) < 0n;
}

function toUnits(amount: string): bigint {
  if (!DECIMAL.test(amount)) {
    throw new MoneyInputError(`Not a decimal amount: ${amount}`);
  }
  const negative = amount.startsWith("-");
  const [whole, fraction = ""] = amount.replace("-", "").split(".") as [string, string?];
  if (fraction.length > SCALE) {
    throw new MoneyInputError(`More than ${SCALE} decimal places: ${amount}`);
  }
  const units = BigInt(whole) * SCALE_FACTOR + BigInt(fraction.padEnd(SCALE, "0"));
  return negative ? -units : units;
}

function fromUnits(units: bigint): string {
  const negative = units < 0n;
  const abs = negative ? -units : units;
  const whole = abs / SCALE_FACTOR;
  const fraction = (abs % SCALE_FACTOR).toString().padStart(SCALE, "0");
  return `${negative ? "-" : ""}${whole}.${fraction}`;
}

const groupFormatters = new Map<string, Intl.NumberFormat>();
const SIGNIFICANT = /[\d.-]/;

/**
 * Groups an amount while it is being typed ("175000" → "1,75,000" in en-IN, "175,000" in en-US). Grouping follows the
 * locale, but the separator is always "," and the point always ".", so parseAmountInput reads the result back exactly.
 * Digits are never added, dropped or rounded; anything that is not a digit, the first point or a leading minus is removed.
 */
export function formatAmountInput(raw: string, options: { locale?: string; allowNegative?: boolean } = {}): string {
  const firstSignificant = raw.match(SIGNIFICANT)?.[0];
  const negative = options.allowNegative === true && firstSignificant === "-";
  const kept = raw.replace(/[^\d.]/g, "");
  const point = kept.indexOf(".");
  const hasPoint = point >= 0;
  const integer = (hasPoint ? kept.slice(0, point) : kept).replace(/^0+(?=\d)/, "");
  const fraction = hasPoint ? kept.slice(point + 1).replace(/\./g, "") : "";
  const sign = negative ? "-" : "";
  if (!integer && !hasPoint) return sign;
  return `${sign}${integer ? groupDigits(integer, options.locale) : "0"}${hasPoint ? `.${fraction}` : ""}`;
}

/** How many characters that survive formatting (digits, point, minus) sit before the caret. */
export function countSignificantBefore(value: string, caret: number): number {
  let count = 0;
  for (const ch of value.slice(0, caret)) if (SIGNIFICANT.test(ch)) count++;
  return count;
}

/** Caret index in the formatted value that follows the same number of significant characters. */
export function caretAfterFormat(formatted: string, significantBefore: number): number {
  if (significantBefore <= 0) return 0;
  let seen = 0;
  for (let i = 0; i < formatted.length; i++) {
    if (SIGNIFICANT.test(formatted[i]!) && ++seen === significantBefore) return i + 1;
  }
  return formatted.length;
}

function groupDigits(digits: string, locale: string | undefined): string {
  const key = locale ?? "";
  let formatter = groupFormatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { useGrouping: true, maximumFractionDigits: 0, numberingSystem: "latn" });
    groupFormatters.set(key, formatter);
  }
  // BigInt keeps every digit exact; only the locale's grouping pattern is used, with "," as the separator.
  return formatter.formatToParts(BigInt(digits))
    .map((part) => (part.type === "integer" ? part.value : part.type === "group" ? "," : ""))
    .join("");
}

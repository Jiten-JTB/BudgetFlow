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

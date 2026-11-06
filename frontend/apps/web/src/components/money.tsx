import { formatMoney } from "@budgetflow/money";

type Size = "xl" | "lg" | "md" | "sm" | "xs";
const SIZE: Record<Size, string> = {
  xl: "text-money-xl font-semibold tracking-tight",
  lg: "text-money-lg font-semibold tracking-tight",
  md: "text-money-md font-semibold",
  sm: "text-money-sm font-medium",
  xs: "text-money-xs",
};
const TONE = { in: "text-indigo", out: "text-cloth", plain: "text-ink", auto: "" } as const;

/** Every amount in the app goes through here: exact, locale-formatted, tabular. */
export function Money({ amount, currency, locale, size = "sm", tone = "plain", sign = "auto", whole = false, className = "" }: {
  amount: string; currency: string; locale: string; size?: Size; tone?: keyof typeof TONE;
  sign?: "auto" | "always"; whole?: boolean; className?: string;
}) {
  const toneClass = tone === "auto" ? (amount.startsWith("-") ? TONE.out : TONE.plain) : TONE[tone];
  return (
    <span className={`num whitespace-nowrap ${SIZE[size]} ${toneClass} ${className}`}>
      {formatMoney(amount, currency, { locale, sign, wholeUnits: whole })}
    </span>
  );
}

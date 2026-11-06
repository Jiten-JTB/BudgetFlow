/** Account types as the API names them, with their labels. Plain data: importable from Server and Client Components alike. */
export const ACCOUNT_TYPES = [
  ["BANK", "Bank account"], ["SAVINGS", "Savings"], ["CASH", "Cash"], ["CREDIT_CARD", "Credit card"],
  ["WALLET", "Wallet"], ["INVESTMENT", "Investment"], ["LOAN", "Loan"],
] as const;

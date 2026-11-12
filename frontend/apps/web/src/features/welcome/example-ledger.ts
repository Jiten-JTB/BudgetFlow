import { compareAmounts, sumAmounts } from "@budgetflow/money";

/**
 * One invented month for the landing page, labelled "Example" wherever it shows. Every total is computed from its
 * lines, so the page's figures reconcile exactly like the product's (example-ledger.test.ts proves it).
 */
const accounts = [
  { name: "HDFC Savings", balance: "152685.00" },
  { name: "SBI Salary", balance: "74000.00" },
  { name: "Cash", balance: "5000.00" },
  { name: "Paytm wallet", balance: "2375.00" },
  { name: "Credit card", balance: "-7500.00" },
];

const moneyIn = [
  { label: "Acme Corp, salary", amount: "110000.00" },
  { label: "Studio Kalam, freelance", amount: "18500.00" },
];
const moneyOut = [
  { label: "Housing", amount: "32000.00" },
  { label: "Food & Dining", amount: "12955.00" },
  { label: "Travel", amount: "4260.00" },
  { label: "Shopping", amount: "4250.00" },
];

type State = "ON_TRACK" | "WARNING" | "EXCEEDED";
const budget = (name: string, spent: string, limit: string, warnAt = 80) => {
  const percent = Math.round((Number(spent) / Number(limit)) * 100); // display only; amounts stay strings
  const state: State = compareAmounts(spent, limit) > 0 ? "EXCEEDED" : percent >= warnAt ? "WARNING" : "ON_TRACK";
  return { name, spent, limit, percent, state, remaining: sumAmounts([limit, `-${spent}`]) };
};

const opening = "63300.00";
const ledgerLines = [
  { date: "1 Nov", description: "Salary, Acme Corp", amount: "110000.00" },
  { date: "2 Nov", description: "Rent", amount: "-32000.00" },
  { date: "3 Nov", description: "Groceries, BigBasket", amount: "-2315.00" },
  { date: "14 Nov", description: "Logo project, Studio Kalam", amount: "18500.00" },
  { date: "20 Nov", description: "Adjustment: matched bank statement", amount: "200.00" },
  { date: "21 Nov", description: "Moved to Cash", amount: "-5000.00" },
];
let running = opening;
const entries = ledgerLines.map((l) => {
  running = sumAmounts([running, l.amount]);
  return { ...l, balanceAfter: running };
});

const inTotal = sumAmounts(moneyIn.map((r) => r.amount));
const outTotal = sumAmounts(moneyOut.map((r) => r.amount));

export const EXAMPLE = {
  currency: "INR",
  locale: "en-IN",
  accounts,
  accountsTotal: sumAmounts(accounts.map((a) => a.balance)),
  moneyIn: { rows: moneyIn, total: inTotal },
  moneyOut: { rows: moneyOut, total: outTotal },
  kept: sumAmounts([inTotal, `-${outTotal}`]),
  budgets: [
    budget("Groceries", "9840.00", "12000.00"),
    budget("Eating out", "3115.00", "6000.00"),
    budget("Shopping", "4250.00", "4000.00"),
    budget("Travel", "4260.00", "8000.00"),
  ],
  ledger: { account: "HDFC Savings", opening, entries },
};

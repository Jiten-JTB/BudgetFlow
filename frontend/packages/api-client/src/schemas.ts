import { z } from "zod";

/** Mirrors of the backend's JSON contracts. Amounts are exact decimal strings, dates ISO strings. */

const amount = z.string().regex(/^-?\d+(\.\d+)?$/, "decimal string");
const uuid = z.string().min(1);
const instant = z.string();
const day = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const AccountType = z.enum(["BANK", "CASH", "CREDIT_CARD", "WALLET", "SAVINGS", "INVESTMENT", "LOAN"]);
export const TransactionKind = z.enum(["INCOME", "EXPENSE", "TRANSFER"]);
export const TransactionStatus = z.enum(["PENDING", "COMPLETED", "CANCELLED"]);
export const CategoryKind = z.enum(["INCOME", "EXPENSE"]);
export const IncomeSourceType = z.enum(["SALARY", "BUSINESS", "FREELANCE", "INVESTMENT", "RENTAL", "PENSION", "GIFT", "POCKET_MONEY", "OTHER"]);
export const BudgetPeriod = z.enum(["WEEKLY", "MONTHLY", "YEARLY"]);
export const BudgetState = z.enum(["ON_TRACK", "WARNING", "EXCEEDED"]);
export const EntryKind = z.enum(["OPENING", "TRANSACTION", "TRANSFER", "ADJUSTMENT", "REVERSAL"]);

export const User = z.object({
  id: uuid, email: z.string(), displayName: z.string(), baseCurrency: z.string(), timezone: z.string(), createdAt: instant,
});

export const Account = z.object({
  id: uuid, name: z.string(), type: AccountType, currency: z.string(), balance: amount,
  status: z.enum(["ACTIVE", "ARCHIVED"]), createdAt: instant, updatedAt: instant,
});

export const BalanceEntry = z.object({
  id: uuid, kind: EntryKind, amount, balanceAfter: amount, referenceId: uuid.nullable(),
  reversesEntryId: uuid.nullable(), description: z.string().nullable(), createdAt: instant,
});

export const Transaction = z.object({
  id: uuid, kind: TransactionKind, status: TransactionStatus, accountId: uuid, counterAccountId: uuid.nullable(),
  amount, currency: z.string(), categoryId: uuid.nullable(), incomeSourceId: uuid.nullable(), occurredOn: day,
  description: z.string().nullable(), payee: z.string().nullable(), failureCode: z.string().nullable(),
  failureReason: z.string().nullable(), createdAt: instant, completedAt: instant.nullable(),
});

export const Category = z.object({
  id: uuid, name: z.string(), kind: CategoryKind, parentId: uuid.nullable(), icon: z.string().nullable(),
  color: z.string().nullable(), archived: z.boolean(), createdAt: instant,
});

export const IncomeSource = z.object({
  id: uuid, name: z.string(), type: IncomeSourceType, defaultAccountId: uuid.nullable(), notes: z.string().nullable(),
  archived: z.boolean(), createdAt: instant,
});

export const Budget = z.object({
  id: uuid, name: z.string(), categoryId: uuid.nullable(), period: BudgetPeriod, limit: amount, currency: z.string(),
  alertThresholdPercent: z.number().int(), startsOn: day, archived: z.boolean(), createdAt: instant,
});

export const BudgetStatus = z.object({
  budget: Budget, periodStart: day, periodEnd: day, spent: amount, remaining: amount,
  percentUsed: z.number().int(), state: BudgetState,
});

export const page = <T extends z.ZodType>(item: T) => z.object({ items: z.array(item), nextCursor: z.string().nullable() });

export const Problem = z.object({
  status: z.number().optional(),
  detail: z.string().optional(),
  code: z.string().optional(),
  errors: z.array(z.object({ field: z.string(), message: z.string() })).optional(),
}).loose();

export type User = z.infer<typeof User>;
export type Account = z.infer<typeof Account>;
export type BalanceEntry = z.infer<typeof BalanceEntry>;
export type Transaction = z.infer<typeof Transaction>;
export type Category = z.infer<typeof Category>;
export type IncomeSource = z.infer<typeof IncomeSource>;
export type Budget = z.infer<typeof Budget>;
export type BudgetStatus = z.infer<typeof BudgetStatus>;
export type Page<T> = { items: T[]; nextCursor: string | null };

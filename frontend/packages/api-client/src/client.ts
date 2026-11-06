import type { z } from "zod";
import * as S from "./schemas";

export type ApiClientOptions = {
  /** Gateway origin on the server (https://budgetflow-api...), or "/api/bff" in the browser. */
  baseUrl: string;
  fetch?: typeof fetch;
  /** Server-side only. Browser calls go through the BFF, which attaches the token itself. */
  getAccessToken?: () => Promise<string | null>;
  /** Extra headers for every request (server-side only), e.g. forwarding the visitor's IP. */
  headers?: () => Record<string, string>;
};

/** A request the API refused; {@link code} is the backend's stable error code. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** The API answered with a shape this client does not understand: a deploy mismatch, never user error. */
export class ApiContractError extends Error {
  constructor(readonly path: string, readonly issues: string) {
    super(`Unexpected response from ${path}: ${issues}`);
    this.name = "ApiContractError";
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;
type RequestOptions = { body?: unknown; query?: Query; idempotencyKey?: string };

export type RecordTransactionInput = {
  kind: z.infer<typeof S.TransactionKind>;
  accountId: string;
  counterAccountId?: string | null;
  amount: string;
  currency: string;
  categoryId?: string | null;
  incomeSourceId?: string | null;
  occurredOn: string;
  description?: string | null;
  payee?: string | null;
};

export type TransactionFilter = {
  from?: string; to?: string; kind?: string; status?: string; accountId?: string; categoryId?: string; incomeSourceId?: string;
  cursor?: string; limit?: number;
};

export function createApiClient(options: ApiClientOptions) {
  const doFetch = options.fetch ?? fetch;

  async function call<T extends z.ZodType>(method: string, path: string, schema: T, opts: RequestOptions = {}) {
    const { response, body } = await send(method, path, opts);
    const parsed = schema.safeParse(body);
    if (!parsed.success) {
      throw new ApiContractError(path, parsed.error.message);
    }
    return { status: response.status, data: parsed.data as z.infer<T> };
  }

  async function send(method: string, path: string, opts: RequestOptions) {
    const headers = new Headers({ Accept: "application/json" });
    if (opts.body !== undefined) headers.set("Content-Type", "application/json");
    if (opts.idempotencyKey) headers.set("Idempotency-Key", opts.idempotencyKey);
    for (const [name, value] of Object.entries(options.headers?.() ?? {})) headers.set(name, value);
    const token = await options.getAccessToken?.();
    if (token) headers.set("Authorization", `Bearer ${token}`);

    const response = await doFetch(options.baseUrl + path + queryString(opts.query), {
      method,
      headers,
      body: opts.body === undefined ? null : JSON.stringify(opts.body),
      cache: "no-store",
    });
    const text = await response.text();
    const body = parseJson(text);
    if (!response.ok) {
      throw toApiError(response.status, body);
    }
    return { response, body };
  }

  const id = encodeURIComponent;

  return {
    users: {
      register: (input: { email: string; password: string; displayName: string; baseCurrency: string; timezone: string }) =>
        call("POST", "/api/v1/users", S.User, { body: input }).then((r) => r.data),
    },
    me: {
      get: () => call("GET", "/api/v1/users/me", S.User).then((r) => r.data),
      update: (input: { displayName?: string; baseCurrency?: string; timezone?: string }) =>
        call("PATCH", "/api/v1/users/me", S.User, { body: input }).then((r) => r.data),
    },
    accounts: {
      list: (q: { includeArchived?: boolean } = {}) =>
        call("GET", "/api/v1/accounts", S.Account.array(), { query: q }).then((r) => r.data),
      get: (accountId: string) => call("GET", `/api/v1/accounts/${id(accountId)}`, S.Account).then((r) => r.data),
      open: (input: { name: string; type: string; currency: string; openingBalance?: string }) =>
        call("POST", "/api/v1/accounts", S.Account, { body: input }).then((r) => r.data),
      rename: (accountId: string, name: string) =>
        call("PATCH", `/api/v1/accounts/${id(accountId)}`, S.Account, { body: { name } }).then((r) => r.data),
      archive: (accountId: string) =>
        call("POST", `/api/v1/accounts/${id(accountId)}/archive`, S.Account).then((r) => r.data),
      entries: (accountId: string, q: { before?: string; limit?: number } = {}) =>
        call("GET", `/api/v1/accounts/${id(accountId)}/entries`, S.page(S.BalanceEntry), { query: q }).then((r) => r.data),
      adjust: (accountId: string, input: { amount: string; description?: string }, idempotencyKey: string) =>
        call("POST", `/api/v1/accounts/${id(accountId)}/adjustments`, S.BalanceEntry, { body: input, idempotencyKey })
          .then((r) => ({ entry: r.data, replayed: r.status === 200 })),
    },
    transactions: {
      list: (q: TransactionFilter = {}) =>
        call("GET", "/api/v1/transactions", S.page(S.Transaction), { query: q }).then((r) => r.data),
      get: (transactionId: string) =>
        call("GET", `/api/v1/transactions/${id(transactionId)}`, S.Transaction).then((r) => r.data),
      /** 202 = accepted (pending, saga running), 200 = replay of the same Idempotency-Key. */
      record: (input: RecordTransactionInput, idempotencyKey: string) =>
        call("POST", "/api/v1/transactions", S.Transaction, { body: input, idempotencyKey })
          .then((r) => ({ transaction: r.data, replayed: r.status === 200 })),
    },
    categories: {
      list: (q: { kind?: string; includeArchived?: boolean } = {}) =>
        call("GET", "/api/v1/categories", S.Category.array(), { query: q }).then((r) => r.data),
      create: (input: { name: string; kind: string; parentId?: string | null; icon?: string; color?: string }) =>
        call("POST", "/api/v1/categories", S.Category, { body: input }).then((r) => r.data),
      update: (categoryId: string, input: { name?: string; icon?: string; color?: string }) =>
        call("PATCH", `/api/v1/categories/${id(categoryId)}`, S.Category, { body: input }).then((r) => r.data),
      archive: (categoryId: string) =>
        call("POST", `/api/v1/categories/${id(categoryId)}/archive`, S.Category).then((r) => r.data),
    },
    incomeSources: {
      list: (q: { includeArchived?: boolean } = {}) =>
        call("GET", "/api/v1/income-sources", S.IncomeSource.array(), { query: q }).then((r) => r.data),
      create: (input: { name: string; type: string; defaultAccountId?: string | null; notes?: string }) =>
        call("POST", "/api/v1/income-sources", S.IncomeSource, { body: input }).then((r) => r.data),
      update: (sourceId: string, input: { name?: string; type?: string; defaultAccountId?: string; notes?: string }) =>
        call("PATCH", `/api/v1/income-sources/${id(sourceId)}`, S.IncomeSource, { body: input }).then((r) => r.data),
      archive: (sourceId: string) =>
        call("POST", `/api/v1/income-sources/${id(sourceId)}/archive`, S.IncomeSource).then((r) => r.data),
    },
    budgets: {
      list: (q: { includeArchived?: boolean } = {}) =>
        call("GET", "/api/v1/budgets", S.Budget.array(), { query: q }).then((r) => r.data),
      statuses: (q: { date?: string } = {}) =>
        call("GET", "/api/v1/budgets/status", S.BudgetStatus.array(), { query: q }).then((r) => r.data),
      create: (input: { name: string; categoryId?: string | null; period: string; limit: string; currency: string;
                        alertThresholdPercent?: number; startsOn?: string }) =>
        call("POST", "/api/v1/budgets", S.Budget, { body: input }).then((r) => r.data),
      update: (budgetId: string, input: { name?: string; limit?: string; alertThresholdPercent?: number }) =>
        call("PATCH", `/api/v1/budgets/${id(budgetId)}`, S.Budget, { body: input }).then((r) => r.data),
      archive: (budgetId: string) =>
        call("POST", `/api/v1/budgets/${id(budgetId)}/archive`, S.Budget).then((r) => r.data),
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;

function queryString(query: Query | undefined): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  }
  const s = params.toString();
  return s ? `?${s}` : "";
}

function parseJson(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function toApiError(status: number, body: unknown): ApiError {
  const problem = S.Problem.safeParse(body);
  if (problem.success && problem.data.code) {
    const fields = Object.fromEntries((problem.data.errors ?? []).map((e) => [e.field, e.message]));
    return new ApiError(status, problem.data.code, problem.data.detail ?? "Request failed", fields);
  }
  return new ApiError(status, `HTTP_${status}`, `Request failed with status ${status}`);
}

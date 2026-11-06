import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, createApiClient } from "./index";

type Call = { url: string; method: string; headers: Headers; body: string | null };

function fakeFetch(status: number, body: unknown) {
  const calls: Call[] = [];
  const fn = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({
      url: String(url),
      method: init?.method ?? "GET",
      headers: new Headers(init?.headers),
      body: (init?.body as string | undefined) ?? null,
    });
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
    });
  });
  return { fn: fn as unknown as typeof fetch, calls };
}

const account = {
  id: "01a1a4e0-9cc6-731d-b650-f818567326ff", name: "HDFC Savings", type: "SAVINGS", currency: "INR",
  balance: "50000.0000", status: "ACTIVE", createdAt: "2026-11-06T10:00:00Z", updatedAt: "2026-11-06T10:00:00Z",
};
const transaction = {
  id: "01a1a50b-0000-7000-8000-000000000001", kind: "EXPENSE", status: "PENDING",
  accountId: account.id, counterAccountId: null, amount: "850.0000", currency: "INR",
  categoryId: "01a1a50b-0000-7000-8000-000000000002", incomeSourceId: null, occurredOn: "2026-11-06",
  description: "Dinner", payee: null, failureCode: null, failureReason: null,
  createdAt: "2026-11-06T10:00:00Z", completedAt: null,
};

describe("api client", () => {
  it("sends the bearer token and parses typed responses", async () => {
    const { fn, calls } = fakeFetch(200, [account]);
    const api = createApiClient({ baseUrl: "https://api.test", fetch: fn, getAccessToken: async () => "tok-1" });

    const accounts = await api.accounts.list({ includeArchived: true });

    expect(accounts[0]?.balance).toBe("50000.0000");
    expect(calls[0]?.url).toBe("https://api.test/api/v1/accounts?includeArchived=true");
    expect(calls[0]?.headers.get("Authorization")).toBe("Bearer tok-1");
    expect(calls[0]?.headers.get("Accept")).toBe("application/json");
  });

  it("omits Authorization when there is no token (browser BFF calls)", async () => {
    const { fn, calls } = fakeFetch(200, [account]);
    const api = createApiClient({ baseUrl: "/api/bff", fetch: fn });

    await api.accounts.list();

    expect(calls[0]?.url).toBe("/api/bff/api/v1/accounts");
    expect(calls[0]?.headers.has("Authorization")).toBe(false);
  });

  it("records a transaction with its Idempotency-Key and reports accepted vs replayed", async () => {
    const accepted = fakeFetch(202, transaction);
    const api = createApiClient({ baseUrl: "https://api.test", fetch: accepted.fn });

    const result = await api.transactions.record({
      kind: "EXPENSE", accountId: account.id, amount: "850", currency: "INR",
      categoryId: transaction.categoryId, occurredOn: "2026-11-06", description: "Dinner",
    }, "web-7f3a9c21");

    expect(result.replayed).toBe(false);
    expect(result.transaction.status).toBe("PENDING");
    expect(accepted.calls[0]?.method).toBe("POST");
    expect(accepted.calls[0]?.headers.get("Idempotency-Key")).toBe("web-7f3a9c21");
    expect(accepted.calls[0]?.headers.get("Content-Type")).toBe("application/json");
    expect(JSON.parse(accepted.calls[0]!.body!)).toMatchObject({ kind: "EXPENSE", amount: "850" });

    const replay = fakeFetch(200, transaction);
    const again = await createApiClient({ baseUrl: "https://api.test", fetch: replay.fn })
      .transactions.record({ kind: "EXPENSE", accountId: account.id, amount: "850", currency: "INR", occurredOn: "2026-11-06" }, "web-7f3a9c21");
    expect(again.replayed).toBe(true);
  });

  it("builds list queries, skipping empty filters", async () => {
    const { fn, calls } = fakeFetch(200, { items: [transaction], nextCursor: "abc" });
    const api = createApiClient({ baseUrl: "https://api.test", fetch: fn });

    const page = await api.transactions.list({ from: "2026-11-01", kind: "EXPENSE", accountId: undefined, limit: 20, cursor: "xyz" });

    expect(page.nextCursor).toBe("abc");
    expect(calls[0]?.url).toBe("https://api.test/api/v1/transactions?from=2026-11-01&kind=EXPENSE&limit=20&cursor=xyz");
  });

  it("turns problem+json into an ApiError with code and field errors", async () => {
    const { fn } = fakeFetch(400, {
      status: 400, title: "Bad Request", detail: "Request validation failed", code: "VALIDATION_FAILED",
      errors: [{ field: "name", message: "must not be blank" }],
    });
    const api = createApiClient({ baseUrl: "https://api.test", fetch: fn });

    const error = await api.accounts.open({ name: "", type: "BANK", currency: "INR" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: "VALIDATION_FAILED", fieldErrors: { name: "must not be blank" } });
  });

  it("maps non-problem failures to an ApiError with a generic code", async () => {
    const fn = (async () => new Response("<html>bad gateway</html>", { status: 502 })) as unknown as typeof fetch;
    const error = await createApiClient({ baseUrl: "https://api.test", fetch: fn }).accounts.list().catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 502, code: "HTTP_502" });
  });

  it("fails loudly when the backend contract drifts", async () => {
    const { fn } = fakeFetch(200, [{ ...account, balance: 50000 }]);
    const error = await createApiClient({ baseUrl: "https://api.test", fetch: fn }).accounts.list().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiContractError);
  });

  it("adds per-request headers, e.g. the visitor's IP for rate limiting on public calls", async () => {
    const { fn, calls } = fakeFetch(201, { id: "u1", email: "a@example.com", displayName: "A", baseCurrency: "INR",
      timezone: "Asia/Kolkata", createdAt: "2026-11-06T10:00:00Z" });
    const api = createApiClient({ baseUrl: "https://api.test", fetch: fn, headers: () => ({ "X-Forwarded-For": "203.0.113.9" }) });

    await api.users.register({ email: "a@example.com", password: "correct horse battery", displayName: "A",
      baseCurrency: "INR", timezone: "Asia/Kolkata" });

    expect(calls[0]?.headers.get("X-Forwarded-For")).toBe("203.0.113.9");
  });

  it("encodes path ids and supports every resource the web app needs", async () => {
    const { fn, calls } = fakeFetch(200, { items: [], nextCursor: null });
    const api = createApiClient({ baseUrl: "https://api.test", fetch: fn });

    await api.accounts.entries("a/b", { limit: 10 });

    expect(calls[0]?.url).toBe("https://api.test/api/v1/accounts/a%2Fb/entries?limit=10");
    expect(Object.keys(api).sort()).toEqual(["accounts", "budgets", "categories", "incomeSources", "me", "transactions", "users"]);
  });
});

import { ApiError } from "@budgetflow/api-client";
import { describe, expect, it, vi } from "vitest";
import { signInPath, withSignInOnUnauthorized } from "./sign-in-redirect";

describe("sign-in redirect for server pages", () => {
  it("builds a safe sign-in link back to the current page", () => {
    expect(signInPath("/transactions?kind=EXPENSE")).toBe("/api/auth/login?returnTo=%2Ftransactions%3Fkind%3DEXPENSE");
    expect(signInPath("https://evil.test")).toBe("/api/auth/login?returnTo=%2F");
    expect(signInPath(null)).toBe("/api/auth/login?returnTo=%2F");
  });

  it("turns an API 401 into a redirect to sign in, and leaves other errors alone", async () => {
    const redirect = vi.fn((url: string) => { throw new Error(`REDIRECT ${url}`); }) as unknown as (url: string) => never;
    await expect(withSignInOnUnauthorized(async () => { throw new ApiError(401, "HTTP_401", "x"); }, "/budgets", redirect))
      .rejects.toThrow("REDIRECT /api/auth/login?returnTo=%2Fbudgets");
    await expect(withSignInOnUnauthorized(async () => { throw new ApiError(404, "ACCOUNT_NOT_FOUND", "x"); }, "/", redirect))
      .rejects.toMatchObject({ code: "ACCOUNT_NOT_FOUND" });
    expect(await withSignInOnUnauthorized(async () => 42, "/", redirect)).toBe(42);
  });
});

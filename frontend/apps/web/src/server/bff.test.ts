import { describe, expect, it, vi } from "vitest";
import type { AuthConfig } from "./auth/config";
import { forwardToApi } from "./bff";

const cfg = {
  issuer: "https://auth.test", internalIssuer: "https://auth.test", clientId: "c", clientSecret: "s",
  appUrl: "https://app.test", apiUrl: "http://gateway:8080", sessionSecret: "x".repeat(40), secureCookies: true,
} satisfies AuthConfig;
const session = { accessToken: "tok", refreshToken: "r", idToken: "i", userId: "u", expiresAt: Date.now() + 600_000 };

function upstream(status = 202, body = '{"id":"t1"}') {
  return vi.fn(async () => new Response(body, { status, headers: { "Content-Type": "application/json" } }));
}

function request(method: string, path: string, headers: Record<string, string> = {}, body?: string) {
  return new Request(`https://app.test/api/bff/${path}`, { method, headers, body: body ?? null });
}

describe("BFF proxy", () => {
  it("forwards API calls with the bearer token, query, body and idempotency key", async () => {
    const fetchFn = upstream();
    const res = await forwardToApi(
      request("POST", "api/v1/transactions?x=1", { Origin: "https://app.test", "Content-Type": "application/json", "Idempotency-Key": "k-12345678" }, '{"a":1}'),
      ["api", "v1", "transactions"], session, cfg, fetchFn as unknown as typeof fetch);

    expect(res.status).toBe(202);
    expect(await res.text()).toBe('{"id":"t1"}');
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://gateway:8080/api/v1/transactions?x=1");
    const headers = new Headers(init.headers);
    expect(headers.get("Authorization")).toBe("Bearer tok");
    expect(headers.get("Idempotency-Key")).toBe("k-12345678");
    expect(init.body).toBe('{"a":1}');
  });

  it("answers 401 without a session so the client can send the user to sign in", async () => {
    const res = await forwardToApi(request("GET", "api/v1/accounts"), ["api", "v1", "accounts"], null, cfg, upstream() as unknown as typeof fetch);
    expect(res.status).toBe(401);
    expect(await res.json()).toMatchObject({ code: "SESSION_EXPIRED" });
  });

  it("refuses cross-site writes (CSRF) and paths outside the public API", async () => {
    const fetchFn = upstream();
    const crossSite = await forwardToApi(request("POST", "api/v1/accounts", { Origin: "https://evil.test" }, "{}"),
      ["api", "v1", "accounts"], session, cfg, fetchFn as unknown as typeof fetch);
    const noOrigin = await forwardToApi(request("DELETE", "api/v1/accounts"), ["api", "v1", "accounts"], session, cfg, fetchFn as unknown as typeof fetch);
    const internal = await forwardToApi(request("GET", "actuator/health"), ["actuator", "health"], session, cfg, fetchFn as unknown as typeof fetch);
    const traversal = await forwardToApi(request("GET", "api/v1/../../actuator"), ["api", "v1", "..", "..", "actuator"], session, cfg, fetchFn as unknown as typeof fetch);

    expect([crossSite.status, noOrigin.status, internal.status, traversal.status]).toEqual([403, 403, 404, 404]);
    expect(fetchFn).not.toHaveBeenCalled();
  });

  it("passes empty answers (204 No Content, 202 Accepted) through as empty, never as an error", async () => {
    for (const status of [204, 202]) {
      const fetchFn = vi.fn(async () => new Response(null, { status }));
      const res = await forwardToApi(request("DELETE", "api/v1/users/me", { Origin: "https://app.test" }, '{"password":"x"}'),
        ["api", "v1", "users", "me"], session, cfg, fetchFn as unknown as typeof fetch);
      expect(res.status).toBe(status);
      expect(await res.text()).toBe("");
    }
  });

  it("passes upstream errors through unchanged", async () => {
    const res = await forwardToApi(request("GET", "api/v1/accounts/x"), ["api", "v1", "accounts", "x"], session, cfg,
      upstream(404, '{"code":"ACCOUNT_NOT_FOUND"}') as unknown as typeof fetch);
    expect(res.status).toBe(404);
    expect(await res.json()).toEqual({ code: "ACCOUNT_NOT_FOUND" });
  });
});

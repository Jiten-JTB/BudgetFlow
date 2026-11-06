import { describe, expect, it, vi } from "vitest";
import type { AuthConfig } from "./config";
import { authorizeUrl, exchangeCode, logoutUrl, refreshSession, safeReturnTo } from "./oauth";

const cfg: AuthConfig = {
  issuer: "https://auth.test", internalIssuer: "http://identity:9000", clientId: "budgetflow-web",
  clientSecret: "s3cret", appUrl: "https://app.test", apiUrl: "http://gateway:8080",
  sessionSecret: "x".repeat(40), secureCookies: true,
};

// header.payload.signature with payload {"sub":"user-42"}
const ACCESS = `e30.${Buffer.from(JSON.stringify({ sub: "user-42" })).toString("base64url")}.sig`;

function tokenFetch(status: number, body: object) {
  return vi.fn(async () => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

describe("oauth", () => {
  it("builds the authorize redirect with PKCE and the API scope", () => {
    const url = new URL(authorizeUrl(cfg, { state: "st", challenge: "ch" }));
    expect(url.origin + url.pathname).toBe("https://auth.test/oauth2/authorize");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      response_type: "code", client_id: "budgetflow-web", redirect_uri: "https://app.test/api/auth/callback",
      scope: "openid budgetflow.api", state: "st", code_challenge: "ch", code_challenge_method: "S256",
    });
  });

  it("exchanges the code server-to-server with client authentication", async () => {
    const fetchFn = tokenFetch(200, { access_token: ACCESS, refresh_token: "rt", id_token: "it", expires_in: 900 });
    const session = await exchangeCode(cfg, { code: "c0de", verifier: "v" }, fetchFn as unknown as typeof fetch, 1_000);

    expect(session).toEqual({ accessToken: ACCESS, refreshToken: "rt", idToken: "it", userId: "user-42", expiresAt: 901_000 });
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("http://identity:9000/oauth2/token");
    expect(new Headers(init.headers).get("Authorization")).toBe(`Basic ${Buffer.from("budgetflow-web:s3cret").toString("base64")}`);
    expect(Object.fromEntries(new URLSearchParams(init.body as string))).toEqual({
      grant_type: "authorization_code", code: "c0de", redirect_uri: "https://app.test/api/auth/callback", code_verifier: "v",
    });
  });

  it("fails the exchange on a token error", async () => {
    await expect(exchangeCode(cfg, { code: "c", verifier: "v" },
      tokenFetch(400, { error: "invalid_grant" }) as unknown as typeof fetch)).rejects.toThrow(/invalid_grant/);
  });

  it("refreshes with the rotated refresh token, or returns null when refused", async () => {
    const old = { accessToken: "a", refreshToken: "rt-1", idToken: "it", userId: "user-42", expiresAt: 0 };
    const ok = tokenFetch(200, { access_token: ACCESS, refresh_token: "rt-2", expires_in: 900 });
    const refreshed = await refreshSession(cfg, old, ok as unknown as typeof fetch, 5_000);
    expect(refreshed).toMatchObject({ refreshToken: "rt-2", idToken: "it", expiresAt: 905_000 });
    const [, init] = ok.mock.calls[0] as unknown as [string, RequestInit];
    expect(new URLSearchParams(init.body as string).get("refresh_token")).toBe("rt-1");

    expect(await refreshSession(cfg, old, tokenFetch(400, { error: "invalid_grant" }) as unknown as typeof fetch)).toBeNull();
  });

  it("logs out at the identity provider and comes back to the app", () => {
    const url = new URL(logoutUrl(cfg, "id-tok"));
    expect(url.origin + url.pathname).toBe("https://auth.test/connect/logout");
    expect(url.searchParams.get("id_token_hint")).toBe("id-tok");
    expect(url.searchParams.get("post_logout_redirect_uri")).toBe("https://app.test/signed-out");
  });

  it("only allows same-site relative return paths", () => {
    expect(safeReturnTo("/transactions?kind=EXPENSE")).toBe("/transactions?kind=EXPENSE");
    for (const bad of [null, "", "https://evil.test", "//evil.test", "/\\evil.test", "javascript:alert(1)", "transactions"]) {
      expect(safeReturnTo(bad)).toBe("/");
    }
  });
});

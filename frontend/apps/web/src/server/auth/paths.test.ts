import { describe, expect, it } from "vitest";
import { isPublicPath } from "./paths";

describe("isPublicPath", () => {
  it("lets sign-in plumbing, sign-up and assets through without a session", () => {
    for (const p of ["/api/auth/login", "/api/auth/callback", "/signup", "/signed-out", "/_next/static/x.js", "/favicon.ico"]) {
      expect(isPublicPath(p)).toBe(true);
    }
  });

  it("protects the app and the BFF", () => {
    for (const p of ["/", "/transactions", "/api/bff/api/v1/accounts", "/signupx", "/api/authx"]) {
      expect(isPublicPath(p)).toBe(false);
    }
  });
});

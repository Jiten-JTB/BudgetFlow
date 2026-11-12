import { describe, expect, it } from "vitest";
import { isPublicPath, signedOutPage } from "./paths";

describe("isPublicPath", () => {
  it("lets sign-in plumbing, sign-up and assets through without a session", () => {
    for (const p of ["/api/auth/login", "/api/auth/callback", "/signup", "/signed-out", "/_next/static/x.js", "/favicon.ico",
      "/verify-email", "/signup/check-email", "/account-deleted", "/welcome"]) {
      expect(isPublicPath(p)).toBe(true);
    }
  });

  it("protects the app and the BFF", () => {
    for (const p of ["/", "/transactions", "/api/bff/api/v1/accounts", "/signupx", "/api/authx"]) {
      expect(isPublicPath(p)).toBe(false);
    }
  });

  it("shows signed-out visitors of the home page the landing page instead of a sign-in wall", () => {
    expect(signedOutPage("/")).toBe("/welcome");
    expect(signedOutPage("/transactions")).toBeNull();
    expect(signedOutPage("/welcome")).toBeNull();
  });
});

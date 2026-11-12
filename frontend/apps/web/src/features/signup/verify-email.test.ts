import { ApiError } from "@budgetflow/api-client";
import { describe, expect, it, vi } from "vitest";
import { confirmEmail, resendLink } from "./verify-email";

describe("confirmEmail", () => {
  it("confirms a good link", async () => {
    const api = { verifyEmail: vi.fn(async () => undefined) };
    expect(await confirmEmail("tok", api)).toEqual({ status: "confirmed" });
    expect(api.verifyEmail).toHaveBeenCalledWith("tok");
  });

  it("explains an expired or used link and offers a new one", async () => {
    const api = { verifyEmail: vi.fn(async () => { throw new ApiError(400, "INVALID_VERIFICATION_TOKEN", "x"); }) };
    expect(await confirmEmail("old", api)).toEqual({
      status: "invalid", message: "This link has expired or was already used. Ask for a new one below." });
  });

  it("refuses an empty token without calling the server", async () => {
    const api = { verifyEmail: vi.fn() };
    expect((await confirmEmail("  ", api)).status).toBe("invalid");
    expect(api.verifyEmail).not.toHaveBeenCalled();
  });

  it("says when to retry after too many attempts", async () => {
    const api = { verifyEmail: vi.fn(async () => { throw new ApiError(429, "HTTP_429", "x"); }) };
    expect(await confirmEmail("tok", api)).toEqual({ status: "failed", message: "Too many attempts from your network. Try again in a minute." });
  });
});

describe("resendLink", () => {
  it("always answers the same way, so it never reveals whether an address has an account", async () => {
    const api = { resendVerification: vi.fn(async () => undefined) };
    expect(await resendLink("asha@example.com", api)).toEqual({ status: "sent", email: "asha@example.com" });
  });

  it("asks for a real address before calling the server", async () => {
    const api = { resendVerification: vi.fn() };
    expect(await resendLink("nope", api)).toEqual({ status: "invalid", message: "Enter the email address you signed up with" });
    expect(api.resendVerification).not.toHaveBeenCalled();
  });

  it("says when to retry after too many attempts", async () => {
    const api = { resendVerification: vi.fn(async () => { throw new ApiError(429, "HTTP_429", "x"); }) };
    expect((await resendLink("asha@example.com", api)).status).toBe("failed");
  });
});

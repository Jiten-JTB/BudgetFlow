import { ApiError } from "@budgetflow/api-client";
import { describe, expect, it, vi } from "vitest";
import { register } from "./register";

const valid = { displayName: "Ananya Rao", email: "ananya@example.com", password: "correct horse battery",
                baseCurrency: "INR", timezone: "Asia/Kolkata" };

describe("register", () => {
  it("creates the user and signals success", async () => {
    const api = { register: vi.fn(async () => ({}) as never) };
    expect(await register(valid, api)).toEqual({ status: "registered" });
    expect(api.register).toHaveBeenCalledWith(valid);
  });

  it("checks fields before calling the API", async () => {
    const api = { register: vi.fn() };
    const result = await register({ ...valid, displayName: " ", email: "nope", password: "short" }, api);
    expect(result).toEqual({ status: "invalid", values: { displayName: " ", email: "nope", baseCurrency: "INR", timezone: "Asia/Kolkata" },
      fieldErrors: { displayName: "Enter your name", email: "Enter an email address like name@example.com",
                     password: "Use at least 12 characters; a few words together works well" } });
    expect(api.register).not.toHaveBeenCalled();
  });

  it("puts API refusals on the right field and never echoes the password", async () => {
    const taken = { register: vi.fn(async () => { throw new ApiError(409, "EMAIL_ALREADY_REGISTERED", "Email is already registered"); }) };
    const r1 = await register(valid, taken);
    expect(r1).toMatchObject({ status: "invalid", fieldErrors: { email: "This email already has an account. Sign in instead." } });
    expect(JSON.stringify(r1)).not.toContain("correct horse battery");

    const weak = { register: vi.fn(async () => { throw new ApiError(400, "WEAK_PASSWORD", "Password must not contain your email address"); }) };
    expect(await register(valid, weak)).toMatchObject({ fieldErrors: { password: "Password must not contain your email address" } });
  });

  it("explains rate limiting and outages without blaming the user", async () => {
    const limited = { register: vi.fn(async () => { throw new ApiError(429, "HTTP_429", "x"); }) };
    expect(await register(valid, limited)).toMatchObject({ status: "failed", message: "Too many sign-ups from your network just now. Try again in a minute." });
    const down = { register: vi.fn(async () => { throw new TypeError("fetch failed"); }) };
    expect(await register(valid, down)).toMatchObject({ status: "failed", message: "Couldn’t reach Budget Flow. Try again in a moment." });
  });
});

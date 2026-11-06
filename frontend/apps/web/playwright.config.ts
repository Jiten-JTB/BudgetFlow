import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running stack (backend via `docker compose --profile apps`, web on E2E_BASE_URL).
 * They sign up a fresh user every test, so they never depend on seeded data. Start the stack with
 * SIGNUP_RATE_TOKENS=1, otherwise the gateway's sign-up limit (5 per minute per IP) stops the run.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3300",
    locale: "en-IN",
    timezoneId: "Asia/Kolkata",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } },
    { name: "mobile", use: { ...devices["Pixel 7"] }, grep: /@mobile/ },
  ],
});

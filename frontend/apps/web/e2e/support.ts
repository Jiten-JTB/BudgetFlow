import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

export const PASSWORD = "plum orchard sunrise";

/** Signs up a brand-new user through the real pages and lands signed in on the overview. */
export async function signUpAndSignIn(page: Page, name = "E2E Tester"): Promise<string> {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  await page.goto("/signup");
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Your name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible(); // handed over to the sign-in page
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  // The default Cash account and categories arrive through backend events, shortly after sign-up.
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole("link", { name: "Cash" })).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 20_000 });
  return email;
}

/** WCAG 2.2 A/AA automated checks (axe-core). Manual checks still matter; this catches regressions. */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .exclude("nextjs-portal") // the dev-only Next.js indicator
    .analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.length})`)).toEqual([]);
}

export async function goto(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
}

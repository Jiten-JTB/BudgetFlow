import { expect, test } from "@playwright/test";
import { expectAccessible, goto, signUpAndSignIn } from "./support";

test("signed-out visitors are sent to the sign-in page", async ({ page }) => {
  await page.goto("/transactions?kind=EXPENSE");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await expectAccessible(page);
});

test("the session cookie is invisible to page scripts", async ({ page, context }) => {
  await signUpAndSignIn(page);
  const cookie = (await context.cookies()).find((c) => c.name === "bf_session");
  expect(cookie?.httpOnly).toBe(true);
  expect(await page.evaluate(() => document.cookie)).not.toContain("bf_session");
});

test("every main screen passes automated WCAG 2.2 AA checks", async ({ page }) => {
  await signUpAndSignIn(page);
  for (const path of ["/", "/transactions", "/transactions/new", "/accounts", "/accounts/new", "/budgets", "/budgets/new",
                      "/income-sources", "/categories", "/settings"]) {
    await goto(page, path);
    await test.step(path, () => expectAccessible(page));
  }
});

test("signing out ends the session @mobile", async ({ page }) => {
  await signUpAndSignIn(page);
  const menu = page.getByText("Menu", { exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole("button", { name: "Sign out" }).last().click();
  await expect(page.getByRole("heading", { name: "You’re signed out" })).toBeVisible();
  await goto(page, "/");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});

import { expect, test } from "@playwright/test";
import { confirmEmail, expectAccessible, goto, PASSWORD, signUp, signUpAndSignIn, uniqueEmail, verificationLink } from "./support";

test("a signed-out visitor sees what Budget Flow is, and can start from there", async ({ page }) => {
  await goto(page, "/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Budget Flow");
  await expect(page.getByRole("region", { name: "How much do I have?" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Am I on track this month?" })).toBeVisible();
  await expectAccessible(page);
  await page.getByRole("link", { name: "Create your account" }).first().click();
  await expect(page.getByRole("heading", { name: "Create your account" })).toBeVisible();
});

test("a new user confirms their email before signing in, and a link works only once", async ({ page }) => {
  const email = uniqueEmail();
  await signUp(page, email);
  await expect(page.getByText(email)).toBeVisible();
  await expectAccessible(page);

  // Signing in first: the right password, but the inbox isn't confirmed yet.
  await goto(page, "/api/auth/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Confirm your email first.")).toBeVisible();

  const link = await verificationLink(email);
  await confirmEmail(page, email);
  await expectAccessible(page);

  await page.goto(link);
  await page.getByRole("button", { name: "Confirm my email" }).click();
  await expect(page.getByText("This link has expired or was already used.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Send a new link" })).toBeVisible();
});

test("deleting the account erases it, signs out, and frees the email", async ({ page }) => {
  const email = await signUpAndSignIn(page);
  await goto(page, "/settings");
  await page.getByRole("button", { name: "Delete my account" }).click();
  await page.getByLabel("Your password").fill("not my password");
  await page.getByRole("button", { name: "Delete everything permanently" }).click();
  await expect(page.getByText("That password isn’t right")).toBeVisible();

  await page.getByLabel("Your password").fill(PASSWORD);
  await page.getByRole("button", { name: "Delete everything permanently" }).click();
  await expect(page.getByRole("heading", { name: "Your account is deleted" })).toBeVisible();

  await goto(page, "/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Budget Flow"); // the landing page: signed out
  await signUp(page, email); // the same address can start again at once
});

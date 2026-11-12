import { expect, test } from "@playwright/test";
import { goto, signUpAndSignIn } from "./support";

test("a new user is guided through setup, which ticks itself off and can be hidden", async ({ page }) => {
  await signUpAndSignIn(page);
  await goto(page, "/");
  const setup = page.getByRole("region", { name: "Set up your books" });
  await expect(setup).toContainText("0 of 4 done");

  await setup.getByRole("link", { name: "Open an account" }).click();
  await page.getByLabel("Name").fill("HDFC Savings");
  await page.getByLabel("Current balance").fill("25000");
  await page.getByRole("button", { name: "Open account" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("HDFC Savings");

  await goto(page, "/transactions/new?kind=INCOME");
  await expect(page.getByRole("link", { name: "Add an income source" })).toBeVisible();
  await page.getByRole("link", { name: "Add an income source" }).click();
  await page.getByLabel("Name").fill("Acme Corp");
  await page.getByRole("button", { name: "Add income source" }).click();
  await expect(page.getByRole("button", { name: "Archive Acme Corp" })).toBeVisible();

  await goto(page, "/");
  await expect(setup).toContainText("2 of 4 done");
  await expect(setup.getByRole("listitem").first()).toContainText("Done");

  await setup.getByRole("button", { name: "Hide these steps" }).click();
  await expect(setup).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("region", { name: "Money in" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Set up your books" })).toHaveCount(0);
});

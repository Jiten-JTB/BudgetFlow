import { expect, test } from "@playwright/test";
import { expectAccessible, goto, signUpAndSignIn } from "./support";

test.describe.configure({ mode: "serial" });

test("a new user records income, spending and a transfer, and every total agrees", async ({ page }) => {
  test.setTimeout(180_000); // a full month of entries across several pages
  await signUpAndSignIn(page);

  await goto(page, "/accounts/new");
  await page.getByLabel("Name").fill("HDFC Savings");
  await page.getByLabel("Type").selectOption("SAVINGS");
  await page.getByLabel("Current balance").fill("50,000");
  await page.getByRole("button", { name: "Open account" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("HDFC Savings");

  await goto(page, "/income-sources");
  await page.getByLabel("Name").fill("Acme Corp");
  await page.getByRole("button", { name: "Add income source" }).click();
  await expect(page.getByRole("button", { name: "Archive Acme Corp" })).toBeVisible();

  // Income
  await goto(page, "/transactions/new?kind=INCOME");
  await page.getByLabel("Amount").pressSequentially("110000"); // typed digit by digit, grouped as it goes
  await expect(page.getByLabel("Amount")).toHaveValue("1,10,000");
  await page.getByLabel("Account").selectOption({ label: "HDFC Savings (INR)" });
  await page.getByLabel("Income source").selectOption({ label: "Acme Corp" });
  await page.getByLabel("Description").fill("November salary");
  await page.getByRole("button", { name: "Save income" }).click();
  await expect(page.getByRole("status")).toHaveText("Income saved");

  // Expense
  await page.getByRole("button", { name: "Add another" }).click();
  await page.locator("label", { hasText: /^Expense$/ }).click(); // the styled label is what people click
  await page.getByLabel("Amount").fill("2,315");
  await page.getByLabel("Category").selectOption({ label: "Groceries" });
  await page.getByLabel("Description").fill("BigBasket groceries");
  await page.getByRole("button", { name: "Save expense" }).click();
  await expect(page.getByRole("status")).toHaveText("Expense saved");

  // Transfer to Cash
  await goto(page, "/transactions/new?kind=TRANSFER");
  await page.getByLabel("Amount").fill("5000");
  await page.getByLabel("From account").selectOption({ label: "HDFC Savings (INR)" });
  await page.getByLabel("To account").selectOption({ label: "Cash (INR)" });
  await page.getByRole("button", { name: "Save transfer" }).click();
  await expect(page.getByRole("status")).toHaveText("Transfer saved");

  await goto(page, "/");
  await expect(page.getByRole("region", { name: "Money in" })).toContainText("₹1,10,000.00");
  await expect(page.getByRole("region", { name: "Money out" })).toContainText("₹2,315.00");
  await expect(page.getByText("Kept this month")).toBeVisible();
  await expect(page.getByText("₹1,07,685.00")).toBeVisible();          // 1,10,000 − 2,315
  await expect(page.getByText(/You have/)).toContainText("₹1,57,685");  // 50,000 + 1,07,685
  await expectAccessible(page);

  // Every figure opens to its ledger lines.
  await page.getByRole("region", { name: "Money out" }).getByRole("link", { name: /Groceries/ }).click();
  await expect(page.getByRole("row", { name: /BigBasket groceries/ })).toBeVisible();
  await expect(page.locator("tbody tr")).toHaveCount(1);

  await goto(page, "/accounts");
  await expect(page.getByRole("link", { name: /HDFC Savings/ })).toContainText("₹1,52,685.00"); // − 5,000 transfer
  await expect(page.getByRole("link", { name: /^Cash/ })).toContainText("₹5,000.00");
});

test("a transaction on an archived account is cancelled with its reason, and moves no money", async ({ page }) => {
  await signUpAndSignIn(page);
  await goto(page, "/accounts/new");
  await page.getByLabel("Name").fill("Old Wallet");
  await page.getByLabel("Current balance").fill("120");
  await page.getByRole("button", { name: "Open account" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Old Wallet");
  // Keep the form open in a second tab, archive in the first: the saga must refuse the stale choice.
  const form = await page.context().newPage();
  await goto(form, "/transactions/new");
  await page.getByRole("button", { name: "Archive account" }).click();
  await page.getByRole("button", { name: "Archive Old Wallet" }).click();
  await expect(page.getByText("Archived", { exact: true })).toBeVisible();

  await form.getByLabel("Amount").fill("99");
  await form.getByLabel("Account").selectOption({ label: "Old Wallet (INR)" });
  await form.getByRole("button", { name: "Save expense" }).click();
  // Next.js also renders a route announcer with role="alert"; match ours by its text.
  await expect(form.getByRole("alert").filter({ hasText: "Not saved" })).toHaveText("Not saved: account is archived.");

  await page.reload();
  await expect(page.getByText("₹120.00").first()).toBeVisible();
});

test("budgets warn before overspending, in words", async ({ page }) => {
  await signUpAndSignIn(page);
  await goto(page, "/budgets/new");
  await page.getByLabel("Spending").selectOption({ label: "Shopping" });
  await page.getByLabel("Limit").fill("1000");
  await page.getByRole("button", { name: "Create budget" }).click();
  await expect(page.getByText("₹1,000.00 left")).toBeVisible();

  await goto(page, "/transactions/new");
  await page.getByLabel("Amount").fill("1250");
  await page.getByLabel("Account").selectOption({ label: "Cash (INR)" });
  await page.getByLabel("Category").selectOption({ label: "Shopping" });
  await page.getByRole("button", { name: "Save expense" }).click();
  await expect(page.getByRole("status")).toHaveText("Expense saved");

  await goto(page, "/budgets");
  await expect(page.getByText("₹250.00 over")).toBeVisible();
  await expect(page.getByRole("progressbar", { name: "Shopping" })).toHaveAttribute("aria-valuenow", "125");
  await expectAccessible(page);
});

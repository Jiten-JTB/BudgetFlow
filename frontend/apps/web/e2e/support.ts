import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

export const PASSWORD = "plum orchard sunrise";

/** Local Mailpit (infra/docker-compose.yml) catches the emails the stack sends. */
const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:58025";

export function uniqueEmail(): string {
  return `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

/** Fills the sign-up form and lands on "Check your inbox". */
export async function signUp(page: Page, email: string, name = "E2E Tester") {
  await page.goto("/signup");
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Your name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Check your inbox" })).toBeVisible();
}

/** The newest verification link sent to this address, read from the real email. */
export async function verificationLink(email: string): Promise<string> {
  let link: string | undefined;
  await expect(async () => {
    const search = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`)).json();
    const id = search.messages?.[0]?.ID;
    expect(id).toBeTruthy();
    const message = await (await fetch(`${MAILPIT}/api/v1/message/${id}`)).json();
    link = /https?:\/\/\S+\/verify-email\?token=[A-Za-z0-9_-]+/.exec(message.Text)?.[0];
    expect(link).toBeTruthy();
  }).toPass({ timeout: 20_000 });
  return link!;
}

/** Opens the emailed link and presses Confirm, as a person would. */
export async function confirmEmail(page: Page, email: string) {
  await page.goto(await verificationLink(email));
  await page.getByRole("button", { name: "Confirm my email" }).click();
  await expect(page.getByRole("status")).toHaveText("Your email is confirmed.");
}

/** Signs up a brand-new user through the real pages, confirms the email, and lands signed in on the overview. */
export async function signUpAndSignIn(page: Page, name = "E2E Tester"): Promise<string> {
  const email = uniqueEmail();
  await signUp(page, email, name);
  await confirmEmail(page, email);
  await page.getByRole("link", { name: "Sign in" }).click();
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

import type { Metadata } from "next";
import { WelcomeView } from "@/features/welcome/welcome-view";

export const metadata: Metadata = {
  title: { absolute: "Budget Flow: a ledger for your own money" },
  description: "See how much you have and whether you’re on track this month: income by source, spending by category, balances exact to the paisa.",
};

/** Signed-out visitors of "/" see this page (proxy.ts rewrites to it; the address stays "/"). */
export default function WelcomePage() {
  return <WelcomeView />;
}

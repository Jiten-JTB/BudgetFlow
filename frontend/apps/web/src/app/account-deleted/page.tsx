import { ActionLink } from "@/components/action-link";
import { PublicSheet } from "@/components/public-sheet";

export const metadata = { title: "Account deleted" };

export default function AccountDeletedPage() {
  return (
    <PublicSheet title="Your account is deleted">
      <p className="text-mute">
        Your accounts, transactions, budgets and income sources are erased, and you’re signed out. Your email address
        is free to use again.
      </p>
      <div className="mt-6 flex flex-wrap gap-3">
        <ActionLink href="/signup">Create a new account</ActionLink>
      </div>
    </PublicSheet>
  );
}

import { PublicSheet } from "@/components/public-sheet";
import { ConfirmEmailForm } from "@/features/signup/verify-forms";
import { confirmEmailAction, resendLinkAction } from "./actions";

export const metadata = { title: "Confirm your email" };

export default async function VerifyEmailPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const token = (await searchParams).token ?? "";
  return (
    <PublicSheet title="Confirm your email">
      <ConfirmEmailForm token={token} action={confirmEmailAction} resendAction={resendLinkAction} />
    </PublicSheet>
  );
}

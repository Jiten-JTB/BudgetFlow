import Link from "next/link";
import { PublicSheet } from "@/components/public-sheet";
import { ResendLinkForm } from "@/features/signup/verify-forms";
import { pendingEmail } from "@/server/pending-email";
import { resendLinkAction } from "@/app/verify-email/actions";

export const metadata = { title: "Check your inbox" };

export default async function CheckEmailPage() {
  const email = await pendingEmail();
  return (
    <PublicSheet title="Check your inbox">
      <div className="flex flex-col gap-5">
        <p>
          We sent a link to {email ? <strong className="font-semibold">{email}</strong> : "your email address"}. Open it to
          confirm it’s you; it works for 24 hours.
        </p>
        <p className="text-sm text-mute">Nothing after a few minutes? Check spam, or send a new link.</p>
        <ResendLinkForm action={resendLinkAction} askForEmail={!email} />
        <p className="border-t border-rule pt-4 text-sm text-mute">
          Wrong address? <Link href="/signup" className="text-indigo underline">Sign up again</Link>
        </p>
      </div>
    </PublicSheet>
  );
}

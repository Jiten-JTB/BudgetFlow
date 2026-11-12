import { cookies } from "next/headers";
import { cookieOptions } from "@/server/auth/cookies";

/** The address that just signed up, so "check your inbox" can name it without putting it in a URL. */
const PENDING_EMAIL_COOKIE = "bf_pending_email";

export async function rememberPendingEmail(email: string): Promise<void> {
  (await cookies()).set(PENDING_EMAIL_COOKIE, email, cookieOptions(60 * 60));
}

export async function pendingEmail(): Promise<string | null> {
  return (await cookies()).get(PENDING_EMAIL_COOKIE)?.value ?? null;
}

"use server";

import { confirmEmail, type ConfirmState, resendLink, type ResendState } from "@/features/signup/verify-email";
import { pendingEmail } from "@/server/pending-email";
import { publicApi } from "@/server/public-api";

export async function confirmEmailAction(_previous: ConfirmState, form: FormData): Promise<ConfirmState> {
  return confirmEmail(String(form.get("token") ?? ""), (await publicApi()).users);
}

/** Uses the typed address, or the one that just signed up on this browser. */
export async function resendLinkAction(_previous: ResendState, form: FormData): Promise<ResendState> {
  const typed = String(form.get("email") ?? "").trim();
  return resendLink(typed || (await pendingEmail()) || "", (await publicApi()).users);
}

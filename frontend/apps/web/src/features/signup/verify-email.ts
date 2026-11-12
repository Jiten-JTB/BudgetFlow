import { ApiError } from "@budgetflow/api-client";

export type ConfirmState =
  | { status: "idle" }
  | { status: "confirmed" }
  | { status: "invalid"; message: string }
  | { status: "failed"; message: string };

export type ResendState =
  | { status: "idle" }
  | { status: "sent"; email: string }
  | { status: "invalid"; message: string }
  | { status: "failed"; message: string };

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const TOO_MANY = "Too many attempts from your network. Try again in a minute.";

export async function confirmEmail(token: string, api: { verifyEmail: (t: string) => Promise<void> }): Promise<ConfirmState> {
  if (!token.trim()) return { status: "invalid", message: "This link is incomplete. Open it again from the email, or ask for a new one below." };
  try {
    await api.verifyEmail(token.trim());
    return { status: "confirmed" };
  } catch (e) {
    if (e instanceof ApiError && e.code === "INVALID_VERIFICATION_TOKEN") {
      return { status: "invalid", message: "This link has expired or was already used. Ask for a new one below." };
    }
    if (e instanceof ApiError && e.status === 429) return { status: "failed", message: TOO_MANY };
    return { status: "failed", message: "Couldn’t confirm your email just now. Try again in a moment." };
  }
}

/** The server answers the same for every address; so does this, to keep accounts private. */
export async function resendLink(email: string, api: { resendVerification: (e: string) => Promise<void> }): Promise<ResendState> {
  const address = email.trim();
  if (!EMAIL.test(address)) return { status: "invalid", message: "Enter the email address you signed up with" };
  try {
    await api.resendVerification(address);
    return { status: "sent", email: address };
  } catch (e) {
    if (e instanceof ApiError && e.status === 429) return { status: "failed", message: TOO_MANY };
    return { status: "failed", message: "Couldn’t send a new link just now. Try again in a moment." };
  }
}

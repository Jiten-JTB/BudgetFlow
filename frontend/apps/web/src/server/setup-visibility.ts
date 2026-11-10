import { cookies } from "next/headers";
import { cookieOptions, readSession } from "@/server/auth/cookies";

/** Holds the id of the user who hid the setup steps, so another person signing in on this browser still sees theirs. */
const SETUP_HIDDEN_COOKIE = "bf_setup_hidden";
const ONE_YEAR = 60 * 60 * 24 * 365;

export async function isSetupHidden(): Promise<boolean> {
  const [store, session] = await Promise.all([cookies(), readSession()]);
  return !!session && store.get(SETUP_HIDDEN_COOKIE)?.value === session.userId;
}

export async function hideSetupForCurrentUser(): Promise<void> {
  const session = await readSession();
  if (!session) return;
  (await cookies()).set(SETUP_HIDDEN_COOKIE, session.userId, cookieOptions(ONE_YEAR));
}

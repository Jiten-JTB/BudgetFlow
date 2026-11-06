import { cookies } from "next/headers";
import { authConfig } from "./config";
import { type Session, SESSION_COOKIE, unseal } from "./session";

/** Current user's session in Server Components and route handlers (proxy.ts keeps it fresh). */
export async function readSession(): Promise<Session | null> {
  const store = await cookies();
  return unseal<Session>(store.get(SESSION_COOKIE)?.value, authConfig().sessionSecret);
}

export function cookieOptions(maxAgeSeconds: number, path = "/") {
  return {
    httpOnly: true,
    secure: authConfig().secureCookies,
    sameSite: "lax" as const,
    path,
    maxAge: maxAgeSeconds,
  };
}

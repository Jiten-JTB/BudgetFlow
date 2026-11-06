import { NextResponse, type NextRequest } from "next/server";
import { authConfig } from "@/server/auth/config";
import { logoutUrl } from "@/server/auth/oauth";
import { type Session, SESSION_COOKIE, unseal } from "@/server/auth/session";

/** POST only (a link must not be able to sign someone out); ends the app session and the sign-in session. */
export async function POST(request: NextRequest) {
  const cfg = authConfig();
  if (request.headers.get("origin") !== new URL(cfg.appUrl).origin) {
    return new NextResponse(null, { status: 403 });
  }
  const session = await unseal<Session>(request.cookies.get(SESSION_COOKIE)?.value, cfg.sessionSecret);
  const response = NextResponse.redirect(logoutUrl(cfg, session?.idToken), 303);
  response.cookies.delete(SESSION_COOKIE);
  return response;
}

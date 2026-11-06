import { NextResponse, type NextRequest } from "next/server";
import { cookieOptions } from "@/server/auth/cookies";
import { authConfig } from "@/server/auth/config";
import { exchangeCode, safeReturnTo } from "@/server/auth/oauth";
import { LOGIN_COOKIE, SESSION_COOKIE, SESSION_MAX_AGE_SECONDS, seal, unseal } from "@/server/auth/session";

type LoginState = { state: string; verifier: string; returnTo: string };

export async function GET(request: NextRequest) {
  const cfg = authConfig();
  const params = request.nextUrl.searchParams;
  const pending = await unseal<LoginState>(request.cookies.get(LOGIN_COOKIE)?.value, cfg.sessionSecret);

  if (params.get("error") || !pending || !params.get("code") || params.get("state") !== pending.state) {
    // Expired, replayed or forged callback: start a clean sign-in rather than trusting it.
    const retry = NextResponse.redirect(new URL("/signed-out?reason=sign-in-failed", cfg.appUrl));
    retry.cookies.delete({ name: LOGIN_COOKIE, path: "/api/auth" });
    return retry;
  }

  const session = await exchangeCode(cfg, { code: params.get("code")!, verifier: pending.verifier });
  const response = NextResponse.redirect(new URL(safeReturnTo(pending.returnTo), cfg.appUrl));
  response.cookies.set(SESSION_COOKIE, await seal(session, cfg.sessionSecret, SESSION_MAX_AGE_SECONDS),
    cookieOptions(SESSION_MAX_AGE_SECONDS));
  response.cookies.delete({ name: LOGIN_COOKIE, path: "/api/auth" });
  return response;
}

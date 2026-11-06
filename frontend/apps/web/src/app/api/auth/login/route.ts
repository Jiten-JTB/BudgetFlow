import { NextResponse, type NextRequest } from "next/server";
import { cookieOptions } from "@/server/auth/cookies";
import { authConfig } from "@/server/auth/config";
import { authorizeUrl, safeReturnTo } from "@/server/auth/oauth";
import { createPkcePair, randomToken } from "@/server/auth/pkce";
import { LOGIN_COOKIE, seal } from "@/server/auth/session";

const LOGIN_TTL_SECONDS = 600;

/** Starts Authorization Code + PKCE; the verifier and state wait in a sealed, short-lived cookie. */
export async function GET(request: NextRequest) {
  const cfg = authConfig();
  const { verifier, challenge } = await createPkcePair();
  const state = randomToken();
  const returnTo = safeReturnTo(request.nextUrl.searchParams.get("returnTo"));

  const response = NextResponse.redirect(authorizeUrl(cfg, { state, challenge }));
  response.cookies.set(LOGIN_COOKIE, await seal({ state, verifier, returnTo }, cfg.sessionSecret, LOGIN_TTL_SECONDS),
    cookieOptions(LOGIN_TTL_SECONDS, "/api/auth"));
  return response;
}

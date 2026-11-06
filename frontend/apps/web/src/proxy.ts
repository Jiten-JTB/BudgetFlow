import { NextResponse, type NextRequest } from "next/server";
import { authConfig } from "@/server/auth/config";
import { refreshSession } from "@/server/auth/oauth";
import { isPublicPath } from "@/server/auth/paths";
import { PATH_HEADER } from "@/server/sign-in-redirect";
import { type Session, SESSION_COOKIE, SESSION_MAX_AGE_SECONDS, seal, sessionDecision, unseal } from "@/server/auth/session";

// Refresh tokens rotate, so concurrent requests must share one refresh (single-instance deployment).
const inFlight = new Map<string, Promise<Session | null>>();

/** Guards every page and BFF call: sign in when there is no session, refresh tokens shortly before expiry. */
export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (isPublicPath(pathname)) return NextResponse.next();

  const cfg = authConfig();
  const session = await unseal<Session>(request.cookies.get(SESSION_COOKIE)?.value, cfg.sessionSecret);
  const decision = sessionDecision(session, Date.now());
  // Server pages read this to return the user here after a forced sign-in.
  request.headers.set(PATH_HEADER, pathname + search);
  if (decision === "valid") return NextResponse.next({ request: { headers: request.headers } });

  const refreshed = decision === "refresh" ? await refreshOnce(session!) : null;
  if (!refreshed) {
    if (pathname.startsWith("/api/")) {
      const res = NextResponse.next();
      res.cookies.delete(SESSION_COOKIE);
      return res; // the BFF answers 401 SESSION_EXPIRED
    }
    const login = new URL("/api/auth/login", cfg.appUrl);
    login.searchParams.set("returnTo", pathname + search);
    const res = NextResponse.redirect(login);
    res.cookies.delete(SESSION_COOKIE);
    return res;
  }

  const sealed = await seal(refreshed, cfg.sessionSecret, SESSION_MAX_AGE_SECONDS);
  // Let this request's server code see the new session, and store it in the browser.
  request.cookies.set(SESSION_COOKIE, sealed);
  const res = NextResponse.next({ request: { headers: request.headers } });
  res.cookies.set(SESSION_COOKIE, sealed, {
    httpOnly: true, secure: cfg.secureCookies, sameSite: "lax", path: "/", maxAge: SESSION_MAX_AGE_SECONDS,
  });
  return res;
}

function refreshOnce(session: Session): Promise<Session | null> {
  let pending = inFlight.get(session.refreshToken);
  if (!pending) {
    pending = refreshSession(authConfig(), session).finally(() => {
      setTimeout(() => inFlight.delete(session.refreshToken), 5_000);
    });
    inFlight.set(session.refreshToken, pending);
  }
  return pending;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};

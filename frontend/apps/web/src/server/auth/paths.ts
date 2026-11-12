/** Routes reachable without a session: exact pages, plus directory prefixes (which end in "/"). */
const PUBLIC_EXACT = new Set(["/signup", "/signed-out", "/verify-email", "/account-deleted", "/welcome", "/favicon.ico", "/robots.txt", "/icon.svg"]);
const PUBLIC_PREFIXES = ["/api/auth/", "/_next/", "/signup/"];

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_EXACT.has(pathname) || PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

/** Where a signed-out visitor sees something other than the sign-in page: the home page shows the landing page. */
export function signedOutPage(pathname: string): string | null {
  return pathname === "/" ? "/welcome" : null;
}

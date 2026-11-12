import { NextResponse, type NextRequest } from "next/server";
import { authConfig } from "@/server/auth/config";
import { SESSION_COOKIE } from "@/server/auth/session";

/**
 * After the account was erased: drop this browser's session. POST and same-origin only, like sign-out. Identity ends
 * its own sign-in session for the erased user on that user's next visit.
 */
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== new URL(authConfig().appUrl).origin) {
    return new NextResponse(null, { status: 403 });
  }
  const response = new NextResponse(null, { status: 204 });
  response.cookies.delete(SESSION_COOKIE);
  return response;
}

import type { AuthConfig } from "./auth/config";
import type { Session } from "./auth/session";

const FORWARDED_HEADERS = ["accept", "content-type", "idempotency-key"];
const UPSTREAM_TIMEOUT_MS = 15_000;

/**
 * The browser's only path to the API. Adds the user's access token server-side, so tokens never reach
 * JavaScript, and refuses anything that is not a same-origin call to the public API.
 */
export async function forwardToApi(request: Request, segments: string[], session: Session | null, cfg: AuthConfig,
                                   fetchFn: typeof fetch = fetch): Promise<Response> {
  if (segments[0] !== "api" || segments[1] !== "v1" || segments.some((s) => s === ".." || s === "." || s === "")) {
    return problem(404, "NOT_FOUND", "Not found");
  }
  const method = request.method.toUpperCase();
  if (method !== "GET" && method !== "HEAD" && request.headers.get("origin") !== new URL(cfg.appUrl).origin) {
    return problem(403, "CROSS_SITE_REQUEST", "Cross-site requests are not allowed");
  }
  if (!session) {
    return problem(401, "SESSION_EXPIRED", "Your session has ended. Sign in again.");
  }

  const headers = new Headers({ Authorization: `Bearer ${session.accessToken}` });
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const target = `${cfg.apiUrl}/${segments.map(encodeURIComponent).join("/")}${new URL(request.url).search}`;
  try {
    const upstream = await fetchFn(target, {
      method,
      headers,
      body: method === "GET" || method === "HEAD" ? null : await request.text(),
      cache: "no-store",
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("content-type") ?? "application/json", "Cache-Control": "no-store" },
    });
  } catch {
    return problem(502, "UPSTREAM_UNAVAILABLE", "Budget Flow is not reachable right now. Try again in a moment.");
  }
}

function problem(status: number, code: string, detail: string): Response {
  return Response.json({ status, code, detail }, {
    status, headers: { "Content-Type": "application/problem+json", "Cache-Control": "no-store" },
  });
}

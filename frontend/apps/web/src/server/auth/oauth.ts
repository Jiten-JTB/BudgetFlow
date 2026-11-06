import { decodeJwt } from "jose";
import type { AuthConfig } from "./config";
import type { Session } from "./session";

const SCOPE = "openid budgetflow.api";

export function callbackUrl(cfg: AuthConfig): string {
  return `${cfg.appUrl}/api/auth/callback`;
}

export function authorizeUrl(cfg: AuthConfig, p: { state: string; challenge: string }): string {
  const url = new URL(`${cfg.issuer}/oauth2/authorize`);
  url.search = new URLSearchParams({
    response_type: "code",
    client_id: cfg.clientId,
    redirect_uri: callbackUrl(cfg),
    scope: SCOPE,
    state: p.state,
    code_challenge: p.challenge,
    code_challenge_method: "S256",
  }).toString();
  return url.toString();
}

export async function exchangeCode(cfg: AuthConfig, p: { code: string; verifier: string },
                                   fetchFn: typeof fetch = fetch, now = Date.now()): Promise<Session> {
  const tokens = await tokenRequest(cfg, {
    grant_type: "authorization_code", code: p.code, redirect_uri: callbackUrl(cfg), code_verifier: p.verifier,
  }, fetchFn);
  if (!tokens.ok) throw new Error(`Token exchange failed: ${tokens.error}`);
  return toSession(tokens.body, null, now);
}

/** Refresh tokens rotate: the old one stops working once this succeeds. @returns null when refused */
export async function refreshSession(cfg: AuthConfig, session: Session,
                                     fetchFn: typeof fetch = fetch, now = Date.now()): Promise<Session | null> {
  const tokens = await tokenRequest(cfg, { grant_type: "refresh_token", refresh_token: session.refreshToken }, fetchFn);
  return tokens.ok ? toSession(tokens.body, session, now) : null;
}

export function logoutUrl(cfg: AuthConfig, idToken: string | undefined): string {
  const url = new URL(`${cfg.issuer}/connect/logout`);
  if (idToken) url.searchParams.set("id_token_hint", idToken);
  url.searchParams.set("post_logout_redirect_uri", `${cfg.appUrl}/signed-out`);
  return url.toString();
}

/** Only same-site absolute paths; anything else (other hosts, protocol-relative, schemes) goes home. */
export function safeReturnTo(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\") || /[\u0000-\u001f]/.test(raw)) {
    return "/";
  }
  return raw;
}

type TokenBody = { access_token: string; refresh_token?: string; id_token?: string; expires_in: number };

async function tokenRequest(cfg: AuthConfig, form: Record<string, string>, fetchFn: typeof fetch):
    Promise<{ ok: true; body: TokenBody } | { ok: false; error: string }> {
  const response = await fetchFn(`${cfg.internalIssuer}/oauth2/token`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
      Authorization: `Basic ${btoa(`${cfg.clientId}:${cfg.clientSecret}`)}`,
    },
    body: new URLSearchParams(form).toString(),
    cache: "no-store",
    signal: AbortSignal.timeout(10_000),
  });
  const body = (await response.json().catch(() => ({}))) as Partial<TokenBody> & { error?: string };
  if (!response.ok || !body.access_token || !body.expires_in) {
    return { ok: false, error: body.error ?? `HTTP ${response.status}` };
  }
  return { ok: true, body: body as TokenBody };
}

function toSession(body: TokenBody, previous: Session | null, now: number): Session {
  const sub = decodeJwt(body.access_token).sub;
  if (!sub) throw new Error("Access token has no subject");
  return {
    accessToken: body.access_token,
    refreshToken: body.refresh_token ?? previous?.refreshToken ?? "",
    idToken: body.id_token ?? previous?.idToken ?? "",
    userId: sub,
    expiresAt: now + body.expires_in * 1000,
  };
}

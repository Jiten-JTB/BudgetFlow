import { EncryptJWT, jwtDecrypt } from "jose";

/** Everything the server needs to call the API for a user. Never sent to the browser in readable form. */
export type Session = {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  userId: string;
  /** Access token expiry, epoch milliseconds. */
  expiresAt: number;
};

export const SESSION_COOKIE = "bf_session";
export const LOGIN_COOKIE = "bf_login";
export const SESSION_MAX_AGE_SECONDS = 30 * 24 * 60 * 60; // matches the refresh token lifetime
const REFRESH_MARGIN_MS = 60_000;

/** Encrypts and authenticates a value (JWE dir + A256GCM) with an expiry. */
export async function seal(value: object, secret: string, maxAgeSeconds: number): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  return new EncryptJWT({ v: value })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt(now)
    .setExpirationTime(now + maxAgeSeconds)
    .encrypt(await keyFrom(secret));
}

/** @returns the value, or null when missing, expired, tampered with, or sealed with another secret */
export async function unseal<T>(token: string | undefined, secret: string): Promise<T | null> {
  if (!token) return null;
  try {
    const { payload } = await jwtDecrypt(token, await keyFrom(secret));
    return (payload.v as T) ?? null;
  } catch {
    return null;
  }
}

export function sessionDecision(session: Session | null, nowMs: number): "none" | "valid" | "refresh" {
  if (!session) return "none";
  return session.expiresAt - nowMs > REFRESH_MARGIN_MS ? "valid" : "refresh";
}

async function keyFrom(secret: string): Promise<Uint8Array> {
  if (secret.length < 32) {
    throw new Error("Session secret must be at least 32 characters");
  }
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret)));
}

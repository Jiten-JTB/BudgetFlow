import { z } from "zod";

export type AuthConfig = {
  /** Public issuer the browser is redirected to (also the `iss` of tokens). */
  issuer: string;
  /** Where this server reaches the token endpoint; an internal URL in Docker, else the issuer. */
  internalIssuer: string;
  clientId: string;
  clientSecret: string;
  /** Public origin of this web app. */
  appUrl: string;
  /** API gateway as reached from this server. */
  apiUrl: string;
  /** At least 32 characters; encrypts session cookies. */
  sessionSecret: string;
  secureCookies: boolean;
};

const Env = z.object({
  AUTH_ISSUER: z.url(),
  AUTH_INTERNAL_URL: z.url().optional(),
  AUTH_CLIENT_ID: z.string().min(1).default("budgetflow-web"),
  AUTH_CLIENT_SECRET: z.string().min(1),
  APP_URL: z.url(),
  API_URL: z.url(),
  SESSION_SECRET: z.string().min(32, "SESSION_SECRET must be at least 32 characters"),
  SECURE_COOKIES: z.enum(["true", "false"]).default("true"),
});

let cached: AuthConfig | undefined;

/** Validated once; a misconfigured deployment fails on first request with a precise message. */
export function authConfig(env: Record<string, string | undefined> = process.env): AuthConfig {
  if (cached && env === process.env) return cached;
  const e = Env.parse(env);
  const strip = (u: string) => u.replace(/\/+$/, "");
  const config: AuthConfig = {
    issuer: strip(e.AUTH_ISSUER),
    internalIssuer: strip(e.AUTH_INTERNAL_URL ?? e.AUTH_ISSUER),
    clientId: e.AUTH_CLIENT_ID,
    clientSecret: e.AUTH_CLIENT_SECRET,
    appUrl: strip(e.APP_URL),
    apiUrl: strip(e.API_URL),
    sessionSecret: e.SESSION_SECRET,
    secureCookies: e.SECURE_COOKIES === "true",
  };
  if (env === process.env) cached = config;
  return config;
}

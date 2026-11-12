import { createApiClient } from "@budgetflow/api-client";
import { headers } from "next/headers";
import { authConfig } from "@/server/auth/config";

/**
 * API client for anonymous server actions (sign-up, email confirmation). Forwards the visitor's IP so the gateway's
 * per-IP limits apply to them, not to this server.
 */
export async function publicApi() {
  const h = await headers();
  const visitorIp = h.get("x-forwarded-for")?.split(",")[0]?.trim() || h.get("x-real-ip") || "";
  return createApiClient({
    baseUrl: authConfig().apiUrl,
    headers: (): Record<string, string> => (visitorIp ? { "X-Forwarded-For": visitorIp } : {}),
  });
}

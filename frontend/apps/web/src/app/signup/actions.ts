"use server";

import { createApiClient } from "@budgetflow/api-client";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { authConfig } from "@/server/auth/config";
import { register, type SignupState } from "@/features/signup/register";

/** Public: runs on the server so the browser needs no token; Next checks the Origin of every action call. */
export async function signUp(_previous: SignupState, form: FormData): Promise<SignupState> {
  const h = await headers();
  // Forward the visitor's IP so the gateway's per-IP sign-up limit applies to them, not to this server.
  const visitorIp = h.get("x-forwarded-for")?.split(",")[0]?.trim() || h.get("x-real-ip") || "";
  const api = createApiClient({
    baseUrl: authConfig().apiUrl,
    headers: (): Record<string, string> => (visitorIp ? { "X-Forwarded-For": visitorIp } : {}),
  });
  const text = (name: string) => String(form.get(name) ?? "");
  const state = await register({
    displayName: text("displayName"), email: text("email"), password: text("password"),
    baseCurrency: text("baseCurrency"), timezone: text("timezone"),
  }, api.users);
  if (state.status === "registered") redirect("/api/auth/login?returnTo=%2F");
  return state;
}

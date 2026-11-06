import "server-only";
import { createApiClient } from "@budgetflow/api-client";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { readSession } from "./auth/cookies";
import { authConfig } from "./auth/config";
import { PATH_HEADER, withSignInOnUnauthorized } from "./sign-in-redirect";

/** API client for Server Components: talks to the gateway directly with the user's token. */
export function serverApi() {
  const client = createApiClient({
    baseUrl: authConfig().apiUrl,
    getAccessToken: async () => (await readSession())?.accessToken ?? null,
  });
  return guard(client);
}

/** Wraps every call so a 401 sends the user to sign in (and back) rather than to an error page. */
function guard<T extends object>(target: T): T {
  return new Proxy(target, {
    get(obj, key) {
      const value = Reflect.get(obj, key);
      if (typeof value === "function") {
        return async (...args: unknown[]) => withSignInOnUnauthorized(
          () => (value as (...a: unknown[]) => Promise<unknown>).apply(obj, args),
          (await headers()).get(PATH_HEADER),
          redirect,
        );
      }
      return value && typeof value === "object" ? guard(value) : value;
    },
  });
}

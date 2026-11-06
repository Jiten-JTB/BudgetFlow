import { ApiError } from "@budgetflow/api-client";
import { safeReturnTo } from "./auth/oauth";

export const PATH_HEADER = "x-bf-path";

export function signInPath(currentPath: string | null): string {
  return `/api/auth/login?returnTo=${encodeURIComponent(safeReturnTo(currentPath))}`;
}

/**
 * The API refused the user's token (revoked, signing key rotated, session ended elsewhere):
 * send them through sign-in and back to the same page instead of showing an error.
 */
export async function withSignInOnUnauthorized<T>(call: () => Promise<T>, currentPath: string | null,
                                                  redirect: (url: string) => never): Promise<T> {
  try {
    return await call();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect(signInPath(currentPath));
    throw e;
  }
}

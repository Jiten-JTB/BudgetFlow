import { ApiError } from "@budgetflow/api-client";

export type SignupInput = { displayName: string; email: string; password: string; baseCurrency: string; timezone: string };
type SafeValues = Omit<SignupInput, "password">;

export type SignupState =
  | { status: "idle" }
  | { status: "registered" }
  | { status: "invalid"; values: SafeValues; fieldErrors: Partial<Record<keyof SignupInput, string>> }
  | { status: "failed"; values: SafeValues; message: string };

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Shared by the server action; the returned state never contains the password. */
export async function register(input: SignupInput, api: { register: (i: SignupInput) => Promise<unknown> }): Promise<SignupState> {
  const { password, ...values } = input;
  const fieldErrors: Partial<Record<keyof SignupInput, string>> = {};
  if (!input.displayName.trim()) fieldErrors.displayName = "Enter your name";
  if (!EMAIL.test(input.email.trim())) fieldErrors.email = "Enter an email address like name@example.com";
  if (password.length < 12) fieldErrors.password = "Use at least 12 characters; a few words together works well";
  if (Object.keys(fieldErrors).length > 0) return { status: "invalid", values, fieldErrors };

  try {
    await api.register({ ...input, displayName: input.displayName.trim(), email: input.email.trim() });
    return { status: "registered" };
  } catch (e) {
    if (e instanceof ApiError) {
      switch (e.code) {
        case "EMAIL_ALREADY_REGISTERED":
          return { status: "invalid", values, fieldErrors: { email: "This email already has an account. Sign in instead." } };
        case "WEAK_PASSWORD":
          return { status: "invalid", values, fieldErrors: { password: e.message } };
        case "INVALID_EMAIL":
        case "INVALID_DISPLAY_NAME":
          return { status: "invalid", values, fieldErrors: { [e.code === "INVALID_EMAIL" ? "email" : "displayName"]: e.message } };
        case "HTTP_429":
          return { status: "failed", values, message: "Too many sign-ups from your network just now. Try again in a minute." };
        default:
          return { status: "failed", values, message: e.message };
      }
    }
    return { status: "failed", values, message: "Couldn’t reach Budget Flow. Try again in a moment." };
  }
}

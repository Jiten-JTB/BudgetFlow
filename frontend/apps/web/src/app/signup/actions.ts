"use server";

import { redirect } from "next/navigation";
import { register, type SignupState } from "@/features/signup/register";
import { rememberPendingEmail } from "@/server/pending-email";
import { publicApi } from "@/server/public-api";

/** Public: runs on the server so the browser needs no token; Next checks the Origin of every action call. */
export async function signUp(_previous: SignupState, form: FormData): Promise<SignupState> {
  const text = (name: string) => String(form.get(name) ?? "");
  const state = await register({
    displayName: text("displayName"), email: text("email"), password: text("password"),
    baseCurrency: text("baseCurrency"), timezone: text("timezone"),
  }, (await publicApi()).users);
  if (state.status === "registered") {
    await rememberPendingEmail(text("email").trim());
    redirect("/signup/check-email");
  }
  return state;
}

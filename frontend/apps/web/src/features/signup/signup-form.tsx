"use client";

import Link from "next/link";
import { useActionState, useEffect, useId, useState } from "react";
import { Field, FormAlert, inputClass, primaryButton } from "@/components/form-kit";
import { CURRENCIES } from "@/lib/reference-data/currencies";
import type { SignupState } from "./register";

const REGION_CURRENCY: Record<string, string> = {
  IN: "INR", US: "USD", GB: "GBP", AE: "AED", SG: "SGD", AU: "AUD", CA: "CAD", JP: "JPY", CH: "CHF",
  DE: "EUR", FR: "EUR", ES: "EUR", IT: "EUR", NL: "EUR", IE: "EUR",
};

export function SignupForm({ action }: { action: (s: SignupState, f: FormData) => Promise<SignupState> }) {
  const [state, formAction, pending] = useActionState(action, { status: "idle" });
  const id = useId();
  const [timezone, setTimezone] = useState("UTC");
  const [defaultCurrency, setDefaultCurrency] = useState("INR");

  useEffect(() => {
    setTimezone(Intl.DateTimeFormat().resolvedOptions().timeZone);
    const region = new Intl.Locale(navigator.language).maximize().region;
    if (region && REGION_CURRENCY[region]) setDefaultCurrency(REGION_CURRENCY[region]);
  }, []);

  const values = state.status === "invalid" || state.status === "failed" ? state.values : undefined;
  const errors = state.status === "invalid" ? state.fieldErrors : {};

  return (
    <form action={formAction} noValidate className="flex flex-col gap-5">
      <input type="hidden" name="timezone" value={values?.timezone ?? timezone} />
      <Field id={`${id}-name`} label="Your name" error={errors.displayName}>
        {(p) => <input {...p} name="displayName" autoComplete="name" maxLength={100} defaultValue={values?.displayName} className={inputClass} />}
      </Field>
      <Field id={`${id}-email`} label="Email" error={errors.email}>
        {(p) => <input {...p} name="email" type="email" autoComplete="email" defaultValue={values?.email} className={inputClass} />}
      </Field>
      <Field id={`${id}-password`} label="Password" hint="12 or more characters" error={errors.password}>
        {(p) => <input {...p} name="password" type="password" autoComplete="new-password" className={inputClass} />}
      </Field>
      <Field id={`${id}-currency`} label="Main currency" hint="You can add accounts in other currencies">
        {(p) => (
          <select {...p} name="baseCurrency" key={defaultCurrency} defaultValue={values?.baseCurrency ?? defaultCurrency} className={inputClass}>
            {CURRENCIES.map(([c]) => <option key={c} value={c}>{c}</option>)}
          </select>
        )}
      </Field>
      {state.status === "failed" && <FormAlert>{state.message}</FormAlert>}
      {errors.email?.includes("Sign in") && (
        <p className="text-sm"><Link href="/api/auth/login" className="text-indigo underline">Sign in to your account</Link></p>
      )}
      <button type="submit" disabled={pending} className={`${primaryButton} py-3`}>{pending ? "Creating your account…" : "Create account"}</button>
    </form>
  );
}

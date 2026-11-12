"use client";

import { useActionState, useId } from "react";
import { ActionLink } from "@/components/action-link";
import { Field, FormAlert, inputClass, primaryButton, quietButton } from "@/components/form-kit";
import type { ConfirmState, ResendState } from "./verify-email";

type Confirm = (s: ConfirmState, f: FormData) => Promise<ConfirmState>;
type Resend = (s: ResendState, f: FormData) => Promise<ResendState>;

/**
 * A button, not an automatic confirm on page load: mail scanners open links, and a person should be the one who
 * confirms their address.
 */
export function ConfirmEmailForm({ token, action, resendAction }: { token: string; action: Confirm; resendAction: Resend }) {
  const [state, formAction, pending] = useActionState(action, { status: "idle" });

  if (state.status === "confirmed") {
    return (
      <div className="flex flex-col gap-4">
        <p role="status" className="text-lg font-semibold text-indigo">Your email is confirmed.</p>
        <p className="text-mute">Sign in to set up your books.</p>
        <ActionLink href="/api/auth/login?returnTo=%2F">Sign in</ActionLink>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      {state.status !== "invalid" && (
        <form action={formAction} className="flex flex-col gap-4">
          <input type="hidden" name="token" value={token} />
          <p className="text-mute">One click and your account is ready.</p>
          <FormAlert>{state.status === "failed" ? state.message : null}</FormAlert>
          <div><button type="submit" disabled={pending} className={primaryButton}>{pending ? "Confirming…" : "Confirm my email"}</button></div>
        </form>
      )}
      {state.status === "invalid" && (
        <>
          <FormAlert>{state.message}</FormAlert>
          <ResendLinkForm action={resendAction} askForEmail />
        </>
      )}
    </div>
  );
}

/** Asks for a fresh link. The answer is the same for every address, so it never reveals who has an account. */
export function ResendLinkForm({ action, askForEmail = false }: { action: Resend; askForEmail?: boolean }) {
  const [state, formAction, pending] = useActionState(action, { status: "idle" });
  const id = useId();
  return (
    <form action={formAction} noValidate className="flex flex-col gap-4">
      {askForEmail && (
        <Field id={`${id}-email`} label="Email" error={state.status === "invalid" ? state.message : undefined}>
          {(p) => <input {...p} name="email" type="email" autoComplete="email" className={inputClass} />}
        </Field>
      )}
      {state.status === "sent" && (
        <p role="status" className="text-indigo">
          If {state.email} is waiting for confirmation, a new link is on its way. Check spam too.
        </p>
      )}
      <FormAlert>{state.status === "failed" || (!askForEmail && state.status === "invalid") ? state.message : null}</FormAlert>
      <div>
        <button type="submit" disabled={pending} className={askForEmail ? primaryButton : quietButton}>
          {pending ? "Sending…" : "Send a new link"}
        </button>
      </div>
    </form>
  );
}

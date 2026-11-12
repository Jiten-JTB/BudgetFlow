"use client";

import { ApiError, type ApiClient } from "@budgetflow/api-client";
import { useId, useMemo, useState } from "react";
import { browserApi, dangerButton, Field, FormAlert, inputClass, quietButton } from "@/components/form-kit";

/** After the API erased the account: end this browser's session, then say goodbye. */
async function signOutAfterDeletion(): Promise<void> {
  await fetch("/api/auth/account-deleted", { method: "POST" });
  window.location.assign("/account-deleted");
}

/**
 * Permanently deletes the user's account. Two steps (open, then confirm with the password), the consequence in words,
 * and an easy way back out, like every other destructive action in the app.
 */
export function DeleteAccountForm({ api, onDeleted = signOutAfterDeletion }: {
  api?: Pick<ApiClient["me"], "delete">;
  onDeleted?: () => Promise<void>;
}) {
  const client = useMemo(() => api ?? browserApi().me, [api]);
  const id = useId();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [fieldError, setFieldError] = useState<string>();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!open) {
    return <button type="button" onClick={() => setOpen(true)} className={dangerButton}>Delete my account</button>;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!password) {
      setFieldError("Enter your password to confirm");
      return;
    }
    setFieldError(undefined);
    setBusy(true);
    try {
      await client.delete(password);
      await onDeleted();
    } catch (err) {
      if (err instanceof ApiError && err.code === "INCORRECT_PASSWORD") setFieldError("That password isn’t right");
      else setError("Couldn’t delete your account just now. Nothing was deleted; try again in a moment.");
      setBusy(false);
    }
  }

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4 rounded-md border border-cloth/40 p-5">
      <p>
        This erases your accounts, transactions, budgets, income sources and categories, and signs you out.{" "}
        <strong className="font-semibold">It can’t be undone.</strong>
      </p>
      <div className="max-w-sm">
        <Field id={`${id}-password`} label="Your password" error={fieldError}>
          {(p) => <input {...p} type="password" autoComplete="current-password" value={password}
            onChange={(ev) => setPassword(ev.target.value)} className={inputClass} />}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={busy} className={dangerButton}>{busy ? "Deleting…" : "Delete everything permanently"}</button>
        <button type="button" onClick={() => { setOpen(false); setPassword(""); setFieldError(undefined); }} className={quietButton}>
          Keep my account
        </button>
      </div>
    </form>
  );
}

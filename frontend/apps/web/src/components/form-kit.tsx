"use client";

import { ApiError, createApiClient } from "@budgetflow/api-client";
import { useCallback, useRef, useState } from "react";

/** Browser-side API client: every call goes through the BFF, which holds the user's tokens. */
export function browserApi() {
  return createApiClient({ baseUrl: "/api/bff" });
}

export const inputClass =
  "w-full rounded-md border border-rule-strong bg-panel px-3 py-2.5 text-ink placeholder:text-mute/70 aria-invalid:border-cloth";
export const primaryButton =
  "rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549] disabled:cursor-wait disabled:opacity-70";
export const quietButton = "rounded-md px-4 py-2.5 font-medium text-indigo hover:bg-rule disabled:opacity-60";
export const dangerButton = "rounded-md border border-cloth px-4 py-2.5 font-medium text-cloth hover:bg-cloth hover:text-white disabled:opacity-60";

type FieldProps = { id: string; "aria-invalid"?: boolean; "aria-describedby"?: string };

/** Label above, hint beside it (described, not part of the name), error below and linked to the control. */
export function Field({ id, label, hint, error, children }: {
  id: string; label: string; hint?: string | undefined; error?: string | undefined;
  children: (props: FieldProps) => React.ReactNode;
}) {
  const describedBy = [error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(" ");
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline gap-2">
        <label htmlFor={id} className="text-sm font-medium text-ink">{label}</label>
        {hint && <span id={`${id}-hint`} className="text-sm text-mute">{hint}</span>}
      </div>
      {children({ id, ...(error ? { "aria-invalid": true } : {}), ...(describedBy ? { "aria-describedby": describedBy } : {}) })}
      {error && <p id={`${id}-error`} className="text-sm text-cloth">{error}</p>}
    </div>
  );
}

export function FormAlert({ children }: { children: React.ReactNode }) {
  if (!children) return null;
  return <p role="alert" className="rounded-md bg-cloth/8 px-4 py-3 text-cloth">{children}</p>;
}

/** Runs one API action at a time and turns every failure into words the user can act on. */
export function useSubmit<T>(action: () => Promise<T>, options: { onSuccess?: (result: T) => void } = {}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const running = useRef(false);

  const submit = useCallback(async () => {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const result = await action();
      options.onSuccess?.(result);
    } catch (e) {
      if (e instanceof ApiError && e.code === "SESSION_EXPIRED") {
        setError("Your session has ended. Reload the page to sign in again.");
      } else if (e instanceof ApiError) {
        setError(e.message);
        setFieldErrors(e.fieldErrors);
      } else {
        setError("Couldn’t reach Budget Flow. Check your connection and try again.");
      }
    } finally {
      running.current = false;
      setBusy(false);
    }
  }, [action, options]);

  return { submit, busy, error, fieldErrors, setError };
}

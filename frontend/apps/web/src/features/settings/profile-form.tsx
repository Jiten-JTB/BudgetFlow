"use client";

import type { ApiClient, User } from "@budgetflow/api-client";
import { useId, useMemo, useState } from "react";
import { browserApi, Field, FormAlert, inputClass, primaryButton, useSubmit } from "@/components/form-kit";
import { CURRENCIES } from "@/lib/reference-data/currencies";
import { TIME_ZONES } from "@/lib/reference-data/time-zones";

export function ProfileForm({ me, api }: { me: User; api?: Pick<ApiClient["me"], "update"> }) {
  const client = useMemo(() => api ?? browserApi().me, [api]);
  const id = useId();
  const [saved, setSaved] = useState(me);
  const [name, setName] = useState(me.displayName);
  const [currency, setCurrency] = useState(me.baseCurrency);
  const [timezone, setTimezone] = useState(me.timezone);
  const [notice, setNotice] = useState("");

  const changes = {
    ...(name.trim() !== saved.displayName ? { displayName: name.trim() } : {}),
    ...(currency !== saved.baseCurrency ? { baseCurrency: currency } : {}),
    ...(timezone !== saved.timezone ? { timezone } : {}),
  };
  const { submit, busy, error } = useSubmit(() => client.update(changes), {
    onSuccess: (user) => { setSaved(user); setNotice("Changes saved"); },
  });

  return (
    <form noValidate className="flex flex-col gap-6 rounded-sheet bg-panel p-7 shadow-sheet"
      onSubmit={(e) => {
        e.preventDefault();
        if (Object.keys(changes).length === 0) { setNotice("Nothing to save"); return; }
        setNotice("");
        void submit();
      }}>
      <Field id={`${id}-email`} label="Email" hint="Used to sign in">
        {(p) => <input {...p} readOnly value={me.email} className={`${inputClass} bg-page text-mute`} />}
      </Field>
      <Field id={`${id}-name`} label="Name">
        {(p) => <input {...p} maxLength={100} value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />}
      </Field>
      <div className="grid gap-6 sm:grid-cols-2">
        <Field id={`${id}-currency`} label="Main currency" hint="Totals on the overview use it">
          {(p) => (
            <select {...p} value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputClass}>
              {CURRENCIES.map(([c]) => <option key={c} value={c}>{c}</option>)}
            </select>
          )}
        </Field>
        <Field id={`${id}-timezone`} label="Time zone" hint="Decides when your day and month start">
          {(p) => (
            <select {...p} value={timezone} onChange={(e) => setTimezone(e.target.value)} className={inputClass}>
              {TIME_ZONES.map((z) => <option key={z} value={z}>{z.replaceAll("_", " ")}</option>)}
            </select>
          )}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div className="flex items-center gap-4">
        <button type="submit" disabled={busy} className={primaryButton}>{busy ? "Saving…" : "Save changes"}</button>
        <p role="status" className="text-sm text-mute">{notice}</p>
      </div>
    </form>
  );
}

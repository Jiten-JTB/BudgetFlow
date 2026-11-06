"use client";

import type { ApiClient } from "@budgetflow/api-client";
import { useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { SOURCE_TYPES } from "./source-types";
import { browserApi, dangerButton, Field, FormAlert, inputClass, primaryButton, quietButton, useSubmit } from "@/components/form-kit";


export function IncomeSourceForm({ accounts, api, onDone }: {
  accounts: { id: string; name: string }[]; api?: Pick<ApiClient["incomeSources"], "create">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().incomeSources, [api]);
  const id = useId();
  const [name, setName] = useState("");
  const [type, setType] = useState("SALARY");
  const [accountId, setAccountId] = useState("");
  const [nameError, setNameError] = useState<string>();
  const { submit, busy, error } = useSubmit(() => client.create({ name: name.trim(), type, ...(accountId ? { defaultAccountId: accountId } : {}) }), {
    onSuccess: () => { setName(""); setAccountId(""); onDone ? onDone() : router.refresh(); },
  });

  return (
    <form noValidate className="flex flex-col gap-4" onSubmit={(e) => {
      e.preventDefault();
      const message = name.trim() ? undefined : "Name where the money comes from";
      setNameError(message);
      if (!message) void submit();
    }}>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field id={`${id}-name`} label="Name" error={nameError}>
          {(p) => <input {...p} maxLength={100} value={name} placeholder="e.g. Acme Corp" onChange={(e) => setName(e.target.value)} className={inputClass} />}
        </Field>
        <Field id={`${id}-type`} label="Type">
          {(p) => (
            <select {...p} value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
              {SOURCE_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          )}
        </Field>
        <Field id={`${id}-account`} label="Usually paid into">
          {(p) => (
            <select {...p} value={accountId} onChange={(e) => setAccountId(e.target.value)} className={inputClass}>
              <option value="">No usual account</option>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          )}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div><button type="submit" disabled={busy} className={primaryButton}>{busy ? "Adding…" : "Add income source"}</button></div>
    </form>
  );
}

export function CategoryForm({ parents, api, onDone }: {
  parents: { id: string; name: string; kind: "INCOME" | "EXPENSE" }[];
  api?: Pick<ApiClient["categories"], "create">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().categories, [api]);
  const id = useId();
  const [name, setName] = useState("");
  const [kind, setKind] = useState<"EXPENSE" | "INCOME">("EXPENSE");
  const [parentId, setParentId] = useState("");
  const [nameError, setNameError] = useState<string>();
  const { submit, busy, error } = useSubmit(() => client.create({ name: name.trim(), kind, ...(parentId ? { parentId } : {}) }), {
    onSuccess: () => { setName(""); setParentId(""); onDone ? onDone() : router.refresh(); },
  });

  return (
    <form noValidate className="flex flex-col gap-4" onSubmit={(e) => {
      e.preventDefault();
      const message = name.trim() ? undefined : "Give the category a name";
      setNameError(message);
      if (!message) void submit();
    }}>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field id={`${id}-name`} label="Name" error={nameError}>
          {(p) => <input {...p} maxLength={60} value={name} placeholder="e.g. Street food" onChange={(e) => setName(e.target.value)} className={inputClass} />}
        </Field>
        <Field id={`${id}-kind`} label="For">
          {(p) => (
            <select {...p} value={kind} onChange={(e) => { setKind(e.target.value as "EXPENSE" | "INCOME"); setParentId(""); }} className={inputClass}>
              <option value="EXPENSE">Spending</option>
              <option value="INCOME">Income</option>
            </select>
          )}
        </Field>
        <Field id={`${id}-parent`} label="Inside">
          {(p) => (
            <select {...p} value={parentId} onChange={(e) => setParentId(e.target.value)} className={inputClass}>
              <option value="">Nothing (top level)</option>
              {parents.filter((c) => c.kind === kind).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          )}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div><button type="submit" disabled={busy} className={primaryButton}>{busy ? "Adding…" : "Add category"}</button></div>
    </form>
  );
}

/**
 * Two-step archive for small catalog items. Takes plain data (not a callback) so Server Components can render it.
 */
export function ArchiveItemButton({ kind, id, label }: { kind: "incomeSource" | "category"; id: string; label: string }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const { submit, busy, error } = useSubmit(
    async (): Promise<void> => {
      if (kind === "incomeSource") await browserApi().incomeSources.archive(id);
      else await browserApi().categories.archive(id);
    },
    { onSuccess: () => router.refresh() },
  );
  if (!confirming) return <button type="button" onClick={() => setConfirming(true)} className={quietButton}>Archive<span className="sr-only"> {label}</span></button>;
  return (
    <span className="flex flex-wrap items-center gap-2">
      <button type="button" disabled={busy} onClick={() => void submit()} className={dangerButton}>Archive {label}</button>
      <button type="button" onClick={() => setConfirming(false)} className={quietButton}>Keep</button>
      <FormAlert>{error}</FormAlert>
    </span>
  );
}

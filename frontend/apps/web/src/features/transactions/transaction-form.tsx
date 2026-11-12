"use client";

import { ApiError, createApiClient, type RecordTransactionInput, type Transaction } from "@budgetflow/api-client";
import { formatMoney } from "@budgetflow/money";
import Link from "next/link";
import { useId, useMemo, useRef, useState } from "react";
import { AmountInput } from "@/components/amount-input";
import { Field, inputClass } from "@/components/form-kit";
import { navigatorLocale } from "@/lib/navigator-locale";
import { awaitOutcome } from "./await-outcome";
import { ActionLink } from "@/components/action-link";
import { type FieldErrors, type FormState, type Kind, toRecordInput } from "./form-model";

export type FormApi = {
  record: (input: RecordTransactionInput, idempotencyKey: string) => Promise<{ transaction: Transaction; replayed: boolean }>;
  get: (id: string) => Promise<Transaction>;
};

type AccountOption = { id: string; name: string; currency: string };
type CategoryOption = { id: string; name: string; kind: "INCOME" | "EXPENSE"; parentId: string | null };
type SourceOption = { id: string; name: string; defaultAccountId: string | null };

type Props = {
  accounts: AccountOption[];
  categories: CategoryOption[];
  incomeSources: SourceOption[];
  today: string;
  initialKind?: Kind;
  api?: FormApi;
  newKey?: () => string;
  pollIntervalMs?: number;
};

type Phase =
  | { name: "editing" }
  | { name: "saving" }
  | { name: "confirming" }
  | { name: "saved"; transaction: Transaction; settled: boolean };

const KINDS: { value: Kind; label: string }[] = [
  { value: "EXPENSE", label: "Expense" },
  { value: "INCOME", label: "Income" },
  { value: "TRANSFER", label: "Transfer" },
];
const NOUN: Record<Kind, string> = { EXPENSE: "expense", INCOME: "income", TRANSFER: "transfer" };

function browserApi(): FormApi {
  const client = createApiClient({ baseUrl: "/api/bff" });
  return { record: client.transactions.record, get: client.transactions.get };
}

export function TransactionForm({ accounts, categories, incomeSources, today, initialKind = "EXPENSE",
                                  api, newKey = () => crypto.randomUUID(), pollIntervalMs = 400 }: Props) {
  const client = useMemo(() => api ?? browserApi(), [api]);
  const empty: FormState = { kind: initialKind, amount: "", accountId: "", counterAccountId: "", categoryId: "",
                             incomeSourceId: "", occurredOn: today, description: "" };
  const [form, setForm] = useState<FormState>(empty);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [alert, setAlert] = useState<React.ReactNode>(null);
  const [phase, setPhase] = useState<Phase>({ name: "editing" });
  // One key per intended transaction: retries reuse it, so the server records it at most once.
  const [key, setKey] = useState(newKey);
  const amountRef = useRef<HTMLInputElement>(null);
  const id = useId();

  const account = accounts.find((a) => a.id === form.accountId);
  const set = <K extends keyof FormState>(field: K, value: FormState[K]) => {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };

  function chooseSource(sourceId: string) {
    const source = incomeSources.find((s) => s.id === sourceId);
    setForm((f) => ({ ...f, incomeSourceId: sourceId,
      accountId: !f.accountId && source?.defaultAccountId ? source.defaultAccountId : f.accountId }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setAlert(null);
    const result = toRecordInput(form, accounts);
    if (!result.ok) {
      setErrors(result.errors);
      const first = Object.keys(result.errors)[0];
      document.getElementById(`${id}-${first}`)?.focus();
      return;
    }
    setPhase({ name: "saving" });
    try {
      const { transaction } = await client.record(result.input, key);
      setPhase({ name: "confirming" });
      const settled = await awaitOutcome(client.get, transaction.id, { intervalMs: pollIntervalMs });
      if (settled.status === "CANCELLED") {
        setKey(newKey()); // that key now belongs to the cancelled transaction
        setPhase({ name: "editing" });
        setAlert(`Not saved: ${lowerFirst(settled.failureReason ?? "it could not be applied")}.`);
        return;
      }
      setPhase({ name: "saved", transaction: settled, settled: settled.status === "COMPLETED" });
    } catch (e) {
      setPhase({ name: "editing" });
      if (e instanceof ApiError && e.code === "SESSION_EXPIRED") {
        setAlert(<>Your session has ended. <a className="underline" href="/api/auth/login?returnTo=/transactions/new">Sign in again</a> and try once more.</>);
      } else if (e instanceof ApiError) {
        setErrors(e.fieldErrors as FieldErrors);
        setAlert(e.message);
      } else {
        setAlert("Couldn’t reach Budget Flow. Nothing was saved twice; try again.");
      }
    }
  }

  function addAnother() {
    setForm((f) => ({ ...empty, kind: f.kind, accountId: f.accountId, occurredOn: f.occurredOn }));
    setErrors({});
    setAlert(null);
    setKey(newKey());
    setPhase({ name: "editing" });
    requestAnimationFrame(() => amountRef.current?.focus());
  }

  const busy = phase.name === "saving" || phase.name === "confirming";
  const noun = NOUN[form.kind];
  const statusText = phase.name === "confirming" ? `Confirming your ${noun}…`
    : phase.name === "saved" ? (phase.settled ? `${capitalise(noun)} saved` : `${capitalise(noun)} recorded; still confirming`) : "";

  return (
    <div className="flex flex-col gap-4">
      <p role="status" aria-live="polite" className="sr-only">{statusText}</p>

      {phase.name === "saved" ? (
        <div className="flex flex-col gap-4 rounded-sheet bg-panel p-7 shadow-sheet">
          <p className="text-xl font-semibold text-indigo" aria-hidden>{statusText}</p>
          <p className="text-mute">
            <span className="num font-medium text-ink">
              {formatMoney(phase.transaction.amount, phase.transaction.currency, { locale: navigatorLocale() })}
            </span>
            {phase.settled ? " is in your ledger." : " will appear in Transactions as soon as it is confirmed."}
          </p>
          <div className="flex flex-wrap gap-3">
            <button type="button" onClick={addAnother}
              className="rounded-md bg-indigo px-5 py-2.5 font-semibold text-white hover:bg-[#1d2549]">Add another</button>
            <Link href="/" className="rounded-md px-5 py-2.5 font-medium text-indigo hover:bg-rule">Go to overview</Link>
          </div>
        </div>
      ) : (
        <form noValidate onSubmit={submit} className="flex flex-col gap-6 rounded-sheet bg-panel p-7 shadow-sheet">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-medium text-mute">Type</legend>
            <div className="inline-flex w-fit rounded-md border border-rule-strong p-1">
              {KINDS.map((k) => (
                <label key={k.value} className="cursor-pointer rounded px-4 py-1.5 text-sm font-medium text-mute has-checked:bg-indigo has-checked:text-white has-focus-visible:outline-2 has-focus-visible:outline-indigo">
                  <input type="radio" name="kind" value={k.value} checked={form.kind === k.value}
                    onChange={() => { set("kind", k.value); set("categoryId", ""); }} className="sr-only" />
                  {k.label}
                </label>
              ))}
            </div>
          </fieldset>

          <Field id={`${id}-amount`} label="Amount" hint={account ? account.currency : undefined} error={errors.amount}>
            {(props) => (
              <AmountInput {...props} ref={amountRef} autoFocus
                placeholder="0.00" value={form.amount} onValueChange={(v) => set("amount", v)}
                className={`${inputClass} num max-w-xs text-money-lg font-semibold text-indigo`} />
            )}
          </Field>

          <div className="grid gap-6 sm:grid-cols-2">
            <Field id={`${id}-accountId`} label={form.kind === "TRANSFER" ? "From account" : "Account"} error={errors.accountId}>
              {(props) => (
                <select {...props} value={form.accountId} onChange={(e) => set("accountId", e.target.value)} className={inputClass}>
                  <option value="">Choose…</option>
                  {accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
                </select>
              )}
            </Field>

            {form.kind === "TRANSFER" ? (
              <Field id={`${id}-counterAccountId`} label="To account" error={errors.counterAccountId}>
                {(props) => (
                  <>
                    <select {...props} value={form.counterAccountId} onChange={(e) => set("counterAccountId", e.target.value)} className={inputClass}>
                      <option value="">Choose…</option>
                      {accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
                    </select>
                    {accounts.length < 2 && (
                      <div className="flex flex-col items-start gap-2">
                        <p className="text-sm text-mute">Transfers move money between two of your accounts.</p>
                        <ActionLink href="/accounts/new" size="sm">Open another account</ActionLink>
                      </div>
                    )}
                  </>
                )}
              </Field>
            ) : (
              <Field id={`${id}-categoryId`} label="Category" error={errors.categoryId}>
                {(props) => (
                  <select {...props} value={form.categoryId} onChange={(e) => set("categoryId", e.target.value)} className={inputClass}>
                    <option value="">No category</option>
                    {categoryOptions(categories, form.kind === "INCOME" ? "INCOME" : "EXPENSE").map((c) => (
                      <option key={c.id} value={c.id}>{c.label}</option>
                    ))}
                  </select>
                )}
              </Field>
            )}

            {form.kind === "INCOME" && (
              <Field id={`${id}-incomeSourceId`} label="Income source" error={errors.incomeSourceId}>
                {(props) => (<>
                  <select {...props} value={form.incomeSourceId} onChange={(e) => chooseSource(e.target.value)} className={inputClass}>
                    <option value="">Not from a named source</option>
                    {incomeSources.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                  {incomeSources.length === 0 && (
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                      <p className="text-sm text-mute">None yet.</p>
                      <ActionLink href="/income-sources" size="sm">Add an income source</ActionLink>
                    </div>
                  )}
                </>)}
              </Field>
            )}

            <Field id={`${id}-occurredOn`} label="Date" error={errors.occurredOn}>
              {(props) => (
                <input {...props} type="date" value={form.occurredOn} max={today}
                  onChange={(e) => set("occurredOn", e.target.value)} className={`${inputClass} num`} />
              )}
            </Field>
          </div>

          <Field id={`${id}-description`} label="Description" error={errors.description}>
            {(props) => (
              <input {...props} type="text" maxLength={200} value={form.description}
                placeholder={form.kind === "INCOME" ? "e.g. November salary" : form.kind === "TRANSFER" ? "e.g. ATM withdrawal" : "e.g. Dinner at Toit"}
                onChange={(e) => set("description", e.target.value)} className={inputClass} />
            )}
          </Field>

          {alert && <p role="alert" className="rounded-md bg-cloth/8 px-4 py-3 text-cloth">{alert}</p>}

          <div className="flex items-center gap-4">
            <button type="submit" disabled={busy}
              className="rounded-md bg-indigo px-6 py-3 font-semibold text-white hover:bg-[#1d2549] disabled:cursor-wait disabled:opacity-70">
              {busy ? (phase.name === "confirming" ? "Confirming…" : "Saving…") : `Save ${noun}`}
            </button>
            <Link href="/" className="font-medium text-indigo hover:underline">Cancel</Link>
          </div>
        </form>
      )}
    </div>
  );
}

function categoryOptions(categories: CategoryOption[], kind: "INCOME" | "EXPENSE") {
  const ofKind = categories.filter((c) => c.kind === kind);
  const parents = ofKind.filter((c) => !c.parentId).sort((a, b) => a.name.localeCompare(b.name));
  return parents.flatMap((p) => [
    { id: p.id, label: p.name },
    ...ofKind.filter((c) => c.parentId === p.id).sort((a, b) => a.name.localeCompare(b.name))
      .map((c) => ({ id: c.id, label: `${p.name} › ${c.name}` })),
  ]);
}

function lowerFirst(s: string): string {
  return s.charAt(0).toLowerCase() + s.slice(1);
}

function capitalise(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

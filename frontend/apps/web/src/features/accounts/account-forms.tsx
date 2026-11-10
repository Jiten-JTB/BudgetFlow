"use client";

import type { Account, ApiClient } from "@budgetflow/api-client";
import { compareAmounts, MoneyInputError, parseAmountInput } from "@budgetflow/money";
import { useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { ACCOUNT_TYPES } from "./account-types";
import { CURRENCIES } from "@/lib/reference-data/currencies";
import { AmountInput } from "@/components/amount-input";
import { browserApi, dangerButton, Field, FormAlert, inputClass, primaryButton, quietButton, useSubmit } from "@/components/form-kit";


function currencyOptions(base: string): { code: string; label: string }[] {
  const named = new Map(CURRENCIES);
  return [base, ...CURRENCIES.map(([c]) => c).filter((c) => c !== base)]
    .map((code) => ({ code, label: `${code}: ${named.get(code) ?? code}` }));
}

export function OpenAccountForm({ baseCurrency, api, navigate }: {
  baseCurrency: string;
  api?: Pick<ApiClient["accounts"], "open">;
  navigate?: (href: string) => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().accounts, [api]);
  const go = navigate ?? ((href: string) => router.push(href));
  const id = useId();
  const [name, setName] = useState("");
  const [type, setType] = useState("BANK");
  const [currency, setCurrency] = useState(baseCurrency);
  const [balance, setBalance] = useState("");
  const [errors, setErrors] = useState<{ name?: string; balance?: string }>({});
  const currencies = useMemo(() => currencyOptions(baseCurrency), [baseCurrency]);

  const { submit, busy, error } = useSubmit(
    () => {
      let openingBalance: string | undefined;
      if (balance.trim()) openingBalance = parseAmountInput(balance, { allowNegative: true });
      return client.open({ name: name.trim(), type, currency, ...(openingBalance ? { openingBalance } : {}) });
    },
    { onSuccess: (account) => go(`/accounts/${account.id}`) },
  );

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!name.trim()) next.name = "Give the account a name";
    if (balance.trim()) {
      try { parseAmountInput(balance, { allowNegative: true }); } catch (err) {
        next.balance = err instanceof MoneyInputError ? err.message : "Enter a valid amount";
      }
    }
    setErrors(next);
    if (Object.keys(next).length === 0) void submit();
  }

  return (
    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-6 rounded-sheet bg-panel p-7 shadow-sheet">
      <Field id={`${id}-name`} label="Name" error={errors.name}>
        {(p) => <input {...p} autoFocus maxLength={100} value={name} placeholder="e.g. HDFC Savings"
          onChange={(e) => setName(e.target.value)} className={inputClass} />}
      </Field>
      <div className="grid gap-6 sm:grid-cols-2">
        <Field id={`${id}-type`} label="Type">
          {(p) => (
            <select {...p} value={type} onChange={(e) => setType(e.target.value)} className={inputClass}>
              {ACCOUNT_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          )}
        </Field>
        <Field id={`${id}-currency`} label="Currency" hint="Can’t be changed later">
          {(p) => (
            <select {...p} value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputClass}>
              {currencies.map((c) => <option key={c.code} value={c.code}>{c.label}</option>)}
            </select>
          )}
        </Field>
      </div>
      <Field id={`${id}-balance`} label="Current balance" hint="Negative for money owed, like a card balance" error={errors.balance}>
        {(p) => <AmountInput {...p} allowNegative value={balance} placeholder="0.00"
          onValueChange={setBalance} className={`${inputClass} num max-w-xs`} />}
      </Field>
      <FormAlert>{error}</FormAlert>
      <div><button type="submit" disabled={busy} className={primaryButton}>{busy ? "Opening…" : "Open account"}</button></div>
    </form>
  );
}

export function AdjustBalanceForm({ account, api, onDone, newKey = () => crypto.randomUUID() }: {
  account: Account;
  api?: Pick<ApiClient["accounts"], "adjust">;
  onDone?: () => void;
  newKey?: () => string;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().accounts, [api]);
  const id = useId();
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [amountError, setAmountError] = useState<string>();
  // Kept across retries so a correction is never applied twice.
  const [key, setKey] = useState(newKey);

  const { submit, busy, error } = useSubmit(
    () => client.adjust(account.id, { amount: parseAmountInput(amount, { allowNegative: true }), ...(note.trim() ? { description: note.trim() } : {}) }, key),
    { onSuccess: () => { setAmount(""); setNote(""); setKey(newKey()); onDone ? onDone() : router.refresh(); } },
  );

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    let message: string | undefined;
    try {
      const parsed = parseAmountInput(amount, { allowNegative: true });
      if (compareAmounts(parsed, "0") === 0) message = "Enter how much to add (or a negative amount to remove)";
    } catch (err) {
      message = err instanceof MoneyInputError ? err.message : "Enter a valid amount";
    }
    setAmountError(message);
    if (!message) void submit();
  }

  return (
    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
      <p className="text-sm text-mute">
        Use this when the balance here differs from your bank or wallet. It is recorded as an adjustment in the ledger.
      </p>
      <div className="grid gap-4 sm:grid-cols-[12rem_1fr]">
        <Field id={`${id}-amount`} label="Change by" hint={account.currency} error={amountError}>
          {(p) => <AmountInput {...p} allowNegative value={amount} placeholder="-250 or 1,000"
            onValueChange={setAmount} className={`${inputClass} num`} />}
        </Field>
        <Field id={`${id}-note`} label="Note">
          {(p) => <input {...p} maxLength={200} value={note} placeholder="e.g. Matched bank statement"
            onChange={(e) => setNote(e.target.value)} className={inputClass} />}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div><button type="submit" disabled={busy} className={primaryButton}>{busy ? "Recording…" : "Record adjustment"}</button></div>
    </form>
  );
}

export function RenameAccountForm({ account, api, onDone }: {
  account: Account; api?: Pick<ApiClient["accounts"], "rename">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().accounts, [api]);
  const id = useId();
  const [name, setName] = useState(account.name);
  const { submit, busy, error } = useSubmit(() => client.rename(account.id, name.trim()),
    { onSuccess: () => (onDone ? onDone() : router.refresh()) });

  return (
    <form noValidate onSubmit={(e) => { e.preventDefault(); if (name.trim()) void submit(); }} className="flex flex-wrap items-end gap-3">
      <div className="min-w-60 flex-1">
        <Field id={`${id}-name`} label="Account name">
          {(p) => <input {...p} maxLength={100} value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />}
        </Field>
      </div>
      <button type="submit" disabled={busy || !name.trim() || name.trim() === account.name} className={quietButton}>
        {busy ? "Saving…" : "Save name"}
      </button>
      <div className="w-full"><FormAlert>{error}</FormAlert></div>
    </form>
  );
}

export function ArchiveAccountButton({ account, api, onDone }: {
  account: Account; api?: Pick<ApiClient["accounts"], "archive">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().accounts, [api]);
  const [confirming, setConfirming] = useState(false);
  const { submit, busy, error } = useSubmit(() => client.archive(account.id),
    { onSuccess: () => (onDone ? onDone() : router.refresh()) });

  if (!confirming) {
    return <button type="button" onClick={() => setConfirming(true)} className={dangerButton}>Archive account</button>;
  }
  return (
    <div className="flex flex-col gap-3 rounded-md border border-cloth/40 p-4">
      <p>
        <strong className="font-semibold">{account.name}</strong> stops accepting new transactions. Its history stays,
        and its name becomes free to reuse.
      </p>
      <FormAlert>{error}</FormAlert>
      <div className="flex gap-3">
        <button type="button" disabled={busy} onClick={() => void submit()} className={dangerButton}>Archive {account.name}</button>
        <button type="button" onClick={() => setConfirming(false)} className={quietButton}>Keep it</button>
      </div>
    </div>
  );
}

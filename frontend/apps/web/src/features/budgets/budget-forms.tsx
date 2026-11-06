"use client";

import type { ApiClient } from "@budgetflow/api-client";
import { compareAmounts, MoneyInputError, parseAmountInput } from "@budgetflow/money";
import { useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { browserApi, dangerButton, Field, FormAlert, inputClass, primaryButton, quietButton, useSubmit } from "@/components/form-kit";

type CategoryOption = { id: string; name: string; kind: "INCOME" | "EXPENSE"; parentId: string | null };
const PERIODS = [["MONTHLY", "Every month"], ["WEEKLY", "Every week (Mon–Sun)"], ["YEARLY", "Every year"]] as const;

function limitError(raw: string): string | undefined {
  if (!raw.trim()) return "Enter the most you want to spend";
  try {
    return compareAmounts(parseAmountInput(raw), "0") <= 0 ? "Enter the most you want to spend" : undefined;
  } catch (e) {
    return e instanceof MoneyInputError ? e.message : "Enter a valid amount";
  }
}

function thresholdError(raw: string): string | undefined {
  const n = Number(raw);
  return Number.isInteger(n) && n >= 1 && n <= 100 ? undefined : "Choose a percentage from 1 to 100";
}

export function CreateBudgetForm({ baseCurrency, categories, api, onDone }: {
  baseCurrency: string; categories: CategoryOption[]; api?: Pick<ApiClient["budgets"], "create">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().budgets, [api]);
  const id = useId();
  const tops = categories.filter((c) => c.kind === "EXPENSE" && !c.parentId).sort((a, b) => a.name.localeCompare(b.name));
  const [categoryId, setCategoryId] = useState("");
  const [name, setName] = useState("All spending");
  const [nameEdited, setNameEdited] = useState(false);
  const [period, setPeriod] = useState("MONTHLY");
  const [limit, setLimit] = useState("");
  const [threshold, setThreshold] = useState("80");
  const [errors, setErrors] = useState<{ limit?: string; threshold?: string; name?: string }>({});

  const { submit, busy, error } = useSubmit(() => client.create({
    name: name.trim(), ...(categoryId ? { categoryId } : {}), period, limit: parseAmountInput(limit),
    currency: baseCurrency, alertThresholdPercent: Number(threshold),
  }), { onSuccess: () => (onDone ? onDone() : router.push("/budgets")) });

  function chooseCategory(value: string) {
    setCategoryId(value);
    if (!nameEdited) setName(value ? tops.find((c) => c.id === value)?.name ?? "" : "All spending");
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const next = { limit: limitError(limit), threshold: thresholdError(threshold), name: name.trim() ? undefined : "Give the budget a name" };
    setErrors(next);
    if (!next.limit && !next.threshold && !next.name) void submit();
  }

  return (
    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-6 rounded-sheet bg-panel p-7 shadow-sheet">
      <div className="grid gap-6 sm:grid-cols-2">
        <Field id={`${id}-category`} label="Spending" hint="Includes its sub-categories">
          {(p) => (
            <select {...p} value={categoryId} onChange={(e) => chooseCategory(e.target.value)} className={inputClass}>
              <option value="">All spending</option>
              {tops.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          )}
        </Field>
        <Field id={`${id}-name`} label="Name" error={errors.name}>
          {(p) => <input {...p} maxLength={100} value={name} onChange={(e) => { setName(e.target.value); setNameEdited(true); }} className={inputClass} />}
        </Field>
        <Field id={`${id}-limit`} label="Limit" hint={baseCurrency} error={errors.limit}>
          {(p) => <input {...p} inputMode="decimal" autoComplete="off" value={limit} placeholder="10000"
            onChange={(e) => setLimit(e.target.value)} className={`${inputClass} num`} />}
        </Field>
        <Field id={`${id}-period`} label="Period">
          {(p) => (
            <select {...p} value={period} onChange={(e) => setPeriod(e.target.value)} className={inputClass}>
              {PERIODS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          )}
        </Field>
        <Field id={`${id}-threshold`} label="Warn me at" hint="% of the limit" error={errors.threshold}>
          {(p) => <input {...p} inputMode="numeric" value={threshold} onChange={(e) => setThreshold(e.target.value)} className={`${inputClass} num max-w-28`} />}
        </Field>
      </div>
      <FormAlert>{error}</FormAlert>
      <div><button type="submit" disabled={busy} className={primaryButton}>{busy ? "Creating…" : "Create budget"}</button></div>
    </form>
  );
}

export function BudgetLimitForm({ budget, api, onDone }: {
  budget: { id: string; limit: string; alertThresholdPercent: number; currency: string };
  api?: Pick<ApiClient["budgets"], "update">; onDone?: () => void;
}) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().budgets, [api]);
  const id = useId();
  const [limit, setLimit] = useState(trimZeros(budget.limit));
  const [threshold, setThreshold] = useState(String(budget.alertThresholdPercent));
  const [errors, setErrors] = useState<{ limit?: string; threshold?: string }>({});
  const { submit, busy, error } = useSubmit(() => client.update(budget.id, { limit: parseAmountInput(limit), alertThresholdPercent: Number(threshold) }),
    { onSuccess: () => (onDone ? onDone() : router.refresh()) });

  return (
    <form noValidate className="flex flex-wrap items-end gap-3" onSubmit={(e) => {
      e.preventDefault();
      const next = { limit: limitError(limit), threshold: thresholdError(threshold) };
      setErrors(next);
      if (!next.limit && !next.threshold) void submit();
    }}>
      <Field id={`${id}-limit`} label="Limit" hint={budget.currency} error={errors.limit}>
        {(p) => <input {...p} inputMode="decimal" value={limit} onChange={(e) => setLimit(e.target.value)} className={`${inputClass} num w-36`} />}
      </Field>
      <Field id={`${id}-threshold`} label="Warn me at" hint="%" error={errors.threshold}>
        {(p) => <input {...p} inputMode="numeric" value={threshold} onChange={(e) => setThreshold(e.target.value)} className={`${inputClass} num w-20`} />}
      </Field>
      <button type="submit" disabled={busy} className={quietButton}>{busy ? "Saving…" : "Save"}</button>
      <div className="w-full"><FormAlert>{error}</FormAlert></div>
    </form>
  );
}

export function ArchiveBudgetButton({ budgetId, name, api }: { budgetId: string; name: string; api?: Pick<ApiClient["budgets"], "archive"> }) {
  const router = useRouter();
  const client = useMemo(() => api ?? browserApi().budgets, [api]);
  const [confirming, setConfirming] = useState(false);
  const { submit, busy, error } = useSubmit(() => client.archive(budgetId), { onSuccess: () => router.refresh() });
  if (!confirming) return <button type="button" onClick={() => setConfirming(true)} className={quietButton}>Archive</button>;
  return (
    <span className="flex flex-wrap items-center gap-2">
      <span className="text-sm">Stop tracking {name}?</span>
      <button type="button" disabled={busy} onClick={() => void submit()} className={dangerButton}>Archive</button>
      <button type="button" onClick={() => setConfirming(false)} className={quietButton}>Keep</button>
      <FormAlert>{error}</FormAlert>
    </span>
  );
}

function trimZeros(amount: string): string {
  return amount.includes(".") ? amount.replace(/\.?0+$/, "") : amount;
}

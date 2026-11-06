import type { Transaction } from "@budgetflow/api-client";

/** Pending and cancelled transactions say so in words; completed ones need no badge. */
export function StatusNote({ transaction: t }: { transaction: Transaction }) {
  if (t.status === "PENDING") {
    return (
      <span className="ml-2 inline-flex items-center gap-1.5 rounded-full border border-rule-strong px-2 py-0.5 text-xs text-mute">
        <span aria-hidden className="size-1.5 animate-pulse rounded-full bg-thread" />
        Confirming
      </span>
    );
  }
  if (t.status === "CANCELLED") {
    const reason = t.failureReason ? lowerFirst(t.failureReason) : "it could not be applied";
    return <span className="ml-2 text-xs text-cloth">Cancelled: {reason}</span>;
  }
  return null;
}

function lowerFirst(s: string): string {
  return s.charAt(0).toLowerCase() + s.slice(1);
}

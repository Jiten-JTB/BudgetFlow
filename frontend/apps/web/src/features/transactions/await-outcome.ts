import type { Transaction } from "@budgetflow/api-client";

type Options = { intervalMs?: number; timeoutMs?: number; sleep?: (ms: number) => Promise<void>; now?: () => number };

/**
 * A recorded transaction is confirmed by the saga within a second or two. Poll until it settles; past the
 * timeout, report it still pending (it will still settle, and the transactions list shows it when it does).
 */
export async function awaitOutcome(get: (id: string) => Promise<Transaction>, id: string, options: Options = {}): Promise<Transaction> {
  const { intervalMs = 400, timeoutMs = 10_000, sleep = (ms) => new Promise((r) => setTimeout(r, ms)), now = Date.now } = options;
  const started = now();
  let last: Transaction | undefined;
  for (;;) {
    try {
      last = await get(id);
      if (last.status !== "PENDING") return last;
    } catch {
      // Transient network trouble: keep trying until the deadline.
    }
    if (now() - started + intervalMs > timeoutMs) {
      if (last) return last;
      throw new Error("Could not check the transaction's status");
    }
    await sleep(intervalMs);
  }
}

import type { Transaction } from "@budgetflow/api-client";
import { describe, expect, it, vi } from "vitest";
import { awaitOutcome } from "./await-outcome";

const t = (status: Transaction["status"]) => ({ id: "t1", status }) as Transaction;

describe("awaitOutcome", () => {
  it("polls until the saga settles", async () => {
    const get = vi.fn().mockResolvedValueOnce(t("PENDING")).mockResolvedValueOnce(t("PENDING")).mockResolvedValueOnce(t("COMPLETED"));
    const sleep = vi.fn(async () => {});

    const result = await awaitOutcome(get, "t1", { sleep, intervalMs: 400, timeoutMs: 10_000 });

    expect(result.status).toBe("COMPLETED");
    expect(get).toHaveBeenCalledTimes(3);
    expect(sleep).toHaveBeenCalledWith(400);
  });

  it("returns a cancellation as soon as it is known", async () => {
    const get = vi.fn().mockResolvedValue(t("CANCELLED"));
    expect((await awaitOutcome(get, "t1", { sleep: async () => {} })).status).toBe("CANCELLED");
    expect(get).toHaveBeenCalledTimes(1);
  });

  it("gives up after the timeout and reports it still pending", async () => {
    let now = 0;
    const get = vi.fn().mockResolvedValue(t("PENDING"));
    const result = await awaitOutcome(get, "t1", {
      intervalMs: 500, timeoutMs: 2_000, now: () => now, sleep: async (ms) => { now += ms; },
    });
    expect(result.status).toBe("PENDING");
    expect(get.mock.calls.length).toBeLessThanOrEqual(5);
  });

  it("keeps polling through a transient network error", async () => {
    const get = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(t("COMPLETED"));
    expect((await awaitOutcome(get, "t1", { sleep: async () => {} })).status).toBe("COMPLETED");
  });
});

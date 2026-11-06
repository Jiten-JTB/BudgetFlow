// @vitest-environment jsdom
import { ApiError } from "@budgetflow/api-client";
import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useSubmit } from "./form-kit";

describe("useSubmit", () => {
  it("runs the action once at a time and reports success", async () => {
    const action = vi.fn(async () => "done");
    const onSuccess = vi.fn();
    const { result } = renderHook(() => useSubmit(action, { onSuccess }));

    await act(async () => { await result.current.submit(); });

    expect(action).toHaveBeenCalledTimes(1);
    expect(onSuccess).toHaveBeenCalledWith("done");
    expect(result.current.busy).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("maps API refusals to their message and field errors", async () => {
    const { result } = renderHook(() => useSubmit(async () => {
      throw new ApiError(409, "DUPLICATE_ACCOUNT_NAME", "An active account with this name already exists", { name: "taken" });
    }));

    await act(async () => { await result.current.submit(); });

    expect(result.current.error).toBe("An active account with this name already exists");
    expect(result.current.fieldErrors).toEqual({ name: "taken" });
  });

  it("explains an expired session and network failures in plain words", async () => {
    const expired = renderHook(() => useSubmit(async () => { throw new ApiError(401, "SESSION_EXPIRED", "x"); }));
    await act(async () => { await expired.result.current.submit(); });
    expect(expired.result.current.error).toBe("Your session has ended. Reload the page to sign in again.");

    const offline = renderHook(() => useSubmit(async () => { throw new TypeError("Failed to fetch"); }));
    await act(async () => { await offline.result.current.submit(); });
    expect(offline.result.current.error).toBe("Couldn’t reach Budget Flow. Check your connection and try again.");
  });

  it("ignores a second submit while the first is running", async () => {
    let release!: () => void;
    const action = vi.fn(() => new Promise<void>((r) => { release = r; }));
    const { result } = renderHook(() => useSubmit(action));

    let first!: Promise<void>;
    act(() => { first = result.current.submit(); });
    await act(async () => { await result.current.submit(); });
    release();
    await act(async () => { await first; });

    expect(action).toHaveBeenCalledTimes(1);
  });
});

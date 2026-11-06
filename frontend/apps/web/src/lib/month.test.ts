import { describe, expect, it } from "vitest";
import { currentMonth, monthLabel, monthRange, parseMonth, rangeLabel, shiftMonth } from "./month";

describe("month helpers", () => {
  it("finds the current month in the user's timezone, not the server's", () => {
    const lateNightUtc = new Date("2026-11-30T20:00:00Z"); // already 1 December in Kolkata
    expect(currentMonth("Asia/Kolkata", lateNightUtc)).toBe("2026-12");
    expect(currentMonth("America/New_York", lateNightUtc)).toBe("2026-11");
  });

  it("gives inclusive calendar ranges, leap years included", () => {
    expect(monthRange("2026-11")).toEqual({ from: "2026-11-01", to: "2026-11-30" });
    expect(monthRange("2028-02")).toEqual({ from: "2028-02-01", to: "2028-02-29" });
  });

  it("steps across year boundaries", () => {
    expect(shiftMonth("2026-12", 1)).toBe("2027-01");
    expect(shiftMonth("2026-01", -1)).toBe("2025-12");
  });

  it("parses only well-formed months", () => {
    expect(parseMonth("2026-11")).toBe("2026-11");
    for (const bad of [undefined, "", "2026-13", "2026-1", "nov", "2026-11-01"]) expect(parseMonth(bad)).toBeNull();
  });

  it("labels inclusive ranges compactly in the reader's format", () => {
    expect(rangeLabel("2026-11-01", "2026-11-30", "en-IN")).toBe("1–30 Nov 2026");
    // ICU sets thin spaces around the dash when the range spans years.
    expect(rangeLabel("2026-12-28", "2027-01-03", "en-IN")).toBe("28 Dec 2026\u2009–\u20093 Jan 2027");
  });

  it("labels months for the reader", () => {
    expect(monthLabel("2026-11", "en-IN")).toBe("November 2026");
  });
});

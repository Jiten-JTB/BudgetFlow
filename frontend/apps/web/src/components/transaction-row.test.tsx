// @vitest-environment jsdom
import type { Transaction } from "@budgetflow/api-client";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TransactionRow } from "./transaction-row";

const transfer = {
  id: "t", kind: "TRANSFER", status: "COMPLETED", accountId: "hdfc", counterAccountId: "cash", amount: "5000.0000",
  currency: "INR", categoryId: null, incomeSourceId: null, occurredOn: "2026-11-05", description: "ATM withdrawal",
  payee: null, failureCode: null, failureReason: null, createdAt: "2026-11-05T10:00:00Z", completedAt: null,
} as Transaction;

describe("TransactionRow", () => {
  it("shows transfer direction with a drawn icon and the word 'to' for screen readers", () => {
    const { container } = render(<table><tbody>
      <TransactionRow t={transfer} locale="en-IN" accountNames={{ hdfc: "HDFC Savings", cash: "Cash" }} categoryNames={{}} />
    </tbody></table>);

    const cell = container.querySelector("td.sm\\:table-cell")!;
    expect(cell.textContent).toBe("HDFC Savings to Cash");
    expect(cell.querySelector("svg[aria-hidden='true']")).not.toBeNull();
    expect(container.textContent).not.toContain("→");
  });
});

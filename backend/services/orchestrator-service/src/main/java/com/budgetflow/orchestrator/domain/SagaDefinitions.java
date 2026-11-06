package com.budgetflow.orchestrator.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The saga recipes. Operation keys are derived from the transaction id, so every participant call is idempotent
 * and its compensation can find it.
 */
public final class SagaDefinitions {

    private SagaDefinitions() {}

    public static List<SagaStep> steps(SagaType type, TransactionSagaData d) {
        return switch (type) {
            case RECORD_TRANSACTION -> recordTransaction(d);
            case TRANSFER_BETWEEN_ACCOUNTS -> transfer(d);
        };
    }

    /** Final command of every compensated saga. */
    public static SagaCommand cancel(TransactionSagaData d, String failureCode, String failureReason) {
        return SagaCommand.of("transaction.CancelTransaction", "userId", d.userId(), "transactionId", d.transactionId(),
                "failureCode", failureCode, "failureReason", failureReason);
    }

    private static List<SagaStep> recordTransaction(TransactionSagaData d) {
        boolean expense = "EXPENSE".equals(d.kind());
        List<SagaStep> steps = new ArrayList<>();
        String applyKey = key(d, "apply");
        steps.add(new SagaStep("APPLY_BALANCE",
                applyBalance(d, d.accountId(), expense ? negate(d.amount()) : d.amount(), "TRANSACTION", applyKey),
                reverseBalance(d, applyKey), false));
        if (expense) {
            String budgetKey = key(d, "budget");
            steps.add(new SagaStep("CONSUME_BUDGET",
                    SagaCommand.of("budget.ConsumeBudget", "userId", d.userId(), "transactionId", d.transactionId(),
                            "categoryId", d.categoryId(), "parentCategoryId", d.parentCategoryId(),
                            "amount", d.amount(), "currency", d.currency(), "occurredOn", d.occurredOn(),
                            "operationKey", budgetKey),
                    SagaCommand.of("budget.ReleaseBudget", "userId", d.userId(), "operationKey", budgetKey), false));
        }
        steps.add(complete(d));
        return List.copyOf(steps);
    }

    private static List<SagaStep> transfer(TransactionSagaData d) {
        String debitKey = key(d, "debit");
        String creditKey = key(d, "credit");
        return List.of(
                new SagaStep("DEBIT_SOURCE", applyBalance(d, d.accountId(), negate(d.amount()), "TRANSFER", debitKey),
                        reverseBalance(d, debitKey), false),
                new SagaStep("CREDIT_TARGET", applyBalance(d, d.counterAccountId(), d.amount(), "TRANSFER", creditKey),
                        reverseBalance(d, creditKey), false),
                complete(d));
    }

    private static SagaStep complete(TransactionSagaData d) {
        return new SagaStep("COMPLETE_TRANSACTION", SagaCommand.of("transaction.CompleteTransaction",
                "userId", d.userId(), "transactionId", d.transactionId()), null, true);
    }

    private static SagaCommand applyBalance(TransactionSagaData d, java.util.UUID accountId, String amount,
                                            String entryKind, String operationKey) {
        return SagaCommand.of("account.ApplyBalanceChange", "userId", d.userId(), "accountId", accountId,
                "amount", amount, "currency", d.currency(), "entryKind", entryKind, "operationKey", operationKey,
                "referenceId", d.transactionId());
    }

    private static SagaCommand reverseBalance(TransactionSagaData d, String operationKey) {
        return SagaCommand.of("account.ReverseBalanceChange", "userId", d.userId(), "operationKey", operationKey);
    }

    private static String key(TransactionSagaData d, String step) {
        return "txn:" + d.transactionId() + ":" + step;
    }

    private static String negate(String amount) {
        return new BigDecimal(amount).negate().toPlainString();
    }
}

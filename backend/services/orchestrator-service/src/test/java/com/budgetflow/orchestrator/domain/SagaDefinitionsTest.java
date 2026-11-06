package com.budgetflow.orchestrator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SagaDefinitionsTest {

    static final UUID TXN = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID ACCOUNT = UUID.randomUUID();
    static final UUID TARGET = UUID.randomUUID();
    static final UUID CATEGORY = UUID.randomUUID();
    static final UUID PARENT = UUID.randomUUID();

    static TransactionSagaData data(String kind) {
        return new TransactionSagaData(TXN, USER, kind, ACCOUNT, kind.equals("TRANSFER") ? TARGET : null, "850.0000",
                "INR", kind.equals("TRANSFER") ? null : CATEGORY, kind.equals("EXPENSE") ? PARENT : null, "2026-11-06");
    }

    @Test
    void choosesSagaTypeByTransactionKind() {
        assertThat(SagaType.forTransactionKind("EXPENSE")).isEqualTo(SagaType.RECORD_TRANSACTION);
        assertThat(SagaType.forTransactionKind("INCOME")).isEqualTo(SagaType.RECORD_TRANSACTION);
        assertThat(SagaType.forTransactionKind("TRANSFER")).isEqualTo(SagaType.TRANSFER_BETWEEN_ACCOUNTS);
        assertThatThrownBy(() -> SagaType.forTransactionKind("GIFT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expenseDebitsAccountConsumesBudgetThenCompletes() {
        List<SagaStep> steps = SagaDefinitions.steps(SagaType.RECORD_TRANSACTION, data("EXPENSE"));

        assertThat(steps).extracting(SagaStep::name).containsExactly("APPLY_BALANCE", "CONSUME_BUDGET", "COMPLETE_TRANSACTION");
        SagaStep apply = steps.get(0);
        assertThat(apply.forward().type()).isEqualTo("account.ApplyBalanceChange");
        assertThat(apply.forward().fields()).containsEntry("userId", USER).containsEntry("accountId", ACCOUNT)
                .containsEntry("amount", "-850.0000").containsEntry("currency", "INR")
                .containsEntry("entryKind", "TRANSACTION").containsEntry("operationKey", "txn:" + TXN + ":apply")
                .containsEntry("referenceId", TXN);
        assertThat(apply.compensation().type()).isEqualTo("account.ReverseBalanceChange");
        assertThat(apply.compensation().fields()).containsEntry("operationKey", "txn:" + TXN + ":apply");

        SagaStep budget = steps.get(1);
        assertThat(budget.forward().type()).isEqualTo("budget.ConsumeBudget");
        assertThat(budget.forward().fields()).containsEntry("transactionId", TXN).containsEntry("categoryId", CATEGORY)
                .containsEntry("parentCategoryId", PARENT).containsEntry("amount", "850.0000")
                .containsEntry("occurredOn", "2026-11-06").containsEntry("operationKey", "txn:" + TXN + ":budget");
        assertThat(budget.compensation().type()).isEqualTo("budget.ReleaseBudget");

        SagaStep complete = steps.get(2);
        assertThat(complete.forward().type()).isEqualTo("transaction.CompleteTransaction");
        assertThat(complete.forward().fields()).containsEntry("transactionId", TXN).containsEntry("userId", USER);
        assertThat(complete.compensation()).isNull();
        assertThat(complete.retriable()).isTrue();
        assertThat(apply.retriable()).isFalse();
    }

    @Test
    void incomeCreditsAccountAndSkipsBudgets() {
        List<SagaStep> steps = SagaDefinitions.steps(SagaType.RECORD_TRANSACTION, data("INCOME"));

        assertThat(steps).extracting(SagaStep::name).containsExactly("APPLY_BALANCE", "COMPLETE_TRANSACTION");
        assertThat(steps.get(0).forward().fields()).containsEntry("amount", "850.0000");
    }

    @Test
    void transferDebitsSourceThenCreditsTarget() {
        List<SagaStep> steps = SagaDefinitions.steps(SagaType.TRANSFER_BETWEEN_ACCOUNTS, data("TRANSFER"));

        assertThat(steps).extracting(SagaStep::name).containsExactly("DEBIT_SOURCE", "CREDIT_TARGET", "COMPLETE_TRANSACTION");
        assertThat(steps.get(0).forward().fields()).containsEntry("accountId", ACCOUNT).containsEntry("amount", "-850.0000")
                .containsEntry("entryKind", "TRANSFER").containsEntry("operationKey", "txn:" + TXN + ":debit");
        assertThat(steps.get(1).forward().fields()).containsEntry("accountId", TARGET).containsEntry("amount", "850.0000")
                .containsEntry("operationKey", "txn:" + TXN + ":credit");
        assertThat(steps.get(1).compensation().fields()).containsEntry("operationKey", "txn:" + TXN + ":credit");
    }

    @Test
    void cancelCommandCarriesFailure() {
        SagaCommand cancel = SagaDefinitions.cancel(data("EXPENSE"), "ACCOUNT_ARCHIVED", "Account is archived");

        assertThat(cancel.type()).isEqualTo("transaction.CancelTransaction");
        assertThat(cancel.fields()).containsEntry("transactionId", TXN).containsEntry("userId", USER)
                .containsEntry("failureCode", "ACCOUNT_ARCHIVED").containsEntry("failureReason", "Account is archived");
    }
}

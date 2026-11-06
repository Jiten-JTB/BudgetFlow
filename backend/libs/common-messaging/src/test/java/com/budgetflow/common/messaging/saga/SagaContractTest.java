package com.budgetflow.common.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SagaContractTest {

    @Test
    void derivesRoutingKeyFromCommandType() {
        assertThat(SagaContract.routingKeyFor("account.ApplyBalanceChange")).isEqualTo("account.apply-balance-change");
        assertThat(SagaContract.routingKeyFor("transaction.CompleteTransaction")).isEqualTo("transaction.complete-transaction");
        assertThat(SagaContract.routingKeyFor("budget.ReleaseBudget")).isEqualTo("budget.release-budget");
    }

    @Test
    void rejectsMalformedCommandTypes() {
        assertThatThrownBy(() -> SagaContract.routingKeyFor("ApplyBalanceChange")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SagaContract.routingKeyFor("account.")).isInstanceOf(IllegalArgumentException.class);
    }
}

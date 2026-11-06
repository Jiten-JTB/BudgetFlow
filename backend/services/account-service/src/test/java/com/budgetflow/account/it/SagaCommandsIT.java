package com.budgetflow.account.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.account.application.AccountService;
import com.budgetflow.account.application.OpenAccountCommand;
import com.budgetflow.account.domain.Account;
import com.budgetflow.common.test.SagaCommands;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** account-service as a saga participant, over real RabbitMQ. */
class SagaCommandsIT extends AccountIntegrationTest {

    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcClient jdbc;
    @Autowired AccountService accounts;

    private final UUID user = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void open() {
        account = accounts.open(user, new OpenAccountCommand("Saga " + UUID.randomUUID(), "BANK", "INR", "1000"));
    }

    @Test
    void appliesAndReversesBalanceChanges() {
        String txn = UUID.randomUUID().toString();
        String applied = apply(txn, "-250", "INR");
        assertThat(applied).contains("\"outcome\":\"SUCCESS\"", "\"commandType\":\"account.ApplyBalanceChange\"");
        assertThat(balance()).isEqualByComparingTo("750");

        String reversed = SagaCommands.sendAndAwaitReply(rabbit, jdbc, "account.ReverseBalanceChange",
                "account.reverse-balance-change", Map.of("userId", user, "operationKey", "txn:" + txn + ":apply"));
        assertThat(reversed).contains("\"outcome\":\"SUCCESS\"");
        assertThat(balance()).isEqualByComparingTo("1000");
    }

    @Test
    void repliesFailureWithBusinessCodeAndLeavesBalanceUntouched() {
        String reply = apply(UUID.randomUUID().toString(), "-250", "USD");

        assertThat(reply).contains("\"outcome\":\"FAILURE\"", "\"errorCode\":\"ACCOUNT_CURRENCY_MISMATCH\"");
        assertThat(balance()).isEqualByComparingTo("1000");
    }

    private String apply(String txn, String amount, String currency) {
        return SagaCommands.sendAndAwaitReply(rabbit, jdbc, "account.ApplyBalanceChange", "account.apply-balance-change",
                Map.of("userId", user, "accountId", account.id(), "amount", amount, "currency", currency,
                        "entryKind", "TRANSACTION", "operationKey", "txn:" + txn + ":apply", "referenceId", txn));
    }

    private BigDecimal balance() {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id").param("id", account.id())
                .query(BigDecimal.class).single();
    }
}

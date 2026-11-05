package com.budgetflow.account.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.account.application.AccountService;
import com.budgetflow.account.application.ApplyBalanceChangeCommand;
import com.budgetflow.account.application.BalanceService;
import com.budgetflow.account.application.OpenAccountCommand;
import com.budgetflow.account.application.Reversal;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.EntryKind;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The saga-facing ledger operations against a real database. */
class BalanceLedgerIT extends AccountIntegrationTest {

    @Autowired AccountService accounts;
    @Autowired BalanceService balances;
    @Autowired JdbcClient jdbc;

    private final UUID user = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void openAccount() {
        account = accounts.open(user, new OpenAccountCommand("Ledger " + UUID.randomUUID(), "BANK", "INR", "100"));
    }

    @Test
    void applyIsIdempotentPerOperationKey() {
        var first = balances.apply(change("txn:a:apply", "-30"));
        var replay = balances.apply(change("txn:a:apply", "-30"));

        assertThat(first.duplicate()).isFalse();
        assertThat(replay.duplicate()).isTrue();
        assertThat(replay.entry().id()).isEqualTo(first.entry().id());
        assertThat(balance()).isEqualByComparingTo("70");
    }

    @Test
    void reversalRestoresBalanceOnceAndIsIdempotent() {
        balances.apply(change("txn:b:apply", "-30"));

        assertThat(balances.reverse(user, "txn:b:apply").outcome()).isEqualTo(Reversal.Outcome.REVERSED);
        assertThat(balances.reverse(user, "txn:b:apply").outcome()).isEqualTo(Reversal.Outcome.ALREADY_REVERSED);
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void compensationBeforeOperationBlocksTheLateOperation() {
        assertThat(balances.reverse(user, "txn:c:apply").outcome()).isEqualTo(Reversal.Outcome.NOTHING_TO_REVERSE);

        assertThatThrownBy(() -> balances.apply(change("txn:c:apply", "-30")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(AccountErrors.OPERATION_CANCELLED);
        assertThat(balance()).isEqualByComparingTo("100");
    }

    @Test
    void rejectsOtherUsersAccountAndForeignCurrency() {
        assertThatThrownBy(() -> balances.apply(new ApplyBalanceChangeCommand(UUID.randomUUID(), account.id(),
                Money.of("-1", "INR"), EntryKind.TRANSACTION, "txn:d:apply", null, null)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(AccountErrors.ACCOUNT_NOT_FOUND);
        assertThatThrownBy(() -> balances.apply(new ApplyBalanceChangeCommand(user, account.id(),
                Money.of("-1", "USD"), EntryKind.TRANSACTION, "txn:e:apply", null, null)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(AccountErrors.ACCOUNT_CURRENCY_MISMATCH);
    }

    @Test
    void concurrentChangesNeverLoseUpdatesAndBalanceMatchesLedger() throws Exception {
        int workers = 24;
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            String key = "txn:conc-" + i + ":apply";
            String amount = i % 2 == 0 ? "-2.5" : "10";
            tasks.add(() -> balances.apply(change(key, amount)));
            tasks.add(() -> balances.apply(change(key, amount))); // duplicate delivery racing the original
        }
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Object> f : pool.invokeAll(tasks)) {
                f.get();
            }
        }

        // 100 opening + 12 * 10 - 12 * 2.5
        assertThat(balance()).isEqualByComparingTo("190");
        BigDecimal ledgerSum = jdbc.sql("SELECT sum(amount) FROM balance_entry WHERE account_id = :id")
                .param("id", account.id()).query(BigDecimal.class).single();
        assertThat(ledgerSum).isEqualByComparingTo(balance());
        assertThat(jdbc.sql("SELECT count(*) FROM balance_entry WHERE account_id = :id AND kind = 'TRANSACTION'")
                .param("id", account.id()).query(Long.class).single()).isEqualTo(workers);
    }

    @Test
    void balanceChangesArePublishedThroughOutbox() {
        balances.apply(change("txn:f:apply", "-5"));

        assertThat(jdbc.sql("""
                SELECT count(*) FROM outbox_message
                WHERE message_type = 'account.BalanceChanged' AND aggregate_id = :id""")
                .param("id", account.id().toString()).query(Long.class).single()).isEqualTo(2); // opening + change
    }

    private ApplyBalanceChangeCommand change(String key, String amount) {
        return new ApplyBalanceChangeCommand(user, account.id(), Money.of(amount, "INR"), EntryKind.TRANSACTION,
                key, UUID.randomUUID(), "test");
    }

    private BigDecimal balance() {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id").param("id", account.id())
                .query(BigDecimal.class).single();
    }
}

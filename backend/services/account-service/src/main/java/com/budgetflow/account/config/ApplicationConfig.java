package com.budgetflow.account.config;

import com.budgetflow.account.adapter.in.messaging.SagaCommandListener;
import com.budgetflow.account.adapter.in.messaging.UserRegisteredListener;
import com.budgetflow.account.adapter.out.messaging.OutboxAccountEventPublisher;
import com.budgetflow.account.adapter.out.persistence.JdbcAccountRepository;
import com.budgetflow.account.adapter.out.persistence.JdbcBalanceEntryRepository;
import com.budgetflow.account.adapter.out.persistence.JdbcOperationRegistry;
import com.budgetflow.account.application.AccountService;
import com.budgetflow.account.application.BalanceService;
import com.budgetflow.account.application.port.AccountEventPublisher;
import com.budgetflow.account.application.port.AccountRepository;
import com.budgetflow.account.application.port.BalanceEntryRepository;
import com.budgetflow.common.messaging.config.Exchanges;
import com.budgetflow.common.messaging.consumer.QueueTopology;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import com.budgetflow.common.messaging.outbox.OutboxWriter;
import com.budgetflow.common.messaging.saga.SagaParticipantFactory;
import java.time.Clock;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AccountRepository accountRepository(JdbcClient jdbc) {
        return new JdbcAccountRepository(jdbc);
    }

    @Bean
    BalanceEntryRepository balanceEntryRepository(JdbcClient jdbc) {
        return new JdbcBalanceEntryRepository(jdbc);
    }

    @Bean
    AccountEventPublisher accountEventPublisher(OutboxWriter outbox) {
        return new OutboxAccountEventPublisher(outbox);
    }

    @Bean
    AccountService accountService(AccountRepository accounts, BalanceEntryRepository entries,
                                  AccountEventPublisher events, Clock clock) {
        return new AccountService(accounts, entries, events, clock);
    }

    @Bean
    BalanceService balanceService(AccountRepository accounts, BalanceEntryRepository entries, JdbcClient jdbc,
                                  AccountEventPublisher events, Clock clock) {
        return new BalanceService(accounts, entries, new JdbcOperationRegistry(jdbc), events, clock);
    }

    @Bean
    Declarables sagaCommandTopology() {
        return QueueTopology.subscribe(SagaCommandListener.QUEUE, Exchanges.COMMANDS, SagaCommandListener.ROUTING_PATTERN);
    }

    @Bean
    SagaCommandListener sagaCommandListener(SagaParticipantFactory participants, BalanceService balances) {
        return new SagaCommandListener(participants, balances);
    }

    @Bean
    Declarables userRegisteredTopology() {
        return QueueTopology.subscribe(UserRegisteredListener.QUEUE, Exchanges.EVENTS, "identity.user-registered");
    }

    @Bean
    UserRegisteredListener userRegisteredListener(IdempotentConsumer inbox, AccountService accounts, JsonMapper json) {
        return new UserRegisteredListener(inbox, accounts, json);
    }
}

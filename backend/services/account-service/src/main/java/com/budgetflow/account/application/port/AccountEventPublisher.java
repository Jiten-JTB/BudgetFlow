package com.budgetflow.account.application.port;

import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.BalanceEntry;

/** Integration events; implementations must enlist in the current transaction (outbox). */
public interface AccountEventPublisher {

    void accountOpened(Account account);

    void balanceChanged(Account account, BalanceEntry entry);
}

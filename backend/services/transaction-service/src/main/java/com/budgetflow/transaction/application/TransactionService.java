package com.budgetflow.transaction.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.common.core.money.Money;
import com.budgetflow.transaction.application.port.CategoryRepository;
import com.budgetflow.transaction.application.port.IncomeSourceRepository;
import com.budgetflow.transaction.application.port.TransactionEventPublisher;
import com.budgetflow.transaction.application.port.TransactionRepository;
import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.CategoryKind;
import com.budgetflow.transaction.domain.IncomeSource;
import com.budgetflow.transaction.domain.Transaction;
import com.budgetflow.transaction.domain.TransactionErrors;
import com.budgetflow.transaction.domain.TransactionKind;
import com.budgetflow.transaction.domain.TransactionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionService {

    public static final int MAX_PAGE_SIZE = 200;

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final IncomeSourceRepository incomeSources;
    private final TransactionEventPublisher events;
    private final Clock clock;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories,
                              IncomeSourceRepository incomeSources, TransactionEventPublisher events, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.incomeSources = incomeSources;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records a PENDING transaction and requests the saga that applies it. Safe to retry with the same
     * Idempotency-Key; reusing a key for a different request is rejected.
     */
    @Transactional
    public RecordResult record(UUID userId, RecordTransactionCommand command, String idempotencyKey) {
        String requestHash = sha256(command.toString());
        Optional<Transaction> existing = transactions.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        TransactionKind kind = Inputs.enumValue(TransactionKind.class, command.kind(), TransactionErrors.INVALID_KIND);
        Money amount = Inputs.money(command.amount(), Inputs.currency(command.currency()));
        LocalDate occurredOn = Inputs.date(command.occurredOn());
        if (occurredOn == null || command.accountId() == null) {
            throw new DomainException(TransactionErrors.INVALID_TRANSACTION, "accountId and occurredOn are required");
        }
        Instant now = clock.instant();
        Transaction transaction = Transaction.request(UuidV7.generate(), userId, kind, command.accountId(),
                command.counterAccountId(), amount, command.categoryId(), command.incomeSourceId(), occurredOn,
                command.description(), command.payee(), idempotencyKey, requestHash,
                LocalDate.ofInstant(now, ZoneOffset.UTC), now);
        UUID parentCategoryId = validateReferences(userId, transaction);

        if (!transactions.insert(transaction)) {
            // Lost a race with a concurrent request carrying the same key.
            return replay(transactions.findByIdempotencyKey(userId, idempotencyKey).orElseThrow(), requestHash);
        }
        events.requested(transaction, parentCategoryId);
        return new RecordResult(transaction, false);
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID userId, UUID transactionId) {
        return transactions.findOwned(userId, transactionId).orElseThrow(TransactionService::notFound);
    }

    @Transactional(readOnly = true)
    public TransactionPage search(UUID userId, TransactionQuery query, String cursor, int limit) {
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        TransactionCursor after = cursor == null ? null : TransactionCursor.decode(cursor);
        List<Transaction> items = transactions.search(userId, query, after, size);
        String next = items.size() == size
                ? new TransactionCursor(items.getLast().occurredOn(), items.getLast().id()).encode()
                : null;
        return new TransactionPage(items, next);
    }

    /** Saga step: the money has moved. Idempotent. */
    @Transactional
    public Transaction complete(UUID userId, UUID transactionId, UUID sagaId) {
        Transaction transaction = get(userId, transactionId);
        if (transaction.complete(sagaId, clock.instant())) {
            transactions.update(transaction);
            events.completed(transaction);
        }
        return transaction;
    }

    /** Saga compensation: the transaction could not be applied. Idempotent. */
    @Transactional
    public Transaction cancel(UUID userId, UUID transactionId, String failureCode, String failureReason) {
        Transaction transaction = get(userId, transactionId);
        if (transaction.cancel(failureCode, failureReason, clock.instant())) {
            transactions.update(transaction);
            events.cancelled(transaction);
        }
        return transaction;
    }

    /** @return the parent of the transaction's category, or null */
    private UUID validateReferences(UUID userId, Transaction transaction) {
        UUID parentCategoryId = null;
        if (transaction.categoryId() != null) {
            Category category = categories.findOwned(userId, transaction.categoryId())
                    .orElseThrow(() -> new DomainException(TransactionErrors.CATEGORY_NOT_FOUND, "Category not found"));
            if (category.isArchived()) {
                throw new DomainException(TransactionErrors.CATEGORY_ARCHIVED, "Category is archived");
            }
            CategoryKind expected = transaction.kind() == TransactionKind.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
            if (category.kind() != expected) {
                throw new DomainException(TransactionErrors.CATEGORY_KIND_MISMATCH,
                        transaction.kind() + " needs an " + expected + " category");
            }
            parentCategoryId = category.parentId();
        }
        if (transaction.incomeSourceId() != null) {
            IncomeSource source = incomeSources.findOwned(userId, transaction.incomeSourceId())
                    .orElseThrow(() -> new DomainException(TransactionErrors.INCOME_SOURCE_NOT_FOUND, "Income source not found"));
            if (source.isArchived()) {
                throw new DomainException(TransactionErrors.INCOME_SOURCE_ARCHIVED, "Income source is archived");
            }
        }
        return parentCategoryId;
    }

    private static RecordResult replay(Transaction existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new DomainException(TransactionErrors.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used for a different request");
        }
        return new RecordResult(existing, true);
    }

    static DomainException notFound() {
        return new DomainException(TransactionErrors.TRANSACTION_NOT_FOUND, "Transaction not found");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses list filters with precise error codes. */
    public static TransactionQuery query(String from, String to, String kind, String status, UUID accountId,
                                         UUID categoryId) {
        return new TransactionQuery(Inputs.date(from), Inputs.date(to),
                kind == null ? null : Inputs.enumValue(TransactionKind.class, kind, TransactionErrors.INVALID_KIND),
                status == null ? null : Inputs.enumValue(TransactionStatus.class, status, TransactionErrors.INVALID_STATUS),
                accountId, categoryId);
    }
}

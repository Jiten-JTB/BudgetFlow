package com.budgetflow.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.transaction.application.port.CategoryRepository;
import com.budgetflow.transaction.application.port.IncomeSourceRepository;
import com.budgetflow.transaction.application.port.TransactionEventPublisher;
import com.budgetflow.transaction.application.port.TransactionRepository;
import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.CategoryKind;
import com.budgetflow.transaction.domain.IncomeSource;
import com.budgetflow.transaction.domain.IncomeSourceType;
import com.budgetflow.transaction.domain.Transaction;
import com.budgetflow.transaction.domain.TransactionErrors;
import com.budgetflow.transaction.domain.TransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final IncomeSourceRepository sources = mock(IncomeSourceRepository.class);
    private final TransactionEventPublisher events = mock(TransactionEventPublisher.class);
    private TransactionService service;

    private final Category food = Category.create(UUID.randomUUID(), USER, "Food", CategoryKind.EXPENSE, null, null, null, NOW);
    private final Category salaryCat = Category.create(UUID.randomUUID(), USER, "Salary", CategoryKind.INCOME, null, null, null, NOW);
    private final IncomeSource employer = IncomeSource.create(UUID.randomUUID(), USER, "Acme", IncomeSourceType.SALARY, null, null, NOW);

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactions, categories, sources, events, Clock.fixed(NOW, ZoneOffset.UTC));
        when(categories.findOwned(USER, food.id())).thenReturn(Optional.of(food));
        when(categories.findOwned(USER, salaryCat.id())).thenReturn(Optional.of(salaryCat));
        when(sources.findOwned(USER, employer.id())).thenReturn(Optional.of(employer));
        when(transactions.insert(any())).thenReturn(true);
    }

    @Test
    void recordsPendingTransactionAndRequestsSaga() {
        RecordResult result = service.record(USER, expense(food.id(), "250"), "key-00000001");

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.transaction().id().version()).isEqualTo(7);
        verify(transactions).insert(result.transaction());
        verify(events).requested(result.transaction(), null);
    }

    @Test
    void recordsIncomeWithIncomeSourceAndIncomeCategory() {
        RecordResult result = service.record(USER, new RecordTransactionCommand("INCOME", ACCOUNT, null, "50000",
                "INR", salaryCat.id(), employer.id(), "2026-11-01", "November salary", null), "key-00000002");

        assertThat(result.transaction().incomeSourceId()).isEqualTo(employer.id());
    }

    @Test
    void rejectsCategoryOfTheWrongKindArchivedOrForeign() {
        assertError(() -> service.record(USER, expense(salaryCat.id(), "1"), "key-00000003"), TransactionErrors.CATEGORY_KIND_MISMATCH);
        food.archive(NOW);
        assertError(() -> service.record(USER, expense(food.id(), "1"), "key-00000004"), TransactionErrors.CATEGORY_ARCHIVED);
        assertError(() -> service.record(USER, expense(UUID.randomUUID(), "1"), "key-00000005"), TransactionErrors.CATEGORY_NOT_FOUND);
        verify(events, never()).requested(any(), any());
    }

    @Test
    void sagaRequestCarriesParentOfSubCategory() {
        Category dining = Category.create(UUID.randomUUID(), USER, "Dining", CategoryKind.EXPENSE, food.id(), null, null, NOW);
        when(categories.findOwned(USER, dining.id())).thenReturn(Optional.of(dining));

        RecordResult result = service.record(USER, expense(dining.id(), "99"), "key-00000011");

        verify(events).requested(result.transaction(), food.id());
    }

    @Test
    void rejectsUnknownIncomeSource() {
        assertError(() -> service.record(USER, new RecordTransactionCommand("INCOME", ACCOUNT, null, "1", "INR", null,
                UUID.randomUUID(), "2026-11-01", null, null), "key-00000006"), TransactionErrors.INCOME_SOURCE_NOT_FOUND);
    }

    @Test
    void replaysSameRequestWithSameIdempotencyKey() {
        Transaction original = service.record(USER, expense(food.id(), "250"), "key-00000007").transaction();
        when(transactions.findByIdempotencyKey(USER, "key-00000007")).thenReturn(Optional.of(original));

        RecordResult replay = service.record(USER, expense(food.id(), "250"), "key-00000007");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.transaction()).isSameAs(original);
    }

    @Test
    void rejectsReuseOfIdempotencyKeyForDifferentRequest() {
        Transaction original = service.record(USER, expense(food.id(), "250"), "key-00000008").transaction();
        when(transactions.findByIdempotencyKey(USER, "key-00000008")).thenReturn(Optional.of(original));

        assertError(() -> service.record(USER, expense(food.id(), "999"), "key-00000008"), TransactionErrors.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void concurrentDuplicateInsertReplaysTheWinner() {
        Transaction winner = service.record(USER, expense(food.id(), "250"), "key-00000009").transaction();
        when(transactions.insert(any())).thenReturn(false);
        when(transactions.findByIdempotencyKey(USER, "key-00000009"))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(winner));

        RecordResult result = service.record(USER, expense(food.id(), "250"), "key-00000009");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isSameAs(winner);
    }

    @Test
    void completesAndCancelsPublishingEventsOnlyOnChange() {
        Transaction t = service.record(USER, expense(food.id(), "10"), "key-00000010").transaction();
        when(transactions.findOwned(USER, t.id())).thenReturn(Optional.of(t));
        UUID saga = UUID.randomUUID();

        service.complete(USER, t.id(), saga);
        service.complete(USER, t.id(), saga);

        verify(events, org.mockito.Mockito.times(1)).completed(t);
        verify(transactions, org.mockito.Mockito.times(1)).update(t);
    }

    private static RecordTransactionCommand expense(UUID categoryId, String amount) {
        return new RecordTransactionCommand("EXPENSE", ACCOUNT, null, amount, "INR", categoryId, null, "2026-11-03",
                "Groceries", "BigBasket");
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}

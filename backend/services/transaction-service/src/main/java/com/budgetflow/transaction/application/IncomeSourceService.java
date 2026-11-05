package com.budgetflow.transaction.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.transaction.application.port.IncomeSourceRepository;
import com.budgetflow.transaction.domain.IncomeSource;
import com.budgetflow.transaction.domain.IncomeSourceType;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class IncomeSourceService {

    private final IncomeSourceRepository sources;
    private final Clock clock;

    public IncomeSourceService(IncomeSourceRepository sources, Clock clock) {
        this.sources = sources;
        this.clock = clock;
    }

    /** For updates, {@code null} fields are left unchanged. */
    public record IncomeSourceCommand(String name, String type, UUID defaultAccountId, String notes) {}

    @Transactional
    public IncomeSource create(UUID userId, IncomeSourceCommand command) {
        IncomeSource source = IncomeSource.create(UuidV7.generate(), userId, command.name(), type(command.type()),
                command.defaultAccountId(), command.notes(), clock.instant());
        sources.insert(source);
        return source;
    }

    @Transactional(readOnly = true)
    public IncomeSource get(UUID userId, UUID sourceId) {
        return sources.findOwned(userId, sourceId)
                .orElseThrow(() -> new DomainException(TransactionErrors.INCOME_SOURCE_NOT_FOUND, "Income source not found"));
    }

    @Transactional(readOnly = true)
    public List<IncomeSource> list(UUID userId, boolean includeArchived) {
        return sources.list(userId, includeArchived);
    }

    @Transactional
    public IncomeSource update(UUID userId, UUID sourceId, IncomeSourceCommand command) {
        IncomeSource source = get(userId, sourceId);
        source.update(command.name(), command.type() == null ? null : type(command.type()), command.defaultAccountId(),
                command.notes(), clock.instant());
        sources.update(source);
        return source;
    }

    @Transactional
    public IncomeSource archive(UUID userId, UUID sourceId) {
        IncomeSource source = get(userId, sourceId);
        source.archive(clock.instant());
        sources.update(source);
        return source;
    }

    private static IncomeSourceType type(String raw) {
        return Inputs.enumValue(IncomeSourceType.class, raw, TransactionErrors.INVALID_TYPE);
    }
}

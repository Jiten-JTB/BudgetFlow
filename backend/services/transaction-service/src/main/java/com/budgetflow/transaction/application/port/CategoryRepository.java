package com.budgetflow.transaction.application.port;

import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.CategoryKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    /** @throws com.budgetflow.common.core.error.DomainException DUPLICATE_NAME */
    void insert(Category category);

    void update(Category category);

    Optional<Category> findOwned(UUID userId, UUID categoryId);

    /** @param kind null for all kinds */
    List<Category> list(UUID userId, CategoryKind kind, boolean includeArchived);

    boolean existsForUser(UUID userId);
}

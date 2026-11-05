package com.budgetflow.transaction.application;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.id.UuidV7;
import com.budgetflow.transaction.application.port.CategoryRepository;
import com.budgetflow.transaction.domain.Category;
import com.budgetflow.transaction.domain.CategoryKind;
import com.budgetflow.transaction.domain.TransactionErrors;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class CategoryService {

    /** name, icon, colour. Seeded for every new user; they can rename or archive them. */
    static final List<String[]> DEFAULT_INCOME = List.of(
            new String[] {"Salary", "briefcase", "#2E7D32"}, new String[] {"Business", "store", "#1565C0"},
            new String[] {"Freelance", "laptop", "#6A1B9A"}, new String[] {"Investments", "trending-up", "#00838F"},
            new String[] {"Gifts", "gift", "#AD1457"}, new String[] {"Other Income", "plus-circle", "#546E7A"});
    static final List<String[]> DEFAULT_EXPENSE = List.of(
            new String[] {"Food & Dining", "utensils", "#E65100"}, new String[] {"Groceries", "shopping-cart", "#558B2F"},
            new String[] {"Transport", "car", "#1565C0"}, new String[] {"Housing", "home", "#4E342E"},
            new String[] {"Utilities", "zap", "#F9A825"}, new String[] {"Health", "heart-pulse", "#C62828"},
            new String[] {"Shopping", "shopping-bag", "#AD1457"}, new String[] {"Entertainment", "film", "#6A1B9A"},
            new String[] {"Education", "book-open", "#283593"}, new String[] {"Travel", "plane", "#00838F"},
            new String[] {"Bills & EMIs", "receipt", "#37474F"}, new String[] {"Other Expenses", "more-horizontal", "#546E7A"});

    private final CategoryRepository categories;
    private final Clock clock;

    public CategoryService(CategoryRepository categories, Clock clock) {
        this.categories = categories;
        this.clock = clock;
    }

    public record CreateCategoryCommand(String name, String kind, UUID parentId, String icon, String color) {}

    public record UpdateCategoryCommand(String name, String icon, String color) {}

    @Transactional
    public Category create(UUID userId, CreateCategoryCommand command) {
        CategoryKind kind = Inputs.enumValue(CategoryKind.class, command.kind(), TransactionErrors.INVALID_KIND);
        if (command.parentId() != null) {
            Category parent = get(userId, command.parentId());
            if (!parent.isTopLevel() || parent.kind() != kind || parent.isArchived()) {
                throw new DomainException(TransactionErrors.INVALID_PARENT_CATEGORY,
                        "Parent must be an active top-level " + kind + " category");
            }
        }
        Category category = Category.create(UuidV7.generate(), userId, command.name(), kind, command.parentId(),
                command.icon(), command.color(), clock.instant());
        categories.insert(category);
        return category;
    }

    @Transactional(readOnly = true)
    public Category get(UUID userId, UUID categoryId) {
        return categories.findOwned(userId, categoryId)
                .orElseThrow(() -> new DomainException(TransactionErrors.CATEGORY_NOT_FOUND, "Category not found"));
    }

    @Transactional(readOnly = true)
    public List<Category> list(UUID userId, String kind, boolean includeArchived) {
        CategoryKind filter = kind == null ? null : Inputs.enumValue(CategoryKind.class, kind, TransactionErrors.INVALID_KIND);
        return categories.list(userId, filter, includeArchived);
    }

    @Transactional
    public Category update(UUID userId, UUID categoryId, UpdateCategoryCommand command) {
        Category category = get(userId, categoryId);
        category.update(command.name(), command.icon(), command.color(), clock.instant());
        categories.update(category);
        return category;
    }

    @Transactional
    public Category archive(UUID userId, UUID categoryId) {
        Category category = get(userId, categoryId);
        category.archive(clock.instant());
        categories.update(category);
        return category;
    }

    /** Idempotent: does nothing if the user already has categories. */
    @Transactional
    public void seedDefaults(UUID userId) {
        if (categories.existsForUser(userId)) {
            return;
        }
        Instant now = clock.instant();
        DEFAULT_INCOME.forEach(d -> categories.insert(
                Category.create(UuidV7.generate(), userId, d[0], CategoryKind.INCOME, null, d[1], d[2], now)));
        DEFAULT_EXPENSE.forEach(d -> categories.insert(
                Category.create(UuidV7.generate(), userId, d[0], CategoryKind.EXPENSE, null, d[1], d[2], now)));
    }
}

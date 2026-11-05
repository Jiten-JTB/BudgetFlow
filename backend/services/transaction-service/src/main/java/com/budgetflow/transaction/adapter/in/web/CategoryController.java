package com.budgetflow.transaction.adapter.in.web;

import com.budgetflow.common.security.CurrentUserId;
import com.budgetflow.transaction.application.CategoryService;
import com.budgetflow.transaction.application.CategoryService.CreateCategoryCommand;
import com.budgetflow.transaction.application.CategoryService.UpdateCategoryCommand;
import com.budgetflow.transaction.domain.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

    private final CategoryService categories;

    CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreateRequest r) {
        return CategoryResponse.of(categories.create(userId,
                new CreateCategoryCommand(r.name(), r.kind(), r.parentId(), r.icon(), r.color())));
    }

    @GetMapping
    List<CategoryResponse> list(@CurrentUserId UUID userId, @RequestParam(required = false) String kind,
                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.list(userId, kind, includeArchived).stream().map(CategoryResponse::of).toList();
    }

    @PatchMapping("/{id}")
    CategoryResponse update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        return CategoryResponse.of(categories.update(userId, id, new UpdateCategoryCommand(r.name(), r.icon(), r.color())));
    }

    @PostMapping("/{id}/archive")
    CategoryResponse archive(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return CategoryResponse.of(categories.archive(userId, id));
    }

    record CreateRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 20) String kind, UUID parentId,
                         @Size(max = 80) String icon, @Size(max = 20) String color) {}

    record UpdateRequest(@Size(max = 120) String name, @Size(max = 80) String icon, @Size(max = 20) String color) {}

    record CategoryResponse(UUID id, String name, String kind, UUID parentId, String icon, String color,
                            boolean archived, Instant createdAt) {
        static CategoryResponse of(Category c) {
            return new CategoryResponse(c.id(), c.name(), c.kind().name(), c.parentId(), c.icon(), c.color(),
                    c.isArchived(), c.createdAt());
        }
    }
}

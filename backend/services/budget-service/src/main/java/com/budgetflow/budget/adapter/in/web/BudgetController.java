package com.budgetflow.budget.adapter.in.web;

import com.budgetflow.budget.application.BudgetService;
import com.budgetflow.budget.application.CreateBudgetCommand;
import com.budgetflow.budget.application.UpdateBudgetCommand;
import com.budgetflow.budget.domain.Budget;
import com.budgetflow.budget.domain.BudgetStatus;
import com.budgetflow.common.security.CurrentUserId;
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
@RequestMapping("/api/v1/budgets")
class BudgetController {

    private final BudgetService budgets;

    BudgetController(BudgetService budgets) {
        this.budgets = budgets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BudgetResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreateRequest r) {
        return BudgetResponse.of(budgets.create(userId, new CreateBudgetCommand(r.name(), r.categoryId(), r.period(),
                r.limit(), r.currency(), r.alertThresholdPercent(), r.startsOn())));
    }

    @GetMapping
    List<BudgetResponse> list(@CurrentUserId UUID userId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        return budgets.list(userId, includeArchived).stream().map(BudgetResponse::of).toList();
    }

    /** Every active budget with its usage in the period containing {@code date} (default today). */
    @GetMapping("/status")
    List<StatusResponse> statuses(@CurrentUserId UUID userId, @RequestParam(required = false) String date) {
        return budgets.statuses(userId, date).stream().map(StatusResponse::of).toList();
    }

    @GetMapping("/{id}")
    BudgetResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return BudgetResponse.of(budgets.get(userId, id));
    }

    @GetMapping("/{id}/status")
    StatusResponse status(@CurrentUserId UUID userId, @PathVariable UUID id, @RequestParam(required = false) String date) {
        return StatusResponse.of(budgets.status(userId, id, date));
    }

    @PatchMapping("/{id}")
    BudgetResponse update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        return BudgetResponse.of(budgets.update(userId, id, new UpdateBudgetCommand(r.name(), r.limit(), r.alertThresholdPercent())));
    }

    @PostMapping("/{id}/archive")
    BudgetResponse archive(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return BudgetResponse.of(budgets.archive(userId, id));
    }

    record CreateRequest(@NotBlank @Size(max = 200) String name, UUID categoryId, @NotBlank @Size(max = 20) String period,
                         @NotBlank @Size(max = 40) String limit, @NotBlank @Size(max = 16) String currency,
                         Integer alertThresholdPercent, @Size(max = 20) String startsOn) {}

    record UpdateRequest(@Size(max = 200) String name, @Size(max = 40) String limit, Integer alertThresholdPercent) {}

    record BudgetResponse(UUID id, String name, UUID categoryId, String period, String limit, String currency,
                          int alertThresholdPercent, String startsOn, boolean archived, Instant createdAt) {
        static BudgetResponse of(Budget b) {
            return new BudgetResponse(b.id(), b.name(), b.categoryId(), b.period().name(), b.limit().amount().toPlainString(),
                    b.currency().getCurrencyCode(), b.alertThresholdPercent(), b.startsOn().toString(), b.isArchived(),
                    b.createdAt());
        }
    }

    record StatusResponse(BudgetResponse budget, String periodStart, String periodEnd, String spent, String remaining,
                          int percentUsed, String state) {
        static StatusResponse of(BudgetStatus s) {
            return new StatusResponse(BudgetResponse.of(s.budget()), s.period().start().toString(),
                    s.period().end().toString(), s.spent().amount().toPlainString(),
                    s.remaining().amount().toPlainString(), s.percentUsed(), s.state().name());
        }
    }
}

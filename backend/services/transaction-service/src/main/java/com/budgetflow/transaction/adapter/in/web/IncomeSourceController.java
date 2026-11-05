package com.budgetflow.transaction.adapter.in.web;

import com.budgetflow.common.security.CurrentUserId;
import com.budgetflow.transaction.application.IncomeSourceService;
import com.budgetflow.transaction.application.IncomeSourceService.IncomeSourceCommand;
import com.budgetflow.transaction.domain.IncomeSource;
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
@RequestMapping("/api/v1/income-sources")
class IncomeSourceController {

    private final IncomeSourceService sources;

    IncomeSourceController(IncomeSourceService sources) {
        this.sources = sources;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IncomeSourceResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreateRequest r) {
        return IncomeSourceResponse.of(sources.create(userId,
                new IncomeSourceCommand(r.name(), r.type(), r.defaultAccountId(), r.notes())));
    }

    @GetMapping
    List<IncomeSourceResponse> list(@CurrentUserId UUID userId,
                                    @RequestParam(defaultValue = "false") boolean includeArchived) {
        return sources.list(userId, includeArchived).stream().map(IncomeSourceResponse::of).toList();
    }

    @PatchMapping("/{id}")
    IncomeSourceResponse update(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        return IncomeSourceResponse.of(sources.update(userId, id,
                new IncomeSourceCommand(r.name(), r.type(), r.defaultAccountId(), r.notes())));
    }

    @PostMapping("/{id}/archive")
    IncomeSourceResponse archive(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return IncomeSourceResponse.of(sources.archive(userId, id));
    }

    record CreateRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 20) String type,
                         UUID defaultAccountId, @Size(max = 1000) String notes) {}

    record UpdateRequest(@Size(max = 200) String name, @Size(max = 20) String type, UUID defaultAccountId,
                         @Size(max = 1000) String notes) {}

    record IncomeSourceResponse(UUID id, String name, String type, UUID defaultAccountId, String notes,
                                boolean archived, Instant createdAt) {
        static IncomeSourceResponse of(IncomeSource s) {
            return new IncomeSourceResponse(s.id(), s.name(), s.type().name(), s.defaultAccountId(), s.notes(),
                    s.isArchived(), s.createdAt());
        }
    }
}

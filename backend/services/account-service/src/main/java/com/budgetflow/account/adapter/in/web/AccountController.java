package com.budgetflow.account.adapter.in.web;

import com.budgetflow.account.application.AccountService;
import com.budgetflow.account.application.BalanceChange;
import com.budgetflow.account.application.BalanceService;
import com.budgetflow.account.application.EntryPage;
import com.budgetflow.account.application.OpenAccountCommand;
import com.budgetflow.account.domain.Account;
import com.budgetflow.account.domain.AccountErrors;
import com.budgetflow.account.domain.BalanceEntry;
import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final AccountService accounts;
    private final BalanceService balances;

    AccountController(AccountService accounts, BalanceService balances) {
        this.accounts = accounts;
        this.balances = balances;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@CurrentUserId UUID userId, @Valid @RequestBody OpenAccountRequest request) {
        Account account = accounts.open(userId, new OpenAccountCommand(
                request.name(), request.type(), request.currency(), request.openingBalance()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.of(account));
    }

    @GetMapping
    List<AccountResponse> list(@CurrentUserId UUID userId,
                               @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accounts.list(userId, includeArchived).stream().map(AccountResponse::of).toList();
    }

    @GetMapping("/{id}")
    AccountResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return AccountResponse.of(accounts.get(userId, id));
    }

    @PatchMapping("/{id}")
    AccountResponse rename(@CurrentUserId UUID userId, @PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return AccountResponse.of(accounts.rename(userId, id, request.name()));
    }

    @PostMapping("/{id}/archive")
    AccountResponse archive(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return AccountResponse.of(accounts.archive(userId, id));
    }

    @GetMapping("/{id}/entries")
    EntryPageResponse entries(@CurrentUserId UUID userId, @PathVariable UUID id,
                              @RequestParam(required = false) UUID before,
                              @RequestParam(defaultValue = "50") int limit) {
        EntryPage page = accounts.entries(userId, id, before, limit);
        return new EntryPageResponse(page.items().stream().map(EntryResponse::of).toList(), page.nextCursor());
    }

    /** 201 when applied, 200 when the same Idempotency-Key was already applied. */
    @PostMapping("/{id}/adjustments")
    ResponseEntity<EntryResponse> adjust(@CurrentUserId UUID userId, @PathVariable UUID id,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                         @Valid @RequestBody AdjustmentRequest request) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new DomainException(AccountErrors.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header (8-100 chars of A-Z a-z 0-9 _ -) is required");
        }
        BalanceChange change = balances.adjust(userId, id, request.amount(), key, request.description());
        return ResponseEntity.status(change.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(EntryResponse.of(change.entry()));
    }

    record OpenAccountRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 40) String type,
                              @NotBlank @Size(max = 16) String currency, @Size(max = 40) String openingBalance) {}

    record RenameRequest(@NotBlank @Size(max = 200) String name) {}

    record AdjustmentRequest(@NotBlank @Size(max = 40) String amount, @Size(max = 200) String description) {}

    record AccountResponse(UUID id, String name, String type, String currency, String balance, String status,
                           Instant createdAt, Instant updatedAt) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.id(), a.name(), a.type().name(), a.currency().getCurrencyCode(),
                    a.balance().amount().toPlainString(), a.status().name(), a.createdAt(), a.updatedAt());
        }
    }

    record EntryResponse(UUID id, String kind, String amount, String balanceAfter, UUID referenceId,
                         UUID reversesEntryId, String description, Instant createdAt) {
        static EntryResponse of(BalanceEntry e) {
            return new EntryResponse(e.id(), e.kind().name(), e.amount().amount().toPlainString(),
                    e.balanceAfter().amount().toPlainString(), e.referenceId(), e.reversesEntryId(), e.description(),
                    e.createdAt());
        }
    }

    record EntryPageResponse(List<EntryResponse> items, UUID nextCursor) {}
}

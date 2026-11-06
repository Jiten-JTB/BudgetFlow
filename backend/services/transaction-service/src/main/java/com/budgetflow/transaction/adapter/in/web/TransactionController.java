package com.budgetflow.transaction.adapter.in.web;

import com.budgetflow.common.security.CurrentUserId;
import com.budgetflow.transaction.application.RecordResult;
import com.budgetflow.transaction.application.RecordTransactionCommand;
import com.budgetflow.transaction.application.TransactionPage;
import com.budgetflow.transaction.application.TransactionService;
import com.budgetflow.transaction.domain.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/transactions")
class TransactionController {

    private final TransactionService transactions;

    TransactionController(TransactionService transactions) {
        this.transactions = transactions;
    }

    /**
     * 202 Accepted: stored as PENDING; the balance and budgets are updated asynchronously by the saga.
     * 200 OK: replay of a request already accepted with this Idempotency-Key.
     */
    @PostMapping
    ResponseEntity<TransactionResponse> record(@CurrentUserId UUID userId,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                               @Valid @RequestBody RecordRequest r) {
        RecordResult result = transactions.record(userId, new RecordTransactionCommand(r.kind(), r.accountId(),
                r.counterAccountId(), r.amount(), r.currency(), r.categoryId(), r.incomeSourceId(), r.occurredOn(),
                r.description(), r.payee()), IdempotencyKeys.require(key));
        Transaction t = result.transaction();
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(t.id()).toUri();
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .location(location).body(TransactionResponse.of(t));
    }

    @GetMapping
    PageResponse search(@CurrentUserId UUID userId,
                        @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                        @RequestParam(required = false) String kind, @RequestParam(required = false) String status,
                        @RequestParam(required = false) UUID accountId, @RequestParam(required = false) UUID categoryId,
                        @RequestParam(required = false) UUID incomeSourceId,
                        @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int limit) {
        TransactionPage page = transactions.search(userId,
                TransactionService.query(from, to, kind, status, accountId, categoryId, incomeSourceId), cursor, limit);
        return new PageResponse(page.items().stream().map(TransactionResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{id}")
    TransactionResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return TransactionResponse.of(transactions.get(userId, id));
    }

    // Coarse size guards; the domain enforces the real rules with precise error codes.
    record RecordRequest(
            @Size(max = 20) String kind, UUID accountId, UUID counterAccountId, @Size(max = 40) String amount,
            @Size(max = 16) String currency, UUID categoryId, UUID incomeSourceId, @Size(max = 20) String occurredOn,
            @Size(max = 400) String description, @Size(max = 200) String payee) {}

    record TransactionResponse(UUID id, String kind, String status, UUID accountId, UUID counterAccountId,
                               String amount, String currency, UUID categoryId, UUID incomeSourceId, String occurredOn,
                               String description, String payee, String failureCode, String failureReason,
                               Instant createdAt, Instant completedAt) {
        static TransactionResponse of(Transaction t) {
            return new TransactionResponse(t.id(), t.kind().name(), t.status().name(), t.accountId(),
                    t.counterAccountId(), t.amount().amount().toPlainString(), t.amount().currency().getCurrencyCode(),
                    t.categoryId(), t.incomeSourceId(), t.occurredOn().toString(), t.description(), t.payee(),
                    t.failureCode(), t.failureReason(), t.createdAt(), t.completedAt());
        }
    }

    record PageResponse(List<TransactionResponse> items, String nextCursor) {}
}

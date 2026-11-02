package com.budgetflow.common.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void carriesCodeCategoryAndDetails() {
        var ex = new DomainException(
                ErrorCode.of("ACCOUNT_NOT_FOUND", ErrorCategory.NOT_FOUND),
                "Account not found",
                Map.of("accountId", "42"));

        assertThat(ex.code().value()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(ex.code().category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Account not found");
        assertThat(ex.details()).containsEntry("accountId", "42");
    }

    @Test
    void detailsAreImmutableAndDefaultToEmpty() {
        var ex = new DomainException(ErrorCode.of("X", ErrorCategory.CONFLICT), "x");

        assertThat(ex.details()).isEmpty();
        assertThat(ex.details()).isUnmodifiable();
    }

    @Test
    void errorCodeMustBeUpperSnakeCase() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ErrorCode.of("bad-code", ErrorCategory.VALIDATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoriesMapToHttpStatus() {
        assertThat(ErrorCategory.VALIDATION.httpStatus()).isEqualTo(400);
        assertThat(ErrorCategory.UNAUTHORIZED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCategory.FORBIDDEN.httpStatus()).isEqualTo(403);
        assertThat(ErrorCategory.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCategory.CONFLICT.httpStatus()).isEqualTo(409);
        assertThat(ErrorCategory.BUSINESS_RULE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCategory.INTERNAL.httpStatus()).isEqualTo(500);
    }
}

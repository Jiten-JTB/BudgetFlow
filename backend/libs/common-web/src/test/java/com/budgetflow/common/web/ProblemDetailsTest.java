package com.budgetflow.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCategory;
import com.budgetflow.common.core.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProblemDetailsTest.TestController.class)
@ImportAutoConfiguration(ProblemDetailsAutoConfiguration.class)
class ProblemDetailsTest {

    @Autowired MockMvc mvc;

    @Test
    void mapsDomainExceptionToProblemWithCodeAndDetails() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Account not found"))
                .andExpect(jsonPath("$.details.accountId").value("42"));
    }

    @Test
    void mapsBusinessRuleToUnprocessableEntity() throws Exception {
        mvc.perform(get("/rule")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"));
    }

    @Test
    void reportsEveryInvalidFieldOnValidationFailure() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void reportsMalformedJson() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void mapsOptimisticLockToConflict() throws Exception {
        mvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void hidesInternalErrorDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @SpringBootConfiguration
    @Import(TestController.class)
    static class TestApp {}

    record Thing(@NotBlank String name) {}

    @RestController
    static class TestController {
        @GetMapping("/not-found")
        void notFound() {
            throw new DomainException(ErrorCode.of("ACCOUNT_NOT_FOUND", ErrorCategory.NOT_FOUND),
                    "Account not found", Map.of("accountId", "42"));
        }

        @GetMapping("/rule")
        void rule() {
            throw new DomainException(ErrorCode.of("BUDGET_EXCEEDED", ErrorCategory.BUSINESS_RULE), "Budget exceeded");
        }

        @PostMapping("/things")
        void create(@Valid @RequestBody Thing thing) {}

        @GetMapping("/stale")
        void stale() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}

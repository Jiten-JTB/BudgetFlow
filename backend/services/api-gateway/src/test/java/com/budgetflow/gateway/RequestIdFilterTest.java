package com.budgetflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestIdFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"web-1234abcd", "0198a3f2-7c1e-7d3a-9b2e-5f6a7b8c9d0e", "A.b_c-12"})
    void acceptsSafeIds(String id) {
        assertThat(RequestIdFilter.resolve(id)).isEqualTo(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "has space1", "new\nline12", "<script>x</script>"})
    void replacesMissingOrUnsafeIds(String id) {
        assertThat(RequestIdFilter.resolve(id)).matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesOverlongIds() {
        assertThat(RequestIdFilter.resolve("a".repeat(65))).hasSize(36);
    }
}

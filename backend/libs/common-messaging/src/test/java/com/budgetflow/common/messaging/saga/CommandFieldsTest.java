package com.budgetflow.common.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CommandFieldsTest {

    private final UUID id = UUID.randomUUID();
    private final JsonNode node = JsonMapper.builder().build()
            .readTree("{\"id\":\"" + id + "\",\"name\":\"x\",\"empty\":null}");

    @Test
    void readsRequiredAndOptionalFields() {
        assertThat(CommandFields.uuid(node, "id")).isEqualTo(id);
        assertThat(CommandFields.text(node, "name")).isEqualTo("x");
        assertThat(CommandFields.optionalUuid(node, "empty")).isNull();
        assertThat(CommandFields.optionalUuid(node, "missing")).isNull();
        assertThat(CommandFields.optionalText(node, "empty")).isNull();
    }

    @Test
    void missingOrMalformedRequiredFieldsAreContractViolations() {
        assertThatThrownBy(() -> CommandFields.uuid(node, "name")).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> CommandFields.text(node, "missing")).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}

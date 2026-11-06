package com.budgetflow.common.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetflow.common.core.error.DomainException;
import com.budgetflow.common.core.error.ErrorCategory;
import com.budgetflow.common.core.error.ErrorCode;
import com.budgetflow.common.messaging.MessagingTestApplication;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(classes = MessagingTestApplication.class)
class SagaParticipantIT {

    private static final ErrorCode ARCHIVED = ErrorCode.of("ACCOUNT_ARCHIVED", ErrorCategory.BUSINESS_RULE);

    @Autowired SagaParticipantFactory participants;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger runs = new AtomicInteger();
    private SagaParticipant participant;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM outbox_message").update();
        jdbc.sql("DELETE FROM participant_effect").update();
        participant = participants.create("test-service", Map.of(
                "test.Succeed", command -> {
                    runs.incrementAndGet();
                    jdbc.sql("INSERT INTO participant_effect (id, note) VALUES (:id, 'ok')")
                            .param("id", UUID.fromString(command.get("sagaId").asString())).update();
                },
                "test.FailBusiness", command -> {
                    runs.incrementAndGet();
                    jdbc.sql("INSERT INTO participant_effect (id, note) VALUES (gen_random_uuid(), 'partial')").update();
                    throw new DomainException(ARCHIVED, "Account is archived");
                },
                "test.FailTechnical", command -> {
                    runs.incrementAndGet();
                    throw new IllegalStateException("database on fire");
                }));
    }

    @Test
    void successCommitsEffectAndRepliesSuccessExactlyOnce() {
        UUID sagaId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        Message command = command("test.Succeed", sagaId, commandId);

        participant.handle(command);
        participant.handle(command); // redelivery

        assertThat(runs).hasValue(1);
        assertThat(effects()).containsExactly("ok");
        List<Map<String, Object>> replies = replies();
        assertThat(replies).hasSize(1);
        assertThat(replies.getFirst()).containsEntry("routing_key", SagaContract.REPLY_ROUTING_KEY)
                .containsEntry("message_type", SagaContract.REPLY_TYPE)
                .containsEntry("correlation_id", sagaId);
        assertThat((String) replies.getFirst().get("payload"))
                .contains("\"sagaId\":\"" + sagaId + "\"", "\"commandId\":\"" + commandId + "\"",
                        "\"commandType\":\"test.Succeed\"", "\"outcome\":\"SUCCESS\"");
    }

    @Test
    void businessFailureRollsBackEffectsAndRepliesFailureWithCode() {
        Message command = command("test.FailBusiness", UUID.randomUUID(), UUID.randomUUID());

        participant.handle(command);
        participant.handle(command); // redelivery after failure must not re-run

        assertThat(runs).hasValue(1);
        assertThat(effects()).as("partial writes rolled back").isEmpty();
        assertThat(replies()).hasSize(1);
        assertThat((String) replies().getFirst().get("payload"))
                .contains("\"outcome\":\"FAILURE\"", "\"errorCode\":\"ACCOUNT_ARCHIVED\"",
                        "\"errorMessage\":\"Account is archived\"");
    }

    @Test
    void technicalFailurePropagatesForRetryAndRecordsNothing() {
        Message command = command("test.FailTechnical", UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> participant.handle(command)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> participant.handle(command)).isInstanceOf(IllegalStateException.class);

        assertThat(runs).as("retried").hasValue(2);
        assertThat(replies()).isEmpty();
    }

    @Test
    void unknownOrMalformedCommandsAreDeadLettered() {
        assertThatThrownBy(() -> participant.handle(command("test.Unknown", UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        Message noIds = message("test.Succeed", "{}");
        assertThatThrownBy(() -> participant.handle(noIds)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(runs).hasValue(0);
    }

    private List<String> effects() {
        return jdbc.sql("SELECT note FROM participant_effect").query(String.class).list();
    }

    private List<Map<String, Object>> replies() {
        return jdbc.sql("SELECT routing_key, message_type, correlation_id, payload::text AS payload FROM outbox_message")
                .query().listOfRows();
    }

    private static Message command(String type, UUID sagaId, UUID commandId) {
        return message(type, "{\"sagaId\":\"" + sagaId + "\",\"commandId\":\"" + commandId + "\",\"amount\":\"-1\"}");
    }

    private static Message message(String type, String body) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(UUID.randomUUID().toString());
        props.setType(type);
        Message m = new Message(body.getBytes(StandardCharsets.UTF_8), props);
        return m;
    }
}

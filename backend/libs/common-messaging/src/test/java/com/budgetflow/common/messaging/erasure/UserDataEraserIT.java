package com.budgetflow.common.messaging.erasure;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetflow.common.messaging.MessagingTestApplication;
import com.budgetflow.common.messaging.inbox.IdempotentConsumer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(classes = MessagingTestApplication.class)
class UserDataEraserIT {

    @Autowired IdempotentConsumer inbox;
    @Autowired JdbcClient jdbc;
    UserDataEraser eraser;

    @BeforeEach
    void setUp() {
        jdbc.sql("CREATE TABLE IF NOT EXISTS erasure_parent (id UUID PRIMARY KEY, user_id UUID NOT NULL)").update();
        jdbc.sql("CREATE TABLE IF NOT EXISTS erasure_child (id UUID PRIMARY KEY, user_id UUID NOT NULL, "
                + "parent_id UUID NOT NULL REFERENCES erasure_parent (id))").update();
        // Children first: statements run in the order given, inside one transaction.
        eraser = new UserDataEraser("test.user-deleted", "test-service.user-deleted", inbox, jdbc, List.of(
                "DELETE FROM erasure_child WHERE user_id = :userId",
                "DELETE FROM erasure_parent WHERE user_id = :userId"));
    }

    @Test
    void erasesOnlyThatUsersRowsAndPublishedMessagesAboutThem() {
        UUID gone = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seed(gone);
        seed(kept);
        outbox(gone, true);
        UUID inFlight = outbox(gone, false);
        outbox(kept, true);

        eraser.onMessage(userDeleted(UUID.randomUUID(), gone));

        assertThat(count("erasure_parent", gone)).isZero();
        assertThat(count("erasure_child", gone)).isZero();
        assertThat(count("erasure_parent", kept)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT id FROM outbox_message WHERE payload::text LIKE '%' || :u || '%'")
                .param("u", gone.toString()).query(UUID.class).list())
                .as("only the not-yet-published message survives, so delivery never breaks").containsExactly(inFlight);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_message WHERE payload::text LIKE '%' || :u || '%'")
                .param("u", kept.toString()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aRedeliveredEventIsHarmless() {
        UUID user = UUID.randomUUID();
        seed(user);
        Message event = userDeleted(UUID.randomUUID(), user);
        eraser.onMessage(event);
        seed(user); // data written after the first delivery (should never happen, proves the second run is skipped)
        eraser.onMessage(event);
        assertThat(count("erasure_parent", user)).isEqualTo(1);
    }

    @Test
    void declaresItsQueueOnTheEventsExchange() {
        assertThat(UserDataEraser.ROUTING_KEY).isEqualTo("identity.user-deleted");
        assertThat(eraser.queue()).isEqualTo("test.user-deleted");
    }

    private void seed(UUID user) {
        UUID parent = UUID.randomUUID();
        jdbc.sql("INSERT INTO erasure_parent VALUES (:id, :u)").param("id", parent).param("u", user).update();
        jdbc.sql("INSERT INTO erasure_child VALUES (:id, :u, :p)").param("id", UUID.randomUUID()).param("u", user)
                .param("p", parent).update();
    }

    private UUID outbox(UUID user, boolean published) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO outbox_message (id, exchange, routing_key, message_type, payload, published_at)
                VALUES (:id, 'x', 'k', 't', CAST(:p AS json), %s)""".formatted(published ? "now()" : "NULL"))
                .param("id", id).param("p", "{\"userId\":\"" + user + "\"}").update();
        return id;
    }

    private int count(String table, UUID user) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :u").param("u", user).query(Integer.class).single();
    }

    private static Message userDeleted(UUID messageId, UUID userId) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId.toString());
        props.setType("identity.UserDeleted");
        String body = "{\"userId\":\"%s\",\"deletedAt\":\"2026-11-10T10:00:00Z\"}".formatted(userId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}

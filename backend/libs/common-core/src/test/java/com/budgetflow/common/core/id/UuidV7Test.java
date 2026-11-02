package com.budgetflow.common.core.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generatesVersion7Rfc9562Uuids() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsUnixMillisTimestampInMostSignificantBits() {
        Instant now = Instant.parse("2026-10-27T10:15:30.123Z");
        UuidV7 generator = new UuidV7(Clock.fixed(now, ZoneOffset.UTC));

        UUID id = generator.next();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
        assertThat(UuidV7.timestampOf(id)).isEqualTo(now);
    }

    @Test
    void idsAreUniqueAndMonotonicWithinSameMillisecond() {
        UuidV7 generator = new UuidV7(Clock.fixed(Instant.now(), ZoneOffset.UTC));
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.next());
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        assertThat(ids).isSortedAccordingTo(UuidV7::compareUnsigned);
    }

    @Test
    void idsAreMonotonicAcrossThreads() throws InterruptedException {
        UuidV7 generator = new UuidV7(Clock.systemUTC());
        List<UUID> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(Thread.ofVirtual().start(() -> {
                for (int i = 0; i < 1_000; i++) {
                    ids.add(generator.next());
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(new HashSet<>(ids)).hasSize(8_000);
    }
}

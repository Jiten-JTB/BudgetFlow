package com.budgetflow.common.core.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: 48-bit Unix millis, 12-bit monotonic counter, 62 random bits.
 * Time-ordered IDs keep B-tree indexes compact. Monotonic per generator instance.
 */
public final class UuidV7 {

    private static final UuidV7 DEFAULT = new UuidV7(Clock.systemUTC());
    private static final int COUNTER_BITS = 12;
    private static final int MAX_COUNTER = (1 << COUNTER_BITS) - 1;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private long lastMillis = -1;
    private int counter;

    public UuidV7(Clock clock) {
        this.clock = clock;
    }

    public static UUID generate() {
        return DEFAULT.next();
    }

    public static Instant timestampOf(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: " + uuid);
        }
        return Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16);
    }

    /** Unsigned comparison; {@link UUID#compareTo} is signed and does not follow generation order. */
    public static int compareUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    public synchronized UUID next() {
        long now = clock.millis();
        if (now > lastMillis) {
            lastMillis = now;
            // Start in the lower half so a burst has room to increment before overflowing.
            counter = random.nextInt(MAX_COUNTER / 2);
        } else if (counter < MAX_COUNTER) {
            counter++;
        } else {
            // Counter exhausted (or clock moved backwards): borrow the next millisecond.
            lastMillis++;
            counter = 0;
        }
        long msb = (lastMillis << 16) | (0x7L << 12) | counter;
        long lsb = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}

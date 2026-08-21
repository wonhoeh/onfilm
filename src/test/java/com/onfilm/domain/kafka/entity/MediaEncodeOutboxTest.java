package com.onfilm.domain.kafka.entity;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MediaEncodeOutboxTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void claimsRetriesWithBackoffAndEventuallyBecomesDead() {
        MediaEncodeOutbox outbox = newOutbox();
        Instant time = NOW;
        for (int attempt = 1; attempt <= MediaEncodeOutbox.MAX_ATTEMPTS; attempt++) {
            outbox.claim(time, Duration.ofMinutes(2));
            outbox.failed("kafka unavailable", time);
            if (attempt < MediaEncodeOutbox.MAX_ATTEMPTS) {
                assertThat(outbox.getStatus()).isEqualTo(MediaEncodeOutboxStatus.PENDING);
                time = outbox.getNextAttemptAt();
            }
        }
        assertThat(outbox.getStatus()).isEqualTo(MediaEncodeOutboxStatus.DEAD);
        assertThat(outbox.getAttempts()).isEqualTo(MediaEncodeOutbox.MAX_ATTEMPTS);
    }

    @Test
    void expiredLeaseCanBeClaimedAgainAndPublished() {
        MediaEncodeOutbox outbox = newOutbox();
        outbox.claim(NOW, Duration.ofMinutes(2));
        assertThat(outbox.isClaimable(NOW.plusSeconds(121))).isTrue();
        outbox.claim(NOW.plusSeconds(121), Duration.ofMinutes(2));
        outbox.published(NOW.plusSeconds(122));
        assertThat(outbox.getStatus()).isEqualTo(MediaEncodeOutboxStatus.PUBLISHED);
    }

    private MediaEncodeOutbox newOutbox() {
        return MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "{\"schemaVersion\":1}", NOW);
    }
}

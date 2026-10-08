package org.openraffle.image;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRateLimiterTest {

    @Test
    void eachUserGetsTheLimitPerWindowAndTheWindowSlides() {
        UploadRateLimiter limiter = new UploadRateLimiter(3, Duration.ofMinutes(10));
        Instant t0 = Instant.parse("2026-10-08T10:00:00Z");

        assertThat(limiter.allow("ann@example.com", t0)).isTrue();
        assertThat(limiter.allow("ann@example.com", t0.plusSeconds(1))).isTrue();
        assertThat(limiter.allow("ann@example.com", t0.plusSeconds(2))).isTrue();
        assertThat(limiter.allow("ann@example.com", t0.plusSeconds(3))).isFalse();
        assertThat(limiter.allow("bob@example.com", t0.plusSeconds(3))).isTrue(); // someone else is unaffected

        // Ten minutes after the first upload it drops out of the window, freeing one slot.
        Instant later = t0.plus(Duration.ofMinutes(10)).plusSeconds(1);
        assertThat(limiter.allow("ann@example.com", later)).isTrue();
        assertThat(limiter.allow("ann@example.com", later)).isFalse();
        assertThat(limiter.limit()).isEqualTo(3);
    }
}

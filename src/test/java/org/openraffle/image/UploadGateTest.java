package org.openraffle.image;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UploadGateTest {

    @Test
    void onlySoManyPicturesAreProcessedAtOnceAndTheRestWaitOrGiveUp() {
        UploadGate gate = new UploadGate(1, Duration.ofMillis(200));

        assertThat(gate.enter()).isTrue();
        assertThat(gate.available()).isZero();
        long start = System.nanoTime();
        assertThat(gate.enter()).isFalse(); // someone else is decoding; waited the full patience
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(150));

        gate.leave();
        assertThat(gate.enter()).isTrue();
        gate.leave();
    }

    @Test
    void atLeastOneAtATimeEvenIfMisconfigured() {
        assertThat(new UploadGate(0, Duration.ofMillis(10)).available()).isEqualTo(1);
    }
}

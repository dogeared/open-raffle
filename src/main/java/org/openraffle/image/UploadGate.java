package org.openraffle.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Lets only a few pictures be processed at once. Decoding a photo takes tens of megabytes
 * of heap, so six at a time on a small server runs out of memory; with the gate the rest
 * simply wait their turn. The browser may still send every file at once; they land in
 * temporary files, not in memory.
 */
@Component
public class UploadGate {

    private final Semaphore permits;
    private final Duration patience;

    @Autowired
    public UploadGate(@Value("${raffle.uploads.concurrent:1}") int concurrent) {
        this(concurrent, Duration.ofMinutes(2));
    }

    UploadGate(int concurrent, Duration patience) {
        this.permits = new Semaphore(Math.max(1, concurrent), true);
        this.patience = patience;
    }

    /** Waits for a turn; false if the wait was too long (the picture is then refused, not lost silently). */
    public boolean enter() {
        try {
            return permits.tryAcquire(patience.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void leave() {
        permits.release();
    }

    int available() {
        return permits.availablePermits();
    }
}

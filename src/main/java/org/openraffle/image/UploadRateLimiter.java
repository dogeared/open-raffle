package org.openraffle.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caps how many pictures one user may upload in a window, so a runaway client cannot flood Drive. */
@Component
public class UploadRateLimiter {

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    @Autowired
    public UploadRateLimiter(@Value("${raffle.uploads.per-user-per-10-minutes:60}") int limit) {
        this(limit, Duration.ofMinutes(10));
    }

    UploadRateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /** Records an attempt and says whether it is within the limit. */
    public boolean allow(String user) {
        return allow(user, Instant.now());
    }

    boolean allow(String user, Instant now) {
        Deque<Instant> times = recent.computeIfAbsent(user == null ? "" : user, k -> new ArrayDeque<>());
        synchronized (times) {
            Instant cutoff = now.minus(window);
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    public int limit() {
        return limit;
    }
}

package no.example.verdan.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter using a sliding window approach.
 * Limits the number of requests per IP within a configurable time window.
 *
 * Thread-safe. Suitable for single-instance deployments.
 * For clustered deployments, use Redis-based rate limiting instead.
 */
public class RateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxRequests;
    private final long windowMs;
    private final Map<String, WindowEntry> entries = new ConcurrentHashMap<>();

    /**
     * @param maxRequests maximum allowed requests per window
     * @param windowMs   time window in milliseconds
     */
    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /**
     * Check if a request from the given key (e.g. IP) is allowed.
     *
     * @param key identifier (typically client IP)
     * @return true if the request is allowed, false if rate limited
     */
    public boolean isAllowed(String key) {
        long now = System.currentTimeMillis();
        entries.compute(key, (k, entry) -> {
            if (entry == null || now - entry.windowStart > windowMs) {
                return new WindowEntry(now, new AtomicInteger(1));
            }
            entry.count.incrementAndGet();
            return entry;
        });

        WindowEntry entry = entries.get(key);
        if (entry != null && entry.count.get() > maxRequests) {
            LOG.warn("Rate limit exceeded for key: {}", key);
            return false;
        }
        return true;
    }

    /**
     * Get the number of remaining requests for a key.
     */
    public int remaining(String key) {
        WindowEntry entry = entries.get(key);
        if (entry == null) return maxRequests;
        long now = System.currentTimeMillis();
        if (now - entry.windowStart > windowMs) return maxRequests;
        return Math.max(0, maxRequests - entry.count.get());
    }

    /** Periodically clean up expired entries (call from a scheduled task). */
    public void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMs * 2);
    }

    private static class WindowEntry {
        final long windowStart;
        final AtomicInteger count;

        WindowEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

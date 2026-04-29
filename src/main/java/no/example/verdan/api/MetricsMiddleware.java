package no.example.verdan.api;

import io.javalin.Javalin;
import io.javalin.http.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Middleware for API request metrics and structured logging.
 *
 * Tracks per-endpoint:
 *   - Total request count
 *   - Average response time (ms)
 *   - Error count (4xx/5xx)
 *
 * Adds MDC context (requestId, user) for log correlation.
 */
public class MetricsMiddleware {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsMiddleware.class);

    /** Metrics storage per "METHOD /path" key. */
    private static final Map<String, EndpointMetrics> METRICS = new ConcurrentHashMap<>();

    /**
     * Register the metrics middleware on the Javalin app.
     */
    public static void register(Javalin app) {
        // Before: set MDC context and record start time
        app.before(ctx -> {
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            ctx.attribute("requestId", requestId);
            ctx.attribute("startTime", System.currentTimeMillis());

            MDC.put("requestId", requestId);
            MDC.put("method", ctx.method().name());
            MDC.put("path", ctx.path());
        });

        // After: compute duration, log, and update metrics
        app.after(ctx -> {
            Long startTime = ctx.attribute("startTime");
            if (startTime == null) return;

            long duration = System.currentTimeMillis() - startTime;
            int status = ctx.status().getCode();
            String key = ctx.method().name() + " " + matchedPath(ctx);

            // Update stored metrics
            METRICS.computeIfAbsent(key, k -> new EndpointMetrics())
                    .record(duration, status);

            // Add user info to MDC if available
            String username = ctx.attribute("username");
            if (username != null) {
                MDC.put("user", username);
            }

            // Log the request
            if (status >= 500) {
                LOG.error("[{}] {} {} → {} ({}ms)", ctx.attribute("requestId"),
                        ctx.method(), ctx.path(), status, duration);
            } else if (status >= 400) {
                LOG.warn("[{}] {} {} → {} ({}ms)", ctx.attribute("requestId"),
                        ctx.method(), ctx.path(), status, duration);
            } else {
                LOG.info("[{}] {} {} → {} ({}ms)", ctx.attribute("requestId"),
                        ctx.method(), ctx.path(), status, duration);
            }

            // Clean up MDC
            MDC.clear();
        });
    }

    /**
     * Get a snapshot of all collected metrics.
     */
    public static Map<String, EndpointMetrics> getMetrics() {
        return Map.copyOf(METRICS);
    }

    /**
     * Reset all metrics (useful for testing).
     */
    public static void reset() {
        METRICS.clear();
    }

    /**
     * Try to get the matched route path (e.g. "/api/users/{id}") instead of
     * the actual path (e.g. "/api/users/42") for better metric grouping.
     */
    private static String matchedPath(Context ctx) {
        try {
            String matched = ctx.matchedPath();
            return matched != null && !matched.isBlank() ? matched : ctx.path();
        } catch (Exception e) {
            return ctx.path();
        }
    }

    /**
     * Metrics for a single endpoint.
     */
    public static class EndpointMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private volatile long maxDurationMs = 0;

        void record(long durationMs, int statusCode) {
            totalRequests.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            if (statusCode >= 400) {
                errorCount.incrementAndGet();
            }
            if (durationMs > maxDurationMs) {
                maxDurationMs = durationMs;
            }
        }

        public long getTotalRequests() { return totalRequests.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public long getMaxDurationMs() { return maxDurationMs; }

        public double getAvgDurationMs() {
            long total = totalRequests.get();
            return total > 0 ? (double) totalDurationMs.get() / total : 0;
        }

        public double getErrorRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) errorCount.get() / total : 0;
        }

        @Override
        public String toString() {
            return String.format("requests=%d, avg=%.1fms, max=%dms, errors=%d (%.1f%%)",
                    totalRequests.get(), getAvgDurationMs(), maxDurationMs,
                    errorCount.get(), getErrorRate() * 100);
        }
    }
}

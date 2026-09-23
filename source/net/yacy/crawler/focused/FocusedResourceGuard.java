// Resource-observer recovery guard for focused autocrawler scheduling.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

/**
 * Small deterministic state machine used to prevent a transient low-memory
 * event from becoming a permanent focused-crawl stall. It never authorizes a
 * manual pause to be resumed: callers must provide the exact observer cause.
 */
public final class FocusedResourceGuard {

    private static final long MIN_HEADROOM = 512L * 1024L * 1024L;
    private static final long MIN_REFILL_HEADROOM = 256L * 1024L * 1024L;
    private static final int REQUIRED_HEALTHY_CHECKS = 2;

    private int healthyChecks;
    private long recoveryAttempts;
    private long recoveries;

    public synchronized boolean observe(final String pauseCause, final long availableMemory,
            final long maxMemory, final boolean shortMemory, final boolean diskHealthy) {
        if (pauseCause == null || !pauseCause.startsWith("resource observer:")) {
            this.healthyChecks = 0;
            return false;
        }
        final long threshold = recoveryThreshold(maxMemory);
        final boolean healthy = !shortMemory && diskHealthy && availableMemory >= threshold;
        if (!healthy) {
            this.healthyChecks = 0;
            return false;
        }
        this.recoveryAttempts++;
        this.healthyChecks++;
        if (this.healthyChecks < REQUIRED_HEALTHY_CHECKS) return false;
        this.healthyChecks = 0;
        this.recoveries++;
        return true;
    }

    public synchronized void reset() {
        this.healthyChecks = 0;
    }

    public synchronized int healthyChecks() { return this.healthyChecks; }
    public synchronized long recoveryAttempts() { return this.recoveryAttempts; }
    public synchronized long recoveries() { return this.recoveries; }

    public static long recoveryThreshold(final long maxMemory) {
        if (maxMemory <= 0L) return MIN_HEADROOM;
        return Math.max(MIN_HEADROOM, maxMemory * 30L / 100L);
    }

    /**
     * Lower threshold for ordinary focused-queue refills. A refill adds
     * bounded queue metadata and is not the same operation as recovering a
     * crawler that YaCy's ResourceObserver has already paused. Keeping this
     * threshold separate avoids draining a healthy queue merely because the
     * JVM has committed most of its currently reserved heap.
     */
    public static long refillThreshold(final long maxMemory) {
        if (maxMemory <= 0L) return MIN_REFILL_HEADROOM;
        return Math.max(MIN_REFILL_HEADROOM, maxMemory * 20L / 100L);
    }
}

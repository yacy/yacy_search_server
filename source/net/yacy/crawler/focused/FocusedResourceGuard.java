// Resource-observer recovery guard for focused autocrawler scheduling.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

/**
 * Small deterministic state machine used to prevent a transient low-memory
 * event from becoming a permanent focused-crawl stall. It never authorizes a
 * manual pause to be resumed: callers must provide the exact observer cause.
 */
public final class FocusedResourceGuard {

    private static final long MIN_PAUSE_HEADROOM = 512L * 1024L * 1024L;
    private static final long MIN_RECOVERY_HEADROOM = 768L * 1024L * 1024L;
    private static final int REQUIRED_HEALTHY_CHECKS = 2;

    private int healthyChecks;
    private long recoveryAttempts;
    private long recoveries;

    public synchronized boolean observe(final String pauseCause, final long availableMemory,
            final long maxMemory, final boolean shortMemory, final boolean diskHealthy) {
        if (!isManagedPauseCause(pauseCause)) {
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

    public static boolean isManagedPauseCause(final String cause) {
        return cause != null && (cause.startsWith("resource observer:")
                || cause.startsWith("focused resource guard:"));
    }

    public static boolean isFocusedPauseCause(final String cause) {
        return cause != null && cause.startsWith("focused resource guard:");
    }

    /** Headroom below which focused scheduling pauses native local crawling. */
    public static long pauseThreshold(final long maxMemory) {
        if (maxMemory <= 0L) return MIN_PAUSE_HEADROOM;
        return Math.max(MIN_PAUSE_HEADROOM, maxMemory * 25L / 100L);
    }

    public static boolean shouldPause(final long availableMemory, final long maxMemory,
            final boolean shortMemory) {
        return shortMemory || availableMemory < pauseThreshold(maxMemory);
    }

    /**
     * A low but non-critical heap reading is an opportunity to ask YaCy's
     * memory controller to reclaim unused heap before stopping the crawler.
     * A sticky short-memory signal is already a failed allocation and must not
     * be delayed by another recovery attempt.
     */
    public static boolean shouldAttemptMemoryRecovery(final long availableMemory,
            final long maxMemory, final boolean shortMemory) {
        return !shortMemory && availableMemory < pauseThreshold(maxMemory);
    }

    public static long recoveryThreshold(final long maxMemory) {
        if (maxMemory <= 0L) return MIN_RECOVERY_HEADROOM;
        return Math.max(MIN_RECOVERY_HEADROOM, maxMemory * 35L / 100L);
    }

    /**
     * Stop queue admissions at the pause threshold. Recovery intentionally
     * requires more headroom, providing hysteresis rather than oscillating at
     * a single memory boundary.
     */
    public static long refillThreshold(final long maxMemory) {
        return pauseThreshold(maxMemory);
    }
}

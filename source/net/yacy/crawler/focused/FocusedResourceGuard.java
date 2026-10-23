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
    private static final long MIN_PHYSICAL_HEADROOM = 512L * 1024L * 1024L;
    private static final int REQUIRED_HEALTHY_CHECKS = 2;

    enum MemoryPressureAction {
        NONE,
        CLEAR_STALE_SHORT_STATUS,
        PAUSE
    }

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

    /**
     * Keep an automatically imposed local-crawler pause across restart. The
     * focused scheduler then monitors recovery without admitting work until
     * the resource guard has observed consecutive healthy checks.
     */
    public static boolean preservePauseOnStartup(final boolean paused, final boolean autodisabled,
            final String cause) {
        return paused && autodisabled && isManagedPauseCause(cause);
    }

    /** A startup resource hold is auto-resumable unless the operator paused the focused scheduler. */
    public static boolean autoResumeOnStartup(final boolean persistedResourcePause,
            final boolean operatorPaused) {
        return persistedResourcePause && !operatorPaused;
    }

    /** Keep at least 20% of physical RAM (and 10% of swap) available to the OS and other services. */
    public static boolean systemMemoryHealthy(final long physicalAvailable, final long physicalTotal,
            final long swapFree, final long swapTotal) {
        if (physicalAvailable >= 0L && physicalTotal > 0L
                && physicalAvailable < physicalMemoryThreshold(physicalTotal)) return false;
        return swapFree < 0L || swapTotal <= 0L || swapFree >= swapTotal / 10L;
    }

    public static long physicalMemoryThreshold(final long physicalTotal) {
        if (physicalTotal <= 0L) return MIN_PHYSICAL_HEADROOM;
        return Math.max(MIN_PHYSICAL_HEADROOM, physicalTotal / 5L);
    }

    /** Refresh the visible pause message with the last measured values instead of leaving stale startup data. */
    public static String recoveryPauseCause(final String previousCause, final long heapAvailable,
            final long heapMaximum, final long physicalAvailable, final long physicalTotal,
            final long swapFree, final long swapTotal, final boolean diskHealthy) {
        final String prefix = previousCause != null && previousCause.startsWith("resource observer:")
                ? "resource observer: " : "focused resource guard: ";
        if (!diskHealthy) return prefix + "waiting for disk space to recover; current JVM headroom "
                + heapAvailable + " bytes";
        if (!systemMemoryHealthy(physicalAvailable, physicalTotal, swapFree, swapTotal)) {
            if (physicalTotal > 0L && physicalAvailable >= 0L
                    && physicalAvailable < physicalMemoryThreshold(physicalTotal)) {
                return prefix + "physical RAM available " + physicalAvailable + " bytes is below "
                        + physicalMemoryThreshold(physicalTotal) + " bytes";
            }
            return prefix + "swap free space is below 10%; current JVM headroom " + heapAvailable + " bytes";
        }
        final long required = recoveryThreshold(heapMaximum);
        if (heapAvailable < required) return prefix + "JVM headroom " + heapAvailable
                + " bytes is below recovery threshold " + required + " bytes";
        return prefix + "awaiting second healthy recovery check; current JVM headroom "
                + heapAvailable + " bytes of " + heapMaximum + " bytes";
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

    /** Pause native local crawling while any focused-crawl safety limit is unhealthy. */
    public static boolean shouldPauseForResources(final boolean jvmPressure,
            final boolean systemMemoryHealthy, final boolean diskHealthy) {
        return jvmPressure || !systemMemoryHealthy || !diskHealthy;
    }

    /**
     * Cached search events retain local RWI containers, which can be large
     * after multi-segment merges. Evict them when a new automatic resource
     * pause is imposed, but do not disturb searches for an existing pause.
     */
    static boolean shouldClearSearchEventCache(final boolean crawlAlreadyPaused,
            final boolean jvmPressure, final boolean systemMemoryHealthy) {
        return !crawlAlreadyPaused && (jvmPressure || !systemMemoryHealthy);
    }

    /**
     * Decide whether to pause crawling or clear a stale short-memory flag.
     * Low headroom must pause immediately; a forced full GC here can briefly
     * restore headroom only for the crawler to consume it again.
     */
    static MemoryPressureAction memoryPressureAction(final long availableMemory,
            final long maxMemory, final boolean shortMemory) {
        if (availableMemory < pauseThreshold(maxMemory)) return MemoryPressureAction.PAUSE;
        if (shortMemory) return MemoryPressureAction.CLEAR_STALE_SHORT_STATUS;
        return MemoryPressureAction.NONE;
    }

    public static long recoveryThreshold(final long maxMemory) {
        if (maxMemory <= 0L) return MIN_RECOVERY_HEADROOM;
        return Math.max(MIN_RECOVERY_HEADROOM, maxMemory * 35L / 100L);
    }

    /** Require recovery headroom before admitting additional focused work. */
    public static boolean hasRecoveryHeadroom(final long availableMemory,
            final long maxMemory, final boolean shortMemory) {
        return !shortMemory && availableMemory >= recoveryThreshold(maxMemory);
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

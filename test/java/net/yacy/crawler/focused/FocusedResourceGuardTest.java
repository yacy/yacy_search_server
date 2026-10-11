package net.yacy.crawler.focused;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FocusedResourceGuardTest {

    @Test
    public void manualPauseNeverRecovers() {
        final FocusedResourceGuard guard = new FocusedResourceGuard();
        assertFalse(guard.observe("user request", 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L, false, true));
        assertFalse(guard.observe("user request", 1024L * 1024L * 1024L,
                2L * 1024L * 1024L * 1024L, false, true));
        assertTrue(guard.healthyChecks() == 0);
    }

    @Test
    public void observerPauseNeedsTwoHealthyChecks() {
        final FocusedResourceGuard guard = new FocusedResourceGuard();
        final String cause = "resource observer: not enough memory space";
        final long max = 2L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.recoveryThreshold(max);
        assertFalse(guard.observe(cause, threshold, max, false, true));
        assertTrue(guard.observe(cause, threshold, max, false, true));
        assertTrue(guard.recoveries() == 1);
    }

    @Test
    public void focusedPauseNeedsTwoHealthyChecks() {
        final FocusedResourceGuard guard = new FocusedResourceGuard();
        final String cause = "focused resource guard: JVM headroom is low";
        final long max = 2L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.recoveryThreshold(max);
        assertFalse(guard.observe(cause, threshold, max, false, true));
        assertTrue(guard.observe(cause, threshold, max, false, true));
        assertTrue(guard.recoveries() == 1);
    }

    @Test
    public void unhealthyCheckResetsStreak() {
        final FocusedResourceGuard guard = new FocusedResourceGuard();
        final String cause = "resource observer: not enough memory space";
        final long max = 2L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.recoveryThreshold(max);
        assertFalse(guard.observe(cause, threshold, max, false, true));
        assertFalse(guard.observe(cause, threshold - 1L, max, false, true));
        assertFalse(guard.observe(cause, threshold, max, false, true));
        assertTrue(guard.healthyChecks() == 1);
    }

    @Test
    public void refillThresholdIsBelowObserverRecoveryThreshold() {
        final long max = 2L * 1024L * 1024L * 1024L;
        assertTrue(FocusedResourceGuard.refillThreshold(max)
                == FocusedResourceGuard.pauseThreshold(max));
        assertTrue(FocusedResourceGuard.refillThreshold(max)
                < FocusedResourceGuard.recoveryThreshold(max));
        assertTrue(FocusedResourceGuard.pauseThreshold(max)
                >= 512L * 1024L * 1024L);
        assertTrue(FocusedResourceGuard.recoveryThreshold(max)
                >= 768L * 1024L * 1024L);
    }

    @Test
    public void pauseAndRecoveryThresholdsScaleWithLargerHeaps() {
        final long max = 4L * 1024L * 1024L * 1024L;
        assertTrue(FocusedResourceGuard.pauseThreshold(max) == max * 25L / 100L);
        assertTrue(FocusedResourceGuard.recoveryThreshold(max) == max * 35L / 100L);
    }

    @Test
    public void lowHeapHeadroomPausesBeforeTheThresholdAndStickyShortMemoryAlwaysPauses() {
        final long max = 2L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.pauseThreshold(max);
        assertTrue(FocusedResourceGuard.shouldPause(threshold - 1L, max, false));
        assertFalse(FocusedResourceGuard.shouldPause(threshold, max, false));
        assertTrue(FocusedResourceGuard.shouldPause(threshold + 1L, max, true));
    }

    @Test
    public void lowHeadroomPausesWithoutForcingHeapReclamation() {
        final long max = 3L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.pauseThreshold(max);
        assertTrue(FocusedResourceGuard.memoryPressureAction(threshold - 1L, max, false)
                == FocusedResourceGuard.MemoryPressureAction.PAUSE);
        assertTrue(FocusedResourceGuard.memoryPressureAction(threshold - 1L, max, true)
                == FocusedResourceGuard.MemoryPressureAction.PAUSE);
        assertTrue(FocusedResourceGuard.memoryPressureAction(threshold, max, false)
                == FocusedResourceGuard.MemoryPressureAction.NONE);
    }

    @Test
    public void staleShortMemoryFlagCanBeClearedOnlyAfterHeadroomRecovers() {
        final long max = 4L * 1024L * 1024L * 1024L;
        final long threshold = FocusedResourceGuard.pauseThreshold(max);
        assertTrue(FocusedResourceGuard.memoryPressureAction(threshold - 1L, max, true)
                == FocusedResourceGuard.MemoryPressureAction.PAUSE);
        assertTrue(FocusedResourceGuard.memoryPressureAction(threshold, max, true)
                == FocusedResourceGuard.MemoryPressureAction.CLEAR_STALE_SHORT_STATUS);
    }

    @Test
    public void onlyObserverAndFocusedGuardPausesAreManaged() {
        assertTrue(FocusedResourceGuard.isManagedPauseCause("resource observer: low memory"));
        assertTrue(FocusedResourceGuard.isManagedPauseCause("focused resource guard: low memory"));
        assertFalse(FocusedResourceGuard.isManagedPauseCause("user request in Crawler_p"));
    }

    @Test
    public void onlyPersistedAutomaticResourcePausesSurviveStartup() {
        assertTrue(FocusedResourceGuard.preservePauseOnStartup(true, true,
                "resource observer: not enough memory space"));
        assertTrue(FocusedResourceGuard.preservePauseOnStartup(true, true,
                "focused resource guard: low JVM headroom"));
        assertFalse(FocusedResourceGuard.preservePauseOnStartup(false, true,
                "focused resource guard: low JVM headroom"));
        assertFalse(FocusedResourceGuard.preservePauseOnStartup(true, false,
                "focused resource guard: low JVM headroom"));
        assertFalse(FocusedResourceGuard.preservePauseOnStartup(true, true, "operator pause"));
    }

    @Test
    public void startupResourcePauseKeepsRecoveryMonitorActiveUnlessOperatorPaused() {
        assertTrue(FocusedResourceGuard.autoResumeOnStartup(true, false));
        assertFalse(FocusedResourceGuard.autoResumeOnStartup(true, true));
        assertFalse(FocusedResourceGuard.autoResumeOnStartup(false, false));
    }

    @Test
    public void systemGuardPreservesPhysicalAndSwapHeadroom() {
        final long gib = 1024L * 1024L * 1024L;
        assertTrue(FocusedResourceGuard.systemMemoryHealthy(2L * gib, 8L * gib,
                12L * gib, 15L * gib));
        assertFalse(FocusedResourceGuard.systemMemoryHealthy(gib, 8L * gib,
                12L * gib, 15L * gib));
        assertFalse(FocusedResourceGuard.systemMemoryHealthy(2L * gib, 8L * gib,
                gib, 15L * gib));
        assertTrue(FocusedResourceGuard.systemMemoryHealthy(-1L, -1L, -1L, -1L));
    }

    @Test
    public void recoveryMessageUsesFreshMeasurementsRatherThanOldPauseHeadroom() {
        final String refreshed = FocusedResourceGuard.recoveryPauseCause(
                "focused resource guard: JVM headroom 52775208 bytes is below 1073741824 bytes",
                2L * 1024L * 1024L * 1024L, 4L * 1024L * 1024L * 1024L,
                3L * 1024L * 1024L * 1024L, 8L * 1024L * 1024L * 1024L,
                12L * 1024L * 1024L * 1024L, 15L * 1024L * 1024L * 1024L, true);
        assertTrue(refreshed.contains("awaiting second healthy recovery check"));
        assertFalse(refreshed.contains("52775208"));
    }

    @Test
    public void recoveryMessageReportsPhysicalMemoryPressureSeparately() {
        final long gib = 1024L * 1024L * 1024L;
        final String refreshed = FocusedResourceGuard.recoveryPauseCause(
                "focused resource guard: JVM headroom 52775208 bytes is below 1073741824 bytes",
                2L * gib, 4L * gib, gib, 8L * gib, 12L * gib, 15L * gib, true);
        assertTrue(refreshed.contains("physical RAM available"));
        assertTrue(refreshed.contains(Long.toString(gib)));
    }
}

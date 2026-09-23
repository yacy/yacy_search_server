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
                < FocusedResourceGuard.recoveryThreshold(max));
        assertTrue(FocusedResourceGuard.refillThreshold(max)
                <= 512L * 1024L * 1024L);
    }
}

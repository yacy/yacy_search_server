package net.yacy.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResourceObserverTest {

    @Test
    public void resumesOnlyAnActiveResourceObserverPause() {
        assertTrue(ResourceObserver.shouldResumePausedCrawl(true, true, "resource observer: not enough memory space"));
    }

    @Test
    public void doesNotResumeAnOperatorPauseWhenAnOldAutoDisableFlagRemains() {
        assertFalse(ResourceObserver.shouldResumePausedCrawl(true, true, "user request in Crawler_p"));
    }

    @Test
    public void doesNotResumeWhenTheCrawlIsAlreadyRunning() {
        assertFalse(ResourceObserver.shouldResumePausedCrawl(false, true, "resource observer: not enough memory space"));
    }

    @Test
    public void doesNotResumeWithoutTheAutoDisableFlag() {
        assertFalse(ResourceObserver.shouldResumePausedCrawl(true, false, "resource observer: not enough memory space"));
    }
}

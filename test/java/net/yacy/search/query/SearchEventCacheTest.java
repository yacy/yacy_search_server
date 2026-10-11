package net.yacy.search.query;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class SearchEventCacheTest {

    @Test
    public void cacheMissDoesNotProbeCrawlQueues() {
        final AtomicBoolean probed = new AtomicBoolean();

        assertFalse(SearchEventCache.shouldInvalidateCachedEvent(false, Long.MAX_VALUE, () -> {
            probed.set(true);
            return true;
        }));

        assertFalse(probed.get());
    }

    @Test
    public void recentCacheHitDoesNotProbeCrawlQueues() {
        final AtomicBoolean probed = new AtomicBoolean();

        assertFalse(SearchEventCache.shouldInvalidateCachedEvent(true, 60000L, () -> {
            probed.set(true);
            return true;
        }));

        assertFalse(probed.get());
    }

    @Test
    public void staleCacheHitChecksWhetherCrawlingIsActive() {
        assertTrue(SearchEventCache.shouldInvalidateCachedEvent(true, 60001L, () -> true));
        assertFalse(SearchEventCache.shouldInvalidateCachedEvent(true, 60001L, () -> false));
    }
}

package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FocusedCrawlSchedulerTest {

    @Test
    public void weightedSelectorPreservesBothLanes() {
        final FocusedCrawlScheduler.WeightedLaneSelector selector = new FocusedCrawlScheduler.WeightedLaneSelector();
        int focused = 0;
        int ordinary = 0;
        for (int i = 0; i < 100; i++) {
            final FocusedCrawlScheduler.Lane lane = selector.choose(true, true, 7, 3);
            if (lane == FocusedCrawlScheduler.Lane.FOCUSED) focused++;
            if (lane == FocusedCrawlScheduler.Lane.ORDINARY) ordinary++;
        }
        assertEquals(70, focused);
        assertEquals(30, ordinary);
        assertEquals(100, selector.dispatches());
    }

    @Test
    public void unavailableLaneDoesNotStarveAvailableLane() {
        final FocusedCrawlScheduler.WeightedLaneSelector selector = new FocusedCrawlScheduler.WeightedLaneSelector();
        assertEquals(FocusedCrawlScheduler.Lane.ORDINARY, selector.choose(false, true, 7, 3));
        assertEquals(FocusedCrawlScheduler.Lane.FOCUSED, selector.choose(true, false, 7, 3));
        assertTrue(selector.choose(false, false, 7, 3) == null);
    }

    @Test
    public void zeroOrdinaryWeightDisablesOrdinaryFallbackAndKeepsFocusedPriority() {
        final FocusedCrawlScheduler.WeightedLaneSelector selector = new FocusedCrawlScheduler.WeightedLaneSelector();
        assertEquals(FocusedCrawlScheduler.Lane.FOCUSED, selector.choose(true, true, 6, 0));
        assertTrue(selector.choose(false, true, 6, 0) == null);
    }
}

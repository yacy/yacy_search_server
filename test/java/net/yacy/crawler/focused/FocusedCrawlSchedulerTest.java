package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

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

    @Test
    public void queueSnapshotRestartsAllStreamsAfterConcurrentMutation() {
        final AtomicInteger racedStreamCalls = new AtomicInteger();
        final IntFunction<Iterator<String>> streams = index -> index == 0
                ? Arrays.asList("focused-a", "focused-b").iterator()
                : racedStreamCalls.getAndIncrement() == 0
                        ? new Iterator<String>() {
                            @Override public boolean hasNext() { return true; }
                            @Override public String next() { throw new NoSuchElementException("concurrent pop"); }
                        }
                        : Collections.singletonList("focused-pdf").iterator();

        assertEquals(3, FocusedQueueCounter.countSnapshot(2, streams, value -> true, 3));
        assertEquals(2, racedStreamCalls.get());
    }

    @Test
    public void queueSnapshotReturnsUnavailableOnlyAfterBoundedRetries() {
        final AtomicInteger calls = new AtomicInteger();
        final IntFunction<Iterator<String>> streams = index -> {
            calls.incrementAndGet();
            return new Iterator<String>() {
                @Override public boolean hasNext() { return true; }
                @Override public String next() { throw new NoSuchElementException("concurrent pop"); }
            };
        };

        assertEquals(-1, FocusedQueueCounter.countSnapshot(1, streams, value -> true, 3));
        assertEquals(3, calls.get());
    }
}

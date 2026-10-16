package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Modifier;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.apache.solr.common.SolrDocument;
import org.junit.Test;

import net.yacy.search.schema.CollectionSchema;

public class FocusedCrawlSchedulerTest {

    @Test
    public void statusReadersDoNotAcquireTheCrawlCycleMonitor() throws Exception {
        assertFalse(Modifier.isSynchronized(FocusedCrawlScheduler.class.getMethod("statuses").getModifiers()));
        assertFalse(Modifier.isSynchronized(FocusedCrawlScheduler.class.getMethod("statusJSON").getModifiers()));
        assertFalse(Modifier.isSynchronized(FocusedCrawlScheduler.class
                .getMethod("statusJSON", java.util.Collection.class).getModifiers()));
    }

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

    @Test
    public void soleEnabledProfileUsesAggregateWithoutWalkingHostQueues() {
        final AtomicInteger exactCalls = new AtomicInteger();
        final FocusedQueueCounter.QueueSize snapshot = FocusedQueueCounter.countForScheduling(true,
                () -> { exactCalls.incrementAndGet(); return 17; }, () -> 42);

        assertEquals(42, snapshot.size());
        assertFalse(snapshot.exact());
        assertFalse(snapshot.unavailable());
        assertEquals(0, exactCalls.get());
    }

    @Test
    public void multipleEnabledProfilesKeepExactCountAndAggregateFallback() {
        final AtomicInteger aggregateCalls = new AtomicInteger();
        final FocusedQueueCounter.QueueSize exact = FocusedQueueCounter.countForScheduling(false,
                () -> 17, () -> { aggregateCalls.incrementAndGet(); return 42; });
        assertEquals(17, exact.size());
        assertTrue(exact.exact());
        assertEquals(0, aggregateCalls.get());

        final FocusedQueueCounter.QueueSize fallback = FocusedQueueCounter.countForScheduling(false,
                () -> -1, () -> { aggregateCalls.incrementAndGet(); return 42; });
        assertEquals(42, fallback.size());
        assertFalse(fallback.exact());
        assertTrue(fallback.unavailable());
        assertEquals(1, aggregateCalls.get());
    }

    @Test
    public void indexedSourceBatchIsCappedWithoutIncreasingSmallerPolicies() {
        assertEquals(FocusedCrawlScheduler.MAX_INDEXED_SOURCE_BATCH,
                FocusedCrawlScheduler.boundedIndexedSourceBatch(200));
        assertEquals(12, FocusedCrawlScheduler.boundedIndexedSourceBatch(12));
        assertEquals(1, FocusedCrawlScheduler.boundedIndexedSourceBatch(0));
    }

    @Test
    public void outboundLinksAreStreamedAndKeepAllDistinctSourceLinks() {
        final SolrDocument source = new SolrDocument();
        final List<String> stubs = new ArrayList<>();
        for (int i = 0; i < 4200; i++) {
            stubs.add("example.ca/path-" + i);
        }
        stubs.add("example.ca/path-0");
        final AtomicInteger visitedStubs = new AtomicInteger();
        final Collection<String> countedStubs = new AbstractCollection<String>() {
            @Override
            public Iterator<String> iterator() {
                final Iterator<String> sourceIterator = stubs.iterator();
                return new Iterator<String>() {
                    @Override public boolean hasNext() { return sourceIterator.hasNext(); }
                    @Override public String next() {
                        visitedStubs.incrementAndGet();
                        return sourceIterator.next();
                    }
                };
            }

            @Override
            public int size() {
                return stubs.size();
            }
        };
        source.setField(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName(), countedStubs);
        source.setField(CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(),
                Collections.singletonList("001-https"));

        final Iterable<String> links = FocusedCrawlScheduler.outboundLinks(source, "http");
        assertEquals(0, visitedStubs.get());
        final Iterator<String> linkIterator = links.iterator();
        assertEquals(0, visitedStubs.get());
        assertEquals("http://example.ca/path-0", linkIterator.next());
        assertEquals(1, visitedStubs.get());
        assertEquals("https://example.ca/path-1", linkIterator.next());

        int count = 2;
        String last = "";
        final Set<String> unique = new HashSet<>();
        unique.add("http://example.ca/path-0");
        unique.add("https://example.ca/path-1");
        while (linkIterator.hasNext()) {
            last = linkIterator.next();
            unique.add(last);
            count++;
        }
        assertEquals(4201, count);
        assertEquals("http://example.ca/path-0", last);
        assertEquals(4200, unique.size());
        assertEquals(4201, visitedStubs.get());
    }
}

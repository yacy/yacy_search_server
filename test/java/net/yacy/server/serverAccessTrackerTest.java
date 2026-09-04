package net.yacy.server;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.yacy.server.serverAccessTracker.Track;

public class serverAccessTrackerTest {

    private ConcurrentHashMap<String, Queue<Track>> histories;
    private Map<String, Queue<Track>> savedHistories;
    private final Map<Field, Object> savedSettings = new HashMap<>();

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        final Field historiesField = serverAccessTracker.class.getDeclaredField("accessTracker");
        historiesField.setAccessible(true);
        this.histories = (ConcurrentHashMap<String, Queue<Track>>) historiesField.get(null);
        this.savedHistories = new HashMap<>(this.histories);
        this.histories.clear();
        for (final String name : new String[] {"maxTrackingTime", "maxTrackingCount", "maxHostCount", "lastCleanup"}) {
            final Field field = serverAccessTracker.class.getDeclaredField(name);
            field.setAccessible(true);
            this.savedSettings.put(field, field.get(null));
            if (name.equals("lastCleanup")) field.setLong(null, System.currentTimeMillis());
        }
        serverAccessTracker.init(3600000, 1000, 100);
    }

    @After
    public void tearDown() throws Exception {
        this.histories.clear();
        this.histories.putAll(this.savedHistories);
        for (final Map.Entry<Field, Object> setting : this.savedSettings.entrySet()) {
            setting.getKey().set(null, setting.getValue());
        }
    }

    @Test
    public void burstRetainsOnlyNewestEntriesImmediately() {
        serverAccessTracker.init(3600000, 10, 100);
        for (int i = 0; i < 1000; i++) {
            serverAccessTracker.track("host", "/" + i);
            assertTrue(this.histories.get("host").size() <= 10);
        }
        final List<Track> retained = new ArrayList<>(serverAccessTracker.accessTrack("host"));
        assertEquals(10, retained.size());
        assertEquals("/990", retained.get(0).getPath());
        assertEquals("/999", retained.get(9).getPath());
    }

    @Test
    public void zeroLimitRetainsNoEntries() {
        serverAccessTracker.init(3600000, 0, 100);
        serverAccessTracker.track("host", "/");
        assertTrue(this.histories.get("host").isEmpty());
    }

    @Test
    public void historyReadsStillRemoveExpiredEntriesRegardlessOfOrder() {
        serverAccessTracker.track("host", null);
        final Queue<Track> history = this.histories.get("host");
        history.add(new Track(System.currentTimeMillis() - 7200000, "/expired"));
        serverAccessTracker.track("host", "/new");
        final Collection<Track> retained = serverAccessTracker.accessTrack("host");
        assertEquals(2, retained.size());
        assertEquals("NULL", retained.iterator().next().getPath());
        assertEquals(2, serverAccessTracker.latestAccessCount("host", 3600000));
    }

    @Test
    public void concurrentFirstRequestsShareHistoryAndRespectLimit() throws Exception {
        runConcurrentBurst(1000);
        assertEquals(800, this.histories.get("host").size());
        this.histories.clear();
        runConcurrentBurst(10);
        assertEquals(10, this.histories.get("host").size());
    }

    private void runConcurrentBurst(final int limit) throws Exception {
        serverAccessTracker.init(3600000, limit, 100);
        final ExecutorService workers = Executors.newFixedThreadPool(8);
        final CountDownLatch ready = new CountDownLatch(8);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                final int worker = i;
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int j = 0; j < 100; j++) {
                        serverAccessTracker.track("host", "/" + worker + "/" + j);
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (final Future<?> result : results) result.get(5, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

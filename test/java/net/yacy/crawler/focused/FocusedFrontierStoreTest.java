package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.crawler.retrieval.Request;

public class FocusedFrontierStoreTest {

    private static final String PROFILE_HANDLE = "123456789012";

    private static CrawlPolicyDecision decision() {
        return new CrawlPolicyDecision("astronomy", "1", CrawlPolicyDecision.Action.ACCEPT, 50,
                PolicyPriority.FOCUSED, Collections.singleton("research"), 3, 100, false,
                86400000L, Collections.singletonList("trusted-host"), NoveltyClass.NEW_HOST);
    }

    private static Request request(final String url) throws Exception {
        return new Request(new byte[] { 1, 2, 3 }, new DigestURL(url), null, "research",
                new Date(), PROFILE_HANDLE, 0, 0);
    }

    @Test
    public void frontierPersistsCandidatesAndRecoversInterruptedAdmissions() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-frontier-").toFile();
        final Request request = request("https://research.example/astronomy/start");
        final byte[] hash = request.url().hash();

        final FocusedFrontierStore first = new FocusedFrontierStore(directory, "astronomy");
        first.discover(request, decision());
        assertEquals(1, first.snapshot().discovered());
        assertEquals(1, first.snapshot().nativeLink());
        assertEquals(1, first.snapshot().recoverable());
        assertEquals(FocusedFrontierStore.DiscoverySource.NATIVE_LINK, first.get(hash).discoverySource());
        first.markAdmitted(hash);
        assertEquals(1, first.snapshot().admitted());
        assertEquals(1, first.recoverable(System.currentTimeMillis(), 10).size());
        first.markInFlight(hash);
        first.close();

        final FocusedFrontierStore restarted = new FocusedFrontierStore(directory, "astronomy");
        assertNotNull(restarted.get(hash));
        assertEquals("https://research.example/astronomy/start", restarted.get(hash).url());
        assertEquals(1, restarted.recoverStale(System.currentTimeMillis() + 601000L, 600000L));
        assertEquals(1, restarted.recoverable(System.currentTimeMillis(), 10).size());
        assertEquals(FocusedFrontierStore.State.READY, restarted.get(hash).state());
        assertEquals(PROFILE_HANDLE, restarted.recoverable(System.currentTimeMillis(), 10).get(0).request().profileHandle());
        restarted.close();
    }

    @Test
    public void bulkFrontierScansDoNotHoldTheStoreMonitor() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-frontier-lock-").toFile();
        final FocusedFrontierStore store = new FocusedFrontierStore(directory, "astronomy");
        store.discover(request("https://research.example/astronomy/one"), decision());
        final Request inFlight = request("https://research.example/astronomy/two");
        store.discover(inFlight, decision());
        store.markInFlight(inFlight.url().hash());

        assertCompletesWhileMonitorHeld(store, () -> store.snapshot());
        assertCompletesWhileMonitorHeld(store, () -> store.recoverable(System.currentTimeMillis(), 10));
        assertCompletesWhileMonitorHeld(store, () -> store.recoverStale(System.currentTimeMillis(), 600000L));
        assertCompletesWhileMonitorHeld(store, () -> store.compact(0L, 100));
        store.close();
    }

    @Test
    public void staleRecoveryCanAdvanceInBoundedSlices() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-frontier-sliced-recovery-").toFile();
        final FocusedFrontierStore store = new FocusedFrontierStore(directory, "astronomy");
        for (int i = 0; i < 7; i++) {
            final Request request = request("https://research.example/astronomy/stale-" + i);
            store.discover(request, decision());
            store.markInFlight(request.url().hash());
        }

        final long recoveryTime = System.currentTimeMillis() + 5000L;
        int recovered = 0;
        for (int i = 0; i < 10 && recovered < 7; i++) {
            final int recoveredThisSlice = store.recoverStale(recoveryTime, 1000L, 2);
            assertTrue("a slice must not recover more rows than it inspects", recoveredThisSlice <= 2);
            recovered += recoveredThisSlice;
        }
        assertEquals(7, recovered);
        assertEquals(7, store.snapshot().ready());
        store.close();
    }

    @Test
    public void discoverySourceSurvivesPersistenceAndSeedBootstrapIsObservable() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-frontier-source-").toFile();
        final Request request = request("https://research.example/astronomy/bootstrap");
        final FocusedFrontierStore store = new FocusedFrontierStore(directory, "astronomy");
        store.discover(request, decision(), FocusedFrontierStore.DiscoverySource.SEED_BOOTSTRAP);
        assertEquals(1, store.snapshot().seedBootstrap());
        assertEquals(0, store.snapshot().nativeLink());
        store.close();

        final FocusedFrontierStore restarted = new FocusedFrontierStore(directory, "astronomy");
        assertEquals(FocusedFrontierStore.DiscoverySource.SEED_BOOTSTRAP,
                restarted.get(request.url().hash()).discoverySource());
        assertEquals(1, restarted.snapshot().recoverable());
        restarted.close();
    }

    @Test
    public void indexedCandidatesAreNotRequeuedAndRetryStatesBecomeEligible() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-frontier-terminal-").toFile();
        final Request request = request("https://research.example/astronomy/terminal");
        final byte[] hash = request.url().hash();
        final FocusedFrontierStore store = new FocusedFrontierStore(directory, "astronomy");
        store.discover(request, decision());
        store.markIndexed(hash);
        assertTrue(store.recoverable(System.currentTimeMillis(), 10).isEmpty());

        store.markFailed(hash, 0L);
        assertEquals(1, store.recoverable(System.currentTimeMillis(), 10).size());
        store.markReady(hash, true);
        assertEquals(1, store.snapshot().ready());
        store.close();
    }

    private static void assertCompletesWhileMonitorHeld(final FocusedFrontierStore store,
            final Runnable operation) throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            started.countDown();
            try {
                operation.run();
            } catch (final Throwable e) {
                failure.set(e);
            } finally {
                finished.countDown();
            }
        }, "focused-frontier-scan-test");
        synchronized (store) {
            worker.start();
            assertTrue("scan worker did not start", started.await(2, TimeUnit.SECONDS));
            assertTrue("bulk frontier scan waited for the store monitor",
                    finished.await(2, TimeUnit.SECONDS));
        }
        worker.join(2000L);
        if (failure.get() != null) throw new AssertionError("bulk frontier scan failed", failure.get());
    }
}

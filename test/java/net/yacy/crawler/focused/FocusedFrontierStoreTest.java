package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;

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
        first.close();

        final FocusedFrontierStore restarted = new FocusedFrontierStore(directory, "astronomy");
        assertNotNull(restarted.get(hash));
        assertEquals("https://research.example/astronomy/start", restarted.get(hash).url());
        assertEquals(1, restarted.recoverStale(System.currentTimeMillis() + 601000L, 600000L));
        assertEquals(1, restarted.recoverable(System.currentTimeMillis(), 10).size());
        assertEquals(PROFILE_HANDLE, restarted.recoverable(System.currentTimeMillis(), 10).get(0).request().profileHandle());
        restarted.close();
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
}

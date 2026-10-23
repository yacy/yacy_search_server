package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.util.Collections;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class FocusedCrawlMetricsTest {

    @Test
    public void countersSurviveSnapshotReload() throws Exception {
        final java.io.File state = Files.createTempDirectory("focused-metrics-").toFile();
        final FocusedCrawlMetrics first = new FocusedCrawlMetrics(state);
        final CrawlPolicyDecision decision = new CrawlPolicyDecision("astronomy", "1",
                CrawlPolicyDecision.Action.ACCEPT, 50, PolicyPriority.FOCUSED,
                Collections.singleton("research"), 2, 100, false, 3600000L,
                Collections.singletonList("topic-term"));
        final CrawlPolicyContext context = new CrawlPolicyContext(
                new DigestURL("https://research.example/paper"), null, 0, "", "focused", 1, null);
        first.record(context, Collections.singletonList(decision), true);
        first.recordAdmission("astronomy");
        first.recordAdmissionRejected("astronomy");
        first.recordDiscovery("astronomy", FocusedFrontierStore.DiscoverySource.NATIVE_LINK);
        first.recordDiscovery("astronomy", FocusedFrontierStore.DiscoverySource.SEED_BOOTSTRAP);
        first.flush();

        final FocusedCrawlMetrics.Snapshot reloaded = new FocusedCrawlMetrics(state).snapshot("astronomy");
        assertEquals(1L, reloaded.queued());
        assertEquals(1L, reloaded.accepted());
        assertEquals(1L, reloaded.indexed());
        assertEquals(1L, reloaded.admitted());
        assertEquals(1L, reloaded.admissionRejected());
        assertEquals(1, reloaded.sourceHosts());
        assertEquals(2L, reloaded.discovered());
        assertEquals(1L, reloaded.nativeLinkDiscovery());
        assertEquals(1L, reloaded.seedBootstrap());
    }
}

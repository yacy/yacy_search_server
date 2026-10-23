package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.Test;

public class FocusedCrawlMetadataStoreTest {

    @Test
    public void metadataSurvivesReloadWithoutChangingQueueRows() throws Exception {
        final java.io.File state = Files.createTempDirectory("focused-metadata-").toFile();
        final CrawlPolicyDecision decision = new CrawlPolicyDecision("astronomy", "1", CrawlPolicyDecision.Action.ACCEPT,
                42, PolicyPriority.FOCUSED, new LinkedHashSet<>(Collections.singleton("research")), 2, 100, true, 3600000L,
                Arrays.asList("trusted-host", "topic-term"));
        final byte[] hash = new byte[] {1, 2, 3};
        try (FocusedCrawlMetadataStore first = new FocusedCrawlMetadataStore(state)) {
            first.record(hash, Collections.singletonList(decision));
        }

        final FocusedCrawlMetadata reloaded;
        try (FocusedCrawlMetadataStore second = new FocusedCrawlMetadataStore(state)) {
            reloaded = second.get(hash);
        }
        assertNotNull(reloaded);
        assertEquals(42, reloaded.relevanceScore());
        assertEquals("astronomy", reloaded.policyIds().get(0));
        assertTrue(reloaded.reasonCodes().contains("topic-term"));
    }

    @Test
    public void transientAndTerminalMetadataDoNotRemainInThePersistentKeyIndex() throws Exception {
        final java.io.File state = Files.createTempDirectory("focused-metadata-lifecycle-").toFile();
        final CrawlPolicyDecision decision = new CrawlPolicyDecision("astronomy", "1",
                CrawlPolicyDecision.Action.ACCEPT, 42, PolicyPriority.FOCUSED,
                new LinkedHashSet<>(Collections.singleton("research")), 2, 100, true, 3600000L,
                Collections.singletonList("trusted-host"));
        final byte[] hash = new byte[] {4, 5, 6};
        try (FocusedCrawlMetadataStore store = new FocusedCrawlMetadataStore(state)) {
            store.remember(hash, Collections.singletonList(decision));
            assertEquals(0, store.statusJSON().optInt("records", -1));
            store.record(hash, Collections.singletonList(decision));
            assertEquals(1, store.statusJSON().optInt("records", -1));
            store.forget(hash);
            assertEquals(0, store.statusJSON().optInt("records", -1));
            assertNull(store.get(hash));
        }
    }
}

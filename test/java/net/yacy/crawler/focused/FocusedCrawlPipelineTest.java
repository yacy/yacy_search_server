package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import net.yacy.cora.document.id.DigestURL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises policy hooks, parent-score inheritance and indexed metadata as one profile pipeline. */
public class FocusedCrawlPipelineTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static JSONObject profile() throws Exception {
        return new JSONObject()
                .put("id", "astronomy")
                .put("version", "3")
                .put("description", "Neutral astronomy research profile")
                .put("enabled", true)
                .put("seeds", new JSONArray())
                .put("trustedHosts", new JSONArray())
                .put("tlds", new JSONArray())
                .put("excludedHosts", new JSONArray())
                .put("limits", new JSONObject().put("queueTarget", 10).put("refillBelow", 5)
                        .put("hardMaximum", 20).put("maximumDepth", 4).put("perHostBudget", 100))
                .put("scoring", new JSONObject()
                        .put("parentInheritanceDivisor", 3)
                        .put("controlledExploration", false)
                        .put("thresholds", new JSONObject().put("probable", 10).put("focused", 20))
                        .put("rules", new JSONArray().put(new JSONObject().put("id", "nebula-topic")
                                .put("score", 30).put("terms", new JSONArray().put("nebula")))))
                .put("collections", new JSONObject()
                        .put("astronomy-reference", new JSONObject()
                                .put("terms", new JSONArray().put("spectroscopy"))
                                .put("recrawlMinutes", 43200)));
    }

    @Test
    public void hooksAssignCollectionsAndPersistExplainableMetadata() throws Exception {
        final Path application = this.temporaryFolder.newFolder("application").toPath();
        final Path data = this.temporaryFolder.newFolder("data").toPath();
        final Path profiles = application.resolve("defaults/focused/policies");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("astronomy.json"), profile().toString(), StandardCharsets.UTF_8);

        final DigestURL parentURL = new DigestURL("https://research.example/nebula-survey");
        final DigestURL childURL = new DigestURL("https://archive.example/papers/42");
        try (FocusedCrawlPolicyManager manager = new FocusedCrawlPolicyManager(application.toFile(), data.toFile())) {
            final CrawlPolicyContext parentContext = new CrawlPolicyContext(parentURL, null, 0,
                    "nebula survey", "neutral", 0, null);
            final List<CrawlPolicyDecision> parentDecisions = manager.preFetch(parentContext);
            assertEquals(1, parentDecisions.size());
            assertEquals(PolicyPriority.FOCUSED, parentDecisions.get(0).priority());
            manager.recordPreFetch(parentURL.hash(), parentDecisions);

            final int inheritedScore = manager.parentScore(parentURL.hash());
            assertEquals(30, inheritedScore);
            final CrawlPolicyContext childContext = new CrawlPolicyContext(childURL, parentURL, inheritedScore,
                    "related paper", "neutral", 1, null);
            final List<CrawlPolicyDecision> childPreFetch = manager.preFetch(childContext);
            final CrawlPolicyDecision inherited = childPreFetch.get(0);
            assertTrue(inherited.reasonCodes().contains("parent-score"));
            assertEquals(PolicyPriority.PROBABLE, inherited.priority());
            manager.recordPreFetch(childURL.hash(), childPreFetch);

            final CrawlPolicyContext parsedContext = childContext.withParsedContent(
                    new CrawlPolicyContext.ParsedContent("Nebula spectroscopy results",
                            Collections.singletonMap("publisher", "Observatory archive"), "en",
                            "A neutral astronomy paper.", Collections.emptyMap(), "journal"));
            final List<CrawlPolicyDecision> postFetch = manager.postFetch(parsedContext);
            assertEquals(Collections.singleton("astronomy-reference"), postFetch.get(0).collections());
            manager.metadataStore().record(childURL.hash(), postFetch);
            manager.recordIndexed(childURL.hash());
            final FocusedCrawlMetadata stored = manager.metadataStore().get(childURL.hash());
            assertNotNull(stored);
            assertEquals(Collections.singleton("astronomy-reference"), stored.collections());
            assertEquals(40, stored.relevanceScore());
            assertTrue(stored.reasonCodes().contains("nebula-topic"));
        }

        try (FocusedCrawlPolicyManager restarted = new FocusedCrawlPolicyManager(application.toFile(), data.toFile())) {
            final FocusedCrawlMetadata restored = restarted.metadataStore().get(childURL.hash());
            assertNotNull(restored);
            assertEquals("astronomy", restored.policyIds().get(0));
            assertEquals(3, Integer.parseInt(restored.policyVersions().get(0)));
            assertTrue(restored.collections().contains("astronomy-reference"));
            assertFalse(restarted.loadErrors().containsKey("astronomy.json"));
        }
    }

    @Test
    public void disabledProfilesLeaveOrdinaryCrawlWithoutPolicyDecisions() throws Exception {
        final Path application = this.temporaryFolder.newFolder("ordinary-application").toPath();
        final Path data = this.temporaryFolder.newFolder("ordinary-data").toPath();
        final Path profiles = application.resolve("defaults/focused/policies");
        Files.createDirectories(profiles);
        final JSONObject disabled = profile().put("enabled", false);
        Files.writeString(profiles.resolve("astronomy.json"), disabled.toString(), StandardCharsets.UTF_8);

        try (FocusedCrawlPolicyManager manager = new FocusedCrawlPolicyManager(application.toFile(), data.toFile())) {
            final List<CrawlPolicyDecision> decisions = manager.preFetch(new CrawlPolicyContext(
                    new DigestURL("https://ordinary.example/page"), null, 0, "ordinary page", "ordinary", 0, null));
            assertTrue(decisions.isEmpty());
            assertEquals(PolicyPriority.ORDINARY, FocusedCrawlPolicyManager.best(decisions).priority());
        }
    }
}

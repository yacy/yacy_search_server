package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class FocusedPolicyConfigurationTest {

    private static JSONObject configuration(final boolean enabled) throws Exception {
        return new JSONObject()
                .put("id", "astronomy")
                .put("version", "1")
                .put("description", "Neutral astronomy research profile")
                .put("enabled", enabled)
                .put("trustedHosts", Arrays.asList("research.example"))
                .put("tlds", Arrays.asList("edu"))
                .put("scoring", new JSONObject()
                        .put("thresholds", new JSONObject().put("probable", 10).put("focused", 25))
                        .put("rules", Arrays.asList(new JSONObject()
                                .put("id", "astronomy-term")
                                .put("score", 15)
                                .put("terms", Arrays.asList("astronomy", "telescope")))))
                .put("collections", new JSONObject()
                        .put("research", new JSONObject().put("terms", Arrays.asList("astronomy"))))
                .put("limits", new JSONObject().put("maximumDepth", 3).put("explorationPercent", 10)
                        .put("seedBatchSize", 3).put("refillBatchSize", 12000));
    }

    @Test
    public void jsonConfigurationIsValidatedAndExposed() throws Exception {
        final JSONObject json = configuration(false);
        json.getJSONObject("limits").put("recrawlFocused", true);
        final PolicyConfiguration configuration = PolicyConfiguration.fromJSON(json);
        assertEquals("astronomy", configuration.id());
        assertEquals(3, configuration.limits().maximumDepth());
        assertEquals(3, configuration.limits().seedBatchSize());
        assertEquals(12000, configuration.limits().refillBatchSize());
        assertTrue(configuration.limits().recrawlFocused());
        assertTrue(!configuration.enabled());
    }

    @Test
    public void refillBatchHasSafeBackwardCompatibleDefaultAndBoundedConfiguration() throws Exception {
        final JSONObject legacy = configuration(false);
        legacy.getJSONObject("limits").remove("refillBatchSize");
        assertEquals(PolicyConfiguration.Limits.DEFAULT_REFILL_BATCH_SIZE,
                PolicyConfiguration.fromJSON(legacy).limits().refillBatchSize());

        legacy.getJSONObject("limits").put("refillBatchSize", 1000000);
        assertEquals(100000, PolicyConfiguration.fromJSON(legacy).limits().refillBatchSize());

        legacy.getJSONObject("limits").put("refillBatchSize", 0);
        assertEquals(1, PolicyConfiguration.fromJSON(legacy).limits().refillBatchSize());
    }

    @Test
    public void queueWatermarksMustBeOrdered() throws Exception {
        final JSONObject refillAboveTarget = configuration(false).put("limits", new JSONObject()
                .put("queueTarget", 100).put("refillBelow", 101).put("hardMaximum", 200));
        try {
            PolicyConfiguration.fromJSON(refillAboveTarget);
            fail("refillBelow above queueTarget must be rejected");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("refillBelow"));
        }

        final JSONObject targetAboveMaximum = configuration(false).put("limits", new JSONObject()
                .put("queueTarget", 201).put("refillBelow", 100).put("hardMaximum", 200));
        try {
            PolicyConfiguration.fromJSON(targetAboveMaximum);
            fail("queueTarget above hardMaximum must be rejected");
        } catch (final IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("queueTarget"));
        }
    }

    @Test
    public void zeroQueueWatermarksRemainValidForAProfileWithoutQueueRefill() throws Exception {
        final PolicyConfiguration parsed = PolicyConfiguration.fromJSON(configuration(false).put("limits", new JSONObject()
                .put("queueTarget", 0).put("refillBelow", 0).put("hardMaximum", 0)));
        assertEquals(0, parsed.limits().queueTarget());
        assertEquals(0, parsed.limits().refillBelow());
        assertEquals(0, parsed.limits().hardMaximum());
    }

    @Test
    public void frontierRecoveryDefaultsToDisabledAndIsConfigurable() throws Exception {
        final JSONObject json = configuration(false).put("frontier", new JSONObject()
                .put("enabled", true).put("seedMode", "initial-only")
                .put("recoveryGraceSeconds", 90).put("maxEntries", 5000)
                .put("terminalRetentionDays", 14));
        final PolicyConfiguration configuration = PolicyConfiguration.fromJSON(json);
        assertTrue(configuration.frontier().enabled());
        assertEquals("initial-only", configuration.frontier().seedMode());
        assertEquals(90000L, configuration.frontier().recoveryGraceMillis());
        assertEquals(5000, configuration.frontier().maxEntries());
        assertEquals(14, configuration.frontier().terminalRetentionDays());
        assertTrue(!PolicyConfiguration.fromJSON(configuration(false)).frontier().enabled());
    }

    @Test
    public void disabledPolicyPreservesOrdinaryCrawling() throws Exception {
        final PolicyConfiguration configuration = PolicyConfiguration.fromJSON(configuration(false));
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(configuration,
                Files.createTempDirectory("focused-policy-").toFile());
        final CrawlPolicyDecision decision = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy"), null, 0,
                "astronomy telescope", "ordinary", 0, null));
        assertEquals(CrawlPolicyDecision.Action.ACCEPT, decision.action());
        assertEquals(PolicyPriority.ORDINARY, decision.priority());
    }

    @Test
    public void parsedContentGetsCollectionAndExplainableReasons() throws Exception {
        final PolicyConfiguration configuration = PolicyConfiguration.fromJSON(configuration(true));
        assertTrue(configuration.enabled());
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(configuration,
                Files.createTempDirectory("focused-policy-").toFile());
        final CrawlPolicyContext context = new CrawlPolicyContext(
                new DigestURL("https://research.example/paper"), null, 0,
                "", "focused", 1,
                new CrawlPolicyContext.ParsedContent("Astronomy paper", Collections.emptyMap(), "en",
                        "A telescope observation and astronomy research paper", Collections.emptyMap(), "academic"));
        final CrawlPolicyDecision decision = policy.postFetch(context);
        assertEquals(CrawlPolicyDecision.Action.ACCEPT, decision.action());
        assertEquals(PolicyPriority.FOCUSED, decision.priority());
        assertTrue(decision.collections().contains("research"));
        assertTrue(decision.reasonCodes().contains("trusted-host"));
    }

    @Test
    public void minimumTermMatchesRejectsIncidentalExternalMention() throws Exception {
        final JSONObject json = configuration(true);
        json.getJSONObject("scoring").put("rules", new JSONArray().put(new JSONObject()
                .put("id", "astronomy-term").put("score", 15)
                .put("minimumTermMatches", 2).put("terms", new JSONArray(Arrays.asList("astronomy", "telescope")))));
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(
                PolicyConfiguration.fromJSON(json), Files.createTempDirectory("focused-policy-").toFile());
        final CrawlPolicyDecision decision = policy.postFetch(new CrawlPolicyContext(
                new DigestURL("https://unrelated.example/paper"), null, 0, "", "ordinary", 1,
                new CrawlPolicyContext.ParsedContent("Astronomy only", Collections.emptyMap(), "en",
                        "An astronomy paper", Collections.emptyMap(), "academic")));
        assertEquals(CrawlPolicyDecision.Action.DEFER, decision.action());
        assertEquals(PolicyPriority.ORDINARY, decision.priority());
        assertTrue(decision.collections().isEmpty());
    }

    @Test
    public void noveltyPrefersNewHostsAndPathsThenKeepsSaturatedUrlsInExpansionLane() throws Exception {
        final JSONObject json = configuration(true).put("novelty", new JSONObject()
                .put("enabled", true)
                .put("newHostWeight", 45)
                .put("newPathWeight", 35)
                .put("expansionWeight", 15)
                .put("freshnessWeight", 5)
                .put("pathPrefixDepth", 2)
                .put("externalRequiresDirectEvidence", true));
        json.getJSONObject("limits").put("recrawlFocused", true).put("defaultRecrawlMinutes", 1);
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(
                PolicyConfiguration.fromJSON(json), Files.createTempDirectory("focused-novelty-policy-").toFile());

        final CrawlPolicyDecision newHost = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/introduction"), null, 0,
                "astronomy telescope", "focused", 0, null));
        assertEquals(NoveltyClass.NEW_HOST, newHost.noveltyClass());
        assertTrue(newHost.focused());

        final CrawlPolicyDecision newPath = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/results"), null, 0,
                "astronomy telescope", "focused", 0, null));
        assertEquals(NoveltyClass.NEW_PATH, newPath.noveltyClass());
        assertTrue(newPath.focused());

        final CrawlPolicyDecision provenExpansion = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/introduction/related"), null, 0,
                "astronomy telescope", "focused", 0, null));
        assertEquals(NoveltyClass.PROVEN_EXPANSION, provenExpansion.noveltyClass());
        assertTrue(provenExpansion.focused());

        final CrawlPolicyDecision freshness = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/introduction"), null, 0,
                "astronomy telescope", "focused", 0, null,
                System.currentTimeMillis() - 120000L));
        assertEquals(NoveltyClass.FRESHNESS, freshness.noveltyClass());
        assertTrue(freshness.focused());

        final CrawlPolicyDecision saturated = policy.preFetch(new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/introduction"), null, 0,
                "astronomy telescope", "focused", 0, null, System.currentTimeMillis()));
        assertEquals(NoveltyClass.SATURATED, saturated.noveltyClass());
        assertTrue(saturated.noveltyClass().focused());
        assertEquals(PolicyPriority.FOCUSED, saturated.priority());
        assertEquals(CrawlPolicyDecision.Action.ACCEPT, saturated.action());
        assertTrue(saturated.reasonCodes().contains("novelty-saturated"));
        assertTrue(saturated.focused());
    }

    @Test
    public void strongParentsMayUseConfiguredExternalExplorationBudget() throws Exception {
        final JSONObject json = configuration(true).put("novelty", new JSONObject()
                .put("enabled", true)
                .put("externalRequiresDirectEvidence", true)
                .put("explorationCollection", "research"));
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(
                PolicyConfiguration.fromJSON(json), Files.createTempDirectory("focused-exploration-policy-").toFile());

        CrawlPolicyDecision exploration = null;
        for (int i = 0; i < 1000 && exploration == null; i++) {
            final CrawlPolicyDecision candidate = policy.preFetch(new CrawlPolicyContext(
                    new DigestURL("https://adjacent-" + i + ".example/discovery"), null, 25,
                    "", "focused", 1, null));
            if (candidate.priority() == PolicyPriority.EXPLORATION) exploration = candidate;
        }

        assertTrue(exploration != null);
        assertEquals(CrawlPolicyDecision.Action.DEFER, exploration.action());
        assertTrue(exploration.collections().contains("research"));
        assertTrue(exploration.reasonCodes().contains("external-exploration"));
        assertTrue(exploration.reasonCodes().contains("exploration-budget"));
    }

    @Test
    public void probableParentsMayUseLowPriorityExternalExploration() throws Exception {
        final JSONObject json = configuration(true).put("novelty", new JSONObject()
                .put("enabled", true)
                .put("externalRequiresDirectEvidence", true)
                .put("explorationCollection", "research"));
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(
                PolicyConfiguration.fromJSON(json), Files.createTempDirectory("focused-probable-exploration-").toFile());

        CrawlPolicyDecision exploration = null;
        for (int i = 0; i < 1000 && exploration == null; i++) {
            final CrawlPolicyDecision candidate = policy.preFetch(new CrawlPolicyContext(
                    new DigestURL("https://probable-adjacent-" + i + ".example/discovery"), null, 10,
                    "", "focused", 1, null));
            if (candidate.priority() == PolicyPriority.EXPLORATION) exploration = candidate;
        }

        assertTrue(exploration != null);
        assertEquals(CrawlPolicyDecision.Action.DEFER, exploration.action());
        assertTrue(exploration.collections().contains("research"));
        assertTrue(exploration.reasonCodes().contains("external-exploration"));
    }
}

package net.yacy.crawler.focused;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.nio.file.Files;

import org.json.JSONObject;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class FocusedURLAdmissionStateTest {

    @Test
    public void ruleBasedPolicyRejectsAnAlreadyAdmittedFocusedCandidate() throws Exception {
        final JSONObject json = new JSONObject()
                .put("id", "astronomy")
                .put("version", "1")
                .put("enabled", true)
                .put("trustedHosts", Arrays.asList("research.example"))
                .put("scoring", new JSONObject()
                        .put("thresholds", new JSONObject().put("probable", 10).put("focused", 25))
                        .put("rules", Arrays.asList(new JSONObject().put("id", "astronomy-term")
                                .put("score", 15).put("terms", Arrays.asList("astronomy")))))
                .put("collections", new JSONObject()
                        .put("research", new JSONObject().put("terms", Arrays.asList("astronomy"))))
                .put("novelty", new JSONObject().put("enabled", true).put("repeatSuppressionMinutes", 60));
        final RuleBasedCrawlPolicy policy = new RuleBasedCrawlPolicy(PolicyConfiguration.fromJSON(json),
                Files.createTempDirectory("focused-admission-policy-").toFile());
        final CrawlPolicyContext context = new CrawlPolicyContext(
                new DigestURL("https://research.example/astronomy/paper"), null, 0,
                "astronomy", "focused", 1, null);

        final CrawlPolicyDecision first = policy.preFetch(context);
        assertEquals(CrawlPolicyDecision.Action.ACCEPT, first.action());
        assertTrue(first.focused());
        policy.recordAdmission(context, first);

        final CrawlPolicyDecision repeated = policy.preFetch(context);
        assertEquals(CrawlPolicyDecision.Action.REJECT, repeated.action());
        assertTrue(repeated.reasonCodes().contains("focused-admission-duplicate"));
    }

    @Test
    public void admissionStateSuppressesRepeatedAttemptsAcrossRestart() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-admission-").toFile();
        final DigestURL url = new DigestURL("https://research.example/astronomy/paper");
        final CrawlPolicyContext context = new CrawlPolicyContext(url, null, 0, "astronomy", "focused", 1, null);
        final FocusedURLAdmissionState first = new FocusedURLAdmissionState(directory, "astronomy");

        assertFalse(first.shouldSuppress(url.hash(), "1", context, 0L, false, 3600000L));
        first.recordAdmission(url, "1");
        assertTrue(first.shouldSuppress(url.hash(), "1", context, 0L, false, 3600000L));
        first.close();

        final FocusedURLAdmissionState afterRestart = new FocusedURLAdmissionState(directory, "astronomy");
        assertTrue(afterRestart.shouldSuppress(url.hash(), "1", context, 0L, false, 3600000L));
        assertFalse(afterRestart.shouldSuppress(url.hash(), "2", context, 0L, false, 3600000L));
        afterRestart.close();
    }

    @Test
    public void indexedUrlIsAllowedWhenOrdinaryProfileRecrawlIsDue() throws Exception {
        final java.io.File directory = Files.createTempDirectory("focused-admission-due-").toFile();
        final DigestURL url = new DigestURL("https://research.example/astronomy/paper");
        final CrawlPolicyContext pending = new CrawlPolicyContext(url, null, 0, "astronomy", "focused", 1, null);
        final FocusedURLAdmissionState state = new FocusedURLAdmissionState(directory, "astronomy");
        state.recordAdmission(url, "1");

        final CrawlPolicyContext due = new CrawlPolicyContext(url, null, 0, "astronomy", "focused", 1, null,
                System.currentTimeMillis(), true);
        assertFalse(state.shouldSuppress(url.hash(), "1", due, 0L, false, 3600000L));
        assertTrue(state.shouldSuppress(url.hash(), "1", pending, 0L, false, 3600000L));
        state.close();
    }
}

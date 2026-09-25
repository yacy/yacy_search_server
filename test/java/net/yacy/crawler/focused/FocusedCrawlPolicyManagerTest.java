package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/** Integration-level checks for independent profile loading and hot reload. */
public class FocusedCrawlPolicyManagerTest {

    private static JSONObject profile(final String id, final boolean enabled) throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("version", "1")
                .put("description", "Neutral " + id + " profile")
                .put("enabled", enabled)
                .put("seeds", new JSONArray())
                .put("trustedHosts", new JSONArray().put(id + ".example"))
                .put("tlds", new JSONArray().put("example"))
                .put("scoring", new JSONObject()
                        .put("thresholds", new JSONObject().put("probable", 10).put("focused", 20))
                        .put("rules", new JSONArray().put(new JSONObject()
                                .put("id", id + "-term").put("score", 20)
                                .put("terms", new JSONArray().put(id)))))
                .put("collections", new JSONObject()
                        .put(id, new JSONObject().put("terms", new JSONArray().put(id))))
                .put("limits", new JSONObject()
                        .put("queueTarget", 10).put("refillBelow", 5).put("hardMaximum", 20));
    }

    @Test
    public void profilesAreIndependentAndReloadable() throws Exception {
        final Path application = Files.createTempDirectory("focused-application-");
        final Path data = Files.createTempDirectory("focused-data-");
        final Path profiles = application.resolve("defaults/focused/policies");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("astronomy.json"), profile("astronomy", true).toString(),
                StandardCharsets.UTF_8);
        Files.writeString(profiles.resolve("fiction.json"), profile("fiction", false).toString(),
                StandardCharsets.UTF_8);

        final FocusedCrawlPolicyManager manager = new FocusedCrawlPolicyManager(application.toFile(), data.toFile());
        try {
            assertEquals(2, manager.policies().size());
            assertEquals(1, manager.enabledPolicies().size());
            assertNotNull(manager.exportProfile("astronomy"));
            assertTrue(manager.exportProfile("astronomy").getBoolean("enabled"));
            assertFalse(manager.exportProfile("fiction").getBoolean("enabled"));

            Files.writeString(profiles.resolve("fiction.json"), profile("fiction", true).toString(),
                    StandardCharsets.UTF_8);
            manager.reload();
            assertEquals(2, manager.enabledPolicies().size());
            assertTrue(manager.loadErrors().isEmpty());
        } finally {
            manager.close();
        }
    }
}

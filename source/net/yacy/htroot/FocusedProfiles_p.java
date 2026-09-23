// Focused Autocrawler Profiles administration and API endpoint.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.htroot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.crawler.focused.FocusedCrawlPolicy;
import net.yacy.crawler.focused.FocusedCrawlPolicyManager;
import net.yacy.crawler.focused.FocusedCrawlScheduler;
import net.yacy.crawler.focused.PolicyConfiguration;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/** Admin UI plus JSON/XML status, import, export, validation and reload API. */
public class FocusedProfiles_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final FocusedCrawlPolicyManager manager = sb.focusedCrawlPolicyManager;
        prop.putHTML("message", "");
        prop.putHTML("error", "");

        if (post != null && hasMutation(post)) {
            if (sb.adminAuthenticated(header) < 2) {
                prop.authenticationRequired();
                return prop;
            }
            try {
                if (post.containsKey("reload")) {
                    manager.reload();
                    if (sb.focusedCrawlScheduler != null) sb.focusedCrawlScheduler.reloadProfiles();
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Focused profiles reloaded");
                } else if (post.containsKey("pause")) {
                    sb.setConfig("focused.autocrawler.paused", true);
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Focused profile scheduling paused");
                } else if (post.containsKey("resume")) {
                    sb.setConfig("focused.autocrawler.paused", false);
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Focused profile scheduling resumed");
                } else if (post.containsKey("validate")) {
                    final PolicyConfiguration parsed = parseProfile(post.get("configuration", ""));
                    prop.putHTML("message", "Valid profile: " + parsed.id() + " v" + parsed.version());
                } else if (post.containsKey("saveProfile") || post.containsKey("importProfile")) {
                    saveProfile(sb, post.get("configuration", ""));
                    manager.reload();
                    if (sb.focusedCrawlScheduler != null) sb.focusedCrawlScheduler.reloadProfiles();
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Focused profile saved and reloaded");
                } else if (post.containsKey("setupProfile")) {
                    saveProfile(sb, quickSetupProfile(manager, post));
                    manager.reload();
                    if (sb.focusedCrawlScheduler != null) sb.focusedCrawlScheduler.reloadProfiles();
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Basic focused profile saved and reloaded");
                } else if (post.containsKey("setEnabled")) {
                    setEnabled(sb, post.get("id", ""), post.getBoolean("enabled"));
                    manager.reload();
                    if (sb.focusedCrawlScheduler != null) sb.focusedCrawlScheduler.reloadProfiles();
                    sb.initFocusedAutocrawl();
                    prop.putHTML("message", "Focused profile state changed");
                } else if (post.containsKey("resetFrontier")) {
                    if (sb.focusedCrawlScheduler == null) throw new IllegalArgumentException("focused scheduler is unavailable");
                    final FocusedCrawlScheduler.ResetReport report = sb.focusedCrawlScheduler.resetProfile(post.get("id", ""));
                    prop.putHTML("message", "Frontier reset for " + report.profile() + ": snapshotted "
                            + report.snapshotted() + ", removed " + report.removed()
                            + ", recoverable frontier entries " + report.frontierSize());
                }
            } catch (final IOException | JSONException | IllegalArgumentException e) {
                prop.putHTML("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }

        final String selected = post == null ? "" : post.get("id", "");
        final JSONObject exported = selected.isEmpty() ? null : manager.exportProfile(selected);
        prop.putHTML("configuration", exported == null ? "" : exported.toString());
        final JSONObject setup = exported == null ? new JSONObject() : exported;
        prop.putHTML("setup_id", setup.optString("id", ""));
        prop.putHTML("setup_description", setup.optString("description", ""));
        prop.putHTML("setup_seeds", joinLines(setup.optJSONArray("seeds")));
        final JSONObject limits = setup.optJSONObject("limits");
        prop.put("setup_queue_target", limits == null ? "0" : limits.optString("queueTarget", "0"));
        prop.put("setup_refill_below", limits == null ? "0" : limits.optString("refillBelow", "0"));
        prop.put("setup_hard_maximum", limits == null ? "0" : limits.optString("hardMaximum", "0"));
        prop.put("setup_storage_target", limits == null ? "0" : limits.optString("storageTargetBytes", "0"));
        prop.put("setup_exploration", limits == null ? "10" : limits.optString("explorationPercent", "10"));
        final JSONObject schedulerStatus = sb.focusedCrawlScheduler == null
                ? new JSONObject(true) : sb.focusedCrawlScheduler.statusJSON();
        prop.putJSON("status", schedulerStatus.toString());
        final JSONObject resourceStatus = schedulerStatus.optJSONObject("resource");
        final JSONObject pdfStatus = schedulerStatus.optJSONObject("pdfLane");
        prop.putHTML("resource_pause_cause", resourceStatus == null ? "" : resourceStatus.optString("pauseCause", ""));
        prop.put("resource_paused", resourceStatus != null && resourceStatus.optBoolean("paused", false));
        prop.put("resource_memory_available", resourceStatus == null ? 0L : resourceStatus.optLong("memoryAvailable", 0L));
        prop.put("resource_memory_threshold", resourceStatus == null ? 0L : resourceStatus.optLong("memoryThreshold", 0L));
        prop.put("resource_healthy_checks", resourceStatus == null ? 0 : resourceStatus.optInt("healthyChecks", 0));
        prop.put("pdf_active", pdfStatus == null ? 0 : pdfStatus.optInt("active", 0));
        prop.put("pdf_admitted", pdfStatus == null ? 0L : pdfStatus.optLong("admitted", 0L));
        prop.put("pdf_completed", pdfStatus == null ? 0L : pdfStatus.optLong("completed", 0L));
        prop.put("pdf_blocked", pdfStatus == null ? 0L : pdfStatus.optLong("blocked", 0L));
        prop.put("paused", sb.getConfigBool("focused.autocrawler.paused", false));
        prop.put("profile_count", manager.policies().size());
        final Map<String, FocusedCrawlScheduler.ProfileStatus> statusById = new HashMap<>();
        if (sb.focusedCrawlScheduler != null) {
            for (final FocusedCrawlScheduler.ProfileStatus status : sb.focusedCrawlScheduler.statuses()) statusById.put(status.id(), status);
        }
        int index = 0;
        for (final FocusedCrawlPolicy policy : manager.policies()) {
            final PolicyConfiguration configuration = policy.configuration();
            prop.putHTML("profiles_" + index + "_id", configuration.id());
            prop.putHTML("profiles_" + index + "_version", configuration.version());
            prop.putHTML("profiles_" + index + "_description", configuration.description());
            prop.put("profiles_" + index + "_enabled", configuration.enabled());
            prop.put("profiles_" + index + "_nextEnabled", Boolean.toString(!configuration.enabled()));
            prop.putHTML("profiles_" + index + "_action", configuration.enabled() ? "Disable" : "Enable");
            final FocusedCrawlScheduler.ProfileStatus status = statusById.get(configuration.id());
            prop.put("profiles_" + index + "_queue", status == null ? 0 : status.queueSize());
            prop.put("profiles_" + index + "_target", status == null ? 0 : status.queueTarget());
            prop.putHTML("profiles_" + index + "_lastReason", status == null ? "" : status.lastReason());
            final net.yacy.crawler.focused.FocusedFrontierStore.Snapshot frontier = status == null ? null : status.frontier();
            prop.put("profiles_" + index + "_frontierRecoverable", frontier == null ? 0 : frontier.recoverable());
            prop.put("profiles_" + index + "_frontierNativeLink", frontier == null ? 0 : frontier.nativeLink());
            prop.put("profiles_" + index + "_frontierSeedBootstrap", frontier == null ? 0 : frontier.seedBootstrap());
            index++;
        }
        prop.put("load_error_count", manager.loadErrors().size());
        int errorIndex = 0;
        for (final java.util.Map.Entry<String, String> error : manager.loadErrors().entrySet()) {
            prop.putHTML("errors_" + errorIndex + "_file", error.getKey());
            prop.putHTML("errors_" + errorIndex + "_message", error.getValue());
            errorIndex++;
        }
        return prop;
    }

    private static boolean hasMutation(final serverObjects post) {
        return post.containsKey("reload") || post.containsKey("pause") || post.containsKey("resume")
                || post.containsKey("validate") || post.containsKey("saveProfile")
                || post.containsKey("importProfile") || post.containsKey("setupProfile") || post.containsKey("setEnabled")
                || post.containsKey("resetFrontier");
    }

    private static String quickSetupProfile(final FocusedCrawlPolicyManager manager, final serverObjects post) throws JSONException {
        final String id = post.get("setup_id", "").trim();
        if (id.isEmpty() || !id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("profile id must contain only letters, numbers, '.', '_' or '-'");
        final JSONObject json = manager.exportProfile(id) == null ? new JSONObject(true) : manager.exportProfile(id);
        json.put("id", id);
        json.put("version", json.optString("version", "1"));
        json.put("description", post.get("setup_description", ""));
        json.put("enabled", json.optBoolean("enabled", false));
        json.put("seeds", lines(post.get("setup_seeds", "")));
        if (!json.has("trustedHosts")) json.put("trustedHosts", new JSONArray());
        if (!json.has("tlds")) json.put("tlds", new JSONArray());
        if (!json.has("excludedHosts")) json.put("excludedHosts", new JSONArray());
        if (!json.has("scoring")) json.put("scoring", new JSONObject(true).put("rules", new JSONArray()));
        if (!json.has("collections")) json.put("collections", new JSONObject(true));
        final JSONObject limits = json.optJSONObject("limits") == null ? new JSONObject(true) : json.optJSONObject("limits");
        limits.put("queueTarget", nonNegativeInt(post, "setup_queue_target", 0));
        limits.put("refillBelow", nonNegativeInt(post, "setup_refill_below", 0));
        limits.put("hardMaximum", nonNegativeInt(post, "setup_hard_maximum", 0));
        limits.put("storageTargetBytes", nonNegativeLong(post, "setup_storage_target", 0L));
        limits.put("explorationPercent", Math.min(100, nonNegativeInt(post, "setup_exploration", 10)));
        json.put("limits", limits);
        return json.toString();
    }

    private static JSONArray lines(final String value) {
        final JSONArray result = new JSONArray();
        if (value == null) return result;
        for (final String line : value.split("\\R")) if (!line.trim().isEmpty()) result.put(line.trim());
        return result;
    }

    private static String joinLines(final JSONArray values) {
        if (values == null) return "";
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (result.length() > 0) result.append(System.lineSeparator());
            result.append(values.optString(i, ""));
        }
        return result.toString();
    }

    private static int nonNegativeInt(final serverObjects post, final String key, final int defaultValue) {
        try { return Math.max(0, Integer.parseInt(post.get(key, Integer.toString(defaultValue)))); }
        catch (final NumberFormatException e) { throw new IllegalArgumentException(key + " must be a non-negative integer"); }
    }

    private static long nonNegativeLong(final serverObjects post, final String key, final long defaultValue) {
        try { return Math.max(0L, Long.parseLong(post.get(key, Long.toString(defaultValue)))); }
        catch (final NumberFormatException e) { throw new IllegalArgumentException(key + " must be a non-negative integer"); }
    }

    private static void saveProfile(final Switchboard sb, final String configuration) throws IOException, JSONException {
        final JSONObject json = new JSONObject(new JSONTokener(configuration));
        final PolicyConfiguration parsed = PolicyConfiguration.fromJSON(json);
        final File directory = new File(sb.getDataPath(), "DATA/SETTINGS/focused/policies");
        Files.createDirectories(directory.toPath());
        final File target = profileFile(directory, parsed.id());
        backup(target, sb);
        final File temporary = new File(directory, target.getName() + ".tmp");
        Files.writeString(temporary.toPath(), json.toString(2) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static PolicyConfiguration parseProfile(final String configuration) throws JSONException {
        if (configuration == null || configuration.trim().isEmpty()) throw new IllegalArgumentException("configuration is required");
        return PolicyConfiguration.fromJSON(new JSONObject(new JSONTokener(configuration)));
    }

    private static void setEnabled(final Switchboard sb, final String id, final boolean enabled) throws IOException, JSONException {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("profile id is required");
        final FocusedCrawlPolicyManager manager = sb.focusedCrawlPolicyManager;
        final JSONObject json = manager.exportProfile(id);
        if (json == null) throw new IllegalArgumentException("unknown focused profile " + id);
        json.put("enabled", enabled);
        saveProfile(sb, json.toString());
    }

    private static File profileFile(final File directory, final String id) {
        if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("invalid profile id");
        return new File(directory, id + ".json");
    }

    private static void backup(final File file, final Switchboard sb) throws IOException {
        if (!file.isFile()) return;
        final File directory = new File(sb.getDataPath(), "DATA/SETTINGS/focused/backups/" + Instant.now().toString().replace(':', '-'));
        Files.createDirectories(directory.toPath());
        Files.copy(file.toPath(), new File(directory, file.getName()).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }
}

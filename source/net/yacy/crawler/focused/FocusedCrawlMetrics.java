// Persistent operational metrics for focused autocrawler profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Small, crash-tolerant counters used by the profile status API. The index is
 * authoritative for document fields; this file only records scheduler and
 * policy activity. The current snapshot is replaced atomically so an
 * interrupted write cannot corrupt the previous measurement and startup does
 * not have to replay an ever-growing history log.
 */
public final class FocusedCrawlMetrics {
    private static final int MAX_HOSTS_PER_PROFILE = 10000;
    private static final int FLUSH_EVERY = 64;

    public static final class Snapshot {
        private final long queued;
        private final long accepted;
        private final long rejected;
        private final long deferred;
        private final long indexed;
        private final long duplicate;
        private final long failed;
        private final long newCandidates;
        private final long recrawlCandidates;
        private final long newHost;
        private final long newPath;
        private final long expansion;
        private final long freshness;
        private final long newIndexed;
        private final long recrawledIndexed;
        private final long ordinaryFallback;
        private final long externalRejected;
        private final long admitted;
        private final long admissionRejected;
        private final long exploration;
        private final long discovered;
        private final long seedBootstrap;
        private final long nativeLinkDiscovery;
        private final long adminDiscovery;
        private final int sourceHosts;
        private final long lastUpdated;

        private Snapshot(final Counters counters) {
            this.queued = counters.queued;
            this.accepted = counters.accepted;
            this.rejected = counters.rejected;
            this.deferred = counters.deferred;
            this.indexed = counters.indexed;
            this.duplicate = counters.duplicate;
            this.failed = counters.failed;
            this.newCandidates = counters.newCandidates;
            this.recrawlCandidates = counters.recrawlCandidates;
            this.newHost = counters.newHost;
            this.newPath = counters.newPath;
            this.expansion = counters.expansion;
            this.freshness = counters.freshness;
            this.newIndexed = counters.newIndexed;
            this.recrawledIndexed = counters.recrawledIndexed;
            this.ordinaryFallback = counters.ordinaryFallback;
            this.externalRejected = counters.externalRejected;
            this.admitted = counters.admitted;
            this.admissionRejected = counters.admissionRejected;
            this.exploration = counters.exploration;
            this.discovered = counters.discovered;
            this.seedBootstrap = counters.seedBootstrap;
            this.nativeLinkDiscovery = counters.nativeLinkDiscovery;
            this.adminDiscovery = counters.adminDiscovery;
            this.sourceHosts = counters.hosts.size();
            this.lastUpdated = counters.lastUpdated;
        }

        public JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("queued", this.queued).put("accepted", this.accepted)
                        .put("rejected", this.rejected).put("deferred", this.deferred)
                        .put("indexed", this.indexed).put("duplicate", this.duplicate)
                        .put("failed", this.failed).put("sourceHosts", this.sourceHosts)
                        .put("newCandidates", this.newCandidates).put("recrawlCandidates", this.recrawlCandidates)
                        .put("newHost", this.newHost).put("newPath", this.newPath)
                        .put("expansion", this.expansion).put("freshness", this.freshness)
                        .put("newIndexed", this.newIndexed).put("recrawledIndexed", this.recrawledIndexed)
                        .put("ordinaryFallback", this.ordinaryFallback).put("externalRejected", this.externalRejected)
                        .put("admitted", this.admitted).put("admissionRejected", this.admissionRejected)
                        .put("exploration", this.exploration)
                        .put("discovered", this.discovered).put("seedBootstrap", this.seedBootstrap)
                        .put("nativeLinkDiscovery", this.nativeLinkDiscovery)
                        .put("adminDiscovery", this.adminDiscovery)
                        .put("lastUpdated", this.lastUpdated);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize focused metrics", e);
            }
            return json;
        }

        public long queued() { return this.queued; }
        public long accepted() { return this.accepted; }
        public long rejected() { return this.rejected; }
        public long deferred() { return this.deferred; }
        public long indexed() { return this.indexed; }
        public long duplicate() { return this.duplicate; }
        public long failed() { return this.failed; }
        public long newCandidates() { return this.newCandidates; }
        public long recrawlCandidates() { return this.recrawlCandidates; }
        public long newHost() { return this.newHost; }
        public long newPath() { return this.newPath; }
        public long expansion() { return this.expansion; }
        public long freshness() { return this.freshness; }
        public long newIndexed() { return this.newIndexed; }
        public long recrawledIndexed() { return this.recrawledIndexed; }
        public long ordinaryFallback() { return this.ordinaryFallback; }
        public long externalRejected() { return this.externalRejected; }
        public long admitted() { return this.admitted; }
        public long admissionRejected() { return this.admissionRejected; }
        public long exploration() { return this.exploration; }
        public long discovered() { return this.discovered; }
        public long seedBootstrap() { return this.seedBootstrap; }
        public long nativeLinkDiscovery() { return this.nativeLinkDiscovery; }
        public long adminDiscovery() { return this.adminDiscovery; }
        public int sourceHosts() { return this.sourceHosts; }
        public long lastUpdated() { return this.lastUpdated; }
    }

    private static final class Counters {
        private long queued;
        private long accepted;
        private long rejected;
        private long deferred;
        private long indexed;
        private long duplicate;
        private long failed;
        private long newCandidates;
        private long recrawlCandidates;
        private long newHost;
        private long newPath;
        private long expansion;
        private long freshness;
        private long newIndexed;
        private long recrawledIndexed;
        private long ordinaryFallback;
        private long externalRejected;
        private long admitted;
        private long admissionRejected;
        private long exploration;
        private long discovered;
        private long seedBootstrap;
        private long nativeLinkDiscovery;
        private long adminDiscovery;
        private long lastUpdated;
        private final Set<String> hosts = new LinkedHashSet<>();
    }

    private final File file;
    private final Map<String, Counters> counters = new LinkedHashMap<>();
    private int pendingWrites;

    public FocusedCrawlMetrics(final File stateDirectory) {
        this.file = new File(stateDirectory, "metrics.snapshot.json");
        load();
    }

    public synchronized void record(final CrawlPolicyContext context, final List<CrawlPolicyDecision> decisions,
            final boolean indexed) {
        if (decisions == null || decisions.isEmpty()) return;
        final String host = context == null ? "" : context.host();
        final boolean knownInIndex = context != null && context.knownInIndex();
        final long now = System.currentTimeMillis();
        for (final CrawlPolicyDecision decision : decisions) {
            if (decision == null || decision.policyId().isEmpty()) continue;
            final Counters value = this.counters.computeIfAbsent(decision.policyId(), ignored -> new Counters());
            value.queued++;
            if (decision.action() == CrawlPolicyDecision.Action.REJECT) value.rejected++;
            else value.accepted++;
            if (decision.action() == CrawlPolicyDecision.Action.DEFER) value.deferred++;
            if (!indexed) {
                if (decision.priority() == PolicyPriority.EXPLORATION) value.exploration++;
                switch (decision.noveltyClass()) {
                    case NEW_HOST: value.newHost++; value.newCandidates++; break;
                    case NEW_PATH: value.newPath++; value.newCandidates++; break;
                    case PROVEN_EXPANSION: value.expansion++; if (!knownInIndex) value.newCandidates++; break;
                    case FRESHNESS: value.freshness++; value.recrawlCandidates++; break;
                    default: if (decision.priority() == PolicyPriority.ORDINARY) value.ordinaryFallback++; break;
                }
                if (decision.reasonCodes().contains("external-no-direct-evidence")) value.externalRejected++;
            } else if (decision.action() != CrawlPolicyDecision.Action.REJECT) {
                value.indexed++;
                if (knownInIndex) value.recrawledIndexed++;
                else value.newIndexed++;
            }
            if (!host.isEmpty() && value.hosts.size() < MAX_HOSTS_PER_PROFILE) value.hosts.add(host);
            value.lastUpdated = now;
        }
        if (++this.pendingWrites >= FLUSH_EVERY) flush();
    }

    public synchronized void recordDuplicate(final String policyId) {
        increment(policyId, "duplicate");
    }

    public synchronized void recordAdmission(final String policyId) {
        increment(policyId, "admitted");
    }

    public synchronized void recordAdmissionRejected(final String policyId) {
        increment(policyId, "admissionRejected");
    }

    /** Record a URL entering the durable frontier before native admission. */
    public synchronized void recordDiscovery(final String policyId,
            final FocusedFrontierStore.DiscoverySource source) {
        if (policyId == null || policyId.isEmpty()) return;
        final Counters value = this.counters.computeIfAbsent(policyId, ignored -> new Counters());
        value.discovered++;
        if (source == FocusedFrontierStore.DiscoverySource.SEED_BOOTSTRAP) value.seedBootstrap++;
        else if (source == FocusedFrontierStore.DiscoverySource.ADMIN_RESET) value.adminDiscovery++;
        else if (source == FocusedFrontierStore.DiscoverySource.NATIVE_LINK) value.nativeLinkDiscovery++;
        value.lastUpdated = System.currentTimeMillis();
        if (++this.pendingWrites >= FLUSH_EVERY) flush();
    }

    public synchronized void recordFailure(final String policyId) {
        increment(policyId, "failed");
    }

    private void increment(final String policyId, final String field) {
        if (policyId == null || policyId.isEmpty()) return;
        final Counters value = this.counters.computeIfAbsent(policyId, ignored -> new Counters());
        if ("duplicate".equals(field)) value.duplicate++;
        if ("failed".equals(field)) value.failed++;
        if ("admitted".equals(field)) value.admitted++;
        if ("admissionRejected".equals(field)) value.admissionRejected++;
        value.lastUpdated = System.currentTimeMillis();
        if (++this.pendingWrites >= FLUSH_EVERY) flush();
    }

    public synchronized Snapshot snapshot(final String policyId) {
        final Counters value = this.counters.get(policyId);
        return new Snapshot(value == null ? new Counters() : value);
    }

    public synchronized Map<String, Snapshot> snapshots() {
        final Map<String, Snapshot> result = new LinkedHashMap<>();
        for (final Map.Entry<String, Counters> entry : this.counters.entrySet()) result.put(entry.getKey(), new Snapshot(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    public synchronized void flush() {
        if (this.counters.isEmpty()) return;
        try {
            Files.createDirectories(this.file.getParentFile().toPath());
            final JSONObject snapshot = new JSONObject(true);
            snapshot.put("timestamp", System.currentTimeMillis());
            final JSONArray profiles = new JSONArray();
            for (final Map.Entry<String, Counters> entry : this.counters.entrySet()) {
                final JSONObject value = new Snapshot(entry.getValue()).toJSON();
                value.put("id", entry.getKey());
                value.put("hosts", new JSONArray(entry.getValue().hosts));
                profiles.put(value);
            }
            snapshot.put("profiles", profiles);
            final File temporary = new File(this.file.getParentFile(), this.file.getName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(snapshot.toString());
                writer.newLine();
            }
            try {
                Files.move(temporary.toPath(), this.file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (final java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            this.pendingWrites = 0;
        } catch (final IOException | JSONException ignored) { }
    }

    private void load() {
        if (!this.file.isFile()) return;
        try (BufferedReader reader = Files.newBufferedReader(this.file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    final JSONObject snapshot = new JSONObject(line);
                    final JSONArray profiles = snapshot.optJSONArray("profiles");
                    if (profiles == null) continue;
                    for (int i = 0; i < profiles.length(); i++) loadProfile(profiles.getJSONObject(i));
                } catch (JSONException | RuntimeException ignored) { }
            }
        } catch (IOException ignored) { }
    }

    private void loadProfile(final JSONObject json) {
        final String id = json.optString("id", "");
        if (id.isEmpty()) return;
        final Counters value = new Counters();
        value.queued = json.optLong("queued", 0L);
        value.accepted = json.optLong("accepted", 0L);
        value.rejected = json.optLong("rejected", 0L);
        value.deferred = json.optLong("deferred", 0L);
        value.indexed = json.optLong("indexed", 0L);
        value.duplicate = json.optLong("duplicate", 0L);
        value.failed = json.optLong("failed", 0L);
        value.newCandidates = json.optLong("newCandidates", 0L);
        value.recrawlCandidates = json.optLong("recrawlCandidates", 0L);
        value.newHost = json.optLong("newHost", 0L);
        value.newPath = json.optLong("newPath", 0L);
        value.expansion = json.optLong("expansion", 0L);
        value.freshness = json.optLong("freshness", 0L);
        value.newIndexed = json.optLong("newIndexed", 0L);
        value.recrawledIndexed = json.optLong("recrawledIndexed", 0L);
        value.ordinaryFallback = json.optLong("ordinaryFallback", 0L);
        value.externalRejected = json.optLong("externalRejected", 0L);
        value.admitted = json.optLong("admitted", 0L);
        value.admissionRejected = json.optLong("admissionRejected", 0L);
        value.exploration = json.optLong("exploration", 0L);
        value.discovered = json.optLong("discovered", 0L);
        value.seedBootstrap = json.optLong("seedBootstrap", 0L);
        value.nativeLinkDiscovery = json.optLong("nativeLinkDiscovery", 0L);
        value.adminDiscovery = json.optLong("adminDiscovery", 0L);
        value.lastUpdated = json.optLong("lastUpdated", 0L);
        final JSONArray hosts = json.optJSONArray("hosts");
        if (hosts != null) for (int i = 0; i < hosts.length() && value.hosts.size() < MAX_HOSTS_PER_PROFILE; i++) {
            final String host = hosts.optString(i, "");
            if (!host.isEmpty()) value.hosts.add(host);
        }
        this.counters.put(id, value);
    }
}

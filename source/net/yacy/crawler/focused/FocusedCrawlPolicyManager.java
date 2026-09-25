// Loader and evaluator for multiple independent focused profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.crawler.retrieval.Request;

/** Hot-reloadable registry of focused autocrawler profiles. */
public final class FocusedCrawlPolicyManager implements AutoCloseable {

    private final File applicationDirectory;
    private final File dataDirectory;
    private final File stateDirectory;
    private final FocusedCrawlMetadataStore metadataStore;
    private final FocusedCrawlMetrics metrics;
    private final Map<String, FocusedFrontierStore> frontiers = new LinkedHashMap<>();
    private final Map<String, Long> recoveryHashes = new ConcurrentHashMap<>();
    private final Map<String, FocusedCrawlPolicy> policies = new LinkedHashMap<>();
    private final Map<String, String> loadErrors = new LinkedHashMap<>();

    public FocusedCrawlPolicyManager(final File applicationDirectory, final File dataDirectory) {
        this.applicationDirectory = applicationDirectory;
        this.dataDirectory = dataDirectory;
        this.stateDirectory = new File(dataDirectory, "DATA/SETTINGS/focused/state");
        this.metadataStore = new FocusedCrawlMetadataStore(this.stateDirectory);
        this.metrics = new FocusedCrawlMetrics(this.stateDirectory);
        reload();
    }

    public synchronized void reload() {
        final Map<String, FocusedCrawlPolicy> loaded = new LinkedHashMap<>();
        final Map<String, String> errors = new LinkedHashMap<>();
        loadDirectory(new File(this.applicationDirectory, "defaults/focused/policies"), loaded, errors);
        loadDirectory(new File(this.dataDirectory, "DATA/SETTINGS/focused/policies"), loaded, errors);
        this.policies.clear();
        this.policies.putAll(loaded);
        this.loadErrors.clear();
        this.loadErrors.putAll(errors);
    }

    private void loadDirectory(final File directory, final Map<String, FocusedCrawlPolicy> target) {
        loadDirectory(directory, target, this.loadErrors);
    }

    private void loadDirectory(final File directory, final Map<String, FocusedCrawlPolicy> target,
            final Map<String, String> errors) {
        if (!directory.isDirectory()) return;
        final File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        for (final File file : files) {
            try {
                final PolicyConfiguration configuration = PolicyConfiguration.load(file);
                target.put(configuration.id(), new RuleBasedCrawlPolicy(configuration,
                        new File(this.dataDirectory, "DATA/SETTINGS/focused/state")));
            } catch (IOException | JSONException | IllegalArgumentException e) {
                errors.put(file.getName(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
    }

    public synchronized Collection<FocusedCrawlPolicy> policies() {
        return Collections.unmodifiableCollection(new ArrayList<>(this.policies.values()));
    }

    public synchronized Collection<FocusedCrawlPolicy> enabledPolicies() {
        final List<FocusedCrawlPolicy> result = new ArrayList<>();
        for (final FocusedCrawlPolicy policy : this.policies.values()) if (policy.configuration().enabled()) result.add(policy);
        return Collections.unmodifiableList(result);
    }

    public synchronized FocusedCrawlPolicy policy(final String id) { return this.policies.get(id); }

    public synchronized JSONObject exportProfile(final String id) {
        final FocusedCrawlPolicy policy = this.policies.get(id);
        return policy == null ? null : policy.configuration().toJSON();
    }

    public synchronized Map<String, String> loadErrors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.loadErrors));
    }

    public FocusedCrawlMetadataStore metadataStore() { return this.metadataStore; }

    public FocusedCrawlMetrics metrics() { return this.metrics; }

    /** Close persistent sidecar stores during YaCy shutdown. */
    @Override
    public synchronized void close() {
        for (final FocusedFrontierStore frontier : this.frontiers.values()) frontier.close();
        this.frontiers.clear();
        this.metrics.flush();
        this.metadataStore.close();
    }

    /** Get the persistent URL frontier for one focused profile. */
    public synchronized FocusedFrontierStore frontier(final String policyId) {
        if (policyId == null || policyId.isEmpty()) return null;
        FocusedFrontierStore frontier = this.frontiers.get(policyId);
        if (frontier == null) {
            frontier = new FocusedFrontierStore(this.stateDirectory, policyId);
            this.frontiers.put(policyId, frontier);
        }
        return frontier;
    }

    /** Record a focused candidate before native YaCy queue admission. */
    public void recordDiscovered(final Request request, final List<CrawlPolicyDecision> decisions) {
        if (request == null || decisions == null) return;
        final FocusedFrontierStore.DiscoverySource source = discoverySource(request);
        for (final CrawlPolicyDecision decision : decisions) {
            if (decision != null && decision.focused() && frontierEnabled(decision.policyId())) {
                final FocusedFrontierStore frontier = frontier(decision.policyId());
                if (frontier != null) {
                    if (frontier.discover(request, decision, source)) {
                        this.metrics.recordDiscovery(decision.policyId(), source);
                    }
                }
            }
        }
    }

    /** Classify the origin without adding a second fetch path. */
    private static FocusedFrontierStore.DiscoverySource discoverySource(final Request request) {
        final String name = request.name();
        return request.referrerhash() == null && request.depth() == 0
                && name != null && name.startsWith("Focused profile ")
                ? FocusedFrontierStore.DiscoverySource.SEED_BOOTSTRAP
                : FocusedFrontierStore.DiscoverySource.NATIVE_LINK;
    }

    public void recordPreFetch(final byte[] urlHash, final List<CrawlPolicyDecision> decisions) {
        this.metadataStore.record(urlHash, decisions);
    }

    /** Record successful native queue admission for each matching profile. */
    public void recordAdmission(final CrawlPolicyContext context, final List<CrawlPolicyDecision> decisions) {
        if (context == null || decisions == null || decisions.isEmpty()) return;
        for (final FocusedCrawlPolicy policy : enabledPolicies()) {
            for (final CrawlPolicyDecision decision : decisions) {
                if (decision != null && policy.configuration().id().equals(decision.policyId())) {
                    policy.recordAdmission(context, decision);
                    if (decision.action() != CrawlPolicyDecision.Action.REJECT && decision.focused()) {
                        this.metrics.recordAdmission(decision.policyId());
                    }
                    final FocusedFrontierStore frontier = frontierEnabled(policy.configuration().id())
                            ? frontier(policy.configuration().id()) : null;
                    if (frontier != null) frontier.markAdmitted(context.url().hash());
                    clearRecovery(context.url().hash());
                    break;
                }
            }
        }
    }

    /** Record a native queue push rejected after policy evaluation. */
    public void recordAdmissionRejected(final List<CrawlPolicyDecision> decisions) {
        if (decisions == null) return;
        for (final CrawlPolicyDecision decision : decisions) {
            if (decision != null && decision.action() != CrawlPolicyDecision.Action.REJECT && decision.focused()) {
                this.metrics.recordAdmissionRejected(decision.policyId());
            }
        }
    }

    public int parentScore(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = this.metadataStore.get(urlHash);
        if (metadata == null) return 0;
        int score = 0;
        final Collection<FocusedCrawlPolicy> enabled = enabledPolicies();
        for (int i = 0; i < metadata.policyIds().size(); i++) {
            final String id = metadata.policyIds().get(i);
            final String version = i < metadata.policyVersions().size() ? metadata.policyVersions().get(i) : "";
            for (final FocusedCrawlPolicy policy : enabled) {
                if (policy.configuration().id().equals(id) && policy.configuration().version().equals(version)) {
                    score = Math.max(score, metadata.relevanceScore());
                    break;
                }
            }
        }
        return score;
    }

    public void recordDuplicate(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = this.metadataStore.get(urlHash);
        if (metadata == null) return;
        for (final String id : metadata.policyIds()) {
            this.metrics.recordDuplicate(id);
            final FocusedFrontierStore frontier = frontierEnabled(id) ? frontier(id) : null;
            if (frontier != null) frontier.markDuplicate(urlHash);
        }
        clearRecovery(urlHash);
    }

    public void recordFailure(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = this.metadataStore.get(urlHash);
        if (metadata == null) return;
        for (final String id : metadata.policyIds()) {
            this.metrics.recordFailure(id);
            final FocusedFrontierStore frontier = frontierEnabled(id) ? frontier(id) : null;
            if (frontier != null) frontier.markFailed(urlHash, 3600000L);
        }
        clearRecovery(urlHash);
    }

    /** Mark a native worker request as in flight. */
    public void recordInFlight(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = this.metadataStore.get(urlHash);
        if (metadata == null) return;
        for (final String id : metadata.policyIds()) {
            final FocusedFrontierStore frontier = frontierEnabled(id) ? frontier(id) : null;
            if (frontier != null) frontier.markInFlight(urlHash);
        }
    }

    /** Mark a successfully stored document as terminal for frontier recovery. */
    public void recordIndexed(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = this.metadataStore.get(urlHash);
        if (metadata == null) return;
        for (final String id : metadata.policyIds()) {
            final FocusedFrontierStore frontier = frontierEnabled(id) ? frontier(id) : null;
            if (frontier != null) frontier.markIndexed(urlHash);
        }
        clearRecovery(urlHash);
    }

    public boolean frontierEnabled(final String policyId) {
        final FocusedCrawlPolicy policy = policy(policyId);
        return policy != null && policy.configuration().frontier().enabled();
    }

    /** Mark a candidate submitted by recovery so repeat-admission suppression can yield once. */
    public void markRecovery(final byte[] urlHash) {
        if (urlHash != null) this.recoveryHashes.put(ASCIIKey(urlHash), System.currentTimeMillis());
    }

    public boolean isRecovery(final byte[] urlHash) {
        if (urlHash == null) return false;
        final Long marked = this.recoveryHashes.get(ASCIIKey(urlHash));
        return marked != null && System.currentTimeMillis() - marked < 3600000L;
    }

    private void clearRecovery(final byte[] urlHash) {
        if (urlHash != null) this.recoveryHashes.remove(ASCIIKey(urlHash));
    }

    private static String ASCIIKey(final byte[] hash) {
        return java.util.Base64.getEncoder().encodeToString(hash);
    }

    public List<CrawlPolicyDecision> preFetch(final CrawlPolicyContext context) {
        final List<CrawlPolicyDecision> decisions = new ArrayList<>();
        for (final FocusedCrawlPolicy policy : enabledPolicies()) decisions.add(policy.preFetch(context));
        this.metrics.record(context, decisions, false);
        return decisions;
    }

    public List<CrawlPolicyDecision> postFetch(final CrawlPolicyContext context) {
        final List<CrawlPolicyDecision> decisions = new ArrayList<>();
        for (final FocusedCrawlPolicy policy : enabledPolicies()) decisions.add(policy.postFetch(context));
        this.metrics.record(context, decisions, true);
        return decisions;
    }

    public static CrawlPolicyDecision best(final List<CrawlPolicyDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) return CrawlPolicyDecision.ordinary("no-policy");
        return decisions.stream().max(Comparator.comparingInt(CrawlPolicyDecision::relevanceScore)
                .thenComparingInt(value -> value.priority().rank())).orElse(CrawlPolicyDecision.ordinary("no-policy"));
    }

    public static Set<String> collections(final List<CrawlPolicyDecision> decisions) {
        final Set<String> result = new LinkedHashSet<>();
        if (decisions != null) for (final CrawlPolicyDecision decision : decisions) result.addAll(decision.collections());
        return result;
    }
}

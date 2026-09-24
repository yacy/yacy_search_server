// Persistent scheduler for Focused Autocrawler Profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.CrawlStacker;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.NoticedURL.StackType;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.schema.CollectionSchema;

/**
 * Maintains multiple configured focused profiles without introducing a second
 * fetch path. It only submits root requests to YaCy's normal CrawlStacker;
 * robots, host balancing, link extraction, parsing and indexing remain native.
 */
public final class FocusedCrawlScheduler {
    private static final int FRONTIER_MAINTENANCE_SCAN_ROWS_PER_CYCLE = 64;
    private static final long FRONTIER_COMPACTION_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;

    private static final ConcurrentLog LOG = new ConcurrentLog("FocusedCrawlScheduler");

    public enum Lane { FOCUSED, ORDINARY }

    /** Result of an explicit profile-scoped frontier reset. */
    public static final class ResetReport {
        private final String profile;
        private final int snapshotted;
        private final int removed;
        private final int frontierSize;

        private ResetReport(final String profile, final int snapshotted, final int removed, final int frontierSize) {
            this.profile = profile;
            this.snapshotted = snapshotted;
            this.removed = removed;
            this.frontierSize = frontierSize;
        }

        public String profile() { return this.profile; }
        public int snapshotted() { return this.snapshotted; }
        public int removed() { return this.removed; }
        public int frontierSize() { return this.frontierSize; }
    }

    /** Deterministic weighted fair selector used by the crawler queue. */
    public static final class WeightedLaneSelector {
        private long dispatches;

        public synchronized Lane choose(final boolean focusedAvailable, final boolean ordinaryAvailable,
                final int focusedWeight, final int ordinaryWeight) {
            final int ordinary = Math.max(0, ordinaryWeight);
            if (!focusedAvailable) return ordinaryAvailable && ordinary > 0 ? Lane.ORDINARY : null;
            if (!ordinaryAvailable) return Lane.FOCUSED;
            final int focused = Math.max(0, focusedWeight);
            if (ordinary == 0) return Lane.FOCUSED;
            final int cycle = Math.max(1, focused + ordinary);
            return (this.dispatches++ % cycle) < focused ? Lane.FOCUSED : Lane.ORDINARY;
        }

        public synchronized long dispatches() { return this.dispatches; }
    }

    public static final class ProfileStatus {
        private final String id;
        private final String version;
        private final boolean enabled;
        private final int queueSize;
        private final int queueTarget;
        private final int refillBelow;
        private final int hardMaximum;
        private final int seeds;
        private final long lastRun;
        private final long lastEnqueued;
        private final String lastReason;
        private final FocusedCrawlMetrics.Snapshot metrics;
        private final FocusedFrontierStore.Snapshot frontier;

        private ProfileStatus(final String id, final String version, final boolean enabled, final int queueSize,
                final PolicyConfiguration.Limits limits, final int seeds, final Properties state,
                final FocusedCrawlMetrics.Snapshot metrics, final FocusedFrontierStore.Snapshot frontier) {
            this.id = id;
            this.version = version;
            this.enabled = enabled;
            this.queueSize = queueSize;
            this.queueTarget = limits.queueTarget();
            this.refillBelow = limits.refillBelow();
            this.hardMaximum = limits.hardMaximum();
            this.seeds = seeds;
            this.lastRun = parseLong(state, "lastRun", 0L);
            this.lastEnqueued = parseLong(state, "lastEnqueued", 0L);
            this.lastReason = state.getProperty("lastReason", "");
            this.metrics = metrics;
            this.frontier = frontier;
        }

        public JSONObject toJSON() {
            final JSONObject json = new JSONObject(true);
            try {
                json.put("id", this.id).put("version", this.version).put("enabled", this.enabled)
                        .put("queueSize", this.queueSize).put("queueTarget", this.queueTarget)
                        .put("refillBelow", this.refillBelow).put("hardMaximum", this.hardMaximum)
                        .put("seeds", this.seeds).put("lastRun", this.lastRun)
                        .put("lastEnqueued", this.lastEnqueued).put("lastReason", this.lastReason)
                        .put("metrics", this.metrics == null ? new JSONObject(true) : this.metrics.toJSON());
                final JSONObject frontierJSON = new JSONObject(true);
                if (this.frontier != null) {
                    frontierJSON.put("total", this.frontier.total()).put("discovered", this.frontier.discovered())
                            .put("ready", this.frontier.ready()).put("admitted", this.frontier.admitted())
                            .put("inFlight", this.frontier.inFlight()).put("indexed", this.frontier.indexed())
                            .put("duplicate", this.frontier.duplicate()).put("failed", this.frontier.failed())
                            .put("blocked", this.frontier.blocked()).put("recoverable", this.frontier.recoverable())
                            .put("seedBootstrap", this.frontier.seedBootstrap())
                            .put("nativeLink", this.frontier.nativeLink())
                            .put("adminReset", this.frontier.adminReset())
                            .put("legacy", this.frontier.legacy());
                }
                json.put("frontier", frontierJSON);
            } catch (final JSONException e) {
                throw new IllegalStateException("cannot serialize focused profile status", e);
            }
            return json;
        }

        public String id() { return this.id; }
        public String version() { return this.version; }
        public boolean enabled() { return this.enabled; }
        public int queueSize() { return this.queueSize; }
        public int queueTarget() { return this.queueTarget; }
        public int refillBelow() { return this.refillBelow; }
        public int hardMaximum() { return this.hardMaximum; }
        public long lastRun() { return this.lastRun; }
        public long lastEnqueued() { return this.lastEnqueued; }
        public String lastReason() { return this.lastReason; }
        public FocusedCrawlMetrics.Snapshot metrics() { return this.metrics; }
        public FocusedFrontierStore.Snapshot frontier() { return this.frontier; }
    }

    private final Switchboard sb;
    private final CrawlStacker stacker;
    private final FocusedCrawlPolicyManager manager;
    private final File stateDirectory;
    private final File reportFile;
    private final File resourceStateFile;
    private final Properties resourceState = new Properties();
    private final FocusedResourceGuard resourceGuard = new FocusedResourceGuard();
    private final Map<String, Properties> states = new LinkedHashMap<>();
    private final Map<String, CrawlProfile> profiles = new LinkedHashMap<>();
    private final Map<String, Long> frontierCompactions = new LinkedHashMap<>();
    private final FileChannel lockChannel;
    private final FileLock processLock;

    public FocusedCrawlScheduler(final Switchboard sb, final FocusedCrawlPolicyManager manager) {
        this.sb = sb;
        this.stacker = sb.crawlStacker;
        this.manager = manager;
        this.stateDirectory = new File(sb.getDataPath(), "DATA/SETTINGS/focused/state");
        this.reportFile = new File(this.stateDirectory, "scheduler.jsonl");
        this.resourceStateFile = new File(this.stateDirectory, "resource.properties");
        FileChannel channel = null;
        FileLock lock = null;
        try {
            Files.createDirectories(this.stateDirectory.toPath());
            if (this.resourceStateFile.isFile()) {
                try (BufferedReader reader = Files.newBufferedReader(this.resourceStateFile.toPath(), StandardCharsets.UTF_8)) {
                    this.resourceState.load(reader);
                }
            }
            channel = FileChannel.open(new File(this.stateDirectory, "scheduler.lock").toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = channel.tryLock();
            } catch (final OverlappingFileLockException ignored) { }
        } catch (final IOException e) {
            LOG.warn("Focused scheduler persistence is unavailable", e);
        }
        this.lockChannel = channel;
        this.processLock = lock;
    }

    /** Run one control cycle. Safe to call from YaCy's BusyThread. */
    public synchronized boolean job() {
        if (!isOwner()) return false;
        recoverResourceObserverPause();
        final Collection<FocusedCrawlPolicy> policies = this.manager.enabledPolicies();
        if (!policies.isEmpty() && pauseFocusedCrawlForMemoryPressure()) return false;
        // Respect both operator pauses and resource pauses. In particular, do
        // not refill a native queue while a focused memory pause is waiting for
        // its recovery headroom; queued requests also consume heap.
        if (this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) return false;
        boolean enqueued = false;
        applyNoveltyWeights(policies);
        for (final FocusedCrawlPolicy policy : policies) {
            if (this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL)) break;
            final PolicyConfiguration configuration = policy.configuration();
            final Properties state = state(configuration.id());
            final CrawlProfile profile = profile(configuration);
            final FocusedFrontierStore frontier = this.manager.frontierEnabled(configuration.id())
                    ? this.manager.frontier(configuration.id()) : null;
            if (frontier != null) {
                // Inspect a small slice on every control tick. A full MapHeap sweep
                // over a large on-disk frontier used to issue hundreds of thousands
                // of disk-backed lookups in one refill and contend with queue updates.
                frontier.recoverStale(System.currentTimeMillis(),
                        configuration.frontier().recoveryGraceMillis(), FRONTIER_MAINTENANCE_SCAN_ROWS_PER_CYCLE);
            }
            final int current = queueSize(profile);
            if (current < 0) {
                save(state, -1, -1, 0, "queue measurement unavailable");
                continue;
            }
            final int target = Math.max(0, configuration.limits().queueTarget());
            final int refillBelow = Math.min(target, Math.max(0, configuration.limits().refillBelow()));
            final int hardMaximum = Math.max(target, configuration.limits().hardMaximum());
            if (current >= refillBelow) {
                save(state, current, current, 0, "queue watermark");
                continue;
            }
            if (!healthy(configuration)) {
                save(state, current, current, 0, "resource guard");
                continue;
            }
            final long lastRefill = parseLong(state, "lastRefill", 0L);
            final long refillInterval = Math.max(30000L, configuration.limits().controlIntervalMillis());
            if (lastRefill > 0L && System.currentTimeMillis() - lastRefill < refillInterval) {
                save(state, current, current, 0, "refill throttle");
                continue;
            }
            final int budget = Math.max(0, Math.min(target - current, hardMaximum - current));
            final int refillBudget = Math.min(budget, configuration.limits().refillBatchSize());
            if (frontier != null) {
                final long now = System.currentTimeMillis();
                final long lastCompaction = this.frontierCompactions.computeIfAbsent(configuration.id(), id -> now);
                if (now - lastCompaction >= FRONTIER_COMPACTION_INTERVAL_MILLIS) {
                    frontier.compact(now - configuration.frontier().terminalRetentionDays() * 86400000L,
                            configuration.frontier().maxEntries());
                    this.frontierCompactions.put(configuration.id(), now);
                }
                if (frontier.size() > 0) state.setProperty("frontierStarted", "true");
                final int recovered = refillFrontier(configuration, profile, frontier, refillBudget);
                final int indexedExpansion = configuration.frontier().expandIndexedSources()
                        ? expandFromIndexedSources(configuration, profile, frontier, refillBudget - recovered, state) : 0;
                if (recovered + indexedExpansion > 0) {
                    boolean memoryPressure = !memoryHealthy();
                    if (memoryPressure) {
                        pauseFocusedCrawlForMemoryPressure();
                        memoryPressure = !memoryHealthy();
                    }
                    // Avoid another full queue/frontier scan after pausing on
                    // low headroom; the persisted native queues remain intact.
                    final int after = memoryPressure ? -1 : queueSize(profile);
                    final String refillReason = memoryPressure ? "resource guard after bounded refill"
                            : indexedExpansion > 0 ? "frontier recovery + indexed source expansion" : "frontier recovery";
                    save(state, current, after, recovered + indexedExpansion, refillReason);
                    enqueued = true;
                    continue;
                }
                if (budget > 0 && ("initial-only".equals(configuration.frontier().seedMode())
                        || "disabled".equals(configuration.frontier().seedMode()))
                        && (frontier.size() > 0
                                || Boolean.parseBoolean(state.getProperty("frontierStarted", "false")))) {
                    save(state, current, current, 0, "frontier exhausted");
                    continue;
                }
            }
            final List<String> seeds = configuration.seeds();
            if (budget == 0 || seeds.isEmpty() || !seedsAllowed(configuration, state, frontier)) {
                save(state, current, current, 0, budget == 0 ? "queue watermark" : "no seeds");
                continue;
            }
            final int cursor = intValue(state, "seedCursor", 0);
            int added = 0;
            // Root submission is bounded by the distinct configured roots and
            // an optional per-cycle batch size. The persisted cursor rotates
            // through the catalogue instead of submitting the complete root
            // set on every refill. The normal crawler, not repeated copies of
            // the same roots, must grow the queue through native link
            // extraction.
            final int configuredSeedBatch = configuration.limits().seedBatchSize();
            final int rootBatch = Math.min(refillBudget, Math.min(seeds.size(),
                    configuredSeedBatch > 0 ? configuredSeedBatch : seeds.size()));
            for (int i = 0; i < rootBatch; i++) {
                final String seed = seeds.get(Math.floorMod(cursor + i, seeds.size()));
                try {
                    final DigestURL url = new DigestURL(seed);
                    final String anchor = "Focused profile " + configuration.id();
                    final Request request = new Request(this.sb.peers.mySeed().hash.getBytes(), url, null, anchor,
                            new Date(), profile.handle(), 0, profile.timezoneOffset());
                    if (configuration.limits().refreshSeeds()) {
                        // A completed broad crawl commonly leaves the
                        // configured roots in the local index. Process the
                        // roots synchronously through the native stacker with
                        // a temporary future recrawl cutoff. This refreshes
                        // only the configured focused roots; it does not
                        // delete index data and does not create a second fetch
                        // path.
                        final long originalRecrawl = profile.recrawlIfOlder();
                        profile.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key,
                                Long.toString(System.currentTimeMillis() + 1000L));
                        try {
                            this.stacker.process(request);
                        } finally {
                            profile.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key,
                                    Long.toString(originalRecrawl));
                        }
                    } else {
                        this.stacker.enqueueEntry(request);
                    }
                    added++;
                } catch (final Exception e) {
                    LOG.warn("Cannot enqueue focused seed " + seed + " for profile " + configuration.id(), e);
                }
            }
            state.setProperty("seedCursor", Integer.toString(Math.floorMod(cursor + added, Math.max(1, seeds.size()))));
            if (added > 0 && frontier != null && "initial-only".equals(configuration.frontier().seedMode())) {
                state.setProperty("frontierStarted", "true");
            }
            state.setProperty("lastRefill", Long.toString(System.currentTimeMillis()));
            final int after = queueSize(profile);
            save(state, current, after, added, "refill");
            enqueued |= added > 0;
        }
        return enqueued;
    }

    private int refillFrontier(final PolicyConfiguration configuration, final CrawlProfile profile,
            final FocusedFrontierStore frontier, final int budget) {
        if (budget <= 0) return 0;
        int added = 0;
        final long now = System.currentTimeMillis();
        for (final FocusedFrontierStore.Candidate candidate : frontier.recoverable(now, budget)) {
            if (!memoryHealthy()) {
                pauseFocusedCrawlForMemoryPressure();
                break;
            }
            if (added >= budget) break;
            final long indexedAt = this.sb.index.getLoadTime(candidate.hash());
            if (indexedAt >= 0L) {
                frontier.markIndexed(candidate.hash());
                continue;
            }
            if (this.sb.crawlQueues.getURL(candidate.hash()) != null) {
                // The candidate was already accepted by YaCy's native
                // stacks or is currently being fetched by a worker. Do not
                // turn it back into READY and submit a duplicate occurrence.
                if (candidate.state() != FocusedFrontierStore.State.ADMITTED) {
                    frontier.markAdmitted(candidate.hash());
                }
                continue;
            }
            try {
                final Request request = candidate.request();
                this.manager.markRecovery(candidate.hash());
                frontier.markReady(candidate.hash(), true);
                this.stacker.enqueueEntry(request);
                added++;
            } catch (final IOException | RuntimeException e) {
                frontier.markFailed(candidate.hash(), 3600000L);
                LOG.warn("Cannot recover focused frontier URL " + candidate.url(), e);
            }
        }
        return added;
    }

    /**
     * Continue from links already present in the local index when the native
     * sidecar frontier is shallow. This does not reload the source page: it
     * only reconstructs a Request for a stored outbound link and hands that
     * request to YaCy's normal stacker. Policy scoring, robots checks, host
     * balancing, duplicate suppression, parsing and indexing remain native.
     */
    private int expandFromIndexedSources(final PolicyConfiguration configuration, final CrawlProfile profile,
            final FocusedFrontierStore frontier, final int budget, final Properties state) {
        if (budget <= 0) return 0;
        final FocusedCrawlPolicy policy = this.manager.policy(configuration.id());
        if (policy == null) return 0;
        final String escapedId = configuration.id().replace("\\", "\\\\").replace("\"", "\\\"");
        final String query = CollectionSchema.focused_policy_sxt.getSolrFieldName() + ":\"" + escapedId + "\" AND "
                + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200 AND "
                + CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName() + ":[* TO *]";
        final String[] fields = {
                CollectionSchema.id.getSolrFieldName(),
                CollectionSchema.sku.getSolrFieldName(),
                CollectionSchema.crawldepth_i.getSolrFieldName(),
                CollectionSchema.focused_relevance_i.getSolrFieldName(),
                CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(),
                CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()
        };
        int added = 0;
        int linksSeen = 0;
        int existing = 0;
        int notFocused = 0;
        int invalid = 0;
        try {
            final SolrQuery sourceQuery = new SolrQuery();
            sourceQuery.setQuery(query);
            sourceQuery.setSort(CollectionSchema.id.getSolrFieldName(), SolrQuery.ORDER.asc);
            final long sourceCount = this.sb.index.fulltext().getDefaultEmbeddedConnector()
                    .getCountByQuery(query);
            final int sourceTotal = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sourceCount));
            int sourceCursor = intValue(state, "indexedSourceCursor", 0);
            if (sourceTotal == 0 || sourceCursor >= sourceTotal) sourceCursor = 0;
            sourceQuery.setStart(sourceCursor);
            sourceQuery.setRows(configuration.frontier().indexedSourceBatch());
            sourceQuery.setFields(fields);
            sourceQuery.setIncludeScore(false);
            final QueryResponse sourceResponse = this.sb.index.fulltext().getDefaultEmbeddedConnector()
                    .getResponseByParams(sourceQuery);
            final SolrDocumentList sources = sourceResponse.getResults();
            final int nextCursor = sourceTotal == 0 || sources.isEmpty() || sourceCursor + sources.size() >= sourceTotal
                    ? 0 : sourceCursor + sources.size();
            state.setProperty("indexedSourceCursor", Integer.toString(nextCursor));
            for (final SolrDocument source : sources) {
                if (!memoryHealthy()) {
                    pauseFocusedCrawlForMemoryPressure();
                    break;
                }
                if (added >= budget) break;
                final String sourceId = stringValue(source.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                if (sourceId.isEmpty()) continue;
                final byte[] referrerHash = ASCII.getBytes(sourceId);
                final int sourceDepth = integerValue(source.getFieldValue(CollectionSchema.crawldepth_i.getSolrFieldName()));
                // Indexed-source continuation is a hand-off from an already
                // crawled page. Keep expansion at the configured ceiling when
                // the old page was already at maximum depth; otherwise every
                // mature source would be permanently unable to discover new
                // URLs after a restart or frontier migration.
                final int targetDepth = Math.min(sourceDepth + 1, configuration.limits().maximumDepth());
                int parentScore = this.manager.parentScore(referrerHash);
                if (parentScore <= 0) {
                    parentScore = integerValue(source.getFieldValue(CollectionSchema.focused_relevance_i.getSolrFieldName()));
                }
                if (parentScore <= 0) {
                    // The source was selected by focused_policy_sxt, so an
                    // older document without sidecar relevance metadata is at
                    // least a probable focused parent for expansion.
                    parentScore = configuration.probableThreshold();
                }
                final String sourceProtocol = protocolOf(source.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                final java.util.Iterator<String> links = outboundLinks(source, sourceProtocol).iterator();
                while (links.hasNext() && added < budget) {
                    if (!memoryHealthy()) {
                        pauseFocusedCrawlForMemoryPressure();
                        break;
                    }
                    final DigestURL url;
                    try {
                        url = new DigestURL(links.next());
                    } catch (final Exception ignored) {
                        invalid++;
                        continue;
                    }
                    linksSeen++;
                    if (frontier.get(url.hash()) != null) {
                        existing++;
                        continue;
                    }
                    final long indexedAt = this.sb.index.getLoadTime(url.hash());
                    final CrawlPolicyContext context = new CrawlPolicyContext(url, null,
                            parentScore, "Indexed focused source expansion",
                            profile.name(), targetDepth, null, indexedAt,
                            indexedAt >= 0L && profile.recrawlIfOlder() > indexedAt);
                    final CrawlPolicyDecision decision = policy.preFetch(context);
                    if (!decision.focused()) {
                        notFocused++;
                        continue;
                    }
                    final Request request = new Request(this.sb.peers.mySeed().hash.getBytes(), url, referrerHash,
                            "Indexed focused source expansion", new Date(), profile.handle(), targetDepth,
                            profile.timezoneOffset());
                    this.manager.recordDiscovered(request, Collections.singletonList(decision));
                    this.stacker.enqueueEntry(request);
                    added++;
                }
            }
            LOG.info("Indexed source expansion for profile " + configuration.id() + ": sources="
                    + sources.size() + ", links=" + linksSeen + ", existing=" + existing
                    + ", notFocused=" + notFocused + ", invalid=" + invalid + ", added=" + added
                    + ", cursor=" + state.getProperty("indexedSourceCursor", "0"));
        } catch (final IOException | RuntimeException e) {
            LOG.warn("Cannot expand focused frontier from indexed outbound links for profile "
                    + configuration.id(), e);
        }
        return added;
    }

    private static String stringValue(final Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integerValue(final Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * Read stored outbound links from both the normal Solr response shape and
     * the embedded connector's occasionally nested multi-value shape.
     */
    private static List<String> outboundLinks(final SolrDocument document, final String fallbackProtocol) {
        final List<String> stubs = flattenFieldValue(
                document.getFieldValue(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()));
        final List<String> encodedProtocols = flattenFieldValue(
                document.getFieldValue(CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName()));
        final List<String> protocols = new ArrayList<>(stubs.size());
        for (int i = 0; i < stubs.size(); i++) protocols.add(fallbackProtocol);
        for (final String encoded : encodedProtocols) {
            final int separator = encoded.indexOf('-');
            if (separator > 0 && separator + 1 < encoded.length()) {
                try {
                    final int index = Integer.parseInt(encoded.substring(0, separator));
                    if (index >= 0 && index < protocols.size()) protocols.set(index, encoded.substring(separator + 1));
                } catch (final NumberFormatException ignored) {
                    // Ignore a malformed protocol entry and retain http.
                }
            } else if (!encoded.isEmpty() && !protocols.isEmpty()) {
                protocols.set(0, encoded);
            }
        }
        final Set<String> links = new LinkedHashSet<>();
        for (int i = 0; i < stubs.size(); i++) {
            final String stub = stubs.get(i);
            if (stub.isEmpty()) continue;
            String url = protocols.get(i) + "://" + stub;
            final int fragment = url.indexOf('#');
            if (fragment > 0) url = url.substring(0, fragment);
            links.add(url);
        }
        return new ArrayList<>(links);
    }

    private static List<String> flattenFieldValue(final Object value) {
        final List<String> flattened = new ArrayList<>();
        if (value instanceof Collection<?>) {
            for (final Object nested : (Collection<?>) value) flattened.addAll(flattenFieldValue(nested));
        } else if (value != null) flattened.add(value.toString());
        return flattened;
    }

    private static String protocolOf(final Object sourceUrl) {
        if (sourceUrl != null) {
            final String value = sourceUrl.toString();
            final int separator = value.indexOf("://");
            if (separator > 0) return value.substring(0, separator).toLowerCase(java.util.Locale.ROOT);
        }
        return "https";
    }

    private static boolean seedsAllowed(final PolicyConfiguration configuration, final Properties state,
            final FocusedFrontierStore frontier) {
        if (frontier == null || !configuration.frontier().enabled()) return true;
        final String mode = configuration.frontier().seedMode();
        if ("disabled".equals(mode)) return false;
        if ("exhausted".equals(mode)) return frontier.recoverable(System.currentTimeMillis(), 1).isEmpty();
        return !Boolean.parseBoolean(state.getProperty("frontierStarted", "false"));
    }

    /**
     * The native focused queue is shared by profiles. Use the first enabled
     * novelty profile's weights and retain the safe 45/35/15/5 defaults when
     * no profile has novelty scheduling enabled.
     */
    private void applyNoveltyWeights(final Collection<FocusedCrawlPolicy> policies) {
        for (final FocusedCrawlPolicy policy : policies) {
            final PolicyConfiguration.Novelty novelty = policy.configuration().novelty();
            if (novelty.enabled()) {
                this.sb.crawlQueues.noticeURL.setFocusedNoveltyWeights(novelty.newHostWeight(),
                        novelty.newPathWeight(), novelty.expansionWeight(), novelty.freshnessWeight());
                return;
            }
        }
        this.sb.crawlQueues.noticeURL.setFocusedNoveltyWeights(45, 35, 15, 5);
    }

    private boolean isOwner() {
        return this.processLock != null && this.processLock.isValid();
    }

    private Properties state(final String id) {
        Properties state = this.states.get(id);
        if (state != null) return state;
        state = new Properties();
        final File file = new File(this.stateDirectory, id + ".properties");
        if (file.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                state.load(reader);
            } catch (final IOException e) {
                LOG.warn("Cannot load focused profile state " + id, e);
            }
        }
        state.setProperty("id", id);
        this.states.put(id, state);
        return state;
    }

    private CrawlProfile profile(final PolicyConfiguration configuration) {
        final CrawlProfile existing = this.profiles.get(configuration.id());
        if (existing != null) return existing;
        final String profileName = "focused-" + configuration.id();

        // Crawl profiles are persisted by YaCy independently of this
        // scheduler. Reusing the persisted profile is essential after a
        // restart: creating a new handle would leave the old native queue
        // running beside it and split one focused profile into duplicates.
        CrawlProfile selected = null;
        int selectedQueue = -1;
        final List<CrawlProfile> duplicates = new ArrayList<>();
        for (final byte[] handle : this.sb.crawler.getActive()) {
            final CrawlProfile candidate = this.sb.crawler.getActive(handle);
            if (candidate == null || !profileName.equals(candidate.name())) continue;
            final int candidateQueue = queueSize(candidate);
            if (selected == null || candidateQueue > selectedQueue) {
                if (selected != null) duplicates.add(selected);
                selected = candidate;
                selectedQueue = candidateQueue;
            } else {
                duplicates.add(candidate);
            }
        }
        if (selected != null) {
            applyConfiguration(selected, configuration);
            selected.setCollections("");
            this.sb.crawler.putActive(ASCII.getBytes(selected.handle()), selected);
            for (final CrawlProfile duplicate : duplicates) removeDuplicate(duplicate);
            this.profiles.put(configuration.id(), selected);
            return selected;
        }

        final CrawlProfile profile = (CrawlProfile) this.sb.crawler.defaultAutocrawlDeepProfile.clone();
        profile.put(CrawlProfile.CrawlAttribute.NAME.key, profileName);
        applyConfiguration(profile, configuration);
        profile.put(CrawlProfile.CrawlAttribute.CACHE_STRAGEGY.key, CacheStrategy.IFFRESH.toString());
        profile.put(CrawlProfile.CrawlAttribute.AGENT_NAME.key, ClientIdentification.yacyInternetCrawlerAgentName);
        // Collection assignment is policy-owned and happens after parsing.
        // Do not put every configured collection on the crawl profile: that
        // would stamp every fetched asset with every collection before the
        // post-fetch classifier can make a decision.
        profile.setCollections("");
        profile.setHandle();
        this.sb.crawler.putActive(ASCII.getBytes(profile.handle()), profile);
        this.profiles.put(configuration.id(), profile);
        return profile;
    }

    /** Apply the current policy limits to both new and recovered profiles. */
    private static void applyConfiguration(final CrawlProfile profile, final PolicyConfiguration configuration) {
        profile.put(CrawlProfile.CrawlAttribute.DEPTH.key,
                Integer.toString(configuration.limits().maximumDepth()));
        profile.put(CrawlProfile.CrawlAttribute.DOM_MAX_PAGES.key,
                Integer.toString(configuration.limits().perHostBudget()));
        profile.put(CrawlProfile.CrawlAttribute.RECRAWL_IF_OLDER.key,
                Long.toString(CrawlProfile.getRecrawlDate(
                        configuration.limits().defaultRecrawlIntervalMillis() / 60000L).getTime()));
    }

    private void removeDuplicate(final CrawlProfile duplicate) {
        final byte[] handle = ASCII.getBytes(duplicate.handle());
        this.sb.crawler.removeActive(handle);
        this.sb.crawler.removePassive(handle);
        try {
            this.sb.crawlQueues.noticeURL.removeByProfileHandle(duplicate.handle(), 10000);
        } catch (final SpaceExceededException e) {
            LOG.warn("Cannot fully remove duplicate focused profile " + duplicate.handle(), e);
        }
        LOG.info("Removed duplicate persisted focused profile " + duplicate.handle());
    }

    private int queueSize(final CrawlProfile profile) {
        int count = 0;
        for (final StackType stack : new StackType[] { StackType.FOCUSED, StackType.FOCUSED_PDF }) {
            final java.util.Iterator<Request> iterator = this.sb.crawlQueues.noticeURL.iterator(stack);
            if (iterator == null) continue;
            try {
                while (iterator.hasNext()) {
                    final Request request;
                    try {
                        request = iterator.next();
                    } catch (final NoSuchElementException e) {
                        return -1;
                    }
                    if (request != null && profile.handle().equals(request.profileHandle())) count++;
                }
            } catch (final NoSuchElementException e) {
                return -1;
            }
        }
        return count;
    }

    private boolean healthy(final PolicyConfiguration configuration) {
        if (!memoryHealthy()) return false;
        if (!diskHealthy(configuration)) return false;
        return true;
    }

    private boolean memoryHealthy() {
        return !MemoryControl.shortStatus()
                && MemoryControl.available() >= FocusedResourceGuard.refillThreshold(MemoryControl.maxMemory());
    }

    /**
     * Stop the native local crawler before a focused workload consumes the
     * last heap headroom. This is deliberately a crawl pause, not just a
     * refusal to refill: already queued requests and parser work also consume
     * memory. The distinct cause lets this scheduler resume only its own pause
     * after the larger recovery threshold is met.
     */
    private boolean pauseFocusedCrawlForMemoryPressure() {
        long available = MemoryControl.available();
        final long maximum = MemoryControl.maxMemory();
        final long threshold = FocusedResourceGuard.pauseThreshold(maximum);
        if (FocusedResourceGuard.shouldAttemptMemoryRecovery(available, maximum,
                MemoryControl.shortStatus())) {
            // This is a bounded, YaCy-managed reclamation attempt. If the
            // collector cannot restore headroom, request() records short
            // memory and the safety pause below still happens immediately.
            MemoryControl.request(threshold, false);
            available = MemoryControl.available();
        }
        if (!FocusedResourceGuard.shouldPause(available, maximum, MemoryControl.shortStatus())) return false;

        final String jobType = SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL;
        if (!this.sb.crawlJobIsPaused(jobType)) {
            final String cause = "focused resource guard: JVM headroom " + available
                    + " bytes is below " + threshold + " bytes";
            this.sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_AUTODISABLED, true);
            this.sb.pauseCrawlJob(jobType, cause);
            LOG.warn("Paused local crawling for focused-profile memory headroom: available="
                    + available + ", threshold=" + threshold);
        }

        this.resourceState.setProperty("lastCheck", Long.toString(System.currentTimeMillis()));
        this.resourceState.setProperty("paused", Boolean.toString(this.sb.crawlJobIsPaused(jobType)));
        this.resourceState.setProperty("pauseCause", this.sb.getConfig(jobType + "_isPaused_cause", ""));
        this.resourceState.setProperty("memoryAvailable", Long.toString(available));
        this.resourceState.setProperty("memoryMaximum", Long.toString(maximum));
        this.resourceState.setProperty("memoryThreshold", Long.toString(FocusedResourceGuard.refillThreshold(maximum)));
        this.resourceState.setProperty("memoryPauseThreshold", Long.toString(threshold));
        this.resourceState.setProperty("memoryRecoveryThreshold", Long.toString(FocusedResourceGuard.recoveryThreshold(maximum)));
        this.resourceState.setProperty("memoryShort", Boolean.toString(MemoryControl.shortStatus()));
        this.resourceState.setProperty("memoryProper", Boolean.toString(MemoryControl.properState()));
        this.resourceState.setProperty("diskHealthy", Boolean.toString(diskHealthy(null)));
        persistResourceState();
        return true;
    }

    private boolean diskHealthy(final PolicyConfiguration configuration) {
        final File index = new File(this.sb.getDataPath(), "DATA/INDEX");
        final File data = new File(this.sb.getDataPath(), "DATA");
        final long target = configuration == null ? 0L : configuration.limits().storageTargetBytes();
        if (target > 0L && index.getUsableSpace() < target) return false;
        if (index.getTotalSpace() > 0L && index.getUsableSpace() * 4L < index.getTotalSpace()) return false;
        return data.getTotalSpace() <= 0L || data.getUsableSpace() * 4L >= data.getTotalSpace();
    }

    /**
     * Recover only pauses explicitly created by YaCy's ResourceObserver or
     * this focused resource guard. Manual/network pauses remain untouched.
     */
    private void recoverResourceObserverPause() {
        final String jobType = SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL;
        final boolean paused = this.sb.crawlJobIsPaused(jobType);
        final String cause = this.sb.getConfig(jobType + "_isPaused_cause", "");
        long available = MemoryControl.available();
        final long maximum = MemoryControl.maxMemory();
        // A previous failed allocation sets a sticky short-status bit. Once
        // the full recovery headroom is present, a tiny successful request is
        // safe and lets MemoryControl clear that transient bit normally.
        final boolean managedPause = paused && FocusedResourceGuard.isManagedPauseCause(cause);
        if (managedPause && available >= FocusedResourceGuard.recoveryThreshold(maximum)
                && MemoryControl.shortStatus()) {
            MemoryControl.request(1024L, false);
            available = MemoryControl.available();
        }
        final boolean disk = diskHealthy(null);
        final boolean recover;
        if (managedPause) {
            recover = this.resourceGuard.observe(cause, available, maximum,
                    MemoryControl.shortStatus(), disk);
        } else {
            this.resourceGuard.reset();
            recover = false;
        }
        this.resourceState.setProperty("lastCheck", Long.toString(System.currentTimeMillis()));
        this.resourceState.setProperty("paused", Boolean.toString(paused));
        this.resourceState.setProperty("pauseCause", cause);
        this.resourceState.setProperty("memoryAvailable", Long.toString(available));
        this.resourceState.setProperty("memoryMaximum", Long.toString(maximum));
        this.resourceState.setProperty("memoryThreshold", Long.toString(FocusedResourceGuard.refillThreshold(maximum)));
        this.resourceState.setProperty("memoryPauseThreshold", Long.toString(FocusedResourceGuard.pauseThreshold(maximum)));
        this.resourceState.setProperty("memoryRecoveryThreshold", Long.toString(FocusedResourceGuard.recoveryThreshold(maximum)));
        this.resourceState.setProperty("memoryShort", Boolean.toString(MemoryControl.shortStatus()));
        this.resourceState.setProperty("memoryProper", Boolean.toString(MemoryControl.properState()));
        this.resourceState.setProperty("diskHealthy", Boolean.toString(disk));
        this.resourceState.setProperty("healthyChecks", Integer.toString(this.resourceGuard.healthyChecks()));
        this.resourceState.setProperty("recoveryAttempts", Long.toString(this.resourceGuard.recoveryAttempts()));
        this.resourceState.setProperty("recoveries", Long.toString(this.resourceGuard.recoveries()));
        if (recover && managedPause) {
            MemoryControl.resetProperState();
            MemoryControl.request(1024L, false);
            if (FocusedResourceGuard.isFocusedPauseCause(cause)) {
                if (this.sb.crawlJobIsPaused(jobType)
                        && this.sb.getConfigBool(SwitchboardConstants.CRAWLJOB_LOCAL_AUTODISABLED, false)
                        && cause.equals(this.sb.getConfig(jobType + "_isPaused_cause", ""))) {
                    this.sb.setConfig(SwitchboardConstants.CRAWLJOB_LOCAL_AUTODISABLED, false);
                    this.sb.continueCrawlJob(jobType);
                    this.sb.setConfig(jobType + "_isPaused_cause", "");
                }
            } else {
                this.sb.observer.resourceObserverJob();
            }
            if (!this.sb.crawlJobIsPaused(jobType)) {
                this.resourceState.setProperty("lastRecovery", Long.toString(System.currentTimeMillis()));
                LOG.info("Recovered managed crawl resource pause after two healthy focused-crawl checks");
            } else {
                this.resourceState.setProperty("lastRecoveryFailure", Long.toString(System.currentTimeMillis()));
            }
        }
        this.resourceState.setProperty("paused", Boolean.toString(this.sb.crawlJobIsPaused(jobType)));
        this.resourceState.setProperty("pauseCause", this.sb.getConfig(jobType + "_isPaused_cause", ""));
        persistResourceState();
    }

    private void persistResourceState() {
        try {
            Files.createDirectories(this.stateDirectory.toPath());
            final File temporary = new File(this.stateDirectory, "resource.properties.tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
                this.resourceState.store(writer, "Focused Autocrawler resource recovery state");
            }
            Files.move(temporary.toPath(), this.resourceStateFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            LOG.warn("Cannot persist focused resource recovery state", e);
        }
    }

    private void save(final Properties state, final int before, final int after, final int added, final String reason) {
        save(state, before, after, added, reason, null);
    }

    private void save(final Properties state, final int before, final int after, final int added, final String reason,
            final FocusedFrontierStore.Snapshot frontier) {
        final long now = System.currentTimeMillis();
        state.setProperty("lastRun", Long.toString(now));
        state.setProperty("lastQueueBefore", Integer.toString(before));
        state.setProperty("lastQueueAfter", Integer.toString(after));
        state.setProperty("lastEnqueued", Integer.toString(added));
        state.setProperty("lastReason", reason == null ? "" : reason);
        if (frontier != null) {
            state.setProperty("lastFrontierTotal", Integer.toString(frontier.total()));
            state.setProperty("lastFrontierDiscovered", Integer.toString(frontier.discovered()));
            state.setProperty("lastFrontierRecoverable", Integer.toString(frontier.recoverable()));
            state.setProperty("lastFrontierSeedBootstrap", Integer.toString(frontier.seedBootstrap()));
            state.setProperty("lastFrontierNativeLink", Integer.toString(frontier.nativeLink()));
            state.setProperty("lastFrontierAdminReset", Integer.toString(frontier.adminReset()));
            state.setProperty("lastFrontierLegacy", Integer.toString(frontier.legacy()));
        }
        final String id = state.getProperty("id", "profile");
        try {
            Files.createDirectories(this.stateDirectory.toPath());
            final File file = new File(this.stateDirectory, id + ".properties");
            final File temporary = new File(this.stateDirectory, id + ".properties.tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
                state.store(writer, "Focused Autocrawler Profile state");
            }
            Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            final JSONObject report = new JSONObject(true);
            report.put("timestamp", now).put("profile", id).put("queueBefore", before)
                    .put("queueAfter", after).put("enqueued", added).put("reason", reason);
            if (frontier != null) {
                report.put("frontierTotal", frontier.total()).put("frontierDiscovered", frontier.discovered())
                        .put("frontierRecoverable", frontier.recoverable())
                        .put("frontierSeedBootstrap", frontier.seedBootstrap())
                        .put("frontierNativeLink", frontier.nativeLink())
                        .put("frontierAdminReset", frontier.adminReset())
                        .put("frontierLegacy", frontier.legacy());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(this.reportFile.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(report.toString());
                writer.newLine();
            }
        } catch (final IOException | JSONException e) {
            LOG.warn("Cannot persist focused scheduler state", e);
        }
    }

    public synchronized Collection<ProfileStatus> statuses() {
        final List<ProfileStatus> result = new ArrayList<>();
        for (final FocusedCrawlPolicy policy : this.manager.policies()) {
            final PolicyConfiguration configuration = policy.configuration();
            final CrawlProfile profile = profile(configuration);
            final Properties state = state(configuration.id());
            state.setProperty("id", configuration.id());
            result.add(new ProfileStatus(configuration.id(), configuration.version(), configuration.enabled(),
                    queueSize(profile), configuration.limits(), configuration.seeds().size(), state,
                    this.manager.metrics().snapshot(configuration.id()),
                    this.manager.frontierEnabled(configuration.id())
                            ? this.manager.frontier(configuration.id()).snapshot() : null));
        }
        return result;
    }

    /**
     * Snapshot and remove only one focused profile's native queue entries.
     * Indexed documents and all other profiles remain untouched.
     */
    public synchronized ResetReport resetProfile(final String profileId) throws IOException {
        final FocusedCrawlPolicy policy = this.manager.policy(profileId);
        if (policy == null) throw new IllegalArgumentException("unknown focused profile " + profileId);
        if (!policy.configuration().frontier().enabled()) {
            throw new IllegalArgumentException("frontier recovery is disabled for profile " + profileId);
        }
        final PolicyConfiguration configuration = policy.configuration();
        final CrawlProfile profile = profile(configuration);
        final FocusedFrontierStore frontier = this.manager.frontier(profileId);
        final int before = queueSize(profile);
        int snapshotted = 0;
        final Set<String> snapshottedHashes = new HashSet<>();

        for (final StackType stackType : StackType.values()) {
            final java.util.Iterator<Request> iterator = this.sb.crawlQueues.noticeURL.iterator(stackType);
            if (iterator == null) continue;
            while (iterator.hasNext()) {
                final Request request = iterator.next();
                if (request == null || !profile.handle().equals(request.profileHandle())) continue;
                if (snapshottedHashes.add(java.util.Base64.getEncoder().encodeToString(request.url().hash()))) {
                    snapshotForReset(frontier, request, configuration, profileId);
                    snapshotted++;
                }
            }
        }
        for (final Request request : this.sb.crawlQueues.activeWorkerEntries().values()) {
            if (request == null || !profile.handle().equals(request.profileHandle())) continue;
            if (snapshottedHashes.add(java.util.Base64.getEncoder().encodeToString(request.url().hash()))) {
                snapshotForReset(frontier, request, configuration, profileId);
                snapshotted++;
            }
        }
        final int removed;
        try {
            removed = this.sb.crawlQueues.noticeURL.removeByProfileHandle(profile.handle(), Long.MAX_VALUE);
        } catch (final SpaceExceededException e) {
            throw new IOException("cannot remove focused profile queue entries", e);
        }
        final Properties state = state(profileId);
        state.setProperty("frontierStarted", "true");
        state.setProperty("lastReset", Long.toString(System.currentTimeMillis()));
        state.setProperty("lastResetSnapshotted", Integer.toString(snapshotted));
        state.setProperty("lastResetRemoved", Integer.toString(removed));
        save(state, before, queueSize(profile), 0, "frontier reset", frontier.snapshot());
        return new ResetReport(profileId, snapshotted, removed, frontier.size());
    }

    private void snapshotForReset(final FocusedFrontierStore frontier, final Request request,
            final PolicyConfiguration configuration, final String profileId) {
        final FocusedCrawlMetadata metadata = this.manager.metadataStore().get(request.url().hash());
        if (metadata != null) {
            frontier.snapshot(request, metadata, true);
            return;
        }
        frontier.snapshot(request, new CrawlPolicyDecision(profileId, configuration.version(),
                CrawlPolicyDecision.Action.ACCEPT, configuration.focusedThreshold(), PolicyPriority.FOCUSED,
                Collections.emptySet(), configuration.limits().maximumDepth(), configuration.limits().perHostBudget(),
                false, configuration.limits().defaultRecrawlIntervalMillis(),
                Collections.singletonList("frontier-reset-snapshot"), NoveltyClass.PROVEN_EXPANSION), true);
    }

    public synchronized JSONObject statusJSON() {
        final JSONObject json = new JSONObject(true);
        try {
            final org.json.JSONArray profilesJSON = new org.json.JSONArray();
            for (final ProfileStatus status : statuses()) profilesJSON.put(status.toJSON());
            final JSONObject resource = new JSONObject(true);
            resource.put("paused", this.sb.crawlJobIsPaused(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL))
                    .put("pauseCause", this.sb.getConfig(SwitchboardConstants.CRAWLJOB_LOCAL_CRAWL + "_isPaused_cause", ""))
                    .put("memoryAvailable", MemoryControl.available())
                    .put("memoryMaximum", MemoryControl.maxMemory())
                    .put("memoryThreshold", FocusedResourceGuard.refillThreshold(MemoryControl.maxMemory()))
                    .put("memoryPauseThreshold", FocusedResourceGuard.pauseThreshold(MemoryControl.maxMemory()))
                    .put("memoryRecoveryThreshold", FocusedResourceGuard.recoveryThreshold(MemoryControl.maxMemory()))
                    .put("memoryShort", MemoryControl.shortStatus())
                    .put("memoryProper", MemoryControl.properState())
                    .put("healthyChecks", this.resourceGuard.healthyChecks())
                    .put("recoveryAttempts", this.resourceGuard.recoveryAttempts())
                    .put("recoveries", this.resourceGuard.recoveries());
            json.put("feature", "Focused Autocrawler Profiles").put("owner", isOwner()).put("profiles", profilesJSON)
                    .put("resource", resource).put("pdfLane", this.sb.crawlQueues.focusedPdfLane().toJSON());
        } catch (final JSONException e) {
            throw new IllegalStateException("cannot serialize focused scheduler status", e);
        }
        return json;
    }

    /** Drop cached CrawlProfile objects after a hot profile reload. Queue rows remain untouched. */
    public synchronized void reloadProfiles() {
        this.profiles.clear();
    }

    public void close() {
        this.manager.close();
        try { if (this.processLock != null) this.processLock.release(); } catch (final IOException ignored) { }
        try { if (this.lockChannel != null) this.lockChannel.close(); } catch (final IOException ignored) { }
    }

    private static int intValue(final Properties properties, final String key, final int defaultValue) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue))); }
        catch (final NumberFormatException e) { return defaultValue; }
    }

    private static long parseLong(final Properties properties, final String key, final long defaultValue) {
        try { return Long.parseLong(properties.getProperty(key, Long.toString(defaultValue))); }
        catch (final NumberFormatException e) { return defaultValue; }
    }
}

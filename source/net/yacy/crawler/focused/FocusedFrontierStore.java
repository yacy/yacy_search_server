// Persistent frontier state for focused autocrawler profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;

/**
 * Crash-safe URL frontier for one focused profile.
 *
 * <p>The native YaCy queues remain authoritative for fetching. This sidecar
 * only remembers enough information to rebuild a profile frontier after a
 * restart or an explicit profile reset.</p>
 */
public final class FocusedFrontierStore implements AutoCloseable {

    /** Avoid a single damaged/restarted profile rewriting an unbounded frontier in one scheduler pass. */
    private static final int MAX_STALE_RECOVERIES_PER_SCAN = 1024;

    public enum State {
        DISCOVERED, READY, ADMITTED, IN_FLIGHT, INDEXED, DUPLICATE, FAILED, BLOCKED
    }

    /** How a focused URL entered the durable frontier. */
    public enum DiscoverySource {
        SEED_BOOTSTRAP, NATIVE_LINK, ADMIN_RESET, LEGACY
    }

    /** A persisted candidate and the request information needed to requeue it. */
    public static final class Candidate {
        private final byte[] hash;
        private final String url;
        private final byte[] initiator;
        private final byte[] referrerHash;
        private final String name;
        private final String profileHandle;
        private final int depth;
        private final int timezoneOffset;
        private final State state;
        private final NoveltyClass lane;
        private final int relevance;
        private final String version;
        private final Set<String> collections;
        private final List<String> reasons;
        private final long firstSeen;
        private final long lastSeen;
        private final long nextEligible;
        private final long lastSubmitted;
        private final int attempts;
        private final boolean recovery;
        private final DiscoverySource discoverySource;

        private Candidate(final byte[] hash, final String url, final byte[] initiator,
                final byte[] referrerHash, final String name, final String profileHandle,
                final int depth, final int timezoneOffset, final State state,
                final NoveltyClass lane, final int relevance, final String version,
                final Set<String> collections, final List<String> reasons,
                final long firstSeen, final long lastSeen, final long nextEligible,
                final long lastSubmitted, final int attempts, final boolean recovery,
                final DiscoverySource discoverySource) {
            this.hash = hash == null ? null : hash.clone();
            this.url = url == null ? "" : url;
            this.initiator = initiator == null ? null : initiator.clone();
            this.referrerHash = referrerHash == null ? null : referrerHash.clone();
            this.name = name == null ? "" : name;
            this.profileHandle = profileHandle == null ? "" : profileHandle;
            this.depth = Math.max(0, depth);
            this.timezoneOffset = timezoneOffset;
            this.state = state == null ? State.DISCOVERED : state;
            this.lane = lane == null ? NoveltyClass.SATURATED : lane;
            this.relevance = relevance;
            this.version = version == null ? "" : version;
            this.collections = Collections.unmodifiableSet(new LinkedHashSet<>(collections == null
                    ? Collections.emptySet() : collections));
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons == null
                    ? Collections.emptyList() : reasons));
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.nextEligible = nextEligible;
            this.lastSubmitted = lastSubmitted;
            this.attempts = Math.max(0, attempts);
            this.recovery = recovery;
            this.discoverySource = discoverySource == null ? DiscoverySource.LEGACY : discoverySource;
        }

        public byte[] hash() { return this.hash == null ? null : this.hash.clone(); }
        public String url() { return this.url; }
        public byte[] initiator() { return this.initiator == null ? null : this.initiator.clone(); }
        public byte[] referrerHash() { return this.referrerHash == null ? null : this.referrerHash.clone(); }
        public String name() { return this.name; }
        public String profileHandle() { return this.profileHandle; }
        public int depth() { return this.depth; }
        public int timezoneOffset() { return this.timezoneOffset; }
        public State state() { return this.state; }
        public NoveltyClass lane() { return this.lane; }
        public int relevance() { return this.relevance; }
        public String version() { return this.version; }
        public Set<String> collections() { return this.collections; }
        public List<String> reasons() { return this.reasons; }
        public long firstSeen() { return this.firstSeen; }
        public long lastSeen() { return this.lastSeen; }
        public long nextEligible() { return this.nextEligible; }
        public long lastSubmitted() { return this.lastSubmitted; }
        public int attempts() { return this.attempts; }
        public boolean recovery() { return this.recovery; }
        public DiscoverySource discoverySource() { return this.discoverySource; }

        public Request request() throws IOException {
            final DigestURL digestURL = new DigestURL(this.url, this.hash);
            return new Request(this.initiator, digestURL, this.referrerHash, this.name,
                    new Date(this.lastSeen), this.profileHandle, this.depth, this.timezoneOffset);
        }
    }

    /** Compact state counts used by the UI and status API. */
    public static final class Snapshot {
        private final int total;
        private final int discovered;
        private final int ready;
        private final int admitted;
        private final int inFlight;
        private final int indexed;
        private final int duplicate;
        private final int failed;
        private final int blocked;
        private final int seedBootstrap;
        private final int nativeLink;
        private final int adminReset;
        private final int legacy;

        private Snapshot(final int total, final int discovered, final int ready, final int admitted,
                final int inFlight, final int indexed, final int duplicate, final int failed, final int blocked,
                final int seedBootstrap, final int nativeLink, final int adminReset, final int legacy) {
            this.total = total;
            this.discovered = discovered;
            this.ready = ready;
            this.admitted = admitted;
            this.inFlight = inFlight;
            this.indexed = indexed;
            this.duplicate = duplicate;
            this.failed = failed;
            this.blocked = blocked;
            this.seedBootstrap = seedBootstrap;
            this.nativeLink = nativeLink;
            this.adminReset = adminReset;
            this.legacy = legacy;
        }

        public int total() { return this.total; }
        public int discovered() { return this.discovered; }
        public int ready() { return this.ready; }
        public int admitted() { return this.admitted; }
        public int inFlight() { return this.inFlight; }
        public int indexed() { return this.indexed; }
        public int duplicate() { return this.duplicate; }
        public int failed() { return this.failed; }
        public int blocked() { return this.blocked; }
        public int recoverable() { return this.discovered + this.ready + this.admitted + this.inFlight; }
        public int seedBootstrap() { return this.seedBootstrap; }
        public int nativeLink() { return this.nativeLink; }
        public int adminReset() { return this.adminReset; }
        public int legacy() { return this.legacy; }
    }

    private static final String URL = "url";
    private static final String INITIATOR = "initiator";
    private static final String REFERRER = "referrer";
    private static final String NAME = "name";
    private static final String PROFILE = "profile";
    private static final String DEPTH = "depth";
    private static final String TIMEZONE = "timezone";
    private static final String STATE = "state";
    private static final String LANE = "lane";
    private static final String RELEVANCE = "relevance";
    private static final String VERSION = "version";
    private static final String COLLECTIONS = "collections";
    private static final String REASONS = "reasons";
    private static final String FIRST_SEEN = "firstSeen";
    private static final String LAST_SEEN = "lastSeen";
    private static final String NEXT_ELIGIBLE = "nextEligible";
    private static final String LAST_SUBMITTED = "lastSubmitted";
    private static final String ATTEMPTS = "attempts";
    private static final String RECOVERY = "recovery";
    private static final String DISCOVERY_SOURCE = "discoverySource";
    private static final String LIST_SEPARATOR = "\u001f";

    private final MapHeap heap;
    private final String profileId;

    public FocusedFrontierStore(final File stateDirectory, final String profileId) {
        this.profileId = profileId == null ? "" : profileId;
        MapHeap opened = null;
        try {
            if (stateDirectory != null) stateDirectory.mkdirs();
            final File file = new File(stateDirectory, this.profileId + ".frontier.heap");
            opened = new MapHeap(file, Word.commonHashLength, NaturalOrder.naturalOrder,
                    1024 * 128, 512, ' ');
        } catch (final IOException ignored) { }
        this.heap = opened;
    }

    /** Snapshot a request that is already in a native YaCy queue. */
    public synchronized void snapshot(final Request request, final FocusedCrawlMetadata metadata,
            final boolean recovery) {
        if (request == null || request.url() == null || metadata == null || this.profileId.isEmpty()) return;
        final String version = metadata.policyVersions().isEmpty() ? "" : metadata.policyVersions().get(0);
        final CrawlPolicyDecision decision = new CrawlPolicyDecision(this.profileId, version,
                CrawlPolicyDecision.Action.ACCEPT, metadata.relevanceScore(), metadata.priority(),
                metadata.collections(), metadata.maximumDepth(), metadata.perHostBudget(),
                false, metadata.recrawlIntervalMillis(), metadata.reasonCodes(), metadata.noveltyClass());
        snapshot(request, decision, recovery);
    }

    /** Snapshot a native request with an already evaluated policy decision. */
    public synchronized void snapshot(final Request request, final CrawlPolicyDecision decision,
            final boolean recovery) {
        if (request == null || request.url() == null || decision == null || this.profileId.isEmpty()) return;
        discover(request, decision, DiscoverySource.ADMIN_RESET);
        final Candidate candidate = get(request.url().hash());
        if (candidate != null) {
            final Candidate updated = new Candidate(candidate.hash(), candidate.url(), candidate.initiator(),
                    candidate.referrerHash(), candidate.name(), candidate.profileHandle(), candidate.depth(),
                    candidate.timezoneOffset(), State.READY, candidate.lane(), candidate.relevance(), candidate.version(),
                    candidate.collections(), candidate.reasons(), candidate.firstSeen(), System.currentTimeMillis(),
                    0L, candidate.lastSubmitted(), candidate.attempts(), recovery, candidate.discoverySource());
            put(updated);
        }
    }

    public synchronized boolean discover(final Request request, final CrawlPolicyDecision decision) {
        return discover(request, decision, DiscoverySource.NATIVE_LINK);
    }

    public synchronized boolean discover(final Request request, final CrawlPolicyDecision decision,
            final DiscoverySource discoverySource) {
        if (request == null || request.url() == null || decision == null || !decision.focused()) return false;
        final byte[] hash = request.url().hash();
        final Candidate previous = get(hash);
        final long now = System.currentTimeMillis();
        if (previous != null && isTerminal(previous.state())) {
            // A newly discovered indexed/failed URL remains terminal until the
            // policy's normal recrawl rules explicitly make it eligible.
            if (previous.state() == State.INDEXED && !decision.recrawlFocused()) return false;
            if (previous.state() == State.DUPLICATE && previous.nextEligible() > now) return false;
        }
        final DiscoverySource effectiveSource = previous == null || previous.discoverySource() == DiscoverySource.LEGACY
                ? (discoverySource == null ? DiscoverySource.NATIVE_LINK : discoverySource)
                : previous.discoverySource();
        final Candidate candidate = new Candidate(hash, request.url().toNormalform(true), request.initiator(),
                request.referrerhash(), request.name(), request.profileHandle(), request.depth(),
                request.timezoneOffset(), previous == null ? State.DISCOVERED : previous.state(),
                decision.noveltyClass(), decision.relevanceScore(), decision.policyVersion(),
                decision.collections(), decision.reasonCodes(), previous == null ? now : previous.firstSeen(), now,
                previous == null ? 0L : previous.nextEligible(), previous == null ? 0L : previous.lastSubmitted(),
                previous == null ? 0 : previous.attempts(), previous != null && previous.recovery(), effectiveSource);
        put(candidate);
        return previous == null;
    }

    public synchronized void markReady(final byte[] hash, final boolean recovery) {
        update(hash, State.READY, 0L, recovery, false);
    }

    public synchronized void markAdmitted(final byte[] hash) {
        update(hash, State.ADMITTED, 0L, false, true);
    }

    public synchronized void markInFlight(final byte[] hash) {
        update(hash, State.IN_FLIGHT, 0L, false, false);
    }

    public synchronized void markIndexed(final byte[] hash) {
        update(hash, State.INDEXED, 0L, false, false);
    }

    public synchronized void markDuplicate(final byte[] hash) {
        update(hash, State.DUPLICATE, System.currentTimeMillis() + 3600000L, false, false);
    }

    public synchronized void markFailed(final byte[] hash, final long retryAfterMillis) {
        update(hash, State.FAILED, System.currentTimeMillis() + Math.max(0L, retryAfterMillis), false, false);
    }

    public synchronized void markBlocked(final byte[] hash) {
        update(hash, State.BLOCKED, 0L, false, false);
    }

    /**
     * Convert stale in-flight requests into recoverable candidates.
     *
     * <p>ADMITTED candidates are already eligible in {@link #recoverable(long, int)} and are
     * checked against YaCy's native queues before resubmission. Only IN_FLIGHT rows need this
     * transition. The heap scan deliberately does not hold this store's monitor: crawl-stack,
     * indexing, and loader callbacks update the same store and must not wait behind a full
     * frontier traversal. Individual stale-row transitions are rechecked and serialized below.</p>
     */
    public int recoverStale(final long now, final long graceMillis) {
        if (this.heap == null) return 0;
        int changed = 0;
        final List<byte[]> hashes = new ArrayList<>();
        try {
            final Iterator<Map.Entry<byte[], Map<String, String>>> entries = this.heap.entries(true, false);
            while (entries.hasNext() && hashes.size() < MAX_STALE_RECOVERIES_PER_SCAN) {
                final Map.Entry<byte[], Map<String, String>> entry = entries.next();
                final Candidate candidate = parse(entry.getKey(), entry.getValue());
                if (candidate == null) continue;
                if (candidate.state() == State.IN_FLIGHT
                        && now - Math.max(candidate.lastSubmitted(), candidate.lastSeen()) >= graceMillis) {
                    hashes.add(candidate.hash());
                }
            }
            for (final byte[] hash : hashes) {
                if (markStaleInFlightReady(hash, now, graceMillis)) changed++;
            }
        } catch (final IOException ignored) { }
        return changed;
    }

    /** Return candidates eligible to rebuild the native YaCy queue. */
    public List<Candidate> recoverable(final long now, final int limit) {
        if (this.heap == null || limit <= 0) return Collections.emptyList();
        final List<Candidate> result = new ArrayList<>();
        try {
            final Iterator<Map.Entry<byte[], Map<String, String>>> entries = this.heap.entries(true, false);
            while (entries.hasNext() && result.size() < limit) {
                final Map.Entry<byte[], Map<String, String>> entry = entries.next();
                final Candidate candidate = parse(entry.getKey(), entry.getValue());
                if (candidate == null || !eligible(candidate, now)) continue;
                result.add(candidate);
            }
        } catch (final IOException ignored) { }
        result.sort((left, right) -> {
            int comparison = Integer.compare(laneRank(right.lane()), laneRank(left.lane()));
            if (comparison != 0) return comparison;
            comparison = Integer.compare(right.relevance(), left.relevance());
            if (comparison != 0) return comparison;
            return Long.compare(left.lastSeen(), right.lastSeen());
        });
        return result;
    }

    /**
     * Return best-effort counts. Concurrent crawl updates can make this snapshot slightly
     * inconsistent, which is preferable to blocking crawl and indexing callbacks for a full scan.
     */
    public Snapshot snapshot() {
        int total = 0, discovered = 0, ready = 0, admitted = 0, inFlight = 0,
                indexed = 0, duplicate = 0, failed = 0, blocked = 0,
                seedBootstrap = 0, nativeLink = 0, adminReset = 0, legacy = 0;
        if (this.heap == null) return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        try {
            final Iterator<Map.Entry<byte[], Map<String, String>>> entries = this.heap.entries(true, false);
            while (entries.hasNext()) {
                final Map.Entry<byte[], Map<String, String>> entry = entries.next();
                final Candidate candidate = parse(entry.getKey(), entry.getValue());
                if (candidate == null) continue;
                total++;
                switch (candidate.state()) {
                    case DISCOVERED: discovered++; break;
                    case READY: ready++; break;
                    case ADMITTED: admitted++; break;
                    case IN_FLIGHT: inFlight++; break;
                    case INDEXED: indexed++; break;
                    case DUPLICATE: duplicate++; break;
                    case FAILED: failed++; break;
                    case BLOCKED: blocked++; break;
                    default: break;
                }
                switch (candidate.discoverySource()) {
                    case SEED_BOOTSTRAP: seedBootstrap++; break;
                    case NATIVE_LINK: nativeLink++; break;
                    case ADMIN_RESET: adminReset++; break;
                    case LEGACY: legacy++; break;
                    default: break;
                }
            }
        } catch (final IOException ignored) { }
        return new Snapshot(total, discovered, ready, admitted, inFlight, indexed, duplicate, failed, blocked,
                seedBootstrap, nativeLink, adminReset, legacy);
    }

    public synchronized int size() { return this.heap == null ? 0 : this.heap.size(); }

    public synchronized Candidate get(final byte[] hash) {
        if (this.heap == null || hash == null) return null;
        try { return parse(hash, this.heap.get(hash)); }
        catch (final IOException | SpaceExceededException ignored) { return null; }
    }

    public void compact(final long terminalBefore) {
        compact(terminalBefore, Integer.MAX_VALUE);
    }

    /** Remove old terminal rows and, when configured, trim oldest terminal rows above the cap. */
    public int compact(final long terminalBefore, final int maxEntries) {
        if (this.heap == null) return 0;
        final List<Candidate> terminal = new ArrayList<>();
        int removed = 0;
        try {
            final Iterator<Map.Entry<byte[], Map<String, String>>> entries = this.heap.entries(true, false);
            while (entries.hasNext()) {
                final Map.Entry<byte[], Map<String, String>> entry = entries.next();
                final Candidate candidate = parse(entry.getKey(), entry.getValue());
                if (candidate != null && isTerminal(candidate.state())) {
                    if (candidate.lastSeen() < terminalBefore) {
                        if (deleteTerminal(candidate.hash(), terminalBefore, true)) removed++;
                    }
                    else terminal.add(candidate);
                }
            }
            if (maxEntries > 0 && this.heap.size() > maxEntries) {
                terminal.sort((left, right) -> Long.compare(left.lastSeen(), right.lastSeen()));
                int excess = this.heap.size() - maxEntries;
                for (int i = 0; i < terminal.size() && excess > 0; i++, excess--) {
                    if (deleteTerminal(terminal.get(i).hash(), 0L, false)) removed++;
                    else excess++;
                }
            }
        } catch (final IOException ignored) { }
        return removed;
    }

    @Override
    public synchronized void close() {
        if (this.heap != null) this.heap.close();
    }

    private void update(final byte[] hash, final State state, final long nextEligible,
            final boolean recovery, final boolean submitted) {
        if (this.heap == null || hash == null) return;
        final Candidate previous = get(hash);
        if (previous == null) return;
        final long now = System.currentTimeMillis();
        final Candidate updated = new Candidate(previous.hash(), previous.url(), previous.initiator(),
                previous.referrerHash(), previous.name(), previous.profileHandle(), previous.depth(),
                previous.timezoneOffset(), state, previous.lane(), previous.relevance(), previous.version(),
                previous.collections(), previous.reasons(), previous.firstSeen(), now, nextEligible,
                submitted ? now : previous.lastSubmitted(), previous.attempts() + (submitted ? 1 : 0),
                recovery || previous.recovery(), previous.discoverySource());
        put(updated);
    }

    /** Recheck and transition one stale in-flight row under the short-lived store lock. */
    private synchronized boolean markStaleInFlightReady(final byte[] hash, final long now,
            final long graceMillis) {
        final Candidate current = get(hash);
        if (current == null || current.state() != State.IN_FLIGHT
                || now - Math.max(current.lastSubmitted(), current.lastSeen()) < graceMillis) return false;
        update(hash, State.READY, 0L, true, false);
        return true;
    }

    /** Recheck a terminal row before deleting it; a concurrent transition to active wins. */
    private synchronized boolean deleteTerminal(final byte[] hash, final long terminalBefore,
            final boolean requireOlder) {
        if (this.heap == null || hash == null) return false;
        final Candidate current = get(hash);
        if (current == null || !isTerminal(current.state())
                || (requireOlder && current.lastSeen() >= terminalBefore)) return false;
        try {
            this.heap.delete(hash);
            return true;
        } catch (final IOException ignored) {
            return false;
        }
    }

    private void put(final Candidate candidate) {
        if (this.heap == null || candidate == null || candidate.hash() == null) return;
        try { this.heap.put(candidate.hash(), serialize(candidate)); }
        catch (final RuntimeException ignored) { }
    }

    private static boolean eligible(final Candidate candidate, final long now) {
        // An admission can disappear from a native stack without reaching a
        // terminal callback (for example after queue compaction or a worker
        // interruption). Keep ADMITTED candidates recoverable so the
        // scheduler can verify native queue/worker presence and re-submit
        // only genuinely lost work.
        if (candidate.state() == State.READY || candidate.state() == State.DISCOVERED
                || candidate.state() == State.ADMITTED) {
            return candidate.nextEligible() <= now;
        }
        if (candidate.state() == State.FAILED || candidate.state() == State.DUPLICATE) {
            return candidate.nextEligible() > 0L && candidate.nextEligible() <= now;
        }
        return false;
    }

    private static boolean isTerminal(final State state) {
        return state == State.INDEXED || state == State.DUPLICATE || state == State.BLOCKED;
    }

    private static int laneRank(final NoveltyClass lane) {
        if (lane == NoveltyClass.NEW_HOST) return 4;
        if (lane == NoveltyClass.NEW_PATH) return 3;
        if (lane == NoveltyClass.PROVEN_EXPANSION) return 2;
        if (lane == NoveltyClass.FRESHNESS) return 1;
        return 0;
    }

    private static Map<String, String> serialize(final Candidate candidate) {
        final Map<String, String> map = new HashMap<>();
        map.put(URL, encode(candidate.url()));
        map.put(INITIATOR, encode(candidate.initiator()));
        map.put(REFERRER, encode(candidate.referrerHash()));
        map.put(NAME, encode(candidate.name()));
        map.put(PROFILE, candidate.profileHandle());
        map.put(DEPTH, Integer.toString(candidate.depth()));
        map.put(TIMEZONE, Integer.toString(candidate.timezoneOffset()));
        map.put(STATE, candidate.state().name());
        map.put(LANE, candidate.lane().name());
        map.put(RELEVANCE, Integer.toString(candidate.relevance()));
        map.put(VERSION, candidate.version());
        map.put(COLLECTIONS, encodeList(candidate.collections()));
        map.put(REASONS, encodeList(candidate.reasons()));
        map.put(FIRST_SEEN, Long.toString(candidate.firstSeen()));
        map.put(LAST_SEEN, Long.toString(candidate.lastSeen()));
        map.put(NEXT_ELIGIBLE, Long.toString(candidate.nextEligible()));
        map.put(LAST_SUBMITTED, Long.toString(candidate.lastSubmitted()));
        map.put(ATTEMPTS, Integer.toString(candidate.attempts()));
        map.put(RECOVERY, Boolean.toString(candidate.recovery()));
        map.put(DISCOVERY_SOURCE, candidate.discoverySource().name());
        return map;
    }

    private static Candidate parse(final byte[] hash, final Map<String, String> map) {
        if (hash == null || map == null) return null;
        try {
            return new Candidate(hash, decode(map.get(URL)), decodeBytes(map.get(INITIATOR)),
                    decodeBytes(map.get(REFERRER)), decode(map.get(NAME)), map.getOrDefault(PROFILE, ""),
                    integer(map.get(DEPTH)), integer(map.get(TIMEZONE)), state(map.get(STATE)),
                    lane(map.get(LANE)), integer(map.get(RELEVANCE)), map.getOrDefault(VERSION, ""),
                    decodeSet(map.get(COLLECTIONS)), decodeList(map.get(REASONS)), longValue(map.get(FIRST_SEEN)),
                    longValue(map.get(LAST_SEEN)), longValue(map.get(NEXT_ELIGIBLE)),
                    longValue(map.get(LAST_SUBMITTED)), integer(map.get(ATTEMPTS)),
                    Boolean.parseBoolean(map.getOrDefault(RECOVERY, "false")),
                    discoverySource(map.get(DISCOVERY_SOURCE)));
        } catch (final RuntimeException ignored) { return null; }
    }

    private static String encode(final String value) {
        return value == null ? "" : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(final byte[] value) {
        return value == null ? "" : Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String decode(final String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static byte[] decodeBytes(final String value) {
        if (value == null || value.isEmpty()) return null;
        return Base64.getUrlDecoder().decode(value);
    }

    private static String encodeList(final Iterable<String> values) {
        final StringBuilder result = new StringBuilder();
        if (values != null) for (final String value : values) {
            if (result.length() > 0) result.append(LIST_SEPARATOR);
            result.append(encode(value));
        }
        return result.toString();
    }

    private static List<String> decodeList(final String value) {
        final List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        for (final String part : value.split(LIST_SEPARATOR, -1)) result.add(decode(part));
        return result;
    }

    private static Set<String> decodeSet(final String value) {
        return new LinkedHashSet<>(decodeList(value));
    }

    private static int integer(final String value) {
        try { return Integer.parseInt(value == null ? "0" : value); }
        catch (final NumberFormatException ignored) { return 0; }
    }

    private static long longValue(final String value) {
        try { return Long.parseLong(value == null ? "0" : value); }
        catch (final NumberFormatException ignored) { return 0L; }
    }

    private static State state(final String value) {
        try { return State.valueOf(value == null ? "DISCOVERED" : value); }
        catch (final IllegalArgumentException ignored) { return State.DISCOVERED; }
    }

    private static NoveltyClass lane(final String value) {
        try { return NoveltyClass.valueOf(value == null ? "SATURATED" : value); }
        catch (final IllegalArgumentException ignored) { return NoveltyClass.SATURATED; }
    }

    private static DiscoverySource discoverySource(final String value) {
        try { return DiscoverySource.valueOf(value == null ? "LEGACY" : value); }
        catch (final IllegalArgumentException ignored) { return DiscoverySource.LEGACY; }
    }
}

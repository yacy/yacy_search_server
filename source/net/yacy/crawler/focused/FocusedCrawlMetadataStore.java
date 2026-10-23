// Crash-safe sidecar metadata for focused crawl URLs.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;

/**
 * Keeps policy metadata in a disk-backed sidecar. Existing YaCy queue rows
 * remain readable by older peers and the latest sidecar record wins after
 * restart.
 *
 * <p>The previous implementation loaded the complete append-only JSON-lines
 * history into a {@code LinkedHashMap}. On a large crawl this retained one
 * object graph per URL indefinitely and could consume most of the JVM heap.
 * The JSON-lines file is intentionally left untouched as a rollback/archive
 * artifact, while new and updated records are stored in a bounded-memory
 * {@link MapHeap}.</p>
 */
public final class FocusedCrawlMetadataStore implements AutoCloseable {
    private static final String VALUE = "metadata";
    private static final int CACHE_SIZE = 4096;

    private final MapHeap heap;
    private final File heapFile;
    private final Map<String, FocusedCrawlMetadata> cache = new LinkedHashMap<String, FocusedCrawlMetadata>(128, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, FocusedCrawlMetadata> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public FocusedCrawlMetadataStore(final File stateDirectory) {
        this.heapFile = new File(stateDirectory, "url-policy-metadata.heap");
        MapHeap opened = null;
        try {
            stateDirectory.mkdirs();
            opened = new MapHeap(this.heapFile,
                    Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 512, ' ');
        } catch (final IOException ignored) { }
        this.heap = opened;
    }

    /** Remember the current classification in the bounded hot cache only. */
    public synchronized void remember(final byte[] urlHash, final List<CrawlPolicyDecision> decisions) {
        final FocusedCrawlMetadata metadata = metadata(urlHash, decisions);
        if (metadata == null) return;
        final String key = cacheKey(urlHash);
        this.cache.put(key, merge(get(urlHash), metadata));
    }

    /** Persist metadata only after YaCy has admitted the URL to its native queue. */
    public synchronized void record(final byte[] urlHash, final List<CrawlPolicyDecision> decisions) {
        final FocusedCrawlMetadata metadata = metadata(urlHash, decisions);
        if (metadata == null) return;
        final String key = cacheKey(urlHash);
        final FocusedCrawlMetadata effective = merge(get(urlHash), metadata);
        final String serialized;
        try {
            serialized = serialize(key, effective);
        } catch (final JSONException ignored) {
            return;
        }
        try {
            if (this.heap != null) {
                final Map<String, String> value = new HashMap<>();
                value.put(VALUE, serialized);
                this.heap.put(storageKey(urlHash), value);
            }
            this.cache.put(key, effective);
        } catch (final RuntimeException ignored) { }
    }

    /** Remove transient or terminal URL metadata and its key from the RAM index. */
    public synchronized void forget(final byte[] urlHash) {
        if (urlHash == null) return;
        this.cache.remove(cacheKey(urlHash));
        if (this.heap != null) {
            try {
                this.heap.delete(storageKey(urlHash));
            } catch (final IOException | RuntimeException ignored) { }
        }
    }

    public synchronized FocusedCrawlMetadata get(final byte[] urlHash) {
        if (urlHash == null) return null;
        final String key = cacheKey(urlHash);
        final FocusedCrawlMetadata cached = this.cache.get(key);
        if (cached != null) return cached;
        if (this.heap == null) return null;
        try {
            final Map<String, String> value = this.heap.get(storageKey(urlHash));
            final FocusedCrawlMetadata metadata = value == null ? null : parse(new JSONObject(value.get(VALUE)));
            if (metadata != null) this.cache.put(key, metadata);
            return metadata;
        } catch (final IOException | SpaceExceededException | JSONException | RuntimeException ignored) {
            return null;
        }
    }

    public synchronized int score(final byte[] urlHash) {
        final FocusedCrawlMetadata metadata = get(urlHash);
        return metadata == null ? 0 : metadata.relevanceScore();
    }

    /** Cheap store diagnostics; {@link MapHeap#memoryBytes()} is an estimate. */
    public synchronized JSONObject statusJSON() {
        final JSONObject status = new JSONObject(true);
        try {
            status.put("records", this.heap == null ? 0 : this.heap.size())
                    .put("estimatedIndexBytes", this.heap == null ? 0L : this.heap.memoryBytes())
                    .put("heapFileBytes", this.heapFile.isFile() ? this.heapFile.length() : 0L)
                    .put("mapHeapCacheEntries", this.heap == null ? 0 : this.heap.cacheSize())
                    .put("metadataCacheEntries", this.cache.size())
                    .put("metadataCacheLimit", CACHE_SIZE);
        } catch (final JSONException e) {
            throw new IllegalStateException("cannot serialize focused metadata store status", e);
        }
        return status;
    }

    private static FocusedCrawlMetadata metadata(final byte[] urlHash,
            final List<CrawlPolicyDecision> decisions) {
        if (urlHash == null) return null;
        return FocusedCrawlMetadata.from(decisions);
    }

    private static FocusedCrawlMetadata merge(final FocusedCrawlMetadata previous,
            final FocusedCrawlMetadata current) {
        if (previous == null) return current;
        final Set<String> reasons = new LinkedHashSet<>(previous.reasonCodes());
        reasons.addAll(current.reasonCodes());
        final Set<String> collections = new LinkedHashSet<>(previous.collections());
        collections.addAll(current.collections());
        final NoveltyClass novelty = current.noveltyClass() == NoveltyClass.NEW_HOST
                || current.noveltyClass() == NoveltyClass.NEW_PATH
                || current.noveltyClass() == NoveltyClass.FRESHNESS
                || previous.noveltyClass() == NoveltyClass.SATURATED
                ? current.noveltyClass() : previous.noveltyClass();
        return new FocusedCrawlMetadata(current.policyIds(), current.policyVersions(),
                Math.max(previous.relevanceScore(), current.relevanceScore()),
                current.priority().rank() >= previous.priority().rank() ? current.priority() : previous.priority(),
                collections, new ArrayList<>(reasons), current.maximumDepth(), current.perHostBudget(),
                current.recrawlIntervalMillis(), novelty);
    }

    @Override
    public synchronized void close() {
        if (this.heap != null) this.heap.close();
    }

    private static String serialize(final String key, final FocusedCrawlMetadata metadata) throws JSONException {
        final JSONObject json = new JSONObject(true);
        json.put("urlHash", key);
        json.put("policyIds", new JSONArray(metadata.policyIds()));
        json.put("policyVersions", new JSONArray(metadata.policyVersions()));
        json.put("relevance", metadata.relevanceScore());
        json.put("priority", metadata.priority().name());
        json.put("collections", new JSONArray(metadata.collections()));
        json.put("reasons", new JSONArray(metadata.reasonCodes()));
        json.put("maximumDepth", metadata.maximumDepth());
        json.put("perHostBudget", metadata.perHostBudget());
        json.put("recrawlIntervalMillis", metadata.recrawlIntervalMillis());
        json.put("noveltyClass", metadata.noveltyClass().name());
        return json.toString();
    }

    private static FocusedCrawlMetadata parse(final JSONObject json) {
        return new FocusedCrawlMetadata(strings(json.optJSONArray("policyIds")), strings(json.optJSONArray("policyVersions")),
                json.optInt("relevance", 0), priority(json.optString("priority", "ORDINARY")),
                new LinkedHashSet<>(strings(json.optJSONArray("collections"))), strings(json.optJSONArray("reasons")),
                json.optInt("maximumDepth", 0), json.optInt("perHostBudget", 0), json.optLong("recrawlIntervalMillis", 0L),
                novelty(json.optString("noveltyClass", "SATURATED")));
    }

    private static PolicyPriority priority(final String value) {
        try { return PolicyPriority.valueOf(value); }
        catch (IllegalArgumentException e) { return PolicyPriority.ORDINARY; }
    }

    private static List<String> strings(final JSONArray values) {
        if (values == null) return Collections.emptyList();
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) result.add(values.optString(i, ""));
        return result;
    }

    private static NoveltyClass novelty(final String value) {
        try { return NoveltyClass.valueOf(value); }
        catch (IllegalArgumentException e) { return NoveltyClass.SATURATED; }
    }

    private static String cacheKey(final byte[] urlHash) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(urlHash);
    }

    private static byte[] storageKey(final byte[] urlHash) {
        return Word.commonHashOrder.encodeSubstring(Digest.encodeMD5Raw(cacheKey(urlHash)), Word.commonHashLength);
    }
}

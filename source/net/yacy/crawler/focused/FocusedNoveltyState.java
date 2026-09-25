// Persistent host/path novelty state for focused crawl profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;

/**
 * Crash-safe, bounded-memory novelty state. Only host and path-prefix keys
 * are retained; individual URL state remains in YaCy's index and queue.
 */
public final class FocusedNoveltyState implements AutoCloseable {

    public static final class Observation {
        private final String hostKey;
        private final String pathKey;
        private final boolean newHost;
        private final boolean newPath;

        private Observation(final String hostKey, final String pathKey, final boolean newHost, final boolean newPath) {
            this.hostKey = hostKey;
            this.pathKey = pathKey;
            this.newHost = newHost;
            this.newPath = newPath;
        }

        public String hostKey() { return this.hostKey; }
        public String pathKey() { return this.pathKey; }
        public boolean newHost() { return this.newHost; }
        public boolean newPath() { return this.newPath; }
    }

    private final MapHeap heap;

    public FocusedNoveltyState(final File stateDirectory, final String policyId) {
        MapHeap opened = null;
        try {
            stateDirectory.mkdirs();
            opened = new MapHeap(new File(stateDirectory, policyId + ".novelty.heap"),
                    Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 512, ' ');
        } catch (final IOException ignored) { }
        this.heap = opened;
    }

    public synchronized Observation observe(final DigestURL url, final int pathPrefixDepth) {
        if (this.heap == null || url == null) return new Observation("", "", true, true);
        final String host = hostKey(url);
        final String path = pathKey(url, pathPrefixDepth);
        final Map<String, String> hostState = read(host);
        final Map<String, String> pathState = read(path);
        final boolean newHost = count(hostState, "seen") == 0L;
        final boolean newPath = count(pathState, "seen") == 0L;
        increment(host, hostState, "seen");
        increment(path, pathState, "seen");
        return new Observation(host, path, newHost, newPath);
    }

    public synchronized void recordAdmission(final Observation observation, final boolean indexedKnown) {
        if (this.heap == null || observation == null) return;
        update(observation.hostKey, "admitted", 1L);
        update(observation.pathKey, "admitted", 1L);
        update(observation.hostKey, indexedKnown ? "recrawled" : "newDocuments", 1L);
        update(observation.pathKey, indexedKnown ? "recrawled" : "newDocuments", 1L);
    }

    public synchronized void recordOutcome(final Observation observation, final boolean newDocument,
            final boolean failed) {
        if (this.heap == null || observation == null) return;
        final String field = failed ? "failed" : newDocument ? "newDocuments" : "recrawled";
        update(observation.hostKey, field, 1L);
        update(observation.pathKey, field, 1L);
    }

    public synchronized Map<String, String> get(final String key) {
        return read(key);
    }

    @Override
    public synchronized void close() {
        if (this.heap != null) this.heap.close();
    }

    private void increment(final String key, final Map<String, String> state, final String field) {
        state.put(field, Long.toString(count(state, field) + 1L));
        state.put("lastSeen", Long.toString(System.currentTimeMillis()));
        write(key, state);
    }

    private void update(final String key, final String field, final long amount) {
        final Map<String, String> state = read(key);
        increment(key, state, field);
    }

    private Map<String, String> read(final String key) {
        if (this.heap == null || key == null || key.isEmpty()) return new HashMap<>();
        try {
            final Map<String, String> value = this.heap.get(storageKey(key));
            return value == null ? new HashMap<>() : new HashMap<>(value);
        } catch (final IOException | SpaceExceededException | RuntimeException ignored) {
            return new HashMap<>();
        }
    }

    private void write(final String key, final Map<String, String> state) {
        if (this.heap == null || key == null || key.isEmpty()) return;
        this.heap.put(storageKey(key), state);
    }

    private static long count(final Map<String, String> state, final String field) {
        try { return Long.parseLong(state.getOrDefault(field, "0")); }
        catch (final NumberFormatException ignored) { return 0L; }
    }

    private static byte[] storageKey(final String key) {
        return Word.commonHashOrder.encodeSubstring(Digest.encodeMD5Raw(key), Word.commonHashLength);
    }

    public static String hostKey(final DigestURL url) {
        String host = url == null || url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        return "host:" + host;
    }

    public static String pathKey(final DigestURL url, final int pathPrefixDepth) {
        if (url == null) return "path:";
        final String raw = url.getPath() == null ? "/" : url.getPath().toLowerCase(Locale.ROOT);
        final String[] segments = raw.split("/");
        final StringBuilder prefix = new StringBuilder("path:").append(hostKey(url)).append('/');
        int added = 0;
        for (final String segment : segments) {
            if (segment.isEmpty()) continue;
            prefix.append(segment).append('/');
            if (++added >= Math.max(1, Math.min(8, pathPrefixDepth))) break;
        }
        return prefix.toString();
    }
}

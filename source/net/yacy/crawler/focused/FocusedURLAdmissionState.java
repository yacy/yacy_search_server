// Persistent URL admission state for focused crawl profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;

/**
 * Crash-safe URL admission ledger for one focused profile. It records URLs
 * after they are successfully handed to YaCy's native queue, preventing
 * repeated link discoveries from re-entering policy evaluation until the
 * normal recrawl rules make them eligible again.
 */
public final class FocusedURLAdmissionState implements AutoCloseable {
    private static final String LAST_ADMITTED = "lastAdmitted";
    private static final String VERSION = "version";

    private final MapHeap heap;

    public FocusedURLAdmissionState(final File stateDirectory, final String policyId) {
        MapHeap opened = null;
        try {
            stateDirectory.mkdirs();
            opened = new MapHeap(new File(stateDirectory, policyId + ".admission.heap"),
                    Word.commonHashLength, NaturalOrder.naturalOrder, 1024 * 64, 512, ' ');
        } catch (final IOException ignored) { }
        this.heap = opened;
    }

    /**
     * Return true when this profile has already admitted the URL and the
     * candidate is not currently eligible for a normal or focused recrawl.
     */
    public synchronized boolean shouldSuppress(final byte[] urlHash, final String policyVersion,
            final CrawlPolicyContext context, final long focusedRecrawlIntervalMillis,
            final boolean focusedRecrawlEnabled, final long retrySuppressionMillis) {
        if (this.heap == null || urlHash == null || context == null) return false;
        final Map<String, String> state = read(urlHash);
        if (!policyVersion.equals(state.getOrDefault(VERSION, ""))) return false;
        final long lastAdmitted = longValue(state.get(LAST_ADMITTED));
        if (lastAdmitted <= 0L) return false;
        final long now = System.currentTimeMillis();
        if (!context.knownInIndex()) return now - lastAdmitted < Math.max(0L, retrySuppressionMillis);
        if (context.profileRecrawlDue()) return false;
        if (focusedRecrawlEnabled && focusedRecrawlIntervalMillis > 0L
                && context.indexedAt() <= now - focusedRecrawlIntervalMillis) return false;
        return true;
    }

    /** Record a URL only after YaCy accepted it into a native queue. */
    public synchronized void recordAdmission(final DigestURL url, final String policyVersion) {
        if (url == null) return;
        recordAdmission(url.hash(), policyVersion);
    }

    public synchronized void recordAdmission(final byte[] urlHash, final String policyVersion) {
        if (this.heap == null || urlHash == null) return;
        final Map<String, String> state = read(urlHash);
        state.put(VERSION, policyVersion == null ? "" : policyVersion);
        state.put(LAST_ADMITTED, Long.toString(System.currentTimeMillis()));
        write(urlHash, state);
    }

    @Override
    public synchronized void close() {
        if (this.heap != null) this.heap.close();
    }

    private Map<String, String> read(final byte[] urlHash) {
        if (this.heap == null || urlHash == null) return new HashMap<>();
        try {
            final Map<String, String> value = this.heap.get(storageKey(urlHash));
            return value == null ? new HashMap<>() : new HashMap<>(value);
        } catch (final IOException | SpaceExceededException | RuntimeException ignored) {
            return new HashMap<>();
        }
    }

    private void write(final byte[] urlHash, final Map<String, String> state) {
        if (this.heap == null || urlHash == null) return;
        try { this.heap.put(storageKey(urlHash), state); }
        catch (final RuntimeException ignored) { }
    }

    private static byte[] storageKey(final byte[] urlHash) {
        return Word.commonHashOrder.encodeSubstring(Digest.encodeMD5Raw(
                Base64.getUrlEncoder().withoutPadding().encodeToString(urlHash)), Word.commonHashLength);
    }

    private static long longValue(final String value) {
        try { return Long.parseLong(value == null ? "0" : value); }
        catch (final NumberFormatException ignored) { return 0L; }
    }
}

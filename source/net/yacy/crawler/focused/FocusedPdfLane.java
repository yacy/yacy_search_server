// Serialized PDF admission for focused crawl profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Response;
import net.yacy.kelondro.util.MemoryControl;

/**
 * Admission control for PDFs belonging to focused profiles.
 *
 * PDF parsing is unusually memory intensive in comparison with HTML parsing.
 * This lane deliberately keeps only one focused PDF between fetch and parsing.
 * The queue itself is persisted by {@code NoticedURL}; this class only owns
 * the in-process permit and its restart-safe counters are exposed as status.
 */
public final class FocusedPdfLane {

    private static final long PDF_PARSER_RESERVATION = 200L * 1024L * 1024L;
    private static final long MIN_PDF_HEADROOM = 256L * 1024L * 1024L;

    private final Semaphore permit = new Semaphore(1, true);
    private final Set<String> active = new HashSet<>();
    private final BooleanSupplier memoryAvailability;
    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public FocusedPdfLane() {
        this(FocusedPdfLane::systemMemoryReady);
    }

    FocusedPdfLane(final BooleanSupplier memoryAvailability) {
        this.memoryAvailability = Objects.requireNonNull(memoryAvailability);
    }

    /** A focused profile name is deliberately generic: all focused profiles use this namespace. */
    public static boolean isFocusedProfile(final CrawlProfile profile) {
        return profile != null && profile.name() != null && profile.name().startsWith("focused-");
    }

    /** Detect PDF URLs before fetching; content-type detection remains in the parser path. */
    public static boolean isPdfURL(final DigestURL url) {
        if (url == null) return false;
        final String path = url.getPath();
        final int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') && path.length() - dot == 4
                && path.regionMatches(true, dot + 1, "pdf", 0, 3);
    }

    public static boolean isPdfResponse(final Response response) {
        if (response == null) return false;
        if (isPdfURL(response.url())) return true;
        final ResponseHeader responseHeader = response.getResponseHeader();
        return responseHeader != null && isPdfMimeType(responseHeader.getContentType());
    }

    /** Match the normalized media type without allocating trimmed/lowercase/substrings. */
    private static boolean isPdfMimeType(final String mime) {
        if (mime == null) return false;
        int start = 0;
        int end = mime.length();
        while (start < end && mime.charAt(start) <= ' ') start++;
        while (end > start && mime.charAt(end - 1) <= ' ') end--;
        final int parameter = mime.indexOf(';', start);
        if (parameter >= 0 && parameter < end) end = parameter;
        final int length = end - start;
        return (length == 15 && mime.regionMatches(true, start, "application/pdf", 0, length))
                || (length == 17 && mime.regionMatches(true, start, "application/x-pdf", 0, length))
                || (length >= 4 && mime.regionMatches(true, end - 4, "/pdf", 0, 4));
    }

    /** Whether starting another focused PDF is safe enough for the next parser allocation. */
    public boolean canStart() {
        return this.permit.availablePermits() > 0 && memoryReady();
    }

    /**
     * Keep enough room for PDFBox's reservation and a safety margin. The
     * resource guard threshold is shared with focused crawl recovery so a PDF
     * cannot start in the same low-memory band that would keep the crawler
     * paused.
     */
    public static long requiredMemory() {
        return Math.max(PDF_PARSER_RESERVATION + MIN_PDF_HEADROOM,
                FocusedResourceGuard.recoveryThreshold(MemoryControl.maxMemory()));
    }

    /** Admit a pre-identified PDF before its network fetch begins. */
    public boolean tryAcquire(final DigestURL url) {
        if (!canStart() || !this.permit.tryAcquire()) {
            this.blocked.incrementAndGet();
            return false;
        }
        synchronized (this.active) {
            this.active.add(key(url));
        }
        this.admitted.incrementAndGet();
        return true;
    }

    /**
     * Acquire a permit for a response whose content type identified it as a
     * PDF even though its URL did not end in .pdf. Usually this is a no-op
     * because URL admission already owns the permit.
     */
    public boolean acquireForParsing(final Response response) throws InterruptedException {
        if (response == null || !isFocusedProfile(response.profile()) || !isPdfResponse(response)) return false;
        final String key = key(response.url());
        final boolean alreadyActive;
        synchronized (this.active) {
            alreadyActive = this.active.contains(key);
        }
        if (alreadyActive) {
            // Do not hold the active-set monitor while waiting for heap headroom:
            // loader completion and release paths need this monitor to free slots.
            waitForMemory();
            synchronized (this.active) {
                if (this.active.contains(key)) return true;
            }
        }
        while (true) {
            if (this.permit.tryAcquire()) {
                if (memoryReady()) {
                    synchronized (this.active) {
                        this.active.add(key);
                    }
                    this.admitted.incrementAndGet();
                    return true;
                }
                this.permit.release();
            }
            this.blocked.incrementAndGet();
            Thread.sleep(1000L);
        }
    }

    private static boolean systemMemoryReady() {
        return !MemoryControl.shortStatus() && MemoryControl.available() >= requiredMemory();
    }

    private boolean memoryReady() {
        return this.memoryAvailability.getAsBoolean();
    }

    private void waitForMemory() throws InterruptedException {
        while (!memoryReady()) {
            this.blocked.incrementAndGet();
            Thread.sleep(1000L);
        }
    }

    /** Release a permit on parser completion, load failure, or shutdown. */
    public void release(final DigestURL url, final boolean success) {
        final boolean owned;
        synchronized (this.active) {
            owned = this.active.remove(key(url));
        }
        if (!owned) return;
        this.permit.release();
        if (success) this.completed.incrementAndGet();
        else this.failed.incrementAndGet();
    }

    public boolean active(final DigestURL url) {
        synchronized (this.active) {
            return this.active.contains(key(url));
        }
    }

    public int activeCount() {
        synchronized (this.active) {
            return this.active.size();
        }
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject(true);
        try {
            json.put("concurrency", 1)
                    .put("active", activeCount())
                    .put("available", this.permit.availablePermits())
                    .put("admitted", this.admitted.get())
                    .put("completed", this.completed.get())
                    .put("blocked", this.blocked.get())
                    .put("failed", this.failed.get())
                    .put("minimumAdmissionBytes", requiredMemory())
                    .put("memoryAvailable", MemoryControl.available())
                    .put("memoryShort", MemoryControl.shortStatus());
        } catch (final JSONException e) {
            throw new IllegalStateException("cannot serialize focused PDF lane status", e);
        }
        return json;
    }

    private static String key(final DigestURL url) {
        return url == null ? "" : ASCII.String(url.hash());
    }
}

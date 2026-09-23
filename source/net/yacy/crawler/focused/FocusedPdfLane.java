// Serialized PDF admission for focused crawl profiles.
// SPDX-License-Identifier: GPL-2.0-or-later
package net.yacy.crawler.focused;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
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
    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    /** A focused profile name is deliberately generic: all focused profiles use this namespace. */
    public static boolean isFocusedProfile(final CrawlProfile profile) {
        return profile != null && profile.name() != null && profile.name().startsWith("focused-");
    }

    /** Detect PDF URLs before fetching; content-type detection remains in the parser path. */
    public static boolean isPdfURL(final DigestURL url) {
        if (url == null) return false;
        final String fileName = url.getFileName();
        if (fileName == null) return false;
        final String extension = MultiProtocolURL.getFileExtension(fileName);
        return extension != null && "pdf".equals(extension.toLowerCase(Locale.ROOT));
    }

    public static boolean isPdfResponse(final Response response) {
        if (response == null) return false;
        final String mime = response.getMimeType();
        return (mime != null && (mime.equals("application/pdf") || mime.equals("application/x-pdf")
                || mime.endsWith("/pdf"))) || isPdfURL(response.url());
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
        synchronized (this.active) {
            if (this.active.contains(key)) {
                waitForMemory();
                return true;
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

    private static boolean memoryReady() {
        return !MemoryControl.shortStatus() && MemoryControl.available() >= requiredMemory();
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

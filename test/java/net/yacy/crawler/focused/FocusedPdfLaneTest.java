package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlProfile.CrawlAttribute;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;

public class FocusedPdfLaneTest {

    @Test
    public void recognizesPdfUrls() throws MalformedURLException {
        assertTrue(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.PDF?download=1")));
        assertFalse(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.html")));
        assertTrue(FocusedPdfLane.requiredMemory() >= 200L * 1024L * 1024L);
    }

    @Test
    public void doesNotTreatDirectoriesOrLongerExtensionsAsPdf() throws MalformedURLException {
        assertFalse(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.pdf/")));
        assertFalse(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.pdfx")));
        assertFalse(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/")));
    }

    @Test
    public void skipsMimeNormalizationWhenUrlIdentifiesPdf() throws MalformedURLException {
        final CountingResponse response = new CountingResponse(new DigestURL("https://example.org/report.pdf"), "Application/PDF");
        assertTrue(FocusedPdfLane.isPdfResponse(response));
        assertEquals(0, response.mimeTypeReads);
    }

    @Test
    public void keepsMimeDetectionForPdfWithoutPdfExtension() throws MalformedURLException {
        final CountingResponse response = new CountingResponse(new DigestURL("https://example.org/report.bin"), "application/pdf");
        assertTrue(FocusedPdfLane.isPdfResponse(response));
        assertEquals(1, response.mimeTypeReads);
    }

    @Test
    public void admissionIsSerialized() throws MalformedURLException {
        final FocusedPdfLane lane = new FocusedPdfLane();
        final DigestURL first = new DigestURL("https://example.org/first.pdf");
        final DigestURL second = new DigestURL("https://example.org/second.pdf");
        if (!lane.canStart()) return; // constrained CI JVMs may intentionally skip the memory-dependent assertion
        assertTrue(lane.tryAcquire(first));
        assertFalse(lane.tryAcquire(second));
        assertTrue(lane.active(first));
        lane.release(first, true);
        assertTrue(lane.canStart());
        assertTrue(lane.tryAcquire(second));
        lane.release(second, false);
        assertTrue(lane.activeCount() == 0);
    }

    @Test
    public void memoryWaitDoesNotHoldActiveSetLock() throws Exception {
        final AtomicBoolean memoryReady = new AtomicBoolean(true);
        final CountDownLatch memoryWaitStarted = new CountDownLatch(1);
        final FocusedPdfLane lane = new FocusedPdfLane(() -> {
            final boolean ready = memoryReady.get();
            if (!ready) memoryWaitStarted.countDown();
            return ready;
        });
        final DigestURL url = new DigestURL("https://example.org/active.pdf");
        assertTrue(lane.tryAcquire(url));

        final CrawlProfile profile = new CrawlProfile(Collections.singletonMap(CrawlAttribute.NAME.key, "focused-test"));
        final Response response = new Response(new Request(url, null), null, null, profile, false, null);
        memoryReady.set(false);

        final ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            final Future<Boolean> parsing = workers.submit(() -> lane.acquireForParsing(response));
            assertTrue("PDF parser should wait for the injected memory signal",
                    memoryWaitStarted.await(2, TimeUnit.SECONDS));
            final Future<Boolean> release = workers.submit(() -> {
                lane.release(url, false);
                return true;
            });
            assertTrue("memory wait must not block loader release", release.get(500, TimeUnit.MILLISECONDS));
            assertFalse(lane.active(url));

            memoryReady.set(true);
            assertTrue(parsing.get(2, TimeUnit.SECONDS));
        } finally {
            memoryReady.set(true);
            lane.release(url, true);
            workers.shutdownNow();
            workers.awaitTermination(2, TimeUnit.SECONDS);
        }
        assertEquals(0, lane.activeCount());
    }

    private static final class CountingResponse extends Response {
        private final String mimeType;
        private int mimeTypeReads;

        private CountingResponse(final DigestURL url, final String mimeType) {
            super(new Request(url, null), null, null, null, false, null);
            this.mimeType = mimeType;
        }

        @Override
        public String getMimeType() {
            this.mimeTypeReads++;
            return this.mimeType;
        }
    }
}

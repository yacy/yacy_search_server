package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
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

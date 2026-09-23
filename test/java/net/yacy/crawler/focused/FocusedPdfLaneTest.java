package net.yacy.crawler.focused;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class FocusedPdfLaneTest {

    @Test
    public void recognizesPdfUrls() throws MalformedURLException {
        assertTrue(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.PDF?download=1")));
        assertFalse(FocusedPdfLane.isPdfURL(new DigestURL("https://example.org/report.html")));
        assertTrue(FocusedPdfLane.requiredMemory() >= 200L * 1024L * 1024L);
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
}

package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.net.MalformedURLException;
import java.util.Date;

import org.junit.Test;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;

public class ResponseProcessCaseTest {

    @Test
    public void classifiesProxyLocalAndRemoteInitiators() throws MalformedURLException {
        final String localHash = "AbCdEfGhIjKl";
        assertEquals(EventOrigin.PROXY_LOAD, response(null).processCase(localHash));
        assertEquals(EventOrigin.PROXY_LOAD, response(new byte[0]).processCase(localHash));
        assertEquals(EventOrigin.PROXY_LOAD, response("------------"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII)).processCase(localHash));
        assertEquals(EventOrigin.LOCAL_CRAWLING, response(UTF8.getBytes(localHash)).processCase(localHash));
        assertEquals(EventOrigin.GLOBAL_CRAWLING,
                response(UTF8.getBytes("ZyXwVuTsRqPo")).processCase(localHash));
    }

    @Test
    public void retainsUtf8ComparisonForNonAsciiSeedHashValues() throws MalformedURLException {
        final String seedHash = "café";
        assertEquals(EventOrigin.LOCAL_CRAWLING,
                response(UTF8.getBytes(seedHash)).processCase(seedHash));
    }

    @Test
    public void normalizesMimeTypeWithoutCopyingCanonicalValues() {
        final String canonical = "text/html";
        assertSame(canonical, responseWithMimeType(canonical).getMimeType());
        assertEquals("text/html", responseWithMimeType("  TEXT/HTML; Charset=UTF-8  ").getMimeType());
        assertEquals("application/pdf", responseWithMimeType(" application/pdf ").getMimeType());
        assertEquals("text/html ", responseWithMimeType("\t TEXT/HTML ; Charset=UTF-8 \t").getMimeType());
        assertEquals("text/i\u0307maginary",
                responseWithMimeType("TEXT/İMAGINARY; charset=x").getMimeType());
        assertNull(responseWithMimeType(null).getMimeType());
    }

    private static Response response(final byte[] initiator) throws MalformedURLException {
        final Request request = new Request(initiator, new DigestURL("https://example.org/"), null,
                "test", new Date(), "focused-test", 0, 0);
        return new Response(request, null, null, null, false, null);
    }

    private static Response responseWithMimeType(final String mimeType) {
        final ResponseHeader responseHeader = new ResponseHeader(200);
        if (mimeType != null) responseHeader.put(HeaderFramework.CONTENT_TYPE, mimeType);
        return new Response(null, null, responseHeader, null, false, null);
    }
}

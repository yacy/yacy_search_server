package net.yacy.crawler.focused;

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.util.Date;

import org.junit.Test;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
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

    private static Response response(final byte[] initiator) throws MalformedURLException {
        final Request request = new Request(initiator, new DigestURL("https://example.org/"), null,
                "test", new Date(), "focused-test", 0, 0);
        return new Response(request, null, null, null, false, null);
    }
}

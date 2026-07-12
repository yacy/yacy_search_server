package net.yacy.http;

import org.junit.Assert;
import org.junit.Test;

public class HttpServerBootstrapConfigTest {

    @Test
    public void testAcceptorCountIsClamped() {
        Assert.assertEquals(1, HttpServerBootstrapConfig.acceptorCountFor(1));
        Assert.assertEquals(1, HttpServerBootstrapConfig.acceptorCountFor(2));
        Assert.assertEquals(2, HttpServerBootstrapConfig.acceptorCountFor(4));
        Assert.assertEquals(4, HttpServerBootstrapConfig.acceptorCountFor(8));
        Assert.assertEquals(4, HttpServerBootstrapConfig.acceptorCountFor(64));
    }

    @Test
    public void testFixedConnectorLimits() {
        Assert.assertEquals(16_384, HttpServerBootstrapConfig.REQUEST_HEADER_SIZE);
        Assert.assertEquals(9_000L, HttpServerBootstrapConfig.CONNECTOR_IDLE_TIMEOUT_MILLIS);
        Assert.assertEquals(128, HttpServerBootstrapConfig.ACCEPT_QUEUE_SIZE);
        Assert.assertEquals(4_096, HttpServerBootstrapConfig.REQUEST_INFLATE_BUFFER_SIZE);
        Assert.assertEquals(200_000, HttpServerBootstrapConfig.MAX_FORM_CONTENT_SIZE);
    }
}

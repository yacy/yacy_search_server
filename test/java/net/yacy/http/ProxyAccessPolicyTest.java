package net.yacy.http;

import org.junit.Assert;
import org.junit.Test;

public class ProxyAccessPolicyTest {

    @Test
    public void testConfiguredClientPatterns() {
        Assert.assertTrue(ProxyAccessPolicy.isClientAllowed("*", "198.51.100.7"));
        Assert.assertTrue(ProxyAccessPolicy.isClientAllowed(
                "localhost,127\\.0\\.0\\.1,192\\.168\\..*", "127.0.0.1"));
        Assert.assertTrue(ProxyAccessPolicy.isClientAllowed(
                "localhost,127\\.0\\.0\\.1,192\\.168\\..*", "192.168.2.15"));
        Assert.assertFalse(ProxyAccessPolicy.isClientAllowed(
                "localhost,127\\.0\\.0\\.1,192\\.168\\..*", "198.51.100.7"));
        Assert.assertFalse(ProxyAccessPolicy.isClientAllowed(null, "127.0.0.1"));
        Assert.assertFalse(ProxyAccessPolicy.isClientAllowed("127\\.0\\.0\\.1", null));
    }
}

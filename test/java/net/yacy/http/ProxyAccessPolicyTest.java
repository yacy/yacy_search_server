package net.yacy.http;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void testMatcherCacheTracksRuntimeConfigurationChanges() {
        Assert.assertTrue(ProxyAccessPolicy.isClientAllowed("127\\.0\\.0\\.1", "127.0.0.1"));
        Assert.assertFalse(ProxyAccessPolicy.isClientAllowed("192\\.0\\.2\\..*", "127.0.0.1"));
        Assert.assertTrue(ProxyAccessPolicy.isClientAllowed("127\\.0\\.0\\.1", "127.0.0.1"));
    }

    @Test
    public void testConcurrentClientChecksDoNotShareMutableMatchers() throws Exception {
        final String patterns = "127\\.0\\.0\\.1,192\\.0\\.2\\..*";
        final ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            final Future<Boolean> allowed = workers.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    if (!ProxyAccessPolicy.isClientAllowed(patterns, "192.0.2.15")) return false;
                }
                return true;
            });
            final Future<Boolean> denied = workers.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    if (ProxyAccessPolicy.isClientAllowed(patterns, "198.51.100.7")) return false;
                }
                return true;
            });
            Assert.assertTrue(allowed.get(5, TimeUnit.SECONDS));
            Assert.assertTrue(denied.get(5, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
    }
}

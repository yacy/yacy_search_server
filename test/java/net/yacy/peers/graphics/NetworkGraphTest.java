// NetworkGraphTest.java
// This is a part of YaCy, a peer-to-peer based web search engine

package net.yacy.peers.graphics;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for the network picture cache in {@link NetworkGraph}. */
public class NetworkGraphTest {

    private NetworkGraph.Cache cache;

    @Before
    public void setUp() {
        this.cache = new NetworkGraph.Cache(60_000, 16);
    }

    @After
    public void clearCache() {
        this.cache.clear();
    }

    @Test
    public void testNetworkPictureCacheIsPartitionedByImageSize() {
        final EncodedImage small = new EncodedImage(new byte[] {1}, "png", false);
        final EncodedImage large = new EncodedImage(new byte[] {2}, "png", false);

        this.cache.put(825, 450, 0, small);
        this.cache.put(1280, 900, 0, large);

        assertSame(small, this.cache.getCached(825, 450, 0));
        assertSame(large, this.cache.getCached(1280, 900, 0));
        assertNull(this.cache.getCached(825, 900, 0));
    }

    @Test
    public void testNetworkPictureCacheIsPartitionedByCoronaAngle() {
        final EncodedImage firstPhase = new EncodedImage(new byte[] {1}, "png", false);
        final EncodedImage secondPhase = new EncodedImage(new byte[] {2}, "png", false);

        this.cache.put(1280, 900, 0, firstPhase);
        this.cache.put(1280, 900, 60, secondPhase);

        assertSame(firstPhase, this.cache.getCached(1280, 900, 0));
        assertSame(secondPhase, this.cache.getCached(1280, 900, 60));
        assertNull(this.cache.getCached(1280, 900, 120));
    }

    @Test
    public void testExpiredNetworkPictureRemainsAvailableForBusyFallback() {
        final EncodedImage image = new EncodedImage(new byte[] {1}, "png", false);
        this.cache = new NetworkGraph.Cache(0, 16);
        this.cache.put(825, 450, 0, image);

        assertNull(this.cache.getFresh(825, 450, 0));
        assertSame(image, this.cache.getCached(825, 450, 0));
    }
}

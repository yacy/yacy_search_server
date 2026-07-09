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

    @Before
    @After
    public void clearCache() {
        NetworkGraph.clearcache();
    }

    @Test
    public void testNetworkPictureCacheIsPartitionedByImageSize() {
        final EncodedImage small = new EncodedImage(new byte[] {1}, "png", false);
        final EncodedImage large = new EncodedImage(new byte[] {2}, "png", false);

        NetworkGraph.cacheNetworkPicture(825, 450, 0, small);
        NetworkGraph.cacheNetworkPicture(1280, 900, 0, large);

        assertSame(small, NetworkGraph.getCachedNetworkPicture(825, 450, 0));
        assertSame(large, NetworkGraph.getCachedNetworkPicture(1280, 900, 0));
        assertNull(NetworkGraph.getCachedNetworkPicture(825, 900, 0));
    }

    @Test
    public void testNetworkPictureCacheIsPartitionedByCoronaAngle() {
        final EncodedImage firstPhase = new EncodedImage(new byte[] {1}, "png", false);
        final EncodedImage secondPhase = new EncodedImage(new byte[] {2}, "png", false);

        NetworkGraph.cacheNetworkPicture(1280, 900, 0, firstPhase);
        NetworkGraph.cacheNetworkPicture(1280, 900, 60, secondPhase);

        assertSame(firstPhase, NetworkGraph.getCachedNetworkPicture(1280, 900, 0));
        assertSame(secondPhase, NetworkGraph.getCachedNetworkPicture(1280, 900, 60));
        assertNull(NetworkGraph.getCachedNetworkPicture(1280, 900, 120));
    }

    @Test
    public void testExpiredNetworkPictureRemainsAvailableForBusyFallback() {
        final EncodedImage image = new EncodedImage(new byte[] {1}, "png", false);
        NetworkGraph.cacheNetworkPicture(825, 450, 0, image);

        assertNull(NetworkGraph.getCachedNetworkPicture(825, 450, 0, 0));
        assertSame(image, NetworkGraph.getCachedNetworkPicture(825, 450, 0));
    }
}

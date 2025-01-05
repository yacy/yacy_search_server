package net.yacy.cora.document.feed;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class RSSFeedTest {

    /**
     * Test of getChannel method, of class RSSFeed.
     */
    @Test
    public void testGetChannel() {
        RSSFeed feed = new RSSFeed(Integer.MAX_VALUE);

        // channel is required in RSS 2.0 and accessed in code w/o != null checks
        RSSMessage channel = feed.getChannel();
        assertNotNull(channel);

    }

}

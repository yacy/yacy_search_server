package net.yacy.cora.document.feed;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class RSSFeedTest {

    /**
     * Test of getChannel method, of class RSSFeed.
     */
    @Test
    public void testGetChannel() throws IOException {
        RSSFeed feed = new RSSFeed(Integer.MAX_VALUE);

        // channel is required in RSS 2.0 and accessed in code w/o != null checks
        RSSMessage channel = feed.getChannel();
        assertNotNull(channel);

    }

}

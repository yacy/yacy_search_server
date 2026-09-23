package net.yacy.crawler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Date;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.WorkTables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Checks native focused lanes remain durable and separate from ordinary queue work. */
public class NoticedURLFocusedLaneTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void focusedAndOrdinaryEntriesStaySeparatedAcrossReopen() throws Exception {
        final File root = this.temporaryFolder.newFolder("root");
        final File queuePath = new File(root, "queues");
        final RobotsTxt robots = new RobotsTxt(new WorkTables(new File(root, "data")), null, 2);
        NoticedURL queues = null;
        try {
            queues = new NoticedURL(queuePath, 100, true);
            final Request focused = request("file:///focused/research");
            final Request ordinary = request("file:///ordinary/page");
            assertNull(queues.push(NoticedURL.StackType.FOCUSED_NEW_HOST, focused, null, robots));
            assertNull(queues.push(NoticedURL.StackType.LOCAL, ordinary, null, robots));
            assertEquals(1, queues.stackSize(NoticedURL.StackType.FOCUSED));
            assertEquals(1, queues.stackSize(NoticedURL.StackType.FOCUSED_NEW_HOST));
            assertEquals(1, queues.stackSize(NoticedURL.StackType.LOCAL));
            assertTrue(queues.contains(focused.url().hash()));
            assertTrue(queues.contains(ordinary.url().hash()));
            queues.close();
            queues = null;

            queues = new NoticedURL(queuePath, 100, true);
            assertEquals(1, queues.stackSize(NoticedURL.StackType.FOCUSED));
            assertEquals(1, queues.stackSize(NoticedURL.StackType.FOCUSED_NEW_HOST));
            assertEquals(1, queues.stackSize(NoticedURL.StackType.LOCAL));
            assertTrue(queues.contains(focused.url().hash()));
            assertTrue(queues.contains(ordinary.url().hash()));
            assertFalse(queues.isEmpty(NoticedURL.StackType.FOCUSED));
            assertFalse(queues.isEmpty(NoticedURL.StackType.LOCAL));
        } finally {
            if (queues != null) queues.close();
            robots.close();
        }
    }

    private static Request request(final String value) throws Exception {
        return new Request(null, new DigestURL(value), null, "test", new Date(), "focusedlane0", 0, 0);
    }
}

package net.yacy.crawler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
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

    @Test
    public void pendingRobotsFetchDoesNotBlockOrClearFocusedQueue() throws Exception {
        final File root = this.temporaryFolder.newFolder("robots-pending");
        final RobotsTxt robots = new BlockingRobotsTxt(new WorkTables(new File(root, "data")));
        final NoticedURL queues = new NoticedURL(new File(root, "queues"), 100, true);
        final BlockingRobotsTxt blockingRobots = (BlockingRobotsTxt) robots;
        final Request focused = request("http://8.8.8.8/research");
        try {
            assertNull(queues.push(NoticedURL.StackType.FOCUSED_NEW_HOST, focused, null, robots));
            assertTrue("the asynchronous robots prefetch should start",
                    blockingRobots.awaitLoadStarted(5, TimeUnit.SECONDS));

            final FutureTask<Request> pop = new FutureTask<>(
                    () -> queues.pop(NoticedURL.StackType.FOCUSED, false, null, robots));
            final Thread dispatcher = new Thread(pop, "focused-robots-pop-test");
            dispatcher.start();
            assertNull("the dispatcher should yield while robots.txt is pending",
                    pop.get(2, TimeUnit.SECONDS));
            assertEquals("the request must remain in the native queue", 1,
                    queues.stackSize(NoticedURL.StackType.FOCUSED));
            assertEquals("the existing async fetch should be coalesced", 1,
                    blockingRobots.loadCount());
        } finally {
            blockingRobots.releaseLoads();
            queues.close();
            robots.close();
        }
    }

    private static final class BlockingRobotsTxt extends RobotsTxt {
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLoads = new CountDownLatch(1);
        private final AtomicInteger loads = new AtomicInteger();

        private BlockingRobotsTxt(final WorkTables tables) {
            super(tables, null, 2);
        }

        @Override
        protected net.yacy.crawler.retrieval.Response loadRobots(
                final DigestURL robotsURL, final ClientIdentification.Agent agent) {
            this.loads.incrementAndGet();
            this.loadStarted.countDown();
            try {
                this.releaseLoads.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        private boolean awaitLoadStarted(final long timeout, final TimeUnit unit) throws InterruptedException {
            return this.loadStarted.await(timeout, unit);
        }

        private int loadCount() {
            return this.loads.get();
        }

        private void releaseLoads() {
            this.releaseLoads.countDown();
        }
    }

    private static Request request(final String value) throws Exception {
        return new Request(null, new DigestURL(value), null, "test", new Date(), "focusedlane0", 0, 0);
    }
}

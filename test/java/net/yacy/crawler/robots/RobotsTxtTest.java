package net.yacy.crawler.robots;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.BEncodedHeap;

public class RobotsTxtTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private WorkTables tables;
    private RobotsTxt robots;
    private Method lockForHost;

    @Before
    public void setUp() throws Exception {
        this.tables = new WorkTables(this.temporaryFolder.newFolder());
        // No loader: the database recheck must avoid any network access.
        this.robots = new RobotsTxt(this.tables, null, 2);
        this.lockForHost = RobotsTxt.class.getDeclaredMethod("lockForHost", String.class);
        this.lockForHost.setAccessible(true);
    }

    @After
    public void tearDown() {
        this.robots.close();
        this.tables.close();
    }

    private Object lock(final String hostPort) throws Exception {
        return this.lockForHost.invoke(this.robots, hostPort);
    }

    @Test
    public void lockStorageStaysBoundedAndStableAcrossClear() throws Exception {
        final Set<Object> locks = Collections.newSetFromMap(new IdentityHashMap<>());
        final Object first = lock("example.org:443");
        for (int i = 0; i < 100000; i++) {
            locks.add(lock("host" + i + ".example:443"));
        }
        assertEquals(256, locks.size());
        assertSame(first, lock(new String("example.org:443")));
        // Exercise negative hashes, including the value Math.abs cannot make positive.
        assertEquals(Integer.MIN_VALUE, "polygenelubricants".hashCode());
        assertNotNull(lock("polygenelubricants"));
        this.robots.clear();
        assertSame(first, lock("example.org:443"));
        for (int i = 0; i < 100000; i++) {
            assertTrue(locks.contains(lock("host" + i + ".example:443")));
        }
    }

    @Test
    public void getEntryWaitsAndRechecksDatabase() throws Exception {
        assertWaitsAndRechecksDatabase(false);
    }

    @Test
    public void ensureExistWaitsAndRechecksDatabase() throws Exception {
        assertWaitsAndRechecksDatabase(true);
    }

    @Test
    public void getEntryDoesNotHoldStripeWhileFetchingRobots() throws Exception {
        assertCollidingFetchesDoNotSerialize(false);
    }

    @Test
    public void ensureExistDoesNotHoldStripeWhileFetchingRobots() throws Exception {
        assertCollidingFetchesDoNotSerialize(true);
    }

    private void assertCollidingFetchesDoNotSerialize(final boolean firstUsesEnsureExist) throws Exception {
        this.robots.close();
        final BlockingRobotsTxt blockingRobots = new BlockingRobotsTxt(this.tables);
        this.robots = blockingRobots;

        final DigestURL firstURL = new DigestURL("http://192.0.2.11/page");
        final DigestURL secondURL = findCollidingURL(firstURL);
        assertSame(lock(RobotsTxt.getHostPort(firstURL)), lock(RobotsTxt.getHostPort(secondURL)));

        final FutureTask<Void> first = new FutureTask<>(() -> {
            if (firstUsesEnsureExist) {
                this.robots.ensureExist(firstURL, ClientIdentification.yacyInternetCrawlerAgent, false);
            } else {
                this.robots.getEntry(firstURL, ClientIdentification.yacyInternetCrawlerAgent);
            }
            return null;
        });
        final FutureTask<Void> second = new FutureTask<>(() -> {
            this.robots.getEntry(secondURL, ClientIdentification.yacyInternetCrawlerAgent);
            return null;
        });
        final Thread firstWorker = new Thread(first, "robots-first-fetch-test");
        final Thread secondWorker = new Thread(second, "robots-colliding-fetch-test");
        try {
            firstWorker.start();
            assertTrue("The first request should enter its simulated network wait",
                    blockingRobots.awaitLoadsStarted(1, 5, TimeUnit.SECONDS));
            secondWorker.start();
            assertTrue("A colliding host must fetch without waiting for the first network request",
                    blockingRobots.awaitLoadsStarted(2, 5, TimeUnit.SECONDS));
        } finally {
            blockingRobots.releaseLoads();
            firstWorker.join(5000);
            secondWorker.join(5000);
        }
        assertFalse("The first simulated fetch must finish", firstWorker.isAlive());
        assertFalse("The colliding simulated fetch must finish", secondWorker.isAlive());
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertEquals(2, this.robots.size());
    }

    private DigestURL findCollidingURL(final DigestURL firstURL) throws Exception {
        final Object firstLock = lock(RobotsTxt.getHostPort(firstURL));
        for (int i = 0; i < 10000; i++) {
            final DigestURL candidate = new DigestURL("http://robots-collision-" + i + ".example.test/page");
            if (firstLock == lock(RobotsTxt.getHostPort(candidate))) return candidate;
        }
        fail("Could not find a test host in the same bounded lock stripe");
        return null;
    }

    private void assertWaitsAndRechecksDatabase(final boolean ensureExist) throws Exception {
        // A public numeric address avoids DNS lookups in ensureExist's locality check.
        final DigestURL url = new DigestURL("http://8.8.8.8/page");
        final String hostPort = RobotsTxt.getHostPort(url);
        final FutureTask<Void> task = new FutureTask<>(() -> {
            if (ensureExist) {
                this.robots.ensureExist(url, ClientIdentification.yacyInternetCrawlerAgent, false);
            } else {
                assertNotNull(this.robots.getEntry(url, ClientIdentification.yacyInternetCrawlerAgent));
            }
            return null;
        });
        final Thread worker = new Thread(task, "robots-lock-test");
        try {
            synchronized (lock(hostPort)) {
                worker.start();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (worker.getState() != Thread.State.BLOCKED && !task.isDone()
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals("The cache recheck must wait on the shared host lock",
                        Thread.State.BLOCKED, worker.getState());
                final RobotsTxtEntry entry = new RobotsTxtEntry(
                        new DigestURL("http://8.8.8.8/robots.txt"), new ArrayList<>(),
                        new ArrayList<>(), new Date(), new Date(), null, null, 0, null);
                final BEncodedHeap heap = this.tables.getHeap(WorkTables.TABLE_ROBOTS_NAME);
                heap.insert(heap.encodedKey(entry.getHostName()), entry.getMem());
            }
            task.get(5, TimeUnit.SECONDS);
            assertEquals(1, this.robots.size());
        } finally {
            worker.interrupt();
            worker.join(5000);
        }
    }

    private static final class BlockingRobotsTxt extends RobotsTxt {

        private final CountDownLatch loadsStarted = new CountDownLatch(2);
        private final CountDownLatch firstLoadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLoads = new CountDownLatch(1);
        private final AtomicInteger loads = new AtomicInteger();

        private BlockingRobotsTxt(final WorkTables tables) {
            super(tables, null, 2);
        }

        @Override
        protected net.yacy.crawler.retrieval.Response loadRobots(
                final DigestURL robotsURL, final ClientIdentification.Agent agent) {
            if (this.loads.incrementAndGet() == 1) this.firstLoadStarted.countDown();
            this.loadsStarted.countDown();
            try {
                this.releaseLoads.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        private boolean awaitLoadsStarted(final int count, final long timeout, final TimeUnit unit)
                throws InterruptedException {
            return count == 1 ? this.firstLoadStarted.await(timeout, unit) : this.loadsStarted.await(timeout, unit);
        }

        private void releaseLoads() {
            this.releaseLoads.countDown();
        }
    }
}

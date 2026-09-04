package net.yacy.crawler.robots;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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
                assertEquals("The fetch must wait on the shared host lock",
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
}

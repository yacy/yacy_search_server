package net.yacy.crawler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.CrawlProfile.CrawlAttribute;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.graphics.WebStructureGraph.StructureEntry;
import net.yacy.server.serverSwitch;

/** Integration of persistent crawl profiles with graph retention, without network services. */
public class CrawlSwitchboardTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private serverSwitch config;
    private File queues;
    private CrawlSwitchboard crawler;

    @Before
    public void openProfiles() throws Exception {
        final File root = this.temporaryFolder.getRoot();
        this.config = new serverSwitch(root, root, "absent.init", "settings/yacy.conf");
        this.config.log = new ConcurrentLog("CrawlSwitchboardTest");
        this.queues = this.temporaryFolder.newFolder("queues");
        this.crawler = new CrawlSwitchboard(null, this.queues, this.config);
    }

    @After
    public void closeProfiles() {
        if (this.crawler != null) this.crawler.close();
    }

    private void restartProfiles() {
        this.crawler.close();
        this.crawler = null;
        this.crawler = new CrawlSwitchboard(null, this.queues, this.config);
    }

    private CrawlProfile profile(final String handle, final String name) {
        final Map<String, String> values = new HashMap<>(this.crawler.defaultProxyProfile);
        values.put(CrawlAttribute.HANDLE.key, handle);
        values.put(CrawlAttribute.NAME.key, name);
        return new CrawlProfile(values);
    }

    private void activate(final CrawlProfile profile) {
        this.crawler.putActive(UTF8.getBytes(profile.handle()), profile);
    }

    private static Request request(final CrawlProfile profile, final String host, final int depth) throws Exception {
        return new Request(UTF8.getBytes("localpeer000"), new DigestURL("https://" + host + "/"),
                null, "start", new Date(), profile.handle(), depth, 0);
    }

    @Test
    public void submittedAndStreamedRootsSurviveDatabaseRestart() throws Exception {
        final CrawlProfile urls = profile("urlprofile00", "abbreviated URL crawl");
        urls.setStartURLs(Arrays.asList(new DigestURL("https://url-root.test/"),
                new DigestURL("http://url-root.test/other")));
        activate(urls);

        // Sitemap and file importers feed local depth-zero requests into this persistence boundary.
        final CrawlProfile sitemap = profile("sitemapprof0", "sitemap loader for https://source.test/sitemap.xml");
        final CrawlProfile file = profile("fileprofile0", "starts.html");
        for (final CrawlProfile imported : Arrays.asList(sitemap, file)) {
            activate(imported);
            final String host = imported == sitemap ? "sitemap-root.test" : "file-root.test";
            this.crawler.recordStartURL(imported, request(imported, host, 0));
            this.crawler.recordStartURL(imported, request(imported, host, 0));
            this.crawler.recordStartURL(imported, request(imported, "descendant.test", 1));
        }
        final Set<String> expected = new HashSet<>(Arrays.asList(
                "url-root.test", "sitemap-root.test", "file-root.test"));
        assertEquals(expected, this.crawler.getActiveStartHosts());
        restartProfiles();
        assertEquals(expected, this.crawler.getActiveStartHosts());

        final CrawlProfile reloaded = this.crawler.getActive(UTF8.getBytes(sitemap.handle()));
        this.crawler.recordStartURL(reloaded, request(reloaded, "later-root.test", 0));
        expected.add("later-root.test");
        restartProfiles();
        assertEquals(expected, this.crawler.getActiveStartHosts());
    }

    @Test
    public void stoppedAndReplacedProfilesIgnoreLateStartRequests() throws Exception {
        final CrawlProfile stopped = profile("stopprofile0", "stopped import");
        activate(stopped);
        this.crawler.recordStartURL(stopped, request(stopped, "stopped.test", 0));
        this.crawler.putPassive(UTF8.getBytes(stopped.handle()), stopped);
        this.crawler.recordStartURL(stopped, request(stopped, "late.test", 0));
        assertNull(this.crawler.getActive(UTF8.getBytes(stopped.handle())));
        assertEquals(Collections.singleton("stopped.test"),
                this.crawler.getPassive(UTF8.getBytes(stopped.handle())).startHosts());
        assertTrue(this.crawler.getActiveStartHosts().isEmpty());
        restartProfiles();
        assertTrue(this.crawler.getActiveStartHosts().isEmpty());

        final CrawlProfile replacement = profile(stopped.handle(), "replacement import");
        activate(replacement);
        this.crawler.recordStartURL(stopped, request(stopped, "obsolete.test", 0));
        this.crawler.recordStartURL(replacement, request(replacement, "replacement.test", 0));
        restartProfiles();
        assertEquals(Collections.singleton("replacement.test"), this.crawler.getActiveStartHosts());
    }

    @Test
    public void defaultAndPassiveProfilesDoNotProtectHosts() throws Exception {
        for (final byte[] handle : this.crawler.getActive()) {
            final CrawlProfile profile = this.crawler.getActive(handle);
            assertTrue(CrawlSwitchboard.DEFAULT_PROFILES.contains(profile.name()));
            this.crawler.recordStartURL(profile, request(profile, "default.test", 0));
            assertFalse(profile.startHosts().contains("default.test"));
            // Even existing metadata on a default profile must not protect its hosts.
            profile.setStartURLs(Collections.singletonList(new DigestURL("https://default.test/")));
            activate(profile);
        }
        final CrawlProfile passive = profile("passiveprof0", "passive.test");
        this.crawler.putPassive(UTF8.getBytes(passive.handle()), passive);
        final CrawlProfile legacy = profile("legacyprof00", "Legacy.test, www.second.test");
        activate(legacy);
        assertEquals(new HashSet<>(Arrays.asList("legacy.test", "www.second.test")),
                this.crawler.getActiveStartHosts());
        restartProfiles();
        assertEquals(new HashSet<>(Arrays.asList("legacy.test", "www.second.test")),
                this.crawler.getActiveStartHosts());
    }

    @Test
    public void concurrentStreamedRootsPersistWithoutLoss() throws Exception {
        final CrawlProfile profile = profile("parallelprof", "parallel import");
        activate(profile);
        final ExecutorService executor = Executors.newFixedThreadPool(4);
        final CountDownLatch start = new CountDownLatch(1);
        final Future<?>[] tasks = new Future<?>[4];
        final Set<String> expected = new HashSet<>();
        try {
            for (int worker = 0; worker < tasks.length; worker++) {
                final int number = worker;
                for (int i = 0; i < 25; i++) expected.add("root-" + worker + "-" + i + ".test");
                tasks[worker] = executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 25; i++) {
                        this.crawler.recordStartURL(profile, request(profile, "root-" + number + "-" + i + ".test", 0));
                    }
                    return null;
                });
            }
            start.countDown();
            for (final Future<?> task : tasks) task.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        restartProfiles();
        assertEquals(expected, this.crawler.getActiveStartHosts());
    }

    @Test
    public void persistedActiveRootsProtectGraphUntilCrawlStops() throws Exception {
        final DigestURL root = new DigestURL("https://root.test/");
        final DigestURL bridge = new DigestURL("http://bridge.test/");
        final DigestURL leaf = new DigestURL("https://leaf.test/");
        final CrawlProfile profile = profile("graphprofile", "abbreviated graph crawl");
        profile.setStartURLs(Collections.singletonList(root));
        activate(profile);
        restartProfiles();

        final Map<String, byte[]> entries = new TreeMap<>();
        storeHost(entries, root, "19900101" + bridge.hosthash() + "0001");
        storeHost(entries, bridge, "19900101" + leaf.hosthash() + "0001");
        storeHost(entries, leaf, "19900101");
        for (int i = 0; i < WebStructureGraph.maxhosts - 2; i++) {
            storeHost(entries, new DigestURL("http://unrelated-" + i + ".test/"), "20990101");
        }
        final File backup = this.temporaryFolder.newFile("webStructure.map");
        FileUtils.saveMapB(backup, entries, "active root and newer unrelated hosts");
        WebStructureGraph graph = new WebStructureGraph(backup, () -> this.crawler.getActiveStartHosts());
        try {
            assertEquals(9000, hostCount(graph));
            assertPath(graph, root, bridge, leaf);
        } finally {
            graph.close();
        }
        restartProfiles();
        graph = new WebStructureGraph(backup, () -> this.crawler.getActiveStartHosts());
        try {
            assertPath(graph, root, bridge, leaf);
            // This is the same profile transition used by crawl termination/cleanup.
            this.crawler.cleanProfiles(Collections.singleton(profile.handle()));
            assertTrue(this.crawler.getActiveStartHosts().isEmpty());
            final DigestURL unrelated = new DigestURL("http://unrelated-0.test/");
            for (int i = 0; i <= 1000; i++) {
                graph.generateCitationReference(new DigestURL("https://new-" + i + ".test/"), unrelated);
            }
        } finally {
            // Drain the real learning worker before inspecting the persisted graph.
            graph.close();
        }
        assertEquals("Runtime pruning must happen before the next startup", 9000, FileUtils.loadMapB(backup).size());
        restartProfiles();
        assertTrue(this.crawler.getActiveStartHosts().isEmpty());
        graph = new WebStructureGraph(backup, () -> this.crawler.getActiveStartHosts());
        try {
            assertEquals(9000, hostCount(graph));
            for (final DigestURL url : Arrays.asList(root, bridge, leaf)) assertFalse(graph.exists(url.hosthash()));
        } finally {
            graph.close();
        }
    }

    private static void storeHost(final Map<String, byte[]> entries, final DigestURL url, final String refs) {
        entries.put(url.hosthash() + "," + url.getHost(), UTF8.getBytes(refs));
    }

    private static int hostCount(final WebStructureGraph graph) {
        final Set<String> keys = new HashSet<>();
        for (final boolean latest : new boolean[] {false, true}) {
            final Iterator<StructureEntry> entries = graph.structureEntryIterator(latest);
            while (entries.hasNext()) {
                final StructureEntry entry = entries.next();
                keys.add(entry.hosthash + "," + entry.hostname);
            }
        }
        return keys.size();
    }

    private static void assertPath(final WebStructureGraph graph, final DigestURL... path) {
        for (int i = 0; i < path.length; i++) {
            assertTrue(graph.exists(path[i].hosthash()));
            assertEquals(path[i].getHost(), graph.hostHash2hostName(path[i].hosthash()));
            if (i > 0) {
                final StructureEntry source = graph.outgoingReferences(path[i - 1].hosthash());
                assertNotNull(source);
                assertTrue(source.references.containsKey(path[i].hosthash()));
            }
        }
    }
}

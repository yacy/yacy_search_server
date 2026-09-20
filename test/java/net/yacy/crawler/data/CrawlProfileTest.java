package net.yacy.crawler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.peers.graphics.WebStructureGraph;

public class CrawlProfileTest {

    @Test
    public void startHostsSurviveProfileRoundTripWithAnAbbreviatedName() throws Exception {
        final Map<String, String> values = new HashMap<>();
        values.put(CrawlProfile.CrawlAttribute.NAME.key, "crawl_for_3_start_points_abbreviated");
        final CrawlProfile profile = new CrawlProfile(values);
        profile.setStartURLs(Arrays.asList(new DigestURL("https://first.net/"),
                new DigestURL("https://second.net/path"), new DigestURL("http://first.net/other")));
        final CrawlProfile reloaded = new CrawlProfile(new HashMap<>(profile));
        assertEquals(new HashSet<>(Arrays.asList("first.net", "second.net")), reloaded.startHosts());
    }

    @Test
    public void legacyProfilesUseTheirHostNames() {
        final Map<String, String> values = new HashMap<>();
        values.put(CrawlProfile.CrawlAttribute.NAME.key, "KLG.de, www.example.net");
        assertEquals(new HashSet<>(Arrays.asList("klg.de", "www.example.net")),
                new CrawlProfile(values).startHosts());
    }

    @Test
    public void streamedSitemapAndFileRootsPersistButDescendantsDoNotBecomeRoots() throws Exception {
        for (final String name : Arrays.asList("sitemap loader for https://source.net/sitemap.xml", "starts.html")) {
            final Map<String, String> values = new HashMap<>();
            values.put(CrawlProfile.CrawlAttribute.NAME.key, name);
            final CrawlProfile profile = new CrawlProfile(values);
            // Both importers submit their start URLs at depth zero to the crawl stacker.
            assertTrue(profile.recordStartURL(new DigestURL("https://first.net/a"), 0));
            assertFalse(profile.recordStartURL(new DigestURL("https://first.net/b"), 0));
            assertFalse(profile.recordStartURL(new DigestURL("https://descendant.net/"), 1));
            final CrawlProfile reloaded = new CrawlProfile(new HashMap<>(profile));
            assertTrue(reloaded.recordStartURL(new DigestURL("https://second.net/"), 0));
            assertEquals(new HashSet<>(Arrays.asList("first.net", "second.net")), reloaded.startHosts());
        }
    }

    @Test
    public void localStartHostsAreOmittedFromCrawlNews() throws Exception {
        final Map<String, String> values = new HashMap<>();
        values.put(CrawlProfile.CrawlAttribute.NAME.key, "crawl_for_2_start_points");
        final CrawlProfile profile = new CrawlProfile(values);
        profile.setStartURLs(Arrays.asList(new DigestURL("https://first.net/"), new DigestURL("https://second.net/")));
        final Map<String, String> news = profile.copyForCrawlNews();
        assertFalse(news.containsKey("startHosts"));
        assertEquals(profile.name(), news.get(CrawlProfile.CrawlAttribute.NAME.key));
        assertEquals(2, profile.startHosts().size());
    }

    @Test
    public void startHostMetadataIsBoundedForLargeImports() throws Exception {
        final CrawlProfile profile = new CrawlProfile(new HashMap<>());
        final List<DigestURL> starts = new ArrayList<>();
        for (int i = 0; i <= WebStructureGraph.maxhosts; i++) starts.add(new DigestURL("https://host" + i + ".net/"));
        profile.setStartURLs(starts);
        assertEquals(WebStructureGraph.maxhosts, profile.startHosts().size());
        assertFalse(profile.recordStartURL(new DigestURL("https://overflow.net/"), 0));
        assertEquals(WebStructureGraph.maxhosts, new CrawlProfile(new HashMap<>(profile)).startHosts().size());
    }
}

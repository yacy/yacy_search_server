// WebStructureGraphTest.java
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.peers.graphics;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.graphics.WebStructureGraph.LearnObject;
import net.yacy.peers.graphics.WebStructureGraph.StructureEntry;

/**
 * Unit tests for {@link WebStructureGraph}
 * 
 * @author luccioman
 *
 */
public class WebStructureGraphTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static DigestURL host(final int number) throws MalformedURLException {
        return new DigestURL("http://host" + number + ".net/");
    }

    private static Set<String> hostKeys(final WebStructureGraph graph, final boolean latest) {
        final Set<String> keys = new HashSet<>();
        final Iterator<StructureEntry> entries = graph.structureEntryIterator(latest);
        while (entries.hasNext()) {
            final StructureEntry entry = entries.next();
            keys.add(entry.hosthash + "," + entry.hostname);
        }
        return keys;
    }

    private static int hostCount(final WebStructureGraph graph) {
        final Set<String> keys = hostKeys(graph, false);
        keys.addAll(hostKeys(graph, true));
        return keys.size();
    }

    @Test
    public void testPruningPreservesProtocolAndPortVariantsAlongDisplayedPaths() throws Exception {
        for (final boolean atStartup : new boolean[] {false, true}) {
            final DigestURL root = new DigestURL("https://root.net/");
            final DigestURL bridgeHttp = new DigestURL("http://bridge.net/");
            final DigestURL bridgeHttps = new DigestURL("https://bridge.net/");
            final DigestURL bridgePort = new DigestURL("http://bridge.net:8080/");
            final DigestURL leaf = new DigestURL("https://leaf.net/");
            final Map<String, byte[]> entries = new TreeMap<>();
            entries.put(root.hosthash() + ",root.net", UTF8.getBytes("19900101" + bridgeHttp.hosthash() + "0001"));
            entries.put(bridgeHttp.hosthash() + ",bridge.net", UTF8.getBytes("19900101"));
            entries.put(bridgeHttps.hosthash() + ",bridge.net", UTF8.getBytes("19900101" + leaf.hosthash() + "0001"));
            entries.put(bridgePort.hosthash() + ",bridge.net", UTF8.getBytes("19900101" + leaf.hosthash() + "0001"));
            entries.put(leaf.hosthash() + ",leaf.net", UTF8.getBytes("19900101"));
            for (int i = 0; i < WebStructureGraph.maxhosts - 5; i++) storeHost(entries, i, "20990101");
            if (atStartup) storeHost(entries, WebStructureGraph.maxhosts, "20990101");
            final File backup = this.temporaryFolder.newFile();
            FileUtils.saveMapB(backup, entries, "hostname graph spanning protocols and ports");
            final WebStructureGraph graph = new WebStructureGraph(backup,
                    () -> Collections.singleton(root.getHost()));
            try {
                if (!atStartup) graph.learnrefs(new LearnObject(host(WebStructureGraph.maxhosts), Collections.emptySet()));
                Assert.assertEquals(9000, hostCount(graph));
                Assert.assertTrue(graph.outgoingReferencesByHostName("root.net").containsKey(bridgeHttp.hosthash()));
                for (final DigestURL variant : new DigestURL[] {bridgeHttp, bridgeHttps, bridgePort}) {
                    Assert.assertTrue("Missing bridge variant " + variant, graph.exists(variant.hosthash()));
                }
                Assert.assertTrue(graph.outgoingReferencesByHostName("bridge.net").containsKey(leaf.hosthash()));
                Assert.assertEquals("leaf.net", graph.hostHash2hostName(leaf.hosthash()));
            } finally {
                graph.close();
            }
        }
    }

    private static void storeHost(final Map<String, byte[]> entries, final int source,
            final String date, final int... targets) throws Exception {
        final DigestURL url = host(source);
        final StringBuilder refs = new StringBuilder(date);
        for (final int target : targets) refs.append(host(target).hosthash()).append("0001");
        entries.put(url.hosthash() + "," + url.getHost(), UTF8.getBytes(refs.toString()));
    }

    private File graphWithOldRootPaths(final int count) throws Exception {
        final Map<String, byte[]> entries = new TreeMap<>();
        for (int i = 0; i < count; i++) storeHost(entries, i, "20990101");
        storeHost(entries, 0, "19900101", 1);
        storeHost(entries, 1, "19900101", 2);
        storeHost(entries, 2, "19900101", 3);
        storeHost(entries, 3, "19900101", 0); // cycle must not defeat the bound
        storeHost(entries, 4, "19900101", 5);
        storeHost(entries, 5, "19900101", 6);
        storeHost(entries, 6, "19900101", 4);
        final File backup = this.temporaryFolder.newFile();
        FileUtils.saveMapB(backup, entries, "old roots and newer unrelated hosts");
        return backup;
    }

    private static void assertPath(final WebStructureGraph graph, final int... path) throws Exception {
        for (int i = 0; i < path.length; i++) {
            Assert.assertTrue("Missing path host " + path[i], graph.exists(host(path[i]).hosthash()));
            Assert.assertEquals(host(path[i]).getHost(), graph.hostHash2hostName(host(path[i]).hosthash()));
            if (i > 0) {
                Assert.assertTrue(graph.outgoingReferences(host(path[i - 1]).hosthash())
                        .references.containsKey(host(path[i]).hosthash()));
            }
        }
    }

    @Test
    public void testStartupPreservesMultipleOldRootPathsAndRestarts() throws Exception {
        final File backup = graphWithOldRootPaths(WebStructureGraph.maxhosts + 1);
        final Set<String> roots = new HashSet<>();
        roots.add(host(0).getHost());
        roots.add("www." + host(4).getHost());
        for (int restart = 0; restart < 2; restart++) {
            final WebStructureGraph graph = new WebStructureGraph(backup, () -> roots);
            try {
                Assert.assertEquals(9000, hostCount(graph));
                assertPath(graph, 0, 1, 2, 3, 0);
                assertPath(graph, 4, 5, 6, 4);
            } finally {
                graph.close();
            }
        }
    }

    @Test
    public void testRuntimePreservesRootPathsAndNewTargetsUntilCrawlStops() throws Exception {
        final Set<String> roots = new HashSet<>();
        final WebStructureGraph graph = new WebStructureGraph(
                graphWithOldRootPaths(WebStructureGraph.maxhosts), () -> roots);
        try {
            // Starting a crawl after graph construction must affect the next pruning pass.
            roots.add(host(0).getHost());
            final DigestURL newTarget = host(WebStructureGraph.maxhosts + 1);
            graph.learnrefs(new LearnObject(host(0), Collections.singleton(newTarget)));
            assertPath(graph, 0, 1, 2, 3, 0);
            assertPath(graph, 0, WebStructureGraph.maxhosts + 1);
            Assert.assertEquals(9000, hostCount(graph));
            Assert.assertFalse(graph.exists(host(4).hosthash())); // unrelated old component
            graph.joinOldNew();
            roots.clear();
            for (int i = 0; i <= 1000; i++) {
                graph.learnrefs(new LearnObject(host(20000 + i), Collections.emptySet()));
            }
            Assert.assertEquals(9000, hostCount(graph));
            Assert.assertFalse("Stopped crawl's old path can be pruned", graph.exists(host(1).hosthash()));
        } finally {
            graph.close();
        }
    }

    @Test
    public void testOversizedConnectedGraphKeepsContinuousPathsWithinBudget() throws Exception {
        final File backup = this.temporaryFolder.newFile();
        final Map<String, byte[]> entries = new TreeMap<>();
        for (int i = 0; i <= WebStructureGraph.maxhosts; i++) {
            storeHost(entries, i, "19900101", (i + 1) % (WebStructureGraph.maxhosts + 1));
        }
        storeHost(entries, 0, "19900101", 1, WebStructureGraph.maxhosts);
        FileUtils.saveMapB(backup, entries, "oversized connected path");
        final WebStructureGraph graph = new WebStructureGraph(backup,
                () -> Collections.singleton("host0.net"));
        try {
            Assert.assertEquals(9000, hostCount(graph));
            for (int i = 0; i < 8999; i++) Assert.assertTrue(graph.exists(host(i).hosthash()));
            Assert.assertFalse(graph.exists(host(8999).hosthash()));
            assertPath(graph, 0, WebStructureGraph.maxhosts); // nearby branch precedes the long path's tail
            assertPath(graph, 0, 1, 2, 3);
        } finally {
            graph.close();
        }
    }

    @Test
    public void testRuntimeHostLimitAcrossOldAndNewAndAfterClear() throws Exception {
        final WebStructureGraph graph = new WebStructureGraph(null);
        try {
            for (int i = 0; i < WebStructureGraph.maxhosts; i++) {
                graph.learnrefs(new LearnObject(host(i), Collections.emptySet()));
                if (i == WebStructureGraph.maxhosts / 2) graph.joinOldNew();
            }
            // Updating every old host must not count it again or drain the recent map.
            for (int i = 0; i <= WebStructureGraph.maxhosts / 2; i++) {
                graph.learnrefs(new LearnObject(host(i), Collections.emptySet()));
            }
            Assert.assertEquals(WebStructureGraph.maxhosts, hostCount(graph));
            Assert.assertEquals(WebStructureGraph.maxhosts, hostKeys(graph, true).size());
            final Iterator<StructureEntry> oldSnapshot = graph.structureEntryIterator(false);
            final Iterator<StructureEntry> recentSnapshot = graph.structureEntryIterator(true);
            graph.learnrefs(new LearnObject(host(WebStructureGraph.maxhosts), Collections.emptySet()));
            Assert.assertEquals(WebStructureGraph.maxhosts * 9 / 10, hostCount(graph));
            Assert.assertEquals(WebStructureGraph.maxhosts / 2 + 1, countEntries(oldSnapshot));
            Assert.assertEquals(WebStructureGraph.maxhosts, countEntries(recentSnapshot));
            graph.joinOldNew();
            // The limit must continue working over successive eviction/merge cycles.
            for (int i = 1; i <= 1100; i++) {
                graph.learnrefs(new LearnObject(host(WebStructureGraph.maxhosts + i), Collections.emptySet()));
            }
            Assert.assertEquals(9099, hostCount(graph));
            graph.clear();
            for (int i = 0; i < WebStructureGraph.maxhosts; i++) {
                graph.learnrefs(new LearnObject(host(i), Collections.emptySet()));
            }
            Assert.assertEquals(WebStructureGraph.maxhosts, hostCount(graph));
        } finally {
            graph.close();
        }
    }

    private static int countEntries(final Iterator<StructureEntry> entries) {
        int count = 0;
        while (entries.hasNext()) {
            Assert.assertNotNull(entries.next());
            count++;
        }
        return count;
    }

    @Test
    public void testTargetHostsAlsoTriggerRuntimeLimit() throws Exception {
        final WebStructureGraph graph = new WebStructureGraph(null);
        try {
            for (int batch = 0; batch < 11; batch++) {
                final Set<DigestURL> targets = new HashSet<>();
                for (int i = 0; i < 1000; i++) targets.add(host(batch * 1000 + i));
                graph.learnrefs(new LearnObject(host(-1), targets));
                Assert.assertTrue(hostCount(graph) <= WebStructureGraph.maxhosts);
            }
        } finally {
            graph.close();
        }
    }

    @Test
    public void testRuntimeEvictionUsesRecentDateAndPersistsLimit() throws Exception {
        final File backup = this.temporaryFolder.newFile("structure.map");
        final Map<String, byte[]> stored = new TreeMap<>();
        for (int i = 0; i < WebStructureGraph.maxhosts; i++) {
            final DigestURL url = host(i);
            stored.put(url.hosthash() + "," + url.getHost(), UTF8.getBytes(i < 2 ? "19900101" : "20000101"));
        }
        FileUtils.saveMapB(backup, stored, "test graph");
        final WebStructureGraph graph = new WebStructureGraph(backup);
        try {
            graph.learnrefs(new LearnObject(host(0), Collections.emptySet()));
            graph.learnrefs(new LearnObject(host(WebStructureGraph.maxhosts), Collections.emptySet()));
            Assert.assertEquals(WebStructureGraph.maxhosts * 9 / 10, hostCount(graph));
            Assert.assertTrue(graph.exists(host(0).hosthash()));
            Assert.assertFalse(graph.exists(host(1).hosthash()));
            Assert.assertTrue(hostKeys(graph, true).contains(host(0).hosthash() + "," + host(0).getHost()));
        } finally {
            graph.close();
        }
        Assert.assertEquals(WebStructureGraph.maxhosts * 9 / 10, FileUtils.loadMapB(backup).size());
    }

    @Test
    public void testStartupStillTrimsOldestHosts() throws Exception {
        final File backup = this.temporaryFolder.newFile("oversized.map");
        final Map<String, byte[]> stored = new TreeMap<>();
        for (int i = 0; i <= WebStructureGraph.maxhosts; i++) {
            final DigestURL url = host(i);
            stored.put(url.hosthash() + "," + url.getHost(), UTF8.getBytes(i == 0 ? "19900101" : "20000101"));
        }
        FileUtils.saveMapB(backup, stored, "test graph");
        final WebStructureGraph graph = new WebStructureGraph(backup);
        try {
            Assert.assertEquals(WebStructureGraph.maxhosts * 9 / 10, hostCount(graph));
            Assert.assertFalse(graph.exists(host(0).hosthash()));
        } finally {
            graph.close();
        }
    }

	/**
	 * Most basic out going references unit test
	 */
	@Test
	public void testOutgoingReferences() throws MalformedURLException {
		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL source = new DigestURL("http://source.net/index.html");
			final String sourceHash = source.hosthash();
			final Set<DigestURL> targets = new HashSet<>();

			final DigestURL target = new DigestURL("http://target.com/index.html");
			final String targetHash = target.hosthash();
			targets.add(target);

			LearnObject lro = new LearnObject(source, targets);
			graph.learnrefs(lro);

			/* Check that reference from the exact source URL is retrieved from structure */
			StructureEntry outRefs = graph.outgoingReferences(sourceHash);
			
			Assert.assertNotNull(outRefs);
			Assert.assertEquals("source.net", outRefs.hostname);
			Assert.assertNotNull(outRefs.references);
			Assert.assertEquals(1, outRefs.references.size());
			Assert.assertEquals(Integer.valueOf(1), outRefs.references.get(targetHash));
			
			/* Check that reference from the host name URL is retrieved from structure */
			outRefs = graph.outgoingReferences(new DigestURL("http://source.net").hosthash());
			
			Assert.assertNotNull(outRefs);
			Assert.assertEquals("source.net", outRefs.hostname);
			Assert.assertNotNull(outRefs.references);
			Assert.assertEquals(1, outRefs.references.size());
			Assert.assertEquals(Integer.valueOf(1), outRefs.references.get(targetHash));
			
		} finally {
			graph.close();
		}
	}
	
	/**
	 * Out going references from one source document to different resources on the same target host
	 */
	@Test
	public void testOutgoingFromOneToMultipleSameTargeHost() throws MalformedURLException {
		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL source = new DigestURL("http://source.net/index.html");
			final String sourceHash = source.hosthash();
			final Set<DigestURL> targets = new HashSet<>();

			final DigestURL indexTarget = new DigestURL("http://target.com/index.html");
			targets.add(indexTarget);
			
			final DigestURL pathTarget = new DigestURL("http://target.com/path/doc.html");
			targets.add(pathTarget);
			
			final DigestURL queryTarget = new DigestURL("http://target.com/path/query?param=value");
			targets.add(queryTarget);

			LearnObject lro = new LearnObject(source, targets);
			graph.learnrefs(lro);

			/* Check that accumulated references from the host name URL is retrieved from structure */
			StructureEntry outRefs = graph.outgoingReferences(sourceHash);
			
			Assert.assertNotNull(outRefs);
			Assert.assertEquals("source.net", outRefs.hostname);
			Assert.assertNotNull(outRefs.references);
			/* One accumulated host target reference */
			Assert.assertEquals(1, outRefs.references.size());
			/* 3 accumulated links to that target host */
			Assert.assertEquals(Integer.valueOf(3), outRefs.references.get(indexTarget.hosthash()));
			
		} finally {
			graph.close();
		}
	}
	
	/**
	 * Out going references by host name
	 */
	@Test
	public void outgoingReferencesByHostName() throws MalformedURLException {
		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL httpSource = new DigestURL("http://source.net/index.html");
			Set<DigestURL> targets = new HashSet<>();
			final DigestURL indexTarget = new DigestURL("http://target.com/index.html");
			targets.add(indexTarget);
			LearnObject lro = new LearnObject(httpSource, targets);
			graph.learnrefs(lro);
			
			final DigestURL httpsSource = new DigestURL("https://source.net/index.html");
			targets = new HashSet<>();
			final DigestURL pathTarget = new DigestURL("http://target.com/path");
			targets.add(pathTarget);
			lro = new LearnObject(httpsSource, targets);
			graph.learnrefs(lro);
			
			final DigestURL otherPortSource = new DigestURL("https://source.net:8080/index.html");
			targets = new HashSet<>();
			final DigestURL queryTarget = new DigestURL("http://target.com/query?param=value");
			targets.add(queryTarget);
			lro = new LearnObject(otherPortSource, targets);
			graph.learnrefs(lro);

			/* Check that accumulated references from the host name is retrieved from structure */
			Map<String, Integer> outRefs = graph.outgoingReferencesByHostName("source.net");
			
			Assert.assertNotNull(outRefs);
			Assert.assertEquals(1, outRefs.size());
			Assert.assertEquals(new DigestURL("http://target.com").hosthash(), outRefs.keySet().iterator().next());
			Assert.assertEquals(Integer.valueOf(3), outRefs.values().iterator().next());
			
			/* Check that accumulated references from unknown host name is empty */
			outRefs = graph.outgoingReferencesByHostName("test.net");
			
			Assert.assertNotNull(outRefs);
			Assert.assertTrue(outRefs.isEmpty());
			
		} finally {
			graph.close();
		}
	}

	/**
	 * Most basic incoming references unit test
	 */
	@Test
	public void testIncomingReferences() throws MalformedURLException {

		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL source = new DigestURL("http://source.net/index.html");
			final String sourceHash = source.hosthash();
			final Set<DigestURL> targets = new HashSet<>();

			final DigestURL target = new DigestURL("http://target.com/index.html");
			final String targetHash = target.hosthash();
			targets.add(target);

			LearnObject lro = new LearnObject(source, targets);
			graph.learnrefs(lro);

			/* Check that reference to the exact target URL is retrieved from structure */
			StructureEntry inRefs = graph.incomingReferences(targetHash);
			
			Assert.assertNotNull(inRefs);
			Assert.assertEquals("target.com", inRefs.hostname);
			Assert.assertNotNull(inRefs.references);
			Assert.assertEquals(1, inRefs.references.size());
			Assert.assertEquals(Integer.valueOf(1), inRefs.references.get(sourceHash));
			
			/* Check that reference to the host name target URL is retrieved from structure */
			inRefs = graph.incomingReferences(new DigestURL("http://target.com").hosthash());
			
			Assert.assertNotNull(inRefs);
			Assert.assertEquals("target.com", inRefs.hostname);
			Assert.assertNotNull(inRefs.references);
			Assert.assertEquals(1, inRefs.references.size());
			Assert.assertEquals(Integer.valueOf(1), inRefs.references.get(sourceHash));
			
		} finally {
			graph.close();
		}
	}
	
	/**
	 * Incoming references from multiple sources on the same host to one target URL
	 */
	@Test
	public void testIncomingReferencesFromMultipleSourcesOnOneHost() throws MalformedURLException {

		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL indexSource = new DigestURL("http://source.net/index.html");
			final String sourceHash = indexSource.hosthash();
			Set<DigestURL> targets = new HashSet<>();

			final DigestURL target = new DigestURL("http://target.com/index.html");
			final String targetHash = target.hosthash();
			targets.add(target);

			LearnObject lro = new LearnObject(indexSource, targets);
			graph.learnrefs(lro);
			
			final DigestURL pathSource = new DigestURL("http://source.net/path/doc.html");
			targets = new HashSet<>();
			targets.add(target);

			lro = new LearnObject(pathSource, targets);
			graph.learnrefs(lro);
			
			final DigestURL querySource = new DigestURL("http://source.net/query?param=value");
			targets = new HashSet<>();
			targets.add(target);

			lro = new LearnObject(querySource, targets);
			graph.learnrefs(lro);

			/* Check that reference to the exact target URL is retrieved from structure */
			StructureEntry inRefs = graph.incomingReferences(targetHash);
			
			Assert.assertNotNull(inRefs);
			Assert.assertEquals("target.com", inRefs.hostname);
			Assert.assertNotNull(inRefs.references);
			/* One accumulated host source reference */
			Assert.assertEquals(1, inRefs.references.size());
			/* 3 accumulated links from that host */
			Assert.assertEquals(Integer.valueOf(3), inRefs.references.get(sourceHash));
			
		} finally {
			graph.close();
		}
	}
	
	/**
	 * Incoming references from multiple sources on the same host to one target
	 * URL accumulated between old and new structure
	 */
	@Test
	public void testIncomingReferencesFromNewAndOld() throws MalformedURLException {

		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			final DigestURL indexSource = new DigestURL("http://source.net/index.html");
			final String sourceHash = indexSource.hosthash();
			Set<DigestURL> targets = new HashSet<>();

			final DigestURL target = new DigestURL("http://target.com/index.html");
			final String targetHash = target.hosthash();
			targets.add(target);

			LearnObject lro = new LearnObject(indexSource, targets);
			graph.learnrefs(lro);
			
			/* Backup learned reference to the old structure */
			graph.joinOldNew();
			
			final DigestURL pathSource = new DigestURL("http://source.net/path/doc.html");
			targets = new HashSet<>();
			targets.add(target);

			lro = new LearnObject(pathSource, targets);
			graph.learnrefs(lro);
			
			final DigestURL querySource = new DigestURL("http://source.net/query?param=value");
			targets = new HashSet<>();
			targets.add(target);

			lro = new LearnObject(querySource, targets);
			graph.learnrefs(lro);

			/* Check that reference to the exact target URL is retrieved from structure */
			StructureEntry inRefs = graph.incomingReferences(targetHash);
			
			Assert.assertNotNull(inRefs);
			Assert.assertEquals("target.com", inRefs.hostname);
			Assert.assertNotNull(inRefs.references);
			/* One accumulated host source reference */
			Assert.assertEquals(1, inRefs.references.size());
			/* 3 accumulated links from that host */
			Assert.assertEquals(Integer.valueOf(3), inRefs.references.get(sourceHash));
			
		} finally {
			graph.close();
		}
	}
	
	/**
	 * Simple performance measurements with a test structure filled to its limits.
	 */
	public static void main(String args[]) throws MalformedURLException {
		WebStructureGraph graph = new WebStructureGraph(null);
		try {
			long beginTime = System.nanoTime();
			/* Generate maxhosts structure entries */
			for(int i = 0; i < WebStructureGraph.maxhosts; i++) {
				final DigestURL source = new DigestURL("http://source" + i + ".net/index.html");
				final Set<DigestURL> targets = new HashSet<>();
				
				/* Generate maxref targets */
				for(int j = 0; j < WebStructureGraph.maxref; j++) {
					final DigestURL target = new DigestURL("http://target" + String.valueOf(j) + ".com/index.html");
					targets.add(target);
				}
				
				LearnObject lro = new LearnObject(source, targets);
				graph.learnrefs(lro);
			}
			long endTime = System.nanoTime();
			System.out.println("testPerfs test structure initialisation time : " + ((endTime - beginTime) / 1000000000) + " seconds"); 
			
			beginTime = System.nanoTime();
			/* Loop and look for incoming references on each sample generated target */
			for(int j = 0; j < WebStructureGraph.maxref; j++) {
				String targetHash = new DigestURL("http://target" + j + ".com/index.html").hosthash();
				graph.incomingReferences(targetHash);
			}
			endTime = System.nanoTime();
			System.out.println("testPerfs incomingReferences running time : " + ((endTime - beginTime) / 1000000000) + " seconds");
			
			beginTime = System.nanoTime();
			/* Loop and look for outgoing references on each sample generated source */
			for(int i = 0; i < WebStructureGraph.maxhosts; i++) {
				String sourceHash = new DigestURL("http://source" + i + ".net/index.html").hosthash();
				graph.outgoingReferences(sourceHash);
			}
			endTime = System.nanoTime();
			System.out.println("testPerfs outgoingReferences running time : " + ((endTime - beginTime) / 1000000000) + " seconds");
			
			beginTime = System.nanoTime();
			/* Loop and look for host hashes from host name on each sample generated source */
			for(int i = 0; i < WebStructureGraph.maxhosts; i++) {
				graph.hostName2HostHashes("source" + i + ".net");
			}
			endTime = System.nanoTime();
			System.out.println("testPerfs hostName2HostHashes running time : " + ((endTime - beginTime) / 1000000000) + " seconds"); 
			
		} finally {
			graph.close();
			ConcurrentLog.shutdown();
		}
	}

}

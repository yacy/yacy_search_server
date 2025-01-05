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

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.WebStructureGraph.LearnObject;
import net.yacy.peers.graphics.WebStructureGraph.StructureEntry;

/**
 * Unit tests for {@link WebStructureGraph}
 * 
 * @author luccioman
 *
 */
public class WebStructureGraphTest {

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

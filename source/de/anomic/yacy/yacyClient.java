// yacyClient.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.Part;

import de.anomic.crawler.HTTPLoader;
import de.anomic.crawler.ResultURLs;
import de.anomic.http.DefaultCharsetFilePart;
import de.anomic.http.DefaultCharsetStringPart;
import de.anomic.http.HttpClient;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpHeader;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.index.indexContainer;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexRWIRowEntry;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.index.indexURLReference;
import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaSearchRankingProcess;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSReader;

public final class yacyClient {

    /**
     * this is called to enrich the seed information by
     * - own address (if peer is behind a nat/router)
     * - check peer type (virgin/junior/senior/principal)
     * 
     * to do this, we send a 'Hello' to another peer
     * this carries the following information:
     * 'iam' - own hash
     * 'youare' - remote hash, to verify that we are correct
     * 'key' - a session key that the remote peer may use to answer
     * and the own seed string
     * we expect the following information to be send back:
     * - 'yourip' the ip of the connection peer (we)
     * - 'yourtype' the type of this peer that the other peer checked by asking for a specific word
     * and the remote seed string
     * 
     * one exceptional failure case is when we know the other's peers hash, the other peers responds correctly
     * but they appear to be another peer by comparisment of the other peer's hash
     * this works of course only if we know the other peer's hash.
     * 
     * @return the number of new seeds 
     */
    public static int publishMySeed(final yacySeed mySeed, final yacyPeerActions peerActions, final String address, final String otherHash) {
        
        HashMap<String, String> result = null;
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null, salt);
        for (int retry = 0; retry < 4; retry++) try {
            // generate request
            post.add(new DefaultCharsetStringPart("count", "20"));
            post.add(new DefaultCharsetStringPart("seed", mySeed.genSeedStr(salt)));
            // send request
            final long start = System.currentTimeMillis();
            final byte[] content = wput("http://" + address + "/yacy/hello.html", yacySeed.b64Hash2hexHash(otherHash) + ".yacyh", post, 15000, false);
            yacyCore.log.logInfo("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' contacted peer at " + address + ", received " + ((content == null) ? "null" : content.length) + " bytes, time = " + (System.currentTimeMillis() - start) + " milliseconds");
            result = nxTools.table(content, "UTF-8");
            break;
        } catch (final Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                yacyCore.log.logWarning("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' interrupted.");
                return -1;
            }
            yacyCore.log.logWarning("yacyClient.publishMySeed thread '" + Thread.currentThread().getName() + "' exception: " + e.getMessage() + "; retry = " + retry);
            // try again (go into loop)
            result = null;
        }
        
        if (result == null || result.size() < 3) {
            yacyCore.log.logFine("yacyClient.publishMySeed result error: " +
            ((result == null) ? "result null" : ("result=" + result.toString())));
            return -1;
        }

        // check consistency with expectation
        yacySeed otherPeer = null;
        String seed;
        if ((otherHash != null) &&
            (otherHash.length() > 0) &&
            ((seed = result.get("seed0")) != null)) {
        	if (seed.length() > yacySeed.maxsize) {
            	yacyCore.log.logInfo("hello/client 0: rejected contacting seed; too large (" + seed.length() + " > " + yacySeed.maxsize + ")");
            } else {
            	otherPeer = yacySeed.genRemoteSeed(seed, salt, false);
            	if (otherPeer == null || !otherPeer.hash.equals(otherHash)) {
            		yacyCore.log.logFine("yacyClient.publishMySeed: consistency error: other peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' wrong");
            		return -1; // no success
            	}
            }
        }

        // set my own seed according to new information
        // we overwrite our own IP number only
        if (serverCore.useStaticIP) {
            mySeed.setIP(serverDomains.myPublicIP());
        } else {
            final String myIP = result.get("yourip");
            final String properIP = yacySeed.isProperIP(myIP);
            if (properIP == null) mySeed.setIP(myIP);
        }

        // change our seed-type
        String mytype = result.get(yacySeed.YOURTYPE);
        if (mytype == null) { mytype = ""; }        
        final yacyAccessible accessible = new yacyAccessible();
        if (mytype.equals(yacySeed.PEERTYPE_SENIOR)||mytype.equals(yacySeed.PEERTYPE_PRINCIPAL)) {
            accessible.IWasAccessed = true;
            if (mySeed.isPrincipal()) {
                mytype = yacySeed.PEERTYPE_PRINCIPAL;
            }
        } else {
            accessible.IWasAccessed = false;
        }
        accessible.lastUpdated = System.currentTimeMillis();
        yacyCore.amIAccessibleDB.put(otherHash, accessible);

        /* 
         * If we were reported as junior we have to check if your port forwarding channel is broken
         * If this is true we try to reconnect the sch channel to the remote server now.
         */
        if (mytype.equalsIgnoreCase(yacySeed.PEERTYPE_JUNIOR)) {
            yacyCore.log.logInfo("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as junior.");
        } else if ((mytype.equalsIgnoreCase(yacySeed.PEERTYPE_SENIOR)) ||
                   (mytype.equalsIgnoreCase(yacySeed.PEERTYPE_PRINCIPAL))) {
            yacyCore.log.logFine("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", accepted other peer.");
        } else {
            // wrong type report
            yacyCore.log.logFine("yacyClient.publishMySeed: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", rejecting other peer.");
            return -1;
        }
        if (mySeed.orVirgin().equals(yacySeed.PEERTYPE_VIRGIN))
            mySeed.put(yacySeed.PEERTYPE, mytype);

        final String error = mySeed.isProper(true);
        if (error != null) {
            yacyCore.log.logSevere("yacyClient.publishMySeed mySeed error - not proper: " + error);
            return -1;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time

        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        int count = 0;
        String seedStr;
        while ((seedStr = result.get("seed" + i++)) != null) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
        	if (seedStr.length() > yacySeed.maxsize) {
            	yacyCore.log.logInfo("hello/client: rejected contacting seed; too large (" + seedStr.length() + " > " + yacySeed.maxsize + ")");
            } else {
            	if (peerActions.peerArrival(yacySeed.genRemoteSeed(seedStr, salt, false), (i == 1))) count++;
            }
        }
        return count;
    }

    /**
     * send data to the server named by vhost
     * 
     * @param address address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param gzipBody send with content gzip encoded
     * @return response body
     * @throws IOException
     */
    /*
    private static byte[] wput(final String url, String vhost, final List<Part> post, boolean gzipBody) throws IOException {
        return wput(url, vhost, post, 10000, gzipBody);
    }
    */
    /**
     * send data to the server named by vhost
     * 
     * @param address address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    private static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout) throws IOException {
        return wput(url, vhost, post, timeout, false);
    }
    /**
     * send data to the server named by vhost
     * 
     * @param address address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param timeout in milliseconds
     * @param gzipBody send with content gzip encoded
     * @return response body
     * @throws IOException
     */
    private static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout, final boolean gzipBody) throws IOException {
        final httpHeader header = new httpHeader();
        header.put(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
        header.put(httpHeader.HOST, vhost);
        final JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(timeout, header);
        client.setProxy(proxyConfig());
        
        JakartaCommonsHttpResponse res = null;
        byte[] content = null;
        try {
            // send request/data
            res = client.POST(url, post, gzipBody);
            content = res.getData();
        } finally {
            if(res != null) {
                // release connection
                res.closeStream();
            }
        }
        return content;
    }

    /**
     * @see wput
     * @param target
     * @param filename
     * @param post
     * @return
     * @throws IOException
     */
    private static byte[] postToFile(final yacySeed target, final String filename, final List<Part> post, final int timeout) throws IOException {
        return wput("http://" + target.getClusterAddress() + "/yacy/" + filename, target.getHexHash() + ".yacyh", post, timeout, false);
    }
    private static byte[] postToFile(final yacySeedDB seedDB, final String targetHash, final String filename, final List<Part> post, final int timeout) throws IOException {
        return wput("http://" + targetAddress(seedDB, targetHash) + "/yacy/" + filename, yacySeed.b64Hash2hexHash(targetHash)+ ".yacyh", post, timeout, false);
    }

    public static yacySeed querySeed(final yacySeed target, final String seedHash) {
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("object", "seed"));
        post.add(new DefaultCharsetStringPart("env", seedHash));
            
        // send request
        try {
            final byte[] content = postToFile(target, "query.html", post, 10000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            
            if (result == null || result.size() == 0) { return null; }
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time
            return yacySeed.genRemoteSeed(result.get("response"), salt, false);
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(final yacySeed target, final String wordHash) {
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("object", "rwicount"));
        post.add(new DefaultCharsetStringPart("ttl", "0"));
        post.add(new DefaultCharsetStringPart("env", wordHash));
            
        // send request
        try {
            final byte[] content = postToFile(target, "query.html", post, 5000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            
            if (result == null || result.size() == 0) { return -1; }
            return Integer.parseInt(result.get("response"));
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.queryRWICount error:" + e.getMessage());
            return -1;
        }
    }

    public static int queryUrlCount(final yacySeed target) {        
        if (target == null) { return -1; }
        
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("object", "lurlcount"));
        post.add(new DefaultCharsetStringPart("ttl", "0"));
        post.add(new DefaultCharsetStringPart("env", ""));
        
        // send request
        try {
            final byte[] content = postToFile(target, "query.html", post, 5000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            
            if ((result == null) || (result.size() == 0)) return -1;
            final String resp = result.get("response");
            if (resp == null) {
                return -1;
            }
            try {
                return Integer.parseInt(resp);
            } catch (final NumberFormatException e) {
                return -1;
            }
        } catch (final IOException e) {
            yacyCore.log.logSevere("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
            return -1;
        }
    }

    public static RSSFeed queryRemoteCrawlURLs(final yacySeedDB seedDB, final yacySeed target, final int count) {
        // returns a list of 
        if (target == null) { return null; }
        
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("call", "remotecrawl"));
        post.add(new DefaultCharsetStringPart("count", Integer.toString(count)));
        
        // send request
        try {
            /* a long time-out is needed */
            final byte[] result = wput("http://" + target.getClusterAddress() + "/yacy/urls.xml", target.getHexHash() + ".yacyh", post, 60000); 
            final RSSReader reader = RSSReader.parse(result);
            if (reader == null) {
                yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (1), reader == null");
                target.put(yacySeed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //e.printStackTrace();
                return null;
            }
            final RSSFeed feed = reader.getFeed();
            if (feed == null) {
                // case where the rss reader does not understand the content
                yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (2)");
                System.out.println("***DEBUG*** rss input = " + new String(result));
                target.put(yacySeed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //e.printStackTrace();
                return null;
            }
            return feed;
        } catch (final IOException e) {
            yacyCore.log.logSevere("yacyClient.queryRemoteCrawlURLs error asking peer '" + target.getName() + "':" + e.toString());
            return null;
        }
    }

    public static String[] search(
            final yacySeed mySeed,
            final String wordhashes,
            final String excludehashes,
            final String urlhashes,
            final String prefer,
            final String filter,
            final int count,
            final int maxDistance,
            final boolean global, 
            final int partitions,
            final yacySeed target,
            final plasmaWordIndex wordIndex,
            final ResultURLs crawlResults,
            final plasmaSearchRankingProcess containerCache,
            final Map<String, TreeMap<String, String>> abstractCache,
            final indexReferenceBlacklist blacklist,
            final plasmaSearchRankingProfile rankingProfile,
            final kelondroBitfield constraint
    ) {
        // send a search request to peer with remote Hash
        // this mainly converts the words into word hashes

        // INPUT:
        // iam        : complete seed of the requesting peer
        // youare     : seed hash of the target peer, used for testing network stability
        // key        : transmission key for response
        // search     : a list of search words
        // hsearch    : a string of word hashes
        // fwdep      : forward depth. if "0" then peer may NOT ask another peer for more results
        // fwden      : forward deny, a list of seed hashes. They may NOT be target of forward hopping
        // count      : maximum number of wanted results
        // global     : if "true", then result may consist of answers from other peers
        // partitions : number of remote peers that are asked (for evaluation of QPM)
        // duetime    : maximum time that a peer should spent to create a result

        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("myseed", mySeed.genSeedStr(salt)));
        post.add(new DefaultCharsetStringPart("count", Integer.toString(Math.max(10, count))));
        post.add(new DefaultCharsetStringPart("resource", ((global) ? "global" : "local")));
        post.add(new DefaultCharsetStringPart("partitions", Integer.toString(partitions)));
        post.add(new DefaultCharsetStringPart("query", wordhashes));
        post.add(new DefaultCharsetStringPart("exclude", excludehashes));
        post.add(new DefaultCharsetStringPart("duetime", "1000"));
        post.add(new DefaultCharsetStringPart("urls", urlhashes));
        post.add(new DefaultCharsetStringPart("prefer", prefer));
        post.add(new DefaultCharsetStringPart("filter", filter));
        post.add(new DefaultCharsetStringPart("ttl", "0"));
        post.add(new DefaultCharsetStringPart("maxdist", Integer.toString(maxDistance)));
        post.add(new DefaultCharsetStringPart("profile", crypt.simpleEncode(rankingProfile.toExternalString())));
        post.add(new DefaultCharsetStringPart("constraint", (constraint == null) ? "" : constraint.exportB64()));
        if (abstractCache != null) post.add(new DefaultCharsetStringPart("abstracts", "auto"));
        final long timestamp = System.currentTimeMillis();

        // send request
        HashMap<String, String> result = null;
        try {
          	result = nxTools.table(wput("http://" + target.getClusterAddress() + "/yacy/search.html", target.getHexHash() + ".yacyh", post, 60000), "UTF-8");
        } catch (final IOException e) {
            yacyCore.log.logInfo("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + "), score=" + target.selectscore + ", DHTdist=" + yacyDHTAction.dhtDistance(target.hash, wordhashes.substring(0, 12)));
            //yacyCore.peerActions.peerDeparture(target, "search request to peer created io exception: " + e.getMessage());
            return null;
        }

        if ((result == null) || (result.size() == 0)) {
			yacyCore.log.logFine("SEARCH failed FROM "
					+ target.hash
					+ ":"
					+ target.getName()
					+ " (zero response), score="
					+ target.selectscore
					+ ", DHTdist="
					+ yacyDHTAction.dhtDistance(target.hash, wordhashes
							.substring(0, 12)));
			return null;
		}

		// compute all computation times
		final long totalrequesttime = System.currentTimeMillis() - timestamp;
		
		// OUTPUT:
		// version : application version of responder
		// uptime : uptime in seconds of responder
		// total : number of total available LURL's for this search
		// count : number of returned LURL's for this search
		// resource<n> : LURL of search
		// fwhop : hops (depth) of forwards that had been performed to construct this result
		// fwsrc : peers that helped to construct this result
		// fwrec : peers that would have helped to construct this result (recommendations)
		// searchtime : time that the peer actually spent to create the result
		// references : references (search hints) that was calculated during search
		
		// now create a plasmaIndex out of this result
		// System.out.println("yacyClient: " + ((urlhashes.length() == 0) ? "primary" : "secondary")+ " search result = " + result.toString()); // debug
		
		int results = 0, joincount = 0;
        try {
            results = Integer.parseInt(result.get("count"));
            joincount = Integer.parseInt(result.get("joincount"));
        } catch (final NumberFormatException e) {
            yacyCore.log.logInfo("SEARCH failed FROM " + target.hash + ":" + target.getName() + ", wrong output format: " + e.getMessage());
            //yacyCore.peerActions.peerDeparture(target, "search request to peer created number format exception");
            return null;
        }
		// System.out.println("***result count " + results);

		// create containers
		final int words = wordhashes.length() / yacySeedDB.commonHashLength;
		final indexContainer[] container = new indexContainer[words];
		for (int i = 0; i < words; i++) {
			container[i] = plasmaWordIndex.emptyContainer(wordhashes.substring(i * yacySeedDB.commonHashLength, (i + 1) * yacySeedDB.commonHashLength), count);
		}

		// insert results to containers
		indexURLReference urlEntry;
		final String[] urls = new String[results];
		for (int n = 0; n < results; n++) {
			// get one single search result
			urlEntry = indexURLReference.importEntry(result.get("resource" + n));
			if (urlEntry == null) continue;
			assert (urlEntry.hash().length() == 12) : "urlEntry.hash() = " + urlEntry.hash();
			if (urlEntry.hash().length() != 12) continue; // bad url hash
			final indexURLReference.Components comp = urlEntry.comp();
			if (blacklist.isListed(indexReferenceBlacklist.BLACKLIST_SEARCH, comp.url())) {
				yacyCore.log.logInfo("remote search (client): filtered blacklisted url " + comp.url() + " from peer " + target.getName());
				continue; // block with backlist
			}
            
			final String urlRejectReason = plasmaSwitchboard.getSwitchboard().acceptURL(comp.url());
            if (urlRejectReason != null) {
                yacyCore.log.logInfo("remote search (client): rejected url '" + comp.url() + "' (" + urlRejectReason + ") from peer " + target.getName());
                continue; // reject url outside of our domain
            }

			// save the url entry
			indexRWIEntry entry;
			if (urlEntry.word() == null) {
				yacyCore.log.logWarning("remote search (client): no word attached from peer " + target.getName() + ", version " + target.getVersion());
				continue; // no word attached
			}

			// the search-result-url transports all the attributes of word indexes
			entry = urlEntry.word();
			if (!(entry.urlHash().equals(urlEntry.hash()))) {
				yacyCore.log.logInfo("remote search (client): url-hash " + urlEntry.hash() + " does not belong to word-attached-hash " + entry.urlHash() + "; url = " + comp.url() + " from peer " + target.getName());
				continue; // spammed
			}

			// passed all checks, store url
			try {
				wordIndex.putURL(urlEntry);
				crawlResults.stack(urlEntry, mySeed.hash, target.hash, 2);
			} catch (final IOException e) {
				yacyCore.log.logSevere("could not store search result", e);
				continue; // db-error
			}

			if (urlEntry.snippet() != null) {
				// we don't store the snippets along the url entry,
                // because they are search-specific.
				// instead, they are placed in a snipped-search cache.
				// System.out.println("--- RECEIVED SNIPPET '" + link.snippet() + "'");
				plasmaSnippetCache.storeToCache(wordhashes, urlEntry.hash(), urlEntry.snippet());
			}
            
			// add the url entry to the word indexes
			for (int m = 0; m < words; m++) {
				container[m].add(entry, System.currentTimeMillis());
			}
            
			// store url hash for statistics
			urls[n] = urlEntry.hash();
		}

        // store remote result to local result container
        synchronized (containerCache) {
            // insert one container into the search result buffer
            containerCache.insertRanked(container[0], false, joincount); // one is enough
            
            // integrate remote topwords
            final String references = result.get("references");
            yacyCore.log.logInfo("remote search (client): peer " + target.getName() + " sent references " + references);
            if (references != null) {
                // add references twice, so they can be countet (must have at least 2 entries)
                containerCache.addReferences(references.split(","));
                containerCache.addReferences(references.split(","));
            }
        }
        
		// read index abstract
		if (abstractCache != null) {
			final Iterator<Map.Entry<String, String>> i = result.entrySet().iterator();
			Map.Entry<String, String> entry;
			TreeMap<String, String> singleAbstract;
			String wordhash;
			serverByteBuffer ci;
			while (i.hasNext()) {
				entry = i.next();
				if (entry.getKey().startsWith("indexabstract.")) {
					wordhash = entry.getKey().substring(14);
					synchronized (abstractCache) {
						singleAbstract = abstractCache.get(wordhash); // a mapping from url-hashes to a string of peer-hashes
						if (singleAbstract == null) singleAbstract = new TreeMap<String, String>();
						ci = new serverByteBuffer(entry.getValue().getBytes());
						//System.out.println("DEBUG-ABSTRACTFETCH: for word hash " + wordhash + " received " + ci.toString());
						indexContainer.decompressIndex(singleAbstract, ci, target.hash);
						abstractCache.put(wordhash, singleAbstract);
					}
				}
			}
		}

		// insert the containers to the index
        for (int m = 0; m < words; m++) {
            wordIndex.addEntries(container[m], true);
        }
        
        // generate statistics
		long searchtime;
		try {
			searchtime = Integer.parseInt(result.get("searchtime"));
		} catch (final NumberFormatException e) {
			searchtime = totalrequesttime;
		}
		yacyCore.log.logFine("SEARCH "
				+ results
				+ " URLS FROM "
				+ target.hash
				+ ":"
				+ target.getName()
				+ ", score="
				+ target.selectscore
				+ ", DHTdist="
				+ ((wordhashes.length() < 12) ? "void" : Double
						.toString(yacyDHTAction.dhtDistance(target.hash,
								wordhashes.substring(0, 12))))
				+ ", searchtime=" + searchtime + ", netdelay="
				+ (totalrequesttime - searchtime) + ", references="
				+ result.get("references"));
		return urls;
	}

    public static HashMap<String, String> permissionMessage(final yacySeedDB seedDB, final String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer
        
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetHash, salt);
        post.add(new DefaultCharsetStringPart("process", "permission"));
        
        // send request
        try {
            final byte[] content = postToFile(seedDB, targetHash, "message.html", post, 5000); 
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            return result;
        } catch (final Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> postMessage(final yacySeedDB seedDB, final String targetHash, final String subject, final byte[] message) {
        // this post a message to the remote message board

        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetHash, salt);
        post.add(new DefaultCharsetStringPart("process", "post"));
        post.add(new DefaultCharsetStringPart("myseed", seedDB.mySeed().genSeedStr(salt)));
        post.add(new DefaultCharsetStringPart("subject", subject));
        try {
            post.add(new DefaultCharsetStringPart("message", new String(message, "UTF-8")));
        } catch (final UnsupportedEncodingException e) {
            post.add(new DefaultCharsetStringPart("message", new String(message)));
        }

        // send request
        try {
            final byte[] content = postToFile(seedDB, targetHash, "message.html", post, 20000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            return result;
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }
    
    public static String targetAddress(final yacySeedDB seedDB, final String targetHash) {
        // find target address    
        String address;
        if (targetHash.equals(seedDB.mySeed().hash)) {
            address = seedDB.mySeed().getClusterAddress();
        } else {
            final yacySeed targetSeed = seedDB.getConnected(targetHash);
            if (targetSeed == null) { return null; }
            address = targetSeed.getClusterAddress();
        }
        if (address == null) address = "localhost:8080";
        return address;
    }
    
    public static HashMap<String, String> transferPermission(final String targetAddress, final long filesize, final String filename) {

        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null, salt);
        post.add(new DefaultCharsetStringPart("process", "permission"));
        post.add(new DefaultCharsetStringPart("purpose", "crcon"));
        post.add(new DefaultCharsetStringPart("filename", filename));
        post.add(new DefaultCharsetStringPart("filesize", Long.toString(filesize)));
        post.add(new DefaultCharsetStringPart("can-send-protocol", "http"));
        
        // send request
        try {
            final byte[] content = wput("http://" + targetAddress + "/yacy/transfer.html", targetAddress, post, 10000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            return result;
        } catch (final Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.permissionTransfer error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> transferStore(final String targetAddress, final String access, final String filename, final byte[] file) {
        
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), null, salt);
        post.add(new DefaultCharsetStringPart("process", "store"));
        post.add(new DefaultCharsetStringPart("purpose", "crcon"));
        post.add(new DefaultCharsetStringPart("filesize", Long.toString(file.length)));
        post.add(new DefaultCharsetStringPart("md5", serverCodings.encodeMD5Hex(file)));
        post.add(new DefaultCharsetStringPart("access", access));
        post.add(new DefaultCharsetFilePart("filename", new ByteArrayPartSource(filename, file)));
        
        // send request
        try {
            final byte[] content = wput("http://" + targetAddress + "/yacy/transfer.html", targetAddress, post, 20000);
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            return result;
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }
    
    public static String transfer(final String targetAddress, final String filename, final byte[] file) {
        final HashMap<String, String> phase1 = transferPermission(targetAddress, file.length, filename);
        if (phase1 == null) return "no connection to remote address " + targetAddress + "; phase 1";
        final String access = phase1.get("access");
        final String nextaddress = phase1.get("address");
        final String protocol = phase1.get("protocol");
        //String path = (String) phase1.get("path");
        //String maxsize = (String) phase1.get("maxsize");
        String response = phase1.get("response");
        if ((response == null) || (protocol == null) || (access == null)) return "wrong return values from other peer; phase 1";
        if (!(response.equals("ok"))) return "remote peer rejected transfer: " + response;
        final String accesscode = serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(access));
        if (protocol.equals("http")) {
            final HashMap<String, String> phase2 = transferStore(nextaddress, accesscode, filename, file);
            if (phase2 == null) return "no connection to remote address " + targetAddress + "; phase 2";
            response = phase2.get("response");
            if (response == null) return "wrong return values from other peer; phase 2";
            if (!(response.equals("ok"))) {
                return "remote peer failed with transfer: " + response;
            } 
            return null;
        }
        return "wrong protocol: " + protocol;
    }

    public static HashMap<String, String> crawlReceipt(final yacySeed mySeed, final yacySeed target, final String process, final String result, final String reason, final indexURLReference entry, final String wordhashes) {
        assert (target != null);
        assert (mySeed != null);
        assert (mySeed != target);

        /*
         the result can have one of the following values:
         negative cases, no retry
           unavailable - the resource is not avaiable (a broken link); not found or interrupted
           robot       - a robot-file has denied to crawl that resource
         
         negative cases, retry possible
           rejected    - the peer has rejected to load the resource
           dequeue     - peer too busy - rejected to crawl
         
         positive cases with crawling
           fill        - the resource was loaded and processed
           update      - the resource was already in database but re-loaded and processed
         
         positive cases without crawling
           known       - the resource is already in database, believed to be fresh and not reloaded
           stale       - the resource was reloaded but not processed because source had no changes
         
         */
        
        // prepare request
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), target.hash, salt);
        post.add(new DefaultCharsetStringPart("process", process));
        post.add(new DefaultCharsetStringPart("urlhash", ((entry == null) ? "" : entry.hash())));
        post.add(new DefaultCharsetStringPart("result", result));
        post.add(new DefaultCharsetStringPart("reason", reason));
        post.add(new DefaultCharsetStringPart("wordh", wordhashes));
        post.add(new DefaultCharsetStringPart("lurlEntry", ((entry == null) ? "" : crypt.simpleEncode(entry.toString(), salt))));
        
        // determining target address
        final String address = target.getClusterAddress();
        if (address == null) { return null; }
            
        // send request
        try {
            final byte[] content = wput("http://" + address + "/yacy/crawlReceipt.html", target.getHexHash() + ".yacyh", post, 10000);
            return nxTools.table(content, "UTF-8");
        } catch (final Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logSevere("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, Object> transferIndex(final yacySeedDB seedDB, final yacySeed targetSeed, final indexContainer[] indexes, final HashMap<String, indexURLReference> urlCache, final boolean gzipBody, final int timeout) {
        
        final HashMap<String, Object> resultObj = new HashMap<String, Object>();
        int payloadSize = 0;
        try {
            
            // check if we got all necessary urls in the urlCache (only for debugging)
            Iterator<indexRWIRowEntry> eenum;
            indexRWIEntry entry;
            for (int i = 0; i < indexes.length; i++) {
                eenum = indexes[i].entries();
                while (eenum.hasNext()) {
                    entry = eenum.next();
                    if (urlCache.get(entry.urlHash()) == null) {
                        yacyCore.log.logFine("DEBUG transferIndex: to-send url hash '" + entry.urlHash() + "' is not contained in urlCache");
                    }
                }
            }        
            
            // transfer the RWI without the URLs
            HashMap<String, String> in = transferRWI(seedDB, targetSeed, indexes, gzipBody, timeout);
            resultObj.put("resultTransferRWI", in);
            
            if (in == null) {
                resultObj.put("result", "no_connection_1");
                return resultObj;
            }        
            if (in.containsKey("indexPayloadSize")) payloadSize += Integer.parseInt(in.get("indexPayloadSize"));
            
            String result = in.get("result");
            if (result == null) { 
                resultObj.put("result", "no_result_1"); 
                return resultObj;
            }
            if (!(result.equals("ok"))) {
                targetSeed.setFlagAcceptRemoteIndex(false);
                seedDB.update(targetSeed.hash, targetSeed);
                resultObj.put("result", result);
                return resultObj;
            }
            
            // in now contains a list of unknown hashes
            final String uhss = in.get("unknownURL");
            if (uhss == null) {
                resultObj.put("result","no_unknownURL_tag_in_response");
                return resultObj;
            }
            if (uhss.length() == 0) { return resultObj; } // all url's known, we are ready here
            
            final String[] uhs = uhss.split(",");
            if (uhs.length == 0) { return resultObj; } // all url's known
            
            // extract the urlCache from the result
            final indexURLReference[] urls = new indexURLReference[uhs.length];
            for (int i = 0; i < uhs.length; i++) {
                urls[i] = urlCache.get(uhs[i]);
                if (urls[i] == null) {
                    yacyCore.log.logFine("DEBUG transferIndex: requested url hash '" + uhs[i] + "', unknownURL='" + uhss + "'");
                }
            }
            
            in = transferURL(seedDB, targetSeed, urls, gzipBody, timeout);
            resultObj.put("resultTransferURL", in);
            
            if (in == null) {
                resultObj.put("result","no_connection_2");
                return resultObj;
            }
            if (in.containsKey("urlPayloadSize")) payloadSize += Integer.parseInt(in.get("urlPayloadSize"));
            
            result = in.get("result");
            if (result == null) {
                resultObj.put("result","no_result_2");
                return resultObj;
            }
            if (!(result.equals("ok"))) {
                targetSeed.setFlagAcceptRemoteIndex(false);
                seedDB.update(targetSeed.hash, targetSeed);
                resultObj.put("result",result);
                return resultObj;
            }
    //      int doubleentries = Integer.parseInt((String) in.get("double"));
    //      System.out.println("DEBUG tansferIndex: transferred " + uhs.length + " URL's, double=" + doubleentries);
            
            return resultObj;
        } finally {
            resultObj.put("payloadSize", Integer.valueOf(payloadSize));
        }
    }

    private static HashMap<String, String> transferRWI(final yacySeedDB seedDB, final yacySeed targetSeed, final indexContainer[] indexes, boolean gzipBody, final int timeout) {
        final String address = targetSeed.getPublicAddress();
        if (address == null) { yacyCore.log.logWarning("no address for transferRWI"); return null; }

        // prepare post values
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash, salt);
        
        // enabling gzip compression for post request body
        if (gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED)) {
            gzipBody = false;
        }
        post.add(new DefaultCharsetStringPart("wordc", Integer.toString(indexes.length)));
        
        int indexcount = 0;
        final StringBuffer entrypost = new StringBuffer(indexes.length*73);
        Iterator<indexRWIRowEntry> eenum;
        indexRWIEntry entry;
        for (int i = 0; i < indexes.length; i++) {
            eenum = indexes[i].entries();
            while (eenum.hasNext()) {
                entry = eenum.next();
                entrypost.append(indexes[i].getWordHash()) 
                         .append(entry.toPropertyForm()) 
                         .append(serverCore.CRLF_STRING);
                indexcount++;
            }
        }

        if (indexcount == 0) {
            // nothing to do but everything ok
            final HashMap<String, String> result = new HashMap<String, String>(2);
            result.put("result", "ok");
            result.put("unknownURL", "");
            return result;
        }

        post.add(new DefaultCharsetStringPart("entryc", Integer.toString(indexcount)));
        post.add(new DefaultCharsetStringPart("indexes", entrypost.toString()));  
        try {
            final byte[] content = wput("http://" + address + "/yacy/transferRWI.html", targetSeed.getHexHash() + ".yacyh", post, timeout, gzipBody);
            final ArrayList<String> v = nxTools.strings(content, "UTF-8");
            // this should return a list of urlhashes that are unknown
            if ((v != null) && (v.size() > 0)) {
                seedDB.mySeed().incSI(indexcount);
            }
            
            final HashMap<String, String> result = nxTools.table(v);
            // return the transfered index data in bytes (for debugging only)
            result.put("indexPayloadSize", Integer.toString(entrypost.length()));
            return result;
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.transferRWI error:" + e.getMessage());
            return null;
        }
    }

    private static HashMap<String, String> transferURL(final yacySeedDB seedDB, final yacySeed targetSeed, final indexURLReference[] urls, boolean gzipBody, final int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getPublicAddress();
        if (address == null) { return null; }

        // prepare post values
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash, salt);
        
        // enabling gzip compression for post request body
        if (gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED)) {
            gzipBody = false;
        }
        
        String resource = "";
        int urlc = 0;
        int urlPayloadSize = 0;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                resource = urls[i].toString();                
                if (resource != null) {
                    post.add(new DefaultCharsetStringPart("url" + urlc, resource));
                    urlPayloadSize += resource.length();
                    urlc++;
                }
            }
        }
        post.add(new DefaultCharsetStringPart("urlc", Integer.toString(urlc)));
        try {
            final byte[] content = wput("http://" + address + "/yacy/transferURL.html", targetSeed.getHexHash() + ".yacyh", post, timeout, gzipBody);
            final ArrayList<String> v = nxTools.strings(content, "UTF-8");
            
            if ((v != null) && (v.size() > 0)) {
                seedDB.mySeed().incSU(urlc);
            }
            
            final HashMap<String, String> result = nxTools.table(v);
            // return the transfered url data in bytes (for debugging only)
            result.put("urlPayloadSize", Integer.toString(urlPayloadSize));            
            return result;
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.transferURL error:" + e.getMessage());
            return null;
        }
    }

    public static HashMap<String, String> getProfile(final yacySeed targetSeed) {

        // this post a message to the remote message board
        final String salt = crypt.randomSalt();
        final List<Part> post = yacyNetwork.basicRequestPost(plasmaSwitchboard.getSwitchboard(), targetSeed.hash, salt);
         
        String address = targetSeed.getClusterAddress();
        if (address == null) { address = "localhost:8080"; }
        try {
            final byte[] content = wput("http://" + address + "/yacy/profile.html", targetSeed.getHexHash() + ".yacyh", post, 5000);
            return nxTools.table(content, "UTF-8");
        } catch (final Exception e) {
            yacyCore.log.logSevere("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }
    
    /**
     * proxy for "to YaCy connections"
     * @return
     */
    private static final httpRemoteProxyConfig proxyConfig() {
        final httpRemoteProxyConfig p = httpRemoteProxyConfig.getRemoteProxyConfig();
        return ((p != null) && (p.useProxy()) && (p.useProxy4Yacy())) ? p : null;
    }

    public static void main(final String[] args) {
        if(args.length > 1) {
        System.out.println("yacyClient Test");
        try {
            final plasmaSwitchboard sb = new plasmaSwitchboard(new File(args[0]), "httpProxy.init", "DATA/SETTINGS/yacy.conf", false);
            /*final yacyCore core =*/ new yacyCore(sb);
            sb.loadSeedLists();
            final yacySeed target = sb.webIndex.seedDB.getConnected(args[1]);
            final String wordhashe = indexWord.word2hash("test");
            //System.out.println("permission=" + permissionMessage(args[1]));
            
            final httpHeader reqHeader = new httpHeader();
            reqHeader.put(httpHeader.USER_AGENT, HTTPLoader.crawlerUserAgent);
            final byte[] content = HttpClient.wget(
                                              "http://" + target.getPublicAddress() + "/yacy/search.html" +
                                                      "?myseed=" + sb.webIndex.seedDB.mySeed().genSeedStr(null) +
                                                      "&youare=" + target.hash + "&key=" +
                                                      "&myseed=" + sb.webIndex.seedDB.mySeed() .genSeedStr(null) +
                                                      "&count=10" +
                                                      "&resource=global" +
                                                      "&query=" + wordhashe +
                                                      "&network.unit.name=" + plasmaSwitchboard.getSwitchboard().getConfig("network.unit.name", yacySeed.DFLT_NETWORK_UNIT),
                                                      reqHeader, 10000, target.getHexHash() + ".yacyh");            
            final HashMap<String, String> result = nxTools.table(content, "UTF-8");
            System.out.println("Result=" + result.toString());
        } catch (final Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
        } else if(args.length == 1) {
            System.out.println("wput Test");
            // connection params
            URL url = null;
            try {
                url = new URL(args[0]);
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
            if(url == null) {
                System.exit(1);
                return;
            }
            final String vhost = url.getHost();
            final int timeout = 10000;
            final boolean gzipBody = false;
            // data
            final List<Part> post = new ArrayList<Part>();
            post.add(new DefaultCharsetStringPart("process", "permission"));
            post.add(new DefaultCharsetStringPart("purpose", "crcon"));
            //post.add(new FilePart("filename", new ByteArrayPartSource(filename, file)));
            // do it!
            try {
                final byte[] response = wput(url.toString(), vhost, post, timeout, gzipBody);
                System.out.println(new String(response));
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

}

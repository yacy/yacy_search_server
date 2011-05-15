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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.services.federated.opensearch.SRURSSConnector;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist;

import org.apache.http.entity.mime.content.ContentBody;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.ResultURLs;
import de.anomic.crawler.ResultURLs.EventOrigin;
import de.anomic.search.ContentDomain;
import de.anomic.search.QueryParams;
import de.anomic.search.RankingProfile;
import de.anomic.search.RankingProcess;
import de.anomic.search.SearchEvent;
import de.anomic.search.Segment;
import de.anomic.search.Switchboard;
import de.anomic.search.TextSnippet;
import de.anomic.server.serverCore;
import de.anomic.tools.crypt;

public final class yacyClient {


    private static byte[] postToFile(final yacySeed target, final String filename, final Map<String,ContentBody> parts, final int timeout) throws IOException {
        // return HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/" + filename), timeout, target.getHexHash() + ".yacyh", parts);
        final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
        return httpClient.POSTbytes(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/" + filename), target.getHexHash() + ".yacyh", parts, false);
    }
    private static byte[] postToFile(final yacySeedDB seedDB, final String targetHash, final String filename, final Map<String,ContentBody> parts, final int timeout) throws IOException {
        // return HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + targetAddress(seedDB, targetHash) + "/yacy/" + filename), timeout, yacySeed.b64Hash2hexHash(targetHash)+ ".yacyh", parts);
    	final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
    	return httpClient.POSTbytes(new MultiProtocolURI("http://" + targetAddress(seedDB, targetHash) + "/yacy/" + filename), yacySeed.b64Hash2hexHash(targetHash)+ ".yacyh", parts, false);
    }
    
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
    public static int hello(final yacySeed mySeed, final yacyPeerActions peerActions, final String address, final String otherHash) {
        
        Map<String, String> result = null;
        final String salt = crypt.randomSalt();
        try {
            // generate request
        	final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), null, salt);
            parts.put("count", UTF8.StringBody("20"));
            parts.put("magic", UTF8.StringBody(Long.toString(yacyCore.magic)));
            parts.put("seed", UTF8.StringBody(mySeed.genSeedStr(salt)));
            // send request
            final long start = System.currentTimeMillis();
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/hello.html"), 30000, yacySeed.b64Hash2hexHash(otherHash) + ".yacyh", parts);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), 30000);
            final byte[] content = httpClient.POSTbytes(new MultiProtocolURI("http://" + address + "/yacy/hello.html"), yacySeed.b64Hash2hexHash(otherHash) + ".yacyh", parts, false);
            yacyCore.log.logInfo("yacyClient.hello thread '" + Thread.currentThread().getName() + "' contacted peer at " + address + ", received " + ((content == null) ? "null" : content.length) + " bytes, time = " + (System.currentTimeMillis() - start) + " milliseconds");
            result = FileUtils.table(content);
        } catch (final Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                yacyCore.log.logInfo("yacyClient.hello thread '" + Thread.currentThread().getName() + "' interrupted.");
                return -1;
            }
            yacyCore.log.logInfo("yacyClient.hello thread '" + Thread.currentThread().getName() + "', peer " +  address + "; exception: " + e.getMessage());
            // try again (go into loop)
            result = null;
        }
        
        if (result == null) {
            yacyCore.log.logInfo("yacyClient.hello result error: " +
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
            	try {
            	    int p = address.indexOf(':');
            	    if (p < 0) return -1;
            	    String host = Domains.dnsResolve(address.substring(0, p)).getHostAddress();
                    otherPeer = yacySeed.genRemoteSeed(seed, salt, false, host);
                    if (!otherPeer.hash.equals(otherHash)) {
                        yacyCore.log.logInfo("yacyClient.hello: consistency error: otherPeer.hash = " + otherPeer.hash + ", otherHash = " + otherHash);
                        return -1; // no success
                    }
                } catch (IOException e) {
                    yacyCore.log.logInfo("yacyClient.hello: consistency error: other seed bad:" + e.getMessage() + ", seed=" + seed);
                    return -1; // no success
                }
            }
        }

        // set my own seed according to new information
        // we overwrite our own IP number only
        if (serverCore.useStaticIP) {
            mySeed.setIP(Switchboard.getSwitchboard().myPublicIP());
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
            yacyCore.log.logInfo("yacyClient.hello: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as junior.");
        } else if ((mytype.equalsIgnoreCase(yacySeed.PEERTYPE_SENIOR)) ||
                   (mytype.equalsIgnoreCase(yacySeed.PEERTYPE_PRINCIPAL))) {
            if (yacyCore.log.isFine()) yacyCore.log.logFine("yacyClient.hello: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", accepted other peer.");
        } else {
            // wrong type report
            if (yacyCore.log.isFine()) yacyCore.log.logFine("yacyClient.hello: Peer '" + ((otherPeer==null)?"unknown":otherPeer.getName()) + "' reported us as " + mytype + ", rejecting other peer.");
            return -1;
        }
        if (mySeed.orVirgin().equals(yacySeed.PEERTYPE_VIRGIN))
            mySeed.put(yacySeed.PEERTYPE, mytype);

        final String error = mySeed.isProper(true);
        if (error != null) {
            yacyCore.log.logWarning("yacyClient.hello mySeed error - not proper: " + error);
            return -1;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time

        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        int count = 0;
        String seedStr;
        yacySeed s;
        while ((seedStr = result.get("seed" + i++)) != null) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
        	if (seedStr.length() > yacySeed.maxsize) {
            	yacyCore.log.logInfo("hello/client: rejected contacting seed; too large (" + seedStr.length() + " > " + yacySeed.maxsize + ")");
            } else {
                try {
                    if (i == 1) {
                        int p = address.indexOf(':');
                        if (p < 0) return -1;
                        String host = Domains.dnsResolve(address.substring(0, p)).getHostAddress();
                        s = yacySeed.genRemoteSeed(seedStr, salt, false, host);
                    } else {
                        s = yacySeed.genRemoteSeed(seedStr, salt, false, null);
                    }
                    if (peerActions.peerArrival(s, (i == 1))) count++;
                } catch (IOException e) {
                    yacyCore.log.logInfo("hello/client: rejected contacting seed; bad (" + e.getMessage() + ")");
                }
            }
        }
        return count;
    }

    public static yacySeed querySeed(final yacySeed target, final String seedHash) {
        // prepare request
        final String salt = crypt.randomSalt();
            
        // send request
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("seed"));
            parts.put("env", UTF8.StringBody(seedHash));
            final byte[] content = postToFile(target, "query.html", parts, 10000);
            final Map<String, String> result = FileUtils.table(content);
            
            if (result == null || result.isEmpty()) { return null; }
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time
            return yacySeed.genRemoteSeed(result.get("response"), salt, false, target.getIP());
        } catch (final Exception e) {
            yacyCore.log.logWarning("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static int queryRWICount(final yacySeed target, final String wordHash) {
        // prepare request
        final String salt = crypt.randomSalt();
            
        // send request
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("rwicount"));
            parts.put("ttl", UTF8.StringBody("0"));
            parts.put("env", UTF8.StringBody(wordHash));
            final byte[] content = postToFile(target, "query.html", parts, 5000);
            final Map<String, String> result = FileUtils.table(content);
            
            if (result == null || result.isEmpty()) { return -1; }
            return Integer.parseInt(result.get("response"));
        } catch (final Exception e) {
            yacyCore.log.logWarning("yacyClient.queryRWICount error:" + e.getMessage());
            return -1;
        }
    }

    /**
     * check the status of a remote peer
     * @param target
     * @return an array of two long: [0] is the count of urls, [1] is a magic
     */
    public static long[] queryUrlCount(final yacySeed target) {        
        if (target == null) return new long[]{-1, -1};
        
        // prepare request
        final String salt = crypt.randomSalt();
        
        // send request
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("lurlcount"));
            parts.put("ttl", UTF8.StringBody("0"));
            parts.put("env", UTF8.StringBody(""));
            final byte[] content = postToFile(target, "query.html", parts, 5000);
            final Map<String, String> result = FileUtils.table(content);
            
            if (result == null || result.isEmpty()) return new long[]{-1, -1};
            final String resp = result.get("response");
            if (resp == null) return new long[]{-1, -1};
            String magic = result.get("magic"); if (magic == null) magic = "0";
            try {
                return new long[]{Long.parseLong(resp), Long.parseLong(magic)};
            } catch (final NumberFormatException e) {
                return new long[]{-1, -1};
            }
        } catch (final IOException e) {
            if (yacyCore.log.isFine()) yacyCore.log.logFine("yacyClient.queryUrlCount error asking peer '" + target.getName() + "':" + e.toString());
            return new long[]{-1, -1};
        }
    }

    public static RSSFeed queryRemoteCrawlURLs(final yacySeedDB seedDB, final yacySeed target, final int maxCount, final long maxTime) {
        // returns a list of 
        if (target == null) { return null; }
        int targetCount = Integer.parseInt(target.get(yacySeed.RCOUNT, "0"));
        if (targetCount <= 0) {
            yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs wrong peer '" + target.getName() + "' selected: not enough links available");
            return null;
        }
        // prepare request
        final String salt = crypt.randomSalt();
        
        // send request
        try {
            /* a long time-out is needed */
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("call", UTF8.StringBody("remotecrawl"));
            parts.put("count", UTF8.StringBody(Integer.toString(maxCount)));
            parts.put("time", UTF8.StringBody(Long.toString(maxTime)));
            // final byte[] result = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/urls.xml"), (int) maxTime, target.getHexHash() + ".yacyh", parts); 
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), (int) maxTime);
            final byte[] result = httpClient.POSTbytes(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/urls.xml"), target.getHexHash() + ".yacyh", parts, false);
            final RSSReader reader = RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
            if (reader == null) {
                yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (1), reader == null");
                target.put(yacySeed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //Log.logException(e);
                return null;
            }
            final RSSFeed feed = reader.getFeed();
            if (feed == null) {
                // case where the rss reader does not understand the content
                yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (2)");
                //System.out.println("***DEBUG*** rss input = " + UTF8.String(result));
                target.put(yacySeed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //Log.logException(e);
                return null;
            }
            // update number of remotely available links in seed
            target.put(yacySeed.RCOUNT, Integer.toString(Math.max(0, targetCount - feed.size())));
            seedDB.update(target.hash, target);
            return feed;
        } catch (final IOException e) {
            yacyCore.log.logWarning("yacyClient.queryRemoteCrawlURLs error asking peer '" + target.getName() + "':" + e.toString());
            return null;
        }
    }
    
    public static RSSFeed search(final yacySeed targetSeed, String query, CrawlProfile.CacheStrategy verify, boolean global, long timeout, int startRecord, int maximumRecords) throws IOException {
        String address = (targetSeed == null || targetSeed == Switchboard.getSwitchboard().peers.mySeed()) ? "localhost:" + Switchboard.getSwitchboard().getConfig("port", "8090") : targetSeed.getClusterAddress();
        String urlBase = "http://" + address + "/yacysearch.rss";
        return SRURSSConnector.loadSRURSS(urlBase, query, timeout, startRecord, maximumRecords, verify, global, null);
    }

    @SuppressWarnings("unchecked")
    public static int search(
            final yacySeed mySeed,
            final String wordhashes,
            final String excludehashes,
            final String urlhashes,
            final Pattern prefer,
            final Pattern filter,
            final Pattern snippet,
            final String language,
            final String sitehash,
            final String authorhash,
            final int count,
            final long time,
            final int maxDistance,
            final boolean global, 
            final int partitions,
            final yacySeed target,
            final Segment indexSegment,
            final RankingProcess containerCache,
            final SearchEvent.SecondarySearchSuperviser secondarySearchSuperviser,
            final Blacklist blacklist,
            final RankingProfile rankingProfile,
            final Bitfield constraint
    ) {
        // send a search request to peer with remote Hash

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

        final long timestamp = System.currentTimeMillis();
        SearchResult result;
        try {
            result = new SearchResult(
                yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, crypt.randomSalt()),
                mySeed, wordhashes, excludehashes, urlhashes, prefer, filter, snippet, language,
                sitehash, authorhash, count, time, maxDistance, global, partitions, target.getHexHash() + ".yacyh", target.getClusterAddress(),
                secondarySearchSuperviser, rankingProfile, constraint);
        } catch (final IOException e) {
            yacyCore.log.logInfo("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + ")");
            //yacyCore.peerActions.peerDeparture(target, "search request to peer created io exception: " + e.getMessage());
            return -1;
        }
        // computation time
        final long totalrequesttime = System.currentTimeMillis() - timestamp;
        
        // create containers
        final int words = wordhashes.length() / Word.commonHashLength;
        assert words > 0 : "wordhashes = " + wordhashes;
        final ReferenceContainer<WordReference>[] container = new ReferenceContainer[words];
        for (int i = 0; i < words; i++) {
            try {
                container[i] = ReferenceContainer.emptyContainer(Segment.wordReferenceFactory, UTF8.getBytes(wordhashes.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength)), count);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
                return -1;
            }
        }

        // insert results to containers
        for (URIMetadataRow urlEntry: result.links) {
            // get one single search result
            if (urlEntry == null) continue;
            assert (urlEntry.hash().length == 12) : "urlEntry.hash() = " + UTF8.String(urlEntry.hash());
            if (urlEntry.hash().length != 12) continue; // bad url hash
            final URIMetadataRow.Components metadata = urlEntry.metadata();
            if (metadata == null) continue;
            if (blacklist.isListed(Blacklist.BLACKLIST_SEARCH, metadata.url())) {
                if (yacyCore.log.isInfo()) yacyCore.log.logInfo("remote search: filtered blacklisted url " + metadata.url() + " from peer " + target.getName());
                continue; // block with backlist
            }
            
            final String urlRejectReason = Switchboard.getSwitchboard().crawlStacker.urlInAcceptedDomain(metadata.url());
            if (urlRejectReason != null) {
                if (yacyCore.log.isInfo()) yacyCore.log.logInfo("remote search: rejected url '" + metadata.url() + "' (" + urlRejectReason + ") from peer " + target.getName());
                continue; // reject url outside of our domain
            }

            // save the url entry
            Reference entry = urlEntry.word();
            if (entry == null) {
                if (yacyCore.log.isWarning()) yacyCore.log.logWarning("remote search: no word attached from peer " + target.getName() + ", version " + target.getVersion());
                continue; // no word attached
            }

            // the search-result-url transports all the attributes of word indexes
            if (!Base64Order.enhancedCoder.equal(entry.metadataHash(), urlEntry.hash())) {
                yacyCore.log.logInfo("remote search: url-hash " + UTF8.String(urlEntry.hash()) + " does not belong to word-attached-hash " + UTF8.String(entry.metadataHash()) + "; url = " + metadata.url() + " from peer " + target.getName());
                continue; // spammed
            }

            // passed all checks, store url
            try {
                indexSegment.urlMetadata().store(urlEntry);
                ResultURLs.stack(urlEntry, mySeed.hash.getBytes(), UTF8.getBytes(target.hash), EventOrigin.QUERIES);
            } catch (final IOException e) {
                yacyCore.log.logWarning("could not store search result", e);
                continue; // db-error
            }

            if (urlEntry.snippet() != null && urlEntry.snippet().length() > 0 && !urlEntry.snippet().equals("null")) {
                // we don't store the snippets along the url entry,
                // because they are search-specific.
                // instead, they are placed in a snipped-search cache.
                // System.out.println("--- RECEIVED SNIPPET '" + urlEntry.snippet() + "'");
                TextSnippet.snippetsCache.put(wordhashes, UTF8.String(urlEntry.hash()), urlEntry.snippet());
            }
            
            // add the url entry to the word indexes
            for (int m = 0; m < words; m++) {
                try {
                    container[m].add(entry);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                    break;
                }
            }
        }

        // store remote result to local result container
        // insert one container into the search result buffer
        // one is enough, only the references are used, not the word
        containerCache.add(container[0], false, target.getName() + "/" + target.hash, result.joincount, true);

        // insert the containers to the index
        for (ReferenceContainer<WordReference> c: container) try {
            indexSegment.termIndex().add(c);
        } catch (Exception e) {
            Log.logException(e);
        }
        boolean thisIsASecondarySearch = urlhashes.length() > 0;
        assert !thisIsASecondarySearch || secondarySearchSuperviser == null;

        yacyCore.log.logInfo("remote search: peer " + target.getName() + " sent " + container[0].size() + "/" + result.joincount + " references for " + (thisIsASecondarySearch ? "a secondary search" : "joined word queries"));

        // integrate remote top-words/topics
        if (result.references != null && result.references.length > 0) {
            yacyCore.log.logInfo("remote search: peer " + target.getName() + " sent " + result.references.length + " topics");
            // add references twice, so they can be counted (must have at least 2 entries)
            synchronized (containerCache) {
                containerCache.addTopic(result.references);
                containerCache.addTopic(result.references);
            }
        }
        
        // read index abstract
        if (secondarySearchSuperviser != null) {
            String wordhash;
            String whacc = "";
            ByteBuffer ci;
            int ac = 0;
            for (Map.Entry<byte[], String> abstractEntry: result.indexabstract.entrySet()) {
                try {
                    ci = new ByteBuffer(abstractEntry.getValue());
                    wordhash = UTF8.String(abstractEntry.getKey());
                } catch (OutOfMemoryError e) {
                    Log.logException(e);
                    continue;
                }
                whacc += wordhash;
                secondarySearchSuperviser.addAbstract(wordhash, WordReferenceFactory.decompressIndex(ci, target.hash));
                ac++;
                
            }
            if (ac > 0) {
                secondarySearchSuperviser.commitAbstract();
                yacyCore.log.logInfo("remote search: peer " + target.getName() + " sent " + ac + " index abstracts for words "+ whacc);
            }
        }
        
        // generate statistics
        if (yacyCore.log.isFine()) yacyCore.log.logFine(
                "SEARCH " + result.urlcount +
                " URLS FROM " + target.hash + ":" + target.getName() +
                ", searchtime=" + result.searchtime +
                ", netdelay=" + (totalrequesttime - result.searchtime) +
                ", references=" + result.references);
        return result.urlcount;
    }
    
    public static class SearchResult {
        
        public String version; // version : application version of responder
        public String uptime; // uptime : uptime in seconds of responder
        public String fwhop; // hops (depth) of forwards that had been performed to construct this result
        public String fwsrc; // peers that helped to construct this result
        public String fwrec; // peers that would have helped to construct this result (recommendations)
        public int urlcount; // number of returned LURL's for this search
        public int joincount; //
        public Map<byte[], Integer> indexcount; //
        public long searchtime; // time that the peer actually spent to create the result
        public String[] references; // search hints, the top-words
        public List<URIMetadataRow> links; // LURLs of search
        public Map<byte[], String> indexabstract; // index abstracts, a collection of url-hashes per word
        
        public SearchResult(
                Map<String,ContentBody> parts,
                final yacySeed mySeed,
                final String wordhashes,
                final String excludehashes,
                final String urlhashes,
                final Pattern prefer,
                final Pattern filter,
                final Pattern snippet,
                final String language,
                final String sitehash,
                final String authorhash,
                final int count,
                final long time,
                final int maxDistance,
                final boolean global, 
                final int partitions,
                String hostname,
                String hostaddress,
                final SearchEvent.SecondarySearchSuperviser secondarySearchSuperviser,
                final RankingProfile rankingProfile,
                final Bitfield constraint) throws IOException {
            // send a search request to peer with remote Hash

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
            
            // send request
            Map<String, String> resultMap = null;
            String key = "";
            ContentBody keyBody = parts.get("key");
            if (keyBody != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(20);
                keyBody.writeTo(baos);
                key = baos.toString();
            }
            parts.put("myseed", UTF8.StringBody((mySeed == null) ? "" : mySeed.genSeedStr(key)));
            parts.put("count", UTF8.StringBody(Integer.toString(Math.max(10, count))));
            parts.put("time", UTF8.StringBody(Long.toString(Math.max(3000, time))));
            parts.put("resource", UTF8.StringBody(((global) ? "global" : "local")));
            parts.put("partitions", UTF8.StringBody(Integer.toString(partitions)));
            parts.put("query", UTF8.StringBody(wordhashes));
            parts.put("exclude", UTF8.StringBody(excludehashes));
            parts.put("duetime", UTF8.StringBody("1000"));
            parts.put("urls", UTF8.StringBody(urlhashes));
            parts.put("prefer", UTF8.StringBody(prefer.pattern()));
            parts.put("filter", UTF8.StringBody(filter.pattern()));
            parts.put("snippet", UTF8.StringBody(snippet.pattern()));
            parts.put("language", UTF8.StringBody(language));
            parts.put("sitehash", UTF8.StringBody(sitehash));
            parts.put("authorhash", UTF8.StringBody(authorhash));
            parts.put("ttl", UTF8.StringBody("0"));
            parts.put("maxdist", UTF8.StringBody(Integer.toString(maxDistance)));
            parts.put("profile", UTF8.StringBody(crypt.simpleEncode(rankingProfile.toExternalString())));
            parts.put("constraint", UTF8.StringBody((constraint == null) ? "" : constraint.exportB64()));
            if (secondarySearchSuperviser != null) parts.put("abstracts", UTF8.StringBody("auto"));
            // resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + hostaddress + "/yacy/search.html"), 60000, hostname, parts));
            //resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.crawlerUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/search.html"), 60000, target.getHexHash() + ".yacyh", parts));

            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), 60000);
            resultMap = FileUtils.table(httpClient.POSTbytes(new MultiProtocolURI("http://" + hostaddress + "/yacy/search.html"), hostname, parts, false));
            
            // evaluate request result
            if (resultMap == null || resultMap.isEmpty()) throw new IOException("resultMap is NULL");
            try {
                this.searchtime = Integer.parseInt(resultMap.get("searchtime"));
            } catch (final NumberFormatException e) {
                throw new IOException("wrong output format for searchtime: " + e.getMessage() + ", map = " + resultMap.toString());
            }
            try {
                this.joincount = Integer.parseInt(resultMap.get("joincount")); // the complete number of hits at remote site
            } catch (final NumberFormatException e) {
                throw new IOException("wrong output format for joincount: " + e.getMessage());
            }
            try {
                this.urlcount = Integer.parseInt(resultMap.get("count"));      // the number of hits that are returned in the result list
            } catch (final NumberFormatException e) {
                throw new IOException("wrong output format for count: " + e.getMessage());
            }
            this.fwhop = resultMap.get("fwhop");
            this.fwsrc = resultMap.get("fwsrc");
            this.fwrec = resultMap.get("fwrec");
            // scan the result map for entries with special prefix
            indexcount = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
            indexabstract = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
            for (Map.Entry<String, String> entry: resultMap.entrySet()) {
                if (entry.getKey().startsWith("indexcount.")) {
                    indexcount.put(UTF8.getBytes(entry.getKey().substring(11)), Integer.parseInt(entry.getValue()));
                }
                if (entry.getKey().startsWith("indexabstract.")) {
                    indexabstract.put(UTF8.getBytes(entry.getKey().substring(14)), entry.getValue());
                }
            }
            references = resultMap.get("references").split(",");
            this.links = new ArrayList<URIMetadataRow>(this.urlcount);
            for (int n = 0; n < this.urlcount; n++) {
                // get one single search result
                String resultLine = resultMap.get("resource" + n);
                if (resultLine == null) continue;
                URIMetadataRow urlEntry = URIMetadataRow.importEntry(resultLine);
                if (urlEntry == null) continue;
                this.links.add(urlEntry);
            }
        }
    }
    
    public static Map<String, String> permissionMessage(final yacySeedDB seedDB, final String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer
        
        // prepare request
        final String salt = crypt.randomSalt();
        
        // send request
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), targetHash, salt);
            parts.put("process", UTF8.StringBody("permission"));
            final byte[] content = postToFile(seedDB, targetHash, "message.html", parts, 5000);
            final Map<String, String> result = FileUtils.table(content);
            return result;
        } catch (final Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logWarning("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> postMessage(final yacySeedDB seedDB, final String targetHash, final String subject, final byte[] message) {
        // this post a message to the remote message board

        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), targetHash, salt);
            parts.put("process", UTF8.StringBody("post"));
            parts.put("myseed", UTF8.StringBody(seedDB.mySeed().genSeedStr(salt)));
            parts.put("subject", UTF8.StringBody(subject));
            parts.put("message", UTF8.StringBody(message));
            final byte[] content = postToFile(seedDB, targetHash, "message.html", parts, 20000);
            final Map<String, String> result = FileUtils.table(content);
            return result;
        } catch (final Exception e) {
            yacyCore.log.logWarning("yacyClient.postMessage error:" + e.getMessage());
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
        if (address == null) address = "localhost:8090";
        return address;
    }

    public static Map<String, String> crawlReceipt(final yacySeed mySeed, final yacySeed target, final String process, final String result, final String reason, final URIMetadataRow entry, final String wordhashes) {
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
        
        // determining target address
        final String address = target.getClusterAddress();
        if (address == null) { return null; }
            
        // send request
        try {
            // prepare request
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("process", UTF8.StringBody(process));
            parts.put("urlhash", UTF8.StringBody(((entry == null) ? "" : UTF8.String(entry.hash()))));
            parts.put("result", UTF8.StringBody(result));
            parts.put("reason", UTF8.StringBody(reason));
            parts.put("wordh", UTF8.StringBody(wordhashes));
            parts.put("lurlEntry", UTF8.StringBody(((entry == null) ? "" : crypt.simpleEncode(entry.toString(), salt))));
            // send request
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/crawlReceipt.html"), 10000, target.getHexHash() + ".yacyh", parts);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), 10000);
            final byte[] content = httpClient.POSTbytes(new MultiProtocolURI("http://" + address + "/yacy/crawlReceipt.html"), target.getHexHash() + ".yacyh", parts, false);
            return FileUtils.table(content);
        } catch (final Exception e) {
            // most probably a network time-out exception
            yacyCore.log.logWarning("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }

    /**
     * transfer the index. If the transmission fails, return a string describing the cause.
     * If everything is ok, return null.
     * @param targetSeed
     * @param indexes
     * @param urlCache
     * @param gzipBody
     * @param timeout
     * @return
     */
    public static String transferIndex(
            final yacySeed targetSeed,
            final ReferenceContainerCache<WordReference> indexes,
            final SortedMap<byte[], URIMetadataRow> urlCache,
            final boolean gzipBody,
            final int timeout) {

        // check if we got all necessary urls in the urlCache (only for debugging)
        Iterator<WordReference> eenum;
        Reference entry;
        for (ReferenceContainer<WordReference> ic: indexes) {
            eenum = ic.entries();
            while (eenum.hasNext()) {
                entry = eenum.next();
                if (urlCache.get(entry.metadataHash()) == null) {
                    if (yacyCore.log.isFine()) yacyCore.log.logFine("DEBUG transferIndex: to-send url hash '" + UTF8.String(entry.metadataHash()) + "' is not contained in urlCache");
                }
            }
        }        
        
        // transfer the RWI without the URLs
        Map<String, String> in = transferRWI(targetSeed, indexes, gzipBody, timeout);
        
        if (in == null) {
            return "no connection from transferRWI";
        }
        
        String result = in.get("result");
        if (result == null) {
            return "no result from transferRWI";
        }
        
        if (!(result.equals("ok"))) {
            return result;
        }
        
        // in now contains a list of unknown hashes
        String uhss = in.get("unknownURL");
        if (uhss == null) {
            return "no unknownURL tag in response";
        }
        yacyChannel.channels(yacyChannel.DHTSEND).addMessage(new RSSMessage("Sent " + indexes.size() + " RWIs to " + targetSeed.getName(), "", targetSeed.hash));
        
        uhss = uhss.trim();
        if (uhss.length() == 0 || uhss.equals(",")) { return null; } // all url's known, we are ready here
        
        final String[] uhs = uhss.split(",");
        if (uhs.length == 0) { return null; } // all url's known
        
        // extract the urlCache from the result
        final URIMetadataRow[] urls = new URIMetadataRow[uhs.length];
        for (int i = 0; i < uhs.length; i++) {
            urls[i] = urlCache.get(UTF8.getBytes(uhs[i]));
            if (urls[i] == null) {
                if (yacyCore.log.isFine()) yacyCore.log.logFine("DEBUG transferIndex: requested url hash '" + uhs[i] + "', unknownURL='" + uhss + "'");
            }
        }
        
        in = transferURL(targetSeed, urls, gzipBody, timeout);
        
        if (in == null) {
            return "no connection from transferURL";
        }
        
        result = in.get("result");
        if (result == null) {
            return "no result from transferURL";
        }
        
        if (!result.equals("ok")) {
            return result;
        }            
        yacyChannel.channels(yacyChannel.DHTSEND).addMessage(new RSSMessage("Sent " + uhs.length + " URLs to peer " + targetSeed.getName(), "", targetSeed.hash));
        
        return null;
    }

    private static Map<String, String> transferRWI(
            final yacySeed targetSeed,
            final ReferenceContainerCache<WordReference> indexes,
            boolean gzipBody,
            final int timeout) {
        final String address = targetSeed.getPublicAddress();
        if (address == null) { yacyCore.log.logWarning("no address for transferRWI"); return null; }

        // prepare post values
        final String salt = crypt.randomSalt();
        
        // enabling gzip compression for post request body
        if (gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED)) {
            gzipBody = false;
        }
        
        int indexcount = 0;
        final StringBuilder entrypost = new StringBuilder(indexes.size() * 73);
        Iterator<WordReference> eenum;
        Reference entry;
        for (ReferenceContainer<WordReference> ic: indexes) {
            eenum = ic.entries();
            while (eenum.hasNext()) {
                entry = eenum.next();
                entrypost.append(UTF8.String(ic.getTermHash())) 
                         .append(entry.toPropertyForm()) 
                         .append(serverCore.CRLF_STRING);
                indexcount++;
            }
        }

        if (indexcount == 0) {
            // nothing to do but everything ok
            final Map<String, String> result = new HashMap<String, String>(2);
            result.put("result", "ok");
            result.put("unknownURL", "");
            return result;
        }
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
            parts.put("wordc", UTF8.StringBody(Integer.toString(indexes.size())));
            parts.put("entryc", UTF8.StringBody(Integer.toString(indexcount)));
            parts.put("indexes", UTF8.StringBody(entrypost.toString()));
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/transferRWI.html"), timeout, targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
            final byte[] content = httpClient.POSTbytes(new MultiProtocolURI("http://" + address + "/yacy/transferRWI.html"), targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final Iterator<String> v = FileUtils.strings(content);
            // this should return a list of urlhashes that are unknown
            
            final Map<String, String> result = FileUtils.table(v);
            // return the transfered index data in bytes (for debugging only)
            result.put("indexPayloadSize", Integer.toString(entrypost.length()));
            return result;
        } catch (final Exception e) {
            yacyCore.log.logInfo("yacyClient.transferRWI to " + address + " error: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, String> transferURL(final yacySeed targetSeed, final URIMetadataRow[] urls, boolean gzipBody, final int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getPublicAddress();
        if (address == null) { return null; }

        // prepare post values
        final String salt = crypt.randomSalt();
        final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
        
        // enabling gzip compression for post request body
        if (gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED)) {
            gzipBody = false;
        }
        
        String resource;
        int urlc = 0;
        int urlPayloadSize = 0;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                resource = urls[i].toString();
                //System.out.println("*** DEBUG resource = " + resource);
                if (resource != null && resource.indexOf(0) == -1) {
                    parts.put("url" + urlc, UTF8.StringBody(resource));
                    urlPayloadSize += resource.length();
                    urlc++;
                }
            }
        }
        try {
            parts.put("urlc", UTF8.StringBody(Integer.toString(urlc)));
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/transferURL.html"), timeout, targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
            final byte[] content = httpClient.POSTbytes(new MultiProtocolURI("http://" + address + "/yacy/transferURL.html"), targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final Iterator<String> v = FileUtils.strings(content);
            
            final Map<String, String> result = FileUtils.table(v);
            // return the transfered url data in bytes (for debugging only)
            result.put("urlPayloadSize", Integer.toString(urlPayloadSize));            
            return result;
        } catch (final Exception e) {
            yacyCore.log.logWarning("yacyClient.transferURL to " + address + " error: " + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> getProfile(final yacySeed targetSeed) {

        // this post a message to the remote message board
        final String salt = crypt.randomSalt();
         
        String address = targetSeed.getClusterAddress();
        if (address == null) { address = "localhost:8090"; }
        try {
            final Map<String,ContentBody> parts = yacyNetwork.basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/profile.html"), 5000, targetSeed.getHexHash() + ".yacyh", parts);
            final HTTPClient httpclient = new HTTPClient(ClientIdentification.getUserAgent(), 5000);
            final byte[] content = httpclient.POSTbytes(new MultiProtocolURI("http://" + address + "/yacy/profile.html"), targetSeed.getHexHash() + ".yacyh", parts, false);
            return FileUtils.table(content);
        } catch (final Exception e) {
            yacyCore.log.logWarning("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }

    public static void main(final String[] args) {
        if(args.length > 2) {
            // search a remote peer. arguments:
            // first  arg: path to application home
            // second arg: address of target peer
            // third  arg: search word or file name with list of search words
            // i.e. /Data/workspace1/yacy/ localhost:8090 /Data/workspace1/yacy/test/words/searchtest.words
            System.out.println("yacyClient Test");
                File searchwordfile = new File(args[2]);
                List<String> searchlines = new ArrayList<String>();
                if (searchwordfile.exists()) {
                    Iterator<String> i;
                    try {
                        i = FileUtils.strings(FileUtils.read(searchwordfile));
                        while (i.hasNext()) searchlines.add(i.next());
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                } else {
                    searchlines.add(args[2]);
                }
                for (final String line: searchlines) {
                    final byte[] wordhashe = UTF8.getBytes(QueryParams.hashSet2hashString(Word.words2hashesHandles(QueryParams.cleanQuery(line)[0])));
                    long time = System.currentTimeMillis();
                    SearchResult result;
                    try {
                        result = new SearchResult(
                                yacyNetwork.basicRequestParts((String) null, (String) null, "freeworld"),
                                null, // sb.peers.mySeed(),
                                UTF8.String(wordhashe),
                                "", // excludehashes,
                                "", // urlhashes,
                                QueryParams.matchnothing_pattern, // prefer,
                                QueryParams.catchall_pattern, // filter,
                                QueryParams.catchall_pattern, // snippet,
                                "", // language,
                                "", // sitehash,
                                "", // authorhash,
                                10, // count,
                                3000, // time,
                                1000, // maxDistance,
                                true, //global, 
                                16, // partitions,
                                "", args[1],
                                null, //secondarySearchSuperviser,
                                new RankingProfile(ContentDomain.TEXT), // rankingProfile,
                                null // constraint);
                        );
                        for (URIMetadataRow link: result.links) {
                                System.out.println(link.metadata().url().toNormalform(true, false));
                                System.out.println(link.snippet());
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    System.out.println("Search Time: " + (System.currentTimeMillis() - time));
                }
            System.exit(0);
        } else if(args.length == 1) {
            System.out.println("wput Test");
            // connection params
            MultiProtocolURI url = null;
            try {
                url = new MultiProtocolURI(args[0]);
            } catch (final MalformedURLException e) {
                Log.logException(e);
            }
            if (url == null) {
                System.exit(1);
                return;
            }
            final String vhost = url.getHost();
            final int timeout = 10000;
            // new data
            final Map<String,ContentBody> newpost = new LinkedHashMap<String,ContentBody>();
            newpost.put("process", UTF8.StringBody("permission"));
            newpost.put("purpose", UTF8.StringBody("crcon"));
			byte[] res;
			try {
				// res = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(url, timeout, vhost, newpost, true);
				final HTTPClient httpClient = new HTTPClient(ClientIdentification.getUserAgent(), timeout);
				res = httpClient.POSTbytes(url, vhost, newpost, true);
				System.out.println(UTF8.String(res));
			} catch (IOException e1) {
				Log.logException(e1);
			}
        }
		try {
			net.yacy.cora.protocol.http.HTTPClient.closeConnectionManager();
		} catch (InterruptedException e) {
			Log.logException(e);
		}
    }

}

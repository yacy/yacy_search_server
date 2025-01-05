// Protocol.java
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

package net.yacy.peers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.yacy.migration;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.ResultURLs;
import net.yacy.crawler.data.ResultURLs.EventOrigin;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.peers.graphics.WebStructureGraph;
import net.yacy.peers.graphics.WebStructureGraph.HostReference;
import net.yacy.peers.operation.yacyVersion;
import net.yacy.repository.Blacklist;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SecondarySearchSuperviser;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.snippet.TextSnippet;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;


public final class Protocol {

    /**
     * wrapper class for multi-post attempts to multiple IPs
     */
    public static class Post {

        /** Contains the result from a successful post or null if no attempt was successful */
        private byte[] result;

        /**
         * @param targetBaseURL the base target URL
         * @param targetHash the hash of the target peer
         * @param path the path on the base URL
         * @param parts the body content
         * @param timeout the timeout in milliseconds
         * @param httpFallback when true, retry as http when a https request failed
         * @throws IOException
         */
        public Post(
            final MultiProtocolURL targetBaseURL,
            final String targetHash,
            final String path,
            final Map<String, ContentBody> parts,
            final int timeout) throws IOException {
            try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
                httpClient.setTimout(timeout);
                MultiProtocolURL targetURL = new MultiProtocolURL(targetBaseURL, path);
                this.result = httpClient.POSTbytes(targetURL, Seed.b64Hash2hexHash(targetHash) + ".yacyh", parts, false, true);
            }
        }

        /**
         * @return the result from a successful post or null if no attempt was successful
         */
        public byte[] getResult() {
            return this.result;
        }
    }

    /**
     * this is called to enrich the seed information by - own address (if peer is behind a nat/router) - check
     * peer type (virgin/junior/senior/principal) to do this, we send a 'Hello' to another peer this carries
     * the following information: 'iam' - own hash 'youare' - remote hash, to verify that we are correct 'key'
     * - a session key that the remote peer may use to answer and the own seed string we expect the following
     * information to be send back: - 'yourip' the ip of the connection peer (we) - 'yourtype' the type of
     * this peer that the other peer checked by asking for a specific word and the remote seed string one
     * exceptional failure case is when we know the other's peers hash, the other peers responds correctly but
     * they appear to be another peer by comparisment of the other peer's hash this works of course only if we
     * know the other peer's hash.
     *
     * @return the number of new seeds
     */
    public static Map<String, String> hello(
        final Seed mySeed,
        final PeerActions peerActions,
        final MultiProtocolURL targetBaseURL,
        final String targetHash) {

        Map<String, String> result = null;
        final String salt = crypt.randomSalt();
        long responseTime = Long.MAX_VALUE;
        byte[] content = null;
        try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 30000)) {
            // generate request
            final Map<String, ContentBody> parts = basicRequestParts(Switchboard.getSwitchboard(), null, salt);
            parts.put("count", UTF8.StringBody("20"));
            parts.put("magic", UTF8.StringBody(Long.toString(Network.magic)));
            parts.put("seed", UTF8.StringBody(mySeed.genSeedStr(salt)));
            // send request
            final long start = System.currentTimeMillis();
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/hello.html"), 30000, yacySeed.b64Hash2hexHash(otherHash) + ".yacyh", parts);
            content = httpClient.POSTbytes(
                    new MultiProtocolURL(targetBaseURL, "/yacy/hello.html"),
                    Seed.b64Hash2hexHash(targetHash) + ".yacyh",
                    parts,
                    false, true);
            responseTime = System.currentTimeMillis() - start;
            result = FileUtils.table(content);
        } catch (final Exception e ) {
            if ( Thread.currentThread().isInterrupted() ) {Network.log.info("yacyClient.hello thread '" + Thread.currentThread().getName() + "' interrupted.");
                return null;
            }
            Network.log.info("yacyClient.hello thread '" + Thread.currentThread().getName() + "', peer " + targetBaseURL + "; exception: " + e.getMessage());
            // try again (go into loop)
            result = null;
        }

        if (result == null || result.size() == 0) {
            Network.log.info("yacyClient.hello result error: "
                + ((result == null) ? "result null" : ("result=" + result.toString())));
            return null;
        }
        Network.log.info("yacyClient.hello thread '" + Thread.currentThread().getName() + "' contacted peer at " + targetBaseURL + ", received " + ((content == null) ? "null" : content.length) + " bytes, time = " + responseTime + " milliseconds");

        // check consistency with expectation
        Seed otherPeer = null;
        String seed;
        if ( (targetHash != null) && (targetHash.length() > 0) && ((seed = result.get("seed0")) != null) ) {
            if ( seed.length() > Seed.maxsize ) {
                Network.log.info("hello/client 0: rejected contacting seed; too large (" + seed.length() + " > " + Seed.maxsize + ")");
            } else {
                try {
                    // patch the remote peer address to avoid that remote peers spoof the network with wrong addresses
                    String host = Domains.stripToHostName(targetBaseURL.getHost());
                    InetAddress ie = Domains.dnsResolve(host);
                    otherPeer = Seed.genRemoteSeed(seed, false, ie.getHostAddress());
                    if ( !otherPeer.hash.equals(targetHash) ) {
                        Network.log.info("yacyClient.hello: consistency error: otherPeer.hash = " + otherPeer.hash + ", otherHash = " + targetHash);
                        return null; // no success
                    }
                } catch (final IOException e ) {
                    Network.log.info("yacyClient.hello: consistency error: other seed bad:" + e.getMessage() + ", seed=" + seed);
                    return null; // no success
                }
            }
        }

        // get access type response
        String mytype = result.get(Seed.YOURTYPE);
        if ( mytype == null ) {
            mytype = "";
        }

        // set my own seed according to new information
        // we overwrite our own IP number only
        if ( serverCore.useStaticIP ) {
            mySeed.setIPs(Switchboard.getSwitchboard().myPublicIPs());
        } else {
            final String myIP = result.get("yourip");
            if (myIP == null) {
                Network.log.info("yacyClient.hello result error: Peer sent incompleet hello message (key yourip is missing)");
                return null; // no success
            }
            // with the IPv6 extension, this may contain several ips, separated by comma ','
            HashSet<String> h = new HashSet<>();
            for (String s: CommonPattern.COMMA.split(myIP)) {
                if (s.length() > 0 && Seed.isProperIP(s)) h.add(s);
            }
            if (h.size() > 0) mySeed.setIPs(h);
        }
        mySeed.setFlagRootNode(
                (mytype.equals(Seed.PEERTYPE_SENIOR) || mytype.equals(Seed.PEERTYPE_PRINCIPAL)) &&
                Switchboard.getSwitchboard().index.fulltext().connectedLocalSolr() &&
                responseTime < 1000 && Domains.isThisHostIP(mySeed.getIPs())
                );

        // change our seed-type
        final Accessible accessible = new Accessible();
        if ( mytype.equals(Seed.PEERTYPE_SENIOR) || mytype.equals(Seed.PEERTYPE_PRINCIPAL) ) {
            accessible.IWasAccessed = true;
            if ( mySeed.isPrincipal() ) {
                mytype = Seed.PEERTYPE_PRINCIPAL;
            }
        } else {
            accessible.IWasAccessed = false;
        }
        accessible.lastUpdated = System.currentTimeMillis();
        Network.amIAccessibleDB.put(targetHash, accessible);

        /*
         * If we were reported as junior we have to check if your port forwarding channel is broken
         * If this is true we try to reconnect the sch channel to the remote server now.
         */
        if ( mytype.equalsIgnoreCase(Seed.PEERTYPE_JUNIOR) ) {
            Network.log.info("yacyClient.hello: Peer '"
                + ((otherPeer == null) ? "unknown" : otherPeer.getName())
                + "' reported us as junior.");
        } else if ( (mytype.equalsIgnoreCase(Seed.PEERTYPE_SENIOR))
            || (mytype.equalsIgnoreCase(Seed.PEERTYPE_PRINCIPAL)) ) {
            if ( Network.log.isFine() ) {
                Network.log.fine("yacyClient.hello: Peer '"
                    + ((otherPeer == null) ? "unknown" : otherPeer.getName())
                    + "' reported us as "
                    + mytype
                    + ", accepted other peer.");
            }
        } else {
            // wrong type report
            if ( Network.log.isFine() ) {
                Network.log.fine("yacyClient.hello: Peer '"
                    + ((otherPeer == null) ? "unknown" : otherPeer.getName())
                    + "' reported us as "
                    + mytype
                    + ", rejecting other peer.");
            }
            return null;
        }
        if ( mySeed.orVirgin().equals(Seed.PEERTYPE_VIRGIN) ) {
            mySeed.put(Seed.PEERTYPE, mytype);
        }

        final String error = mySeed.isProper(true);
        if ( error != null ) {
            Network.log.warn("yacyClient.hello mySeed error - not proper: " + error);
            return null;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time

        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        String seedStr;
        Seed s;
        final int connectedBefore = peerActions.sizeConnected();
        while ( (seedStr = result.get("seed" + i++)) != null ) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
            if ( seedStr.length() > Seed.maxsize ) {
                Network.log.info("hello/client: rejected contacting seed; too large ("+ seedStr.length() + " > " + Seed.maxsize + ")");
            } else {
                try {
                    if ( i == 1 ) {
                        String host = Domains.stripToHostName(targetBaseURL.getHost());
                        InetAddress ia = Domains.dnsResolve(host);
                        if (ia == null) continue;
                        host = ia.getHostAddress(); // the actual address of the target as we had been successful when contacting them is patched here
                        s = Seed.genRemoteSeed(seedStr, false, host);
                    } else {
                        s = Seed.genRemoteSeed(seedStr, false, null);
                    }
                    peerActions.peerArrival(s, (i == 1));
                } catch (final IOException e ) {
                    Network.log.info("hello/client: rejected contacting seed; bad (" + e.getMessage() + ")");
                }
            }
        }
        final int connectedAfter = peerActions.sizeConnected();

        // update event tracker
        EventTracker.update(EventTracker.EClass.PEERPING, new ProfilingGraph.EventPing(mySeed.getName(), targetHash, true, connectedAfter - connectedBefore), false);

        return result;
    }

    public static long[] queryRWICount(final MultiProtocolURL targetBaseURL, final Seed target, int timeout) {
        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts = basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("rwicount"));
            parts.put("env", UTF8.StringBody(""));
            //ConcurrentLog.info("**hello-DEBUG**queryRWICount**", "posting request to " + targetBaseURL);
            final Post post = new Post(targetBaseURL, target.hash, "/yacy/query.html", parts, timeout);

            //ConcurrentLog.info("**hello-DEBUG**queryRWICount**", "received CONTENT from requesting " + targetBaseURL + (post.result == null ? "NULL" : (": length = " + post.result.length)));
            final Map<String, String> result = FileUtils.table(post.result);
            if (result == null || result.isEmpty()) return new long[] {-1, -1};
            //ConcurrentLog.info("**hello-DEBUG**queryRWICount**", "received RESULT from requesting " + targetBaseURL + " : result = " + result.toString());
            final String resp = result.get("response");
            //ConcurrentLog.info("**hello-DEBUG**queryRWICount**", "received RESPONSE from requesting " + targetBaseURL + " : response = " + resp);
            if (resp == null) return new long[] {-1, -1};
            String magic = result.get("magic");
            if (magic == null) magic = "0";
            try {
                return new long[] {Long.parseLong(resp), Long.parseLong(magic)};
            } catch (final NumberFormatException e ) {
                return new long[] {-1, -1};
            }
        } catch (final Exception e ) {
            //ConcurrentLog.info("**hello-DEBUG**queryRWICount**", "received EXCEPTION from requesting " + targetBaseURL + ": " + e.getMessage());
            if (Network.log.isFine()) Network.log.fine("yacyClient.queryRWICount error:" + e.getMessage());
            return new long[] {-1, -1};
        }
    }

    public static RSSFeed queryRemoteCrawlURLs(
        final SeedDB seedDB,
        final Seed target,
        final int maxCount,
        final long maxTime,
        final boolean preferHttps) {
        // returns a list of
        if ( target == null ) {
            return null;
        }
        final int targetCount = Integer.parseInt(target.get(Seed.RCOUNT, "0"));
        if ( targetCount <= 0 ) {
            Network.log.warn("yacyClient.queryRemoteCrawlURLs wrong peer '"
                + target.getName()
                + "' selected: not enough links available");
            return null;
        }
        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        /* a long time-out is needed */
        final Map<String, ContentBody> parts =
            basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
        parts.put("call", UTF8.StringBody("remotecrawl"));
        parts.put("count", UTF8.StringBody(Integer.toString(maxCount)));
        parts.put("time", UTF8.StringBody(Long.toString(maxTime)));
        // final byte[] result = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/urls.xml"), (int) maxTime, target.getHexHash() + ".yacyh", parts);
        RSSReader reader = null;
        try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, (int) maxTime)) {
            for (final String ip: target.getIPs()) {
                MultiProtocolURL targetBaseURL = null;
                try {
                    targetBaseURL = target.getPublicMultiprotocolURL(ip, preferHttps);
                    byte[] result;
                    try {
                        result = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/urls.xml"), target.getHexHash() + ".yacyh", parts, false, true);
                    } catch(final IOException e) {
                        if(targetBaseURL.isHTTPS()) {
                            /* Failed with https : retry with http */
                            targetBaseURL = target.getPublicMultiprotocolURL(ip, false);
                            result = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/urls.xml"), target.getHexHash() + ".yacyh", parts, false, true);
                            if(result != null) {
                                /* Got something with http : mark peer SSL as unavailable on target peer */
                                markSSLUnavailableOnPeer(seedDB, target, ip, "yacyClient.queryRemoteCrawlURLs");
                            }
                        } else {
                            throw e;
                        }
                    }
                    reader = RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
                } catch(MalformedURLException e) {
                    Network.log.warn("yacyClient.queryRemoteCrawlURLs malformed target URL for peer '" + target.getName()
                            + "' on address : " + ip);
                } catch (final IOException e ) {
                    reader = null;
                    Network.log.warn("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (1), reader == null");
                }
                if (reader != null) {
                    break;
                }
                target.put(Seed.RCOUNT, "0");
                seedDB.peerActions.interfaceDeparture(target, ip);
            }
        } catch (IOException e) {
            Network.log.warn(e);
        }

        final RSSFeed feed = reader == null ? null : reader.getFeed();
        if ( feed == null ) {
            // case where the rss reader does not understand the content
            Network.log.warn("yacyClient.queryRemoteCrawlURLs failed asking peer '" + target.getName() + "': probably bad response from remote peer (2)");
            //System.out.println("***DEBUG*** rss input = " + UTF8.String(result));
            target.put(Seed.RCOUNT, "0");
            seedDB.updateConnected(target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
            //Log.logException(e);
            return null;
        }
        // update number of remotely available links in seed
        target.put(Seed.RCOUNT, Integer.toString(Math.max(0, targetCount - feed.size())));
        seedDB.updateConnected(target);
        return feed;
    }

    protected static int primarySearch(
        final SearchEvent event,
        final String wordhashes,
        final String excludehashes,
        final String language,
        final ContentDomain contentdom,
        final boolean strictContentDom,
        final int count,
        final long time,
        final int maxDistance,
        final int partitions,
        final Seed target,
        final SecondarySearchSuperviser secondarySearchSuperviser,
        final Blacklist blacklist) throws InterruptedException {
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
        event.addExpectedRemoteReferences(count);
        SearchResult result = null;
        for (String ip: target.getIPs()) {
            //if (ip.indexOf(':') >= 0) System.out.println("Search target: IPv6: " + ip);
            final String targetBaseURL;
            if (target.clash(event.peers.mySeed().getIPs())) {
                targetBaseURL = "http://localhost:" + event.peers.mySeed().getPort();
            } else {
                targetBaseURL = target.getPublicURL(ip,
                        Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
                                SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED_DEFAULT));
            }
            try {
                result =
                    new SearchResult(
                        event,
                        basicRequestParts(Switchboard.getSwitchboard(), target.hash, crypt.randomSalt()),
                        wordhashes,
                        excludehashes,
                        "",
                        language,
                        contentdom,
                        strictContentDom,
                        count,
                        time,
                        maxDistance,
                        partitions,
                        target.getHexHash() + ".yacyh",
                        targetBaseURL,
                        secondarySearchSuperviser
                        );
                break;
            } catch (final IOException e ) {
                Network.log.info("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + ")");
                if(targetBaseURL.startsWith("https")) {
                    /* First mark https unavailable on this peer before removing any interface */
                    target.setFlagSSLAvailable(false);
                    event.peers.updateConnected(target);
                } else {
                    event.peers.peerActions.interfaceDeparture(target, ip);
                }
                return -1;
            }
        }
        if (result == null) return -1;

        // computation time
        final long totalrequesttime = System.currentTimeMillis() - timestamp;

        try {
            remoteSearchProcess(event, count, totalrequesttime, wordhashes, target, blacklist, result);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return -1;
        }

        // read index abstract
        if ( secondarySearchSuperviser != null ) {
            String wordhash;
            String whacc = "";
            ByteBuffer ci;
            int ac = 0;
            for ( final Map.Entry<byte[], String> abstractEntry : result.indexabstract.entrySet() ) {
                try {
                    ci = new ByteBuffer(abstractEntry.getValue());
                    wordhash = ASCII.String(abstractEntry.getKey());
                } catch (final OutOfMemoryError e ) {
                    ConcurrentLog.logException(e);
                    continue;
                }
                whacc += wordhash;
                secondarySearchSuperviser.addAbstract(
                    wordhash,
                    WordReferenceFactory.decompressIndex(ci, target.hash));
                ac++;

            }
            if ( ac > 0 ) {
                secondarySearchSuperviser.commitAbstract();
                Network.log.info("remote search: peer " + target.getName() + " sent " + ac + " index abstracts for words " + whacc);
            }
        }
        return result.availableCount;
    }

    protected static int secondarySearch(
        final SearchEvent event,
        final String wordhashes,
        final String urlhashes,
        final ContentDomain contentdom,
        final boolean strictContentDom,
        final int count,
        final long time,
        final int maxDistance,
        final int partitions,
        final Seed target,
        final Blacklist blacklist) throws InterruptedException {

        final long timestamp = System.currentTimeMillis();
        event.addExpectedRemoteReferences(count);
        SearchResult result = null;
        for (String ip: target.getIPs()) {
            final String targetBaseURL = target.getPublicURL(ip,
                    Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
                            SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED_DEFAULT));
            try {
                result =
                    new SearchResult(
                        event,
                        basicRequestParts(Switchboard.getSwitchboard(), target.hash, crypt.randomSalt()),
                        wordhashes,
                        "",
                        urlhashes,
                        "",
                        contentdom,
                        strictContentDom,
                        count,
                        time,
                        maxDistance,
                        partitions,
                        target.getHexHash() + ".yacyh",
                        targetBaseURL,
                        null
                        );
                break;
            } catch (final IOException e ) {
                Network.log.info("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + ")");
                if(targetBaseURL.startsWith("https")) {
                    /* First mark https unavailable on this peer before removing any interface */
                    target.setFlagSSLAvailable(false);
                    event.peers.updateConnected(target);
                } else {
                    event.peers.peerActions.interfaceDeparture(target, ip);
                }
                return -1;
            }
        }
        if (result == null) return -1;

        // computation time
        final long totalrequesttime = System.currentTimeMillis() - timestamp;

        try {
            remoteSearchProcess(event, count, totalrequesttime, wordhashes, target, blacklist, result);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return -1;
        }
        return result.availableCount;
    }

    private static void remoteSearchProcess(
        final SearchEvent event,
        final int count,
        final long time,
        final String wordhashes,
        final Seed target,
        final Blacklist blacklist,
        final SearchResult result
        ) throws SpaceExceededException, InterruptedException {

        // create containers
        final int words = wordhashes.length() / Word.commonHashLength;
        assert words > 0 : "wordhashes = " + wordhashes;
        final List<ReferenceContainer<WordReference>> container = new ArrayList<ReferenceContainer<WordReference>>(words);
        for ( int i = 0; i < words; i++ ) {
            container.add(ReferenceContainer.emptyContainer(
                        Segment.wordReferenceFactory,
                        ASCII.getBytes(wordhashes.substring(i * Word.commonHashLength, (i + 1) * Word.commonHashLength)),
                        count)); // throws SpaceExceededException
        }

        // insert results to containers
        int term = count;
        Map<String, LinkedHashSet<String>> snip;
        if (event.addResultsToLocalIndex) {
            snip = null;
        } else {
            snip = new HashMap<String, LinkedHashSet<String>>(); // needed to display nodestack results
        }
        List<URIMetadataNode> storeDocs = new ArrayList<URIMetadataNode>(result.links.size());
        for ( final URIMetadataNode urlEntry : result.links ) {
            if ( term-- <= 0 ) {
                break; // do not process more that requested (in case that evil peers fill us up with rubbish)
            }
            // get one single search result
            if ( urlEntry == null ) {
                continue;
            }
            assert (urlEntry.hash().length == 12) : "urlEntry.hash() = " + ASCII.String(urlEntry.hash());
            if ( urlEntry.hash().length != 12 ) {
                continue; // bad url hash
            }
            if ( blacklist.isListed(BlacklistType.SEARCH, urlEntry.url()) ) {
                if ( Network.log.isInfo() ) {
                    Network.log.info("remote search: filtered blacklisted url " + urlEntry.url().toNormalform(true) + " from peer " + target.getName());
                }
                continue; // block with backlist
            }

            final String urlRejectReason =
                Switchboard.getSwitchboard().crawlStacker.urlInAcceptedDomain(urlEntry.url());
            if ( urlRejectReason != null ) {
                if ( Network.log.isInfo() ) {
                    Network.log.info("remote search: rejected url '" + urlEntry.url().toNormalform(true) + "' (" + urlRejectReason + ") from peer " + target.getName());
                }
                continue; // reject url outside of our domain
            }

            // save the url entry
            final Reference entry = urlEntry.word();
            if ( entry == null ) {
                if ( Network.log.isWarn() ) {
                    Network.log.warn("remote search: no word attached from peer " + target.getName() + ", version " + target.getVersion());
                }
                continue; // no word attached
            }

            // the search-result-url transports all the attributes of word indexes
            if ( !Base64Order.enhancedCoder.equal(entry.urlhash(), urlEntry.hash()) ) {
                Network.log.info("remote search: url-hash " + ASCII.String(urlEntry.hash()) + " does not belong to word-attached-hash " + ASCII.String(entry.urlhash()) + "; url = " + urlEntry.url().toNormalform(true) + " from peer " + target.getName());
                continue; // spammed
            }

            // passed all checks, store url
            storeDocs.add(urlEntry);
            ResultURLs.stack(
                ASCII.String(urlEntry.url().hash()),
                urlEntry.url().getHost(),
                event.peers.mySeed().hash.getBytes(),
                UTF8.getBytes(target.hash),
                EventOrigin.QUERIES);

            if ( urlEntry.snippet() != null
                && urlEntry.snippet().length() > 0
                && !urlEntry.snippet().equals("null") ) {
                // we don't store the snippets along the url entry,
                // because they are search-specific.
                // instead, they are placed in a snipped-search cache.
                // System.out.println("--- RECEIVED SNIPPET '" + urlEntry.snippet() + "'");
                TextSnippet.snippetsCache.put(wordhashes, ASCII.String(urlEntry.hash()), urlEntry.snippet());
                // add snippet for snippethandling for nodestack entries (used if not stored to index)
                if (!event.addResultsToLocalIndex) {
                    // TODO: must have a snippet even to get the snippetcache entry back when adding to nodestack
                    LinkedHashSet<String> sniptxt = new LinkedHashSet<String>();
                    sniptxt.add(urlEntry.snippet());
                    snip.put(ASCII.String(urlEntry.hash()), sniptxt);
                }
            }

            // add the url entry to the word indexes
            for ( final ReferenceContainer<WordReference> c : container ) {
                try {
                    c.add(entry);
                } catch (final SpaceExceededException e ) {
                    ConcurrentLog.logException(e);
                    break;
                }
            }
        }

        // store remote result to local result container
        // insert one container into the search result buffer
        // one is enough, only the references are used, not the word
        if (event.addResultsToLocalIndex) {
            /*
             * Current thread might be interrupted by SearchEvent.cleanup()
             */
            if (Thread.interrupted()) {
                throw new InterruptedException("solrQuery interrupted");
            }
            WriteMetadataNodeToLocalIndexThread writerToLocalIndex = new WriteMetadataNodeToLocalIndexThread(event.query.getSegment(), storeDocs);
            writerToLocalIndex.start();
            try {
                writerToLocalIndex.join();
            } catch(InterruptedException e) {
                /*
                 * Current thread interruption might happen while waiting
                 * for writeToLocalIndexThread.
                 */
                writerToLocalIndex.stopWriting();
                throw new InterruptedException("remoteProcess stopped!");
            }
            event.addRWIs(container.get(0), false, target.getName() + "/" + target.hash, result.totalCount, time);
        } else {
            // feed results as nodes (SolrQuery results) which carry metadata,
            // to prevent a call to getMetaData for RWI results, which would fail (if no metadata in index and no display of these results)
            event.addNodes(storeDocs, null, snip, false, target.getName() + "/" + target.hash, count, true);
        }
        event.addFinalize();
        event.addExpectedRemoteReferences(-count);

        // insert the containers to the index
        for ( final ReferenceContainer<WordReference> c : container ) {
            try {
                event.query.getSegment().storeRWI(c);
            } catch (final Exception e ) {
                ConcurrentLog.logException(e);
            }
        }

        // integrate remote top-words/topics
        if ( result.references != null && result.references.length > 0 ) {
            Network.log.info("remote search: peer " + target.getName() + " sent " + result.references.length + " topics");
            // add references twice, so they can be counted (must have at least 2 entries)
            synchronized (event) {
                event.addTopic(result.references);
                event.addTopic(result.references);
            }
        }
        Network.log.info("remote search: peer " + target.getName() + " sent " + container.get(0).size() + "/" + result.totalCount + " references");
    }

    /**
     * This thread is used to write a collection of URIMetadataNode documents to a segment allowing to be safely stopped.
     * Indeed, if one interrupt a thread while commiting to Solr index, the index is closed and will be no more writable
     * (later calls would throw a org.apache.lucene.store.AlreadyClosedException) because Solr IndexWriter uses an InterruptibleChanel.
     * This thread allow to safely stop writing operation using an AtomicBoolean.
     * @author luc
     *
     */
    private static class WriteMetadataNodeToLocalIndexThread extends Thread {

        private AtomicBoolean stop = new AtomicBoolean(false);

        private Segment segment;

        private Collection<URIMetadataNode> storeDocs;

        /**
         * Parameters must be not null.
         * @param segment solr segment to write
         * @param storeDocs solr documents collection to put to segment
         */
        public WriteMetadataNodeToLocalIndexThread(Segment segment, Collection<URIMetadataNode> storeDocs) {
            super("WriteMetadataNodeToLocalIndexThread");
            this.segment = segment;
            this.storeDocs = storeDocs;
        }

        /**
         * Use this to stop writing operation. This thread will not stop immediately as Solr might be writing something.
         */
        public void stopWriting() {
            this.stop.set(true);
        }

        @Override
        public void run() {
            for (URIMetadataNode entry : this.storeDocs) {
                if(this.stop.get()) {
                    Network.log.info("Writing documents collection to Solr segment was stopped.");
                    return;
                }
                try {
                    this.segment.setFirstSeenTime(entry.hash(), Math.min(entry.moddate().getTime(), System.currentTimeMillis())); // Set only firstsee, but not load time - because we did not load the document
                    this.segment.fulltext().putMetadata(entry); // it will be checked inside the putMetadata that poor metadata does not overwrite rich metadata
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
    }

    private static class SearchResult {
        public int availableCount; // number of returned LURL's for this search
        public int totalCount; //
        public Map<byte[], Integer> indexcount; //
        //public long searchtime; // time that the peer actually spent to create the result
        public String[] references; // search hints, the top-words
        public List<URIMetadataNode> links; // LURLs of search
        public Map<byte[], String> indexabstract; // index abstracts, a collection of url-hashes per word

        public SearchResult(
            final SearchEvent event,
            final Map<String, ContentBody> parts,
            final String wordhashes,
            final String excludehashes,
            final String urlhashes,
            final String language,
            final ContentDomain contentdom,
            final boolean strictContentDom,
            final int count,
            final long time,
            final int maxDistance,
            final int partitions,
            final String hostname,
            final String targetBaseURL,
            final SecondarySearchSuperviser secondarySearchSuperviser
            ) throws IOException {
            // send a search request to peer with remote Hash

            //if (hostaddress.equals(mySeed.getClusterAddress())) hostaddress = "127.0.0.1:" + mySeed.getPort(); // for debugging

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
            final ContentBody keyBody = parts.get("key");
            if (keyBody != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(20);
                keyBody.writeTo(baos);
                key = UTF8.String(baos.toByteArray());
                baos.close();
                baos = null;
            }

            parts.put("myseed", UTF8.StringBody((event.peers.mySeed() == null) ? "" : event.peers.mySeed().genSeedStr(key)));
            parts.put("count", UTF8.StringBody(Integer.toString(Math.max(10, count))));
            parts.put("time", UTF8.StringBody(Long.toString(Math.max(3000, time))));
            parts.put("partitions", UTF8.StringBody(Integer.toString(partitions)));
            parts.put("query", UTF8.StringBody(wordhashes));
            parts.put("exclude", UTF8.StringBody(excludehashes));
            parts.put("urls", UTF8.StringBody(urlhashes));
            parts.put("prefer", UTF8.StringBody(event.query.prefer.pattern()));
            parts.put("filter", UTF8.StringBody(event.query.urlMaskString));
            parts.put("modifier", UTF8.StringBody(event.query.modifier.toString()));
            parts.put("language", UTF8.StringBody(language));
            parts.put("sitehash", UTF8.StringBody(event.query.modifier.sitehash));
            //parts.put("sitehost", UTF8.StringBody(event.query.modifier.sitehost));
            parts.put("author", UTF8.StringBody(event.query.modifier.author));
            parts.put("contentdom", UTF8.StringBody(contentdom == null ? ContentDomain.ALL.toString() : contentdom.toString()));
            if(strictContentDom) {
                parts.put("strictContentDom", UTF8.StringBody("true"));
            }
            parts.put("maxdist", UTF8.StringBody(Integer.toString(maxDistance)));
            parts.put("profile", UTF8.StringBody(crypt.simpleEncode(event.query.ranking.toExternalString())));
            parts.put("constraint", UTF8.StringBody((event.query.constraint == null) ? "" : event.query.constraint.exportB64()));
            if ( secondarySearchSuperviser != null ) {
                parts.put("abstracts", UTF8.StringBody("auto"));
                // resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + hostaddress + "/yacy/search.html"), 60000, hostname, parts));
                //resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.crawlerUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/search.html"), 60000, target.getHexHash() + ".yacyh", parts));
            }

            try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 8000)) {
                byte[] a = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL + "/yacy/search.html"), hostname, parts, false, true);
                if (a != null && a.length > 200000) {
                    // there is something wrong. This is too large, maybe a hack on the other side?
                    a = null;
                }
                resultMap = FileUtils.table(a);
            }

            // evaluate request result
            if ( resultMap == null || resultMap.isEmpty() ) {
                throw new IOException("resultMap is NULL");
            }
            /*
            try {
                this.searchtime = Integer.parseInt(resultMap.get("searchtime"));
            } catch (final NumberFormatException e ) {
                throw new IOException("wrong output format for searchtime: "
                    + e.getMessage()
                    + ", map = "
                    + resultMap.toString());
            }
            */
            try {
                this.totalCount = Integer.parseInt(resultMap.get("joincount")); // the complete number of hits at remote site; rwi+solr (via: theSearch.getResultCount())
            } catch (final NumberFormatException e ) {
                throw new IOException("wrong output format for joincount: " + e.getMessage());
            }
            try {
                this.availableCount = Integer.parseInt(resultMap.get("count")); // the number of hits that are returned in the result list
            } catch (final NumberFormatException e ) {
                throw new IOException("wrong output format for count: " + e.getMessage());
            }
            // scan the result map for entries with special prefix
            this.indexcount = new TreeMap<byte[], Integer>(Base64Order.enhancedCoder);
            this.indexabstract = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
            for ( final Map.Entry<String, String> entry : resultMap.entrySet() ) {
                if ( entry.getKey().startsWith("indexcount.") ) {
                    this.indexcount.put(
                        UTF8.getBytes(entry.getKey().substring(11)),
                        Integer.parseInt(entry.getValue()));
                }
                if ( entry.getKey().startsWith("indexabstract.") ) {
                    this.indexabstract.put(UTF8.getBytes(entry.getKey().substring(14)), entry.getValue());
                }
            }
            this.references = CommonPattern.COMMA.split(resultMap.get("references"));
            this.links = new ArrayList<URIMetadataNode>(this.availableCount);
            for ( int n = 0; n < this.availableCount; n++ ) {
                // get one single search result
                final String resultLine = resultMap.get("resource" + n);
                if ( resultLine == null ) {
                    continue;
                }
                final URIMetadataNode urlEntry = URIMetadataNode.importEntry(resultLine, "dht");
                if ( urlEntry == null ) {
                    continue;
                }
                this.links.add(urlEntry);
            }
        }
    }

    /**
     * A task dedicated to requesting a Solr instance
     */
    protected static class SolrRequestTask extends Thread {

        /** Maximum length of detailed log error message */
        private final static int MAX_ERROR_MESSAGE_LENGTH = 500;

        /** Logger */
        private final static ConcurrentLog log = new ConcurrentLog(SolrRequestTask.class.getSimpleName());

        /** The solr to request */
        private RemoteInstance instance;

        /** Connector to the Solr instance */
        private SolrConnector solrConnector;

        /** The solr query to run */
        private final SolrQuery solrQuery;

        /** The instance base URL */
        private final String targetBaseURL;

        /** The target seed information */
        private final Seed target;

        /** Set to true when the target is this local peer */
        private final boolean mySeed;

        /** The request timeout in milliseconds */
        private final int timeout;

        /** The query response array to fill */
        private final QueryResponse[] rsp;

        /** The result documents list to fill */
        private final SolrDocumentList[] docList;

        /** Indicates wether this task has been closed */
        private volatile boolean closed;

        /**
         * Constructor. All parameters are required to not be null.
         * @param solrQuery the Solr query to run
         * @param targetBaseURL the instance base URL : http(s) + host name or IP + the eventual port
         * @param target the remote target seed information
         * @param timeout the request timeout in milliseconds
         */
        protected SolrRequestTask(final SolrQuery solrQuery, final String targetBaseURL, final Seed target,
                final boolean mySeed, final int timeout, final QueryResponse[] rsp, final SolrDocumentList[] docList) {
            super("Protocol.solrQuery(" + solrQuery.getQuery() + " to " + target.hash + ")");
            this.solrQuery = solrQuery;
            this.targetBaseURL = targetBaseURL;
            this.target = target;
            this.mySeed = mySeed;
            this.timeout = timeout;
            this.rsp = rsp;
            this.docList = docList;
            this.closed = false;
        }

        /**
         * Logs the exception detailed message if any, at fine level because errors on remote solr queries to other peers occurs quite frequently.
         * @param messageBegin beginning of the log message
         * @param ex exception to log
         */
        private void logError(final String messageBegin, final Exception ex) {
            if(log.isFine()) {
                String message = ex.getMessage();
                if(message == null) {
                    message = "no details";
                } else if(message.length() > MAX_ERROR_MESSAGE_LENGTH){
                    /* Strip too large details to avoid polluting this log with complete remote stack traces */
                    message = message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "...";
                }
                log.fine(messageBegin + " at " + this.targetBaseURL + " : " + message);
            }
        }

        @Override
        public void run() {
            try {
                boolean trustSelfSignedOnAuthenticatedServer = SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT;
                if (Switchboard.getSwitchboard() != null) {
                    trustSelfSignedOnAuthenticatedServer = Switchboard.getSwitchboard().getConfigBool(
                            SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED,
                            SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_AUTHENTICATED_ALLOW_SELF_SIGNED_DEFAULT);
                }
                /* Add a limit to the maximum acceptable size of the remote peer Solr response. This can help prevent out of memory errors when :
                 * - this peer is overloaded
                 * - the remote peer has indexed documents with excessively large metadata (too large at least to fit within this peer resources)
                 * - the remote peer is a malicious one and would like to trigger a deny of service */
                final long maxBytesPerResponse = MemoryControl.available() / 4;

                this.instance = new RemoteInstance(this.targetBaseURL, null, "solr", this.timeout, trustSelfSignedOnAuthenticatedServer, maxBytesPerResponse, false); // this is a 'patch configuration' which considers 'solr' as default collection
                try {
                    boolean useBinaryResponseWriter = SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT;
                    if (Switchboard.getSwitchboard() != null) {
                        useBinaryResponseWriter = Switchboard.getSwitchboard().getConfigBool(
                                SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED,
                                SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT);
                    }
                    this.solrConnector = new RemoteSolrConnector(this.instance, useBinaryResponseWriter && (this.mySeed ? true : this.target.getVersion() >= 1.63), "solr");
                    if (!this.solrConnector.isClosed() && !this.closed) {
                        try {
                            this.rsp[0] = this.solrConnector.getResponseByParams(this.solrQuery);
                            this.docList[0] = this.rsp[0].getResults();
                            if(log.isFine() && this.rsp[0] != null && this.rsp[0].getElapsedTime() >= 0) {
                                log.fine("Got a response from solr instance at " + this.targetBaseURL + " in " + this.rsp[0].getElapsedTime() + "ms");
                            }
                        } catch (Exception e) {
                            logError("Could not get result from solr", e);
                        }
                    }
                } catch (Exception ee) {
                    logError("Could not connect to solr instance", ee);
                }
            } catch (Exception eee) {
                logError("Could not set up remote solr instance", eee);
            } finally {
                this.close();
            }
        }

        /**
         * Stop the eventually running Solr request, and close the eventually opened connector and instance to the target Solr.
         */
        protected synchronized void close() {
            if (!this.closed) {
                try {
                    if (this.solrConnector != null) {
                        this.solrConnector.close();
                    }
                } catch (Exception e) {
                    logError("Could not close solr connector", e);
                } finally {
                    try {
                        if (this.instance != null) {
                            this.instance.close();
                        }
                    } catch (Exception e) {
                        logError("Could not close solr instance", e);
                    } finally {
                        this.closed = true;
                    }
                }
            }
        }

    }

    /**
     * Execute solr query against specified target.
     * @param event search event ot feed with results
     * @param solrQuery solr query
     * @param offset pagination start indice
     * @param count expected maximum results
     * @param target target peer to query. May be null : in that case, local peer is queried.
     * @param partitions
     * @param blacklist url list to exclude from results
     * @param useSolrFacets when true, use Solr computed facets when possible to update the event navigators counters
     * @param incrementNavigators when true, increment event navigators either with facet counts or with individual results
     * @return the size of results list
     * @throws InterruptedException when interrupt status on calling thread is detected while processing
     */
    protected static int solrQuery(
            final SearchEvent event,
            final SolrQuery solrQuery,
            final int offset,
            final int count,
            final Seed target,
            final int partitions,
            final Blacklist blacklist,
            final boolean useSolrFacets,
            final boolean incrementNavigators) throws InterruptedException {

        //try {System.out.println("*** debug-query *** " + URLDecoder.decode(solrQuery.toString(), "UTF-8"));} catch (UnsupportedEncodingException e) {}

        if (event.query.getQueryGoal().getQueryString(false) == null || event.query.getQueryGoal().getQueryString(false).length() == 0) {
            return -1; // we cannot query solr only with word hashes, there is no clear text string
        }
        event.addExpectedRemoteReferences(count);
        if (partitions > 0) solrQuery.set("partitions", partitions);
        solrQuery.setStart(offset);
        solrQuery.setRows(count);

        boolean localsearch = target == null || target.equals(event.peers.mySeed());
        Map<String, ReversibleScoreMap<String>> facets = new HashMap<String, ReversibleScoreMap<String>>(event.query.facetfields.size());
        Map<String, LinkedHashSet<String>> snippets = new HashMap<String, LinkedHashSet<String>>(); // this will be a list of urlhash-snippet entries
        final QueryResponse[] rsp = new QueryResponse[]{null};
        final SolrDocumentList[] docList = new SolrDocumentList[]{null};
        {// encapsulate expensive solr QueryResponse object
            if (localsearch && !Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.DEBUG_SEARCH_REMOTE_SOLR_TESTLOCAL, false)) {
                // search the local index
                try {
                    SolrConnector sc = event.getQuery().getSegment().fulltext().getDefaultConnector();
                    if (!sc.isClosed()) {
                        rsp[0] = sc.getResponseByParams(solrQuery);
                        docList[0] = rsp[0].getResults();
                    }
                } catch (final Throwable e) {
                    Network.log.info("SEARCH failed (solr), localpeer (" + e.getMessage() + ")", e);
                    return -1;
                }
            } else {
                String targetBaseURL = null;
                try {
                    final boolean myseed = target == event.peers.mySeed();
                    if(myseed) {
                        targetBaseURL = "http://localhost:" + target.getPort();
                    } else {
                        final Set<String> ips = target.getIPs();
                        if(ips.isEmpty()) {
                            /* This should not happen : seeds db maintains only seeds with at least one IP */
                            Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + " has no known IP address");
                            target.setFlagSolrAvailable(false);
                            return -1;
                        }
                        final String ip = ips.iterator().next();

                        targetBaseURL = target.getPublicURL(ip,
                                Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED,
                                        SwitchboardConstants.REMOTESEARCH_HTTPS_PREFERRED_DEFAULT));
                    }
                    if (!myseed && !target.getFlagSolrAvailable()) { // skip if peer.dna has flag that last try resulted in error
                        Network.log.info("SEARCH skip (solr), remote Solr interface not accessible, peer=" + target.getName());
                        return -1;
                    }
                    final int solrtimeout = Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 6000);
                    SolrRequestTask remoteRequest = new SolrRequestTask(solrQuery, targetBaseURL, target, myseed, solrtimeout, rsp, docList);
                    remoteRequest.start();
                    remoteRequest.join(solrtimeout); // just wait until timeout appears
                    if (remoteRequest.isAlive()) {
                        /* Try to free the request thread resources properly */
                        remoteRequest.close();
                        if(remoteRequest.isAlive()) {
                            /* Thread still running : try also with interrupt*/
                            remoteRequest.interrupt();
                        }
                        Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + "/" + targetBaseURL + " does not answer (time-out)");
                        target.setFlagSolrAvailable(false || myseed);
                        return -1; // give up, leave remoteRequest abandoned.
                    }

                    if (rsp[0] == null || docList[0] == null) {
                        Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + "/" + targetBaseURL + " returned null");
                        if(!myseed) {
                            if(targetBaseURL.startsWith("https")) {
                                /* First mark https unavailable on this peer before removing anything else */
                                target.setFlagSSLAvailable(false);
                                event.peers.updateConnected(target);
                            } else {
                                target.setFlagSolrAvailable(false);
                            }
                        }
                        return -1;
                    }
                } catch(InterruptedException e) {
                    /* Current thread might be interrupted by SearchEvent.cleanup() :
                     * we must not in that case mark the target as not available but rather transmit the exception to the caller (likely RemoteSearch.solrRemoteSearch) */
                    throw e;
                } catch (final Throwable e) {
                    if(Network.log.isInfo()) {
                        Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + (targetBaseURL != null ? "/" + targetBaseURL : "") + " (" + e.getMessage() + ")");
                    }
                    target.setFlagSolrAvailable(false || localsearch);
                    return -1;
                }
            }

            // evaluate facets
            if(useSolrFacets) {
                for (String field: event.query.facetfields.keySet()) {
                    FacetField facet = rsp[0].getFacetField(field);
                    ReversibleScoreMap<String> result = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                    List<Count> values = facet == null ? null : facet.getValues();
                    if (values == null) {
                        continue;
                    }
                    for (Count ff: values) {
                        int c = (int) ff.getCount();
                        if (c == 0) {
                            continue;
                        }
                        if (ff.getName().length() == 0) {
                            continue; // facet entry without text is not useful
                        }
                        result.set(ff.getName(), c);
                    }
                    if (result.size() > 0) {
                        facets.put(field, result);
                    }
                }
            }

            // evaluate snippets
            final Map<String, Map<String, List<String>>> rawsnippets = rsp[0].getHighlighting(); // a map from the urlhash to a map with key=field and value = list of snippets
            if (rawsnippets != null) {
                nextsnippet: for (final Map.Entry<String, Map<String, List<String>>> re: rawsnippets.entrySet()) {
                    final Map<String, List<String>> rs = re.getValue();
                    for (final String field: solrQuery.getHighlightFields()) {
                        if (rs.containsKey(field)) {
                            final List<String> s = rs.get(field);
                            if (s.size() > 0) {
                                final LinkedHashSet<String> ls = new LinkedHashSet<String>();
                                ls.addAll(s);
                                snippets.put(re.getKey(), ls);
                                continue nextsnippet;
                            }
                        }
                    }
                    // no snippet found :( --we don't assign a value here by default; that can be done as an evaluation outside this method
                }
            }
            rsp[0] = null;
        }

        // evaluate result
        final int numFound = (int) docList[0].getNumFound();
        if (docList == null || docList[0].isEmpty()) {
            Network.log.info("SEARCH (solr), returned 0 out of 0 documents from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())) + " query = " + solrQuery.toString()) ;
            if(localsearch && offset > 0) {
                /* No documents were returned from Solr because the offset is too high, but we have to keep the total number of matching documents for accurate pagination.
                 * This case can notably happen on latest results pages, when mixing results from local RWI and local Solr ("Stealth Mode") */
                event.local_solr_stored.set(numFound);
            }
            return 0;
        }

        List<URIMetadataNode> resultContainer = new ArrayList<URIMetadataNode>();
        Network.log.info("SEARCH (solr), returned " + docList[0].size() + " out of " + docList[0].getNumFound() + " documents and " + facets.size() + " facets " + facets.keySet().toString() + " from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())));
        int term = count;
        Collection<SolrInputDocument> docs;
        if (event.addResultsToLocalIndex) { // only needed to store remote results
            docs = new ArrayList<SolrInputDocument>(docList[0].size());
        } else docs = null;
        for (final SolrDocument tmpdoc: docList[0]) {
            //System.out.println("***DEBUG*** " + ((String) doc.getFieldValue("sku")));
            if ( term-- <= 0 ) {
                break; // do not process more that requested (in case that evil peers fill us up with rubbish)
            }
            // get one single search result
            if ( tmpdoc == null ) {
                continue;
            }
            URIMetadataNode urlEntry;
            try {
                urlEntry = new URIMetadataNode(tmpdoc);
            } catch (MalformedURLException ex) {
                continue;
            }

            if ( blacklist.isListed(BlacklistType.SEARCH, urlEntry.url()) ) {
                if ( Network.log.isInfo() ) {
                    if (localsearch) {
                        Network.log.info("local search (solr): filtered blacklisted url " + urlEntry.url().toNormalform(true));
                    } else {
                        Network.log.info("remote search (solr): filtered blacklisted url " + urlEntry.url().toNormalform(true) + " from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())));
                    }
                }
                continue; // block with blacklist
            }

            final String urlRejectReason = Switchboard.getSwitchboard().crawlStacker.urlInAcceptedDomain(urlEntry.url());
            if ( urlRejectReason != null ) {
                if ( Network.log.isInfo() ) {
                    if (localsearch) {
                        Network.log.info("local search (solr): rejected url '" + urlEntry.url().toNormalform(true) + "' (" + urlRejectReason + ")");
                    } else {
                        Network.log.info("remote search (solr): rejected url '" + urlEntry.url().toNormalform(true) + "' (" + urlRejectReason + ") from peer " + target.getName());
                    }
                }
                continue; // reject url outside of our domain
            }

            // passed all checks, store url
            if (!localsearch) {

                // put the remote documents to the local index. We must convert the solr document to a solr input document:
                if (event.addResultsToLocalIndex) {
                    /* Check document size, only if a limit is set on remote documents size allowed to be stored to local index */
                    if (checkDocumentSize(tmpdoc, event.getRemoteDocStoredMaxSize() * 1024)) {
                        final SolrInputDocument sid = event.query.getSegment().fulltext().getDefaultConfiguration().toSolrInputDocument(tmpdoc);

                        // the input document stays untouched because it contains top-level cloned objects
                        docs.add(sid);
                        // will be stored to index, and is a full solr document, can be added to firstseen
                        event.query.getSegment().setFirstSeenTime(urlEntry.hash(), Math.min(urlEntry.moddate().getTime(), System.currentTimeMillis()));
                    } else {
                        Network.log.info("Document size greater than " + event.getRemoteDocStoredMaxSize() + " kbytes, excludes it from being stored to local index. Url : " + urlEntry.urlstring());
                    }
                }

                // after this conversion we can remove the largest and not used field text_t and synonyms_sxt from the document
                // because that goes into a search cache and would take a lot of memory in the search cache
                //doc.removeFields(CollectionSchema.text_t.getSolrFieldName());
                tmpdoc.removeFields(CollectionSchema.synonyms_sxt.getSolrFieldName());

                ResultURLs.stack(
                        ASCII.String(urlEntry.url().hash()),
                        urlEntry.url().getHost(),
                        event.peers.mySeed().hash.getBytes(),
                        UTF8.getBytes(target.hash),
                        EventOrigin.QUERIES);
            }

            // add the url entry to the checked results
            resultContainer.add(urlEntry);
        }
        docList[0].clear();
        docList[0] = null;
        if (localsearch) {
            event.addNodes(resultContainer, facets, snippets, true, "localpeer", numFound, incrementNavigators);
            event.addFinalize();
            event.addExpectedRemoteReferences(-count);
            Network.log.info("local search (solr): localpeer sent " + resultContainer.size() + "/" + numFound + " references");
        } else {
            if (event.addResultsToLocalIndex) {
                /*
         * Current thread might be interrupted by SearchEvent.cleanup()
                 */
                if (Thread.interrupted()) {
                    throw new InterruptedException("solrQuery interrupted");
                }
                WriteToLocalIndexThread writeToLocalIndexThread = new WriteToLocalIndexThread(event.query.getSegment(),
                        docs); // will clear docs on return
                writeToLocalIndexThread.start();
            }
            event.addNodes(resultContainer, facets, snippets, false, target.getName() + "/" + target.hash, numFound, incrementNavigators);
            event.addFinalize();
            event.addExpectedRemoteReferences(-count);
            Network.log.info("remote search (solr): peer " + target.getName() + " sent " + (resultContainer.size()) + "/" + numFound + " references");
        }
        return resultContainer.size();
    }

    /**
     * This thread is used to write a collection of Solr documents to a segment allowing to be safely stopped.
     * Indeed, if one interrupt a thread while commiting to Solr index, the index is closed and will be no more writable
     * (later calls would throw a org.apache.lucene.store.AlreadyClosedException) because Solr IndexWriter uses an InterruptibleChanel.
     * This thead allow to safely stop writing operation using an AtomicBoolean.
     * @author luc
     *
     */
    private static class WriteToLocalIndexThread extends Thread {

        private AtomicBoolean stop = new AtomicBoolean(false);

        private Segment segment;

        private Collection<SolrInputDocument> docs;

        /**
         * Parameters must be not null.
         * After writing the collection is cleared
         * @param segment solr segment to write
         * @param docs solr documents collection to put to segment
         */
        public WriteToLocalIndexThread(Segment segment, Collection<SolrInputDocument> docs) {
            super("WriteToLocalIndexThread");
            this.segment = segment;
            this.docs = docs;
        }

//        /**
//         * Use this to stop writing operation. This thread will not stop immediately as Solr might be writing something.
//         */
//        public void stopWriting() {
//            this.stop.set(true);
//        }

        @Override
        public void run() {
            for (SolrInputDocument doc : this.docs) {
                if (this.stop.get()) {
                    this.docs.clear();
                    Network.log.info("Writing documents collection to Solr segment was stopped.");
                    return;
                }
                this.segment.putDocument(doc);
            }
            this.docs.clear();
        }
    }

    /**
     * Only when maxSize is greater than zero, check that doc size is lower. To
     * process in a reasonable amount of time, document size is not evaluated
     * summing all fields sizes, but only against text_t field which is quite representative and might weigh
     * some MB.
     *
     * @param doc
     *            document to verify. Must not be null.
     * @param maxSize
     *            maximum allowed size in bytes
     * @return true when document evaluated size is lower or equal than maxSize, or when
     *         maxSize is lower or equal than zero.
     */
    protected static boolean checkDocumentSize(SolrDocument doc, long maxSize) {
        if (maxSize > 0) {
            /* All text field is often the largest */
            Object value = doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName());
            if(value instanceof String) {
                /* Each char uses 2 bytes */
                if(((String)value).length() > (maxSize /2)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Post a request asking for allowed message size and attachment size to the
     * target peer on the selected target ip. All parameters must not be null.
     *
     * @param targetBaseURL
     *            the public base URL of the target peer on one of its reported IP
     *            addresses in {@link Seed#getIPs()}
     * @param target
     *            the target peer
     * @param sb
     *            the switchboard instance
     * @return the result of the request
     * @throws IOException
     *             when the peer doesn't answer on this IP or any other error
     *             occurred
     */
    public static Map<String, String> permissionMessage(final MultiProtocolURL targetBaseURL, final Seed target,
            final Switchboard sb) throws IOException {
        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        final Map<String, ContentBody> parts = basicRequestParts(sb, target.hash, salt);
        parts.put("process", UTF8.StringBody("permission"));
        final Post post = new Post(targetBaseURL, target.hash, "/yacy/message.html", parts, 6000);

        final Map<String, String> result = FileUtils.table(post.result);
        return result;
    }

    public static Map<String, String> crawlReceipt(
        final Switchboard sb,
        final Seed mySeed,
        final Seed target,
        final String process,
        final String result,
        final String reason,
        final URIMetadataNode entry,
        final String wordhashes) {
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

        final boolean preferHttps = sb.getConfigBool(SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED,
                SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED_DEFAULT);

        for (final String ip : target.getIPs()) {
            // send request
            try {
                // prepare request
                final Map<String, ContentBody> parts = basicRequestParts(sb, target.hash, salt);
                parts.put("process", UTF8.StringBody(process));
                parts.put("urlhash", UTF8.StringBody(((entry == null) ? "" : ASCII.String(entry.hash()))));
                parts.put("result", UTF8.StringBody(result));
                parts.put("reason", UTF8.StringBody(reason));
                parts.put("wordh", UTF8.StringBody(wordhashes));
                final String lurlstr;
                if (entry == null) {
                    lurlstr = "";
                } else {
                    final ArrayList<String> ldesc = entry.getDescription();
                    if (ldesc.isEmpty()) {
                        lurlstr = entry.toString();
                    } else { // add document abstract/description as snippet (remotely stored in description_txt)
                        lurlstr = entry.toString(ldesc.get(0));
                    }
                }
                parts.put("lurlEntry", UTF8.StringBody(crypt.simpleEncode(lurlstr, salt)));
                // send request
                byte[] content;
                try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 10000)) {
                    MultiProtocolURL targetBaseURL = target.getPublicMultiprotocolURL(ip, preferHttps);
                    try {
                        content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/crawlReceipt.html"),
                                target.getHexHash() + ".yacyh", parts, false, true);
                    } catch(final IOException e) {
                        if(targetBaseURL.isHTTPS()) {
                            /* Failed using https : retry with http */
                            targetBaseURL = target.getPublicMultiprotocolURL(ip, false);
                            content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/crawlReceipt.html"),
                                    target.getHexHash() + ".yacyh", parts, false, true);
                            if(content != null) {
                                /* Success with http : mark SSL as unavailable on the target peer */
                                markSSLUnavailableOnPeer(sb.peers, target, ip, "yacyClient.crawlReceipt");
                            }
                        } else {
                            throw e;
                        }
                    }
                }
                return FileUtils.table(content);
            } catch (final Exception e ) {
                // most probably a network time-out exception
                Network.log.warn("yacyClient.crawlReceipt error:" + e.getMessage());
            }
        }
        return null;
    }

    public static AtomicInteger metadataRetrievalRunning = new AtomicInteger(0);

    /**
     * transfer the index. If the transmission fails, return a string describing the
     * cause. If everything is ok, return null.
     *
     * @param sb
     *            the Switchboard instance holding server environment
     * @param targetSeed
     *            the target peer
     * @param indexes
     *            the index entries to transfer
     * @param urlCache
     * @param gzipBody
     *            when true, the transferred data are compressed using gzip
     * @param timeout
     *            the maximum time in milliseconds to wait for a success of the
     *            http(s) request to the remote peer
     * @return
     */
    public static String transferIndex(
        final Switchboard sb,
        final Seed targetSeed,
        final ReferenceContainerCache<WordReference> indexes,
        final HandleSet urlRefs,
        final Segment segment,
        final boolean gzipBody,
        final int timeout) {

        // check if we got all necessary urls in the urlCache (only for debugging)
        if (Network.log.isFine()) {
            Iterator<WordReference> eenum;
            Reference entry;
            for ( final ReferenceContainer<WordReference> ic : indexes ) {
                eenum = ic.entries();
                while ( eenum.hasNext() ) {
                    entry = eenum.next();
                    if ( !urlRefs.has(entry.urlhash()) ) {
                        Network.log.fine("DEBUG transferIndex: to-send url hash '"
                                + ASCII.String(entry.urlhash())
                                + "' is not contained in urlCache");
                    }
                }
            }
        }

        final boolean preferHttps = sb.getConfigBool(SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED,
                SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED_DEFAULT);

        // transfer the RWI without the URLs
        Map<String, String> in = transferRWI(targetSeed, indexes, gzipBody, timeout, preferHttps);

        if ( in == null ) {
            // targetSeed interface departure is already handled within transferRWI() for no response situation
            String errorCause = "no connection from transferRWI";
            return errorCause;
        }

        String result = in.get("result");
        if ( result == null ) {
            String errorCause = "no result from transferRWI";
            String usedIP = in.get(Seed.IP);
            sb.peers.peerActions.interfaceDeparture(targetSeed, usedIP); // disconnect unavailable peer
            return errorCause;
        }

        if ( !(result.equals("ok")) ) {
            targetSeed.setFlagAcceptRemoteIndex(false); // the peer does not want our index
            sb.peers.addConnected(targetSeed); // update the peer
            return result;
        }

        // in now contains a list of unknown hashes
        String uhss = in.get("unknownURL");
        if ( uhss == null ) {
            return "no unknownURL tag in response";
        }
        uhss = uhss.trim();
        if ( uhss.isEmpty() || uhss.equals(",") ) {
            return null;
        } // all url's known, we are ready here

        final String[] uhs = CommonPattern.COMMA.split(uhss);
        if ( uhs.length == 0 ) {
            return null;
        } // all url's known

        EventChannel.channels(EventChannel.DHTSEND).addMessage(new RSSMessage("Sent " + indexes.size() + " RWIs " + indexes.toString() + " to " + targetSeed.getName() + "/[" + targetSeed.hash + "], " + uhs.length + " URLs there unknown", "", targetSeed.hash));

        in = transferURL(targetSeed, uhs, urlRefs, segment, gzipBody, timeout, preferHttps);

        if ( in == null ) {
            return "no connection from transferURL";
        }

        result = in.get("result");
        if ( result == null ) {
            String errorCause = "no result from transferURL";
            String usedIP = in.get(Seed.IP);
            sb.peers.peerActions.interfaceDeparture(targetSeed, usedIP); // disconnect unavailable peer ip
            return errorCause;
        }

        if ( !result.equals("ok") ) {
            targetSeed.setFlagAcceptRemoteIndex(false); // the peer does not want our index
            sb.peers.addConnected(targetSeed); // update the peer
            return result;
        }
        EventChannel.channels(EventChannel.DHTSEND).addMessage(
            new RSSMessage(
                "Sent " + uhs.length + " URLs to peer " + targetSeed.getName()+ "/[" + targetSeed.hash + "]",
                "",
                targetSeed.hash));

        return null;
    }

    /**
     * Transfer Reverse Word Index entries to remote peer. If the used IP is not
     * responding, this IP (interface) is removed from targtSeed IP list. Remote
     * peer responds with list of unknown url hashes
     *
     * @param targetSeed
     *            the target peer
     * @param indexes
     *            the index entries to transfer
     * @param gzipBody
     *            when true, the transferred data are compressed using gzip
     * @param timeout
     *            the maximum time in milliseconds to wait for a success of the
     *            http(s) request(s) to the remote peer
     * @param preferHttps
     *            when true, use https when available on the target peer
     * @return peer response or null if transfer failed
     */
    private static Map<String, String> transferRWI(
        final Seed targetSeed,
        final ReferenceContainerCache<WordReference> indexes,
        boolean gzipBody,
        final int timeout,
        final boolean preferHttps) {
        for (final String ip : targetSeed.getIPs()) {
            if (ip == null) {
                Network.log.warn("no address for transferRWI");
                return null;
            }
            MultiProtocolURL targetBaseURL = null;
            try {
                targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, preferHttps);
            } catch(final MalformedURLException e) {
                Network.log.info("yacyClient.transferRWI malformed target URL : " + targetBaseURL);
                // disconnect unavailable peer ip
                Switchboard.getSwitchboard().peers.peerActions.interfaceDeparture(targetSeed, ip);
                continue;
            }

            // prepare post values
            final String salt = crypt.randomSalt();

            // enabling gzip compression for post request body
            if ( gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED) ) {
                gzipBody = false;
            }

            int indexcount = 0;
            final StringBuilder entrypost = new StringBuilder(indexes.size() * 73);
            Iterator<WordReference> eenum;
            Reference entry;
            for ( final ReferenceContainer<WordReference> ic : indexes ) {
                eenum = ic.entries();
                while ( eenum.hasNext() ) {
                    entry = eenum.next();
                    entrypost
                            .append(ASCII.String(ic.getTermHash()))
                            .append(entry.toPropertyForm())
                            .append(serverCore.CRLF_STRING);
                    indexcount++;
                }
            }

            if ( indexcount == 0 ) {
                // nothing to do but everything ok
                final Map<String, String> result = new HashMap<String, String>(2);
                result.put("result", "ok");
                result.put("unknownURL", "");
                return result;
            }
            try {
                final Map<String, ContentBody> parts = basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
                parts.put("wordc", UTF8.StringBody(Integer.toString(indexes.size())));
                parts.put("entryc", UTF8.StringBody(Integer.toString(indexcount)));
                parts.put("indexes", UTF8.StringBody(entrypost.toString()));
                byte[] content = null;
                try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, timeout)) {
                    try {
                        content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/transferRWI.html"),
                                targetSeed.getHexHash() + ".yacyh", parts, gzipBody, true);
                    } catch(final IOException e) {
                        if(targetBaseURL.isHTTPS()) {
                            targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, false);
                            /* Failed with https : retry with http on the same address */
                            content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/transferRWI.html"),
                                    targetSeed.getHexHash() + ".yacyh", parts, gzipBody, true);
                            if(content != null) {
                                /* Success with http : mark SSL as unavailable on the target peer */
                                markSSLUnavailableOnPeer(Switchboard.getSwitchboard().peers, targetSeed, ip, "yacyClient.transferRWI");
                            }
                        } else {
                            throw e;
                        }
                    }
                }
                final Iterator<String> v = FileUtils.strings(content);
                // this should return a list of urlhashes that are unknown

                final Map<String, String> result = FileUtils.table(v);
                // return the transfered index data in bytes (for debugging only)
                result.put("indexPayloadSize", Integer.toString(entrypost.length()));
                result.put(Seed.IP, ip); // add used ip to result for error handling (in case no "result" key was received)
                return result;
            } catch (final Exception e ) {
                Network.log.info("yacyClient.transferRWI to " + targetBaseURL + " error: " + e.getMessage());
                // disconnect unavailable peer ip
                Switchboard.getSwitchboard().peers.peerActions.interfaceDeparture(targetSeed, ip);
            }
        }
        return null;
    }

    /**
     * Transfer URL entries to remote peer
     *
     * @param targetSeed
     *            the target peer
     * @param uhs hashes of URLs to transfer (unknown by the target peer)
     * @param urlRefs list of locally known URLs entries
     * @param segment

     * @param gzipBody
     *            when true, the transferred data are compressed using gzip
     * @param timeout
     *            the maximum time in milliseconds to wait for a success of the
     *            http(s) request(s) to the remote peer
     * @param preferHttps when true, use https when available on the target peer
     * @return remote peer response
     */
    private static Map<String, String> transferURL(
        final Seed targetSeed,
        final String[] uhs,
        final HandleSet urlRefs,
        final Segment segment,
        boolean gzipBody,
        final int timeout,
        final boolean preferHttps) {
        // this post a message to the remote message board
        for (final String ip : targetSeed.getIPs()) {
            // prepare post values
            final String salt = crypt.randomSalt();
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);

            // enabling gzip compression for post request body
            if ( gzipBody && (targetSeed.getVersion() < yacyVersion.YACY_SUPPORTS_GZIP_POST_REQUESTS_CHUNKED) ) {
                gzipBody = false;
            }

            // extract the urlCache from the result; this is io-intensive;
            // other transmissions should not be started as long as this is running
            byte[] key;
            URIMetadataNode url;
            String resource;
            int urlc = 0;
            int urlPayloadSize = 0;
            metadataRetrievalRunning.incrementAndGet();
            for (int i = 0; i < uhs.length; i++) {
                key = ASCII.getBytes(uhs[i]);
                if (urlRefs.has(key)) {
                    url = segment.fulltext().getMetadata(key);
                    if (url == null) {
                        if (Network.log.isFine()) Network.log.fine("DEBUG transferIndex: requested url hash '" + uhs[i] + "'");
                        continue;
                    }
                    resource = url.toString();
                    //System.out.println("*** DEBUG resource = " + resource);
                    if ( resource != null && resource.indexOf(0) == -1 ) {
                        parts.put("url" + urlc, UTF8.StringBody(resource));
                        urlPayloadSize += resource.length();
                        urlc++;
                    }
                }
            }
            metadataRetrievalRunning.decrementAndGet();

            try {
                MultiProtocolURL targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, preferHttps);

                parts.put("urlc", UTF8.StringBody(Integer.toString(urlc)));
                byte[] content = null;
                try (final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, timeout)) {
                    try {
                        content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/transferURL.html"),
                                targetSeed.getHexHash() + ".yacyh", parts, gzipBody, true);
                    } catch(final IOException e) {
                        if(targetBaseURL.isHTTPS()) {
                            targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, false);
                            /* Failed with https : retry with http on the same address */
                            content = httpClient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/transferURL.html"),
                                    targetSeed.getHexHash() + ".yacyh", parts, gzipBody, true);
                        } else {
                            throw e;
                        }
                    }
                }
                final Iterator<String> v = FileUtils.strings(content);

                final Map<String, String> result = FileUtils.table(v);
                // return the transfered url data in bytes (for debugging only)
                result.put("urlPayloadSize", Integer.toString(urlPayloadSize));
                result.put(Seed.IP, ip); // add used ip to result for error handling (in case no "result" key was received)
                return result;
            } catch (final Exception e ) {
                Network.log.warn("yacyClient.transferURL to " + ip + " error: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Receive remote peers profile data
     *
     * @param sb a Switchboard instance holding server environment
     * @param targetSeed
     * @return profile or null
     */
    public static Map<String, String> getProfile(final Switchboard sb, final Seed targetSeed) {

        // this post a message to the remote message board
        final String salt = crypt.randomSalt();

        final boolean preferHttps = sb.getConfigBool(SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED,
                SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED_DEFAULT);

        for (final String ip : targetSeed.getIPs()) {
            try (final HTTPClient httpclient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 15000)) {
                final Map<String, ContentBody> parts = basicRequestParts(sb, targetSeed.hash, salt);
                MultiProtocolURL targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, preferHttps);
                byte[] content;
                try {
                    content = httpclient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/profile.html"),
                        targetSeed.getHexHash() + ".yacyh", parts, false, true);
                } catch(final IOException e) {
                    if(targetBaseURL.isHTTPS()) {
                        /* Failed with https : retry using http */
                        targetBaseURL = targetSeed.getPublicMultiprotocolURL(ip, false);
                        content = httpclient.POSTbytes(new MultiProtocolURL(targetBaseURL, "/yacy/profile.html"),
                                targetSeed.getHexHash() + ".yacyh", parts, false, true);
                        if(content != null) {
                            /* Got something with http : mark peer SSL as unavailable on target peer */
                            markSSLUnavailableOnPeer(sb.peers, targetSeed, ip, "yacyClient.getProfile");
                        }
                    } else {
                        throw e;
                    }
                }
                return FileUtils.table(content);
            } catch (final Exception e ) {
                Network.log.warn("yacyClient.getProfile error:" + e.getMessage());
            }
        }
        return null;
    }

    public static ReferenceContainerCache<HostReference> loadIDXHosts(final Seed target) {
        final ReferenceContainerCache<HostReference> index =
            new ReferenceContainerCache<HostReference>(
                WebStructureGraph.hostReferenceFactory,
                Base64Order.enhancedCoder,
                6);
        // check if the host supports this protocol
        if ( target.getVersion()< migration.IDX_HOST_VER ) {
            // if the protocol is not supported then we just return an empty host reference container
            return index;
        }

        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("host"));
            final Set<String> targetIps = target.getIPs();
            if(targetIps.isEmpty()) {
                Network.log.warn("yacyClient.loadIDXHosts error: no known address on target peer.");
                return null;
            }
            final String remoteBaseURL = target.getPublicURL(targetIps.iterator().next(),
                    Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED,
                            SwitchboardConstants.NETWORK_PROTOCOL_HTTPS_PREFERRED_DEFAULT));
            final Post post = new Post(new MultiProtocolURL(remoteBaseURL), target.hash, "/yacy/idx.json", parts, 30000);
            if ( post.result == null || post.result.length == 0 ) {
                Network.log.warn("yacyClient.loadIDXHosts error: empty result");
                return null;
            }
            final JSONObject json =
                new JSONObject(new JSONTokener(new InputStreamReader(new ByteArrayInputStream(post.result))));
            /* the json has the following form:
            {
            "version":"#[version]#",
            "uptime":"#[uptime]#",
            "name":"#[name]#",
            "rowdef":"#[rowdef]#",
            "idx":{
            #{list}#"#[term]#":[#[references]#]#(comma)#::,#(/comma)#
            #{/list}#
            }
            }
            */
            final JSONObject idx = json.getJSONObject("idx");
            // iterate over all references
            final Iterator<String> termIterator = idx.keys();
            String term;
            while ( termIterator.hasNext() ) {
                term = termIterator.next();
                final JSONArray references = idx.getJSONArray(term);
                // iterate until we get an exception or null
                int c = 0;
                String reference;
                final ReferenceContainer<HostReference> referenceContainer =
                    new ReferenceContainer<HostReference>(
                        WebStructureGraph.hostReferenceFactory,
                        UTF8.getBytes(term));
                try {
                    while ( (reference = references.getString(c++)) != null ) {
                        //System.out.println("REFERENCE: " + reference);
                        referenceContainer.add(new HostReference(reference));
                    }
                } catch (final JSONException e ) {
                } // this finishes the iteration
                index.add(referenceContainer);
            }
            return index;
        } catch (final Exception e ) {
            Network.log.warn("yacyClient.loadIDXHosts error:" + e.getMessage());
            return index;
        }
    }

    public static final boolean authentifyRequest(final serverObjects post, final serverSwitch env) {
        if ( post == null || env == null ) {
            return false;
        }

        // identify network
        final String unitName = post.get(SwitchboardConstants.NETWORK_NAME, Seed.DFLT_NETWORK_UNIT); // the network unit
        if ( !unitName.equals(env.getConfig(SwitchboardConstants.NETWORK_NAME, Seed.DFLT_NETWORK_UNIT)) ) {
            return false;
        }

        // check authentication method
        final String authenticationControl = env.getConfig("network.unit.protocol.control", "uncontrolled");
        if ( authenticationControl.equals("uncontrolled") ) {
            return true;
        }
        final String authenticationMethod =
            env.getConfig("network.unit.protocol.request.authentication.method", "");
        if ( authenticationMethod.isEmpty() ) {
            return false;
        }
        if ( authenticationMethod.equals("salted-magic-sim") ) {
            // authorize the peer using the md5-magic
            final String salt = post.get("key", "");
            final String iam = post.get("iam", "");
            final String magic = env.getConfig("network.unit.protocol.request.authentication.essentials", "");
            final String md5 = Digest.encodeMD5Hex(salt + iam + magic);
            return post.get("magicmd5", "").equals(md5);
        }

        // unknown authentication method
        return false;
    }

    /**
     * put in all the essentials for routing and network authentication
     * @param sb
     * @param targetHash
     * @param salt
     * @return
     */
    public static final LinkedHashMap<String, ContentBody> basicRequestParts(final Switchboard sb, final String targetHash, final String salt) {
        final LinkedHashMap<String, ContentBody> parts = new LinkedHashMap<String, ContentBody>();

        // just standard identification essentials
        if ( sb.peers.mySeed().hash != null ) {
            parts.put("iam", UTF8.StringBody(sb.peers.mySeed().hash));
            if ( targetHash != null ) parts.put("youare", UTF8.StringBody(targetHash));

            // time information for synchronization
            final long myTime = System.currentTimeMillis();
            String formattedTime;
            try {
                /* Prefer using first the shared and thread-safe DateTimeFormatter instance */
                formattedTime = GenericFormatter.FORMAT_SHORT_SECOND.format(Instant.ofEpochMilli(myTime));
            } catch(final DateTimeException e) {
                /* This should not happen, but rather than failing we fallback to the old formatter wich uses synchronization locks */
                formattedTime = GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date(myTime));
            }
            parts.put("mytime", UTF8.StringBody(formattedTime));
            parts.put("myUTC", UTF8.StringBody(Long.toString(myTime)));

            // network identification
            parts.put(SwitchboardConstants.NETWORK_NAME, UTF8.StringBody(Switchboard.getSwitchboard().getConfig(
                                SwitchboardConstants.NETWORK_NAME,
                                Seed.DFLT_NETWORK_UNIT)));
        }
        parts.put("key", UTF8.StringBody(salt));

        // authentication essentials
        final String authenticationControl = sb.getConfig("network.unit.protocol.control", "uncontrolled");
        final String authenticationMethod = sb.getConfig("network.unit.protocol.request.authentication.method", "");
        if ((authenticationControl.equals("controlled")) && (authenticationMethod.length() > 0) ) {
            if (authenticationMethod.equals("salted-magic-sim") ) {
                // generate an authentication essential using the salt, the iam-hash and the network magic
                final String magic = sb.getConfig("network.unit.protocol.request.authentication.essentials", "");
                final String md5 = Digest.encodeMD5Hex(salt + sb.peers.mySeed().hash + magic);
                parts.put("magicmd5", UTF8.StringBody(md5));
            }
        }
        return parts;
    }

    public static String requestPartsToString(Map<String, ContentBody> parts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ContentBody> part: parts.entrySet()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                part.getValue().writeTo(baos);
                baos.close();
                sb.append("&").append(part.getKey()).append("=").append(ASCII.String(baos.toByteArray()));
            } catch (IOException e) {}
        }
        return "?" + sb.toString().substring(1);
    }

    /**
     * Mark a SSL/TLS as unavailable on a connected peer and log an information
     * level message. Use when http is successful whereas https is not on the target
     * peer. All parameters must not be null.
     *
     * @param seedDB
     *            the seeds database to update
     * @param peer
     *            the peer to update
     * @param address
     *            the address on peer where http is successful but https fails.
     * @param logPrefix
     *            a prefix to the log message
     */
    private static void markSSLUnavailableOnPeer(final SeedDB seedDB, final Seed peer, final String address,
            final String logPrefix) {
        Network.log.info(logPrefix + " SSL/TLS unavailable on peer '" + peer.getName()
                + "' : can be reached using http but not https on address " + address);
        peer.setFlagSSLAvailable(false);
        seedDB.updateConnected(peer);
    }

}

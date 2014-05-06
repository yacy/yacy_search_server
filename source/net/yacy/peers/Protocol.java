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

package net.yacy.peers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.migration;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.opensearch.SRURSSConnector;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.JSONArray;
import net.yacy.cora.util.JSONException;
import net.yacy.cora.util.JSONObject;
import net.yacy.cora.util.JSONTokener;
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

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;


public final class Protocol {

    private static byte[] postToFile(
        final Seed target,
        final String filename,
        final Map<String, ContentBody> parts,
        final int timeout) throws IOException {
        return postToFile(target.getClusterAddress(), target.hash, filename, parts, timeout);
    }

    private static byte[] postToFile(
        final SeedDB seedDB,
        final String targetHash,
        final String filename,
        final Map<String, ContentBody> parts,
        final int timeout) throws IOException {
        return postToFile(seedDB.targetAddress(targetHash), targetHash, filename, parts, timeout);
    }

    private static byte[] postToFile(
        final String targetAddress,
        final String targetPeerHash,
        final String filename,
        final Map<String, ContentBody> parts,
        final int timeout) throws IOException {
        final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        httpClient.setTimout(timeout);
        return httpClient.POSTbytes(
            new MultiProtocolURL("http://" + targetAddress + "/yacy/" + filename),
            Seed.b64Hash2hexHash(targetPeerHash) + ".yacyh",
            parts,
            false, true);
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
    public static int hello(
        final Seed mySeed,
        final PeerActions peerActions,
        final String address,
        final String otherHash,
        final String otherName) {

        Map<String, String> result = null;
        final String salt = crypt.randomSalt();
        long responseTime = Long.MAX_VALUE;
        byte[] content = null;
        try {
            // generate request
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), null, salt);
            parts.put("count", UTF8.StringBody("20"));
            parts.put("magic", UTF8.StringBody(Long.toString(Network.magic)));
            parts.put("seed", UTF8.StringBody(mySeed.genSeedStr(salt)));
            // send request
            final long start = System.currentTimeMillis();
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/hello.html"), 30000, yacySeed.b64Hash2hexHash(otherHash) + ".yacyh", parts);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 30000);
            content =
                httpClient.POSTbytes(
                    new MultiProtocolURL("http://" + address + "/yacy/hello.html"),
                    Seed.b64Hash2hexHash(otherHash) + ".yacyh",
                    parts,
                    false, true);
            responseTime = System.currentTimeMillis() - start;
            result = FileUtils.table(content);
        } catch (final Exception e ) {
            if ( Thread.currentThread().isInterrupted() ) {
                Network.log.info("yacyClient.hello thread '"
                    + Thread.currentThread().getName()
                    + "' interrupted.");
                return -1;
            }
            Network.log.info("yacyClient.hello thread '"
                + Thread.currentThread().getName()
                + "', peer "
                + address
                + "; exception: "
                + e.getMessage());
            // try again (go into loop)
            result = null;
        }

        if (result == null || result.size() == 0) {
            Network.log.info("yacyClient.hello result error: "
                + ((result == null) ? "result null" : ("result=" + result.toString())));
            return -1;
        }
        Network.log.info("yacyClient.hello thread '"
                        + Thread.currentThread().getName()
                        + "' contacted peer at "
                        + address
                        + ", received "
                        + ((content == null) ? "null" : content.length)
                        + " bytes, time = "
                        + responseTime
                        + " milliseconds");

        // check consistency with expectation
        Seed otherPeer = null;
        String seed;
        if ( (otherHash != null) && (otherHash.length() > 0) && ((seed = result.get("seed0")) != null) ) {
            if ( seed.length() > Seed.maxsize ) {
                Network.log.info("hello/client 0: rejected contacting seed; too large ("
                    + seed.length()
                    + " > "
                    + Seed.maxsize
                    + ")");
            } else {
                try {
                    final int p = address.indexOf(':');
                    if ( p < 0 ) {
                        return -1;
                    }
                    String h = address.substring(0, p);
                    InetAddress ie = Domains.dnsResolve(h);
                    final String host = ie == null ? h : ie.getHostAddress(); // hack to prevent NPEs
                    otherPeer = Seed.genRemoteSeed(seed, false, host);
                    if ( !otherPeer.hash.equals(otherHash) ) {
                        Network.log.info("yacyClient.hello: consistency error: otherPeer.hash = "
                            + otherPeer.hash
                            + ", otherHash = "
                            + otherHash);
                        return -1; // no success
                    }
                } catch (final IOException e ) {
                    Network.log.info("yacyClient.hello: consistency error: other seed bad:"
                        + e.getMessage()
                        + ", seed="
                        + seed);
                    return -1; // no success
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
            mySeed.setIP(Switchboard.getSwitchboard().myPublicIP());
        } else {
            final String myIP = result.get("yourip");
            final String properIP = Seed.isProperIP(myIP);
            if ( properIP == null ) mySeed.setIP(myIP);
        }
        mySeed.setFlagRootNode(
                (mytype.equals(Seed.PEERTYPE_SENIOR) || mytype.equals(Seed.PEERTYPE_PRINCIPAL)) &&
                responseTime < 1000 && Domains.isThisHostIP(mySeed.getIP()) &&
                Switchboard.getSwitchboard().index.fulltext().connectedLocalSolr()
                ); // RootNode may receive direct Solr queries, set flag only if available
        
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
        Network.amIAccessibleDB.put(otherHash, accessible);

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
            return -1;
        }
        if ( mySeed.orVirgin().equals(Seed.PEERTYPE_VIRGIN) ) {
            mySeed.put(Seed.PEERTYPE, mytype);
        }

        final String error = mySeed.isProper(true);
        if ( error != null ) {
            Network.log.warn("yacyClient.hello mySeed error - not proper: " + error);
            return -1;
        }

        //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time

        // read the seeds that the peer returned and integrate them into own database
        int i = 0;
        int count = 0;
        String seedStr;
        Seed s;
        final int connectedBefore = peerActions.sizeConnected();
        while ( (seedStr = result.get("seed" + i++)) != null ) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
            if ( seedStr.length() > Seed.maxsize ) {
                Network.log.info("hello/client: rejected contacting seed; too large ("
                    + seedStr.length()
                    + " > "
                    + Seed.maxsize
                    + ")");
            } else {
                try {
                    if ( i == 1 ) {
                        final int p = address.indexOf(':');
                        if ( p < 0 ) {
                            return -1;
                        }
                        InetAddress ia = Domains.dnsResolve(address.substring(0, p));
                        if (ia == null) continue;
                        final String host = ia.getHostAddress();
                        s = Seed.genRemoteSeed(seedStr, false, host);
                    } else {
                        s = Seed.genRemoteSeed(seedStr, false, null);
                    }
                    if ( peerActions.peerArrival(s, (i == 1)) ) {
                        count++;
                    }
                } catch (final IOException e ) {
                    Network.log.info("hello/client: rejected contacting seed; bad ("
                        + e.getMessage()
                        + ")");
                }
            }
        }
        final int connectedAfter = peerActions.sizeConnected();

        // update event tracker
        EventTracker.update(EventTracker.EClass.PEERPING, new ProfilingGraph.EventPing(
            mySeed.getName(),
            otherName,
            true,
            connectedAfter - connectedBefore), false);

        return count;
    }
    /*
    private int readSeeds(String prefix) {
        String seedStr;
        while ( (seedStr = result.get("seed" + i++)) != null ) {
            // integrate new seed into own database
            // the first seed, "seed0" is the seed of the responding peer
            if ( seedStr.length() > Seed.maxsize ) {
                Network.log.logInfo("hello/client: rejected contacting seed; too large ("
                    + seedStr.length()
                    + " > "
                    + Seed.maxsize
                    + ")");
            } else {
                try {
                    if ( i == 1 ) {
                        final int p = address.indexOf(':');
                        if ( p < 0 ) {
                            return -1;
                        }
                        InetAddress ia = Domains.dnsResolve(address.substring(0, p));
                        if (ia == null) continue;
                        final String host = ia.getHostAddress();
                        s = Seed.genRemoteSeed(seedStr, false, host);
                    } else {
                        s = Seed.genRemoteSeed(seedStr, false, null);
                    }
                    if ( peerActions.peerArrival(s, (i == 1)) ) {
                        count++;
                    }
                } catch (final IOException e ) {
                    Network.log.logInfo("hello/client: rejected contacting seed; bad ("
                        + e.getMessage()
                        + ")");
                }
            }
        }
    }
*/
    public static Seed querySeed(final Seed target, final String seedHash) {
        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("seed"));
            parts.put("env", UTF8.StringBody(seedHash));
            final byte[] content = postToFile(target, "query.html", parts, 10000);
            final Map<String, String> result = FileUtils.table(content);

            if ( result == null || result.isEmpty() ) {
                return null;
            }
            //final Date remoteTime = yacyCore.parseUniversalDate((String) result.get(yacySeed.MYTIME)); // read remote time
            return Seed.genRemoteSeed(result.get("response"), false, target.getIP());
        } catch (final Exception e ) {
            Network.log.warn("yacyClient.querySeed error:" + e.getMessage());
            return null;
        }
    }

    public static long[] queryRWICount(final Seed target, final String wordHash) {
        if (target == null) return new long[] {-1, -1};
        
        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts = basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("object", UTF8.StringBody("rwicount"));
            parts.put("ttl", UTF8.StringBody("0"));
            parts.put("env", UTF8.StringBody(wordHash));
            final byte[] content = postToFile(target, "query.html", parts, 6000);
            final Map<String, String> result = FileUtils.table(content);
            if (result == null || result.isEmpty()) return new long[] {-1, -1};
            final String resp = result.get("response");
            if (resp == null) return new long[] {-1, -1};
            String magic = result.get("magic");
            if (magic == null) magic = "0";
            try {
                return new long[] {Long.parseLong(resp), Long.parseLong(magic)};
            } catch (final NumberFormatException e ) {
                return new long[] {-1, -1};
            }
        } catch (final Exception e ) {
            if (Network.log.isFine()) Network.log.fine("yacyClient.queryRWICount error:" + e.getMessage());
            return new long[] {-1, -1};
        }
    }

    public static RSSFeed queryRemoteCrawlURLs(
        final SeedDB seedDB,
        final Seed target,
        final int maxCount,
        final long maxTime) {
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
        try {
            /* a long time-out is needed */
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("call", UTF8.StringBody("remotecrawl"));
            parts.put("count", UTF8.StringBody(Integer.toString(maxCount)));
            parts.put("time", UTF8.StringBody(Long.toString(maxTime)));
            // final byte[] result = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/urls.xml"), (int) maxTime, target.getHexHash() + ".yacyh", parts);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, (int) maxTime);
            final byte[] result =
                httpClient.POSTbytes(new MultiProtocolURL("http://"
                    + target.getClusterAddress()
                    + "/yacy/urls.xml"), target.getHexHash() + ".yacyh", parts, false, true);
            final RSSReader reader = RSSReader.parse(RSSFeed.DEFAULT_MAXSIZE, result);
            if ( reader == null ) {
                Network.log.warn("yacyClient.queryRemoteCrawlURLs failed asking peer '"
                    + target.getName()
                    + "': probably bad response from remote peer (1), reader == null");
                target.put(Seed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //Log.logException(e);
                return null;
            }
            final RSSFeed feed = reader.getFeed();
            if ( feed == null ) {
                // case where the rss reader does not understand the content
                Network.log.warn("yacyClient.queryRemoteCrawlURLs failed asking peer '"
                    + target.getName()
                    + "': probably bad response from remote peer (2)");
                //System.out.println("***DEBUG*** rss input = " + UTF8.String(result));
                target.put(Seed.RCOUNT, "0");
                seedDB.update(target.hash, target); // overwrite number of remote-available number to avoid that this peer is called again (until update is done by peer ping)
                //Log.logException(e);
                return null;
            }
            // update number of remotely available links in seed
            target.put(Seed.RCOUNT, Integer.toString(Math.max(0, targetCount - feed.size())));
            seedDB.update(target.hash, target);
            return feed;
        } catch (final IOException e ) {
            Network.log.warn("yacyClient.queryRemoteCrawlURLs error asking peer '"
                + target.getName()
                + "':"
                + e.toString());
            return null;
        }
    }

    public static RSSFeed search(
        final Seed targetSeed,
        final String query,
        final CacheStrategy verify,
        final boolean global,
        final long timeout,
        final int startRecord,
        final int maximumRecords) throws IOException {
        final String address =
            (targetSeed == null || targetSeed == Switchboard.getSwitchboard().peers.mySeed()) ? "localhost:"
                + Switchboard.getSwitchboard().getConfig("port", "8090") : targetSeed.getClusterAddress();
        final String urlBase = "http://" + address + "/yacysearch.rss";
        return SRURSSConnector.loadSRURSS(
            urlBase,
            query,
            timeout,
            startRecord,
            maximumRecords,
            verify,
            global,
            ClientIdentification.yacyInternetCrawlerAgent);
    }

    protected static int primarySearch(
        final SearchEvent event,
        final String wordhashes,
        final String excludehashes,
        final String language,
        final String contentdom,
        final int count,
        final long time,
        final int maxDistance,
        final int partitions,
        final Seed target,
        final SecondarySearchSuperviser secondarySearchSuperviser,
        final Blacklist blacklist) {
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
        SearchResult result;
        String clusteraddress = target.getClusterAddress();
        if (clusteraddress.equals(event.peers.mySeed().getClusterAddress())) clusteraddress = "localhost:" + event.peers.mySeed().getPort();
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
                    count,
                    time,
                    maxDistance,
                    partitions,
                    target.getHexHash() + ".yacyh",
                    clusteraddress,
                    secondarySearchSuperviser
                    );
        } catch (final IOException e ) {
            Network.log.info("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + ")");
            event.peers.peerActions.peerDeparture(target, "search request to peer created io exception: " + e.getMessage());
            return -1;
        }
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
        final String contentdom,
        final int count,
        final long time,
        final int maxDistance,
        final int partitions,
        final Seed target,
        final Blacklist blacklist) {

        final long timestamp = System.currentTimeMillis();
        event.addExpectedRemoteReferences(count);
        SearchResult result;
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
                    count,
                    time,
                    maxDistance,
                    partitions,
                    target.getHexHash() + ".yacyh",
                    target.getClusterAddress(),
                    null
                    );
        } catch (final IOException e ) {
            Network.log.info("SEARCH failed, Peer: " + target.hash + ":" + target.getName() + " (" + e.getMessage() + ")");
            event.peers.peerActions.peerDeparture(target, "search request to peer created io exception: " + e.getMessage());
            return -1;
        }
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
        ) throws SpaceExceededException {

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
        Collection<URIMetadataNode> storeDocs = new ArrayList<URIMetadataNode>(result.links.size());
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
                    Network.log.info("remote search: filtered blacklisted url " + urlEntry.url() + " from peer " + target.getName());
                }
                continue; // block with backlist
            }

            final String urlRejectReason =
                Switchboard.getSwitchboard().crawlStacker.urlInAcceptedDomain(urlEntry.url());
            if ( urlRejectReason != null ) {
                if ( Network.log.isInfo() ) {
                    Network.log.info("remote search: rejected url '" + urlEntry.url() + "' (" + urlRejectReason + ") from peer " + target.getName());
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
                Network.log.info("remote search: url-hash " + ASCII.String(urlEntry.hash()) + " does not belong to word-attached-hash " + ASCII.String(entry.urlhash()) + "; url = " + urlEntry.url() + " from peer " + target.getName());
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
        
        for (URIMetadataNode entry: storeDocs) {
            try {
                event.query.getSegment().fulltext().putMetadata(entry);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        // store remote result to local result container
        // insert one container into the search result buffer
        // one is enough, only the references are used, not the word
        event.addRWIs(container.get(0), false, target.getName() + "/" + target.hash, result.totalCount, time);
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
            final String contentdom,
            final int count,
            final long time,
            final int maxDistance,
            final int partitions,
            final String hostname,
            final String hostaddress,
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
            
            String filter = event.query.urlMask.pattern().toString();
            parts.put("myseed", UTF8.StringBody((event.peers.mySeed() == null) ? "" : event.peers.mySeed().genSeedStr(key)));
            parts.put("count", UTF8.StringBody(Integer.toString(Math.max(10, count))));
            parts.put("time", UTF8.StringBody(Long.toString(Math.max(3000, time))));
            parts.put("partitions", UTF8.StringBody(Integer.toString(partitions)));
            parts.put("query", UTF8.StringBody(wordhashes));
            parts.put("exclude", UTF8.StringBody(excludehashes));
            parts.put("duetime", UTF8.StringBody("1000"));
            parts.put("urls", UTF8.StringBody(urlhashes));
            parts.put("prefer", UTF8.StringBody(event.query.prefer.pattern()));
            parts.put("filter", UTF8.StringBody(filter));
            parts.put("modifier", UTF8.StringBody(event.query.modifier.toString()));
            parts.put("language", UTF8.StringBody(language));
            parts.put("sitehash", UTF8.StringBody(event.query.modifier.sitehash));
            parts.put("author", UTF8.StringBody(event.query.modifier.author));
            parts.put("contentdom", UTF8.StringBody(contentdom));
            parts.put("ttl", UTF8.StringBody("0"));
            parts.put("maxdist", UTF8.StringBody(Integer.toString(maxDistance)));
            parts.put("profile", UTF8.StringBody(crypt.simpleEncode(event.query.ranking.toExternalString())));
            parts.put("constraint", UTF8.StringBody((event.query.constraint == null) ? "" : event.query.constraint.exportB64()));
            if ( secondarySearchSuperviser != null ) {
                parts.put("abstracts", UTF8.StringBody("auto"));
                // resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + hostaddress + "/yacy/search.html"), 60000, hostname, parts));
                //resultMap = FileUtils.table(HTTPConnector.getConnector(MultiProtocolURI.crawlerUserAgent).post(new MultiProtocolURI("http://" + target.getClusterAddress() + "/yacy/search.html"), 60000, target.getHexHash() + ".yacyh", parts));
            }

            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 8000);
            byte[] a = httpClient.POSTbytes(new MultiProtocolURL("http://" + hostaddress + "/yacy/search.html"), hostname, parts, false, true);
            if (a != null && a.length > 200000) {
                // there is something wrong. This is too large, maybe a hack on the other side?
                a = null;
            }
            resultMap = FileUtils.table(a);

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
            this.references = resultMap.get("references").split(",");
            this.links = new ArrayList<URIMetadataNode>(this.availableCount);
            for ( int n = 0; n < this.availableCount; n++ ) {
                // get one single search result
                final String resultLine = resultMap.get("resource" + n);
                if ( resultLine == null ) {
                    continue;
                }
                final URIMetadataNode urlEntry = URIMetadataNode.importEntry(resultLine);
                if ( urlEntry == null ) {
                    continue;
                }
                this.links.add(urlEntry);
            }
        }
    }

    private final static CollectionSchema[] snippetFields = new CollectionSchema[]{CollectionSchema.description_txt, CollectionSchema.h4_txt, CollectionSchema.h3_txt, CollectionSchema.h2_txt, CollectionSchema.h1_txt, CollectionSchema.text_t};
    
    protected static int solrQuery(
            final SearchEvent event,
            final SolrQuery solrQuery,
            final int offset,
            final int count,
            final Seed target,
            final int partitions,
            final Blacklist blacklist) {

        if (event.query.getQueryGoal().getQueryString(false) == null || event.query.getQueryGoal().getQueryString(false).length() == 0) {
            return -1; // we cannot query solr only with word hashes, there is no clear text string
        }
        event.addExpectedRemoteReferences(count);
        if (partitions > 0) solrQuery.set("partitions", partitions);
        solrQuery.setStart(offset);
        solrQuery.setRows(count);
        
        // set highlighting query attributes
        if (event.query.contentdom == Classification.ContentDomain.TEXT || event.query.contentdom == Classification.ContentDomain.ALL) {
            solrQuery.setHighlight(true);
            solrQuery.setHighlightFragsize(SearchEvent.SNIPPET_MAX_LENGTH);
            //solrQuery.setHighlightRequireFieldMatch();
            solrQuery.setHighlightSimplePost("</b>");
            solrQuery.setHighlightSimplePre("<b>");
            solrQuery.setHighlightSnippets(5);
            for (CollectionSchema field: snippetFields) solrQuery.addHighlightField(field.getSolrFieldName());
        } else {
            solrQuery.setHighlight(false);
        }
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
                try {
                    final boolean myseed = target == event.peers.mySeed();
                    if (!myseed && !target.getFlagSolrAvailable()) { // skip if peer.dna has flag that last try resulted in error
                        Network.log.info("SEARCH skip (solr), remote Solr interface not accessible, peer=" + target.getName());
                        return -1;
                    }
                    final String address = myseed ? "localhost:" + target.getPort() : target.getPublicAddress();
                    final int solrtimeout = Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_TIMEOUT, 6000);
                    Thread remoteRequest = new Thread() {
                        @Override
                        public void run() {
                            this.setName("Protocol.solrQuery(" + solrQuery.getQuery() + " to " + target.hash + ")");
                            try {
                                RemoteInstance instance = new RemoteInstance("http://" + address, null, "solr", solrtimeout); // this is a 'patch configuration' which considers 'solr' as default collection
                                try {
                                    SolrConnector solrConnector = new RemoteSolrConnector(instance, myseed ? true : target.getVersion() >= 1.63, "solr");
                                    if (!solrConnector.isClosed()) try {
                                        rsp[0] = solrConnector.getResponseByParams(solrQuery);
                                        docList[0] = rsp[0].getResults();
                                    } catch (Throwable e) {} finally {
                                        solrConnector.close();
                                    }
                                } catch (Throwable ee) {} finally {
                                    instance.close();
                                }
                            } catch (Throwable eee) {}
                        }
                    };
                    remoteRequest.start();
                    remoteRequest.join(solrtimeout); // just wait until timeout appears
                    if (remoteRequest.isAlive()) {
                        try {remoteRequest.interrupt();} catch (Throwable e) {}
                        Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + "/" + target.getPublicAddress() + " does not answer (time-out)");
                        target.setFlagSolrAvailable(false || myseed);
                        return -1; // give up, leave remoteRequest abandoned.
                    }
                    // no need to close this here because that sends a commit to remote solr which is not wanted here
                } catch (final Throwable e) {
                    Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + "/" + target.getPublicAddress() + " (" + e.getMessage() + ")");
                    target.setFlagSolrAvailable(false || localsearch);
                    return -1;
                }
            }

            if (rsp[0] == null || docList[0] == null) {
                Network.log.info("SEARCH failed (solr), remote Peer: " + target.getName() + "/" + target.getPublicAddress() + " returned null");
                target.setFlagSolrAvailable(false || localsearch);
                return -1;
            }
            
            // evaluate facets
            for (String field: event.query.facetfields) {
                FacetField facet = rsp[0].getFacetField(field);
                ReversibleScoreMap<String> result = new ClusteredScoreMap<String>(UTF8.insensitiveUTF8Comparator);
                List<Count> values = facet == null ? null : facet.getValues();
                if (values == null) continue;
                for (Count ff: values) {
                    int c = (int) ff.getCount();
                    if (c == 0) continue;
                    result.set(ff.getName(), c);
                }
                if (result.size() > 0) facets.put(field, result);
            }
            
            // evaluate snippets
            Map<String, Map<String, List<String>>> rawsnippets = rsp[0].getHighlighting(); // a map from the urlhash to a map with key=field and value = list of snippets
            if (rawsnippets != null) {
                nextsnippet: for (Map.Entry<String, Map<String, List<String>>> re: rawsnippets.entrySet()) {
                    Map<String, List<String>> rs = re.getValue();
                    for (CollectionSchema field: snippetFields) {
                        if (rs.containsKey(field.getSolrFieldName())) {
                            List<String> s = rs.get(field.getSolrFieldName());
                            if (s.size() > 0) {
                                LinkedHashSet<String> ls = new LinkedHashSet<String>();
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
        if (docList == null || docList[0].size() == 0) {
            Network.log.info("SEARCH (solr), returned 0 out of 0 documents from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())) + " query = " + solrQuery.toString()) ;
            return 0;
        }
	List<URIMetadataNode> container = new ArrayList<URIMetadataNode>();
        Network.log.info("SEARCH (solr), returned " + docList[0].size() + " out of " + docList[0].getNumFound() + " documents and " + facets.size() + " facets " + facets.keySet().toString() + " from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())));
        int term = count;
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>(docList[0].size());
        for (final SolrDocument doc: docList[0]) {
            if ( term-- <= 0 ) {
                break; // do not process more that requested (in case that evil peers fill us up with rubbish)
            }
            // get one single search result
            if ( doc == null ) {
                continue;
            }
            URIMetadataNode urlEntry = new URIMetadataNode(doc);

            if ( blacklist.isListed(BlacklistType.SEARCH, urlEntry.url()) ) {
                if ( Network.log.isInfo() ) {
                    if (localsearch) {
                        Network.log.info("local search (solr): filtered blacklisted url " + urlEntry.url());
                    } else {
                        Network.log.info("remote search (solr): filtered blacklisted url " + urlEntry.url() + " from " + (target == null ? "shard" : ("peer " + target.hash + ":" + target.getName())));
                    }
                }
                continue; // block with blacklist
            }

            final String urlRejectReason = Switchboard.getSwitchboard().crawlStacker.urlInAcceptedDomain(urlEntry.url());
            if ( urlRejectReason != null ) {
                if ( Network.log.isInfo() ) {
                    if (localsearch) {
                        Network.log.info("local search (solr): rejected url '" + urlEntry.url() + "' (" + urlRejectReason + ")");
                    } else {
                        Network.log.info("remote search (solr): rejected url '" + urlEntry.url() + "' (" + urlRejectReason + ") from peer " + target.getName());
                    }
                }
                continue; // reject url outside of our domain
            }

            // passed all checks, store url
            if (!localsearch) {
                // put the remote documents to the local index. We must convert the solr document to a solr input document:
                SolrInputDocument sid = event.query.getSegment().fulltext().getDefaultConfiguration().toSolrInputDocument(doc);

                // after this conversion we can remove the largest and not used field text_t and synonyms_sxt from the document
                // because that goes into a search cache and would take a lot of memory in the search cache
                //doc.removeFields(CollectionSchema.text_t.getSolrFieldName());
                doc.removeFields(CollectionSchema.synonyms_sxt.getSolrFieldName());
                
                // the input document stays untouched because it contains top-level cloned objects
                docs.add(sid);
                ResultURLs.stack(
                    ASCII.String(urlEntry.url().hash()),
                    urlEntry.url().getHost(),
                    event.peers.mySeed().hash.getBytes(),
                    UTF8.getBytes(target.hash),
                    EventOrigin.QUERIES);
            }

            // add the url entry to the word indexes
            container.add(urlEntry);
        }
        final int dls = docList[0].size();
        final int numFound = (int) docList[0].getNumFound();
        docList[0].clear();
        docList[0] = null;
        if (localsearch) {
            event.addNodes(container, facets, snippets, true, "localpeer", numFound);
            event.addFinalize();
            event.addExpectedRemoteReferences(-count);
            Network.log.info("local search (solr): localpeer sent " + container.size() + "/" + numFound + " references");
        } else {
            for (SolrInputDocument doc: docs) {
                event.query.getSegment().putDocument(doc);
            }
            docs.clear(); docs = null;
            event.addNodes(container, facets, snippets, false, target.getName() + "/" + target.hash, numFound);
            event.addFinalize();
            event.addExpectedRemoteReferences(-count);
            Network.log.info("remote search (solr): peer " + target.getName() + " sent " + (container.size() == 0 ? 0 : container.size()) + "/" + numFound + " references");
        }
        return dls;
    }

    public static Map<String, String> permissionMessage(final SeedDB seedDB, final String targetHash) {
        // ask for allowed message size and attachement size
        // if this replies null, the peer does not answer

        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), targetHash, salt);
            parts.put("process", UTF8.StringBody("permission"));
            final byte[] content = postToFile(seedDB, targetHash, "message.html", parts, 6000);
            final Map<String, String> result = FileUtils.table(content);
            return result;
        } catch (final Exception e ) {
            // most probably a network time-out exception
            Network.log.warn("yacyClient.permissionMessage error:" + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> postMessage(
        final SeedDB seedDB,
        final String targetHash,
        final String subject,
        final byte[] message) {
        // this post a message to the remote message board

        // prepare request
        final String salt = crypt.randomSalt();

        // send request
        try {
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), targetHash, salt);
            parts.put("process", UTF8.StringBody("post"));
            parts.put("myseed", UTF8.StringBody(seedDB.mySeed().genSeedStr(salt)));
            parts.put("subject", UTF8.StringBody(subject));
            parts.put("message", UTF8.StringBody(message));
            final byte[] content = postToFile(seedDB, targetHash, "message.html", parts, 20000);
            final Map<String, String> result = FileUtils.table(content);
            return result;
        } catch (final Exception e ) {
            Network.log.warn("yacyClient.postMessage error:" + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> crawlReceipt(
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

        // determining target address
        final String address = target.getClusterAddress();
        if ( address == null ) {
            return null;
        }

        // send request
        try {
            // prepare request
            final Map<String, ContentBody> parts = basicRequestParts(Switchboard.getSwitchboard(), target.hash, salt);
            parts.put("process", UTF8.StringBody(process));
            parts.put("urlhash", UTF8.StringBody(((entry == null) ? "" : ASCII.String(entry.hash()))));
            parts.put("result", UTF8.StringBody(result));
            parts.put("reason", UTF8.StringBody(reason));
            parts.put("wordh", UTF8.StringBody(wordhashes));
            parts.put(
                "lurlEntry",
                UTF8.StringBody(((entry == null) ? "" : crypt.simpleEncode(entry.toString(), salt))));
            // send request
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/crawlReceipt.html"), 10000, target.getHexHash() + ".yacyh", parts);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 10000);
            final byte[] content =
                httpClient.POSTbytes(
                    new MultiProtocolURL("http://" + address + "/yacy/crawlReceipt.html"),
                    target.getHexHash() + ".yacyh",
                    parts,
                    false, true);
            return FileUtils.table(content);
        } catch (final Exception e ) {
            // most probably a network time-out exception
            Network.log.warn("yacyClient.crawlReceipt error:" + e.getMessage());
            return null;
        }
    }

    public static AtomicInteger metadataRetrievalRunning = new AtomicInteger(0);

    /**
     * transfer the index. If the transmission fails, return a string describing the cause. If everything is
     * ok, return null.
     *
     * @param targetSeed
     * @param indexes
     * @param urlCache
     * @param gzipBody
     * @param timeout
     * @return
     */
    public static String transferIndex(
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
        
        // transfer the RWI without the URLs
        Map<String, String> in = transferRWI(targetSeed, indexes, gzipBody, timeout);

        if ( in == null ) {
            return "no connection from transferRWI";
        }

        String result = in.get("result");
        if ( result == null ) {
            return "no result from transferRWI";
        }

        if ( !(result.equals("ok")) ) {
            return result;
        }

        // in now contains a list of unknown hashes
        String uhss = in.get("unknownURL");
        if ( uhss == null ) {
            return "no unknownURL tag in response";
        }
        EventChannel
            .channels(EventChannel.DHTSEND)
            .addMessage(
                new RSSMessage(
                    "Sent " + indexes.size() + " RWIs to " + targetSeed.getName(),
                    "",
                    targetSeed.hash));

        uhss = uhss.trim();
        if ( uhss.isEmpty() || uhss.equals(",") ) {
            return null;
        } // all url's known, we are ready here

        final String[] uhs = uhss.split(",");
        if ( uhs.length == 0 ) {
            return null;
        } // all url's known

        in = transferURL(targetSeed, uhs, urlRefs, segment, gzipBody, timeout);

        if ( in == null ) {
            return "no connection from transferURL";
        }

        result = in.get("result");
        if ( result == null ) {
            return "no result from transferURL";
        }

        if ( !result.equals("ok") ) {
            return result;
        }
        EventChannel.channels(EventChannel.DHTSEND).addMessage(
            new RSSMessage(
                "Sent " + uhs.length + " URLs to peer " + targetSeed.getName(),
                "",
                targetSeed.hash));

        return null;
    }

    private static Map<String, String> transferRWI(
        final Seed targetSeed,
        final ReferenceContainerCache<WordReference> indexes,
        boolean gzipBody,
        final int timeout) {
        final String address = targetSeed.getPublicAddress();
        if ( address == null ) {
            Network.log.warn("no address for transferRWI");
            return null;
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
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
            parts.put("wordc", UTF8.StringBody(Integer.toString(indexes.size())));
            parts.put("entryc", UTF8.StringBody(Integer.toString(indexcount)));
            parts.put("indexes", UTF8.StringBody(entrypost.toString()));
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/transferRWI.html"), timeout, targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, timeout);
            final byte[] content =
                httpClient.POSTbytes(
                    new MultiProtocolURL("http://" + address + "/yacy/transferRWI.html"),
                    targetSeed.getHexHash() + ".yacyh",
                    parts,
                    gzipBody, true);
            final Iterator<String> v = FileUtils.strings(content);
            // this should return a list of urlhashes that are unknown

            final Map<String, String> result = FileUtils.table(v);
            // return the transfered index data in bytes (for debugging only)
            result.put("indexPayloadSize", Integer.toString(entrypost.length()));
            return result;
        } catch (final Exception e ) {
            Network.log.info("yacyClient.transferRWI to " + address + " error: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, String> transferURL(
        final Seed targetSeed,
        final String[] uhs,
        final HandleSet urlRefs,
        final Segment segment,
        boolean gzipBody,
        final int timeout) {
        // this post a message to the remote message board
        final String address = targetSeed.getPublicAddress();
        if ( address == null ) {
            return null;
        }

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
            parts.put("urlc", UTF8.StringBody(Integer.toString(urlc)));
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/transferURL.html"), timeout, targetSeed.getHexHash() + ".yacyh", parts, gzipBody);
            final HTTPClient httpClient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, timeout);
            final byte[] content =
                httpClient.POSTbytes(
                    new MultiProtocolURL("http://" + address + "/yacy/transferURL.html"),
                    targetSeed.getHexHash() + ".yacyh",
                    parts,
                    gzipBody, true);
            final Iterator<String> v = FileUtils.strings(content);

            final Map<String, String> result = FileUtils.table(v);
            // return the transfered url data in bytes (for debugging only)
            result.put("urlPayloadSize", Integer.toString(urlPayloadSize));
            return result;
        } catch (final Exception e ) {
            Network.log.warn("yacyClient.transferURL to " + address + " error: " + e.getMessage());
            return null;
        }
    }

    public static Map<String, String> getProfile(final Seed targetSeed) {
        // ReferenceContainerCache<HostReference> ref = loadIDXHosts(targetSeed);

        // this post a message to the remote message board
        final String salt = crypt.randomSalt();

        String address = targetSeed.getClusterAddress();
        if ( address == null ) {
            address = "localhost:8090";
        }
        try {
            final Map<String, ContentBody> parts =
                basicRequestParts(Switchboard.getSwitchboard(), targetSeed.hash, salt);
            // final byte[] content = HTTPConnector.getConnector(MultiProtocolURI.yacybotUserAgent).post(new MultiProtocolURI("http://" + address + "/yacy/profile.html"), 5000, targetSeed.getHexHash() + ".yacyh", parts);
            final HTTPClient httpclient = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent, 15000);
            final byte[] content =
                httpclient.POSTbytes(
                    new MultiProtocolURL("http://" + address + "/yacy/profile.html"),
                    targetSeed.getHexHash() + ".yacyh",
                    parts,
                    false, true);
            return FileUtils.table(content);
        } catch (final Exception e ) {
            Network.log.warn("yacyClient.getProfile error:" + e.getMessage());
            return null;
        }
    }

    public static ReferenceContainerCache<HostReference> loadIDXHosts(final Seed target) {
        final ReferenceContainerCache<HostReference> index =
            new ReferenceContainerCache<HostReference>(
                WebStructureGraph.hostReferenceFactory,
                Base64Order.enhancedCoder,
                6);
        // check if the host supports this protocol
        if ( target.getRevision() < migration.IDX_HOST ) {
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
            final byte[] content = postToFile(target, "idx.json", parts, 30000);
            if ( content == null || content.length == 0 ) {
                Network.log.warn("yacyClient.loadIDXHosts error: empty result");
                return null;
            }
            final JSONObject json =
                new JSONObject(new JSONTokener(new InputStreamReader(new ByteArrayInputStream(content))));
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

    private static final LinkedHashMap<String, ContentBody> basicRequestParts(
        final Switchboard sb,
        final String targetHash,
        final String salt) {
        // put in all the essentials for routing and network authentication
        // generate a session key
        final LinkedHashMap<String, ContentBody> parts =
            basicRequestParts(
                sb.peers.mySeed().hash,
                targetHash,
                Switchboard.getSwitchboard().getConfig(
                    SwitchboardConstants.NETWORK_NAME,
                    Seed.DFLT_NETWORK_UNIT));
        parts.put("key", UTF8.StringBody(salt));

        // authentication essentials
        final String authenticationControl = sb.getConfig("network.unit.protocol.control", "uncontrolled");
        final String authenticationMethod =
            sb.getConfig("network.unit.protocol.request.authentication.method", "");
        if ( (authenticationControl.equals("controlled")) && (authenticationMethod.length() > 0) ) {
            if ( authenticationMethod.equals("salted-magic-sim") ) {
                // generate an authentication essential using the salt, the iam-hash and the network magic
                final String magic =
                    sb.getConfig("network.unit.protocol.request.authentication.essentials", "");
                final String md5 = Digest.encodeMD5Hex(salt + sb.peers.mySeed().hash + magic);
                parts.put("magicmd5", UTF8.StringBody(md5));
            }
        }

        return parts;
    }

    private static final LinkedHashMap<String, ContentBody> basicRequestParts(
        final String myHash,
        final String targetHash,
        final String networkName) {
        // put in all the essentials for routing and network authentication
        // generate a session key
        final LinkedHashMap<String, ContentBody> parts = new LinkedHashMap<String, ContentBody>();

        // just standard identification essentials
        if ( myHash != null ) {
            parts.put("iam", UTF8.StringBody(myHash));
            if ( targetHash != null ) {
                parts.put("youare", UTF8.StringBody(targetHash));
            }

            // time information for synchronization
            // use our own formatter to prevent concurrency locks with other processes
            final GenericFormatter my_SHORT_SECOND_FORMATTER =
                new GenericFormatter(GenericFormatter.FORMAT_SHORT_SECOND, GenericFormatter.time_second);
            parts.put("mytime", UTF8.StringBody(my_SHORT_SECOND_FORMATTER.format()));
            parts.put("myUTC", UTF8.StringBody(Long.toString(System.currentTimeMillis())));

            // network identification
            parts.put(SwitchboardConstants.NETWORK_NAME, UTF8.StringBody(networkName));
        }

        return parts;
    }

}
